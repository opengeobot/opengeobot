# Function: Tool gateway handler unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for the ToolGatewayHandler."""

from __future__ import annotations

import json
from typing import Any

import pytest

from opengeobot_mcp_gateway.config import GatewayConfig
from opengeobot_mcp_gateway.handler import ToolGatewayHandler
from opengeobot_mcp_gateway.registry import (
    ToolDefinition,
    ToolRegistry,
    ToolRegistration,
)
from opengeobot_mcp_gateway.router import ToolRouter


class MockMsg:
    """Mimics nats.aio.msg.Msg for testing."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply


class MockNats:
    """Records publishes and simulates request-reply for testing."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []
        self._reply_data: bytes = b'{}'

    def set_reply(self, data: bytes) -> None:
        self._reply_data = data

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        return _MockReply(self._reply_data)

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return True


class _MockReply:
    def __init__(self, data: bytes) -> None:
        self.data = data


def _make_config() -> GatewayConfig:
    return GatewayConfig(
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        tool_backend_timeout=30.0,
        log_level="DEBUG",
    )


def _make_handler() -> tuple[ToolGatewayHandler, MockNats, ToolRegistry, ToolRouter]:
    config = _make_config()
    nats = MockNats()
    registry = ToolRegistry()
    router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]
    handler = ToolGatewayHandler(config, nats, registry, router)  # type: ignore[arg-type]
    return handler, nats, registry, router


def _make_msg(data: dict[str, Any], reply: str = "reply.subject") -> MockMsg:
    return MockMsg(
        data=json.dumps(data).encode("utf-8"),
        reply=reply,
    )


def _published_on(nats: MockNats, subject: str) -> list[tuple[str, bytes]]:
    return [(s, d) for s, d in nats.published if s == subject]


def _make_tool_def(tool_name: str = "get_status", version: str = "1.0.0") -> dict[str, Any]:
    return {
        "tool_id": f"tid_{tool_name}",
        "tool_name": tool_name,
        "version": version,
        "description": "Get robot status",
        "input_schema": {},
        "output_schema": {},
        "permission_code": "robot.robot.read",
        "risk_level": "R0_READ_ONLY",
        "idempotent": True,
        "timeout_ms": 5000,
        "cancellable": True,
        "audit_level": "SUMMARY",
    }


class TestRegisterHandler:
    async def test_register_remote_tool(self):
        """Registering a remote tool should succeed."""
        handler, nats, registry, _ = _make_handler()

        msg = _make_msg({
            "definition": _make_tool_def("get_status"),
            "routing": "stable",
            "backend_type": "remote",
            "backend_subject": "opengeobot.tool.get_status.invoke",
            "canary_percentage": 0,
        })
        await handler.handle_register(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is True
        assert response["tool_name"] == "get_status"

        # Verify it was stored.
        entry = await registry.lookup("get_status")
        assert entry.stable is not None

    async def test_register_canary_version(self):
        """Registering a canary version should store it separately."""
        handler, nats, registry, _ = _make_handler()

        # First register stable.
        await handler.handle_register(_make_msg({
            "definition": _make_tool_def("get_status", "1.0.0"),
            "routing": "stable",
            "backend_type": "remote",
            "backend_subject": "opengeobot.tool.get_status.stable",
            "canary_percentage": 20,
        }))

        nats.published.clear()

        # Then register canary.
        await handler.handle_register(_make_msg({
            "definition": _make_tool_def("get_status", "2.0.0"),
            "routing": "canary",
            "backend_type": "remote",
            "backend_subject": "opengeobot.tool.get_status.canary",
        }))

        replies = _published_on(nats, "reply.subject")
        response = json.loads(replies[0][1])
        assert response["success"] is True
        assert response["routing"] == "canary"

        entry = await registry.lookup("get_status")
        assert entry.stable is not None
        assert entry.canary is not None

    async def test_malformed_register_payload(self):
        """Malformed registration payload should return an error."""
        handler, nats, _, _ = _make_handler()

        msg = MockMsg(data=b"not-json", reply="reply.subject")
        await handler.handle_register(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False

    async def test_register_no_reply(self):
        """Registration without a reply subject should not crash."""
        handler, nats, _, _ = _make_handler()

        msg = MockMsg(
            data=json.dumps({
                "definition": _make_tool_def("test"),
                "routing": "stable",
                "backend_type": "remote",
                "backend_subject": "subj",
            }).encode("utf-8"),
            reply="",
        )
        await handler.handle_register(msg)
        assert len(nats.published) == 0


class TestListHandler:
    async def test_list_empty(self):
        handler, nats, _, _ = _make_handler()

        msg = _make_msg({})
        await handler.handle_list(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["count"] == 0
        assert response["tools"] == []

    async def test_list_with_tools(self):
        handler, nats, registry, _ = _make_handler()

        # Register a tool first.
        await registry.register(
            ToolRegistration(
                definition=ToolDefinition(
                    tool_id="tid_a",
                    tool_name="tool_a",
                    version="1.0.0",
                    description="Tool A",
                    permission_code="mcp.tool.invoke",
                ),
                routing="stable",
                backend_type="remote",
                backend_subject="subj_a",
            ),
        )

        nats.published.clear()
        msg = _make_msg({})
        await handler.handle_list(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["count"] == 1
        assert response["tools"][0]["tool_name"] == "tool_a"


class TestInvokeHandler:
    async def test_invoke_unknown_tool(self):
        handler, nats, _, _ = _make_handler()

        msg = _make_msg({
            "invocation_id": "inv_001",
            "trace_id": "trace_001",
            "tool_name": "nonexistent",
            "input": {},
        })
        await handler.handle_invoke(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False
        assert "not found" in (response["error"] or "")

    async def test_invoke_malformed_payload(self):
        handler, nats, _, _ = _make_handler()

        msg = MockMsg(data=b"not-json", reply="reply.subject")
        await handler.handle_invoke(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False

    async def test_invoke_remote_tool(self):
        handler, nats, registry, _ = _make_handler()

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
                backend_type="remote",
                backend_subject="opengeobot.tool.echo",
            ),
        )

        nats.set_reply(b'{"echoed": true}')

        msg = _make_msg({
            "invocation_id": "inv_002",
            "trace_id": "trace_002",
            "tool_name": "echo",
            "input": {"msg": "hello"},
        })
        await handler.handle_invoke(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is True
        assert response["result"]["output"] == {"echoed": True}


class TestUnregisterHandler:
    async def test_unregister_existing(self):
        handler, nats, registry, _ = _make_handler()

        await registry.register(
            ToolRegistration(
                definition=ToolDefinition(
                    tool_id="tid_test",
                    tool_name="test_tool",
                    version="1.0.0",
                    description="Test",
                    permission_code="mcp.tool.invoke",
                ),
                routing="stable",
                backend_type="remote",
                backend_subject="subj",
            ),
        )

        msg = _make_msg({"tool_name": "test_tool", "routing": "stable"})
        await handler.handle_unregister(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is True

    async def test_unregister_not_found(self):
        handler, nats, _, _ = _make_handler()

        msg = _make_msg({"tool_name": "nonexistent", "routing": "stable"})
        await handler.handle_unregister(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["success"] is False
