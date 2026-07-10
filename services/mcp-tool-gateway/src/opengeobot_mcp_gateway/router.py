# Function: MCP tool router — canary/stable routing and invocation dispatch
# Time: 2026-07-06
# Author: AxeXie
"""Tool router for canary/stable routing and invocation dispatch.

The router selects the target tool version (stable or canary) based on the
canary percentage configured on the stable registration, then dispatches the
invocation to the appropriate backend (local function or remote NATS subject).

All invocations are logged with ``trace_id``, input, output and status for
audit correlation.

The router does NOT directly access ``/cmd_vel``, motors or vendor SDKs — all
motion is delegated to registered tools that are themselves validated through
the platform safety pipeline.
"""

from __future__ import annotations

import asyncio
import json
import random
import uuid
from collections import deque
from datetime import datetime, timezone
from typing import TYPE_CHECKING, Any

import jsonschema
from loguru import logger
from pydantic import BaseModel, Field

from .registry import ToolEntry, ToolNotFoundError, ToolRegistry

if TYPE_CHECKING:
    from .config import GatewayConfig
    from .nats_client import NatsBridge


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class ToolInvocation(BaseModel):
    """Inbound tool invocation request."""

    invocation_id: str = ""
    trace_id: str = ""
    tool_name: str
    input: dict[str, Any] = Field(default_factory=dict)
    requested_at: str = ""

    model_config = {"extra": "ignore"}


class ToolInvocationResult(BaseModel):
    """Result of a tool invocation."""

    invocation_id: str
    trace_id: str
    tool_name: str
    success: bool
    output: dict[str, Any] = Field(default_factory=dict)
    error: str | None = None
    routing: str = "stable"
    started_at: str
    completed_at: str

    model_config = {"extra": "ignore"}


class InvocationLogEntry(BaseModel):
    """Audit log entry for a tool invocation."""

    invocation_id: str
    trace_id: str
    tool_name: str
    routing: str
    input_summary: dict[str, Any] = Field(default_factory=dict)
    output_summary: dict[str, Any] = Field(default_factory=dict)
    success: bool
    error: str | None = None
    started_at: str
    completed_at: str

    model_config = {"extra": "ignore"}


class ToolRouter:
    """Routes tool invocations to the correct version (stable/canary)."""

    def __init__(self, config: GatewayConfig, nats: NatsBridge, registry: ToolRegistry) -> None:
        self._config = config
        self._nats = nats
        self._registry = registry
        # In-memory cache of the last 100 invocations for recent queries.
        # Primary audit storage is NATS (opengeobot.audit.mcp_tool_call).
        self._invocation_log: deque[InvocationLogEntry] = deque(maxlen=100)
        self._rng = random.Random()

    @property
    def invocation_log(self) -> list[InvocationLogEntry]:
        """Read-only access to the in-memory invocation log."""
        return list(self._invocation_log)

    def select_routing(self, entry: ToolEntry) -> str:
        """Select 'stable' or 'canary' based on the canary percentage.

        If no canary is registered, always returns 'stable'. If no stable is
        registered, always returns 'canary'. Otherwise, uses the canary
        percentage to probabilistically route.
        """
        if entry.canary is None:
            return "stable"
        if entry.stable is None:
            return "canary"
        # Both exist — route based on percentage.
        if entry.canary_percentage <= 0:
            return "stable"
        if entry.canary_percentage >= 100:
            return "canary"
        roll = self._rng.randint(1, 100)
        return "canary" if roll <= entry.canary_percentage else "stable"

    async def invoke(self, invocation: ToolInvocation) -> ToolInvocationResult:
        """Route and execute a tool invocation."""
        if not invocation.invocation_id:
            invocation.invocation_id = f"inv_{uuid.uuid4().hex}"

        started_at = _now_iso()

        try:
            entry = await self._registry.lookup(invocation.tool_name)
        except ToolNotFoundError as exc:
            result = ToolInvocationResult(
                invocation_id=invocation.invocation_id,
                trace_id=invocation.trace_id,
                tool_name=invocation.tool_name,
                success=False,
                error=str(exc),
                started_at=started_at,
                completed_at=_now_iso(),
            )
            await self._log_invocation(invocation, result, {})
            return result

        routing = self.select_routing(entry)
        registration = entry.stable if routing == "stable" else entry.canary

        if registration is None:
            # Fallback: if selected routing is missing, try the other.
            registration = entry.canary if routing == "stable" else entry.stable
            routing = "canary" if registration is entry.canary else "stable"
            if registration is None:
                result = ToolInvocationResult(
                    invocation_id=invocation.invocation_id,
                    trace_id=invocation.trace_id,
                    tool_name=invocation.tool_name,
                    success=False,
                    error=f"No registration found for tool '{invocation.tool_name}'",
                    started_at=started_at,
                    completed_at=_now_iso(),
                )
                await self._log_invocation(invocation, result, {})
                return result

        logger.bind(
            invocation_id=invocation.invocation_id,
            trace_id=invocation.trace_id,
            tool_name=invocation.tool_name,
            routing=routing,
            version=registration.definition.version,
        ).info("Invoking tool")

        # Validate input against the tool's input schema before dispatching.
        input_schema = registration.definition.input_schema
        if input_schema:
            try:
                jsonschema.validate(invocation.input, input_schema)
            except jsonschema.ValidationError as exc:
                result = ToolInvocationResult(
                    invocation_id=invocation.invocation_id,
                    trace_id=invocation.trace_id,
                    tool_name=invocation.tool_name,
                    success=False,
                    error=f"input_schema_invalid: {exc.message}",
                    routing=routing,
                    started_at=started_at,
                    completed_at=_now_iso(),
                )
                await self._log_invocation(invocation, result, {})
                return result

        try:
            output = await self._dispatch(registration, invocation)

            # Validate output against the tool's output schema. On failure,
            # log a warning but still return the result.
            output_schema = registration.definition.output_schema
            if output_schema:
                try:
                    jsonschema.validate(output, output_schema)
                except jsonschema.ValidationError as exc:
                    logger.bind(
                        invocation_id=invocation.invocation_id,
                        trace_id=invocation.trace_id,
                        tool_name=invocation.tool_name,
                        error=exc.message,
                    ).warning("Output schema validation failed")

            result = ToolInvocationResult(
                invocation_id=invocation.invocation_id,
                trace_id=invocation.trace_id,
                tool_name=invocation.tool_name,
                success=True,
                output=output,
                routing=routing,
                started_at=started_at,
                completed_at=_now_iso(),
            )
        except asyncio.TimeoutError:
            result = ToolInvocationResult(
                invocation_id=invocation.invocation_id,
                trace_id=invocation.trace_id,
                tool_name=invocation.tool_name,
                success=False,
                error=f"Tool backend timed out after {self._config.tool_backend_timeout}s",
                routing=routing,
                started_at=started_at,
                completed_at=_now_iso(),
            )
        except Exception as exc:  # noqa: BLE001 — backend failure must not crash router
            logger.bind(
                invocation_id=invocation.invocation_id,
                trace_id=invocation.trace_id,
                error=str(exc),
            ).exception("Tool backend failed")
            result = ToolInvocationResult(
                invocation_id=invocation.invocation_id,
                trace_id=invocation.trace_id,
                tool_name=invocation.tool_name,
                success=False,
                error=f"Tool backend failed: {exc}",
                routing=routing,
                started_at=started_at,
                completed_at=_now_iso(),
            )

        await self._log_invocation(invocation, result, output if result.success else {})
        return result

    async def _dispatch(
        self,
        registration: Any,
        invocation: ToolInvocation,
    ) -> dict[str, Any]:
        """Dispatch the invocation to the correct backend."""
        from .registry import ToolRegistration

        reg: ToolRegistration = registration

        if reg.backend_type == "local":
            backend = self._registry.get_local_backend(
                invocation.tool_name, reg.routing
            )
            if backend is None:
                raise RuntimeError(
                    f"No local backend registered for tool '{invocation.tool_name}' "
                    f"routing='{reg.routing}'"
                )
            # Enforce the tool timeout.
            timeout_s = reg.definition.timeout_ms / 1000.0
            return await asyncio.wait_for(
                backend(invocation.input), timeout=timeout_s
            )

        if reg.backend_type == "remote":
            payload = invocation.model_dump_json().encode("utf-8")
            reply = await self._nats.request(
                reg.backend_subject,
                payload,
                timeout=self._config.tool_backend_timeout,
            )
            data = json.loads(reply.data)
            if not isinstance(data, dict):
                raise RuntimeError("Remote backend returned non-dict response")
            return data

        raise ValueError(f"Unknown backend_type '{reg.backend_type}'")

    async def _log_invocation(
        self,
        invocation: ToolInvocation,
        result: ToolInvocationResult,
        output: dict[str, Any],
    ) -> None:
        """Record the invocation in the audit log and publish to NATS.

        The in-memory cache (last 100 entries) is kept for recent queries.
        Primary audit storage is the NATS subject
        ``opengeobot.audit.mcp_tool_call``.
        """
        entry = InvocationLogEntry(
            invocation_id=result.invocation_id,
            trace_id=result.trace_id,
            tool_name=result.tool_name,
            routing=result.routing,
            input_summary=invocation.input,
            output_summary=output,
            success=result.success,
            error=result.error,
            started_at=result.started_at,
            completed_at=result.completed_at,
        )
        self._invocation_log.append(entry)

        try:
            await self._nats.publish(
                self._config.audit_subject,
                entry.model_dump_json().encode("utf-8"),
            )
        except Exception as exc:  # noqa: BLE001 - audit publish failure must not crash router
            logger.bind(
                invocation_id=entry.invocation_id,
                error=str(exc),
            ).warning("Failed to publish audit event")

        logger.bind(
            invocation_id=entry.invocation_id,
            trace_id=entry.trace_id,
            tool_name=entry.tool_name,
            routing=entry.routing,
            success=entry.success,
        ).info("Invocation logged")
