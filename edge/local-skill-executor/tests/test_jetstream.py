# Function: JetStream persistence tests for the local skill executor
# Time: 2026-07-09
# Author: AxeXie
"""JetStream persistence tests for the local skill executor.

Verifies that:
  * The NatsBridge creates a JetStream stream with the correct name and subjects.
  * The NatsBridge subscribes via a durable consumer with manual ack.
  * The executor acks JetStream messages after execution completes.
"""

from __future__ import annotations

import asyncio
import json
from typing import Any
from unittest.mock import MagicMock

import pytest

from opengeobot_skill_executor.adapter_client import (
    AdapterClient,
    SkillExecutionRequest,
    SkillExecutionResponse,
)
from opengeobot_skill_executor.config import ExecutorConfig
from opengeobot_skill_executor.executor import SkillExecutor
from opengeobot_skill_executor.nats_client import NatsBridge


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


class MockNats:
    """Records publishes and simulates request-reply for testing."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []
        self._reply_data: bytes | None = None
        self._request_should_timeout = False
        self._request_should_error = False

    def set_reply(self, data: bytes) -> None:
        self._reply_data = data

    def set_timeout(self) -> None:
        self._request_should_timeout = True

    def set_error(self) -> None:
        self._request_should_error = True

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        if self._request_should_timeout:
            raise asyncio.TimeoutError()
        if self._request_should_error:
            raise RuntimeError("NATS connection error")
        if self._reply_data is None:
            raise RuntimeError("No reply data configured")
        return _MockReply(self._reply_data)

    async def subscribe_jetstream(
        self, subject: str, handler: Any, durable: str, manual_ack: bool = True
    ) -> Any:
        return None

    async def ensure_stream(self, name: str, subjects: list[str]) -> None:
        pass

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return True


class _MockReply:
    """Mimics a NATS reply message."""

    def __init__(self, data: bytes) -> None:
        self.data = data


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


def _make_config() -> ExecutorConfig:
    return ExecutorConfig(
        gateway_id="test_edge",
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        adapter_timeout=10.0,
        default_adapter_type="simulation",
        log_level="DEBUG",
    )


def _make_executor(
    nats: MockNats | None = None,
) -> tuple[SkillExecutor, MockNats, AdapterClient]:
    config = _make_config()
    nats = nats or MockNats()
    adapter_client = AdapterClient(config, nats)  # type: ignore[arg-type]
    executor = SkillExecutor(config, nats, adapter_client)  # type: ignore[arg-type]
    return executor, nats, adapter_client


def _make_request_data(**overrides: Any) -> dict[str, Any]:
    base = {
        "request_id": "skreq_001",
        "trace_id": "trace_001",
        "robot_id": "rbt_01",
        "skill_id": "move_forward",
        "params": {"distance": 1.0, "speed": 0.5},
        "requested_at": "2026-01-01T00:00:00Z",
    }
    base.update(overrides)
    return base


def _make_response(**overrides: Any) -> SkillExecutionResponse:
    base = {
        "request_id": "skreq_001",
        "trace_id": "trace_001",
        "skill_id": "move_forward",
        "success": True,
        "started_at": "2026-01-01T00:00:00Z",
        "completed_at": "2026-01-01T00:00:02Z",
    }
    base.update(overrides)
    return SkillExecutionResponse.model_validate(base)


def _published_on(nats: MockNats, subject: str) -> list[tuple[str, bytes]]:
    return [(s, d) for s, d in nats.published if s == subject]


# ------------------------------------------------------------------
# Config tests.
# ------------------------------------------------------------------


class TestJetStreamConfig:
    def test_stream_subjects_include_approved_subject(self):
        config = _make_config()
        subjects = config.jetstream_stream_subjects
        assert "edge.test_edge.skill.execute.approved" in subjects

    def test_durable_name_includes_gateway_id(self):
        config = _make_config()
        assert config.jetstream_durable_name == "skill-executor-test_edge"

    def test_default_stream_name(self):
        config = _make_config()
        assert config.jetstream_stream_name == "SKILL_EXECUTOR_STREAM"


# ------------------------------------------------------------------
# NatsBridge JetStream tests.
# ------------------------------------------------------------------


class TestNatsBridgeJetStream:
    async def test_ensure_stream_creates_stream_with_correct_name_and_subjects(
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

        await bridge.ensure_stream(
            config.jetstream_stream_name,
            config.jetstream_stream_subjects,
        )

        assert len(mock_js_obj.streams_created) == 1
        created = mock_js_obj.streams_created[0]
        assert created.name == "SKILL_EXECUTOR_STREAM"
        assert "edge.test_edge.skill.execute.approved" in created.subjects

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
            "edge.test_edge.skill.execute.approved",
            _handler,
            durable="skill-executor-test_edge",
            manual_ack=True,
        )

        assert len(mock_js_obj.subscriptions) == 1
        sub = mock_js_obj.subscriptions[0]
        assert sub["subject"] == "edge.test_edge.skill.execute.approved"
        assert sub["durable"] == "skill-executor-test_edge"
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
# Executor ack tests.
# ------------------------------------------------------------------


class TestExecutorAck:
    async def test_ack_after_successful_execution(self):
        """A JetStream message should be acked after successful execution."""
        executor, nats, _ = _make_executor()
        nats.set_reply(_make_response().model_dump_json().encode("utf-8"))

        msg = JetStreamMockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await executor.handle_approved_request(msg)

        assert msg.acked is True
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is True

    async def test_ack_after_adapter_timeout(self):
        """A JetStream message should be acked even when the adapter times out."""
        executor, nats, _ = _make_executor()
        nats.set_timeout()

        msg = JetStreamMockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await executor.handle_approved_request(msg)

        assert msg.acked is True
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False
        assert "did not respond" in response["error"]

    async def test_ack_after_adapter_error(self):
        """A JetStream message should be acked even when the adapter errors."""
        executor, nats, _ = _make_executor()
        nats.set_error()

        msg = JetStreamMockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await executor.handle_approved_request(msg)

        assert msg.acked is True
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False
        assert "Adapter call failed" in response["error"]

    async def test_ack_after_malformed_payload(self):
        """A malformed JetStream message should be acked (not redelivered)."""
        executor, nats, _ = _make_executor()

        msg = JetStreamMockMsg(data=b"not-json", reply="reply.subject")
        await executor.handle_approved_request(msg)

        assert msg.acked is True
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False

    async def test_ack_failure_does_not_crash_executor(self):
        """If ack raises, the executor should not crash."""
        executor, nats, _ = _make_executor()
        nats.set_reply(_make_response().model_dump_json().encode("utf-8"))

        class FailingAckMsg(JetStreamMockMsg):
            async def ack(self) -> None:
                raise RuntimeError("ack failed")

        msg = FailingAckMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await executor.handle_approved_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1


# ------------------------------------------------------------------
# Integration-style: executor + JetStream mock message end-to-end.
# ------------------------------------------------------------------


class TestJetStreamPersistenceFlow:
    async def test_full_persistence_flow_success(self):
        """Full flow: request arrives, executed, response sent, acked."""
        executor, nats, _ = _make_executor()
        nats.set_reply(_make_response().model_dump_json().encode("utf-8"))

        msg = JetStreamMockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await executor.handle_approved_request(msg)

        # 1. Response published on reply subject.
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is True
        assert response["request_id"] == "skreq_001"

        # 2. Message acked (not redelivered).
        assert msg.acked is True

    async def test_full_persistence_flow_failure(self):
        """Full flow: request arrives, adapter fails, failure response sent, acked."""
        executor, nats, _ = _make_executor()
        nats.set_timeout()

        msg = JetStreamMockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply="reply.subject",
        )
        await executor.handle_approved_request(msg)

        # 1. Failure response published on reply subject.
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False

        # 2. Message acked (not redelivered).
        assert msg.acked is True
