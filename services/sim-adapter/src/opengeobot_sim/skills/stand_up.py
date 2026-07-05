# Function: stand_up skill simulation (locomotion)
# Time: 2026-07-05
# Author: AxeXie
"""Simulated ``stand_up`` skill.

Input:  ``{"duration": float}`` (seconds the robot holds the stand posture).
Output: ``{"success": bool, "duration": float}``.
"""

from __future__ import annotations

import asyncio
from typing import Any

from loguru import logger

from .base import SkillContext, SkillResult


class StandUpSkill:
    skill_id = "stand_up"

    async def execute(self, params: dict[str, Any], ctx: SkillContext) -> SkillResult:
        raw_duration = params.get("duration", 0.0)
        try:
            duration = float(raw_duration)
        except (TypeError, ValueError) as exc:
            return SkillResult(
                success=False, error=f"Invalid duration: {raw_duration!r} ({exc})"
            )
        if duration < 0:
            return SkillResult(success=False, error="duration must be >= 0")

        # Simulate posture servo settling; capped to keep tests fast.
        await asyncio.sleep(min(duration, ctx.simulation_step) if duration > 0 else 0)

        logger.bind(skill=self.skill_id, duration=duration).info("stand_up executed (sim)")
        return SkillResult(success=True, output={"success": True, "duration": duration})
