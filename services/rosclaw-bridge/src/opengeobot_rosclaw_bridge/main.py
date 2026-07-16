# Function: ROSClaw NATS Bridge asyncio entry point
# Time: 2026-07-16
# Author: AxeXie
"""ROSClaw bridge runtime entry point.

Run directly:
    python -m opengeobot_rosclaw_bridge.main
or:
    python src/opengeobot_rosclaw_bridge/main.py
"""

from __future__ import annotations

import asyncio
import signal
import sys

from loguru import logger

from .bridge import RosclawBridge
from .config import BridgeConfig
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


async def _run() -> None:
    config = BridgeConfig.from_env()
    _configure_logging(config.log_level)

    nats = NatsBridge(config)
    bridge = RosclawBridge(config)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.create_task(bridge.stop()))
        except NotImplementedError:
            # signal handlers are not available on all platforms (e.g. Windows)
            pass

    await bridge.start(nats)
    await bridge.wait_for_shutdown()


def main() -> None:
    try:
        asyncio.run(_run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
