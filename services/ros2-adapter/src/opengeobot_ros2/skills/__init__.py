# Function: Skill registry for the ROS2 adapter
# Time: 2026-07-15
# Author: AxeXie
"""Skill registry for the ROS2 adapter.

Re-exports the shared protocol/types from ``base`` and registers the default
skill set. Each skill is a registered, versioned action; the adapter never
invokes ``/cmd_vel`` or vendor SDKs directly - all motion goes through
registered Skills after Safety Gateway ALLOW.

When rclpy is available the skills publish real ROS2 messages; when rclpy is
unavailable they fall back to simulation mode.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from .base import Skill, SkillContext, SkillResult
from .capture_image import CaptureImageSkill
from .emergency_stop import EmergencyStopSkill
from .move_forward import MoveForwardSkill
from .stand_up import StandUpSkill
from .stop import StopSkill

if TYPE_CHECKING:
    from .base import Node


def default_skills(node: Node | None = None) -> dict[str, Skill]:
    """Return the default skill registry keyed by ``skill_id``.

    Args:
        node: Optional rclpy ``Node`` for real ROS2 publishing. When ``None``
            (or when rclpy is unavailable) skills operate in simulation mode.
    """
    skills: list[Skill] = [
        StandUpSkill(node=node),
        StopSkill(node=node),
        MoveForwardSkill(node=node),
        CaptureImageSkill(node=node),
        EmergencyStopSkill(node=node),
    ]
    registry: dict[str, Skill] = {}
    for skill in skills:
        if skill.skill_id in registry:
            raise ValueError(f"Duplicate skill_id in registry: {skill.skill_id}")
        registry[skill.skill_id] = skill
    return registry


__all__ = [
    "CaptureImageSkill",
    "EmergencyStopSkill",
    "MoveForwardSkill",
    "Skill",
    "SkillContext",
    "SkillResult",
    "StandUpSkill",
    "StopSkill",
    "default_skills",
]
