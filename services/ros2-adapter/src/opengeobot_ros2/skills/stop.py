# Function: stop skill for ROS2 (locomotion)
# Time: 2026-07-15
# Author: AxeXie
"""ROS2 ``stop`` skill (halt current motion).

Publishes a zero ``Twist`` to ``/turtle1/cmd_vel`` to stop the robot, or returns
success in simulation mode when rclpy is unavailable.

Input:  ``{}``.
Output: ``{"success": bool}``.
"""

from __future__ import annotations

from typing import Any

from loguru import logger

from .base import Ros2SkillBase, SkillContext, SkillResult

CMD_VEL_TOPIC = "/turtle1/cmd_vel"


class StopSkill(Ros2SkillBase):
    skill_id = "stop"

    async def execute(
        self, params: dict[str, Any], ctx: SkillContext
    ) -> SkillResult:
        published = self._publish_twist(CMD_VEL_TOPIC)

        logger.bind(skill=self.skill_id, published=published).info(
            "stop executed (ros2)"
        )
        return SkillResult(success=True, output={"success": True})
