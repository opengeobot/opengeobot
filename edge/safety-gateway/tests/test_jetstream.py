# Function: JetStream persistence tests for the Safety Gateway
# Time: 2026-07-09
# Author: AxeXie
"""JetStream persistence tests for the Safety Gateway.

Verifies that:
  * The NatsBridge creates a JetStream stream with the correct name and subjects.
  * The NatsBridge subscribes via a durable consumer with manual ack.
  * The handler acks JetStream messages after safety evaluation and forwarding.
"""

from __future__ import annotations

import json
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from opengeobot_safety_gateway.config import SafetyGatewayConfig
from opengeobot_safety_gateway.handler import SafetyHandler
from opengeobot_safety_gateway.nats_client import NatsBridge
from opengeobot_safety_gateway.safety_checker import SafetyChecker
from opengeobot_safety_gateway.safety_state import SafetyStateMachine


# ------------------------------------------------------------------
# Mock objects.
# ------------------------------------------------------------------


class JetStreamMockMsg:
    """Mimics a nats.aio.msg.Msg with JetStream ack support."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply
        self.acked = False
        self.nacked = False

    async def ack(self) -> None:
        self.acked = True

    async def nak(self, delay: float | None = None) -> None:
        self.nacked = True

    async def term(self) -> None:
        pass


class MockNats:
    """Records publishes for verification."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def subscribe(self, subject: str, handler: Any, queue: str | None = None) -> Any:
        return None

    async def subscribe_jetstream(
        self, subject: str, handler: Any, durable: str, manual_ack: bool = True
    ) -> Any:
        return None

    async def ensure_stream(self, name: str, subjects: list[str]) -> None:
        pass

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        raise RuntimeError("Not used in tests")

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return True


class MockJetStream:
    """Records stream creation and durable subscriptions."""

    def __init__(self) -> None:
        self.streams_created: list[Any] = []
        self.subscriptions: list[dict[str, Any]] = []

    async def add_stream(self, config: Any = None, **kwargs: Any) -> Any:
        self.streams_created.append(config)
        return MagicMock()

    async def subscribe(
        self,
        subject: str,
        cb: Any = None,
        durable: str | None = None,
        manual_ack: bool = False,
        **kwargs: Any,
    ) -> Any:
        sub = {
            "subject": subject,
            "cb": cb,
            "durable": durable,
            "manual_ack": manual_ack,
        }
        self.subscriptions.append(sub)
        return MagicMock()


# ------------------------------------------------------------------
# Helpers.
# ------------------------------------------------------------------


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


def _make_skill_request_data(**overrides: Any) -> dict[str, Any]:
    base = {
        "request_id": "skreq_001",
        "trace_id": "trace_001",
        "robot_id": "rbt_01",
        "skill_id": "move_to",
        "params": {"position": {"x": 0.0, "y": 0.0}},
        "requested_at": "2026-01-01T00:00:00Z",
    }
    base.update(overrides)
    return base


def _published_on(nats: MockNats, subject_prefix: str) -> list[tuple[str, bytes]]:
    return [(s, d) for s, d in nats.published if s.startswith(subject_prefix)]


# ------------------------------------------------------------------
# Config tests.
# ------------------------------------------------------------------


class TestJetStreamConfig:
    def test_stream_subjects_include_safety_and_skill(self):
        config = _make_config()
        subjects = config.jetstream_stream_subjects
        assert "edge.test_edge.safety.>" in subjects
        assert "edge.test_edge.skill.execute" in subjects

    def test_durable_name_includes_gateway_id(self):
        config = _make_config()
        assert config.jetstream_durable_name == "safety-gw-test_edge-skill"

    def test_default_stream_name(self):
        config = _make_config()
        assert config.jetstream_stream_name == "SAFETY_STREAM"


# ------------------------------------------------------------------
# NatsBridge JetStream tests.
# ------------------------------------------------------------------


class TestNatsBridgeJetStream:
    async def test_ensure_stream_creates_stream_with_correct_name_and_subjects(
        self, monkeypatch: pytest.MonkeyPatch
    ):
        mock_js = MockJetStream()
        mock_nc = MagicMock()
        mock_nc.jetstream = MagicMock(return_value=mock_nc)
        mock_nc.is_connected = True
        mock_js_obj = MockJetStream()
        mock_nc.jetstream = MagicMock(return_value=mock_js_obj)

        async def _mock_connect(*args: Any, **kwargs: Any) -> Any:
            return mock_nc

        monkeypatch.setattr("nats.connect", _mock_connect)

        config = _make_config()
        bridge = NatsBridge(config)
        await bridge.connect()

        await bridge.ensure_stream(
            config.jetstream_stream_name,
            config.jetstream_stream_subjects,
        )

        assert len(mock_js_obj.streams_created) == 1
        created = mock_js_obj.streams_created[0]
        assert created.name == "SAFETY_STREAM"
        assert "edge.test_edge.safety.>" in created.subjects
        assert "edge.test_edge.skill.execute" in created.subjects

    async def test_subscribe_jetstream_uses_durable_consumer(
        self, monkeypatch: pytest.MonkeyPatch
    ):
        mock_nc = MagicMock()
        mock_js_obj = MockJetStream()
        mock_nc.jetstream = MagicMock(return_value=mock_js_obj)
        mock_nc.is_connected = True

        async def _mock_connect(*args: Any, **kwargs: Any) -> Any:
            return mock_nc

        monkeypatch.setattr("nats.connect", _mock_connect)

        config = _make_config()
        bridge = NatsBridge(config)
        await bridge.connect()

        async def _handler(msg: Any) -> None:
            pass

        await bridge.subscribe_jetstream(
            "edge.test_edge.skill.execute",
            _handler,
            durable="safety-gw-test_edge-skill",
            manual_ack=True,
        )

        assert len(mock_js_obj.subscriptions) == 1
        sub = mock_js_obj.subscriptions[0]
        assert sub["subject"] == "edge.test_edge.skill.execute"
        assert sub["durable"] == "safety-gw-test_edge-skill"
        assert sub["manual_ack"] is True

    async def test_jetstream_initialised_on_connect(
        self, monkeypatch: pytest.MonkeyPatch
    ):
        mock_nc = MagicMock()
        mock_js_obj = MockJetStream()
        mock_nc.jetstream = MagicMock(return_value=mock_js_obj)
        mock_nc.is_connected = True

        async def _mock_connect(*args: Any, **kwargs: Any) -> Any:
            return mock_nc

        monkeypatch.setattr("nats.connect", _mock_connect)

        config = _make_config()
        bridge = NatsBridge(config)
        await bridge.connect()

        assert bridge._js is not None


# ------------------------------------------------------------------
# Handler ack tests.
# ------------------------------------------------------------------


class TestHandlerAck:
    async def test_ack_after_successful_forward(self):
        """A JetStream message should be acked after successful safety evaluation and forward."""
        handler, nats, sm = _make_handler()
        msg = JetStreamMockMsg(
            data=json.dumps(_make_skill_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await handler.handle_skill_execute(msg)

        # Message should be acked.
        assert msg.acked is True
        assert msg.nacked is False

        # Skill should be forwarded.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 1

    async def test_ack_after_safety_denial(self):
        """A JetStream message should be acked even when safety denies the request."""
        handler, nats, sm = _make_handler()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")

        msg = JetStreamMockMsg(
            data=json.dumps(_make_skill_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await handler.handle_skill_execute(msg)

        assert msg.acked is True

        # Skill should NOT be forwarded.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 0

    async def test_ack_after_malformed_payload(self):
        """A malformed JetStream message should be acked (not redelivered)."""
        handler, nats, sm = _make_handler()
        msg = JetStreamMockMsg(data=b"not-json", reply="")
        await handler.handle_skill_execute(msg)

        assert msg.acked is True

    async def test_ack_after_safety_check_denial(self):
        """A JetStream message denied by action-level checks should be acked."""
        handler, nats, sm = _make_handler()
        msg = JetStreamMockMsg(
            data=json.dumps(
                _make_skill_request_data(params={"linear_speed": 5.0})
            ).encode("utf-8"),
            reply="reply.subject",
        )
        await handler.handle_skill_execute(msg)

        assert msg.acked is True
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 0

    async def test_ack_failure_does_not_crash_handler(self):
        """If ack raises, the handler should not crash."""
        handler, nats, sm = _make_handler()

        class FailingAckMsg(JetStreamMockMsg):
            async def ack(self) -> None:
                raise RuntimeError("ack failed")

        msg = FailingAckMsg(
            data=json.dumps(_make_skill_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await handler.handle_skill_execute(msg)

        # Forward should still have happened.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 1


# ------------------------------------------------------------------
# Integration-style: handler + JetStream mock message end-to-end.
# ------------------------------------------------------------------


class TestJetStreamPersistenceFlow:
    async def test_full_persistence_flow_allowed(self):
        """Full flow: message arrives, safety passes, forwarded, response sent, acked."""
        handler, nats, sm = _make_handler()

        msg = JetStreamMockMsg(
            data=json.dumps(_make_skill_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await handler.handle_skill_execute(msg)

        # 1. Forwarded to executor.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 1

        # 2. Response published on reply subject.
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["allowed"] is True
        assert response["forwarded"] is True

        # 3. Message acked (not redelivered).
        assert msg.acked is True

    async def test_full_persistence_flow_denied(self):
        """Full flow: message arrives, safety denies, response sent, acked."""
        handler, nats, sm = _make_handler()
        await sm.trigger_emergency_stop(reason="Test", trace_id="t1")

        msg = JetStreamMockMsg(
            data=json.dumps(_make_skill_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await handler.handle_skill_execute(msg)

        # 1. NOT forwarded to executor.
        forwards = _published_on(nats, "edge.test_edge.skill.execute.approved")
        assert len(forwards) == 0

        # 2. Response published on reply subject (denied).
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["allowed"] is False
        assert response["state"] == "EMERGENCY_STOPPED"

        # 3. Message acked (not redelivered).
        assert msg.acked is True
