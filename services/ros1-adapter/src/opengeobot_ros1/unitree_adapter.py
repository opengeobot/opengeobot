# Function: Unitree Go1/Go2 protocol adapter (F-ADAPTER-002)
# Time: 2026-07-05
# Author: AxeXie
"""Unitree Go1/Go2 specific protocol translation.

Translates platform skill commands into Unitree ROS1 topic/command payloads.
The mapping is based on the Unitree ROS1 message interface for Go1/Go2
(quadruped robots). Translations are proposals — the edge Safety Gateway
validates them before any motor command is published.

Skill command mapping:
    stand_up        → standUpCmd (std_msgs/Bool)
    move_forward    → walkCmd    (geometry_msgs/Twist, linear.x > 0)
    stop            → stopCmd    (geometry_msgs/Twist, all zero)
    emergency_stop  → emergencyStopCmd (std_msgs/Bool, true)
"""

from __future__ import annotations

from typing import Any

from .adapter import TranslationError

# Default forward speed for move_forward (m/s). The Safety Gateway may clamp this.
DEFAULT_FORWARD_SPEED = 0.5


class UnitreeAdapter:
    """Unitree Go1/Go2 protocol translation handler."""

    protocol_type: str = "UNITREE"

    def translate(
        self, skill_id: str, params: dict[str, Any]
    ) -> dict[str, Any]:
        if skill_id == "stand_up":
            return self._stand_up(params)
        if skill_id == "move_forward":
            return self._move_forward(params)
        if skill_id == "stop":
            return self._stop(params)
        if skill_id == "emergency_stop":
            return self._emergency_stop(params)
        raise TranslationError(
            f"Unsupported skill_id '{skill_id}' for UNITREE protocol"
        )

    @staticmethod
    def _stand_up(params: dict[str, Any]) -> dict[str, Any]:
        return {
            "topic": "/standUpCmd",
            "type": "std_msgs/Bool",
            "data": True,
            "duration": params.get("duration", 2.0),
        }

    @staticmethod
    def _move_forward(params: dict[str, Any]) -> dict[str, Any]:
        speed = float(params.get("speed", DEFAULT_FORWARD_SPEED))
        distance = float(params.get("distance", 1.0))
        return {
            "topic": "/walkCmd",
            "type": "geometry_msgs/Twist",
            "linear": {"x": speed, "y": 0.0, "z": 0.0},
            "angular": {"x": 0.0, "y": 0.0, "z": 0.0},
            "distance": distance,
        }

    @staticmethod
    def _stop(params: dict[str, Any]) -> dict[str, Any]:
        return {
            "topic": "/stopCmd",
            "type": "geometry_msgs/Twist",
            "linear": {"x": 0.0, "y": 0.0, "z": 0.0},
            "angular": {"x": 0.0, "y": 0.0, "z": 0.0},
        }

    @staticmethod
    def _emergency_stop(params: dict[str, Any]) -> dict[str, Any]:
        return {
            "topic": "/emergencyStopCmd",
            "type": "std_msgs/Bool",
            "data": True,
        }
