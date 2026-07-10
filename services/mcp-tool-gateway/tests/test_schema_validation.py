# Function: Schema validation and audit persistence tests
# Time: 2026-07-09
# Author: AxeXie
"""Tests for input/output schema validation and NATS audit persistence.

Verifies that:
- Input validation failure prevents tool execution and returns an error.
- Output validation failure logs a warning but still returns the result.
- Audit events are published to the NATS audit subject.
"""

from __future__ import annotations

import json
from typing import Any

from opengeobot_mcp_gateway.config import GatewayConfig
from opengeobot_mcp_gateway.registry import (
    ToolDefinition,
    ToolRegistry,
    ToolRegistration,
)
from opengeobot_mcp_gateway.router import ToolInvocation, ToolRouter


# ------------------------------------------------------------------
# Test helpers.
# ------------------------------------------------------------------


class RecordingNats:
    """Records all publishes and simulates request-reply for testing."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []
        self._reply_data: bytes = b'{}'

    def set_reply(self, data: bytes) -> None:
        self._reply_data = data

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def request(self, subject: str, data: bytes, timeout: float) -> Any:
        return _MockReply(self._reply_data)

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


def _make_definition(
    tool_name: str = "validated_tool",
    input_schema: dict[str, Any] | None = None,
    output_schema: dict[str, Any] | None = None,
) -> ToolDefinition:
    return ToolDefinition(
        tool_id=f"tid_{tool_name}",
        tool_name=tool_name,
        version="1.0.0",
        description="A tool with schema validation",
        input_schema=input_schema or {},
        output_schema=output_schema or {},
        permission_code="mcp.tool.invoke",
        risk_level="R0_READ_ONLY",
        timeout_ms=5000,
    )


def _audit_events(nats: RecordingNats, config: GatewayConfig) -> list[bytes]:
    """Return the data payloads published to the audit subject."""
    return [data for subj, data in nats.published if subj == config.audit_subject]


# ------------------------------------------------------------------
# Input schema validation tests.
# ------------------------------------------------------------------


class TestInputSchemaValidation:
    """Verify that input schema validation rejects invalid input."""

    async def test_input_validation_missing_required_field(self):
        """Missing a required field should reject the invocation."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        input_schema = {
            "type": "object",
            "properties": {
                "name": {"type": "string"},
            },
            "required": ["name"],
        }

        await registry.register(
            ToolRegistration(
                definition=_make_definition(input_schema=input_schema),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_echo_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_001",
            trace_id="trace_001",
            tool_name="validated_tool",
            input={},
        )
        result = await router.invoke(invocation)

        assert result.success is False
        assert "input_schema_invalid" in (result.error or "")

    async def test_input_validation_wrong_type(self):
        """Wrong type for a property should reject the invocation."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        input_schema = {
            "type": "object",
            "properties": {
                "count": {"type": "integer"},
            },
            "required": ["count"],
        }

        await registry.register(
            ToolRegistration(
                definition=_make_definition(input_schema=input_schema),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_echo_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_002",
            trace_id="trace_002",
            tool_name="validated_tool",
            input={"count": "not-a-number"},
        )
        result = await router.invoke(invocation)

        assert result.success is False
        assert "input_schema_invalid" in (result.error or "")

    async def test_input_validation_passes_valid_input(self):
        """Valid input should pass validation and execute the tool."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        input_schema = {
            "type": "object",
            "properties": {
                "name": {"type": "string"},
            },
            "required": ["name"],
        }

        await registry.register(
            ToolRegistration(
                definition=_make_definition(input_schema=input_schema),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_echo_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_003",
            trace_id="trace_003",
            tool_name="validated_tool",
            input={"name": "test"},
        )
        result = await router.invoke(invocation)

        assert result.success is True
        assert result.output == {"echoed": {"name": "test"}}

    async def test_input_validation_failure_does_not_execute_tool(self):
        """When input validation fails, the tool backend must not be called."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        call_count = 0

        async def _counting_backend(params: dict[str, Any]) -> dict[str, Any]:
            nonlocal call_count
            call_count += 1
            return {"ok": True}

        input_schema = {
            "type": "object",
            "properties": {"x": {"type": "integer"}},
            "required": ["x"],
        }

        await registry.register(
            ToolRegistration(
                definition=_make_definition(input_schema=input_schema),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_counting_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_004",
            trace_id="trace_004",
            tool_name="validated_tool",
            input={},
        )
        result = await router.invoke(invocation)

        assert result.success is False
        assert call_count == 0


# ------------------------------------------------------------------
# Output schema validation tests.
# ------------------------------------------------------------------


class TestOutputSchemaValidation:
    """Verify that output schema validation logs but does not block."""

    async def test_output_validation_failure_still_returns_result(self):
        """When output validation fails, the result is still returned."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        output_schema = {
            "type": "object",
            "properties": {
                "result": {"type": "string"},
            },
            "required": ["result"],
        }

        async def _bad_output_backend(params: dict[str, Any]) -> dict[str, Any]:
            return {"result": 123}

        await registry.register(
            ToolRegistration(
                definition=_make_definition(output_schema=output_schema),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_bad_output_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_005",
            trace_id="trace_005",
            tool_name="validated_tool",
            input={},
        )
        result = await router.invoke(invocation)

        # The tool still succeeds despite output schema mismatch.
        assert result.success is True
        assert result.output == {"result": 123}

    async def test_output_validation_passes_valid_output(self):
        """Valid output should pass validation without warnings."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        output_schema = {
            "type": "object",
            "properties": {
                "status": {"type": "string"},
            },
            "required": ["status"],
        }

        async def _good_output_backend(params: dict[str, Any]) -> dict[str, Any]:
            return {"status": "ok"}

        await registry.register(
            ToolRegistration(
                definition=_make_definition(output_schema=output_schema),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_good_output_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_006",
            trace_id="trace_006",
            tool_name="validated_tool",
            input={},
        )
        result = await router.invoke(invocation)

        assert result.success is True
        assert result.output == {"status": "ok"}


# ------------------------------------------------------------------
# Audit persistence tests.
# ------------------------------------------------------------------


class TestAuditPersistence:
    """Verify that audit events are published via NATS."""

    async def test_audit_event_published_on_success(self):
        """A successful invocation must publish an audit event."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            ToolRegistration(
                definition=_make_definition(),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_echo_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_audit_001",
            trace_id="trace_audit_001",
            tool_name="validated_tool",
            input={"key": "value"},
        )
        await router.invoke(invocation)

        audit_payloads = _audit_events(nats, config)
        assert len(audit_payloads) == 1

        entry = json.loads(audit_payloads[0])
        assert entry["invocation_id"] == "inv_audit_001"
        assert entry["trace_id"] == "trace_audit_001"
        assert entry["tool_name"] == "validated_tool"
        assert entry["success"] is True
        assert entry["input_summary"] == {"key": "value"}

    async def test_audit_event_published_on_failure(self):
        """A failed invocation must also publish an audit event."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            ToolRegistration(
                definition=_make_definition(),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_failing_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_audit_002",
            trace_id="trace_audit_002",
            tool_name="validated_tool",
            input={},
        )
        await router.invoke(invocation)

        audit_payloads = _audit_events(nats, config)
        assert len(audit_payloads) == 1

        entry = json.loads(audit_payloads[0])
        assert entry["invocation_id"] == "inv_audit_002"
        assert entry["success"] is False
        assert "Backend crashed" in (entry.get("error") or "")

    async def test_audit_event_published_on_input_validation_failure(self):
        """An input validation failure must also publish an audit event."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        input_schema = {
            "type": "object",
            "properties": {"x": {"type": "integer"}},
            "required": ["x"],
        }

        await registry.register(
            ToolRegistration(
                definition=_make_definition(input_schema=input_schema),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_echo_backend,
        )

        invocation = ToolInvocation(
            invocation_id="inv_audit_003",
            trace_id="trace_audit_003",
            tool_name="validated_tool",
            input={},
        )
        await router.invoke(invocation)

        audit_payloads = _audit_events(nats, config)
        assert len(audit_payloads) == 1

        entry = json.loads(audit_payloads[0])
        assert entry["success"] is False
        assert "input_schema_invalid" in (entry.get("error") or "")

    async def test_invocation_log_cache_keeps_last_100(self):
        """The in-memory cache should cap at 100 entries."""
        config = _make_config()
        nats = RecordingNats()
        registry = ToolRegistry()
        router = ToolRouter(config, nats, registry)  # type: ignore[arg-type]

        await registry.register(
            ToolRegistration(
                definition=_make_definition(),
                routing="stable",
                backend_type="local",
            ),
            local_backend=_echo_backend,
        )

        for i in range(150):
            invocation = ToolInvocation(
                invocation_id=f"inv_cache_{i}",
                trace_id=f"trace_cache_{i}",
                tool_name="validated_tool",
                input={},
            )
            await router.invoke(invocation)

        assert len(router.invocation_log) == 100
        # The first 50 should have been evicted; the cache should start at 50.
        assert router.invocation_log[0].invocation_id == "inv_cache_50"
        assert router.invocation_log[-1].invocation_id == "inv_cache_149"


# ------------------------------------------------------------------
# Backends.
# ------------------------------------------------------------------


async def _echo_backend(params: dict[str, Any]) -> dict[str, Any]:
    return {"echoed": params}


async def _failing_backend(params: dict[str, Any]) -> dict[str, Any]:
    raise RuntimeError("Backend crashed")
