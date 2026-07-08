# Function: Offline cache unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the offline cache (F-EDGE-002)."""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

import pytest

from opengeobot_edge.offline_cache import OfflineCache


def _cache_path(tmp_path: Path) -> str:
    return str(tmp_path / "test-cache.json")


class TestInitEmpty:
    async def test_starts_empty_when_file_missing(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        assert await cache.pending_state_count() == 0
        assert await cache.pending_command_count() == 0

    async def test_starts_empty_when_file_unreadable(self, tmp_path: Path) -> None:
        path = tmp_path / "bad-cache.json"
        path.write_text("not-json{", encoding="utf-8")
        cache = OfflineCache(str(path))
        assert await cache.pending_state_count() == 0
        assert await cache.pending_command_count() == 0


class TestAddPendingState:
    async def test_add_state_increases_count(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_state({"state_id": "s1"})
        assert await cache.pending_state_count() == 1

    async def test_add_multiple_states(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_state({"state_id": "s1"})
        await cache.add_pending_state({"state_id": "s2"})
        await cache.add_pending_state({"state_id": "s3"})
        assert await cache.pending_state_count() == 3

    async def test_pending_states_returns_copy(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_state({"state_id": "s1"})
        states = await cache.pending_states()
        states.clear()
        # Mutating the returned list must not affect the cache.
        assert await cache.pending_state_count() == 1


class TestAddPendingCommand:
    async def test_add_command_stores_by_id(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_command({"command_id": "c1", "type": "start"})
        assert await cache.pending_command_count() == 1

    async def test_duplicate_command_id_not_added(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_command({"command_id": "c1", "type": "start"})
        await cache.add_pending_command({"command_id": "c1", "type": "start"})
        assert await cache.pending_command_count() == 1

    async def test_command_without_id_ignored(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_command({"type": "start"})
        assert await cache.pending_command_count() == 0

    async def test_pending_commands_returns_copy(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_command({"command_id": "c1"})
        cmds = await cache.pending_commands()
        cmds.clear()
        assert await cache.pending_command_count() == 1


class TestMarkCommandDone:
    async def test_mark_done_removes_command(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_command({"command_id": "c1"})
        await cache.mark_command_done("c1")
        assert await cache.pending_command_count() == 0

    async def test_mark_done_nonexistent_is_noop(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        # Should not raise.
        await cache.mark_command_done("nonexistent")


class TestClearAndDropStates:
    async def test_clear_pending_states(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_state({"state_id": "s1"})
        await cache.add_pending_state({"state_id": "s2"})
        await cache.clear_pending_states()
        assert await cache.pending_state_count() == 0

    async def test_drop_pending_states_removes_prefix(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        for i in range(5):
            await cache.add_pending_state({"state_id": f"s{i}"})
        await cache.drop_pending_states(3)
        states = await cache.pending_states()
        assert len(states) == 2
        assert states[0]["state_id"] == "s3"
        assert states[1]["state_id"] == "s4"

    async def test_drop_zero_is_noop(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_state({"state_id": "s1"})
        await cache.drop_pending_states(0)
        assert await cache.pending_state_count() == 1

    async def test_drop_negative_is_noop(self, tmp_path: Path) -> None:
        cache = OfflineCache(_cache_path(tmp_path))
        await cache.add_pending_state({"state_id": "s1"})
        await cache.drop_pending_states(-5)
        assert await cache.pending_state_count() == 1


class TestPersistence:
    async def test_states_persisted_to_disk(self, tmp_path: Path) -> None:
        path = _cache_path(tmp_path)
        cache = OfflineCache(path)
        await cache.add_pending_state({"state_id": "s1", "status": "ONLINE"})

        raw = Path(path).read_text(encoding="utf-8")
        data = json.loads(raw)
        assert len(data["pending_states"]) == 1
        assert data["pending_states"][0]["state_id"] == "s1"

    async def test_commands_persisted_to_disk(self, tmp_path: Path) -> None:
        path = _cache_path(tmp_path)
        cache = OfflineCache(path)
        await cache.add_pending_command({"command_id": "c1", "type": "start"})

        raw = Path(path).read_text(encoding="utf-8")
        data = json.loads(raw)
        assert len(data["pending_commands"]) == 1
        assert data["pending_commands"][0]["command_id"] == "c1"

    async def test_loads_existing_states_on_init(self, tmp_path: Path) -> None:
        path = _cache_path(tmp_path)
        cache1 = OfflineCache(path)
        await cache1.add_pending_state({"state_id": "s1"})
        await cache1.add_pending_command({"command_id": "c1"})

        # New instance loads from disk.
        cache2 = OfflineCache(path)
        assert await cache2.pending_state_count() == 1
        assert await cache2.pending_command_count() == 1
        states = await cache2.pending_states()
        assert states[0]["state_id"] == "s1"

    async def test_persisted_updated_at_present(self, tmp_path: Path) -> None:
        path = _cache_path(tmp_path)
        cache = OfflineCache(path)
        await cache.add_pending_state({"state_id": "s1"})

        raw = Path(path).read_text(encoding="utf-8")
        data = json.loads(raw)
        assert "updated_at" in data
        assert data["updated_at"] != ""

    async def test_persist_creates_parent_directory(self, tmp_path: Path) -> None:
        path = str(tmp_path / "subdir" / "nested" / "cache.json")
        cache = OfflineCache(path)
        await cache.add_pending_state({"state_id": "s1"})
        assert os.path.exists(path)

    async def test_atomic_write_uses_tmp_file(self, tmp_path: Path) -> None:
        path = _cache_path(tmp_path)
        cache = OfflineCache(path)
        await cache.add_pending_state({"state_id": "s1"})
        # The .tmp file should have been replaced, not left behind.
        assert not Path(path + ".tmp").exists()


class TestConcurrentAccess:
    async def test_concurrent_adds_are_serialized(self, tmp_path: Path) -> None:
        import asyncio

        cache = OfflineCache(_cache_path(tmp_path))
        await asyncio.gather(
            *[cache.add_pending_state({"state_id": f"s{i}"}) for i in range(10)]
        )
        assert await cache.pending_state_count() == 10
