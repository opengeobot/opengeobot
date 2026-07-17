# Function: MCP tool gateway configuration loaded from environment variables
# Time: 2026-07-06
# Author: AxeXie
"""Environment-driven configuration for the MCP tool gateway."""

from __future__ import annotations

import os
from dataclasses import dataclass

DEFAULT_NATS_URL = "nats://localhost:4222"


def _env_str(key: str, default: str) -> str:
    value = os.getenv(key)
    return value if value is not None else default


def _env_int(key: str, default: int) -> int:
    raw = os.getenv(key)
    if raw is None or raw.strip() == "":
        return default
    return int(raw)


def _env_float(key: str, default: float) -> float:
    raw = os.getenv(key)
    if raw is None or raw.strip() == "":
        return default
    return float(raw)


def _env_csv(key: str) -> list[str]:
    raw = os.getenv(key)
    if raw is None or raw.strip() == "":
        return []
    return [part.strip() for part in raw.split(",") if part.strip()]


@dataclass(frozen=True)
class GatewayConfig:
    """Immutable MCP tool gateway configuration."""

    nats_url: str
    nats_max_reconnect: int
    nats_reconnect_wait: float
    nats_connect_timeout: float
    # Timeout for remote tool backend calls via NATS request-reply.
    tool_backend_timeout: float
    log_level: str
    http_host: str
    http_port: int
    http_auth_token: str
    allowed_origins: list[str]
    # JetStream durable consumer configuration.
    js_stream_name: str = "MCP_STREAM"
    js_durable_consumer: str = "mcp-tool-gateway-consumer"
    js_stream_subjects: str = "opengeobot.mcp.>"

    # ------------------------------------------------------------------
    # NATS subjects.
    # ------------------------------------------------------------------
    @property
    def register_subject(self) -> str:
        """Inbound: tool registration requests."""
        return "opengeobot.mcp.tool.register"

    @property
    def invoke_subject(self) -> str:
        """Inbound: tool invocation requests."""
        return "opengeobot.mcp.tool.invoke"

    @property
    def list_subject(self) -> str:
        """Inbound: tool list requests."""
        return "opengeobot.mcp.tool.list"

    @property
    def unregister_subject(self) -> str:
        """Inbound: tool unregistration requests."""
        return "opengeobot.mcp.tool.unregister"

    @property
    def audit_subject(self) -> str:
        """Outbound: tool invocation audit events."""
        return "opengeobot.audit.mcp_tool_call"

    @classmethod
    def from_env(cls) -> GatewayConfig:
        return cls(
            nats_url=_env_str("NATS_URL", DEFAULT_NATS_URL),
            nats_max_reconnect=_env_int("NATS_MAX_RECONNECT", -1),
            nats_reconnect_wait=_env_float("MCP_NATS_RECONNECT_WAIT", 2.0),
            nats_connect_timeout=_env_float("NATS_CONNECT_TIMEOUT", 5.0),
            tool_backend_timeout=_env_float("MCP_TOOL_BACKEND_TIMEOUT", 30.0),
            log_level=_env_str("LOG_LEVEL", "INFO"),
            http_host=_env_str("MCP_HTTP_HOST", "0.0.0.0"),
            http_port=_env_int("MCP_HTTP_PORT", 8090),
            http_auth_token=_env_str("MCP_HTTP_AUTH_TOKEN", ""),
            allowed_origins=_env_csv("MCP_ALLOWED_ORIGINS"),
            js_stream_name=_env_str("MCP_JS_STREAM_NAME", "MCP_STREAM"),
            js_durable_consumer=_env_str(
                "MCP_JS_DURABLE_CONSUMER", "mcp-tool-gateway-consumer"
            ),
            js_stream_subjects=_env_str("MCP_JS_STREAM_SUBJECTS", "opengeobot.mcp.>"),
        )
