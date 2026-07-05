# Function: stop skill simulation (locomotion)
# Time: 2026-07-05
# Author: AxeXie
"""Simulated ``stop`` skill (halt current motion).

Input:  ``{}``.
Output: ``{"success": bool}``.
"""

from __future__ import annotations

import asyncio
from typing import Any

from loguru import logger

from .base import SkillContext, SkillResult


class StopSkill:
    skill_id = "stop"

    async def execute(self, params: dict[str, Any], ctx: SkillContext) -> SkillResult:
        # params is intentionally empty for stop; ignore unexpected extras.
        await asyncio.sleep(ctx.simulation_step)
        logger.bind(skill=self.skill_id).info("stop executed (sim)")
        return SkillResult(success=True, output={"success": True})
