# Function: JetStream persistence tests
# Time: 2026-07-09
# Author: AxeXie
"""Tests for JetStream durable consumer persistence.

These tests verify that messages survive a consumer disconnect/reconnect
scenario when using a JetStream durable consumer, and that the NatsBridge
correctly creates the stream and subscribes durably.
"""

from __future__ import annotations

import json
from typing import Any
from unittest.mock import AsyncMock, MagicMock

from nats.js import api as js_api
from nats.js.errors import NotFoundError

from opengeobot_mcp_gateway.config import GatewayConfig
from opengeobot_mcp_gateway.handler import ToolGatewayHandler
from opengeobot_mcp_gateway.nats_client import NatsBridge
from opengeobot_mcp_gateway.registry import (
    ToolDefinition,
    ToolRegistry,
    ToolRegistration,
)
from opengeobot_mcp_gateway.router import ToolRouter

# ------------------------------------------------------------------
# Mock JetStream objects.
# ------------------------------------------------------------------


class MockJSMsg:
    """Simulates a JetStream message for persistence testing."""

    def __init__(
        self,
        subject: str,
        data: bytes,
        reply: str,
        seq: int,
        ack_set: set[int] | None = None,
    ) -> None:
        self.subject = subject
        self.data = data
        self.reply = reply
        self._seq = seq
        self._ack_set = ack_set
        self._ackd = False

    async def ack(self) -> None:
        self._ackd = True
        if self._ack_set is not None:
            self._ack_set.add(self._seq)


class MockJetStreamContext:
    """Simulates a JetStream context with durable consumer persistence.

    Messages are stored in an internal list (the "stream").  Each durable
    consumer tracks which message sequences it has acked.  When a durable
    consumer subscribes, all unacked messages are delivered to the callback.
    """

    def __init__(self) -> None:
        self._messages: list[tuple[str, bytes, int]] = []
        self._streams: dict[str, Any] = {}
        self._acked: dict[str, set[int]] = {}
        self._next_seq = 0

    async def add_stream(
        self, config: js_api.StreamConfig | None = None, **params: Any
    ) -> Any:
        name = config.name if config else params.get("name")
        self._streams[name] = config

    async def stream_info(
        self, name: str, subjects_filter: str | None = None
    ) -> Any:
        if name not in self._streams:
            raise NotFoundError(f"stream {name} not found")
        return MagicMock(name=name)

    async def update_stream(
        self, config: js_api.StreamConfig | None = None, **params: Any
    ) -> Any:
        name = config.name if config else params.get("name")
        self._streams[name] = config

    async def publish(self, subject: str, data: bytes, **kwargs: Any) -> Any:
        self._next_seq += 1
        self._messages.append((subject, data, self._next_seq))
        return MagicMock(stream="MCP_STREAM", seq=self._next_seq)

    async def subscribe(
        self,
        subject: str | None = None,
        cb: Any = None,
        durable: str | None = None,
        stream: str | None = None,
        manual_ack: bool = False,
        **kwargs: Any,
    ) -> Any:
        if durable is not None:
            if durable not in self._acked:
                self._acked[durable] = set()
            ack_set = self._acked[durable]
        else:
            ack_set = None

        delivered: list[MockJSMsg] = []
        for msg_subject, msg_data, seq in self._messages:
            if ack_set is not None and seq in ack_set:
                continue
            reply = (
                f"$JS.ACK.MCP_STREAM.{durable}.{seq}" if durable else ""
            )
            msg = MockJSMsg(
                subject=msg_subject,
                data=msg_data,
                reply=reply,
                seq=seq,
                ack_set=ack_set,
            )
            if cb is not None:
                await cb(msg)
            delivered.append(msg)
        return delivered


# ------------------------------------------------------------------
# Config / helper factories.
# ------------------------------------------------------------------


def _make_config() -> GatewayConfig:
    return GatewayConfig(
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        tool_backend_timeout=30.0,
        log_level="DEBUG",
    )


# ------------------------------------------------------------------
# JetStream persistence tests.
# ------------------------------------------------------------------


class TestJetStreamPersistence:
    """Verify that messages survive consumer disconnect/reconnect."""

    async def test_unacked_message_redelivered_on_reconnect(self):
        """A message published while the consumer is offline must be
        redelivered when the durable consumer reconnects."""
        js = MockJetStreamContext()
        await js.add_stream(
            config=js_api.StreamConfig(
                name="MCP_STREAM",
                subjects=["opengeobot.mcp.>"],
            )
        )

        # Publish while no consumer is connected.
        await js.publish(
            "opengeobot.mcp.tool.invoke",
            b'{"tool_name": "survive_test"}',
        )

        # First consumer connects, receives but does NOT ack (crash).
        received_first: list[MockJSMsg] = []

        async def handler_first(msg: MockJSMsg) -> None:
            received_first.append(msg)

        await js.subscribe(
            subject="opengeobot.mcp.tool.invoke",
            cb=handler_first,
            durable="mcp-tool-gateway-consumer",
            manual_ack=True,
        )
        assert len(received_first) == 1
        assert received_first[0].data == b'{"tool_name": "survive_test"}'

        # Consumer "crashes" and reconnects with the same durable name.
        received_reconnect: list[MockJSMsg] = []

        async def handler_reconnect(msg: MockJSMsg) -> None:
            received_reconnect.append(msg)
            await msg.ack()

        await js.subscribe(
            subject="opengeobot.mcp.tool.invoke",
            cb=handler_reconnect,
            durable="mcp-tool-gateway-consumer",
            manual_ack=True,
        )
        assert len(received_reconnect) == 1
        assert received_reconnect[0].data == b'{"tool_name": "survive_test"}'

        # Reconnect again - message was acked, so it must NOT be redelivered.
        received_after_ack: list[MockJSMsg] = []

        async def handler_after_ack(msg: MockJSMsg) -> None:
            received_after_ack.append(msg)

        await js.subscribe(
            subject="opengeobot.mcp.tool.invoke",
            cb=handler_after_ack,
            durable="mcp-tool-gateway-consumer",
            manual_ack=True,
        )
        assert len(received_after_ack) == 0

    async def test_acked_message_not_redelivered(self):
        """An acked message must not be delivered to a reconnecting consumer."""
        js = MockJetStreamContext()
        await js.add_stream(
            config=js_api.StreamConfig(
                name="MCP_STREAM",
                subjects=["opengeobot.mcp.>"],
            )
        )

        await js.publish(
            "opengeobot.mcp.tool.invoke",
            b'{"tool_name": "ack_test"}',
        )

        # Consumer processes and acks.
        received: list[MockJSMsg] = []

        async def handler(msg: MockJSMsg) -> None:
            received.append(msg)
            await msg.ack()

        await js.subscribe(
            subject="opengeobot.mcp.tool.invoke",
            cb=handler,
            durable="mcp-tool-gateway-consumer",
            manual_ack=True,
        )
        assert len(received) == 1

        # Reconnect - the acked message must not be redelivered.
        received_reconnect: list[MockJSMsg] = []

        async def handler_reconnect(msg: MockJSMsg) -> None:
            received_reconnect.append(msg)

        await js.subscribe(
            subject="opengeobot.mcp.tool.invoke",
            cb=handler_reconnect,
            durable="mcp-tool-gateway-consumer",
            manual_ack=True,
        )
        assert len(received_reconnect) == 0

    async def test_multiple_messages_delivered_in_order(self):
        """Multiple messages published offline are delivered in order."""
        js = MockJetStreamContext()
        await js.add_stream(
            config=js_api.StreamConfig(
                name="MCP_STREAM",
                subjects=["opengeobot.mcp.>"],
            )
        )

        for i in range(3):
            await js.publish(
                "opengeobot.mcp.tool.invoke",
                f'{{"tool_name": "tool_{i}"}}'.encode(),
            )

        received: list[MockJSMsg] = []

        async def handler(msg: MockJSMsg) -> None:
            received.append(msg)
            await msg.ack()

        await js.subscribe(
            subject="opengeobot.mcp.tool.invoke",
            cb=handler,
            durable="mcp-tool-gateway-consumer",
            manual_ack=True,
        )

        assert len(received) == 3
        for i, msg in enumerate(received):
            assert f"tool_{i}" in msg.data.decode()


# ------------------------------------------------------------------
# NatsBridge JetStream wiring tests (mocked NATS connection).
# ------------------------------------------------------------------


class TestNatsBridgeJetStream:
    """Verify that NatsBridge creates the stream and subscribes durably."""

    async def test_ensure_stream_creates_when_missing(self):
        """ensure_stream should create the stream when it does not exist."""
        config = _make_config()
        bridge = NatsBridge(config)

        mock_js = MagicMock()
        mock_js.stream_info = AsyncMock(side_effect=NotFoundError("not found"))
        mock_js.add_stream = AsyncMock()

        bridge._js = mock_js  # type: ignore[attr-defined]
        await bridge.ensure_stream()

        mock_js.stream_info.assert_awaited_once_with(config.js_stream_name)
        mock_js.add_stream.assert_awaited_once()
        call_kwargs = mock_js.add_stream.call_args
        stream_config = call_kwargs.kwargs.get("config")
        assert stream_config is not None
        assert stream_config.name == config.js_stream_name
        assert "opengeobot.mcp.>" in stream_config.subjects

    async def test_ensure_stream_updates_when_exists(self):
        """ensure_stream should update the stream when it already exists."""
        config = _make_config()
        bridge = NatsBridge(config)

        mock_js = MagicMock()
        mock_js.stream_info = AsyncMock(return_value=MagicMock())
        mock_js.update_stream = AsyncMock()
        mock_js.add_stream = AsyncMock()

        bridge._js = mock_js  # type: ignore[attr-defined]
        await bridge.ensure_stream()

        mock_js.stream_info.assert_awaited_once_with(config.js_stream_name)
        mock_js.update_stream.assert_awaited_once()
        mock_js.add_stream.assert_not_awaited()

    async def test_subscribe_js_uses_durable_consumer(self):
        """subscribe_js should create a durable consumer subscription."""
        config = _make_config()
        bridge = NatsBridge(config)

        async def _handler(msg: Any) -> None:
            pass

        mock_js = MagicMock()
        mock_js.subscribe = AsyncMock()

        bridge._js = mock_js  # type: ignore[attr-defined]
        await bridge.subscribe_js(
            subject="opengeobot.mcp.tool.invoke",
            handler=_handler,
            durable="mcp-tool-gateway-consumer",
        )

        mock_js.subscribe.assert_awaited_once()
        call_kwargs = mock_js.subscribe.call_args.kwargs
        assert call_kwargs["subject"] == "opengeobot.mcp.tool.invoke"
        assert call_kwargs["durable"] == "mcp-tool-gateway-consumer"
        assert call_kwargs["manual_ack"] is True

    async def test_jetstream_property_returns_context(self):
        """The jetstream property should return the JS context after connect."""
        config = _make_config()
        bridge = NatsBridge(config)

        assert bridge.jetstream is None

        mock_js = MagicMock()
        bridge._js = mock_js  # type: ignore[attr-defined]
        assert bridge.jetstream is mock_js


# ------------------------------------------------------------------
# Handler ack integration tests.
# ------------------------------------------------------------------


class AckRecordingMsg:
    """Mimics a JetStream Msg that records whether ack was called."""

    def __init__(self, data: bytes, reply: str = "reply.subject") -> None:
        self.data = data
        self.reply = reply
        self.acked = False

    async def ack(self) -> None:
        self.acked = True


class MockNats:
    """Records publishes for verification."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return True


async def _echo_backend(params: dict[str, Any]) -> dict[str, Any]:
    return {"echoed": params}


class TestHandlerAck:
    """Verify that the handler acks JetStream messages after processing."""

    async def test_handler_acks_js_message_after_success(self):
        """The handler must call msg.ack() after successful processing."""
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            ToolRegistration(
                definition=ToolDefinition(
                    tool_id="tid_echo",
                    tool_name="echo",
                    version="1.0.0",
                    description="Echo",
                    permission_code="mcp.tool.invoke",
                    timeout_ms=30000,
                ),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_echo_backend,
        )

        handler = ToolGatewayHandler(config, nats, registry, router)  # type: ignore[arg-type]

        msg = AckRecordingMsg(
            data=json.dumps(
                {
                    "invocation_id": "inv_001",
                    "trace_id": "trace_001",
                    "tool_name": "echo",
                    "input": {"message": "hello"},
                }
            ).encode("utf-8"),
            reply="reply.subject",
        )
        await handler.handle_invoke(msg)  # type: ignore[arg-type]

        assert msg.acked is True

    async def test_handler_acks_js_message_on_malformed_payload(self):
        """The handler must ack even when the payload is malformed."""
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]
        handler = ToolGatewayHandler(config, nats, registry, router)  # type: ignore[arg-type]

        msg = AckRecordingMsg(data=b"not-json", reply="reply.subject")
        await handler.handle_invoke(msg)  # type: ignore[arg-type]

        assert msg.acked is True

    async def test_handler_acks_js_message_on_unknown_tool(self):
        """The handler must ack even when the tool is not found."""
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]
        handler = ToolGatewayHandler(config, nats, registry, router)  # type: ignore[arg-type]

        msg = AckRecordingMsg(
            data=json.dumps(
                {
                    "invocation_id": "inv_002",
                    "trace_id": "trace_002",
                    "tool_name": "nonexistent",
                    "input": {},
                }
            ).encode("utf-8"),
            reply="reply.subject",
        )
        await handler.handle_invoke(msg)  # type: ignore[arg-type]

        assert msg.acked is True
