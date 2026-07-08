# Function: Skill executor — receives approved requests, dispatches to adapters
# Time: 2026-07-06
# Author: AxeXie
"""Skill executor: receives Safety-Gateway-approved requests and dispatches them.

The executor subscribes to ``edge.{gateway_id}.skill.execute.approved`` — the
subject to which the Safety Gateway publishes requests that have passed both the
latching state machine and action-level safety checks.

For each approved request the executor:

1. Determines the target adapter (simulation / ROS2 / ROS1) based on the robot
   model configured in ``robot_model_adapter_map``.
2. Forwards the request to the adapter via NATS request-reply using
   ``AdapterClient``.
3. Returns the adapter's execution result to the original caller on the NATS
   reply subject.
4. Handles adapter timeouts and errors gracefully, returning structured
   failure responses instead of crashing the main loop.
5. Logs every execution with ``trace_id`` for end-to-end audit correlation.

The executor never calls ``/cmd_vel``, motors or vendor SDKs directly — all
motion is delegated to a registered, versioned skill in the downstream adapter.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import TYPE_CHECKING

from loguru import logger
from nats.aio.msg import Msg

from .adapter_client import (
    AdapterClient,
    AdapterTimeoutError,
    SkillExecutionRequest,
    SkillExecutionResponse,
)
from .config import ExecutorConfig

if TYPE_CHECKING:
    from .nats_client import NatsBridge


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class SkillExecutor:
    """Receives approved skill requests and dispatches them to the correct adapter."""

    def __init__(
        self,
        config: ExecutorConfig,
        nats: NatsBridge,
        adapter_client: AdapterClient,
    ) -> None:
        self._config = config
        self._nats = nats
        self._adapter_client = adapter_client
        # Maps robot_id → adapter type ("simulation" | "ros2" | "ros1").
        # In production this would be populated from fleet metadata; for M2
        # the default adapter type is used unless overridden.
        self._robot_adapter_map: dict[str, str] = {}

    def register_robot_adapter(self, robot_id: str, adapter_type: str) -> None:
        """Register the adapter type for a specific robot."""
        self._robot_adapter_map[robot_id] = adapter_type

    def resolve_adapter_type(self, robot_id: str) -> str:
        """Determine the adapter type for a robot, falling back to the default."""
        return self._robot_adapter_map.get(
            robot_id, self._config.default_adapter_type
        )

    async def handle_approved_request(self, msg: Msg) -> None:
        """NATS subscription callback: process an approved skill request."""
        raw = getattr(msg, "data", b"")
        try:
            payload = json.loads(raw)
            request = SkillExecutionRequest.model_validate(payload)
        except (json.JSONDecodeError, ValueError) as exc:
            logger.bind(error=str(exc)).warning(
                "Rejected malformed skill.execute.approved payload"
            )
            await self._respond(msg, _error_response("", "", "", str(exc)))
            return

        logger.bind(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
            robot_id=request.robot_id,
        ).info("Received approved skill execution request")

        adapter_type = self.resolve_adapter_type(request.robot_id)

        started_at = _now_iso()
        try:
            response = await self._adapter_client.call_adapter(adapter_type, request)
        except AdapterTimeoutError as exc:
            logger.bind(
                request_id=request.request_id,
                trace_id=request.trace_id,
                adapter_type=adapter_type,
            ).warning("Adapter timeout")
            response = SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error=str(exc),
                started_at=started_at,
                completed_at=_now_iso(),
            )
        except Exception as exc:  # noqa: BLE001 — adapter failure must not crash executor
            logger.bind(
                request_id=request.request_id,
                trace_id=request.trace_id,
                adapter_type=adapter_type,
                error=str(exc),
            ).exception("Adapter call failed")
            response = SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error=f"Adapter call failed: {exc}",
                started_at=started_at,
                completed_at=_now_iso(),
            )

        logger.bind(
            request_id=response.request_id,
            trace_id=response.trace_id,
            skill_id=response.skill_id,
            success=response.success,
            adapter_type=adapter_type,
        ).info("Skill execution completed")

        await self._respond(msg, response)

    async def _respond(self, msg: Msg, response: SkillExecutionResponse) -> None:
        """Respond to the original NATS request if a reply subject is available."""
        reply = getattr(msg, "reply", None)
        if not reply:
            logger.bind(request_id=response.request_id).warning(
                "No reply subject available; cannot return skill result"
            )
            return
        try:
            await self._nats.publish(reply, response.model_dump_json().encode("utf-8"))
        except Exception as exc:  # noqa: BLE001 — publish failure must not crash executor
            logger.bind(
                request_id=response.request_id,
                error=str(exc),
            ).warning("Failed to publish skill execution response")


def _error_response(
    request_id: str,
    trace_id: str,
    skill_id: str,
    error: str,
) -> SkillExecutionResponse:
    """Build a failure response for a malformed request."""
    now = _now_iso()
    return SkillExecutionResponse(
        request_id=request_id,
        trace_id=trace_id,
        skill_id=skill_id,
        success=False,
        error=error,
        started_at=now,
        completed_at=now,
    )
