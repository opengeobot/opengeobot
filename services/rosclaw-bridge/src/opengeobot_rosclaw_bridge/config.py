# Function: ROSClaw NATS Bridge configuration loaded from environment variables
# Time: 2026-07-16
# Author: AxeXie
"""Environment-driven configuration for the ROSClaw NATS Bridge."""

from __future__ import annotations

import os
from dataclasses import dataclass

DEFAULT_ROBOT_ID = "rbt_01J00000000000000000000001"
DEFAULT_NATS_URL = "nats://localhost:4222"
DEFAULT_GATEWAY_ID = "edge_01"
DEFAULT_ROS_ENDPOINT = "ws://ros2-turtlesim:9090"
DEFAULT_ROSCLAW_PROFILE = "offline"
DEFAULT_LOG_LEVEL = "INFO"
DEFAULT_SKILL_REQUEST_TIMEOUT = 30.0
DEFAULT_READY_FILE_PATH = "/tmp/opengeobot-rosclaw-bridge.ready"


def _env_str(key: str, default: str) -> str:
    value = os.getenv(key)
    return value if value is not None else default


def _env_float(key: str, default: float) -> float:
    raw = os.getenv(key)
    if raw is None or raw.strip() == "":
        return default
    return float(raw)


def _env_bool(key: str, default: bool) -> bool:
    raw = os.getenv(key)
    if raw is None or raw.strip() == "":
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


@dataclass(frozen=True)
class BridgeConfig:
    """Immutable ROSClaw bridge configuration."""

    robot_id: str
    gateway_id: str
    nats_url: str
    nats_max_reconnect: int
    nats_reconnect_wait: float
    nats_connect_timeout: float
    robot_ros_endpoint: str
    rosclaw_profile: str
    rosclaw_sandbox: bool
    log_level: str
    skill_request_timeout: float
    ready_file_path: str

    @property
    def skill_execute_subject(self) -> str:
        """Edge -> bridge skill execution request subject."""
        return f"opengeobot.dev.edge.skill.execute.{self.robot_id}"

    @classmethod
    def from_env(cls) -> BridgeConfig:
        return cls(
            robot_id=_env_str("ROBOT_ID", DEFAULT_ROBOT_ID),
            gateway_id=_env_str("GATEWAY_ID", DEFAULT_GATEWAY_ID),
            nats_url=_env_str("NATS_URL", DEFAULT_NATS_URL),
            nats_max_reconnect=int(os.getenv("NATS_MAX_RECONNECT", "-1")),
            nats_reconnect_wait=_env_float("NATS_RECONNECT_WAIT", 2.0),
            nats_connect_timeout=_env_float("NATS_CONNECT_TIMEOUT", 5.0),
            robot_ros_endpoint=_env_str("ROBOT_ROS_ENDPOINT", DEFAULT_ROS_ENDPOINT),
            rosclaw_profile=_env_str("ROSCLAW_PROFILE", DEFAULT_ROSCLAW_PROFILE),
            rosclaw_sandbox=_env_bool("ROSCLAW_SANDBOX", True),
            log_level=_env_str("LOG_LEVEL", DEFAULT_LOG_LEVEL),
            skill_request_timeout=_env_float("SKILL_REQUEST_TIMEOUT", DEFAULT_SKILL_REQUEST_TIMEOUT),
            ready_file_path=_env_str(
                "ROSCLAW_BRIDGE_READY_FILE",
                DEFAULT_READY_FILE_PATH,
            ),
        )
