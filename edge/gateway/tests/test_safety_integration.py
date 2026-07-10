# Function: Safety state machine integration tests
# Time: 2026-07-09
# Author: AxeXie
"""Tests verifying the unified SafetyStateMachine (SM-SAFETY-001) is used
by CommandHandler and StatePublisher instead of an independent boolean
(Task 8 Part 2)."""

from __future__ import annotations

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
from opengeobot_edge.offline_cache import OfflineCache
from opengeobot_edge.state_publisher import EdgeState, RobotStatus, StatePublisher
from opengeobot_safety_gateway.safety_state import SafetyState, SafetyStateMachine


# ---------------------------------------------------------------------------
# Shared mock helpers.
# ---------------------------------------------------------------------------


class MockNats:
    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []
        self._reply_data: bytes | None = None
        self._connected = True

    def set_reply(self, data: bytes) -> None:
        self._reply_data = data

    def set_disconnected(self) -> None:
        self._connected = False

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        if self._reply_data is None:
            raise RuntimeError("No reply data")
        return type("Reply", (), {"data": self._reply_data})()

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return self._connected


class MockStatePublisher:
    def __init__(self) -> None:
        self.published: list[dict[str, Any]] = []

    def mark_offline(self) -> None:
        pass

    def mark_online(self) -> None:
        pass

    async def publish_state(
        self,
        trace_id: str,
        last_command_id: str | None = None,
        command_result: CommandResult | None = None,
    ) -> None:
        self.published.append(
            {"trace_id": trace_id, "last_command_id": last_command_id}
        )

    async def start_heartbeat(self) -> None:
        pass

    async def stop_heartbeat(self) -> None:
        pass


class MockOfflineCache:
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
    safety_state: SafetyStateMachine | None = None,
    config: EdgeConfig | None = None,
    nats: MockNats | None = None,
) -> tuple[CommandHandler, MockNats, MockStatePublisher, MockOfflineCache, SafetyStateMachine]:
    config = config or _make_config()
    nats = nats or MockNats()
    state_pub = MockStatePublisher()
    cache = MockOfflineCache()
    safety = safety_state or SafetyStateMachine()
    handler = CommandHandler(config, nats, state_pub, cache, safety_state=safety)  # type: ignore[arg-type]
    return handler, nats, state_pub, cache, safety


# ---------------------------------------------------------------------------
# CommandHandler safety integration tests.
# ---------------------------------------------------------------------------


class TestSafetyStateMachineUsed:
    """Verify CommandHandler uses SafetyStateMachine, not a local boolean."""

    def test_handler_exposes_safety_state_machine(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)
        assert handler.safety_state is safety

    def test_safety_latched_false_when_normal(self) -> None:
        handler, _, _, _, _ = _make_handler()
        assert handler.safety_latched is False

    async def test_emergency_stop_transitions_to_emergency_stopped(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )

        assert result.accepted is True
        assert result.success is True
        assert safety.state is SafetyState.EMERGENCY_STOPPED
        assert handler.safety_latched is True

    async def test_reset_safety_transitions_back_to_normal(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

        # Engage emergency stop.
        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )
        assert safety.state is SafetyState.EMERGENCY_STOPPED

        # Reset safety.
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="t2",
                command_type=CommandType.RESET_SAFETY,
            )
        )

        assert result.accepted is True
        assert result.success is True
        assert safety.state is SafetyState.NORMAL
        assert handler.safety_latched is False

    async def test_reset_safety_when_already_normal(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.RESET_SAFETY,
            )
        )

        assert result.accepted is True
        assert result.success is True
        assert "already in NORMAL" in result.detail
        assert safety.state is SafetyState.NORMAL

    async def test_reset_safety_from_resetting_state(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

        # Engage emergency stop, then request reset (leaves in RESETTING).
        await safety.trigger_emergency_stop()
        await safety.request_reset()
        assert safety.state is SafetyState.RESETTING

        result = await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.RESET_SAFETY,
            )
        )

        assert result.accepted is True
        assert result.success is True
        assert safety.state is SafetyState.NORMAL


class TestSafetyBlocksMotion:
    """Verify motion commands are blocked when safety is engaged."""

    async def test_execute_skill_refused_after_emergency_stop(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

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
        assert safety.state is SafetyState.EMERGENCY_STOPPED

    async def test_start_mission_refused_after_emergency_stop(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

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
                command_type=CommandType.START_MISSION,
                mission_id="m1",
            )
        )

        assert result.accepted is False
        assert "Safety latch" in result.detail

    async def test_resume_mission_refused_after_emergency_stop(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

        # Start mission first.
        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.START_MISSION,
                mission_id="m1",
            )
        )
        # Engage emergency stop.
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


class TestSafetyRestoredAfterReset:
    """Verify motion commands succeed after reset."""

    async def test_execute_skill_allowed_after_reset(self) -> None:
        nats = MockNats()
        nats.set_reply(
            SkillExecutionResponse(
                request_id="skreq_1",
                trace_id="t3",
                skill_id="move_forward",
                success=True,
                output={"distance": 1.0},
                started_at="2026-01-01T00:00:00Z",
                completed_at="2026-01-01T00:00:01Z",
            ).model_dump_json().encode("utf-8")
        )
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety, nats=nats)

        # Engage emergency stop.
        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )
        # Reset safety.
        await handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="t2",
                command_type=CommandType.RESET_SAFETY,
            )
        )
        assert safety.state is SafetyState.NORMAL

        # Execute skill should now succeed.
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c3",
                trace_id="t3",
                command_type=CommandType.EXECUTE_SKILL,
                skill_id="move_forward",
            )
        )

        assert result.accepted is True
        assert result.success is True

    async def test_start_mission_allowed_after_reset(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

        # Engage emergency stop.
        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )
        # Reset safety.
        await handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="t2",
                command_type=CommandType.RESET_SAFETY,
            )
        )

        # Start mission should now succeed.
        result = await handler._dispatch(
            EdgeCommand(
                command_id="c3",
                trace_id="t3",
                command_type=CommandType.START_MISSION,
                mission_id="m1",
            )
        )

        assert result.accepted is True
        assert result.success is True


class TestSafetyStateTransitionsRecorded:
    """Verify safety state transitions are recorded with trace_id."""

    async def test_emergency_stop_records_trace_id(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="trace_estop_001",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )

        transitions = safety.get_transitions()
        assert len(transitions) >= 1
        estop_transition = transitions[-1]
        assert estop_transition.to_state is SafetyState.EMERGENCY_STOPPED
        assert estop_transition.trace_id == "trace_estop_001"

    async def test_reset_records_trace_id(self) -> None:
        safety = SafetyStateMachine()
        handler, _, _, _, _ = _make_handler(safety_state=safety)

        await handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="trace_estop_002",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )
        await handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="trace_reset_002",
                command_type=CommandType.RESET_SAFETY,
            )
        )

        transitions = safety.get_transitions()
        # Should have: EMERGENCY_STOPPED, RESETTING, NORMAL
        assert len(transitions) >= 3
        reset_transition = transitions[-2]
        complete_transition = transitions[-1]
        assert reset_transition.to_state is SafetyState.RESETTING
        assert reset_transition.trace_id == "trace_reset_002"
        assert complete_transition.to_state is SafetyState.NORMAL
        assert complete_transition.trace_id == "trace_reset_002"


# ---------------------------------------------------------------------------
# StatePublisher safety integration tests.
# ---------------------------------------------------------------------------


class TestStatePublisherSafetyIntegration:
    """Verify StatePublisher reads safety state from the shared state machine."""

    def test_safety_latched_reads_from_state_machine(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()
        pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]

        assert pub.safety_latched is False

    async def test_safety_latched_true_after_emergency_stop(
        self, tmp_path: pytest.Path
    ) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()
        pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]

        await safety.trigger_emergency_stop()

        assert pub.safety_latched is True

    async def test_safety_latched_false_after_reset(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()
        pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]

        await safety.trigger_emergency_stop()
        await safety.request_reset()
        await safety.complete_reset()

        assert pub.safety_latched is False

    async def test_set_safety_latched_true_drives_state_machine(
        self, tmp_path: pytest.Path
    ) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()
        pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]

        await pub.set_safety_latched(True)

        assert safety.state is SafetyState.EMERGENCY_STOPPED
        assert pub.safety_latched is True
        assert pub.status is RobotStatus.ERROR

    async def test_set_safety_latched_false_drives_state_machine(
        self, tmp_path: pytest.Path
    ) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()
        pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]

        await pub.set_safety_latched(True)
        await pub.set_safety_latched(False)

        assert safety.state is SafetyState.NORMAL
        assert pub.safety_latched is False

    async def test_publish_state_includes_safety_latched_from_state_machine(
        self, tmp_path: pytest.Path
    ) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()
        pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]

        await safety.trigger_emergency_stop()
        await pub.publish_state(trace_id="t1")

        published_data = [d for s, d in nats.published if s == config.state_subject]
        assert len(published_data) == 1
        state = json.loads(published_data[0])
        assert state["safety_latched"] is True
        assert state["status"] == "ERROR"

    async def test_derive_status_error_when_safety_engaged(
        self, tmp_path: pytest.Path
    ) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()
        pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]

        await safety.trigger_emergency_stop()

        status = pub.derive_status(None, online=True)
        assert status is RobotStatus.ERROR

    async def test_mark_online_after_reconnect_preserves_error(
        self, tmp_path: pytest.Path
    ) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()
        pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]

        # set_safety_latched(True) drives the state machine AND sets ERROR status.
        await pub.set_safety_latched(True)
        assert pub.status is RobotStatus.ERROR

        # Reconnect should NOT overwrite ERROR when safety is still engaged.
        await pub.mark_online_after_reconnect()

        assert pub.status is RobotStatus.ERROR

    async def test_mark_online_after_reconnect_when_safe(
        self, tmp_path: pytest.Path
    ) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()
        pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]

        pub.mark_offline()
        await pub.mark_online_after_reconnect()

        assert pub.status is RobotStatus.ONLINE


# ---------------------------------------------------------------------------
# Shared safety state machine test.
# ---------------------------------------------------------------------------


class TestSharedSafetyStateMachine:
    """Verify CommandHandler and StatePublisher share the same state machine."""

    async def test_shared_state_machine_reflects_changes(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        safety = SafetyStateMachine()

        state_pub = StatePublisher(config, nats, cache, safety_state=safety)  # type: ignore[arg-type]
        cmd_cache = MockOfflineCache()
        cmd_handler = CommandHandler(
            config, nats, state_pub, cmd_cache, safety_state=safety  # type: ignore[arg-type]
        )

        # Initially safe.
        assert cmd_handler.safety_latched is False
        assert state_pub.safety_latched is False

        # Command handler triggers emergency stop.
        await cmd_handler._dispatch(
            EdgeCommand(
                command_id="c1",
                trace_id="t1",
                command_type=CommandType.EMERGENCY_STOP,
            )
        )

        # State publisher should see the same state.
        assert cmd_handler.safety_latched is True
        assert state_pub.safety_latched is True
        assert safety.state is SafetyState.EMERGENCY_STOPPED

        # Command handler resets safety.
        await cmd_handler._dispatch(
            EdgeCommand(
                command_id="c2",
                trace_id="t2",
                command_type=CommandType.RESET_SAFETY,
            )
        )

        # Both should see NORMAL again.
        assert cmd_handler.safety_latched is False
        assert state_pub.safety_latched is False
        assert safety.state is SafetyState.NORMAL
