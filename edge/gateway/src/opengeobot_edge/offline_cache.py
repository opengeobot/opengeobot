# Function: Offline state cache for the edge gateway (F-EDGE-002)
# Time: 2026-07-05
# Author: AxeXie
"""Persistent local cache used while the cloud connection is unavailable.

Two categories are cached:
  * ``pending_states`` — state updates that could not be published to the cloud
    and must be flushed on reconnect.
  * ``pending_commands`` — commands received from the cloud but not yet
    acknowledged as completed, so they survive a gateway restart and can be
    reported during reconciliation.

The cache is serialized to a JSON file under ``EDGE_OFFLINE_CACHE_PATH``. A
single ``asyncio.Lock`` serializes access; volumes are small for M2.
"""

from __future__ import annotations

import asyncio
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from loguru import logger


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class OfflineCache:
    """JSON-backed cache of unsent states and unacked commands."""

    def __init__(self, path: str) -> None:
        self._path = Path(path)
        self._lock = asyncio.Lock()
        self._pending_states: list[dict[str, Any]] = []
        self._pending_commands: dict[str, dict[str, Any]] = {}
        self._load()

    def _load(self) -> None:
        if not self._path.exists():
            return
        try:
            raw = self._path.read_text(encoding="utf-8")
            data = json.loads(raw)
        except (json.JSONDecodeError, OSError) as exc:
            logger.bind(error=str(exc), path=str(self._path)).warning(
                "Offline cache unreadable; starting empty"
            )
            return
        self._pending_states = list(data.get("pending_states", []))
        # Commands are stored as a list on disk but keyed by command_id in memory.
        for cmd in data.get("pending_commands", []):
            cmd_id = cmd.get("command_id")
            if cmd_id:
                self._pending_commands[cmd_id] = cmd

    def _persist(self) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "pending_states": self._pending_states,
            "pending_commands": list(self._pending_commands.values()),
            "updated_at": _now_iso(),
        }
        tmp = self._path.with_suffix(self._path.suffix + ".tmp")
        tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        os.replace(tmp, self._path)

    async def add_pending_state(self, state: dict[str, Any]) -> None:
        async with self._lock:
            self._pending_states.append(state)
            self._persist()
            logger.bind(state_id=state.get("state_id")).debug("Cached pending state")

    async def add_pending_command(self, command: dict[str, Any]) -> None:
        async with self._lock:
            cmd_id = command.get("command_id")
            if not cmd_id:
                return
            if cmd_id not in self._pending_commands:
                self._pending_commands[cmd_id] = command
                self._persist()
                logger.bind(command_id=cmd_id).debug("Cached pending command")

    async def mark_command_done(self, command_id: str) -> None:
        async with self._lock:
            if self._pending_commands.pop(command_id, None) is not None:
                self._persist()
                logger.bind(command_id=command_id).debug("Marked command done in cache")

    async def pending_states(self) -> list[dict[str, Any]]:
        """Return a copy of cached pending states (without clearing)."""
        async with self._lock:
            return list(self._pending_states)

    async def pending_commands(self) -> list[dict[str, Any]]:
        """Return pending commands not yet acknowledged."""
        async with self._lock:
            return list(self._pending_commands.values())

    async def clear_pending_states(self) -> None:
        async with self._lock:
            self._pending_states.clear()
            self._persist()

    async def drop_pending_states(self, count: int) -> None:
        """Drop the first ``count`` cached states after they were flushed."""
        if count <= 0:
            return
        async with self._lock:
            del self._pending_states[:count]
            self._persist()

    async def pending_state_count(self) -> int:
        async with self._lock:
            return len(self._pending_states)

    async def pending_command_count(self) -> int:
        async with self._lock:
            return len(self._pending_commands)
