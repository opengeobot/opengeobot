# Function: capture_image skill for ROS2 (perception)
# Time: 2026-07-15
# Author: AxeXie
"""ROS2 ``capture_image`` skill (perception).

turtlesim has no camera, so this skill returns simulated image data regardless
of whether rclpy is available. When a real camera topic is pinned the
implementation will subscribe to the camera image topic behind this same Skill
interface.

Input:  ``{"resolution": str}`` (e.g. "640x480").
Output: ``{"success": bool, "image_path": str, "size_bytes": int}``.
"""

from __future__ import annotations

import re
import uuid
from typing import Any

from loguru import logger

from .base import Ros2SkillBase, SkillContext, SkillResult

_RESOLUTION_RE = re.compile(r"^\s*(\d+)\s*[xX]\s*(\d+)\s*$")


class CaptureImageSkill(Ros2SkillBase):
    skill_id = "capture_image"

    async def execute(
        self, params: dict[str, Any], ctx: SkillContext
    ) -> SkillResult:
        resolution = str(params.get("resolution", "640x480"))
        match = _RESOLUTION_RE.match(resolution)
        if not match:
            return SkillResult(
                success=False,
                error=f"Invalid resolution {resolution!r}; expected <W>x<H>",
            )

        width, height = int(match.group(1)), int(match.group(2))
        if width <= 0 or height <= 0:
            return SkillResult(success=False, error="resolution dimensions must be > 0")

        # Simulate a JPEG capture: 3 bytes per pixel, ~70% JPEG compression.
        size_bytes = int(width * height * 3 * 0.7)
        capture_id = uuid.uuid4().hex
        image_path = f"/ros2/captures/{capture_id}.jpg"

        logger.bind(
            skill=self.skill_id, resolution=resolution, size_bytes=size_bytes
        ).info("capture_image executed (ros2)")
        return SkillResult(
            success=True,
            output={
                "success": True,
                "image_path": image_path,
                "size_bytes": size_bytes,
            },
        )
