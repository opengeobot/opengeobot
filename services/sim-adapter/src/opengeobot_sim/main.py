# Function: Simulation adapter asyncio entry point (F-ADAPTER-001)
# Time: 2026-07-05
# Author: AxeXie
"""Simulation adapter runtime entry point.

Run directly:
    python -m opengeobot_sim.main
or:
    python src/opengeobot_sim/main.py
"""

from __future__ import annotations

import asyncio
import signal
import sys

from loguru import logger

from .adapter import SimAdapter
from .config import SimConfig


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


async def _run() -> None:
    config = SimConfig.from_env()
    _configure_logging(config.log_level)
    adapter = SimAdapter(config)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.create_task(adapter.stop()))
        except NotImplementedError:
            pass

    await adapter.start()
    await adapter.wait_for_shutdown()


def main() -> None:
    try:
        asyncio.run(_run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
