# Function: Safety Gateway handler unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for the NATS subscription handler."""

from __future__ import annotations

import json
from typing import Any

import pytest

from opengeobot_safety_gateway.config import SafetyGatewayConfig
from opengeobot_safety_gateway.handler import SafetyHandler
from opengeobot_safety_gateway.safety_checker import SafetyChecker
from opengeobot_safety_gateway.safety_state import SafetyState, SafetyStateMachine


class MockMsg:
    """Mimics nats.aio.msg.Msg for testing."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply


class MockNats:
    """Records publishes for verification."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def subscribe(self, subject: str, handler: Any, queue: str | None = None) -> Any:
        return None

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        raise RuntimeError("Not used in tests")

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return True


def _make_config() -> SafetyGatewayConfig:
    return SafetyGatewayConfig(
        gateway_id="test_edge",
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        max_linear_speed=1.5,
        max_angular_speed=1.0,
        collision_proximity_threshold=0.5,
        restricted_zones=[],
        health_check_port=8081,
        skill_forward_subject_suffix="execute.approved",
        log_level="DEBUG",
    )


def _make_handler() -> tuple[SafetyHandler, MockNats, SafetyStateMachine]:
    config = _make_config()
    nats = MockNats()
    sm = SafetyStateMachine()
    checker = SafetyChecker(config)
    handler = SafetyHandler(config, nats, sm, checker)  # type: ignore[arg-type]
    return handler, nats, sm


def _make_msg(data: dict[str, Any], reply: str = "") -> MockMsg:
    return MockMsg(
        data=json.dumps(data).encode("utf-8"),
        reply=reply,
    )


def _published_on(nats: MockNats, subject_prefix: str) -> list[tuple[str, bytes]]:
    return [(s, d) for s, d in nats.published if s.startswith(subject_prefix)]


class TestEmergencyStopHandler:
    async def test_emergency_stop_triggers_state_machine(self):
        handler, nats, sm = _make_handler()
        msg = _make_msg({"trace_id": "t1", "reason": "Button pressed"})
        await handler.handle_emergency_stop(msg)
        assert sm.get_state() is SafetyState.EMERGENCY_STOPPED
        assert sm.is_safe() is False

    async def test_emergency_stop_publishes_state_change(self):
        handler, nats, sm = _make_handler()
        msg = _make_msg({"trace_id": "t1", "reason": "Button pressed"})
        await handler.handle_emergency_stop(msg)
        state_changes = _published_on(nats, "edge.test_edge.safety.state_changed")
        assert len(state_changes) == 1
        event = json.loads(state_changes[0][1])
        assert event["state"] == "EMERGENCY_STOPPED"
        assert event["gateway_id"] == "test_edge"
        assert event["trace_id"] == "t1"
        assert event["reason"] == "Button pressed"

    async def test_malformed_emergency_stop_still_triggers(self):
        """Fail safe: malformed payload must still trigger emergency stop."""
        handler, nats, sm = _make_handler()
        msg = MockMsg(data=b"not-json", reply="")
        await handler.handle_emergency_stop(msg)
        assert sm.get_state() is SafetyState.EMERGENCY_STOPPED
        assert sm.is_safe() is False

    async def test_emergency_stop_with_empty_payload(self):
        handler, nats, sm = _make_handler()
        msg = MockMsg(data=b"{}", reply="")
        await handler.handle_emergency_stop(msg)
        assert sm.get_state() is SafetyState.EMERGENCY_STOPPED


class TestResetHandler:
    async def test_reset_transitions_through_resetting_to_normal(self):
        handler, nats, sm = _make_handler()
        # First trigger emergency stop.
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")

        # Now reset.
        msg = _make_msg({"trace_id": "t2"})
        await handler.handle_reset(msg)

        assert sm.get_state() is SafetyState.NORMAL
        assert sm.is_safe() is True

    async def test_reset_publishes_two_state_changes(self):
        """Reset should publish: RESETTING then NORMAL."""
        handler, nats, sm = _make_handler()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")

        msg = _make_msg({"trace_id": "t2"})
        await handler.handle_reset(msg)

        state_changes = _published_on(nats, "edge.test_edge.safety.state_changed")
        assert len(state_changes) == 2

        first = json.loads(state_changes[0][1])
        assert first["state"] == "RESETTING"
        assert first["previous_state"] == "EMERGENCY_STOPPED"

        second = json.loads(state_changes[1][1])
        assert second["state"] == "NORMAL"
        assert second["previous_state"] == "RESETTING"

    async def test_reset_ignored_from_normal(self):
        """Reset from NORMAL should be ignored (no state change published)."""
        handler, nats, sm = _make_handler()
        msg = _make_msg({"trace_id": "t1"})
        await handler.handle_reset(msg)
        assert sm.get_state() is SafetyState.NORMAL
        state_changes = _published_on(nats, "edge.test_edge.safety.state_changed")
        assert len(state_changes) == 0

    async def test_reset_after_reset_fails(self):
        """Double reset: second should be ignored since already in NORMAL."""
        handler, nats, sm = _make_handler()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")

        msg = _make_msg({"trace_id": "t2"})
        await handler.handle_reset(msg)
        assert sm.get_state() is SafetyState.NORMAL

        nats.published.clear()
        await handler.handle_reset(msg)
        state_changes = _published_on(nats, "edge.test_edge.safety.state_changed")
        assert len(state_changes) == 0


class TestSkillExecutionInterception:
    async def test_skill_blocked_when_emergency_stopped(self):
        """When emergency stop is latched, skill execution must be blocked."""
        handler, nats, sm = _make_handler()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")

        skill_msg = _make_msg(
            {
                "request_id": "skreq_001",
                "trace_id": "t2",
                "robot_id": "rbt_01",
                "skill_id": "move_to",
                "params": {"position": {"x": 0.0, "y": 0.0}},
                "requested_at": "2026-01-01T00:00:00Z",
            },
            reply="reply.subject",
        )
        await handler.handle_skill_execute(skill_msg)

        # No forward to executor.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 0

        # Response should be published on the reply subject.
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["allowed"] is False
        assert response["state"] == "EMERGENCY_STOPPED"
        assert response["forwarded"] is False
        assert "safety_state" in response["denied_checks"]

    async def test_skill_allowed_and_forwarded_when_safe(self):
        """When safe, skill execution should be forwarded to the executor."""
        handler, nats, sm = _make_handler()
        assert sm.is_safe() is True

        skill_msg = _make_msg(
            {
                "request_id": "skreq_001",
                "trace_id": "t1",
                "robot_id": "rbt_01",
                "skill_id": "move_to",
                "params": {"position": {"x": 0.0, "y": 0.0}},
                "requested_at": "2026-01-01T00:00:00Z",
            },
            reply="reply.subject",
        )
        await handler.handle_skill_execute(skill_msg)

        # Should be forwarded to the executor subject.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 1
        forwarded = json.loads(forwards[0][1])
        assert forwarded["request_id"] == "skreq_001"
        assert forwarded["skill_id"] == "move_to"

        # Response should be published on the reply subject.
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["allowed"] is True
        assert response["forwarded"] is True
        assert response["state"] == "NORMAL"

    async def test_skill_denied_by_safety_checker(self):
        """When safe but speed exceeds limit, skill should be denied."""
        handler, nats, sm = _make_handler()
        assert sm.is_safe() is True

        skill_msg = _make_msg(
            {
                "request_id": "skreq_002",
                "trace_id": "t1",
                "robot_id": "rbt_01",
                "skill_id": "move_to",
                "params": {"linear_speed": 5.0},
                "requested_at": "2026-01-01T00:00:00Z",
            },
            reply="reply.subject",
        )
        await handler.handle_skill_execute(skill_msg)

        # Should NOT be forwarded.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 0

        # Response should deny.
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["allowed"] is False
        assert "speed_limit" in response["denied_checks"]

    async def test_skill_no_reply_subject(self):
        """When no reply subject, handler should not crash."""
        handler, nats, sm = _make_handler()
        skill_msg = _make_msg(
            {
                "request_id": "skreq_003",
                "trace_id": "t1",
                "robot_id": "rbt_01",
                "skill_id": "pickup",
                "params": {},
                "requested_at": "2026-01-01T00:00:00Z",
            },
            reply="",
        )
        await handler.handle_skill_execute(skill_msg)

        # Should still be forwarded.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 1

    async def test_malformed_skill_request_ignored(self):
        """Malformed skill request should be ignored gracefully."""
        handler, nats, sm = _make_handler()
        msg = MockMsg(data=b"not-json", reply="")
        await handler.handle_skill_execute(msg)
        # No forward, no crash.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 0

    async def test_skill_blocked_during_resetting(self):
        """During RESETTING state, skills should also be blocked."""
        handler, nats, sm = _make_handler()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")
        await sm.request_reset(trace_id="t2")
        assert sm.get_state() is SafetyState.RESETTING

        skill_msg = _make_msg(
            {
                "request_id": "skreq_004",
                "trace_id": "t3",
                "robot_id": "rbt_01",
                "skill_id": "move_to",
                "params": {},
                "requested_at": "2026-01-01T00:00:00Z",
            },
            reply="reply.subject",
        )
        await handler.handle_skill_execute(skill_msg)

        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 0

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["allowed"] is False
        assert response["state"] == "RESETTING"


class TestStateChangePublishing:
    async def test_state_change_event_has_event_id(self):
        handler, nats, sm = _make_handler()
        msg = _make_msg({"trace_id": "t1", "reason": "Test"})
        await handler.handle_emergency_stop(msg)

        state_changes = _published_on(nats, "edge.test_edge.safety.state_changed")
        event = json.loads(state_changes[0][1])
        assert event["event_id"].startswith("sevt_")

    async def test_state_change_on_reset_has_correct_previous_state(self):
        handler, nats, sm = _make_handler()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")

        msg = _make_msg({"trace_id": "t2"})
        await handler.handle_reset(msg)

        state_changes = _published_on(nats, "edge.test_edge.safety.state_changed")
        # First state change: EMERGENCY_STOPPED -> RESETTING
        first = json.loads(state_changes[0][1])
        assert first["previous_state"] == "EMERGENCY_STOPPED"
        assert first["state"] == "RESETTING"
        # Second state change: RESETTING -> NORMAL
        second = json.loads(state_changes[1][1])
        assert second["previous_state"] == "RESETTING"
        assert second["state"] == "NORMAL"
