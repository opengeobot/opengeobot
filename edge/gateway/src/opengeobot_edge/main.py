# Function: Edge gateway asyncio entry point (F-EDGE-001/002)
# Time: 2026-07-05
# Author: AxeXie
"""Edge gateway runtime entry point.

Wires configuration, NATS connection, command handler, state publisher, offline
cache and reconciler. The gateway subscribes to the cloud command channel,
forwards skill executions to the local skill executor (sim-adapter for M2), and
publishes state back to the cloud. On NATS reconnect the reconciler flushes
cached states and reports pending commands.

Run directly:
    python -m opengeobot_edge.main
or:
    python src/opengeobot_edge/main.py
"""

from __future__ import annotations

import asyncio
import signal
import sys

from loguru import logger

from .command_handler import CommandHandler
from .config import EdgeConfig
from .nats_client import NatsBridge
from .offline_cache import OfflineCache
from .reconciliation import Reconciler
from .state_publisher import StatePublisher


def _configure_logging(level: str) -> None:
    logger.remove()
    logger.add(
        sys.stderr,
        level=level,
        format=(
            "<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | "
            "<level>{level: <8}</level> | "
            "<cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - "
            "<level>{message}</level>"
        ),
        backtrace=False,
        diagnose=False,
    )


class EdgeGateway:
    """Top-level orchestrator for the edge gateway runtime."""

    def __init__(self, config: EdgeConfig) -> None:
        self._config = config
        self._nats = NatsBridge(config)
        self._offline_cache = OfflineCache(config.offline_cache_path)
        self._state_publisher = StatePublisher(config, self._nats, self._offline_cache)
        self._command_handler = CommandHandler(
            config, self._nats, self._state_publisher, self._offline_cache
        )
        self._reconciler = Reconciler(
            config, self._nats, self._offline_cache, self._state_publisher
        )
        self._stop_event = asyncio.Event()

    async def start(self) -> None:
        # Wire connection lifecycle hooks before connecting.
        self._nats.on_disconnect = self._on_disconnect
        self._nats.on_reconnect = self._reconciler.reconcile

        await self._nats.connect()
        await self._nats.subscribe(
            self._config.command_subject, self._command_handler.handle_command
        )
        await self._state_publisher.start_heartbeat()

        # Announce the edge is online.
        await self._state_publisher.publish_state(trace_id="edge-bootstrap")

        logger.bind(robot_id=self._config.robot_id).info(
            "Edge gateway started and subscribed to command channel"
        )

    async def stop(self) -> None:
        logger.info("Edge gateway stopping...")
        await self._state_publisher.stop_heartbeat()
        await self._nats.drain_and_close()
        self._stop_event.set()

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()

    async def _on_disconnect(self, _error: Exception | None) -> None:
        self._state_publisher.mark_offline()


async def _run() -> None:
    config = EdgeConfig.from_env()
    _configure_logging(config.log_level)
    gateway = EdgeGateway(config)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.create_task(gateway.stop()))
        except NotImplementedError:
            # Signals are not available on all platforms (e.g. Windows Proactor).
            pass

    await gateway.start()
    await gateway.wait_for_shutdown()


def main() -> None:
    try:
        asyncio.run(_run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
