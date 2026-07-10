# Function: JetStream persistence tests for the ROS1 adapter
# Time: 2026-07-09
# Author: AxeXie
"""JetStream persistence tests for the ROS1 protocol adapter.

Verifies that:
  * The config exposes correct JetStream stream subjects and durable name.
  * The adapter creates a JetStream stream and subscribes via a durable consumer.
  * The handler acks JetStream messages after translation completes.
"""

from __future__ import annotations

import json
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from opengeobot_ros1.config import Ros1Config
from opengeobot_ros1.main import Ros1Adapter


# ------------------------------------------------------------------
# Mock objects.
# ------------------------------------------------------------------


class JetStreamMockMsg:
    """Mimics a nats.aio.msg.Msg with JetStream ack support."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply
        self.acked = False

    async def ack(self) -> None:
        self.acked = True

    async def nak(self, delay: float | None = None) -> None:
        pass

    async def term(self) -> None:
        pass


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


class MockNats:
    """Records publishes and provides a mock JetStream context."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []
        self._js = MockJetStream()

    def jetstream(self) -> MockJetStream:
        return self._js

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def drain(self) -> None:
        pass


# ------------------------------------------------------------------
# Helpers.
# ------------------------------------------------------------------


def _make_config(**overrides: Any) -> Ros1Config:
    defaults: dict[str, Any] = {
        "adapter_id": "adp_test",
        "robot_id": "rbt_test",
        "protocol_type": "UNITREE",
        "version": "0.1.0",
        "nats_url": "nats://localhost:4222",
        "nats_max_reconnect": -1,
        "nats_reconnect_wait": 2.0,
        "nats_connect_timeout": 5.0,
        "ros_master_uri": "http://localhost:11311",
        "node_name": "opengeobot_ros1",
        "log_level": "DEBUG",
    }
    defaults.update(overrides)
    return Ros1Config(**defaults)


def _make_adapter(
    config: Ros1Config | None = None,
) -> tuple[Ros1Adapter, MockNats]:
    config = config or _make_config()
    adapter = Ros1Adapter(config)
    nats = MockNats()
    adapter._nc = nats  # type: ignore[assignment]
    return adapter, nats


def _make_request_data(**overrides: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "request_id": "req_001",
        "trace_id": "trace_001",
        "adapter_id": "adp_test",
        "skill_id": "stand_up",
        "params": {"duration": 3.0},
        "requested_at": "2026-01-01T00:00:00Z",
    }
    base.update(overrides)
    return base


def _replies_on(nats: MockNats, subject: str) -> list[bytes]:
    return [d for s, d in nats.published if s == subject]


# ------------------------------------------------------------------
# Config tests.
# ------------------------------------------------------------------


class TestJetStreamConfig:
    def test_stream_subjects_include_translate_subject(self):
        config = _make_config()
        subjects = config.jetstream_stream_subjects
        assert "opengeobot.dev.adapter.translate.adp_test" in subjects

    def test_durable_name_includes_adapter_id(self):
        config = _make_config()
        assert config.jetstream_durable_name == "ros1-adapter-adp_test"

    def test_default_stream_name(self):
        config = _make_config()
        assert config.jetstream_stream_name == "ROS1_ADAPTER_STREAM"


# ------------------------------------------------------------------
# Adapter start/stop JetStream tests.
# ------------------------------------------------------------------


class TestAdapterJetStreamStart:
    async def test_start_creates_jetstream_stream(self, monkeypatch: pytest.MonkeyPatch) -> None:
        mock_nc = MockNats()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        config = _make_config()
        adapter = Ros1Adapter(config)
        await adapter.start()

        assert len(mock_nc._js.streams_created) == 1
        created = mock_nc._js.streams_created[0]
        assert created.name == "ROS1_ADAPTER_STREAM"
        assert "opengeobot.dev.adapter.translate.adp_test" in created.subjects

    async def test_start_subscribes_with_durable_consumer(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        mock_nc = MockNats()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        config = _make_config()
        adapter = Ros1Adapter(config)
        await adapter.start()

        assert len(mock_nc._js.subscriptions) == 1
        sub = mock_nc._js.subscriptions[0]
        assert sub["subject"] == "opengeobot.dev.adapter.translate.adp_test"
        assert sub["durable"] == "ros1-adapter-adp_test"
        assert sub["manual_ack"] is True

    async def test_stop_clears_jetstream_context(self, monkeypatch: pytest.MonkeyPatch) -> None:
        mock_nc = MockNats()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        config = _make_config()
        adapter = Ros1Adapter(config)
        await adapter.start()
        await adapter.stop()

        assert adapter._js is None


# ------------------------------------------------------------------
# Handler ack tests.
# ------------------------------------------------------------------


class TestHandlerAck:
    async def test_ack_after_successful_translation(self):
        """A JetStream message should be acked after a successful translation."""
        adapter, nats = _make_adapter(_make_config(protocol_type="UNITREE"))
        msg = JetStreamMockMsg(
            data=json.dumps(_make_request_data(skill_id="stand_up")).encode("utf-8"),
            reply="reply.subject",
        )
        await adapter._handle_request(msg)

        assert msg.acked is True
        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True

    async def test_ack_after_translation_error(self):
        """A JetStream message should be acked even when translation fails."""
        adapter, nats = _make_adapter(_make_config(protocol_type="UNITREE"))
        msg = JetStreamMockMsg(
            data=json.dumps(_make_request_data(skill_id="nonexistent")).encode("utf-8"),
            reply="reply.subject",
        )
        await adapter._handle_request(msg)

        assert msg.acked is True
        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False

    async def test_ack_after_malformed_payload(self):
        """A malformed JetStream message should be acked (not redelivered)."""
        adapter, nats = _make_adapter()
        msg = JetStreamMockMsg(data=b"not-json", reply="reply.subject")
        await adapter._handle_request(msg)

        assert msg.acked is True
        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False

    async def test_ack_after_handler_crash(self):
        """A JetStream message should be acked even when the handler crashes."""

        class CrashingHandler:
            protocol_type = "CRASH"

            def translate(self, skill_id: str, params: dict[str, Any]) -> dict[str, Any]:
                raise RuntimeError("handler crashed")

        adapter, nats = _make_adapter()
        adapter._handler = CrashingHandler()  # type: ignore[assignment]

        msg = JetStreamMockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await adapter._handle_request(msg)

        assert msg.acked is True
        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Translation crashed" in response["error"]

    async def test_ack_failure_does_not_crash_adapter(self):
        """If ack raises, the adapter should not crash."""
        adapter, nats = _make_adapter(_make_config(protocol_type="UNITREE"))

        class FailingAckMsg(JetStreamMockMsg):
            async def ack(self) -> None:
                raise RuntimeError("ack failed")

        msg = FailingAckMsg(
            data=json.dumps(_make_request_data(skill_id="stand_up")).encode("utf-8"),
            reply="reply.subject",
        )
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1


# ------------------------------------------------------------------
# Integration-style: adapter + JetStream mock message end-to-end.
# ------------------------------------------------------------------


class TestJetStreamPersistenceFlow:
    async def test_full_persistence_flow_success(self):
        """Full flow: request arrives, translated, response sent, acked."""
        adapter, nats = _make_adapter(_make_config(protocol_type="UNITREE"))
        msg = JetStreamMockMsg(
            data=json.dumps(
                _make_request_data(skill_id="stand_up", params={"duration": 3.0})
            ).encode("utf-8"),
            reply="reply.subject",
        )
        await adapter._handle_request(msg)

        # 1. Response published on reply subject.
        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["request_id"] == "req_001"
        assert response["translated_command"]["topic"] == "/standUpCmd"

        # 2. Message acked (not redelivered).
        assert msg.acked is True

    async def test_full_persistence_flow_failure(self):
        """Full flow: request arrives, translation fails, error response sent, acked."""
        adapter, nats = _make_adapter(_make_config(protocol_type="UNITREE"))
        msg = JetStreamMockMsg(
            data=json.dumps(_make_request_data(skill_id="nonexistent")).encode("utf-8"),
            reply="reply.subject",
        )
        await adapter._handle_request(msg)

        # 1. Error response published on reply subject.
        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False

        # 2. Message acked (not redelivered).
        assert msg.acked is True
