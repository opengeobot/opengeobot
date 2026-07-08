# Function: Safety state machine — latching emergency stop (SM-SAFETY-001)
# Time: 2026-07-06
# Author: AxeXie
"""Latching safety state machine for the edge Safety Gateway.

Implements the SM-SAFETY-001 state machine:
    NORMAL → EMERGENCY_STOPPED → RESETTING → NORMAL

Key safety red lines enforced here:
  * Emergency stop is **latching** — once triggered, ALL skill execution is
    blocked until an explicit ``complete_reset()`` returns the machine to
    NORMAL.
  * The machine operates **entirely locally**. It does not depend on the cloud,
    NATS, or any network connection. The state lives in process memory and
    survives NATS disconnects.
  * ``trigger_emergency_stop()`` can be called from ANY state (NORMAL,
    RESETTING, or even EMERGENCY_STOPPED itself). It always re-latches.
  * ``request_reset()`` is only valid from EMERGENCY_STOPPED. It transitions to
    RESETTING, which is still **not safe** — skills remain blocked.
  * ``complete_reset()`` is only valid from RESETTING. It transitions to
    NORMAL, at which point ``is_safe()`` returns True again.

All transitions are logged with UTC timestamps and trace context so they can
be correlated with the audit trail.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from enum import Enum

from loguru import logger
from pydantic import BaseModel, Field


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class SafetyState(str, Enum):
    """States of the SM-SAFETY-001 state machine."""

    NORMAL = "NORMAL"
    EMERGENCY_STOPPED = "EMERGENCY_STOPPED"
    RESETTING = "RESETTING"


class StateTransition(BaseModel):
    """Audit record for a single state machine transition."""

    from_state: SafetyState
    to_state: SafetyState
    reason: str = ""
    trace_id: str = ""
    timestamp: str

    model_config = {"extra": "ignore"}


class StateSnapshot(BaseModel):
    """Immutable snapshot of the current safety state."""

    state: SafetyState
    reason: str = ""
    last_transition_at: str = ""
    safe: bool

    model_config = {"extra": "ignore"}


class SafetyStateMachine:
    """Thread-safe (asyncio) latching safety state machine.

    The state machine is the authoritative local safety latch. It must function
    without cloud or network connectivity — ``trigger_emergency_stop()`` and
    ``is_safe()`` are callable at any time, from any coroutine.
    """

    def __init__(self) -> None:
        self._state: SafetyState = SafetyState.NORMAL
        self._reason: str = ""
        self._last_transition_at: str = _now_iso()
        self._lock = asyncio.Lock()
        self._transitions: list[StateTransition] = []

    @property
    def state(self) -> SafetyState:
        """Current state (atomic read; no lock required)."""
        return self._state

    def is_safe(self) -> bool:
        """Returns True only when the machine is in NORMAL state."""
        return self._state is SafetyState.NORMAL

    def get_state(self) -> SafetyState:
        """Returns the current SafetyState enum value."""
        return self._state

    def get_snapshot(self) -> StateSnapshot:
        """Returns an immutable snapshot of the current state."""
        return StateSnapshot(
            state=self._state,
            reason=self._reason,
            last_transition_at=self._last_transition_at,
            safe=self.is_safe(),
        )

    def get_transitions(self) -> list[StateTransition]:
        """Returns a copy of the transition history (for audit/debugging)."""
        return list(self._transitions)

    async def trigger_emergency_stop(self, reason: str = "", trace_id: str = "") -> bool:
        """Latch the emergency stop from any state.

        Returns True if the state changed, False if it was already
        EMERGENCY_STOPPED with the same reason.
        """
        async with self._lock:
            previous = self._state
            now = _now_iso()
            self._state = SafetyState.EMERGENCY_STOPPED
            self._reason = reason or "Emergency stop triggered"
            self._last_transition_at = now

            transition = StateTransition(
                from_state=previous,
                to_state=SafetyState.EMERGENCY_STOPPED,
                reason=self._reason,
                trace_id=trace_id,
                timestamp=now,
            )
            self._transitions.append(transition)

            logger.bind(
                from_state=previous.value,
                to_state="EMERGENCY_STOPPED",
                reason=self._reason,
                trace_id=trace_id,
            ).warning("Emergency stop latched — all skill execution blocked")
            return previous is not SafetyState.EMERGENCY_STOPPED

    async def request_reset(self, trace_id: str = "") -> bool:
        """Transition from EMERGENCY_STOPPED to RESETTING.

        Returns True if the transition succeeded, False if the machine was not
        in EMERGENCY_STOPPED state. RESETTING is **not safe** — skills remain
        blocked until ``complete_reset()``.
        """
        async with self._lock:
            if self._state is not SafetyState.EMERGENCY_STOPPED:
                logger.bind(
                    current_state=self._state.value,
                    trace_id=trace_id,
                ).warning("Reset requested from non-EMERGENCY_STOPPED state; ignored")
                return False

            now = _now_iso()
            self._state = SafetyState.RESETTING
            self._reason = "Reset requested"
            self._last_transition_at = now

            transition = StateTransition(
                from_state=SafetyState.EMERGENCY_STOPPED,
                to_state=SafetyState.RESETTING,
                reason="Reset requested",
                trace_id=trace_id,
                timestamp=now,
            )
            self._transitions.append(transition)

            logger.bind(
                from_state="EMERGENCY_STOPPED",
                to_state="RESETTING",
                trace_id=trace_id,
            ).info("Safety reset initiated — transitioning to RESETTING")
            return True

    async def complete_reset(self, trace_id: str = "") -> bool:
        """Transition from RESETTING to NORMAL.

        Returns True if the transition succeeded, False if the machine was not
        in RESETTING state. Only after this call does ``is_safe()`` return True.
        """
        async with self._lock:
            if self._state is not SafetyState.RESETTING:
                logger.bind(
                    current_state=self._state.value,
                    trace_id=trace_id,
                ).warning("Complete reset called from non-RESETTING state; ignored")
                return False

            now = _now_iso()
            self._state = SafetyState.NORMAL
            self._reason = "Reset completed"
            self._last_transition_at = now

            transition = StateTransition(
                from_state=SafetyState.RESETTING,
                to_state=SafetyState.NORMAL,
                reason="Reset completed",
                trace_id=trace_id,
                timestamp=now,
            )
            self._transitions.append(transition)

            logger.bind(
                from_state="RESETTING",
                to_state="NORMAL",
                trace_id=trace_id,
            ).info("Safety reset completed — system is now SAFE")
            return True
