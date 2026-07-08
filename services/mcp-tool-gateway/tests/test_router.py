# Function: Tool router unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for the ToolRouter."""

from __future__ import annotations

import asyncio
import random
from typing import Any

import pytest

from opengeobot_mcp_gateway.config import GatewayConfig
from opengeobot_mcp_gateway.registry import (
    ToolDefinition,
    ToolRegistry,
    ToolRegistration,
)
from opengeobot_mcp_gateway.router import ToolInvocation, ToolRouter


def _make_config() -> GatewayConfig:
    return GatewayConfig(
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        tool_backend_timeout=30.0,
        log_level="DEBUG",
    )


def _make_definition(tool_name: str = "echo", version: str = "1.0.0") -> ToolDefinition:
    return ToolDefinition(
        tool_id=f"tid_{tool_name}",
        tool_name=tool_name,
        version=version,
        description="Echo tool",
        permission_code="mcp.tool.invoke",
        risk_level="R0_READ_ONLY",
        timeout_ms=5000,
    )


def _make_registration(
    tool_name: str = "echo",
    routing: str = "stable",
    backend_type: str = "local",
    backend_subject: str = "",
    canary_percentage: int = 0,
    version: str = "1.0.0",
) -> ToolRegistration:
    return ToolRegistration(
        definition=_make_definition(tool_name, version),
        routing=routing,
        backend_type=backend_type,
        backend_subject=backend_subject,
        canary_percentage=canary_percentage,
    )


class MockNats:
    """Records request-reply calls for testing remote backends."""

    def __init__(self) -> None:
        self.request_calls: list[tuple[str, bytes, float]] = []
        self._reply_data: bytes = b'{"result": "ok"}'

    def set_reply(self, data: bytes) -> None:
        self._reply_data = data

    async def publish(self, subject: str, data: bytes) -> None:
        pass

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        self.request_calls.append((subject, data, timeout))
        return _MockReply(self._reply_data)

    @property
    def is_connected(self) -> bool:
        return True


class _MockReply:
    def __init__(self, data: bytes) -> None:
        self.data = data


async def _echo_backend(params: dict[str, Any]) -> dict[str, Any]:
    return {"echoed": params}


async def _failing_backend(params: dict[str, Any]) -> dict[str, Any]:
    raise RuntimeError("Backend crashed")


async def _slow_backend(params: dict[str, Any]) -> dict[str, Any]:
    await asyncio.sleep(10)
    return {}


class TestSelectRouting:
    def test_no_canary_always_stable(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        entry = registry._tools.get("echo") or _make_entry()
        entry.stable = _make_registration(routing="stable")
        entry.canary = None
        entry.canary_percentage = 0

        for _ in range(10):
            assert router.select_routing(entry) == "stable"

    def test_100_percent_canary(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        entry = _make_entry()
        entry.stable = _make_registration(routing="stable")
        entry.canary = _make_registration(routing="canary", version="2.0.0")
        entry.canary_percentage = 100

        for _ in range(10):
            assert router.select_routing(entry) == "canary"

    def test_0_percent_canary(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        entry = _make_entry()
        entry.stable = _make_registration(routing="stable")
        entry.canary = _make_registration(routing="canary", version="2.0.0")
        entry.canary_percentage = 0

        for _ in range(10):
            assert router.select_routing(entry) == "stable"

    def test_50_percent_canary_approximately_even(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]
        router._rng = random.Random(42)

        entry = _make_entry()
        entry.stable = _make_registration(routing="stable")
        entry.canary = _make_registration(routing="canary", version="2.0.0")
        entry.canary_percentage = 50

        stable_count = 0
        canary_count = 0
        for _ in range(1000):
            if router.select_routing(entry) == "stable":
                stable_count += 1
            else:
                canary_count += 1

        # With 1000 samples and 50%, expect roughly 400-600 each.
        assert 400 < stable_count < 600
        assert 400 < canary_count < 600

    def test_no_stable_routes_to_canary(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        entry = _make_entry()
        entry.stable = None
        entry.canary = _make_registration(routing="canary", version="2.0.0")
        entry.canary_percentage = 0

        assert router.select_routing(entry) == "canary"


def _make_entry():
    from opengeobot_mcp_gateway.registry import ToolEntry

    return ToolEntry()


class TestInvoke:
    async def test_invoke_local_backend_success(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            _make_registration(routing="stable"),
            local_backend=_echo_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_001",
            trace_id="trace_001",
            tool_name="echo",
            input={"message": "hello"},
        )
        result = await router.invoke(invocation)

        assert result.success is True
        assert result.output == {"echoed": {"message": "hello"}}
        assert result.routing == "stable"

    async def test_invoke_unknown_tool(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        invocation = ToolInvocation(
            invocation_id="inv_002",
            trace_id="trace_002",
            tool_name="nonexistent",
            input={},
        )
        result = await router.invoke(invocation)

        assert result.success is False
        assert "not found" in (result.error or "")

    async def test_invoke_backend_error(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            _make_registration(routing="stable"),
            local_backend=_failing_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_003",
            trace_id="trace_003",
            tool_name="echo",
            input={},
        )
        result = await router.invoke(invocation)

        assert result.success is False
        assert "Backend crashed" in (result.error or "")

    async def test_invoke_backend_timeout(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            _make_registration(routing="stable"),
            local_backend=_slow_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_004",
            trace_id="trace_004",
            tool_name="echo",
            input={},
        )
        result = await router.invoke(invocation)

        assert result.success is False
        assert "timed out" in (result.error or "").lower()

    async def test_invoke_generates_invocation_id(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            _make_registration(routing="stable"),
            local_backend=_echo_backend,
        )

        invocation = ToolInvocation(
            trace_id="trace_005",
            tool_name="echo",
            input={},
        )
        result = await router.invoke(invocation)

        assert result.invocation_id.startswith("inv_")
        assert result.success is True

    async def test_invoke_logs_invocation(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            _make_registration(routing="stable"),
            local_backend=_echo_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_006",
            trace_id="trace_006",
            tool_name="echo",
            input={"key": "value"},
        )
        await router.invoke(invocation)

        assert len(router.invocation_log) == 1
        log = router.invocation_log[0]
        assert log.invocation_id == "inv_006"
        assert log.trace_id == "trace_006"
        assert log.tool_name == "echo"
        assert log.success is True
        assert log.input_summary == {"key": "value"}
        assert log.routing == "stable"

    async def test_invoke_remote_backend(self):
        config = _make_config()
        nats = MockNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            _make_registration(
                routing="stable",
                backend_type="remote",
                backend_subject="opengeobot.tool.echo.invoke",
            ),
        )

        nats.set_reply(b'{"result": "remote_ok"}')

        invocation = ToolInvocation(
            invocation_id="inv_007",
            trace_id="trace_007",
            tool_name="echo",
            input={"query": "test"},
        )
        result = await router.invoke(invocation)

        assert result.success is True
        assert result.output == {"result": "remote_ok"}
        assert len(nats.request_calls) == 1
        subject, _, _ = nats.request_calls[0]
        assert subject == "opengeobot.tool.echo.invoke"
