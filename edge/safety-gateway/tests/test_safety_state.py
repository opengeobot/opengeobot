# Function: Safety state machine unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for the latching safety state machine (SM-SAFETY-001)."""

from __future__ import annotations

import asyncio

from opengeobot_safety_gateway.safety_state import (
    SafetyState,
    SafetyStateMachine,
)


class TestSafetyStateMachineInit:
    def test_initial_state_is_normal(self):
        sm = SafetyStateMachine()
        assert sm.get_state() is SafetyState.NORMAL

    def test_is_safe_true_when_normal(self):
        sm = SafetyStateMachine()
        assert sm.is_safe() is True

    def test_get_snapshot_reflects_initial_state(self):
        sm = SafetyStateMachine()
        snapshot = sm.get_snapshot()
        assert snapshot.state is SafetyState.NORMAL
        assert snapshot.safe is True
        assert snapshot.last_transition_at != ""


class TestEmergencyStop:
    async def test_trigger_from_normal_transitions_to_emergency_stopped(self):
        sm = SafetyStateMachine()
        changed = await sm.trigger_emergency_stop(reason="Button pressed", trace_id="t1")
        assert changed is True
        assert sm.get_state() is SafetyState.EMERGENCY_STOPPED
        assert sm.is_safe() is False

    async def test_trigger_is_latching(self):
        """Once triggered, is_safe() must stay False until explicit reset."""
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")
        # State remains EMERGENCY_STOPPED — latched.
        assert sm.is_safe() is False
        assert sm.get_state() is SafetyState.EMERGENCY_STOPPED

    async def test_trigger_is_idempotent(self):
        """Re-triggering emergency stop stays in EMERGENCY_STOPPED."""
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(reason="First", trace_id="t1")
        changed = await sm.trigger_emergency_stop(reason="Second", trace_id="t2")
        assert changed is False
        assert sm.get_state() is SafetyState.EMERGENCY_STOPPED
        assert sm.is_safe() is False

    async def test_trigger_from_resetting_re_latches(self):
        """Emergency stop during RESETTING must re-latch to EMERGENCY_STOPPED."""
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(reason="Initial", trace_id="t1")
        await sm.request_reset(trace_id="t2")
        assert sm.get_state() is SafetyState.RESETTING
        changed = await sm.trigger_emergency_stop(reason="Re-latch", trace_id="t3")
        assert changed is True
        assert sm.get_state() is SafetyState.EMERGENCY_STOPPED
        assert sm.is_safe() is False

    async def test_trigger_with_empty_reason_uses_default(self):
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(trace_id="t1")
        snapshot = sm.get_snapshot()
        assert snapshot.reason == "Emergency stop triggered"


class TestReset:
    async def test_request_reset_from_emergency_stopped(self):
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")
        ok = await sm.request_reset(trace_id="t2")
        assert ok is True
        assert sm.get_state() is SafetyState.RESETTING
        # RESETTING is NOT safe — skills still blocked.
        assert sm.is_safe() is False

    async def test_request_reset_from_normal_fails(self):
        sm = SafetyStateMachine()
        ok = await sm.request_reset(trace_id="t1")
        assert ok is False
        assert sm.get_state() is SafetyState.NORMAL

    async def test_complete_reset_from_resetting(self):
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")
        await sm.request_reset(trace_id="t2")
        ok = await sm.complete_reset(trace_id="t3")
        assert ok is True
        assert sm.get_state() is SafetyState.NORMAL
        assert sm.is_safe() is True

    async def test_complete_reset_from_normal_fails(self):
        sm = SafetyStateMachine()
        ok = await sm.complete_reset(trace_id="t1")
        assert ok is False
        assert sm.get_state() is SafetyState.NORMAL

    async def test_complete_reset_from_emergency_stopped_fails(self):
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")
        ok = await sm.complete_reset(trace_id="t2")
        assert ok is False
        assert sm.get_state() is SafetyState.EMERGENCY_STOPPED


class TestFullResetFlow:
    async def test_full_reset_flow(self):
        """NORMAL -> EMERGENCY_STOPPED -> RESETTING -> NORMAL -> is_safe True."""
        sm = SafetyStateMachine()

        # Trigger emergency stop.
        await sm.trigger_emergency_stop(reason="Obstacle detected", trace_id="t1")
        assert sm.is_safe() is False

        # Request reset (transitions to RESETTING, still not safe).
        ok = await sm.request_reset(trace_id="t2")
        assert ok is True
        assert sm.get_state() is SafetyState.RESETTING
        assert sm.is_safe() is False

        # Complete reset (transitions to NORMAL, now safe).
        ok = await sm.complete_reset(trace_id="t3")
        assert ok is True
        assert sm.get_state() is SafetyState.NORMAL
        assert sm.is_safe() is True

    async def test_skills_remain_blocked_throughout_reset(self):
        """Skills must be blocked in both EMERGENCY_STOPPED and RESETTING."""
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")
        assert sm.is_safe() is False

        await sm.request_reset(trace_id="t2")
        assert sm.is_safe() is False

        await sm.complete_reset(trace_id="t3")
        assert sm.is_safe() is True


class TestTransitionLog:
    async def test_transitions_are_logged(self):
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")
        await sm.request_reset(trace_id="t2")
        await sm.complete_reset(trace_id="t3")

        transitions = sm.get_transitions()
        assert len(transitions) == 3

        # EMERGENCY_STOPPED transition.
        t1 = transitions[0]
        assert t1.from_state is SafetyState.NORMAL
        assert t1.to_state is SafetyState.EMERGENCY_STOPPED
        assert t1.trace_id == "t1"
        assert t1.timestamp != ""

        # RESETTING transition.
        t2 = transitions[1]
        assert t2.from_state is SafetyState.EMERGENCY_STOPPED
        assert t2.to_state is SafetyState.RESETTING
        assert t2.trace_id == "t2"

        # NORMAL transition.
        t3 = transitions[2]
        assert t3.from_state is SafetyState.RESETTING
        assert t3.to_state is SafetyState.NORMAL
        assert t3.trace_id == "t3"

    async def test_re_trigger_logs_transition(self):
        sm = SafetyStateMachine()
        await sm.trigger_emergency_stop(reason="First", trace_id="t1")
        await sm.trigger_emergency_stop(reason="Second", trace_id="t2")
        transitions = sm.get_transitions()
        assert len(transitions) == 2
        # Second trigger: from EMERGENCY_STOPPED to EMERGENCY_STOPPED.
        assert transitions[1].from_state is SafetyState.EMERGENCY_STOPPED
        assert transitions[1].to_state is SafetyState.EMERGENCY_STOPPED


class TestConcurrency:
    async def test_concurrent_trigger_emergency_stop(self):
        """Concurrent trigger_emergency_stop calls must not corrupt state."""
        sm = SafetyStateMachine()
        results = await asyncio.gather(
            sm.trigger_emergency_stop(reason="Concurrent 1", trace_id="t1"),
            sm.trigger_emergency_stop(reason="Concurrent 2", trace_id="t2"),
            sm.trigger_emergency_stop(reason="Concurrent 3", trace_id="t3"),
        )
        # All calls succeed; state must be EMERGENCY_STOPPED.
        assert sm.get_state() is SafetyState.EMERGENCY_STOPPED
        assert sm.is_safe() is False
        # At least one changed (the first), others may or may not.
        assert any(results)
