# Function: Safety Gateway asyncio entry point (F-SAFETY-001 edge)
# Time: 2026-07-06
# Author: AxeXie
"""Safety Gateway runtime entry point.

Wires configuration, NATS connection, safety state machine, safety checker and
handler. The gateway subscribes to emergency stop, reset and skill execution
interception subjects. A minimal health check HTTP endpoint exposes the current
safety state for orchestration.

The safety state machine and safety checker operate **entirely locally** and
do not depend on NATS or the cloud. If NATS is unavailable the gateway still
latches emergency stops and blocks skill execution — only the state change
broadcast is skipped (with a warning).

Run directly:
    python -m opengeobot_safety_gateway.main
or:
    python src/opengeobot_safety_gateway/main.py
"""

from __future__ import annotations

import asyncio
import signal
import sys

from aiohttp import web
from loguru import logger

from .config import SafetyGatewayConfig
from .handler import SafetyHandler
from .nats_client import NatsBridge
from .safety_checker import SafetyChecker
from .safety_state import SafetyStateMachine


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


class SafetyGateway:
    """Top-level orchestrator for the edge Safety Gateway runtime."""

    def __init__(self, config: SafetyGatewayConfig) -> None:
        self._config = config
        self._nats = NatsBridge(config)
        self._state_machine = SafetyStateMachine()
        self._safety_checker = SafetyChecker(config)
        self._handler = SafetyHandler(
            config, self._nats, self._state_machine, self._safety_checker
        )
        self._stop_event = asyncio.Event()
        self._health_runner: web.AppRunner | None = None

    @property
    def state_machine(self) -> SafetyStateMachine:
        return self._state_machine

    @property
    def handler(self) -> SafetyHandler:
        return self._handler

    async def start(self) -> None:
        """Connect to NATS, subscribe to subjects, and start the health check."""
        await self._nats.connect()

        await self._nats.ensure_stream(
            self._config.jetstream_stream_name,
            self._config.jetstream_stream_subjects,
        )

        await self._nats.subscribe(
            self._config.emergency_stop_subject,
            self._handler.handle_emergency_stop,
        )
        await self._nats.subscribe(
            self._config.reset_subject,
            self._handler.handle_reset,
        )
        await self._nats.subscribe_jetstream(
            self._config.skill_execute_subject,
            self._handler.handle_skill_execute,
            durable=self._config.jetstream_durable_name,
        )

        await self._start_health_check()

        logger.bind(gateway_id=self._config.gateway_id).info(
            "Safety Gateway started — subscribed to safety and skill subjects"
        )

    async def stop(self) -> None:
        """Graceful shutdown: stop health check, drain NATS."""
        logger.info("Safety Gateway stopping...")
        await self._stop_health_check()
        await self._nats.drain_and_close()
        self._stop_event.set()

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()

    # ------------------------------------------------------------------
    # Health check HTTP endpoint.
    # ------------------------------------------------------------------
    async def _start_health_check(self) -> None:
        app = web.Application()
        app.router.add_get("/health", self._health_handler)
        app.router.add_get("/ready", self._health_handler)
        self._health_runner = web.AppRunner(app)
        await self._health_runner.setup()
        site = web.TCPSite(self._health_runner, "0.0.0.0", self._config.health_check_port)
        await site.start()
        logger.info(
            "Health check endpoint listening on port {}", self._config.health_check_port
        )

    async def _stop_health_check(self) -> None:
        if self._health_runner is not None:
            await self._health_runner.cleanup()
            self._health_runner = None

    async def _health_handler(self, _request: web.Request) -> web.Response:
        snapshot = self._state_machine.get_snapshot()
        return web.json_response(
            {
                "status": "ok",
                "gateway_id": self._config.gateway_id,
                "safety_state": snapshot.state.value,
                "safe": snapshot.safe,
                "reason": snapshot.reason,
                "last_transition_at": snapshot.last_transition_at,
                "nats_connected": self._nats.is_connected,
            }
        )


async def _run() -> None:
    config = SafetyGatewayConfig.from_env()
    _configure_logging(config.log_level)
    gateway = SafetyGateway(config)

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
