# Function: ROS2 adapter configuration loaded from environment variables
# Time: 2026-07-15
# Author: AxeXie
"""Environment-driven configuration for the ROS2 adapter."""

from __future__ import annotations

import os
from dataclasses import dataclass

DEFAULT_ROBOT_ID = "rbt_01J00000000000000000000001"
DEFAULT_NATS_URL = "nats://localhost:4222"
DEFAULT_JETSTREAM_STREAM = "ROS2_ADAPTER_STREAM"


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
class Ros2Config:
    """Immutable ROS2 adapter configuration."""

    robot_id: str
    nats_url: str
    nats_max_reconnect: int
    nats_reconnect_wait: float
    nats_connect_timeout: float
    # ROS_DOMAIN_ID for DDS isolation.
    dds_domain_id: int
    log_level: str
    jetstream_stream_name: str

    @property
    def skill_execute_subject(self) -> str:
        """Edge -> ROS2 adapter skill execution request subject."""
        return f"opengeobot.dev.edge.ros2.skill.execute.{self.robot_id}"

    @property
    def jetstream_stream_subjects(self) -> list[str]:
        """Subjects covered by the JetStream persistence stream."""
        return [self.skill_execute_subject]

    @property
    def jetstream_durable_name(self) -> str:
        """Durable consumer name for the skill execution subject."""
        return f"ros2-adapter-{self.robot_id}"

    @classmethod
    def from_env(cls) -> Ros2Config:
        return cls(
            robot_id=_env_str("ROBOT_ID", DEFAULT_ROBOT_ID),
            nats_url=_env_str("NATS_URL", DEFAULT_NATS_URL),
            nats_max_reconnect=int(os.getenv("NATS_MAX_RECONNECT", "-1")),
            nats_reconnect_wait=_env_float("ROS2_NATS_RECONNECT_WAIT", 2.0),
            nats_connect_timeout=_env_float("NATS_CONNECT_TIMEOUT", 5.0),
            dds_domain_id=_env_int("ROS_DOMAIN_ID", 42),
            log_level=_env_str("LOG_LEVEL", "INFO"),
            jetstream_stream_name=_env_str(
                "ROS2_JETSTREAM_STREAM", DEFAULT_JETSTREAM_STREAM
            ),
        )
