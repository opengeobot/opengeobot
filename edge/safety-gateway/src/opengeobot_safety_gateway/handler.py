# Function: NATS subscription handler for safety commands and skill interception
# Time: 2026-07-06
# Author: AxeXie
"""NATS subscription handler for the Safety Gateway.

Subscribes to three inbound subjects and publishes state changes:

  * ``edge.{gateway_id}.safety.emergency_stop`` — latches the emergency stop.
  * ``edge.{gateway_id}.safety.reset`` — transitions through RESETTING → NORMAL.
  * ``edge.{gateway_id}.skill.execute`` — intercepts skill execution requests,
    validates safety, and forwards approved requests to the local skill
    executor.

State changes are broadcast on ``edge.{gateway_id}.safety.state_changed``.

When the safety state machine is **not** in NORMAL state, ALL skill execution
requests are blocked regardless of the action-level check result. This is the
latching behaviour required by the safety red line: once triggered, the
emergency stop stays active until an explicit reset completes.
"""

from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone
from typing import TYPE_CHECKING

from loguru import logger
from pydantic import BaseModel, Field

from .config import SafetyGatewayConfig
from .safety_checker import SafetyChecker, SafetyDecision
from .safety_state import SafetyState, SafetyStateMachine

if TYPE_CHECKING:
    from .nats_client import NatsBridge


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _new_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex}"


# ------------------------------------------------------------------
# Message models (NATS payload contracts).
# ------------------------------------------------------------------


class EmergencyStopCommand(BaseModel):
    """Inbound: emergency stop command."""

    trace_id: str = ""
    reason: str = ""
    timestamp: str = ""

    model_config = {"extra": "ignore"}


class ResetCommand(BaseModel):
    """Inbound: safety reset command."""

    trace_id: str = ""
    timestamp: str = ""

    model_config = {"extra": "ignore"}


class SkillExecutionRequest(BaseModel):
    """Inbound: skill execution request to be intercepted."""

    request_id: str
    trace_id: str = ""
    robot_id: str = ""
    skill_id: str = ""
    params: dict[str, object] = Field(default_factory=dict)
    requested_at: str = ""

    model_config = {"extra": "ignore"}


class SafetyDecisionResponse(BaseModel):
    """Response to a skill execution request (safety decision)."""

    request_id: str
    trace_id: str
    allowed: bool
    reason: str
    state: str
    denied_checks: list[str] = Field(default_factory=list)
    forwarded: bool = False
    timestamp: str

    model_config = {"extra": "ignore"}


class SkillExecutionResponse(BaseModel):
    """Execution result returned to the edge gateway after safety interception."""

    request_id: str
    trace_id: str = ""
    skill_id: str = ""
    success: bool
    output: dict[str, object] = Field(default_factory=dict)
    error: str | None = None
    started_at: str = ""
    completed_at: str = ""

    model_config = {"extra": "ignore"}


class SafetyStateChangedEvent(BaseModel):
    """Outbound: safety state change broadcast."""

    event_id: str
    gateway_id: str
    state: SafetyState
    reason: str = ""
    previous_state: SafetyState = SafetyState.NORMAL
    timestamp: str
    trace_id: str = ""

    model_config = {"extra": "ignore"}


class SafetyHandler:
    """Processes inbound safety commands and intercepts skill execution."""

    def __init__(
        self,
        config: SafetyGatewayConfig,
        nats: NatsBridge,
        state_machine: SafetyStateMachine,
        safety_checker: SafetyChecker,
    ) -> None:
        self._config = config
        self._nats = nats
        self._sm = state_machine
        self._checker = safety_checker
        # Other robot positions for collision check (updated by external telemetry).
        self._robot_positions: dict[str, tuple[float, float]] = {}

    def update_robot_positions(self, positions: dict[str, tuple[float, float]]) -> None:
        """Update known robot positions for collision risk checks."""
        self._robot_positions = dict(positions)

    # ------------------------------------------------------------------
    # NATS subscription callbacks.
    # ------------------------------------------------------------------
    async def handle_emergency_stop(self, msg: object) -> None:
        """Latch the emergency stop from an inbound NATS command."""
        raw = getattr(msg, "data", b"")
        trace_id = ""
        try:
            payload = json.loads(raw)
            command = EmergencyStopCommand.model_validate(payload)
            trace_id = command.trace_id
        except (json.JSONDecodeError, ValueError) as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed emergency_stop payload")
            # Still trigger the emergency stop — fail safe.
            command = EmergencyStopCommand(reason="Malformed emergency_stop payload")

        previous = self._sm.get_state()
        await self._sm.trigger_emergency_stop(reason=command.reason, trace_id=trace_id)
        await self._publish_state_change(trace_id=trace_id, previous_state=previous)

    async def handle_reset(self, msg: object) -> None:
        """Transition through RESETTING → NORMAL from an inbound NATS command."""
        raw = getattr(msg, "data", b"")
        trace_id = ""
        try:
            payload = json.loads(raw)
            command = ResetCommand.model_validate(payload)
            trace_id = command.trace_id
        except (json.JSONDecodeError, ValueError) as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed reset payload")
            command = ResetCommand()

        # Phase 1: EMERGENCY_STOPPED → RESETTING.
        previous = self._sm.get_state()
        ok = await self._sm.request_reset(trace_id=trace_id)
        if not ok:
            logger.bind(
                current_state=self._sm.get_state().value,
                trace_id=trace_id,
            ).warning("Reset ignored — not in EMERGENCY_STOPPED state")
            return
        await self._publish_state_change(trace_id=trace_id, previous_state=previous)

        # Phase 2: RESETTING → NORMAL.
        previous = self._sm.get_state()
        await self._sm.complete_reset(trace_id=trace_id)
        await self._publish_state_change(trace_id=trace_id, previous_state=previous)

    async def handle_skill_execute(self, msg: object) -> None:
        """Intercept a skill execution request, validate, and forward if safe.

        Uses NATS request-reply to forward approved requests to the skill
        executor and returns the execution result as a ``SkillExecutionResponse``
        to the original caller. If the safety check blocks the request, a
        failure ``SkillExecutionResponse`` is returned directly.
        """
        raw = getattr(msg, "data", b"")
        try:
            payload = json.loads(raw)
            request = SkillExecutionRequest.model_validate(payload)
        except (json.JSONDecodeError, ValueError) as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed skill.execute payload")
            await self._respond_error(msg, request_id="", trace_id="", skill_id="", error=str(exc))
            return

        logger.bind(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
            robot_id=request.robot_id,
        ).info("Intercepted skill execution request")

        decision = self._evaluate(request)

        if not decision.allowed:
            # Safety check blocked the request - return a failure response.
            response = SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error=f"Safety blocked: {decision.reason}",
                started_at=_now_iso(),
                completed_at=_now_iso(),
            )
            await self._respond(msg, response.model_dump_json().encode("utf-8"))
            return

        # Safety check passed - forward to skill executor via request-reply.
        try:
            reply = await self._nats.request(
                self._config.skill_forward_subject,
                request.model_dump_json().encode("utf-8"),
                timeout=30.0,
            )
            response = SkillExecutionResponse.model_validate_json(reply.data)
        except Exception as exc:  # noqa: BLE001 - executor failure must not crash gateway
            logger.bind(error=str(exc)).warning("Skill executor request failed")
            response = SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error=f"Skill executor unavailable: {exc}",
                started_at=_now_iso(),
                completed_at=_now_iso(),
            )

        await self._respond(msg, response.model_dump_json().encode("utf-8"))

    # ------------------------------------------------------------------
    # Internal helpers.
    # ------------------------------------------------------------------
    def _evaluate(self, request: SkillExecutionRequest) -> SafetyDecision:
        """Evaluate a skill request against the safety state and action checks."""
        # Gate 1: latching safety state — if not NORMAL, block immediately.
        if not self._sm.is_safe():
            return SafetyDecision(
                allowed=False,
                reason=f"Safety latch engaged (state={self._sm.get_state().value}); "
                f"skill execution blocked until reset",
                robot_id=request.robot_id,
                skill_name=request.skill_id,
                trace_id=request.trace_id,
                checks_run=["safety_state"],
                denied_checks=["safety_state"],
                timestamp=_now_iso(),
            )

        # Gate 2: action-level safety checks.
        return self._checker.check_skill_execution(
            robot_id=request.robot_id,
            skill_name=request.skill_id,
            params=request.params,
            robot_positions=self._robot_positions,
            trace_id=request.trace_id,
        )

    async def _publish_state_change(
        self, trace_id: str = "", previous_state: SafetyState = SafetyState.NORMAL
    ) -> None:
        """Broadcast the current safety state on the state_changed subject."""
        snapshot = self._sm.get_snapshot()
        event = SafetyStateChangedEvent(
            event_id=_new_id("sevt"),
            gateway_id=self._config.gateway_id,
            state=snapshot.state,
            reason=snapshot.reason,
            previous_state=previous_state,
            timestamp=snapshot.last_transition_at,
            trace_id=trace_id,
        )
        payload = event.model_dump_json().encode("utf-8")
        try:
            await self._nats.publish(self._config.state_changed_subject, payload)
            logger.bind(
                event_id=event.event_id,
                state=event.state.value,
                previous_state=event.previous_state.value,
                trace_id=trace_id,
            ).info("Published safety state change")
        except Exception as exc:  # noqa: BLE001 — publishing failure must not crash gateway
            logger.bind(error=str(exc)).warning(
                "Failed to publish safety state change (NATS unavailable); "
                "state is still latched locally"
            )

    async def _respond(self, msg: object, data: bytes) -> None:
        """Respond to a NATS request if a reply subject is available."""
        reply = getattr(msg, "reply", None)
        if reply:
            try:
                await self._nats.publish(reply, data)
            except Exception as exc:  # noqa: BLE001
                logger.bind(error=str(exc)).warning("Failed to respond to skill.execute request")

    async def _respond_error(
        self, msg: object, *, request_id: str, trace_id: str, skill_id: str, error: str
    ) -> None:
        """Respond with a failure SkillExecutionResponse for malformed requests."""
        response = SkillExecutionResponse(
            request_id=request_id,
            trace_id=trace_id,
            skill_id=skill_id,
            success=False,
            error=error,
            started_at=_now_iso(),
            completed_at=_now_iso(),
        )
        await self._respond(msg, response.model_dump_json().encode("utf-8"))

    async def _ack(self, msg: object) -> None:
        """Acknowledge a JetStream message if it supports ack.

        Core NATS messages have no ``ack`` method and are silently skipped;
        JetStream messages are explicitly acked so they are not redelivered.
        """
        ack = getattr(msg, "ack", None)
        if ack is None:
            return
        try:
            await ack()
        except Exception as exc:  # noqa: BLE001 - ack failure must not crash gateway
            logger.bind(error=str(exc)).warning("Failed to ack JetStream message")
