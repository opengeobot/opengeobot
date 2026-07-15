# Function: ROS2 adapter skill execution interface (F-ADAPTER-003)
# Time: 2026-07-15
# Author: AxeXie
"""Skill execution adapter over NATS JetStream request/reply.

The adapter subscribes to
``opengeobot.dev.edge.ros2.skill.execute.{robot_id}``, executes the requested
skill via rclpy, and replies with a ``SkillExecutionResponse``.

It never publishes ``/cmd_vel`` directly from an Agent/LLM - all motion is a
registered, versioned skill executed through the Safety Gateway. The adapter
does **not** maintain a local safety latch; safety is delegated to the
SafetyStateMachine.

When rclpy is unavailable (e.g. unit-test host) the skills operate in
simulation mode, returning success without touching a real robot.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from typing import Any

import nats
from loguru import logger
from nats.aio.client import Client as NatsClient
from nats.aio.msg import Msg
from nats.js.api import StorageType, StreamConfig
from pydantic import BaseModel, Field

from .config import Ros2Config
from .skills import SkillContext, SkillResult, default_skills
from .skills.base import RCLPY_AVAILABLE, Node

# Default QoS depth for publishers.
DEFAULT_QOS_DEPTH = 10


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class SkillExecutionRequest(BaseModel):
    """Edge -> ROS2 adapter skill execution request."""

    request_id: str
    trace_id: str
    robot_id: str
    skill_id: str
    params: dict[str, Any] = Field(default_factory=dict)
    requested_at: str = ""

    model_config = {"extra": "ignore"}


class SkillExecutionResponse(BaseModel):
    """ROS2 adapter -> edge skill execution response."""

    request_id: str
    trace_id: str
    skill_id: str
    success: bool
    output: dict[str, Any] = Field(default_factory=dict)
    error: str | None = None
    started_at: str
    completed_at: str


class Ros2Adapter:
    """NATS-driven ROS2 adapter executing registered skills via rclpy."""

    def __init__(self, config: Ros2Config) -> None:
        self._config = config
        self._nc: NatsClient | None = None
        self._js: Any = None
        self._active_executions = 0
        self._stop_event = asyncio.Event()

        # Initialise rclpy node when available.
        self._node: Node | None = None
        if RCLPY_AVAILABLE:
            self._node = Node("opengeobot_ros2")

        self._skills = default_skills(node=self._node)

    @property
    def registered_skills(self) -> list[str]:
        return list(self._skills.keys())

    async def start(self) -> None:
        self._nc = await nats.connect(
            servers=self._config.nats_url,
            name=f"ros2-adapter-{self._config.robot_id}",
            max_reconnect_attempts=self._config.nats_max_reconnect,
            reconnect_time_wait=self._config.nats_reconnect_wait,
            connect_timeout=self._config.nats_connect_timeout,
            allow_reconnect=True,
        )
        self._js = self._nc.jetstream()
        await self._ensure_stream(
            self._config.jetstream_stream_name,
            self._config.jetstream_stream_subjects,
        )
        await self._js.subscribe(
            self._config.skill_execute_subject,
            cb=self._handle_request,
            durable=self._config.jetstream_durable_name,
            manual_ack=True,
        )
        logger.bind(
            robot_id=self._config.robot_id,
            skills=self.registered_skills,
            rclpy_available=RCLPY_AVAILABLE,
        ).info("ROS2 adapter started and subscribed to skill execute channel")

    async def _ensure_stream(self, name: str, subjects: list[str]) -> None:
        """Create or update a JetStream stream covering the given subjects."""
        if self._js is None:
            logger.warning("JetStream not initialised; cannot ensure stream")
            return
        try:
            config = StreamConfig(
                name=name,
                subjects=subjects,
                storage=StorageType.FILE,
            )
            await self._js.add_stream(config=config)
            logger.bind(stream=name, subjects=subjects).info(
                "JetStream stream ensured"
            )
        except Exception as exc:  # noqa: BLE001 - stream creation failure must not crash adapter
            logger.bind(stream=name, error=str(exc)).warning(
                "Failed to ensure JetStream stream; adapter continues"
            )

    async def stop(self) -> None:
        self._stop_event.set()
        if self._nc is not None:
            try:
                await self._nc.drain()
            finally:
                self._nc = None
                self._js = None

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()

    async def _handle_request(self, msg: Msg) -> None:
        try:
            request = SkillExecutionRequest.model_validate_json(msg.data)
        except ValueError as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed skill request")
            await self._reply_error(
                msg, request_id="", trace_id="", skill_id="", error=str(exc)
            )
            await self._ack(msg)
            return

        logger.bind(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
        ).info("Executing skill")

        started_at = _now_iso()
        # Safety is delegated to the SafetyStateMachine; the adapter does not
        # maintain its own safety latch.
        ctx = SkillContext(active_executions=self._active_executions)

        skill = self._skills.get(request.skill_id)
        if skill is None:
            response = SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error=f"Unknown skill_id {request.skill_id}",
                started_at=started_at,
                completed_at=_now_iso(),
            )
            await self._respond(msg, response)
            await self._ack(msg)
            return

        self._active_executions += 1
        # Refresh the context so emergency_stop sees the live count.
        ctx = SkillContext(active_executions=self._active_executions)
        try:
            result: SkillResult = await skill.execute(request.params, ctx)
        except Exception as exc:  # noqa: BLE001 - a skill crash must not kill the adapter
            logger.bind(skill=request.skill_id, error=str(exc)).exception(
                "Skill execution raised"
            )
            result = SkillResult(success=False, error=f"Skill crashed: {exc}")
        finally:
            self._active_executions -= 1

        response = SkillExecutionResponse(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
            success=result.success,
            output=result.output,
            error=result.error,
            started_at=started_at,
            completed_at=_now_iso(),
        )
        await self._respond(msg, response)
        await self._ack(msg)

    async def _respond(self, msg: Msg, response: SkillExecutionResponse) -> None:
        if msg.reply is None or self._nc is None:
            logger.bind(request_id=response.request_id).warning(
                "No reply subject available; cannot return skill result"
            )
            return
        await self._nc.publish(msg.reply, response.model_dump_json().encode("utf-8"))

    async def _reply_error(
        self,
        msg: Msg,
        *,
        request_id: str,
        trace_id: str,
        skill_id: str,
        error: str,
    ) -> None:
        response = SkillExecutionResponse(
            request_id=request_id,
            trace_id=trace_id,
            skill_id=skill_id,
            success=False,
            error=error,
            started_at=_now_iso(),
            completed_at=_now_iso(),
        )
        await self._respond(msg, response)

    async def _ack(self, msg: Msg) -> None:
        """Acknowledge a JetStream message if it supports ack.

        Core NATS messages have no ``ack`` method and are silently skipped;
        JetStream messages are explicitly acked after execution completes so
        they are not redelivered.
        """
        ack = getattr(msg, "ack", None)
        if ack is None:
            return
        try:
            await ack()
        except Exception as exc:  # noqa: BLE001 - ack failure must not crash adapter
            logger.bind(error=str(exc)).warning("Failed to ack JetStream message")
