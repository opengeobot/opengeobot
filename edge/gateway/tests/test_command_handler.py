# Function: Edge gateway command handler unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the command handler (F-EDGE-001/002)."""

from __future__ import annotations

import asyncio
import json
from typing import Any

import pytest

from opengeobot_edge.command_handler import (
    CommandHandler,
    CommandResult,
    CommandType,
    EdgeCommand,
    SkillExecutionResponse,
)
from opengeobot_edge.config import EdgeConfig


class MockMsg:
    """Mimics nats.aio.msg.Msg for testing."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply


class MockNats:
    """Records publishes and simulates request-reply."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []
        self._reply_data: bytes | None = None
        self._request_should_timeout = False
        self._request_should_error = False
        self._connected = True

    def set_reply(self, data: bytes) -> None:
        self._reply_data = data

    def set_timeout(self) -> None:
        self._request_should_timeout = True

    def set_error(self) -> None:
        self._request_should_error = True

    def set_disconnected(self) -> None:
        self._connected = False

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

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return self._connected


class _MockReply:
    def __init__(self, data: bytes) -> None:
        self.data = data


class MockStatePublisher:
    """Records state publications without touching NATS."""

    def __init__(self) -> None:
        self.published: list[dict[str, Any]] = []
        self._online = True

    def mark_offline(self) -> None:
        self._online = False

    def mark_online(self) -> None:
        self._online = True

    async def publish_state(
        self,
        trace_id: str,
        last_command_id: str | None = None,
        command_result: CommandResult | None = None,
    ) -> None:
        self.published.append(
            {
                "trace_id": trace_id,
                "last_command_id": last_command_id,
                "command_result": command_result,
            }
        )

    async def start_heartbeat(self) -> None:
        pass

    async def stop_heartbeat(self) -> None:
        pass


class MockOfflineCache:
    """Records cache operations."""

    def __init__(self) -> None:
        self.pending_commands: dict[str, dict[str, Any]] = {}
        self.done_commands: list[str] = []

    async def add_pending_command(self, command: dict[str, Any]) -> None:
        cmd_id = command.get("command_id")
        if cmd_id and cmd_id not in self.pending_commands:
            self.pending_commands[cmd_id] = command

    async def mark_command_done(self, command_id: str) -> None:
        self.pending_commands.pop(command_id, None)
        self.done_commands.append(command_id)

    async def pending_commands_list(self) -> list[dict[str, Any]]:
        return list(self.pending_commands.values())


def _make_config(**overrides: Any) -> EdgeConfig:
    defaults: dict[str, Any] = {
        "robot_id": "rbt_test",
        "nats_url": "nats://localhost:4222",
        "nats_max_reconnect": -1,
        "nats_reconnect_wait": 2.0,
        "nats_connect_timeout": 5.0,
        "cloud_api_base_url": "http://localhost:8080",
        "state_publish_interval": 5.0,
        "skill_request_timeout": 10.0,
        "offline_cache_path": "",
        "log_level": "DEBUG",
    }
    defaults.update(overrides)
    return EdgeConfig(**defaults)


def _make_handler(
    nats: MockNats | None = None,
    config: EdgeConfig | None = None,
) -> tuple[CommandHandler, MockNats, MockStatePublisher, MockOfflineCache]:
    config = config or _make_config()
    nats = nats or MockNats()
    state_pub = MockStatePublisher()
    cache = MockOfflineCache()
    handler = CommandHandler(config, nats, state_pub, cache)  # type: ignore[arg-type]
    return handler, nats, state_pub, cache


def _make_command_data(**overrides: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "command_id": "cmd_001",
        "trace_id": "trace_001",
        "command_type": "start_mission",
        "mission_id": "mission_001",
    }
    base.update(overrides)
    return base


def _make_msg(data: dict[str, Any]) -> MockMsg:
    return MockMsg(data=json.dumps(data).encode("utf-8"))


class TestHandleCommandMalformed:
    async def test_rejects_invalid_json(self) -> None:
        handler, _, _, cache = _make_handler()
        msg = MockMsg(data=b"not-json")
        await handler.handle_command(msg)
        assert len(cache.pending_commands) == 0

    async def test_rejects_missing_required_fields(self) -> None:
        handler, _, _, cache = _make_handler()
        msg = MockMsg(data=json.dumps({"command_id": "x"}).encode("utf-8"))
        await handler.handle_command(msg)
        # Command validation fails before caching.
        assert len(cache.pending_commands) == 0


class TestStartMission:
    async def test_start_mission_success(self) -> None:
        handler, _, _, cache = _make_handler()
        msg = _make_msg(_make_command_data(command_type="start_mission"))
        await handler.handle_command(msg)

        assert handler.active_mission_id == "mission_001"
        assert "cmd_001" in cache.done_commands

    async def test_start_mission_missing_mission_id(self) -> None:
        handler, _, _, _ = _make_handler()
        msg = _make_msg(
            _make_command_data(command_type="start_mission", mission_id=None)
        )
        await handler.handle_command(msg)

        assert handler.active_mission_id is None

    async def test_start_mission_refused_when_safety_latched(self) -> None:
        handler, _, _, _ = _make_handler()
        # Engage safety latch.
        await handler.handle_command(
            _make_msg(_make_command_data(command_type="emergency_stop"))
        )
        assert handler.safety_latched is True

        # Now try to start a mission — should be refused.
        handler._active_mission_id = None  # reset
        msg = _make_msg(_make_command_data(command_type="start_mission"))
        await handler.handle_command(msg)
        assert handler.active_mission_id is None


class TestPauseResumeMission:
    async def test_pause_no_active_mission(self) -> None:
        handler, _, _, _ = _make_handler()
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.PAUSE_MISSION,
            )
        )
        assert result.accepted is False
        assert "No active mission" in result.detail

    async def test_pause_active_mission(self) -> None:
        handler, _, _, _ = _make_handler()
        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.START_MISSION,
                mission_id="m1",
            )
        )
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="t2",
                command_type=CommandType.PAUSE_MISSION,
            )
        )
        assert result.accepted is True
        assert "paused" in result.detail

    async def test_resume_refused_when_safety_latched(self) -> None:
        handler, _, _, _ = _make_handler()
        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.START_MISSION,
                mission_id="m1",
            )
        )
        await handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="t2",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c3",
                trace_id="t3",
                command_type=CommandType.RESUME_MISSION,
            )
        )
        assert result.accepted is False
        assert "Safety latch" in result.detail


class TestCancelMission:
    async def test_cancel_active_mission(self) -> None:
        handler, _, _, _ = _make_handler()
        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.START_MISSION,
                mission_id="m1",
            )
        )
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="t2",
                command_type=CommandType.CANCEL_MISSION,
            )
        )
        assert result.accepted is True
        assert handler.active_mission_id is None

    async def test_cancel_no_active_mission(self) -> None:
        handler, _, _, _ = _make_handler()
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.CANCEL_MISSION,
            )
        )
        assert result.accepted is False
        assert "No active mission" in result.detail


class TestEmergencyStopAndReset:
    async def test_emergency_stop_latches(self) -> None:
        handler, _, _, _ = _make_handler()
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )
        assert result.accepted is True
        assert result.success is True
        assert handler.safety_latched is True

    async def test_reset_safety_clears_latch(self) -> None:
        handler, _, _, _ = _make_handler()
        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )
        assert handler.safety_latched is True

        result = await handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="t2",
                command_type=CommandType.RESET_SAFETY,
            )
        )
        assert result.accepted is True
        assert handler.safety_latched is False


class TestExecuteSkill:
    async def test_execute_skill_success(self) -> None:
        nats = MockNats()
        nats.set_reply(
            SkillExecutionResponse(
                request_id="skreq_1",
                trace_id="trace_001",
                skill_id="move_forward",
                success=True,
                output={"distance": 1.0},
                started_at="2026-01-01T00:00:00Z",
                completed_at="2026-01-01T00:00:01Z",
            ).model_dump_json().encode("utf-8")
        )
        handler, _, _, _ = _make_handler(nats=nats)

        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EXECUTE_SKILL,
                skill_id="move_forward",
                params={"distance": 1.0, "speed": 0.5},
            )
        )
        assert result.accepted is True
        assert result.success is True
        assert result.output == {"distance": 1.0}

    async def test_execute_skill_missing_skill_id(self) -> None:
        handler, _, _, _ = _make_handler()
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EXECUTE_SKILL,
            )
        )
        assert result.accepted is False
        assert "skill_id" in result.detail

    async def test_execute_skill_refused_when_safety_latched(self) -> None:
        handler, _, _, _ = _make_handler()
        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="t2",
                command_type=CommandType.EXECUTE_SKILL,
                skill_id="move_forward",
            )
        )
        assert result.accepted is False
        assert "Safety latch" in result.detail

    async def test_execute_skill_timeout(self) -> None:
        nats = MockNats()
        nats.set_timeout()
        handler, _, _, _ = _make_handler(nats=nats)

        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EXECUTE_SKILL,
                skill_id="move_forward",
            )
        )
        assert result.accepted is True
        assert result.success is False
        assert "timed out" in result.detail

    async def test_execute_skill_executor_unreachable(self) -> None:
        nats = MockNats()
        nats.set_error()
        handler, _, _, _ = _make_handler(nats=nats)

        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EXECUTE_SKILL,
                skill_id="move_forward",
            )
        )
        assert result.accepted is False
        assert result.success is False
        assert "Local executor unavailable" in result.detail


class TestHandleCommandPublishesState:
    async def test_handle_command_publishes_state_after_dispatch(self) -> None:
        handler, _, state_pub, _ = _make_handler()
        msg = _make_msg(_make_command_data(command_type="start_mission"))
        await handler.handle_command(msg)

        assert len(state_pub.published) == 1
        state = state_pub.published[0]
        assert state["trace_id"] == "trace_001"
        assert state["last_command_id"] == "cmd_001"

    async def test_successful_command_marked_done(self) -> None:
        handler, _, _, cache = _make_handler()
        msg = _make_msg(_make_command_data(command_type="start_mission"))
        await handler.handle_command(msg)
        assert "cmd_001" in cache.done_commands


class TestUnsupportedCommand:
    async def test_unknown_command_type_returns_error(self) -> None:
        handler, _, _, _ = _make_handler()
        # Use a raw dict with an unsupported command_type to exercise validation.
        msg = MockMsg(
            data=json.dumps(
                {
                    "command_id": "c1",
                    "trace_id": "t1",
                    "command_type": "nonexistent_command",
                }
            ).encode("utf-8")
        )
        await handler.handle_command(msg)
        # Pydantic validation will reject the invalid enum value.
        assert handler.active_mission_id is None
