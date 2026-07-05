# Function: ROS1 protocol translation interface (F-ADAPTER-002)
# Time: 2026-07-05
# Author: AxeXie
"""Protocol translation interface for ROS1-compatible robot protocols.

Every concrete protocol handler (Unitree, custom, ROS1 native) implements the
``ProtocolAdapter`` protocol. The adapter never directly publishes ``/cmd_vel``
or calls vendor SDKs — it produces a translated command payload that the edge
Safety Gateway validates before execution.
"""

from __future__ import annotations

from typing import Any, Protocol, runtime_checkable


@runtime_checkable
class ProtocolAdapter(Protocol):
    """Protocol port every robot-protocol translation handler must satisfy."""

    protocol_type: str

    def translate(
        self, skill_id: str, params: dict[str, Any]
    ) -> dict[str, Any]:
        """Translate a platform skill command into a robot-native protocol message.

        Args:
            skill_id: Platform skill identifier (e.g. ``stand_up``).
            params: Skill-specific input parameters.

        Returns:
            A dict representing the protocol-specific translated command
            payload. The shape depends on ``protocol_type``.
        """
        ...


class TranslationError(Exception):
    """Raised when a skill command cannot be translated to the target protocol."""
