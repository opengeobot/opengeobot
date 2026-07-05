# Function: Generic custom protocol adapter (F-ADAPTER-002)
# Time: 2026-07-05
# Author: AxeXie
"""Generic custom protocol translation handler.

Translates platform skill commands into a generic JSON command/response
protocol for robots that do not use ROS1 or Unitree natively. The translated
command carries the skill_id, params and a monotonic sequence number so the
receiving robot can correlate requests and responses.
"""

from __future__ import annotations

import itertools
from typing import Any

from .adapter import TranslationError

# Supported skill commands for the custom protocol.
SUPPORTED_SKILLS = frozenset(
    {"stand_up", "move_forward", "stop", "emergency_stop", "capture_image"}
)


class CustomAdapter:
    """Generic custom protocol translation handler."""

    protocol_type: str = "CUSTOM"

    def __init__(self) -> None:
        self._sequence = itertools.count(1)

    def translate(
        self, skill_id: str, params: dict[str, Any]
    ) -> dict[str, Any]:
        if skill_id not in SUPPORTED_SKILLS:
            raise TranslationError(
                f"Unsupported skill_id '{skill_id}' for CUSTOM protocol"
            )
        return {
            "command": skill_id,
            "topic": f"/custom/{skill_id}",
            "type": "opengeobot/CustomCommand",
            "seq": next(self._sequence),
            "params": dict(params),
            "expected_response": {
                "type": "opengeobot/CustomResponse",
                "success_field": "ok",
            },
        }
