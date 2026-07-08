# Function: Skill executor unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for the SkillExecutor."""

from __future__ import annotations

import json
from typing import Any

import pytest

from opengeobot_skill_executor.adapter_client import (
    AdapterClient,
    AdapterTimeoutError,
    SkillExecutionRequest,
    SkillExecutionResponse,
)
from opengeobot_skill_executor.config import ExecutorConfig
from opengeobot_skill_executor.executor import SkillExecutor


class MockMsg:
    """Mimics nats.aio.msg.Msg for testing."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply


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
        import asyncio

        if self._request_should_timeout:
            raise asyncio.TimeoutError()
        if self._request_should_error:
            raise RuntimeError("NATS connection error")
        if self._reply_data is None:
            raise RuntimeError("No reply data configured")
        return _MockReply(self._reply_data)

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return True


class _MockReply:
    """Mimics a NATS reply message."""

    def __init__(self, data: bytes) -> None:
        self.data = data


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


def _make_msg(data: dict[str, Any], reply: str = "reply.subject") -> MockMsg:
    return MockMsg(
        data=json.dumps(data).encode("utf-8"),
        reply=reply,
    )


def _published_on(nats: MockNats, subject: str) -> list[tuple[str, bytes]]:
    return [(s, d) for s, d in nats.published if s == subject]


class TestAdapterResolution:
    def test_default_adapter_type(self):
        executor, _, _ = _make_executor()
        assert executor.resolve_adapter_type("rbt_01") == "simulation"

    def test_registered_robot_adapter(self):
        executor, _, _ = _make_executor()
        executor.register_robot_adapter("rbt_02", "ros2")
        assert executor.resolve_adapter_type("rbt_02") == "ros2"

    def test_unregistered_robot_uses_default(self):
        executor, _, _ = _make_executor()
        executor.register_robot_adapter("rbt_02", "ros2")
        assert executor.resolve_adapter_type("rbt_03") == "simulation"


class TestHandleApprovedRequest:
    async def test_success_forward_and_reply(self):
        """Approved request should be forwarded and the result returned."""
        executor, nats, _ = _make_executor()

        adapter_response = SkillExecutionResponse(
            request_id="skreq_001",
            trace_id="trace_001",
            skill_id="move_forward",
            success=True,
            output={"distance": 1.0, "duration": 2.0},
            started_at="2026-01-01T00:00:00Z",
            completed_at="2026-01-01T00:00:02Z",
        )
        nats.set_reply(adapter_response.model_dump_json().encode("utf-8"))

        msg = _make_msg(_make_request_data())
        await executor.handle_approved_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is True
        assert response["request_id"] == "skreq_001"
        assert response["trace_id"] == "trace_001"

    async def test_adapter_timeout_returns_failure(self):
        """Adapter timeout should return a failure response, not crash."""
        executor, nats, _ = _make_executor()
        nats.set_timeout()

        msg = _make_msg(_make_request_data())
        await executor.handle_approved_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False
        assert "did not respond" in response["error"]

    async def test_adapter_error_returns_failure(self):
        """Adapter NATS error should return a failure response."""
        executor, nats, _ = _make_executor()
        nats.set_error()

        msg = _make_msg(_make_request_data())
        await executor.handle_approved_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False
        assert "Adapter call failed" in response["error"]

    async def test_malformed_payload_returns_error(self):
        """Malformed JSON should be rejected with an error response."""
        executor, nats, _ = _make_executor()

        msg = MockMsg(data=b"not-json", reply="reply.subject")
        await executor.handle_approved_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False

    async def test_no_reply_subject_does_not_crash(self):
        """When no reply subject is available, executor should not crash."""
        executor, nats, _ = _make_executor()

        adapter_response = SkillExecutionResponse(
            request_id="skreq_001",
            trace_id="trace_001",
            skill_id="move_forward",
            success=True,
            started_at="2026-01-01T00:00:00Z",
            completed_at="2026-01-01T00:00:02Z",
        )
        nats.set_reply(adapter_response.model_dump_json().encode("utf-8"))

        msg = MockMsg(data=json.dumps(_make_request_data()).encode("utf-8"), reply="")
        await executor.handle_approved_request(msg)

        # No reply published since reply subject is empty.
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 0

    async def test_malformed_adapter_response_returns_failure(self):
        """Malformed adapter response should return a failure response."""
        executor, nats, _ = _make_executor()
        nats.set_reply(b"not-json")

        msg = _make_msg(_make_request_data())
        await executor.handle_approved_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False
        assert "Malformed adapter response" in response["error"]

    async def test_ros2_robot_routed_to_ros2_adapter(self):
        """A robot registered as ros2 should be forwarded to the ros2 subject."""
        executor, nats, adapter_client = _make_executor()
        executor.register_robot_adapter("rbt_ros2", "ros2")

        adapter_response = SkillExecutionResponse(
            request_id="skreq_002",
            trace_id="trace_002",
            skill_id="move_forward",
            success=True,
            started_at="2026-01-01T00:00:00Z",
            completed_at="2026-01-01T00:00:02Z",
        )
        nats.set_reply(adapter_response.model_dump_json().encode("utf-8"))

        msg = _make_msg(_make_request_data(
            request_id="skreq_002",
            robot_id="rbt_ros2",
        ))
        await executor.handle_approved_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is True
