# Function: NATS bridge unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the NATS connection bridge (F-EDGE-001)."""

from __future__ import annotations

import asyncio
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from opengeobot_edge.config import EdgeConfig
from opengeobot_edge.nats_client import NatsBridge, NatsConnectionError


def _make_config(**overrides: Any) -> EdgeConfig:
    defaults: dict[str, Any] = {
        "robot_id": "rbt_test",
        "gateway_id": "edge_01",
        "nats_url": "nats://localhost:4222",
        "nats_max_reconnect": -1,
        "nats_reconnect_wait": 2.0,
        "nats_connect_timeout": 5.0,
        "cloud_api_base_url": "http://localhost:8080",
        "state_publish_interval": 5.0,
        "skill_request_timeout": 10.0,
        "offline_cache_path": "",
        "log_level": "DEBUG",
    }
    defaults.update(overrides)
    return EdgeConfig(**defaults)


def _make_mock_nc() -> MagicMock:
    nc = MagicMock()
    nc.is_connected = True
    nc.subscribe = AsyncMock(return_value=MagicMock())
    nc.publish = AsyncMock()
    nc.request = AsyncMock()
    nc.drain = AsyncMock()
    # Mock JetStream context so _init_jetstream succeeds in tests.
    mock_js = MagicMock()
    mock_js.stream_info = AsyncMock(return_value=MagicMock())  # stream exists
    mock_js.add_stream = AsyncMock()
    mock_js.subscribe = AsyncMock(return_value=MagicMock())
    nc.jetstream = MagicMock(return_value=mock_js)
    return nc


class TestConnect:
    async def test_connect_sets_connected_state(self, monkeypatch: pytest.MonkeyPatch) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        assert bridge.is_connected is False

        await bridge.connect()

        assert bridge.is_connected is True
        mock_nc.subscribe.assert_not_called()

    async def test_connect_passes_config_to_nats(self, monkeypatch: pytest.MonkeyPatch) -> None:
        config = _make_config(
            nats_url="nats://custom:4222",
            nats_max_reconnect=5,
            nats_reconnect_wait=1.5,
            nats_connect_timeout=3.0,
        )
        mock_nc = _make_mock_nc()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        bridge = NatsBridge(config)
        await bridge.connect()

        mock_connect.assert_called_once()
        call_kwargs = mock_connect.call_args.kwargs
        assert call_kwargs["servers"] == "nats://custom:4222"
        assert call_kwargs["max_reconnect_attempts"] == 5
        assert call_kwargs["reconnect_time_wait"] == 1.5
        assert call_kwargs["connect_timeout"] == 3.0
        assert call_kwargs["allow_reconnect"] is True
        assert "edge-gateway-rbt_test" == call_kwargs["name"]

    async def test_connect_registers_callbacks(self, monkeypatch: pytest.MonkeyPatch) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        bridge = NatsBridge(config)
        await bridge.connect()

        call_kwargs = mock_connect.call_args.kwargs
        assert call_kwargs["disconnected_cb"] is not None
        assert call_kwargs["reconnected_cb"] is not None
        assert call_kwargs["closed_cb"] is not None
        assert call_kwargs["error_cb"] is not None


class TestReconnectCallbacks:
    async def test_disconnected_callback_clears_event_and_invokes_hook(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        bridge = NatsBridge(config)
        hook_called = False

        async def on_disconnect(error: Exception | None) -> None:
            nonlocal hook_called
            hook_called = True

        bridge.on_disconnect = on_disconnect
        await bridge.connect()

        # Simulate the real NATS client reporting disconnect.
        mock_nc.is_connected = False
        await mock_connect.call_args.kwargs["disconnected_cb"]()

        assert hook_called is True
        # The _connected event is cleared, so wait_for_connection times out.
        result = await bridge.wait_for_connection(timeout=0.05)
        assert result is False

    async def test_reconnected_callback_sets_connected_and_invokes_hook(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        bridge = NatsBridge(config)
        hook_called = False

        async def on_reconnect() -> None:
            nonlocal hook_called
            hook_called = True

        bridge.on_reconnect = on_reconnect
        await bridge.connect()

        # Simulate a disconnect then reconnect.
        mock_nc.is_connected = False
        await mock_connect.call_args.kwargs["disconnected_cb"]()
        assert hook_called is False  # disconnect hook not set

        mock_nc.is_connected = True
        await mock_connect.call_args.kwargs["reconnected_cb"]()

        assert bridge.is_connected is True
        assert hook_called is True

    async def test_disconnect_hook_exception_does_not_propagate(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        bridge = NatsBridge(config)

        async def on_disconnect(error: Exception | None) -> None:
            raise RuntimeError("hook crash")

        bridge.on_disconnect = on_disconnect
        await bridge.connect()

        mock_nc.is_connected = False
        # Should not raise.
        await mock_connect.call_args.kwargs["disconnected_cb"]()

    async def test_reconnect_hook_exception_does_not_propagate(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        bridge = NatsBridge(config)

        async def on_reconnect() -> None:
            raise RuntimeError("hook crash")

        bridge.on_reconnect = on_reconnect
        await bridge.connect()

        # Should not raise.
        await mock_connect.call_args.kwargs["reconnected_cb"]()

    async def test_closed_callback_clears_connection(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        bridge = NatsBridge(config)
        await bridge.connect()

        mock_nc.is_connected = False
        await mock_connect.call_args.kwargs["closed_cb"]()

        assert bridge.is_connected is False
        result = await bridge.wait_for_connection(timeout=0.05)
        assert result is False


class TestSubscribe:
    async def test_subscribe_before_connect_raises(self) -> None:
        config = _make_config()
        bridge = NatsBridge(config)

        async def handler(msg: Any) -> None:
            pass

        with pytest.raises(NatsConnectionError, match="not connected"):
            await bridge.subscribe("subject", handler)

    async def test_subscribe_delegates_to_nats(self, monkeypatch: pytest.MonkeyPatch) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        async def handler(msg: Any) -> None:
            pass

        await bridge.subscribe("test.subject", handler, queue="queue1")

        mock_nc.subscribe.assert_called_once_with(
            "test.subject", cb=handler, queue="queue1"
        )


class TestPublish:
    async def test_publish_before_connect_raises(self) -> None:
        config = _make_config()
        bridge = NatsBridge(config)
        with pytest.raises(NatsConnectionError, match="not connected"):
            await bridge.publish("subject", b"data")

    async def test_publish_delegates_to_nats(self, monkeypatch: pytest.MonkeyPatch) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        await bridge.publish("test.subject", b"payload")

        mock_nc.publish.assert_called_once_with("test.subject", b"payload")


class TestRequest:
    async def test_request_before_connect_raises(self) -> None:
        config = _make_config()
        bridge = NatsBridge(config)
        with pytest.raises(NatsConnectionError, match="not connected"):
            await bridge.request("subject", b"data", timeout=5.0)

    async def test_request_delegates_to_nats(self, monkeypatch: pytest.MonkeyPatch) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        mock_reply = MagicMock()
        mock_reply.data = b"reply-data"
        mock_nc.request = AsyncMock(return_value=mock_reply)
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        reply = await bridge.request("test.subject", b"payload", timeout=3.0)

        mock_nc.request.assert_called_once_with("test.subject", b"payload", timeout=3.0)
        assert reply.data == b"reply-data"


class TestWaitForConnection:
    async def test_returns_true_when_already_connected(self, monkeypatch: pytest.MonkeyPatch) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        result = await bridge.wait_for_connection(timeout=0.1)
        assert result is True

    async def test_returns_false_on_timeout(self) -> None:
        config = _make_config()
        bridge = NatsBridge(config)
        # _connected event is never set because connect() was never called.
        result = await bridge.wait_for_connection(timeout=0.05)
        assert result is False


class TestDrainAndClose:
    async def test_drain_and_close_calls_drain(self, monkeypatch: pytest.MonkeyPatch) -> None:
        config = _make_config()
        mock_nc = _make_mock_nc()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        await bridge.drain_and_close()

        mock_nc.drain.assert_called_once()
        assert bridge.is_connected is False

    async def test_drain_and_close_noop_when_not_connected(self) -> None:
        config = _make_config()
        bridge = NatsBridge(config)
        # Should not raise.
        await bridge.drain_and_close()

    async def test_drain_handles_connection_closed_error(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        from nats.errors import ConnectionClosedError

        config = _make_config()
        mock_nc = _make_mock_nc()
        mock_nc.drain = AsyncMock(side_effect=ConnectionClosedError)
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        # Should not raise.
        await bridge.drain_and_close()
        assert bridge.is_connected is False

    async def test_drain_handles_no_servers_error(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        from nats.errors import NoServersError

        config = _make_config()
        mock_nc = _make_mock_nc()
        mock_nc.drain = AsyncMock(side_effect=NoServersError)
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        # Should not raise.
        await bridge.drain_and_close()
        assert bridge.is_connected is False
