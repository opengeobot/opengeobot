# Function: NATS connection management for the local skill executor
# Time: 2026-07-06
# Author: AxeXie
"""NATS connection management with auto-reconnect and lifecycle hooks.

Reuses the connection pattern from ``opengeobot_edge.nats_client``: nats-py
provides native reconnect support and this wrapper wires its callbacks to
asyncio events so the executor can react to disconnect/reconnect gracefully.
"""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from typing import Any

import nats
from loguru import logger
from nats.aio.client import Client as NatsClient
from nats.aio.msg import Msg
from nats.errors import ConnectionClosedError, NoServersError
from nats.js.api import StorageType, StreamConfig

from .config import ExecutorConfig

# Type alias for an async message handler.
MsgHandler = Callable[[Msg], Awaitable[None]]


class NatsConnectionError(RuntimeError):
    """Raised when a NATS operation is attempted on a closed connection."""


class NatsBridge:
    """Manages a single NATS connection with reconnect-driven lifecycle hooks."""

    def __init__(self, config: ExecutorConfig) -> None:
        self._config = config
        self._nc: NatsClient | None = None
        self._js: Any = None
        self._connected = asyncio.Event()
        self._closed = asyncio.Event()
        # External hooks.
        self.on_reconnect: Callable[[], Awaitable[None]] | None = None
        self.on_disconnect: Callable[[Exception | None], Awaitable[None]] | None = None

    @property
    def is_connected(self) -> bool:
        return self._nc is not None and self._nc.is_connected

    async def connect(self) -> None:
        """Establish the NATS connection with reconnect callbacks enabled."""

        async def _disconnected_cb() -> None:
            self._connected.clear()
            logger.warning("NATS connection lost; skill executor entering standby")
            if self.on_disconnect is not None:
                try:
                    await self.on_disconnect(None)
                except Exception:  # noqa: BLE001 — hooks must not break the loop
                    logger.exception("on_disconnect hook raised")

        async def _reconnected_cb() -> None:
            self._connected.set()
            logger.info("NATS reconnected; skill executor resuming")
            if self.on_reconnect is not None:
                try:
                    await self.on_reconnect()
                except Exception:  # noqa: BLE001
                    logger.exception("on_reconnect hook raised")

        async def _closed_cb() -> None:
            self._connected.clear()
            self._closed.set()
            logger.error("NATS connection closed permanently")

        async def _error_cb(e: Exception) -> None:
            logger.bind(error=str(e)).warning("NATS async error")

        max_reconnect = self._config.nats_max_reconnect
        self._nc = await nats.connect(
            servers=self._config.nats_url,
            name=f"skill-executor-{self._config.gateway_id}",
            max_reconnect_attempts=max_reconnect,
            reconnect_time_wait=self._config.nats_reconnect_wait,
            connect_timeout=self._config.nats_connect_timeout,
            disconnected_cb=_disconnected_cb,
            reconnected_cb=_reconnected_cb,
            closed_cb=_closed_cb,
            error_cb=_error_cb,
            allow_reconnect=True,
        )
        self._connected.set()
        self._closed.clear()
        self._js = self._nc.jetstream()
        logger.info("NATS connected to {}", self._config.nats_url)

    async def subscribe(self, subject: str, handler: MsgHandler, queue: str | None = None) -> Any:
        """Subscribe with an async handler. Returns the subscription object."""
        if self._nc is None:
            raise NatsConnectionError("NATS client not connected")
        return await self._nc.subscribe(subject, cb=handler, queue=queue)

    async def ensure_stream(self, name: str, subjects: list[str]) -> None:
        """Create or update a JetStream stream covering the given subjects.

        Safe to call repeatedly - if the stream already exists, no action is
        taken. Failures are logged but do not prevent the executor from
        operating.
        """
        if self._js is None:
            logger.warning("JetStream not initialised; cannot ensure stream")
            return
        try:
            config = StreamConfig(
                name=name,
                subjects=subjects,
                storage=StorageType.FILE,
            )
            await self._js.add_stream(config=config)
            logger.bind(stream=name, subjects=subjects).info(
                "JetStream stream ensured"
            )
        except Exception as exc:  # noqa: BLE001 - stream creation failure must not crash executor
            logger.bind(stream=name, error=str(exc)).warning(
                "Failed to ensure JetStream stream; executor continues"
            )

    async def subscribe_jetstream(
        self,
        subject: str,
        handler: MsgHandler,
        durable: str,
        manual_ack: bool = True,
    ) -> Any:
        """Subscribe via a JetStream durable consumer with manual ack."""
        if self._js is None:
            raise NatsConnectionError("JetStream context not initialised")
        return await self._js.subscribe(
            subject,
            cb=handler,
            durable=durable,
            manual_ack=manual_ack,
        )

    async def publish(self, subject: str, data: bytes) -> None:
        """Publish a message; raises if the connection is permanently closed."""
        if self._nc is None:
            raise NatsConnectionError("NATS client not connected")
        await self._nc.publish(subject, data)

    async def request(self, subject: str, data: bytes, timeout: float) -> Msg:
        """Send a request and await the reply."""
        if self._nc is None:
            raise NatsConnectionError("NATS client not connected")
        return await self._nc.request(subject, data, timeout=timeout)

    async def wait_for_connection(self, timeout: float | None = None) -> bool:
        """Wait until the connection is ready. Returns False on timeout."""
        try:
            await asyncio.wait_for(self._connected.wait(), timeout=timeout)
            return True
        except asyncio.TimeoutError:
            return False

    async def drain_and_close(self) -> None:
        """Flush pending messages and close the connection gracefully."""
        if self._nc is None:
            return
        try:
            await self._nc.drain()
        except (ConnectionClosedError, NoServersError) as e:
            logger.bind(error=str(e)).debug("drain skipped (connection already closed)")
        finally:
            self._nc = None
            self._js = None
            self._connected.clear()
