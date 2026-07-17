# Function: JetStream integration tests for the edge gateway
# Time: 2026-07-09
# Author: AxeXie
"""Tests for JetStream context creation, stream management, durable consumers
and message acknowledgement (Task 8 Part 1)."""

from __future__ import annotations

import json
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from opengeobot_edge.command_handler import (
    CommandHandler,
    CommandType,
    EdgeCommand,
    SkillExecutionResponse,
)
from opengeobot_edge.config import EdgeConfig
from opengeobot_edge.nats_client import NatsBridge, NatsConnectionError
from opengeobot_safety_gateway.safety_state import SafetyStateMachine


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


def _make_mock_nc_with_js() -> tuple[MagicMock, MagicMock]:
    """Create a mock NATS client with a mock JetStream context."""
    mock_js = MagicMock()
    mock_js.add_stream = AsyncMock()
    mock_js.stream_info = AsyncMock()
    mock_js.update_stream = AsyncMock()
    mock_js.subscribe = AsyncMock(return_value=MagicMock())

    mock_nc = MagicMock()
    mock_nc.is_connected = True
    mock_nc.subscribe = AsyncMock(return_value=MagicMock())
    mock_nc.publish = AsyncMock()
    mock_nc.request = AsyncMock()
    mock_nc.drain = AsyncMock()
    mock_nc.jetstream = MagicMock(return_value=mock_js)

    return mock_nc, mock_js


class TestJetStreamContextCreation:
    """Verify the JetStream context is created after connecting."""

    async def test_connect_creates_jetstream_context(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc, mock_js = _make_mock_nc_with_js()
        mock_js.stream_info = AsyncMock(side_effect=Exception("not found"))
        mock_js.add_stream = AsyncMock()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        assert bridge.has_jetstream is False

        await bridge.connect()

        assert bridge.has_jetstream is True
        mock_nc.jetstream.assert_called_once()

    async def test_jetstream_property_raises_before_connect(self) -> None:
        config = _make_config()
        bridge = NatsBridge(config)

        with pytest.raises(NatsConnectionError, match="not available"):
            _ = bridge.jetstream

    async def test_jetstream_property_returns_context(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc, mock_js = _make_mock_nc_with_js()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        assert bridge.jetstream is mock_js


class TestStreamManagement:
    """Verify the edge stream is created or reused."""

    async def test_creates_stream_when_not_found(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        from nats.js.errors import NotFoundError

        config = _make_config()
        mock_nc, mock_js = _make_mock_nc_with_js()
        # stream_info raises NotFoundError -> stream should be created.
        mock_js.stream_info = AsyncMock(side_effect=NotFoundError("no stream"))
        mock_js.add_stream = AsyncMock()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        mock_js.add_stream.assert_called_once()
        call_kwargs = mock_js.add_stream.call_args.kwargs
        assert call_kwargs["name"] == config.jetstream_stream_name
        assert call_kwargs["subjects"] == [
            "opengeobot.dev.edge.command.*",
            "opengeobot.dev.edge.state.*",
            "opengeobot.dev.edge.reconcile.*",
        ]

    async def test_does_not_create_stream_when_exists(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc, mock_js = _make_mock_nc_with_js()
        # stream_info succeeds -> stream already exists, should NOT add_stream.
        mock_js.stream_info = AsyncMock(return_value=MagicMock())
        mock_js.add_stream = AsyncMock()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        mock_js.update_stream.assert_called_once()
        call_kwargs = mock_js.update_stream.call_args.kwargs
        assert call_kwargs["name"] == config.jetstream_stream_name
        assert call_kwargs["subjects"] == [
            "opengeobot.dev.edge.command.*",
            "opengeobot.dev.edge.state.*",
            "opengeobot.dev.edge.reconcile.*",
        ]
        mock_js.add_stream.assert_not_called()

    async def test_custom_stream_name_from_config(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        from nats.js.errors import NotFoundError

        config = _make_config(
            jetstream_stream_name="CUSTOM_STREAM",
            jetstream_stream_subjects="custom.edge.>",
        )
        mock_nc, mock_js = _make_mock_nc_with_js()
        mock_js.stream_info = AsyncMock(side_effect=NotFoundError("no stream"))
        mock_js.add_stream = AsyncMock()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        call_kwargs = mock_js.add_stream.call_args.kwargs
        assert call_kwargs["name"] == "CUSTOM_STREAM"
        assert call_kwargs["subjects"] == ["custom.edge.>"]

    async def test_jetstream_degraded_when_server_unavailable(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """If JetStream is not available, the bridge should still work."""
        config = _make_config()
        mock_nc, mock_js = _make_mock_nc_with_js()
        # stream_info raises a generic error (JetStream not enabled).
        mock_js.stream_info = AsyncMock(side_effect=RuntimeError("JetStream not enabled"))
        mock_js.add_stream = AsyncMock()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        # Should not raise even if JetStream is unavailable.
        await bridge.connect()
        # JetStream context is still created (just stream creation failed).
        assert bridge.has_jetstream is True


class TestSubscribeJetStream:
    """Verify the JetStream durable consumer subscription."""

    async def test_subscribe_jetstream_before_connect_raises(self) -> None:
        config = _make_config()
        bridge = NatsBridge(config)

        async def handler(msg: Any) -> None:
            pass

        with pytest.raises(NatsConnectionError, match="not available"):
            await bridge.subscribe_jetstream("subject", "durable", handler)

    async def test_subscribe_jetstream_delegates_to_js(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc, mock_js = _make_mock_nc_with_js()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        async def handler(msg: Any) -> None:
            pass

        await bridge.subscribe_jetstream("test.subject", "durable-1", handler)

        mock_js.subscribe.assert_called_once_with(
            "test.subject", durable="durable-1", cb=handler
        )

    async def test_subscribe_jetstream_uses_consumer_name(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config(robot_id="rbt_js_001")
        mock_nc, mock_js = _make_mock_nc_with_js()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()

        async def handler(msg: Any) -> None:
            pass

        await bridge.subscribe_jetstream(
            config.command_subject, config.jetstream_consumer_name, handler
        )

        call_kwargs = mock_js.subscribe.call_args.kwargs
        assert call_kwargs["durable"] == "edge-cmd-rbt_js_001"

    async def test_drain_and_close_clears_jetstream(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        config = _make_config()
        mock_nc, mock_js = _make_mock_nc_with_js()
        monkeypatch.setattr("nats.connect", AsyncMock(return_value=mock_nc))

        bridge = NatsBridge(config)
        await bridge.connect()
        assert bridge.has_jetstream is True

        await bridge.drain_and_close()

        assert bridge.has_jetstream is False


class TestJetStreamMessageAck:
    """Verify JetStream messages are acked after processing."""

    async def test_handle_command_acks_jetstream_message(self) -> None:
        """A JetStream message should be acked after successful processing."""
        config = _make_config()
        nats = _AckMockNats()
        state_pub = _AckMockStatePublisher()
        cache = _AckMockOfflineCache()
        safety = SafetyStateMachine()
        handler = CommandHandler(config, nats, state_pub, cache, safety_state=safety)  # type: ignore[arg-type]

        msg = _AckMockMsg(
            data=json.dumps(
                {
                    "command_id": "cmd_js_001",
                    "trace_id": "trace_js_001",
                    "command_type": "start_mission",
                    "mission_id": "mission_js_001",
                }
            ).encode("utf-8")
        )

        await handler.handle_command(msg)

        assert msg.acked is True

    async def test_handle_command_acks_on_malformed_payload(self) -> None:
        """Malformed messages should still be acked to prevent redelivery."""
        config = _make_config()
        nats = _AckMockNats()
        state_pub = _AckMockStatePublisher()
        cache = _AckMockOfflineCache()
        safety = SafetyStateMachine()
        handler = CommandHandler(config, nats, state_pub, cache, safety_state=safety)  # type: ignore[arg-type]

        msg = _AckMockMsg(data=b"not-json")
        await handler.handle_command(msg)

        assert msg.acked is True

    async def test_handle_command_acks_on_safety_refused(self) -> None:
        """Commands refused by safety should still be acked."""
        config = _make_config()
        nats = _AckMockNats()
        state_pub = _AckMockStatePublisher()
        cache = _AckMockOfflineCache()
        safety = SafetyStateMachine()
        handler = CommandHandler(config, nats, state_pub, cache, safety_state=safety)  # type: ignore[arg-type]

        # Latch the safety state.
        await safety.trigger_emergency_stop()

        msg = _AckMockMsg(
            data=json.dumps(
                {
                    "command_id": "cmd_js_002",
                    "trace_id": "trace_js_002",
                    "command_type": "execute_skill",
                    "skill_id": "move_forward",
                }
            ).encode("utf-8")
        )
        await handler.handle_command(msg)

        assert msg.acked is True

    async def test_plain_nats_message_no_ack_call(self) -> None:
        """A plain NATS message (no ack method) should not crash."""
        config = _make_config()
        nats = _AckMockNats()
        state_pub = _AckMockStatePublisher()
        cache = _AckMockOfflineCache()
        safety = SafetyStateMachine()
        handler = CommandHandler(config, nats, state_pub, cache, safety_state=safety)  # type: ignore[arg-type]

        # Plain NATS message without ack method.
        msg = type("PlainMsg", (), {"data": json.dumps(
            {
                "command_id": "cmd_plain_001",
                "trace_id": "trace_plain_001",
                "command_type": "start_mission",
                "mission_id": "mission_plain_001",
            }
        ).encode("utf-8")})()

        # Should not raise.
        await handler.handle_command(msg)


# ---------------------------------------------------------------------------
# Mock helpers for JetStream tests.
# ---------------------------------------------------------------------------


class _AckMockMsg:
    """Mock message with a JetStream-style ack()."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply
        self.acked = False

    async def ack(self) -> None:
        self.acked = True


class _AckMockNats:
    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []
        self._reply_data: bytes | None = None
        self._connected = True

    def set_reply(self, data: bytes) -> None:
        self._reply_data = data

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        if self._reply_data is None:
            raise RuntimeError("No reply data configured")
        return type("Reply", (), {"data": self._reply_data})()

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return self._connected


class _AckMockStatePublisher:
    def __init__(self) -> None:
        self.published: list[dict[str, Any]] = []

    def mark_offline(self) -> None:
        pass

    def mark_online(self) -> None:
        pass

    async def publish_state(
        self,
        trace_id: str,
        last_command_id: str | None = None,
        command_result: Any = None,
    ) -> None:
        self.published.append(
            {"trace_id": trace_id, "last_command_id": last_command_id}
        )

    async def start_heartbeat(self) -> None:
        pass

    async def stop_heartbeat(self) -> None:
        pass


class _AckMockOfflineCache:
    def __init__(self) -> None:
        self.pending_commands: dict[str, dict[str, Any]] = {}
        self.done_commands: list[str] = []

    async def add_pending_command(self, command: dict[str, Any]) -> None:
        cmd_id = command.get("command_id")
        if cmd_id and cmd_id not in self.pending_commands:
            self.pending_commands[cmd_id] = command

    async def mark_command_done(self, command_id: str) -> None:
        self.pending_commands.pop(command_id, None)
        self.done_commands.append(command_id)

    async def pending_commands_list(self) -> list[dict[str, Any]]:
        return list(self.pending_commands.values())
