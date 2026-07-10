# Function: Safety Gateway configuration loaded from environment variables
# Time: 2026-07-06
# Author: AxeXie
"""Environment-driven configuration for the edge Safety Gateway.

All values are sourced from environment variables with sensible defaults that
match the local docker-compose stack. The configuration is immutable once loaded.
Safety parameters (speed limits, restricted zones, collision proximity) are
configurable so that different robot platforms and environments can enforce
site-specific safety policies.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field

DEFAULT_GATEWAY_ID = "edge_01"
DEFAULT_NATS_URL = "nats://localhost:4222"

# Default safety parameters (conservative for indoor AMR platforms).
DEFAULT_MAX_LINEAR_SPEED = 1.5  # m/s
DEFAULT_MAX_ANGULAR_SPEED = 1.0  # rad/s
DEFAULT_COLLISION_PROXIMITY = 0.5  # metres
DEFAULT_HEALTH_CHECK_PORT = 8081
DEFAULT_JETSTREAM_STREAM = "SAFETY_STREAM"


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


def _env_json(key: str, default: list) -> list:
    """Parse a JSON list from an environment variable."""
    raw = os.getenv(key)
    if raw is None or raw.strip() == "":
        return list(default)
    try:
        parsed = json.loads(raw)
        if not isinstance(parsed, list):
            return list(default)
        return parsed
    except (json.JSONDecodeError, TypeError):
        return list(default)


@dataclass(frozen=True)
class SafetyGatewayConfig:
    """Immutable Safety Gateway configuration."""

    gateway_id: str
    nats_url: str
    nats_max_reconnect: int
    nats_reconnect_wait: float
    nats_connect_timeout: float
    # Action-level safety parameters.
    max_linear_speed: float
    max_angular_speed: float
    collision_proximity_threshold: float
    # Runtime.
    health_check_port: int
    skill_forward_subject_suffix: str
    log_level: str
    restricted_zones: list[dict[str, float]] = field(default_factory=list)
    # JetStream persistence.
    jetstream_stream_name: str = DEFAULT_JETSTREAM_STREAM

    # ------------------------------------------------------------------
    # NATS subjects (edge.{gateway_id}.safety.* / edge.{gateway_id}.skill.*).
    # ------------------------------------------------------------------
    @property
    def emergency_stop_subject(self) -> str:
        """Inbound: emergency stop command."""
        return f"edge.{self.gateway_id}.safety.emergency_stop"

    @property
    def reset_subject(self) -> str:
        """Inbound: safety reset command."""
        return f"edge.{self.gateway_id}.safety.reset"

    @property
    def skill_execute_subject(self) -> str:
        """Inbound: skill execution request (intercepted by Safety Gateway)."""
        return f"edge.{self.gateway_id}.skill.execute"

    @property
    def skill_forward_subject(self) -> str:
        """Outbound: approved skill execution forwarded to local skill executor."""
        return f"edge.{self.gateway_id}.skill.{self.skill_forward_subject_suffix}"

    @property
    def state_changed_subject(self) -> str:
        """Outbound: safety state change broadcast."""
        return f"edge.{self.gateway_id}.safety.state_changed"

    @property
    def jetstream_stream_subjects(self) -> list[str]:
        """Subjects covered by the JetStream persistence stream."""
        return [
            f"edge.{self.gateway_id}.safety.>",
            f"edge.{self.gateway_id}.skill.execute",
        ]

    @property
    def jetstream_durable_name(self) -> str:
        """Durable consumer name for the skill.execute interception."""
        return f"safety-gw-{self.gateway_id}-skill"

    @classmethod
    def from_env(cls) -> SafetyGatewayConfig:
        return cls(
            gateway_id=_env_str("GATEWAY_ID", DEFAULT_GATEWAY_ID),
            nats_url=_env_str("NATS_URL", DEFAULT_NATS_URL),
            nats_max_reconnect=_env_int("NATS_MAX_RECONNECT", -1),
            nats_reconnect_wait=_env_float("EDGE_NATS_RECONNECT_WAIT", 2.0),
            nats_connect_timeout=_env_float("NATS_CONNECT_TIMEOUT", 5.0),
            max_linear_speed=_env_float("SAFETY_MAX_LINEAR_SPEED", DEFAULT_MAX_LINEAR_SPEED),
            max_angular_speed=_env_float("SAFETY_MAX_ANGULAR_SPEED", DEFAULT_MAX_ANGULAR_SPEED),
            collision_proximity_threshold=_env_float(
                "SAFETY_COLLISION_PROXIMITY", DEFAULT_COLLISION_PROXIMITY
            ),
            restricted_zones=_env_json("SAFETY_RESTRICTED_ZONES", []),
            health_check_port=_env_int("SAFETY_HEALTH_CHECK_PORT", DEFAULT_HEALTH_CHECK_PORT),
            skill_forward_subject_suffix=_env_str("SKILL_FORWARD_SUFFIX", "execute.approved"),
            log_level=_env_str("LOG_LEVEL", "INFO"),
            jetstream_stream_name=_env_str("SAFETY_JETSTREAM_STREAM", DEFAULT_JETSTREAM_STREAM),
        )
