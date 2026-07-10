# Function: Edge gateway configuration loaded from environment variables
# Time: 2026-07-05
# Author: AxeXie
"""Environment-driven configuration for the edge gateway.

All values are sourced from environment variables with defaults that match the
repository ``.env.example`` so the gateway runs out of the box against the local
docker-compose stack. The configuration is immutable once loaded.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

DEFAULT_ROBOT_ID = "rbt_01J00000000000000000000001"
DEFAULT_NATS_URL = "nats://localhost:4222"
DEFAULT_CLOUD_API = "http://localhost:8080"
DEFAULT_JETSTREAM_STREAM_NAME = "EDGE_STREAM"
DEFAULT_JETSTREAM_STREAM_SUBJECTS = "opengeobot.dev.edge.>"
DEFAULT_JETSTREAM_CONSUMER_PREFIX = "edge-cmd"


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


@dataclass(frozen=True)
class EdgeConfig:
    """Immutable edge gateway configuration."""

    robot_id: str
    nats_url: str
    nats_max_reconnect: int
    nats_reconnect_wait: float
    nats_connect_timeout: float
    cloud_api_base_url: str
    state_publish_interval: float
    skill_request_timeout: float
    offline_cache_path: str
    log_level: str
    jetstream_stream_name: str = DEFAULT_JETSTREAM_STREAM_NAME
    jetstream_stream_subjects: str = DEFAULT_JETSTREAM_STREAM_SUBJECTS
    jetstream_consumer_prefix: str = DEFAULT_JETSTREAM_CONSUMER_PREFIX

    @property
    def command_subject(self) -> str:
        """Cloud → edge command channel."""
        return f"opengeobot.dev.edge.command.{self.robot_id}"

    @property
    def state_subject(self) -> str:
        """Edge → cloud state channel."""
        return f"opengeobot.dev.edge.state.{self.robot_id}"

    @property
    def skill_execute_subject(self) -> str:
        """Edge → local skill executor (sim-adapter) request subject."""
        return f"opengeobot.dev.edge.skill.execute.{self.robot_id}"

    @property
    def reconciliation_subject(self) -> str:
        """Cloud → edge reconciliation channel (pending commands on reconnect)."""
        return f"opengeobot.dev.edge.reconcile.{self.robot_id}"

    @property
    def jetstream_consumer_name(self) -> str:
        """Durable consumer name for the command JetStream consumer."""
        return f"{self.jetstream_consumer_prefix}-{self.robot_id}"

    @classmethod
    def from_env(cls) -> EdgeConfig:
        return cls(
            robot_id=_env_str("ROBOT_ID", DEFAULT_ROBOT_ID),
            nats_url=_env_str("NATS_URL", DEFAULT_NATS_URL),
            nats_max_reconnect=_env_int("NATS_MAX_RECONNECT", -1),
            nats_reconnect_wait=_env_float("EDGE_NATS_RECONNECT_WAIT", 2.0),
            nats_connect_timeout=_env_float("NATS_CONNECT_TIMEOUT", 5.0),
            cloud_api_base_url=_env_str("CLOUD_API_BASE_URL", DEFAULT_CLOUD_API),
            state_publish_interval=_env_float("EDGE_STATE_PUBLISH_INTERVAL", 5.0),
            skill_request_timeout=_env_float("EDGE_SKILL_REQUEST_TIMEOUT", 10.0),
            offline_cache_path=_env_str(
                "EDGE_OFFLINE_CACHE_PATH", "./.edge-data/offline-cache.json"
            ),
            log_level=_env_str("LOG_LEVEL", "INFO"),
            jetstream_stream_name=_env_str(
                "EDGE_JETSTREAM_STREAM_NAME", DEFAULT_JETSTREAM_STREAM_NAME
            ),
            jetstream_stream_subjects=_env_str(
                "EDGE_JETSTREAM_STREAM_SUBJECTS", DEFAULT_JETSTREAM_STREAM_SUBJECTS
            ),
            jetstream_consumer_prefix=_env_str(
                "EDGE_JETSTREAM_CONSUMER_PREFIX", DEFAULT_JETSTREAM_CONSUMER_PREFIX
            ),
        )
