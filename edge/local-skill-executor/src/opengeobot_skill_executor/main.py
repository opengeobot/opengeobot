# Function: Local skill executor asyncio entry point
# Time: 2026-07-06
# Author: AxeXie
"""Local skill executor runtime entry point.

Wires configuration, NATS connection, adapter client and the skill executor. The
executor subscribes to the Safety-Gateway-approved skill execution subject and
dispatches each request to the appropriate adapter (simulation / ROS2 / ROS1).

Run directly:
    python -m opengeobot_skill_executor.main
or:
    python src/opengeobot_skill_executor/main.py
"""

from __future__ import annotations

import asyncio
import signal
import sys

from loguru import logger

from .adapter_client import AdapterClient
from .config import ExecutorConfig
from .executor import SkillExecutor
from .nats_client import NatsBridge


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


class SkillExecutorService:
    """Top-level orchestrator for the local skill executor runtime."""

    def __init__(self, config: ExecutorConfig) -> None:
        self._config = config
        self._nats = NatsBridge(config)
        self._adapter_client = AdapterClient(config, self._nats)
        self._executor = SkillExecutor(config, self._nats, self._adapter_client)
        self._stop_event = asyncio.Event()

    @property
    def executor(self) -> SkillExecutor:
        return self._executor

    async def start(self) -> None:
        await self._nats.connect()
        # Use plain NATS (not JetStream) for the approved subject so that
        # the reply subject from the Safety Gateway's request-reply is
        # preserved, allowing the executor to return the SkillExecutionResponse.
        await self._nats.subscribe(
            self._config.skill_execute_approved_subject,
            self._executor.handle_approved_request,
        )
        logger.bind(gateway_id=self._config.gateway_id).info(
            "Skill executor started - subscribed to approved skill requests"
        )

    async def stop(self) -> None:
        logger.info("Skill executor stopping...")
        await self._nats.drain_and_close()
        self._stop_event.set()

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()


async def _run() -> None:
    config = ExecutorConfig.from_env()
    _configure_logging(config.log_level)
    service = SkillExecutorService(config)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.create_task(service.stop()))
        except NotImplementedError:
            # Signals are not available on all platforms (e.g. Windows Proactor).
            pass

    await service.start()
    await service.wait_for_shutdown()


def main() -> None:
    try:
        asyncio.run(_run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
