# Function: capture_image skill simulation (perception)
# Time: 2026-07-05
# Author: AxeXie
"""Simulated ``capture_image`` skill (perception).

Input:  ``{"resolution": str}`` (e.g. "640x480").
Output: ``{"success": bool, "image_path": str, "size_bytes": int}``.
"""

from __future__ import annotations

import asyncio
import re
import uuid
from typing import Any

from loguru import logger

from .base import SkillContext, SkillResult

_RESOLUTION_RE = re.compile(r"^\s*(\d+)\s*[xX]\s*(\d+)\s*$")


class CaptureImageSkill:
    skill_id = "capture_image"

    async def execute(self, params: dict[str, Any], ctx: SkillContext) -> SkillResult:
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

        await asyncio.sleep(ctx.simulation_step)

        # Simulate a JPEG capture: 3 bytes per pixel, ~70% JPEG compression.
        size_bytes = int(width * height * 3 * 0.7)
        capture_id = uuid.uuid4().hex
        image_path = f"/sim/captures/{capture_id}.jpg"

        logger.bind(
            skill=self.skill_id, resolution=resolution, size_bytes=size_bytes
        ).info("capture_image executed (sim)")
        return SkillResult(
            success=True,
            output={
                "success": True,
                "image_path": image_path,
                "size_bytes": size_bytes,
            },
        )
