# Function: emergency_stop skill for ROS2 (safety)
# Time: 2026-07-15
# Author: AxeXie
"""ROS2 ``emergency_stop`` skill.

Publishes a zero ``Twist`` to ``/turtle1/cmd_vel`` to halt all motion, or
returns success in simulation mode when rclpy is unavailable. The local
emergency stop is latching in the Safety Gateway; the adapter itself does not
maintain a safety latch.

Input:  ``{}``.
Output: ``{"success": bool, "stopped_missions": int}``.
"""

from __future__ import annotations

from typing import Any

from loguru import logger

from .base import Ros2SkillBase, SkillContext, SkillResult

CMD_VEL_TOPIC = "/turtle1/cmd_vel"


class EmergencyStopSkill(Ros2SkillBase):
    skill_id = "emergency_stop"

    async def execute(
        self, params: dict[str, Any], ctx: SkillContext
    ) -> SkillResult:
        stopped = ctx.active_executions
        published = self._publish_twist(CMD_VEL_TOPIC)

        logger.bind(
            skill=self.skill_id, stopped_missions=stopped, published=published
        ).warning("emergency_stop executed (ros2); motion halted")
        return SkillResult(
            success=True,
            output={"success": True, "stopped_missions": stopped},
        )
