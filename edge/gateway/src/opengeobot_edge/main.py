# Function: Edge gateway asyncio entry point (F-EDGE-001/002)
# Time: 2026-07-05
# Author: AxeXie
"""Edge gateway runtime entry point.

Wires configuration, NATS connection, JetStream durable consumer, command
handler, state publisher, offline cache and reconciler. The gateway subscribes
to the cloud command channel via a JetStream durable consumer (so commands are
not lost during transient disconnects), forwards skill executions to the local
skill executor (sim-adapter for M2), and publishes state back to the cloud.

Safety is enforced through a shared ``SafetyStateMachine`` (SM-SAFETY-001)
that is local-first and does not depend on the cloud or NATS. On NATS reconnect
the reconciler flushes cached states and reports pending commands; the JetStream
durable consumer resumes from the last acknowledged message.

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

from opengeobot_safety_gateway.safety_state import SafetyStateMachine

from .command_handler import CommandHandler
from .config import EdgeConfig
from .nats_client import NatsBridge, NatsConnectionError
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
        # Shared safety state machine (SM-SAFETY-001) - local-first, cloud-independent.
        self._safety_state = SafetyStateMachine()
        self._state_publisher = StatePublisher(
            config, self._nats, self._offline_cache, safety_state=self._safety_state
        )
        self._command_handler = CommandHandler(
            config,
            self._nats,
            self._state_publisher,
            self._offline_cache,
            safety_state=self._safety_state,
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

        # Subscribe to the command channel via a JetStream durable consumer.
        # The durable name ensures the consumer resumes from the last acked
        # message on reconnect, preventing command loss.
        await self._subscribe_command_channel()

        # Subscribe to safety state changes from the standalone Safety Gateway.
        await self._subscribe_safety_state_changes()

        await self._state_publisher.start_heartbeat()

        # Announce the edge is online.
        await self._state_publisher.publish_state(trace_id="edge-bootstrap")

        logger.bind(robot_id=self._config.robot_id).info(
            "Edge gateway started and subscribed to command channel via JetStream"
        )

    async def _subscribe_command_channel(self) -> None:
        """Subscribe to the command channel.

        Prefers a JetStream durable consumer so commands survive transient
        disconnects. Falls back to a plain NATS subscription if JetStream is
        not available (e.g. NATS server without JetStream enabled).
        """
        if self._nats.has_jetstream:
            try:
                await self._nats.subscribe_jetstream(
                    self._config.command_subject,
                    durable=self._config.jetstream_consumer_name,
                    handler=self._command_handler.handle_command,
                )
                logger.info(
                    "Subscribed to command channel via JetStream durable consumer '{}'",
                    self._config.jetstream_consumer_name,
                )
                return
            except NatsConnectionError:
                logger.warning(
                    "JetStream subscription failed; falling back to plain NATS"
                )
        # Fallback: plain NATS subscription (no durability).
        await self._nats.subscribe(
            self._config.command_subject, self._command_handler.handle_command
        )
        logger.info("Subscribed to command channel via plain NATS (no JetStream)")

    async def _subscribe_safety_state_changes(self) -> None:
        """Subscribe to safety state changes from the standalone Safety Gateway.

        When the Safety Gateway triggers an emergency stop or resets, the edge
        gateway syncs its local SafetyStateMachine latch so that motion commands
        are blocked until the reset is confirmed by the Safety Gateway.
        """
        await self._nats.subscribe(
            self._config.safety_state_changed_subject,
            self._handle_safety_state_change,
        )
        logger.info(
            "Subscribed to safety state changes on '{}'",
            self._config.safety_state_changed_subject,
        )

    async def _handle_safety_state_change(self, msg: object) -> None:
        """Sync local safety latch with Safety Gateway state changes."""
        import json

        from opengeobot_safety_gateway.safety_state import SafetyState

        raw = getattr(msg, "data", b"")
        try:
            payload = json.loads(raw)
            state_str = payload.get("state", "")
        except (json.JSONDecodeError, ValueError):
            logger.warning("Malformed safety state change event; ignoring")
            return

        if state_str == SafetyState.EMERGENCY_STOPPED.value:
            await self._safety_state.trigger_emergency_stop(
                reason="Safety Gateway emergency stop broadcast",
                trace_id=payload.get("trace_id", ""),
            )
            logger.warning("Safety Gateway triggered emergency stop; local latch engaged")
        elif state_str == SafetyState.NORMAL.value:
            if not self._safety_state.is_safe():
                await self._safety_state.request_reset(
                    trace_id=payload.get("trace_id", "")
                )
                await self._safety_state.complete_reset(
                    trace_id=payload.get("trace_id", "")
                )
                logger.info("Safety Gateway reset; local latch cleared")

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
