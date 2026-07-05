# Function: Simulation adapter skill execution interface (F-ADAPTER-001)
# Time: 2026-07-05
# Author: AxeXie
"""Skill execution adapter over NATS request/reply.

The adapter subscribes to ``opengeobot.dev.edge.skill.execute.{robot_id}``,
executes the requested skill against the local (simulated) skill registry, and
replies with a ``SkillExecutionResponse``. It never publishes ``/cmd_vel`` or
calls vendor SDKs directly — all motion is a registered, versioned skill.

When the ROS2 Jazzy contract (EXT-ROS2-JAZZY) is pinned, the skill registry
will be swapped for rclpy-backed implementations behind the same ``Skill``
protocol, without changing this adapter or the edge gateway.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from typing import Any

import nats
from loguru import logger
from nats.aio.client import Client as NatsClient
from nats.aio.msg import Msg
from pydantic import BaseModel, Field

from .config import SimConfig
from .skills import SkillContext, SkillResult, default_skills


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class SkillExecutionRequest(BaseModel):
    """Edge → simulator skill execution request."""

    request_id: str
    trace_id: str
    robot_id: str
    skill_id: str
    params: dict[str, Any] = Field(default_factory=dict)
    requested_at: str = ""

    model_config = {"extra": "ignore"}


class SkillExecutionResponse(BaseModel):
    """Simulator → edge skill execution response."""

    request_id: str
    trace_id: str
    skill_id: str
    success: bool
    output: dict[str, Any] = Field(default_factory=dict)
    error: str | None = None
    started_at: str
    completed_at: str


class SimAdapter:
    """NATS-driven simulation adapter executing the registered skills."""

    def __init__(self, config: SimConfig) -> None:
        self._config = config
        self._nc: NatsClient | None = None
        self._skills = default_skills()
        self._active_executions = 0
        self._safety_latched = False
        self._stop_event = asyncio.Event()

    @property
    def registered_skills(self) -> list[str]:
        return list(self._skills.keys())

    async def start(self) -> None:
        self._nc = await nats.connect(
            servers=self._config.nats_url,
            name=f"sim-adapter-{self._config.robot_id}",
            max_reconnect_attempts=self._config.nats_max_reconnect,
            reconnect_time_wait=self._config.nats_reconnect_wait,
            connect_timeout=self._config.nats_connect_timeout,
            allow_reconnect=True,
        )
        await self._nc.subscribe(
            self._config.skill_execute_subject, cb=self._handle_request
        )
        logger.bind(
            robot_id=self._config.robot_id,
            skills=self.registered_skills,
        ).info("Sim adapter started and subscribed to skill execute channel")

    async def stop(self) -> None:
        self._stop_event.set()
        if self._nc is not None:
            try:
                await self._nc.drain()
            finally:
                self._nc = None

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()

    async def _handle_request(self, msg: Msg) -> None:
        try:
            request = SkillExecutionRequest.model_validate_json(msg.data)
        except ValueError as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed skill request")
            await self._reply_error(msg, request_id="", trace_id="", skill_id="", error=str(exc))
            return

        logger.bind(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
        ).info("Executing skill")

        started_at = _now_iso()
        ctx = SkillContext(
            active_executions=self._active_executions,
            safety_latched=self._safety_latched,
            simulation_step=self._config.simulation_step,
        )

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
            return

        self._active_executions += 1
        # Refresh the context so emergency_stop sees the live count.
        ctx = SkillContext(
            active_executions=self._active_executions,
            safety_latched=self._safety_latched,
            simulation_step=self._config.simulation_step,
        )
        try:
            result: SkillResult = await skill.execute(request.params, ctx)
        except Exception as exc:  # noqa: BLE001 — a skill crash must not kill the adapter
            logger.bind(skill=request.skill_id, error=str(exc)).exception(
                "Skill execution raised"
            )
            result = SkillResult(success=False, error=f"Skill crashed: {exc}")
            if request.skill_id == "emergency_stop":
                self._safety_latched = True
        finally:
            self._active_executions -= 1

        if request.skill_id == "emergency_stop" and result.success:
            # Local, latching emergency stop.
            self._safety_latched = True

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
