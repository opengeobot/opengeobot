# Function: NATS handler for MCP tool registration, invocation, and listing
# Time: 2026-07-06
# Author: AxeXie
"""NATS subscription handler for the MCP tool gateway.

Handles four inbound subjects:

* ``opengeobot.mcp.tool.register`` — register or update a tool.
* ``opengeobot.mcp.tool.invoke`` — invoke a registered tool.
* ``opengeobot.mcp.tool.list`` — list all registered tools.
* ``opengeobot.mcp.tool.unregister`` — unregister a tool.

The handler never crashes on individual request failures — it returns
structured error responses on the NATS reply subject instead.

Safety red line: the handler never accesses ``/cmd_vel``, motors or vendor
SDKs directly. All tool execution is delegated through the ``ToolRouter`` which
dispatches to registered, versioned tool backends.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import TYPE_CHECKING, Any

from loguru import logger
from nats.aio.msg import Msg
from pydantic import BaseModel, Field

from .registry import (
    LocalToolBackend,
    ToolDefinition,
    ToolRegistration,
)
from .router import ToolInvocation, ToolInvocationResult

if TYPE_CHECKING:
    from .config import GatewayConfig
    from .nats_client import NatsBridge
    from .registry import ToolRegistry
    from .router import ToolRouter


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


# ------------------------------------------------------------------
# Request/Response models.
# ------------------------------------------------------------------


class RegisterToolRequest(BaseModel):
    """Inbound: tool registration request."""

    definition: ToolDefinition
    routing: str = "stable"
    backend_type: str = "local"
    backend_subject: str = ""
    canary_percentage: int = 0

    model_config = {"extra": "ignore"}


class RegisterToolResponse(BaseModel):
    """Outbound: tool registration response."""

    success: bool
    tool_name: str
    version: str
    routing: str
    error: str | None = None

    model_config = {"extra": "ignore"}


class UnregisterToolRequest(BaseModel):
    """Inbound: tool unregistration request."""

    tool_name: str
    routing: str = "stable"

    model_config = {"extra": "ignore"}


class UnregisterToolResponse(BaseModel):
    """Outbound: tool unregistration response."""

    success: bool
    tool_name: str
    routing: str
    error: str | None = None

    model_config = {"extra": "ignore"}


class ListToolsResponse(BaseModel):
    """Outbound: tool list response."""

    tools: list[dict[str, Any]] = Field(default_factory=list)
    count: int = 0

    model_config = {"extra": "ignore"}


class InvokeToolResponse(BaseModel):
    """Outbound: tool invocation response wrapper."""

    success: bool
    result: dict[str, Any] | None = None
    error: str | None = None

    model_config = {"extra": "ignore"}


# ------------------------------------------------------------------
# Handler.
# ------------------------------------------------------------------


class ToolGatewayHandler:
    """Processes inbound NATS requests for tool registration and invocation."""

    def __init__(
        self,
        config: GatewayConfig,
        nats: NatsBridge,
        registry: ToolRegistry,
        router: ToolRouter,
    ) -> None:
        self._config = config
        self._nats = nats
        self._registry = registry
        self._router = router

    # ------------------------------------------------------------------
    # Registration handler.
    # ------------------------------------------------------------------
    async def handle_register(self, msg: Msg) -> None:
        raw = getattr(msg, "data", b"")
        try:
            payload = json.loads(raw)
            request = RegisterToolRequest.model_validate(payload)
        except (json.JSONDecodeError, ValueError) as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed register payload")
            await self._respond(msg, RegisterToolResponse(
                success=False, tool_name="", version="", routing="", error=str(exc)
            ))
            return

        local_backend = _extract_local_backend(request)
        registration = ToolRegistration(
            definition=request.definition,
            routing=request.routing,
            backend_type=request.backend_type,
            backend_subject=request.backend_subject,
            canary_percentage=request.canary_percentage,
        )

        try:
            await self._registry.register(registration, local_backend)
            response = RegisterToolResponse(
                success=True,
                tool_name=request.definition.tool_name,
                version=request.definition.version,
                routing=request.routing,
            )
        except ValueError as exc:
            response = RegisterToolResponse(
                success=False,
                tool_name=request.definition.tool_name,
                version=request.definition.version,
                routing=request.routing,
                error=str(exc),
            )

        await self._respond(msg, response)

    # ------------------------------------------------------------------
    # Unregistration handler.
    # ------------------------------------------------------------------
    async def handle_unregister(self, msg: Msg) -> None:
        raw = getattr(msg, "data", b"")
        try:
            payload = json.loads(raw)
            request = UnregisterToolRequest.model_validate(payload)
        except (json.JSONDecodeError, ValueError) as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed unregister payload")
            await self._respond(msg, UnregisterToolResponse(
                success=False, tool_name="", routing="", error=str(exc)
            ))
            return

        removed = await self._registry.unregister(request.tool_name, request.routing)
        response = UnregisterToolResponse(
            success=removed,
            tool_name=request.tool_name,
            routing=request.routing,
            error=None if removed else f"Tool '{request.tool_name}' routing='{request.routing}' not found",
        )
        await self._respond(msg, response)

    # ------------------------------------------------------------------
    # List handler.
    # ------------------------------------------------------------------
    async def handle_list(self, msg: Msg) -> None:
        entries = await self._registry.list_tools()
        tools = [e.model_dump() for e in entries]
        response = ListToolsResponse(tools=tools, count=len(tools))
        await self._respond(msg, response)

    # ------------------------------------------------------------------
    # Invocation handler.
    # ------------------------------------------------------------------
    async def handle_invoke(self, msg: Msg) -> None:
        raw = getattr(msg, "data", b"")
        try:
            payload = json.loads(raw)
            invocation = ToolInvocation.model_validate(payload)
        except (json.JSONDecodeError, ValueError) as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed invoke payload")
            await self._respond(msg, InvokeToolResponse(
                success=False, error=str(exc)
            ))
            return

        if not invocation.invocation_id:
            invocation.invocation_id = f"inv_{msg.reply or 'unknown'}"

        result = await self._router.invoke(invocation)
        response = InvokeToolResponse(
            success=result.success,
            result=result.model_dump() if result.success else None,
            error=result.error,
        )
        await self._respond(msg, response)

    # ------------------------------------------------------------------
    # Helpers.
    # ------------------------------------------------------------------
    async def _respond(self, msg: Msg, response: BaseModel) -> None:
        reply = getattr(msg, "reply", None)
        if not reply:
            logger.warning("No reply subject available; cannot respond")
            return
        try:
            await self._nats.publish(reply, response.model_dump_json().encode("utf-8"))
        except Exception as exc:  # noqa: BLE001 — publish failure must not crash handler
            logger.bind(error=str(exc)).warning("Failed to publish response")


def _extract_local_backend(request: RegisterToolRequest) -> LocalToolBackend | None:
    """Extract a local backend callable from the registration request.

    For the M2 implementation, local backends are registered programmatically
    rather than from NATS payloads (you cannot serialize a Python function over
    the wire). When a local backend is needed, it is injected after
    registration via ``ToolRegistry.register`` in-process.

    This function returns ``None`` for remote backends, which is the expected
    case for NATS-driven registration.
    """
    if request.backend_type == "local":
        # Local backends must be registered programmatically; the NATS
        # registration only stores the metadata. A warning is logged if a
        # local backend is requested via NATS without a programmatic inject.
        logger.warning(
            "Local backend requested via NATS for tool '{}' — "
            "backend must be injected programmatically after registration",
            request.definition.tool_name,
        )
        return None
    return None
