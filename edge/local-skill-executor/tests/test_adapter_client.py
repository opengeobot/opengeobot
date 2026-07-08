# Function: Adapter client unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for the AdapterClient."""

from __future__ import annotations

import asyncio
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


class _MockReply:
    """Mimics a NATS reply message."""

    def __init__(self, data: bytes) -> None:
        self.data = data


class MockNats:
    """Records request subjects and simulates replies for testing."""

    def __init__(self) -> None:
        self.request_calls: list[tuple[str, bytes, float]] = []
        self._reply_data: bytes | None = None
        self._request_should_timeout = False

    def set_reply(self, data: bytes) -> None:
        self._reply_data = data

    def set_timeout(self) -> None:
        self._request_should_timeout = True

    async def publish(self, subject: str, data: bytes) -> None:
        pass

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        self.request_calls.append((subject, data, timeout))
        if self._request_should_timeout:
            raise asyncio.TimeoutError()
        if self._reply_data is None:
            raise RuntimeError("No reply data configured")
        return _MockReply(self._reply_data)


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


def _make_request(**overrides: Any) -> SkillExecutionRequest:
    base = {
        "request_id": "skreq_001",
        "trace_id": "trace_001",
        "robot_id": "rbt_01",
        "skill_id": "move_forward",
        "params": {"distance": 1.0},
        "requested_at": "2026-01-01T00:00:00Z",
    }
    base.update(overrides)
    return SkillExecutionRequest.model_validate(base)


class TestCallAdapter:
    async def test_simulation_adapter_subject(self):
        """Simulation adapter should resolve to the sim subject."""
        config = _make_config()
        nats = MockNats()
        client = AdapterClient(config, nats)  # type: ignore[arg-type]

        response = SkillExecutionResponse(
            request_id="skreq_001",
            trace_id="trace_001",
            skill_id="move_forward",
            success=True,
            started_at="2026-01-01T00:00:00Z",
            completed_at="2026-01-01T00:00:02Z",
        )
        nats.set_reply(response.model_dump_json().encode("utf-8"))

        await client.call_adapter("simulation", _make_request())

        assert len(nats.request_calls) == 1
        subject, _, _ = nats.request_calls[0]
        assert subject == "opengeobot.dev.edge.skill.execute.rbt_01"

    async def test_ros2_adapter_subject(self):
        """ROS2 adapter should resolve to the ros2 subject."""
        config = _make_config()
        nats = MockNats()
        client = AdapterClient(config, nats)  # type: ignore[arg-type]

        response = SkillExecutionResponse(
            request_id="skreq_001",
            trace_id="trace_001",
            skill_id="move_forward",
            success=True,
            started_at="2026-01-01T00:00:00Z",
            completed_at="2026-01-01T00:00:02Z",
        )
        nats.set_reply(response.model_dump_json().encode("utf-8"))

        await client.call_adapter("ros2", _make_request())

        assert len(nats.request_calls) == 1
        subject, _, _ = nats.request_calls[0]
        assert subject == "opengeobot.dev.edge.ros2.skill.execute.rbt_01"

    async def test_ros1_adapter_subject(self):
        """ROS1 adapter should resolve to the ros1 subject."""
        config = _make_config()
        nats = MockNats()
        client = AdapterClient(config, nats)  # type: ignore[arg-type]

        response = SkillExecutionResponse(
            request_id="skreq_001",
            trace_id="trace_001",
            skill_id="move_forward",
            success=True,
            started_at="2026-01-01T00:00:00Z",
            completed_at="2026-01-01T00:00:02Z",
        )
        nats.set_reply(response.model_dump_json().encode("utf-8"))

        await client.call_adapter("ros1", _make_request())

        assert len(nats.request_calls) == 1
        subject, _, _ = nats.request_calls[0]
        assert subject == "opengeobot.dev.edge.ros1.skill.execute.rbt_01"

    async def test_success_returns_response(self):
        """A successful adapter reply should be parsed and returned."""
        config = _make_config()
        nats = MockNats()
        client = AdapterClient(config, nats)  # type: ignore[arg-type]

        expected = SkillExecutionResponse(
            request_id="skreq_001",
            trace_id="trace_001",
            skill_id="move_forward",
            success=True,
            output={"distance": 1.0, "duration": 2.0},
            started_at="2026-01-01T00:00:00Z",
            completed_at="2026-01-01T00:00:02Z",
        )
        nats.set_reply(expected.model_dump_json().encode("utf-8"))

        result = await client.call_adapter("simulation", _make_request())
        assert result.success is True
        assert result.request_id == "skreq_001"
        assert result.output["distance"] == 1.0

    async def test_timeout_raises_adapter_timeout_error(self):
        """A timeout should raise AdapterTimeoutError."""
        config = _make_config()
        nats = MockNats()
        client = AdapterClient(config, nats)  # type: ignore[arg-type]
        nats.set_timeout()

        with pytest.raises(AdapterTimeoutError, match="did not respond"):
            await client.call_adapter("simulation", _make_request())

    async def test_malformed_response_returns_failure(self):
        """A malformed adapter response should return a failure response."""
        config = _make_config()
        nats = MockNats()
        client = AdapterClient(config, nats)  # type: ignore[arg-type]
        nats.set_reply(b"not-json")

        result = await client.call_adapter("simulation", _make_request())
        assert result.success is False
        assert "Malformed adapter response" in (result.error or "")

    async def test_request_payload_contains_request_data(self):
        """The NATS request payload should contain the serialized request."""
        config = _make_config()
        nats = MockNats()
        client = AdapterClient(config, nats)  # type: ignore[arg-type]

        response = SkillExecutionResponse(
            request_id="skreq_001",
            trace_id="trace_001",
            skill_id="move_forward",
            success=True,
            started_at="2026-01-01T00:00:00Z",
            completed_at="2026-01-01T00:00:02Z",
        )
        nats.set_reply(response.model_dump_json().encode("utf-8"))

        request = _make_request(skill_id="stop")
        await client.call_adapter("simulation", request)

        assert len(nats.request_calls) == 1
        _, payload, _ = nats.request_calls[0]
        sent = json.loads(payload)
        assert sent["request_id"] == "skreq_001"
        assert sent["skill_id"] == "stop"

    async def test_uses_configured_timeout(self):
        """The adapter timeout should be passed to the NATS request."""
        config = _make_config()
        nats = MockNats()
        client = AdapterClient(config, nats)  # type: ignore[arg-type]

        response = SkillExecutionResponse(
            request_id="skreq_001",
            trace_id="trace_001",
            skill_id="move_forward",
            success=True,
            started_at="2026-01-01T00:00:00Z",
            completed_at="2026-01-01T00:00:02Z",
        )
        nats.set_reply(response.model_dump_json().encode("utf-8"))

        await client.call_adapter("simulation", _make_request())

        assert len(nats.request_calls) == 1
        _, _, timeout = nats.request_calls[0]
        assert timeout == config.adapter_timeout
