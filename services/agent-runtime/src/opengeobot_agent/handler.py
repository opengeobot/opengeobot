# Function: NATS handler for mission planning requests
# Time: 2026-07-06
# Author: AxeXie
"""NATS subscription handler for mission planning requests.

The handler subscribes to ``opengeobot.agent.mission.plan_request`` and
processes each request by calling the ``AgentRuntimeProvider`` to generate an
UNTRUSTED plan proposal. The proposal is returned to the caller on the NATS
reply subject.

The handler never crashes on individual request failures — it returns
structured error proposals instead, so the main event loop stays alive.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import TYPE_CHECKING

from loguru import logger
from nats.aio.msg import Msg

from .provider import AgentRuntimeProvider, MissionContext, PlanProposal

if TYPE_CHECKING:
    from .config import AgentConfig
    from .nats_client import NatsBridge


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class PlanningRequestHandler:
    """Handles inbound mission planning requests via NATS."""

    def __init__(
        self,
        config: AgentConfig,
        nats: NatsBridge,
        provider: AgentRuntimeProvider,
    ) -> None:
        self._config = config
        self._nats = nats
        self._provider = provider

    async def handle_plan_request(self, msg: Msg) -> None:
        """NATS subscription callback: generate a plan proposal."""
        raw = getattr(msg, "data", b"")
        try:
            payload = json.loads(raw)
            mission = MissionContext.model_validate(payload)
        except (json.JSONDecodeError, ValueError) as exc:
            logger.bind(error=str(exc)).warning(
                "Rejected malformed plan request payload"
            )
            await self._respond(msg, _error_proposal("", str(exc)))
            return

        logger.bind(
            mission_id=mission.mission_id,
            trace_id=mission.trace_id,
            robot_id=mission.robot_id,
        ).info("Received mission planning request")

        try:
            proposal = await self._provider.generate_plan(mission)
        except Exception as exc:  # noqa: BLE001 — provider failure must not crash handler
            logger.bind(
                mission_id=mission.mission_id,
                trace_id=mission.trace_id,
                error=str(exc),
            ).exception("Provider raised unexpected error")
            proposal = _error_proposal(mission.mission_id, str(exc), mission.trace_id)

        logger.bind(
            mission_id=proposal.mission_id,
            trace_id=proposal.trace_id,
            plan_id=proposal.plan_id,
            is_trusted=proposal.is_trusted,
            steps_count=len(proposal.steps),
        ).info("Plan proposal generated (UNTRUSTED)")

        await self._respond(msg, proposal)

    async def _respond(self, msg: Msg, proposal: PlanProposal) -> None:
        """Respond to the NATS request if a reply subject is available."""
        reply = getattr(msg, "reply", None)
        if not reply:
            logger.bind(mission_id=proposal.mission_id).warning(
                "No reply subject available; cannot return plan proposal"
            )
            return
        try:
            await self._nats.publish(
                reply, proposal.model_dump_json().encode("utf-8")
            )
        except Exception as exc:  # noqa: BLE001 — publish failure must not crash handler
            logger.bind(
                mission_id=proposal.mission_id,
                error=str(exc),
            ).warning("Failed to publish plan proposal response")


def _error_proposal(
    mission_id: str,
    error: str,
    trace_id: str = "",
) -> PlanProposal:
    """Build a failure plan proposal."""
    return PlanProposal(
        plan_id="",
        mission_id=mission_id,
        trace_id=trace_id,
        is_trusted=False,
        error=error,
        generated_at=_now_iso(),
    )
