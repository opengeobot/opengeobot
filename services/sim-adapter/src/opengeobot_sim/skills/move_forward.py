# Function: move_forward skill simulation (bounded locomotion)
# Time: 2026-07-05
# Author: AxeXie
"""Simulated ``move_forward`` skill (bounded motion).

Input:  ``{"distance": float, "speed": float}``.
Output: ``{"success": bool, "distance": float, "duration": float}``.

The simulation enforces bounded-motion safety limits (max distance and speed)
that mirror the platform R2_BOUNDED_MOTION risk level. Requests outside the
bounds are rejected deterministically so the cloud and Safety Gateway can rely on
a predictable contract.
"""

from __future__ import annotations

import asyncio
from typing import Any

from loguru import logger

from .base import SkillContext, SkillResult

# Bounded-motion safety limits (platform-wide R2 constraints).
MAX_DISTANCE_METERS = 10.0
MAX_SPEED_MPS = 1.5


class MoveForwardSkill:
    skill_id = "move_forward"

    async def execute(self, params: dict[str, Any], ctx: SkillContext) -> SkillResult:
        if ctx.safety_latched:
            return SkillResult(
                success=False, error="Safety latch engaged; motion refused"
            )

        try:
            distance = float(params.get("distance", 0.0))
            speed = float(params.get("speed", 0.0))
        except (TypeError, ValueError) as exc:
            return SkillResult(
                success=False, error=f"Invalid distance/speed parameters ({exc})"
            )

        if distance < 0:
            return SkillResult(success=False, error="distance must be >= 0")
        if speed <= 0:
            return SkillResult(success=False, error="speed must be > 0")
        if distance > MAX_DISTANCE_METERS:
            return SkillResult(
                success=False,
                error=f"distance {distance} exceeds bounded limit {MAX_DISTANCE_METERS}",
            )
        if speed > MAX_SPEED_MPS:
            return SkillResult(
                success=False,
                error=f"speed {speed} exceeds bounded limit {MAX_SPEED_MPS}",
            )

        duration = distance / speed
        # Simulate traversal; capped to keep tests fast.
        await asyncio.sleep(min(duration, ctx.simulation_step) if duration > 0 else 0)

        logger.bind(
            skill=self.skill_id, distance=distance, speed=speed, duration=duration
        ).info("move_forward executed (sim)")
        return SkillResult(
            success=True,
            output={"success": True, "distance": distance, "duration": duration},
        )
