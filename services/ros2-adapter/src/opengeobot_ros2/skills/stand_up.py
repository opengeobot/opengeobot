# Function: stand_up skill for ROS2 (locomotion)
# Time: 2026-07-15
# Author: AxeXie
"""ROS2 ``stand_up`` skill.

Calls the ``/turtle1/teleport_absolute`` service to reset the turtle posture,
or returns success in simulation mode when rclpy is unavailable.

Input:  ``{"duration": float}`` (seconds the robot holds the stand posture).
Output: ``{"success": bool, "duration": float}``.
"""

from __future__ import annotations

from typing import Any

from loguru import logger

from .base import RCLPY_AVAILABLE, Ros2SkillBase, SkillContext, SkillResult, TeleportAbsolute


class StandUpSkill(Ros2SkillBase):
    skill_id = "stand_up"

    async def execute(
        self, params: dict[str, Any], ctx: SkillContext
    ) -> SkillResult:
        raw_duration = params.get("duration", 0.0)
        try:
            duration = float(raw_duration)
        except (TypeError, ValueError) as exc:
            return SkillResult(
                success=False, error=f"Invalid duration: {raw_duration!r} ({exc})"
            )
        if duration < 0:
            return SkillResult(success=False, error="duration must be >= 0")

        published = False
        if RCLPY_AVAILABLE and self._node is not None and TeleportAbsolute is not None:
            client = self._node.create_client(
                TeleportAbsolute, "/turtle1/teleport_absolute"
            )
            req = TeleportAbsolute.Request()
            req.x = 5.5
            req.y = 5.5
            req.theta = 0.0
            client.call_async(req)
            published = True

        logger.bind(
            skill=self.skill_id, duration=duration, published=published
        ).info("stand_up executed (ros2)")
        return SkillResult(
            success=True, output={"success": True, "duration": duration}
        )
