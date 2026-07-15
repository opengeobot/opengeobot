# Function: ROS2 skill protocol and base class (F-ADAPTER-003)
# Time: 2026-07-15
# Author: AxeXie
"""Shared protocol, result types and ROS2 skill base for the ROS2 adapter.

Kept in a dedicated module so the individual skill modules can import these
types without creating a circular import with ``skills/__init__``.

rclpy is imported gracefully so that unit tests can run without a ROS2 Jazzy
installation. When rclpy is unavailable the skills fall back to a simulation
mode that returns success without publishing.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Protocol, runtime_checkable

# ---------------------------------------------------------------------------
# Graceful rclpy import
# ---------------------------------------------------------------------------
# rclpy and the ROS2 message types are only available on a ROS2 Jazzy host.
# Unit tests run on plain Python 3.12 where these packages are absent.
# When the import fails every name is set to ``None`` and ``RCLPY_AVAILABLE``
# is ``False``; the skills then operate in simulation mode.
try:
    import rclpy  # noqa: F401
    from geometry_msgs.msg import Twist
    from rclpy.node import Node
    from turtlesim.srv import TeleportAbsolute

    RCLPY_AVAILABLE = True
except ImportError:  # pragma: no cover - exercised only on non-ROS hosts
    rclpy = None
    Node = None
    Twist = None
    TeleportAbsolute = None
    RCLPY_AVAILABLE = False


__all__ = [
    "Node",
    "RCLPY_AVAILABLE",
    "Ros2SkillBase",
    "Skill",
    "SkillContext",
    "SkillResult",
    "TeleportAbsolute",
    "Twist",
    "rclpy",
]


@dataclass(frozen=True)
class SkillContext:
    """Adapter-level context passed into each skill execution.

    ``active_executions`` is the count of in-flight skill executions at the
    moment the skill is invoked; ``safety_latched`` reflects the Safety Gateway
    latch state. The ROS2 adapter does **not** maintain its own safety latch -
    safety is delegated to the SafetyStateMachine.
    """

    active_executions: int = 0
    safety_latched: bool = False
    simulation_step: float = 0.05


@dataclass
class SkillResult:
    """Normalized result of a skill execution."""

    success: bool
    output: dict[str, Any] = field(default_factory=dict)
    error: str | None = None


@runtime_checkable
class Skill(Protocol):
    """Capability port every skill implementation must satisfy."""

    skill_id: str

    async def execute(
        self, params: dict[str, Any], ctx: SkillContext
    ) -> SkillResult: ...


class Ros2SkillBase(ABC):
    """Abstract base class for ROS2-backed skill implementations.

    Each concrete skill inherits from this class and implements ``execute()``.
    When an rclpy ``Node`` is provided the skill publishes real ROS2 messages;
    when the node is ``None`` or rclpy is unavailable the skill falls back to
    simulation mode, returning success without touching the robot.

    rclpy publish is non-blocking, so it can be called directly inside the
    async ``execute()`` without spinning the node.
    """

    skill_id: str

    def __init__(self, node: Node | None = None) -> None:
        self._node = node
        self._publishers: dict[str, Any] = {}
        if RCLPY_AVAILABLE and node is not None:
            self._setup_publishers(node)

    def _setup_publishers(self, node: Node) -> None:  # noqa: B027
        """Hook for subclasses to create ROS2 publishers on the given node."""

    def _get_or_create_publisher(
        self, topic: str, msg_type: Any, qos_depth: int = 10
    ) -> Any | None:
        """Return an existing publisher or create one on the node.

        Returns ``None`` when rclpy is unavailable or no node was provided.
        """
        if not RCLPY_AVAILABLE or self._node is None:
            return None
        if topic not in self._publishers:
            self._publishers[topic] = self._node.create_publisher(
                msg_type, topic, qos_depth
            )
        return self._publishers[topic]

    def _publish_twist(
        self, topic: str, *, linear_x: float = 0.0, angular_z: float = 0.0
    ) -> bool:
        """Publish a ``Twist`` message to *topic*.

        Returns ``True`` when the message was published to a real ROS2 topic,
        ``False`` when operating in simulation mode.
        """
        pub = self._get_or_create_publisher(topic, Twist)
        if pub is None:
            return False
        msg = Twist()
        msg.linear.x = float(linear_x)
        msg.angular.z = float(angular_z)
        pub.publish(msg)
        return True

    @abstractmethod
    async def execute(
        self, params: dict[str, Any], ctx: SkillContext
    ) -> SkillResult:
        """Execute the skill. Must be implemented by subclasses."""
        ...
