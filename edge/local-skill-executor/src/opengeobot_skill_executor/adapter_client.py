# Function: Adapter client — forwards skill requests to ROS2/ROS1/simulation adapters
# Time: 2026-07-06
# Author: AxeXie
"""Client for forwarding skill execution requests to the appropriate adapter.

The adapter client sends each approved skill request to the target adapter via
NATS request-reply and parses the response. It never calls ``/cmd_vel``, motors
or vendor SDKs directly — all motion is delegated to a registered, versioned
skill in the downstream adapter.

Adapter routing is determined by the ``SkillExecutor`` based on the robot model
before calling ``AdapterClient.call_adapter``.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from typing import TYPE_CHECKING, Any

from loguru import logger
from pydantic import BaseModel, Field

if TYPE_CHECKING:
    from .nats_client import NatsBridge

from .config import ExecutorConfig


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class SkillExecutionRequest(BaseModel):
    """Skill execution request forwarded from the Safety Gateway."""

    request_id: str
    trace_id: str = ""
    robot_id: str = ""
    skill_id: str = ""
    params: dict[str, Any] = Field(default_factory=dict)
    requested_at: str = ""

    model_config = {"extra": "ignore"}


class SkillExecutionResponse(BaseModel):
    """Adapter response to a skill execution request."""

    request_id: str
    trace_id: str = ""
    skill_id: str = ""
    success: bool
    output: dict[str, Any] = Field(default_factory=dict)
    error: str | None = None
    started_at: str = ""
    completed_at: str = ""

    model_config = {"extra": "ignore"}


class AdapterTimeoutError(RuntimeError):
    """Raised when the adapter does not respond within the configured timeout."""


class AdapterClient:
    """Forwards skill execution requests to adapters via NATS request-reply."""

    def __init__(self, config: ExecutorConfig, nats: NatsBridge) -> None:
        self._config = config
        self._nats = nats

    async def call_adapter(
        self,
        adapter_type: str,
        request: SkillExecutionRequest,
    ) -> SkillExecutionResponse:
        """Send a skill request to the target adapter and await the reply.

        Raises ``AdapterTimeoutError`` if the adapter does not respond in time,
        or ``RuntimeError`` if the NATS connection is unavailable.
        """
        subject = self._config.adapter_subject(adapter_type, request.robot_id)
        payload = request.model_dump_json().encode("utf-8")

        logger.bind(
            request_id=request.request_id,
            trace_id=request.trace_id,
            adapter_type=adapter_type,
            subject=subject,
        ).info("Forwarding skill request to adapter")

        try:
            reply = await self._nats.request(
                subject,
                payload,
                timeout=self._config.adapter_timeout,
            )
        except asyncio.TimeoutError as exc:
            raise AdapterTimeoutError(
                f"Adapter '{adapter_type}' did not respond within "
                f"{self._config.adapter_timeout}s for request {request.request_id}"
            ) from exc

        try:
            response = SkillExecutionResponse.model_validate_json(reply.data)
        except ValueError as exc:
            logger.bind(
                request_id=request.request_id,
                error=str(exc),
            ).error("Adapter returned malformed response")
            return SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error=f"Malformed adapter response: {exc}",
                started_at=_now_iso(),
                completed_at=_now_iso(),
            )

        return response
