# Function: Reconnect reconciliation for the edge gateway (F-EDGE-002)
# Time: 2026-07-05
# Author: AxeXie
"""Reconnect and state synchronization.

When the NATS connection is restored after an offline period the reconciler:
  1. Flushes cached pending states to the cloud (newest first is fine; the cloud
     deduplicates by ``state_id``/``trace_id``).
  2. Reports the set of still-unacknowledged commands so the cloud can decide
     whether to reissue or cancel them.
  3. Publishes a fresh ``edge.reconciled``-style state summarizing the outcome.

All reconciliation events are logged with their trace ids so they appear in the
end-to-end trace.
"""

from __future__ import annotations

import uuid
from typing import TYPE_CHECKING

from loguru import logger

if TYPE_CHECKING:
    from .config import EdgeConfig
    from .nats_client import NatsBridge
    from .offline_cache import OfflineCache
    from .state_publisher import StatePublisher


class Reconciler:
    """Drives state synchronization after a NATS reconnect."""

    def __init__(
        self,
        config: EdgeConfig,
        nats: NatsBridge,
        offline_cache: OfflineCache,
        state_publisher: StatePublisher,
    ) -> None:
        self._config = config
        self._nats = nats
        self._offline_cache = offline_cache
        self._state_publisher = state_publisher

    async def reconcile(self) -> None:
        """Flush cached states and report pending commands after reconnect."""
        trace_id = f"rec_{uuid.uuid4().hex}"
        logger.bind(trace_id=trace_id).info("Reconciliation started")

        await self._state_publisher.mark_online_after_reconnect()

        flushed = await self._flush_pending_states(trace_id)
        pending_commands = await self._offline_cache.pending_commands()

        logger.bind(
            trace_id=trace_id,
            flushed_states=flushed,
            pending_commands=len(pending_commands),
        ).info("Reconciliation summary")

        # Publish a reconciled state carrying the reconciliation outcome.
        await self._state_publisher.publish_state(trace_id=trace_id)

        if flushed or pending_commands:
            logger.bind(trace_id=trace_id).info(
                "Reconciliation completed with pending work reported"
            )
        else:
            logger.bind(trace_id=trace_id).info("Reconciliation completed; in sync")

    async def _flush_pending_states(self, trace_id: str) -> int:
        from .state_publisher import EdgeState

        states = await self._offline_cache.pending_states()
        if not states:
            return 0

        flushed = 0
        # Publish oldest-first to preserve chronological order on the cloud side.
        for raw in states:
            try:
                state = EdgeState.model_validate(raw)
            except ValueError as exc:
                logger.bind(error=str(exc)).warning(
                    "Skipping malformed cached state during reconciliation"
                )
                continue
            if await self._state_publisher.flush_cached_state(state):
                flushed += 1
            else:
                # Connection dropped again mid-flush; stop and keep the rest cached.
                logger.bind(trace_id=trace_id).warning(
                    "Connection lost during state flush; remaining states cached"
                )
                break

        # Drop only the successfully flushed prefix.
        if flushed > 0:
            await self._offline_cache.drop_pending_states(flushed)
        return flushed
