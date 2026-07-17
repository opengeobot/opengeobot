# Function: ROSClaw NATS Bridge unit tests
# Time: 2026-07-16
# Author: AxeXie
"""Unit tests for the ROSClaw NATS Bridge.

These tests mock the ROSClaw runtime components and the NATS connection so
they can run without a real NATS server or ROSClaw installation.
"""

from __future__ import annotations

import json
import sys
import tempfile
import types
from typing import Any
from unittest.mock import MagicMock

import pytest

from opengeobot_rosclaw_bridge.bridge import (
    RosclawBridge,
    SkillExecutionRequest,
    SkillExecutionResponse,
)
from opengeobot_rosclaw_bridge.config import BridgeConfig


# ---------------------------------------------------------------------------
# Mock helpers
# ---------------------------------------------------------------------------


class MockMsg:
    """Mimics nats.aio.msg.Msg for testing."""

    def __init__(self, data: bytes, reply: str | None = "reply.subject") -> None:
        self.data = data
        self.reply = reply


class MockNats:
    """Records publishes for verification. Mimics the NatsBridge interface."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []

    async def connect(self) -> None:
        pass

    async def subscribe(self, subject: str, handler: Any = None, queue: str | None = None) -> Any:
        return MagicMock()

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def drain_and_close(self) -> None:
        pass


class MockEventPriority:
    CRITICAL = 0


class MockEvent:
    """Mimics rosclaw.core.event_bus.Event."""

    def __init__(
        self,
        topic: str = "",
        payload: Any = None,
        source: str = "",
        priority: Any = None,
    ) -> None:
        self.topic = topic
        self.payload = payload
        self.source = source
        self.priority = priority


def _make_config(**overrides: Any) -> BridgeConfig:
    defaults: dict[str, Any] = {
        "robot_id": "rbt_test",
        "gateway_id": "edge_test",
        "nats_url": "nats://localhost:4222",
        "nats_max_reconnect": -1,
        "nats_reconnect_wait": 2.0,
        "nats_connect_timeout": 5.0,
        "robot_ros_endpoint": "ws://localhost:9090",
        "rosclaw_profile": "offline",
        "rosclaw_sandbox": True,
        "log_level": "DEBUG",
        "skill_request_timeout": 5.0,
        "ready_file_path": tempfile.mktemp(prefix="rosclaw-bridge-ready-"),
    }
    defaults.update(overrides)
    return BridgeConfig(**defaults)


def _make_bridge(config: BridgeConfig | None = None) -> tuple[RosclawBridge, MockNats]:
    """Create a bridge with a mock NATS client injected."""
    config = config or _make_config()
    bridge = RosclawBridge(config)
    nats = MockNats()
    bridge._nats = nats  # type: ignore[assignment]
    return bridge, nats


def _make_bridge_with_rosclaw(
    config: BridgeConfig | None = None,
) -> tuple[RosclawBridge, MockNats, MagicMock, MagicMock, MagicMock]:
    """Create a bridge with mocked ROSClaw components enabled."""
    bridge, nats = _make_bridge(config)

    mock_registry = MagicMock()
    mock_executor = MagicMock()
    mock_event_bus = MagicMock()

    bridge._rosclaw_available = True
    bridge._event_bus = mock_event_bus
    bridge._skill_registry = mock_registry
    bridge._skill_executor = mock_executor
    bridge._Event_cls = MockEvent
    bridge._EventPriority_cls = MockEventPriority

    return bridge, nats, mock_registry, mock_executor, mock_event_bus


def _make_request_data(**overrides: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "request_id": "skreq_001",
        "trace_id": "trace_001",
        "robot_id": "rbt_test",
        "skill_id": "move_forward",
        "params": {"distance": 2.0, "speed": 1.0},
        "requested_at": "2026-01-01T00:00:00Z",
    }
    base.update(overrides)
    return base


def _make_msg(data: dict[str, Any], reply: str | None = "reply.subject") -> MockMsg:
    return MockMsg(data=json.dumps(data).encode("utf-8"), reply=reply)


def _replies_on(nats: MockNats, subject: str) -> list[bytes]:
    return [d for s, d in nats.published if s == subject]


# ---------------------------------------------------------------------------
# Tests: valid skill request via ROSClaw
# ---------------------------------------------------------------------------


class TestValidSkillRequest:
    async def test_move_forward_success(self) -> None:
        bridge, nats, registry, executor, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = MagicMock(name="move_forward")
        executor.execute.return_value = {
            "status": "success",
            "skill": "move_forward",
            "handler_result": {"distance": 2.0},
            "duration_sec": 0.05,
        }

        msg = _make_msg(_make_request_data())
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["skill_id"] == "move_forward"
        assert response["request_id"] == "skreq_001"
        assert response["trace_id"] == "trace_001"
        assert response["started_at"] != ""
        assert response["completed_at"] != ""
        assert response["output"]["status"] == "success"
        assert response["error"] is None

    async def test_dispatched_status_is_success(self) -> None:
        bridge, nats, registry, executor, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = MagicMock(name="capture_image")
        executor.execute.return_value = {
            "status": "dispatched",
            "skill": "capture_image",
        }

        msg = _make_msg(_make_request_data(skill_id="capture_image", params={}))
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["output"]["status"] == "dispatched"

    async def test_executor_called_with_correct_args(self) -> None:
        bridge, _, registry, executor, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = MagicMock(name="move_forward")
        executor.execute.return_value = {"status": "success", "skill": "move_forward"}

        msg = _make_msg(_make_request_data())
        await bridge._handle_request(msg)

        executor.execute.assert_called_once_with("move_forward", {"distance": 2.0, "speed": 1.0})


# ---------------------------------------------------------------------------
# Tests: unknown skill
# ---------------------------------------------------------------------------


class TestUnknownSkill:
    async def test_unknown_skill_returns_error(self) -> None:
        bridge, nats, registry, _, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = None

        msg = _make_msg(_make_request_data(skill_id="nonexistent"))
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Unknown skill_id" in response["error"]
        assert response["skill_id"] == "nonexistent"

    async def test_unknown_skill_does_not_call_executor(self) -> None:
        bridge, _, registry, executor, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = None

        msg = _make_msg(_make_request_data(skill_id="nonexistent"))
        await bridge._handle_request(msg)

        executor.execute.assert_not_called()


# ---------------------------------------------------------------------------
# Tests: emergency_stop
# ---------------------------------------------------------------------------


class TestEmergencyStop:
    async def test_emergency_stop_succeeds_and_latches(self) -> None:
        bridge, nats, _, _, event_bus = _make_bridge_with_rosclaw()

        msg = _make_msg(_make_request_data(skill_id="emergency_stop", params={}))
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["output"]["stopped"] is True
        assert response["output"]["mode"] == "rosclaw"
        assert bridge.safety_latched is True

    async def test_emergency_stop_publishes_event_on_bus(self) -> None:
        bridge, _, _, _, event_bus = _make_bridge_with_rosclaw()

        msg = _make_msg(_make_request_data(skill_id="emergency_stop", params={"reason": "test"}))
        await bridge._handle_request(msg)

        event_bus.publish.assert_called_once()
        published_event = event_bus.publish.call_args.args[0]
        assert published_event.topic == "robot.emergency_stop"
        assert published_event.payload["reason"] == "test"
        assert published_event.source == "rosclaw_bridge"

    async def test_emergency_stop_fallback_mode(self) -> None:
        """Emergency stop must latch even without ROSClaw."""
        bridge, nats = _make_bridge()

        msg = _make_msg(_make_request_data(skill_id="emergency_stop", params={}))
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["output"]["mode"] == "fallback"
        assert bridge.safety_latched is True

    async def test_safety_latch_blocks_other_skills(self) -> None:
        bridge, nats, registry, executor, _ = _make_bridge_with_rosclaw()
        bridge._safety_latched = True
        registry.get.return_value = MagicMock()

        msg = _make_msg(_make_request_data(skill_id="move_forward"))
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Safety latch" in response["error"]
        executor.execute.assert_not_called()

    async def test_emergency_stop_accepted_when_latched(self) -> None:
        bridge, nats, _, _, event_bus = _make_bridge_with_rosclaw()
        bridge._safety_latched = True

        msg = _make_msg(_make_request_data(skill_id="emergency_stop"))
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True


# ---------------------------------------------------------------------------
# Tests: ROSClaw unavailable (fallback)
# ---------------------------------------------------------------------------


class TestFallbackMode:
    async def test_fallback_returns_failure_for_motion_skill(self) -> None:
        bridge, nats = _make_bridge()
        assert bridge.rosclaw_available is False

        msg = _make_msg(_make_request_data(skill_id="move_forward"))
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "not available" in response["error"]
        assert response["output"]["mode"] == "fallback"

    async def test_fallback_emergency_stop_still_works(self) -> None:
        bridge, nats = _make_bridge()

        msg = _make_msg(_make_request_data(skill_id="emergency_stop"))
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["output"]["mode"] == "fallback"
        assert bridge.safety_latched is True


# ---------------------------------------------------------------------------
# Tests: error handling
# ---------------------------------------------------------------------------


class TestSkillExecutionError:
    async def test_executor_returns_error_status(self) -> None:
        bridge, nats, registry, executor, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = MagicMock(name="move_forward")
        executor.execute.return_value = {
            "status": "error",
            "error": "Motor driver timeout",
        }

        msg = _make_msg(_make_request_data())
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Motor driver timeout" in response["error"]

    async def test_executor_returns_blocked_status(self) -> None:
        bridge, nats, registry, executor, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = MagicMock(name="move_forward")
        executor.execute.return_value = {
            "status": "blocked",
            "message": "Body compatibility check failed",
        }

        msg = _make_msg(_make_request_data())
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Body compatibility" in response["error"]

    async def test_executor_raises_exception(self) -> None:
        bridge, nats, registry, executor, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = MagicMock(name="move_forward")
        executor.execute.side_effect = RuntimeError("connection lost")

        msg = _make_msg(_make_request_data())
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "SkillExecutor error" in response["error"]


# ---------------------------------------------------------------------------
# Tests: malformed requests
# ---------------------------------------------------------------------------


class TestMalformedRequest:
    async def test_invalid_json_returns_error(self) -> None:
        bridge, nats = _make_bridge()
        msg = MockMsg(data=b"not-json", reply="reply.subject")
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert response["error"] != ""

    async def test_missing_required_fields_returns_error(self) -> None:
        bridge, nats = _make_bridge()
        msg = MockMsg(
            data=json.dumps({"request_id": "x"}).encode("utf-8"),
            reply="reply.subject",
        )
        await bridge._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False


# ---------------------------------------------------------------------------
# Tests: no reply subject
# ---------------------------------------------------------------------------


class TestNoReplySubject:
    async def test_none_reply_does_not_publish(self) -> None:
        bridge, nats = _make_bridge()
        msg = MockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply=None,
        )
        await bridge._handle_request(msg)
        assert len(nats.published) == 0


# ---------------------------------------------------------------------------
# Tests: active execution count
# ---------------------------------------------------------------------------


class TestActiveExecutionCount:
    async def test_count_decremented_after_success(self) -> None:
        bridge, _, registry, executor, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = MagicMock()
        executor.execute.return_value = {"status": "success"}

        msg = _make_msg(_make_request_data())
        await bridge._handle_request(msg)
        assert bridge._active_executions == 0

    async def test_count_decremented_after_error(self) -> None:
        bridge, _, registry, executor, _ = _make_bridge_with_rosclaw()
        registry.get.return_value = MagicMock()
        executor.execute.return_value = {"status": "error", "error": "fail"}

        msg = _make_msg(_make_request_data())
        await bridge._handle_request(msg)
        assert bridge._active_executions == 0


# ---------------------------------------------------------------------------
# Tests: config
# ---------------------------------------------------------------------------


class TestConfig:
    def test_skill_execute_subject(self) -> None:
        config = _make_config(robot_id="rbt_custom")
        assert config.skill_execute_subject == "opengeobot.dev.edge.skill.execute.rbt_custom"

    def test_defaults(self) -> None:
        config = _make_config()
        assert config.gateway_id == "edge_test"
        assert config.rosclaw_profile == "offline"
        assert config.rosclaw_sandbox is True
        assert config.skill_request_timeout == 5.0
        assert config.ready_file_path != ""


class TestReadinessMarker:
    async def test_start_writes_ready_file_after_subscription(self, tmp_path: pytest.TempPathFactory) -> None:
        ready_file = tmp_path / "bridge.ready"
        config = _make_config(ready_file_path=str(ready_file))
        bridge, nats = _make_bridge(config)

        await bridge.start(nats)

        assert ready_file.exists() is True
        assert ready_file.read_text(encoding="utf-8").strip() == config.skill_execute_subject

    async def test_disconnect_and_stop_remove_ready_file(
        self, tmp_path: pytest.TempPathFactory
    ) -> None:
        ready_file = tmp_path / "bridge.ready"
        config = _make_config(ready_file_path=str(ready_file))
        bridge, nats = _make_bridge(config)

        await bridge.start(nats)
        assert ready_file.exists() is True

        assert nats.on_disconnect is not None
        await nats.on_disconnect(None)
        assert ready_file.exists() is False

        assert nats.on_reconnect is not None
        await nats.on_reconnect()
        assert ready_file.exists() is True

        await bridge.stop()
        assert ready_file.exists() is False


class FakeSkillEntry:
    def __init__(self, **kwargs: Any) -> None:
        for key, value in kwargs.items():
            setattr(self, key, value)


class TestRuntimeSkillRegistration:
    def test_register_bridge_skills_registers_move_forward(self) -> None:
        bridge, _, registry, _, _ = _make_bridge_with_rosclaw()
        registry.list_skills.return_value = ["move_forward"]

        bridge._register_bridge_skills(FakeSkillEntry)

        registry.register.assert_called_once()
        entry = registry.register.call_args.args[0]
        assert entry.name == "move_forward"
        assert entry.skill_type == "programmed"
        assert entry.metadata["runtime_handler"] == "navigate_to"
        assert callable(entry.handler)

    def test_move_forward_handler_uses_navigate_to_runtime_handler(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        bridge, _ = _make_bridge()

        class FakePlugin:
            def get_handler(self, skill_name: str) -> Any:
                assert skill_name == "navigate_to"
                return lambda params: {
                    "status": "success",
                    "skill": "navigate_to",
                    "target": params["target"],
                }

        plugin_module = types.ModuleType("rosclaw.runtime.plugin")
        plugin_module.get_runtime_plugin = lambda: FakePlugin()
        runtime_module = types.ModuleType("rosclaw.runtime")
        rosclaw_module = types.ModuleType("rosclaw")

        monkeypatch.setitem(sys.modules, "rosclaw", rosclaw_module)
        monkeypatch.setitem(sys.modules, "rosclaw.runtime", runtime_module)
        monkeypatch.setitem(
            sys.modules, "rosclaw.runtime.plugin", plugin_module
        )

        result = bridge._handle_move_forward_skill(
            {"distance": 2.0, "speed": 0.4}
        )

        assert result["status"] == "success"
        assert result["skill"] == "navigate_to"
        assert result["translated_params"]["target"] == "forward:2.00m"
        assert result["translated_params"]["speed"] == 0.4


# ---------------------------------------------------------------------------
# Tests: model serialization
# ---------------------------------------------------------------------------


class TestModels:
    def test_request_parses_extra_fields(self) -> None:
        data = {
            "request_id": "r1",
            "trace_id": "t1",
            "robot_id": "rbt_1",
            "skill_id": "stop",
            "params": {},
            "requested_at": "",
            "extra_field": "ignored",
        }
        request = SkillExecutionRequest.model_validate(data)
        assert request.request_id == "r1"
        assert request.skill_id == "stop"

    def test_response_serialization(self) -> None:
        response = SkillExecutionResponse(
            request_id="r1",
            trace_id="t1",
            skill_id="stop",
            success=True,
            output={"mode": "rosclaw"},
        )
        data = json.loads(response.model_dump_json())
        assert data["success"] is True
        assert data["output"]["mode"] == "rosclaw"
        assert data["error"] is None
        assert data["started_at"] == ""
        assert data["completed_at"] == ""
