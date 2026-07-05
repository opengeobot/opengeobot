# Function: Simulation adapter configuration loaded from environment variables
# Time: 2026-07-05
# Author: AxeXie
"""Environment-driven configuration for the simulation adapter."""

from __future__ import annotations

import os
from dataclasses import dataclass

DEFAULT_ROBOT_ID = "rbt_01J00000000000000000000001"
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
class SimConfig:
    """Immutable simulation adapter configuration."""

    robot_id: str
    nats_url: str
    nats_max_reconnect: int
    nats_reconnect_wait: float
    nats_connect_timeout: float
    # Caps simulated execution delays so tests stay fast.
    simulation_step: float
    log_level: str

    @property
    def skill_execute_subject(self) -> str:
        """Edge → simulator skill execution request subject."""
        return f"opengeobot.dev.edge.skill.execute.{self.robot_id}"

    @classmethod
    def from_env(cls) -> SimConfig:
        return cls(
            robot_id=_env_str("ROBOT_ID", DEFAULT_ROBOT_ID),
            nats_url=_env_str("NATS_URL", DEFAULT_NATS_URL),
            nats_max_reconnect=int(os.getenv("NATS_MAX_RECONNECT", "-1")),
            nats_reconnect_wait=_env_float("SIM_NATS_RECONNECT_WAIT", 2.0),
            nats_connect_timeout=_env_float("NATS_CONNECT_TIMEOUT", 5.0),
            simulation_step=_env_float("SIMULATION_STEP", 0.05),
            log_level=_env_str("LOG_LEVEL", "INFO"),
        )
