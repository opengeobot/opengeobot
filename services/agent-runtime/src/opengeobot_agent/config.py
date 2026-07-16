# Function: Agent runtime configuration loaded from environment variables
# Time: 2026-07-06
# Author: AxeXie
"""Environment-driven configuration for the agent runtime.

The agent runtime receives mission planning requests via NATS and calls the
QwenPaw API to generate mission plan proposals. All configuration is sourced
from environment variables with sensible defaults.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

DEFAULT_NATS_URL = "nats://localhost:4222"
DEFAULT_QWENPAW_ENDPOINT = "http://localhost:8000/v1/chat/completions"
DEFAULT_QWENPAW_ADMIN_BASE_URL = "http://qwenpaw:8088"
DEFAULT_QWENPAW_AGENT_ID = "opengeobot-controller"
DEFAULT_QWENPAW_AGENT_NAME = "一脑多控"


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


def _env_bool(key: str, default: bool) -> bool:
    raw = os.getenv(key)
    if raw is None or raw.strip() == "":
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class AgentConfig:
    """Immutable agent runtime configuration."""

    nats_url: str
    nats_max_reconnect: int
    nats_reconnect_wait: float
    nats_connect_timeout: float
    # QwenPaw API endpoint URL.
    qwenpaw_endpoint: str
    qwenpaw_api_key: str
    qwenpaw_timeout: float
    # NATS subject for receiving mission planning requests.
    plan_request_subject: str
    log_level: str
    # JetStream durable consumer configuration.
    js_stream_name: str = "AGENT_STREAM"
    js_durable_consumer: str = "agent-runtime-consumer"
    js_stream_subjects: str = "opengeobot.agent.>"
    # Skill registry query configuration.
    skill_list_subject: str = "opengeobot.skill.list"
    skill_request_timeout: float = 5.0
    # QwenPaw management API for agent creation/verification on startup.
    qwenpaw_admin_base_url: str = DEFAULT_QWENPAW_ADMIN_BASE_URL
    qwenpaw_agent_id: str = DEFAULT_QWENPAW_AGENT_ID
    qwenpaw_agent_name: str = DEFAULT_QWENPAW_AGENT_NAME
    qwenpaw_agent_create_on_start: bool = True

    @classmethod
    def from_env(cls) -> AgentConfig:
        return cls(
            nats_url=_env_str("NATS_URL", DEFAULT_NATS_URL),
            nats_max_reconnect=_env_int("NATS_MAX_RECONNECT", -1),
            nats_reconnect_wait=_env_float("AGENT_NATS_RECONNECT_WAIT", 2.0),
            nats_connect_timeout=_env_float("NATS_CONNECT_TIMEOUT", 5.0),
            qwenpaw_endpoint=_env_str("QWENPAW_ENDPOINT", DEFAULT_QWENPAW_ENDPOINT),
            qwenpaw_api_key=_env_str("QWENPAW_API_KEY", ""),
            qwenpaw_timeout=_env_float("QWENPAW_TIMEOUT", 30.0),
            plan_request_subject=_env_str(
                "AGENT_PLAN_REQUEST_SUBJECT",
                "opengeobot.agent.mission.plan_request",
            ),
            log_level=_env_str("LOG_LEVEL", "INFO"),
            js_stream_name=_env_str("AGENT_JS_STREAM_NAME", "AGENT_STREAM"),
            js_durable_consumer=_env_str(
                "AGENT_JS_DURABLE_CONSUMER", "agent-runtime-consumer"
            ),
            js_stream_subjects=_env_str(
                "AGENT_JS_STREAM_SUBJECTS", "opengeobot.agent.>"
            ),
            skill_list_subject=_env_str(
                "AGENT_SKILL_LIST_SUBJECT", "opengeobot.skill.list"
            ),
            skill_request_timeout=_env_float("AGENT_SKILL_REQUEST_TIMEOUT", 5.0),
            qwenpaw_admin_base_url=_env_str(
                "QWENPAW_ADMIN_BASE_URL", DEFAULT_QWENPAW_ADMIN_BASE_URL
            ),
            qwenpaw_agent_id=_env_str("QWENPAW_AGENT_ID", DEFAULT_QWENPAW_AGENT_ID),
            qwenpaw_agent_name=_env_str(
                "QWENPAW_AGENT_NAME", DEFAULT_QWENPAW_AGENT_NAME
            ),
            qwenpaw_agent_create_on_start=_env_bool(
                "QWENPAW_AGENT_CREATE_ON_START", True
            ),
        )
