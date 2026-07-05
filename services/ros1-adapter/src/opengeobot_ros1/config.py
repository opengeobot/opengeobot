# Function: ROS1 adapter configuration loaded from environment variables
# Time: 2026-07-05
# Author: AxeXie
"""Environment-driven configuration for the ROS1 protocol adapter."""

from __future__ import annotations

import os
from dataclasses import dataclass

DEFAULT_NATS_URL = "nats://localhost:4222"


def _env_str(key: str, default: str) -> str:
    value = os.getenv(key)
    return value if value is not None else default


def _env_float(key: str, default: float) -> float:
    raw = os.getenv(key)
    if raw is None or raw.strip() == "":
        return default
    return float(raw)


@dataclass(frozen=True)
class Ros1Config:
    """Immutable ROS1 adapter configuration."""

    adapter_id: str
    robot_id: str
    protocol_type: str
    version: str
    nats_url: str
    nats_max_reconnect: int
    nats_reconnect_wait: float
    nats_connect_timeout: float
    ros_master_uri: str
    node_name: str
    log_level: str

    @property
    def translate_subject(self) -> str:
        """Cloud → adapter command translation request subject."""
        return f"opengeobot.dev.adapter.translate.{self.adapter_id}"

    @classmethod
    def from_env(cls) -> Ros1Config:
        return cls(
            adapter_id=_env_str("ADAPTER_ID", "adp_01J00000000000000000000001"),
            robot_id=_env_str("ROBOT_ID", "rbt_01J00000000000000000000001"),
            protocol_type=_env_str("PROTOCOL_TYPE", "ROS1"),
            version=_env_str("ADAPTER_VERSION", "0.1.0"),
            nats_url=_env_str("NATS_URL", DEFAULT_NATS_URL),
            nats_max_reconnect=int(os.getenv("NATS_MAX_RECONNECT", "-1")),
            nats_reconnect_wait=_env_float("ROS1_NATS_RECONNECT_WAIT", 2.0),
            nats_connect_timeout=_env_float("NATS_CONNECT_TIMEOUT", 5.0),
            ros_master_uri=_env_str("ROS_MASTER_URI", "http://localhost:11311"),
            node_name=_env_str("ROS1_NODE_NAME", "opengeobot_ros1"),
            log_level=_env_str("LOG_LEVEL", "INFO"),
        )
