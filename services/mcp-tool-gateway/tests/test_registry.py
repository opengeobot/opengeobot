# Function: Tool registry unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for the ToolRegistry."""

from __future__ import annotations

import pytest

from opengeobot_mcp_gateway.registry import (
    ToolDefinition,
    ToolNotFoundError,
    ToolRegistry,
    ToolRegistration,
)


def _make_definition(tool_name: str = "get_status", version: str = "1.0.0") -> ToolDefinition:
    return ToolDefinition(
        tool_id=f"tid_{tool_name}",
        tool_name=tool_name,
        version=version,
        description="Get robot status",
        permission_code="robot.robot.read",
        risk_level="R0_READ_ONLY",
    )


def _make_registration(
    tool_name: str = "get_status",
    routing: str = "stable",
    backend_type: str = "remote",
    backend_subject: str = "opengeobot.tool.get_status.invoke",
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


async def _dummy_backend(params: dict) -> dict:
    return {"ok": True}


class TestRegister:
    async def test_register_stable(self):
        registry = ToolRegistry()
        reg = _make_registration(routing="stable")
        await registry.register(reg)
        entry = await registry.lookup("get_status")
        assert entry.stable is not None
        assert entry.canary is None

    async def test_register_canary(self):
        registry = ToolRegistry()
        await registry.register(_make_registration(routing="stable", canary_percentage=20))
        await registry.register(_make_registration(routing="canary", version="1.1.0"))
        entry = await registry.lookup("get_status")
        assert entry.stable is not None
        assert entry.canary is not None
        assert entry.canary_percentage == 20

    async def test_register_local_requires_backend(self):
        registry = ToolRegistry()
        reg = _make_registration(backend_type="local")
        with pytest.raises(ValueError, match="local_backend must be provided"):
            await registry.register(reg)

    async def test_register_local_with_backend(self):
        registry = ToolRegistry()
        reg = _make_registration(backend_type="local", backend_subject="")
        await registry.register(reg, local_backend=_dummy_backend)
        entry = await registry.lookup("get_status")
        assert entry.stable is not None

    async def test_register_updates_canary_percentage(self):
        registry = ToolRegistry()
        await registry.register(_make_registration(canary_percentage=10))
        entry = await registry.lookup("get_status")
        assert entry.canary_percentage == 10

        await registry.register(_make_registration(canary_percentage=50))
        entry = await registry.lookup("get_status")
        assert entry.canary_percentage == 50

    async def test_register_invalid_routing_raises(self):
        registry = ToolRegistry()
        reg = _make_registration(routing="invalid")
        with pytest.raises(ValueError, match="Unknown routing"):
            await registry.register(reg)


class TestUnregister:
    async def test_unregister_stable(self):
        registry = ToolRegistry()
        await registry.register(_make_registration(routing="stable"))
        removed = await registry.unregister("get_status", "stable")
        assert removed is True
        with pytest.raises(ToolNotFoundError):
            await registry.lookup("get_status")

    async def test_unregister_canary_only(self):
        registry = ToolRegistry()
        await registry.register(_make_registration(routing="stable"))
        await registry.register(_make_registration(routing="canary", version="1.1.0"))
        removed = await registry.unregister("get_status", "canary")
        assert removed is True
        # Stable should still be there.
        entry = await registry.lookup("get_status")
        assert entry.stable is not None
        assert entry.canary is None

    async def test_unregister_not_found(self):
        registry = ToolRegistry()
        removed = await registry.unregister("nonexistent", "stable")
        assert removed is False

    async def test_unregister_cleans_up_local_backend(self):
        registry = ToolRegistry()
        await registry.register(
            _make_registration(backend_type="local", backend_subject=""),
            local_backend=_dummy_backend,
        )
        await registry.unregister("get_status", "stable")
        assert registry.get_local_backend("get_status", "stable") is None


class TestListTools:
    async def test_list_empty(self):
        registry = ToolRegistry()
        entries = await registry.list_tools()
        assert len(entries) == 0

    async def test_list_single_tool(self):
        registry = ToolRegistry()
        await registry.register(_make_registration(routing="stable"))
        entries = await registry.list_tools()
        assert len(entries) == 1
        assert entries[0].tool_name == "get_status"
        assert entries[0].routing == "stable"

    async def test_list_multiple_tools_with_canary(self):
        registry = ToolRegistry()
        await registry.register(_make_registration(tool_name="tool_a", routing="stable"))
        await registry.register(_make_registration(tool_name="tool_a", routing="canary", version="2.0.0"))
        await registry.register(_make_registration(tool_name="tool_b", routing="stable"))
        entries = await registry.list_tools()
        assert len(entries) == 3
        names = {(e.tool_name, e.routing) for e in entries}
        assert ("tool_a", "stable") in names
        assert ("tool_a", "canary") in names
        assert ("tool_b", "stable") in names


class TestLookup:
    async def test_lookup_found(self):
        registry = ToolRegistry()
        await registry.register(_make_registration(routing="stable"))
        entry = await registry.lookup("get_status")
        assert entry is not None

    async def test_lookup_not_found(self):
        registry = ToolRegistry()
        with pytest.raises(ToolNotFoundError):
            await registry.lookup("nonexistent")


class TestLocalBackend:
    async def test_get_local_backend(self):
        registry = ToolRegistry()
        await registry.register(
            _make_registration(backend_type="local", backend_subject=""),
            local_backend=_dummy_backend,
        )
        backend = registry.get_local_backend("get_status", "stable")
        assert backend is not None
        result = await backend({"param": 1})
        assert result == {"ok": True}

    async def test_get_local_backend_missing(self):
        registry = ToolRegistry()
        assert registry.get_local_backend("nonexistent", "stable") is None
