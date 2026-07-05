# Function: emergency_stop skill simulation (safety)
# Time: 2026-07-05
# Author: AxeXie
"""Simulated ``emergency_stop`` skill.

Input:  ``{}``.
Output: ``{"success": bool, "stopped_missions": int}``.

The adapter passes the current count of in-flight skill executions via the
``SkillContext`` so the result reports how many missions were halted. The local
emergency stop is latching and does not depend on the cloud or network.
"""

from __future__ import annotations

import asyncio
from typing import Any

from loguru import logger

from .base import SkillContext, SkillResult


class EmergencyStopSkill:
    skill_id = "emergency_stop"

    async def execute(self, params: dict[str, Any], ctx: SkillContext) -> SkillResult:
        # params is intentionally empty; the stopped count comes from the adapter
        # context so the skill stays a pure function of its inputs.
        await asyncio.sleep(ctx.simulation_step)
        stopped = ctx.active_executions
        logger.bind(skill=self.skill_id, stopped_missions=stopped).warning(
            "emergency_stop executed (sim); motion latched"
        )
        return SkillResult(
            success=True,
            output={"success": True, "stopped_missions": stopped},
        )
