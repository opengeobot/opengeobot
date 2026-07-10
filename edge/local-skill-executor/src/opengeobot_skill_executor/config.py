# Function: Local skill executor configuration loaded from environment variables
# Time: 2026-07-06
# Author: AxeXie
"""Environment-driven configuration for the local skill executor.

The executor subscribes to the Safety-Gateway-approved skill execution subject
(``edge.{gateway_id}.skill.execute.approved``) and routes each approved request
to the correct adapter based on the robot model. Adapter routing is configurable
via environment variables so that the same executor binary can target
simulation, ROS2 or ROS1 backends without code changes.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

DEFAULT_GATEWAY_ID = "edge_01"
DEFAULT_NATS_URL = "nats://localhost:4222"

# Default adapter type when the robot model is not explicitly mapped.
DEFAULT_ADAPTER_TYPE = "simulation"
DEFAULT_JETSTREAM_STREAM = "SKILL_EXECUTOR_STREAM"


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
class ExecutorConfig:
    """Immutable local skill executor configuration."""

    gateway_id: str
    nats_url: str
    nats_max_reconnect: int
    nats_reconnect_wait: float
    nats_connect_timeout: float
    # Timeout for adapter request-reply calls.
    adapter_timeout: float
    # Default adapter type: simulation | ros2 | ros1
    default_adapter_type: str
    log_level: str
    # JetStream persistence.
    jetstream_stream_name: str = DEFAULT_JETSTREAM_STREAM

    # ------------------------------------------------------------------
    # NATS subjects.
    # ------------------------------------------------------------------
    @property
    def skill_execute_approved_subject(self) -> str:
        """Inbound: skill execution requests approved by the Safety Gateway."""
        return f"edge.{self.gateway_id}.skill.execute.approved"

    @property
    def jetstream_stream_subjects(self) -> list[str]:
        """Subjects covered by the JetStream persistence stream."""
        return [f"edge.{self.gateway_id}.skill.execute.approved"]

    @property
    def jetstream_durable_name(self) -> str:
        """Durable consumer name for the skill execution subject."""
        return f"skill-executor-{self.gateway_id}"

    @property
    def sim_adapter_subject(self) -> str:
        """Outbound: simulation adapter skill execution subject (per robot)."""
        return "opengeobot.dev.edge.skill.execute.{robot_id}"

    @property
    def ros2_adapter_subject(self) -> str:
        """Outbound: ROS2 adapter skill execution subject (per robot)."""
        return "opengeobot.dev.edge.ros2.skill.execute.{robot_id}"

    @property
    def ros1_adapter_subject(self) -> str:
        """Outbound: ROS1 adapter skill execution subject (per robot)."""
        return "opengeobot.dev.edge.ros1.skill.execute.{robot_id}"

    def adapter_subject(self, adapter_type: str, robot_id: str) -> str:
        """Resolve the adapter subject for a given adapter type and robot."""
        if adapter_type == "ros2":
            return self.ros2_adapter_subject.format(robot_id=robot_id)
        if adapter_type == "ros1":
            return self.ros1_adapter_subject.format(robot_id=robot_id)
        return self.sim_adapter_subject.format(robot_id=robot_id)

    @classmethod
    def from_env(cls) -> ExecutorConfig:
        return cls(
            gateway_id=_env_str("GATEWAY_ID", DEFAULT_GATEWAY_ID),
            nats_url=_env_str("NATS_URL", DEFAULT_NATS_URL),
            nats_max_reconnect=_env_int("NATS_MAX_RECONNECT", -1),
            nats_reconnect_wait=_env_float("SKILL_NATS_RECONNECT_WAIT", 2.0),
            nats_connect_timeout=_env_float("NATS_CONNECT_TIMEOUT", 5.0),
            adapter_timeout=_env_float("SKILL_ADAPTER_TIMEOUT", 10.0),
            default_adapter_type=_env_str("DEFAULT_ADAPTER_TYPE", DEFAULT_ADAPTER_TYPE),
            log_level=_env_str("LOG_LEVEL", "INFO"),
            jetstream_stream_name=_env_str("SKILL_JETSTREAM_STREAM", DEFAULT_JETSTREAM_STREAM),
        )
