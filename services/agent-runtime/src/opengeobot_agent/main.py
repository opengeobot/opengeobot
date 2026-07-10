# Function: Agent runtime asyncio entry point
# Time: 2026-07-06
# Author: AxeXie
"""Agent runtime entry point.

Wires configuration, NATS connection, QwenPaw provider and the planning request
handler. The runtime subscribes to the mission planning request subject and
generates UNTRUSTED plan proposals via the QwenPaw API.

Run directly:
    python -m opengeobot_agent.main
or:
    python src/opengeobot_agent/main.py
"""

from __future__ import annotations

import asyncio
import signal
import sys

from loguru import logger

from .config import AgentConfig
from .handler import PlanningRequestHandler
from .nats_client import NatsBridge
from .provider import NatsSkillRegistry, QwenPawProvider


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


class AgentRuntime:
    """Top-level orchestrator for the agent runtime."""

    def __init__(self, config: AgentConfig) -> None:
        self._config = config
        self._nats = NatsBridge(config)
        skill_registry = NatsSkillRegistry(
            self._nats,
            subject=config.skill_list_subject,
            timeout=config.skill_request_timeout,
        )
        self._provider = QwenPawProvider(config, skill_registry=skill_registry)
        self._handler = PlanningRequestHandler(config, self._nats, self._provider)
        self._stop_event = asyncio.Event()

    async def start(self) -> None:
        await self._nats.connect()
        await self._nats.ensure_stream()
        await self._nats.subscribe_js(
            self._config.plan_request_subject,
            self._handler.handle_plan_request,
            durable=self._config.js_durable_consumer,
        )
        logger.info(
            "Agent runtime started - JetStream durable consumer '{}' "
            "subscribed to {}",
            self._config.js_durable_consumer,
            self._config.plan_request_subject,
        )

    async def stop(self) -> None:
        logger.info("Agent runtime stopping...")
        await self._nats.drain_and_close()
        self._stop_event.set()

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()


async def _run() -> None:
    config = AgentConfig.from_env()
    _configure_logging(config.log_level)
    runtime = AgentRuntime(config)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.create_task(runtime.stop()))
        except NotImplementedError:
            # Signals are not available on all platforms (e.g. Windows Proactor).
            pass

    await runtime.start()
    await runtime.wait_for_shutdown()


def main() -> None:
    try:
        asyncio.run(_run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
