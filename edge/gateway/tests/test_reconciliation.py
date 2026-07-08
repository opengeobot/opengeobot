# Function: Reconciliation and state publisher unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the reconciler and state publisher (F-EDGE-002)."""

from __future__ import annotations

import asyncio
import json
from typing import Any

import pytest

from opengeobot_edge.command_handler import CommandResult, CommandType
from opengeobot_edge.config import EdgeConfig
from opengeobot_edge.offline_cache import OfflineCache
from opengeobot_edge.reconciliation import Reconciler
from opengeobot_edge.state_publisher import (
    EdgeState,
    RobotStatus,
    StatePublisher,
)


class MockNats:
    """Records publishes and simulates connection state."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []
        self._connected = True

    def set_connected(self, connected: bool) -> None:
        self._connected = connected

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        raise RuntimeError("Not used")

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return self._connected


class DisconnectingMockNats(MockNats):
    """Simulates a connection that drops after the first successful publish."""

    def __init__(self) -> None:
        super().__init__()
        self._publish_count = 0

    async def publish(self, subject: str, data: bytes) -> None:
        self._publish_count += 1
        self.published.append((subject, data))
        # Drop connection after the first publish.
        if self._publish_count >= 1:
            self._connected = False


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


def _published_on(nats: MockNats, subject: str) -> list[bytes]:
    return [d for s, d in nats.published if s == subject]


# ---------------------------------------------------------------------------
# State publisher tests
# ---------------------------------------------------------------------------


class TestStatePublisherStatus:
    def test_initial_status_is_online(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]
        assert pub.status is RobotStatus.ONLINE

    def test_mark_offline_sets_status(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]
        pub.mark_offline()
        assert pub.status is RobotStatus.OFFLINE

    def test_mark_online_sets_status(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]
        pub.mark_offline()
        pub.mark_online()
        assert pub.status is RobotStatus.ONLINE

    def test_set_safety_latched_sets_error_status(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]
        pub.set_safety_latched(True)
        assert pub.safety_latched is True
        assert pub.status is RobotStatus.ERROR

    def test_set_mission_updates_mission(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]
        pub.set_mission("m1")
        assert pub._mission_id == "m1"

    async def test_mark_online_after_reconnect_when_safe(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]
        pub.mark_offline()
        await pub.mark_online_after_reconnect()
        assert pub.status is RobotStatus.ONLINE

    async def test_mark_online_after_reconnect_preserves_error(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]
        pub.set_safety_latched(True)
        # Status is ERROR because of the safety latch.
        assert pub.status is RobotStatus.ERROR
        await pub.mark_online_after_reconnect()
        # Safety latch still engaged → status preserved as ERROR.
        assert pub.status is RobotStatus.ERROR


class TestDeriveStatus:
    def _make_pub(self, tmp_path: pytest.Path) -> StatePublisher:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        return StatePublisher(config, nats, cache)  # type: ignore[arg-type]

    def test_safety_latched_returns_error(self, tmp_path: pytest.Path) -> None:
        pub = self._make_pub(tmp_path)
        pub.set_safety_latched(True)
        status = pub.derive_status(None, online=True)
        assert status is RobotStatus.ERROR

    def test_offline_returns_offline(self, tmp_path: pytest.Path) -> None:
        pub = self._make_pub(tmp_path)
        status = pub.derive_status(None, online=False)
        assert status is RobotStatus.OFFLINE

    def test_success_with_mission_returns_busy(self, tmp_path: pytest.Path) -> None:
        pub = self._make_pub(tmp_path)
        pub.set_mission("m1")
        result = CommandResult(
            command_id="c1", trace_id="t1",
            command_type=CommandType.START_MISSION, accepted=True, success=True,
        )
        status = pub.derive_status(result, online=True)
        assert status is RobotStatus.BUSY

    def test_success_without_mission_returns_online(self, tmp_path: pytest.Path) -> None:
        pub = self._make_pub(tmp_path)
        result = CommandResult(
            command_id="c1", trace_id="t1",
            command_type=CommandType.RESET_SAFETY, accepted=True, success=True,
        )
        status = pub.derive_status(result, online=True)
        assert status is RobotStatus.ONLINE

    def test_failure_returns_error(self, tmp_path: pytest.Path) -> None:
        pub = self._make_pub(tmp_path)
        result = CommandResult(
            command_id="c1", trace_id="t1",
            command_type=CommandType.EXECUTE_SKILL, accepted=False, success=False,
        )
        status = pub.derive_status(result, online=True)
        assert status is RobotStatus.ERROR


class TestPublishState:
    async def test_publish_state_when_online(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        nats.set_connected(True)
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]

        await pub.publish_state(trace_id="t1")

        states = _published_on(nats, config.state_subject)
        assert len(states) == 1
        state = json.loads(states[0])
        assert state["robot_id"] == "rbt_test"
        assert state["trace_id"] == "t1"
        assert state["offline_mode"] is False

    async def test_publish_state_caches_when_offline(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        nats.set_connected(False)
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]

        await pub.publish_state(trace_id="t1")

        # Nothing published to NATS.
        assert len(_published_on(nats, config.state_subject)) == 0
        # State cached.
        assert await cache.pending_state_count() == 1

    async def test_publish_state_includes_command_result(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        nats.set_connected(True)
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]

        result = CommandResult(
            command_id="c1", trace_id="t1",
            command_type=CommandType.START_MISSION, accepted=True, success=True,
        )
        await pub.publish_state(trace_id="t1", last_command_id="c1", command_result=result)

        states = _published_on(nats, config.state_subject)
        assert len(states) == 1
        state = json.loads(states[0])
        assert state["last_command_id"] == "c1"
        assert state["last_command_success"] is True


class TestFlushCachedState:
    async def test_flush_cached_state_when_connected(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        nats.set_connected(True)
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]

        state = EdgeState(
            state_id="est_1", trace_id="t1", robot_id="rbt_test",
            status=RobotStatus.ONLINE, reported_at="2026-01-01T00:00:00Z",
        )
        ok = await pub.flush_cached_state(state)
        assert ok is True
        assert len(_published_on(nats, config.state_subject)) == 1

    async def test_flush_cached_state_when_disconnected(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        nats.set_connected(False)
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]

        state = EdgeState(
            state_id="est_1", trace_id="t1", robot_id="rbt_test",
            status=RobotStatus.ONLINE, reported_at="2026-01-01T00:00:00Z",
        )
        ok = await pub.flush_cached_state(state)
        assert ok is False


class TestHeartbeat:
    async def test_start_and_stop_heartbeat(self, tmp_path: pytest.Path) -> None:
        config = _make_config(
            offline_cache_path=str(tmp_path / "cache.json"),
            state_publish_interval=0.01,
        )
        nats = MockNats()
        nats.set_connected(True)
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]

        await pub.start_heartbeat()
        await asyncio.sleep(0.05)
        await pub.stop_heartbeat()

        # At least one heartbeat should have been published.
        assert len(nats.published) >= 1

    async def test_start_heartbeat_idempotent(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]

        await pub.start_heartbeat()
        task1 = pub._heartbeat_task
        await pub.start_heartbeat()
        assert pub._heartbeat_task is task1
        await pub.stop_heartbeat()

    async def test_stop_heartbeat_when_not_started(self, tmp_path: pytest.Path) -> None:
        config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
        nats = MockNats()
        cache = OfflineCache(config.offline_cache_path)
        pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]
        # Should not raise.
        await pub.stop_heartbeat()


# ---------------------------------------------------------------------------
# Reconciler tests
# ---------------------------------------------------------------------------


def _make_reconciler(
    tmp_path: pytest.Path, nats: MockNats | None = None
) -> tuple[Reconciler, MockNats, OfflineCache, StatePublisher, EdgeConfig]:
    config = _make_config(offline_cache_path=str(tmp_path / "cache.json"))
    nats = nats or MockNats()
    cache = OfflineCache(config.offline_cache_path)
    pub = StatePublisher(config, nats, cache)  # type: ignore[arg-type]
    reconciler = Reconciler(config, nats, cache, pub)  # type: ignore[arg-type]
    return reconciler, nats, cache, pub, config


class TestReconciler:
    async def test_reconcile_no_pending(self, tmp_path: pytest.Path) -> None:
        reconciler, nats, cache, pub, config = _make_reconciler(tmp_path)
        nats.set_connected(True)

        await reconciler.reconcile()

        # A reconciled state should have been published.
        states = _published_on(nats, config.state_subject)
        assert len(states) >= 1

    async def test_reconcile_flushes_cached_states(self, tmp_path: pytest.Path) -> None:
        reconciler, nats, cache, pub, config = _make_reconciler(tmp_path)
        nats.set_connected(True)

        # Add some cached states.
        state1 = EdgeState(
            state_id="est_1", trace_id="t1", robot_id="rbt_test",
            status=RobotStatus.ONLINE, reported_at="2026-01-01T00:00:00Z",
        )
        state2 = EdgeState(
            state_id="est_2", trace_id="t2", robot_id="rbt_test",
            status=RobotStatus.BUSY, reported_at="2026-01-01T00:00:01Z",
        )
        await cache.add_pending_state(state1.model_dump())
        await cache.add_pending_state(state2.model_dump())

        await reconciler.reconcile()

        # Both cached states flushed.
        assert await cache.pending_state_count() == 0
        states = _published_on(nats, config.state_subject)
        assert len(states) >= 2

    async def test_reconcile_stops_on_connection_loss(self, tmp_path: pytest.Path) -> None:
        nats = DisconnectingMockNats()
        reconciler, nats, cache, pub, config = _make_reconciler(tmp_path, nats=nats)

        state1 = EdgeState(
            state_id="est_1", trace_id="t1", robot_id="rbt_test",
            status=RobotStatus.ONLINE, reported_at="2026-01-01T00:00:00Z",
        )
        state2 = EdgeState(
            state_id="est_2", trace_id="t2", robot_id="rbt_test",
            status=RobotStatus.BUSY, reported_at="2026-01-01T00:00:01Z",
        )
        await cache.add_pending_state(state1.model_dump())
        await cache.add_pending_state(state2.model_dump())

        await reconciler.reconcile()

        # The first state was flushed, then the connection dropped.
        # The second state should remain cached.
        assert await cache.pending_state_count() >= 1

    async def test_reconcile_reports_pending_commands(self, tmp_path: pytest.Path) -> None:
        reconciler, nats, cache, pub, config = _make_reconciler(tmp_path)
        nats.set_connected(True)

        await cache.add_pending_command({"command_id": "c1", "command_type": "start_mission"})
        await cache.add_pending_command({"command_id": "c2", "command_type": "execute_skill"})

        await reconciler.reconcile()

        # Pending commands should still be there (not cleared by reconcile).
        cmds = await cache.pending_commands()
        assert len(cmds) == 2

    async def test_reconcile_skips_malformed_cached_state(self, tmp_path: pytest.Path) -> None:
        reconciler, nats, cache, pub, config = _make_reconciler(tmp_path)
        nats.set_connected(True)

        # Add a valid and a malformed state.
        state1 = EdgeState(
            state_id="est_1", trace_id="t1", robot_id="rbt_test",
            status=RobotStatus.ONLINE, reported_at="2026-01-01T00:00:00Z",
        )
        await cache.add_pending_state(state1.model_dump())
        await cache.add_pending_state({"garbage": True, "not_a_valid_state": 42})

        await reconciler.reconcile()

        # The valid state was flushed; malformed was skipped (dropped).
        states = _published_on(nats, config.state_subject)
        assert len(states) >= 1
