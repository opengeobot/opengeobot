# Function: NATS connection management for the agent runtime
# Time: 2026-07-06
# Author: AxeXie
"""NATS connection management with auto-reconnect and lifecycle hooks.

Reuses the connection pattern from ``opengeobot_edge.nats_client``.

Includes JetStream durable consumer support for mission-critical subjects so
that messages survive consumer disconnect/reconnect scenarios.
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
from nats.js import api as js_api
from nats.js.client import JetStreamContext
from nats.js.errors import NotFoundError

from .config import AgentConfig

# Type alias for an async message handler.
MsgHandler = Callable[[Msg], Awaitable[None]]


class NatsConnectionError(RuntimeError):
    """Raised when a NATS operation is attempted on a closed connection."""


class NatsBridge:
    """Manages a single NATS connection with reconnect-driven lifecycle hooks.

    After connecting, a JetStream context is created and a durable stream is
    ensured so that mission-critical subjects (``opengeobot.agent.>``) are
    persisted.  Subscribers created via :meth:`subscribe_js` use durable
    consumers so messages survive disconnect/reconnect.
    """

    def __init__(self, config: AgentConfig) -> None:
        self._config = config
        self._nc: NatsClient | None = None
        self._js: JetStreamContext | None = None
        self._connected = asyncio.Event()
        self._closed = asyncio.Event()
        self.on_reconnect: Callable[[], Awaitable[None]] | None = None
        self.on_disconnect: Callable[[Exception | None], Awaitable[None]] | None = None

    @property
    def is_connected(self) -> bool:
        return self._nc is not None and self._nc.is_connected

    async def connect(self) -> None:
        """Establish the NATS connection with reconnect callbacks enabled."""

        async def _disconnected_cb() -> None:
            self._connected.clear()
            logger.warning("NATS connection lost; agent runtime in standby")
            if self.on_disconnect is not None:
                try:
                    await self.on_disconnect(None)
                except Exception:  # noqa: BLE001 — hooks must not break the loop
                    logger.exception("on_disconnect hook raised")

        async def _reconnected_cb() -> None:
            self._connected.set()
            logger.info("NATS reconnected; agent runtime resuming")
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
            name="agent-runtime",
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

    async def ensure_stream(self) -> None:
        """Create or verify the JetStream durable stream for agent subjects.

        The stream covers ``opengeobot.agent.>`` so that mission-critical
        messages are persisted and survive consumer disconnects.
        """
        if self._js is None:
            raise NatsConnectionError("JetStream context not initialised")
        subjects = [s.strip() for s in self._config.js_stream_subjects.split(",") if s.strip()]
        stream_config = js_api.StreamConfig(
            name=self._config.js_stream_name,
            subjects=subjects,
            storage=js_api.StorageType.FILE,
        )
        try:
            await self._js.stream_info(stream_config.name)
            await self._js.update_stream(config=stream_config)
            logger.info(
                "JetStream stream '{}' already exists (subjects={})",
                stream_config.name,
                subjects,
            )
        except NotFoundError:
            await self._js.add_stream(config=stream_config)
            logger.info(
                "JetStream stream '{}' created (subjects={})",
                stream_config.name,
                subjects,
            )

    async def subscribe(self, subject: str, handler: MsgHandler, queue: str | None = None) -> Any:
        """Subscribe with an async handler. Returns the subscription object."""
        if self._nc is None:
            raise NatsConnectionError("NATS client not connected")
        return await self._nc.subscribe(subject, cb=handler, queue=queue)

    async def subscribe_js(
        self,
        subject: str,
        handler: MsgHandler,
        durable: str,
        manual_ack: bool = True,
    ) -> Any:
        """Subscribe via JetStream durable consumer with manual ack.

        The durable consumer name ensures that messages are redelivered after
        a disconnect/reconnect.  When ``manual_ack`` is True the handler must
        call ``msg.ack()`` after processing.
        """
        if self._js is None:
            raise NatsConnectionError("JetStream context not initialised")
        return await self._js.subscribe(
            subject=subject,
            cb=handler,
            durable=durable,
            manual_ack=manual_ack,
        )

    @property
    def jetstream(self) -> JetStreamContext | None:
        """Return the JetStream context, or ``None`` before connect."""
        return self._js

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
