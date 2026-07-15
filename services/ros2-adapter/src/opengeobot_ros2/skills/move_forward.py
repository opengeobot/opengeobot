# Function: move_forward skill for ROS2 (bounded locomotion)
# Time: 2026-07-15
# Author: AxeXie
"""ROS2 ``move_forward`` skill (bounded motion).

Publishes a ``Twist`` with ``linear.x > 0`` to ``/turtle1/cmd_vel``, or returns
success in simulation mode when rclpy is unavailable.

Input:  ``{"distance": float, "speed": float}``.
Output: ``{"success": bool, "distance": float, "duration": float}``.

The skill enforces bounded-motion safety limits (max distance and speed) that
mirror the platform R2_BOUNDED_MOTION risk level.
"""

from __future__ import annotations

from typing import Any

from loguru import logger

from .base import Ros2SkillBase, SkillContext, SkillResult

CMD_VEL_TOPIC = "/turtle1/cmd_vel"

# Bounded-motion safety limits (platform-wide R2 constraints).
MAX_DISTANCE_METERS = 10.0
MAX_SPEED_MPS = 1.5


class MoveForwardSkill(Ros2SkillBase):
    skill_id = "move_forward"

    async def execute(
        self, params: dict[str, Any], ctx: SkillContext
    ) -> SkillResult:
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
        published = self._publish_twist(
            CMD_VEL_TOPIC, linear_x=speed, angular_z=0.0
        )

        logger.bind(
            skill=self.skill_id,
            distance=distance,
            speed=speed,
            duration=duration,
            published=published,
        ).info("move_forward executed (ros2)")
        return SkillResult(
            success=True,
            output={"success": True, "distance": distance, "duration": duration},
        )
