# Function: Simulation adapter unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the simulation adapter (F-ADAPTER-001)."""

from __future__ import annotations

import json
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from opengeobot_sim.adapter import SimAdapter, SkillExecutionRequest, SkillExecutionResponse
from opengeobot_sim.config import SimConfig


class MockMsg:
    """Mimics nats.aio.msg.Msg for testing."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply


class MockNats:
    """Records publishes for verification."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []
        self._subscribed: list[tuple[str, Any]] = []

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def subscribe(self, subject: str, cb: Any = None, queue: str | None = None) -> Any:
        self._subscribed.append((subject, cb))
        return MagicMock()

    async def drain(self) -> None:
        pass


def _make_config(**overrides: Any) -> SimConfig:
    defaults: dict[str, Any] = {
        "robot_id": "rbt_test",
        "nats_url": "nats://localhost:4222",
        "nats_max_reconnect": -1,
        "nats_reconnect_wait": 2.0,
        "nats_connect_timeout": 5.0,
        "simulation_step": 0.0,
        "log_level": "DEBUG",
    }
    defaults.update(overrides)
    return SimConfig(**defaults)


def _make_adapter(config: SimConfig | None = None) -> tuple[SimAdapter, MockNats]:
    config = config or _make_config()
    adapter = SimAdapter(config)
    nats = MockNats()
    adapter._nc = nats  # type: ignore[assignment]
    return adapter, nats


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


def _make_msg(data: dict[str, Any], reply: str = "reply.subject") -> MockMsg:
    return MockMsg(data=json.dumps(data).encode("utf-8"), reply=reply)


def _replies_on(nats: MockNats, subject: str) -> list[bytes]:
    return [d for s, d in nats.published if s == subject]


class TestRegisteredSkills:
    def test_all_skills_registered(self) -> None:
        adapter, _ = _make_adapter()
        skills = adapter.registered_skills
        assert "stand_up" in skills
        assert "stop" in skills
        assert "move_forward" in skills
        assert "capture_image" in skills
        assert "emergency_stop" in skills

    def test_registered_skills_count(self) -> None:
        adapter, _ = _make_adapter()
        assert len(adapter.registered_skills) == 5


class TestHandleRequestSuccess:
    async def test_move_forward_success(self) -> None:
        adapter, nats = _make_adapter()
        msg = _make_msg(_make_request_data())
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["skill_id"] == "move_forward"
        assert response["request_id"] == "skreq_001"
        assert response["trace_id"] == "trace_001"
        assert response["started_at"] != ""
        assert response["completed_at"] != ""

    async def test_stand_up_success(self) -> None:
        adapter, nats = _make_adapter()
        msg = _make_msg(_make_request_data(skill_id="stand_up", params={"duration": 1.0}))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["output"]["duration"] == 1.0

    async def test_stop_success(self) -> None:
        adapter, nats = _make_adapter()
        msg = _make_msg(_make_request_data(skill_id="stop", params={}))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True

    async def test_capture_image_success(self) -> None:
        adapter, nats = _make_adapter()
        msg = _make_msg(_make_request_data(skill_id="capture_image", params={"resolution": "640x480"}))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["output"]["image_path"].startswith("/sim/captures/")

    async def test_emergency_stop_latches_safety(self) -> None:
        adapter, nats = _make_adapter()
        msg = _make_msg(_make_request_data(skill_id="emergency_stop", params={}))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        # The emergency_stop execution itself counts as 1 active execution.
        assert response["output"]["stopped_missions"] == 1
        assert adapter._safety_latched is True


class TestHandleRequestUnknownSkill:
    async def test_unknown_skill_returns_error(self) -> None:
        adapter, nats = _make_adapter()
        msg = _make_msg(_make_request_data(skill_id="nonexistent"))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Unknown skill_id" in response["error"]


class TestHandleRequestMalformed:
    async def test_invalid_json_returns_error(self) -> None:
        adapter, nats = _make_adapter()
        msg = MockMsg(data=b"not-json", reply="reply.subject")
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert response["error"] != ""

    async def test_missing_required_fields_returns_error(self) -> None:
        adapter, nats = _make_adapter()
        msg = MockMsg(data=json.dumps({"request_id": "x"}).encode("utf-8"), reply="reply.subject")
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False


class TestNoReplySubject:
    async def test_none_reply_does_not_publish(self) -> None:
        adapter, nats = _make_adapter()
        msg = MockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply=None,  # type: ignore[arg-type]
        )
        await adapter._handle_request(msg)
        assert len(nats.published) == 0


class TestSafetyLatchPropagation:
    async def test_move_forward_refused_when_safety_latched(self) -> None:
        adapter, nats = _make_adapter()
        adapter._safety_latched = True

        msg = _make_msg(_make_request_data())
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Safety latch" in response["error"]


class TestActiveExecutionCount:
    async def test_active_count_decremented_after_success(self) -> None:
        adapter, _ = _make_adapter()
        msg = _make_msg(_make_request_data())
        await adapter._handle_request(msg)
        assert adapter._active_executions == 0

    async def test_active_count_decremented_after_unknown_skill(self) -> None:
        adapter, _ = _make_adapter()
        msg = _make_msg(_make_request_data(skill_id="nonexistent"))
        await adapter._handle_request(msg)
        assert adapter._active_executions == 0


class TestStartStop:
    async def test_start_connects_and_subscribes(self, monkeypatch: pytest.MonkeyPatch) -> None:
        mock_nc = MockNats()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        config = _make_config()
        adapter = SimAdapter(config)
        await adapter.start()

        mock_connect.assert_called_once()
        assert mock_connect.call_args.kwargs["servers"] == "nats://localhost:4222"
        assert len(mock_nc._subscribed) == 1
        assert mock_nc._subscribed[0][0] == config.skill_execute_subject

    async def test_stop_drains_and_clears_nc(self, monkeypatch: pytest.MonkeyPatch) -> None:
        mock_nc = MockNats()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        config = _make_config()
        adapter = SimAdapter(config)
        await adapter.start()
        await adapter.stop()

        assert adapter._nc is None

    async def test_stop_without_start_does_not_crash(self) -> None:
        adapter = SimAdapter(_make_config())
        await adapter.stop()


class TestEmergencyStopLatchesOnCrash:
    async def test_emergency_stop_crash_still_latches(self) -> None:
        """If emergency_stop skill raises, the adapter should still latch safety."""

        class CrashingEmergencyStop:
            skill_id = "emergency_stop"

            async def execute(self, params: Any, ctx: Any) -> Any:
                raise RuntimeError("crash")

        adapter, nats = _make_adapter()
        adapter._skills["emergency_stop"] = CrashingEmergencyStop()  # type: ignore[assignment]

        msg = _make_msg(_make_request_data(skill_id="emergency_stop", params={}))
        await adapter._handle_request(msg)

        # The adapter catches the crash and sets safety_latched.
        assert adapter._safety_latched is True
        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "crash" in response["error"]


class TestSkillCrashDoesNotKillAdapter:
    async def test_skill_crash_returns_error_response(self) -> None:
        class CrashingSkill:
            skill_id = "crash_test"

            async def execute(self, params: Any, ctx: Any) -> Any:
                raise RuntimeError("skill crashed")

        adapter, nats = _make_adapter()
        adapter._skills["crash_test"] = CrashingSkill()  # type: ignore[assignment]

        msg = _make_msg(_make_request_data(skill_id="crash_test", params={}))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Skill crashed" in response["error"]
        # Active count still decremented.
        assert adapter._active_executions == 0
