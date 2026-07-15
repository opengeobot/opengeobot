# Function: ROS2 adapter asyncio entry point (F-ADAPTER-003)
# Time: 2026-07-15
# Author: AxeXie
"""ROS2 adapter runtime entry point.

Initialises rclpy, creates the adapter, and handles graceful shutdown.

Run directly:
    python -m opengeobot_ros2.main
or:
    python src/opengeobot_ros2/main.py
"""

from __future__ import annotations

import asyncio
import signal
import sys

from loguru import logger

from .adapter import Ros2Adapter
from .config import Ros2Config
from .skills.base import RCLPY_AVAILABLE, rclpy


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
    config = Ros2Config.from_env()
    _configure_logging(config.log_level)

    # Initialise rclpy before creating the adapter (which creates a Node).
    if RCLPY_AVAILABLE and rclpy is not None:
        rclpy.init()

    adapter = Ros2Adapter(config)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.create_task(adapter.stop()))
        except NotImplementedError:
            pass

    await adapter.start()
    await adapter.wait_for_shutdown()

    # Shutdown rclpy after the adapter has stopped.
    if RCLPY_AVAILABLE and rclpy is not None:
        rclpy.shutdown()


def main() -> None:
    try:
        asyncio.run(_run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
