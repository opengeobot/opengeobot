# Function: MCP tool registry — register, list, and lookup tools
# Time: 2026-07-06
# Author: AxeXie
"""MCP tool registry for registering, listing and looking up tools.

The registry maintains tool definitions (matching the MCP tool-schema.json
contract) along with their backend routing information. Each tool name can have
a ``stable`` version and an optional ``canary`` version for progressive
rollout.

Tools can be backed by:

* **Local** — an in-process async Python function.
* **Remote** — a NATS subject (request-reply) that the gateway calls.

The registry itself does NOT execute tools — it only stores metadata and routes
lookups. The ``ToolRouter`` is responsible for selecting stable vs. canary and
dispatching invocations.

Safety red line: the registry and gateway never access ``/cmd_vel``, motors or
vendor SDKs directly. All motion is a registered, versioned skill invoked
through the platform pipeline.
"""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from datetime import datetime, timezone
from typing import Any

from loguru import logger
from pydantic import BaseModel, Field

# Type alias for a local tool backend (async function taking dict input, returning dict output).
LocalToolBackend = Callable[[dict[str, Any]], Awaitable[dict[str, Any]]]


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


# ------------------------------------------------------------------
# Data models (matching contracts/mcp/tool-schema.json).
# ------------------------------------------------------------------


class ToolDefinition(BaseModel):
    """MCP tool definition matching the platform tool-schema.json contract."""

    tool_id: str
    tool_name: str
    version: str = "0.0.1"
    description: str = ""
    input_schema: dict[str, Any] = Field(default_factory=dict)
    output_schema: dict[str, Any] = Field(default_factory=dict)
    permission_code: str = ""
    risk_level: str = "R0_READ_ONLY"
    idempotent: bool = False
    timeout_ms: int = 30000
    cancellable: bool = True
    audit_level: str = "SUMMARY"

    model_config = {"extra": "ignore"}


class ToolRegistration(BaseModel):
    """A registered tool with its backend routing information."""

    definition: ToolDefinition
    routing: str = "stable"  # "stable" | "canary"
    backend_type: str = "local"  # "local" | "remote"
    backend_subject: str = ""  # NATS subject for remote backends
    canary_percentage: int = 0  # 0-100, stored on stable registration
    registered_at: str = ""

    model_config = {"extra": "ignore"}


class ToolListEntry(BaseModel):
    """Summary entry for the tool list endpoint."""

    tool_id: str
    tool_name: str
    version: str
    description: str
    routing: str
    risk_level: str

    model_config = {"extra": "ignore"}


# ------------------------------------------------------------------
# Registry.
# ------------------------------------------------------------------


class DuplicateToolError(ValueError):
    """Raised when a tool is registered with a routing that already exists."""


class ToolNotFoundError(KeyError):
    """Raised when a tool is not found in the registry."""


class ToolEntry:
    """Internal registry entry holding stable and canary registrations."""

    __slots__ = ("stable", "canary", "canary_percentage", "_lock")

    def __init__(self) -> None:
        self.stable: ToolRegistration | None = None
        self.canary: ToolRegistration | None = None
        self.canary_percentage: int = 0
        self._lock = asyncio.Lock()

    def has_any(self) -> bool:
        return self.stable is not None or self.canary is not None


class ToolRegistry:
    """Thread-safe (asyncio) registry for MCP tools."""

    def __init__(self) -> None:
        self._tools: dict[str, ToolEntry] = {}
        # Maps tool_name → routing → local backend function.
        self._local_backends: dict[str, dict[str, LocalToolBackend]] = {}
        self._lock = asyncio.Lock()

    async def register(
        self,
        registration: ToolRegistration,
        local_backend: LocalToolBackend | None = None,
    ) -> None:
        """Register a tool definition with the given routing.

        If ``routing`` is ``stable`` and ``canary_percentage`` is set, it
        updates the canary percentage for this tool. If ``routing`` is
        ``canary``, the canary version is stored without changing the
        percentage.

        For local backends, pass ``local_backend`` as the async callable.
        """
        async with self._lock:
            name = registration.definition.tool_name
            entry = self._tools.get(name)
            if entry is None:
                entry = ToolEntry()
                self._tools[name] = entry

            now = _now_iso()
            registration = registration.model_copy(update={"registered_at": now})

            if registration.routing == "stable":
                entry.stable = registration
                entry.canary_percentage = registration.canary_percentage
            elif registration.routing == "canary":
                entry.canary = registration
            else:
                raise ValueError(f"Unknown routing '{registration.routing}'; must be 'stable' or 'canary'")

            if registration.backend_type == "local":
                if local_backend is None:
                    raise ValueError(
                        "local_backend must be provided for backend_type='local'"
                    )
                backends = self._local_backends.setdefault(name, {})
                backends[registration.routing] = local_backend

            logger.bind(
                tool_name=name,
                version=registration.definition.version,
                routing=registration.routing,
                backend_type=registration.backend_type,
            ).info("Tool registered")

    async def unregister(self, tool_name: str, routing: str = "stable") -> bool:
        """Unregister a specific routing of a tool. Returns True if removed."""
        async with self._lock:
            entry = self._tools.get(tool_name)
            if entry is None:
                return False

            removed = False
            if routing == "stable" and entry.stable is not None:
                entry.stable = None
                removed = True
            elif routing == "canary" and entry.canary is not None:
                entry.canary = None
                removed = True

            # Clean up local backend.
            backends = self._local_backends.get(tool_name)
            if backends and routing in backends:
                del backends[routing]
                if not backends:
                    del self._local_backends[tool_name]

            # Remove the tool entry entirely if both are gone.
            if not entry.has_any():
                del self._tools[tool_name]

            if removed:
                logger.bind(tool_name=tool_name, routing=routing).info("Tool unregistered")
            return removed

    async def list_tools(self) -> list[ToolListEntry]:
        """List all registered tools with their stable routing."""
        async with self._lock:
            entries: list[ToolListEntry] = []
            for name, entry in self._tools.items():
                for routing, reg in (("stable", entry.stable), ("canary", entry.canary)):
                    if reg is not None:
                        entries.append(
                            ToolListEntry(
                                tool_id=reg.definition.tool_id,
                                tool_name=name,
                                version=reg.definition.version,
                                description=reg.definition.description,
                                routing=routing,
                                risk_level=reg.definition.risk_level,
                            )
                        )
            return entries

    async def lookup(self, tool_name: str) -> ToolEntry:
        """Look up the registry entry for a tool. Raises if not found."""
        async with self._lock:
            entry = self._tools.get(tool_name)
            if entry is None or not entry.has_any():
                raise ToolNotFoundError(f"Tool '{tool_name}' not found in registry")
            return entry

    def get_local_backend(self, tool_name: str, routing: str) -> LocalToolBackend | None:
        """Return the local backend for a tool/routing, if registered."""
        backends = self._local_backends.get(tool_name)
        if backends is None:
            return None
        return backends.get(routing)
