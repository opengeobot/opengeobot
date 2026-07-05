# Function: Skill registry for the simulation adapter
# Time: 2026-07-05
# Author: AxeXie
"""Skill registry for the simulation adapter.

Re-exports the shared protocol/types from ``base`` and registers the M2 default
skill set. Each skill is a registered, versioned action; the adapter never
invokes ``/cmd_vel`` or vendor SDKs directly.
"""

from __future__ import annotations

from .base import Skill, SkillContext, SkillResult
from .capture_image import CaptureImageSkill
from .emergency_stop import EmergencyStopSkill
from .move_forward import MoveForwardSkill
from .stand_up import StandUpSkill
from .stop import StopSkill


def default_skills() -> dict[str, Skill]:
    """Return the M2 default skill registry keyed by skill_id."""
    skills: list[Skill] = [
        StandUpSkill(),
        StopSkill(),
        MoveForwardSkill(),
        CaptureImageSkill(),
        EmergencyStopSkill(),
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
