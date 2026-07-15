# Function: ROS1 rospy native protocol adapter (F-ADAPTER-002)
# Time: 2026-07-15
# Author: AxeXie
"""ROS1 native protocol translation handler using rospy.

Translates platform skill commands into real ROS1 topic messages published
via rospy to ``/turtle1/cmd_vel`` (geometry_msgs/Twist). This adapter is
intended for the turtlesim simulation target.

The adapter implements the ``ProtocolAdapter`` protocol. Motion commands
flow through registered Skills that are validated by the edge Safety Gateway
before reaching this adapter - the adapter does not bypass safety checks.

rospy is imported gracefully so that unit tests can run without a ROS1
installation. When rospy is unavailable, ``translate()`` returns the correct
translated dict but skips the actual publish.
"""

from __future__ import annotations

from typing import Any

from loguru import logger

from .adapter import TranslationError

# Default forward speed for move_forward (m/s). The Safety Gateway may clamp this.
DEFAULT_FORWARD_SPEED = 0.5

# turtlesim cmd_vel topic name.
CMD_VEL_TOPIC = "/turtle1/cmd_vel"

# Attempt to import rospy and geometry_msgs gracefully. Unit tests and CI
# environments that do not have ROS1 installed must still be able to import
# this module and exercise the translate() logic.
try:
    import rospy
    from geometry_msgs.msg import Twist

    _ROSPY_AVAILABLE = True
except ImportError:  # pragma: no cover - exercised in environments without ROS1
    rospy = None  # type: ignore[assignment]
    Twist = None  # type: ignore[assignment,misc]
    _ROSPY_AVAILABLE = False


class Ros1NativeAdapter:
    """ROS1 native protocol translation handler backed by rospy.

    Publishes ``geometry_msgs/Twist`` messages to ``/turtle1/cmd_vel`` for
    motion skills. When rospy is not installed the adapter falls back to
    returning the translated dict without publishing, so the translation
    contract remains testable.
    """

    protocol_type: str = "ROS1_NATIVE"

    def __init__(self, ros_master_uri: str, node_name: str) -> None:
        self._ros_master_uri = ros_master_uri
        self._node_name = node_name
        self._publisher: Any = None

        if _ROSPY_AVAILABLE:
            self._init_rospy()
        else:
            logger.warning(
                "rospy not available; Ros1NativeAdapter will return translations"
                " without publishing"
            )

    def _init_rospy(self) -> None:
        """Initialize the rospy node and create the cmd_vel publisher."""
        # ROS_MASTER_URI must be set before rospy.init_node takes effect.
        import os

        os.environ.setdefault("ROS_MASTER_URI", self._ros_master_uri)
        try:
            if not rospy.core.is_initialized():  # type: ignore[union-attr]
                rospy.init_node(self._node_name, anonymous=True)  # type: ignore[union-attr]
            self._publisher = rospy.Publisher(  # type: ignore[union-attr]
                CMD_VEL_TOPIC, Twist, queue_size=10
            )
            logger.bind(
                ros_master_uri=self._ros_master_uri,
                node_name=self._node_name,
                topic=CMD_VEL_TOPIC,
            ).info("Ros1NativeAdapter rospy node initialised")
        except Exception as exc:  # noqa: BLE001 - rospy init failure must not crash adapter
            logger.bind(error=str(exc)).warning(
                "Failed to initialise rospy node; falling back to dict-only mode"
            )
            self._publisher = None

    def translate(
        self, skill_id: str, params: dict[str, Any]
    ) -> dict[str, Any]:
        if skill_id == "move_forward":
            return self._move_forward(params)
        if skill_id == "stop":
            return self._stop(params)
        if skill_id == "emergency_stop":
            return self._emergency_stop(params)
        if skill_id == "stand_up":
            return self._stand_up(params)
        if skill_id == "capture_image":
            return self._capture_image(params)
        raise TranslationError(
            f"Unsupported skill_id '{skill_id}' for ROS1_NATIVE protocol"
        )

    # ------------------------------------------------------------------
    # Motion skills - publish Twist to /turtle1/cmd_vel.
    # ------------------------------------------------------------------

    def _move_forward(self, params: dict[str, Any]) -> dict[str, Any]:
        speed = float(params.get("speed", DEFAULT_FORWARD_SPEED))
        duration = float(params.get("duration", 1.0))
        message = self._build_twist(linear_x=speed)
        self._publish_twist(message)
        return {
            "topic": CMD_VEL_TOPIC,
            "type": "geometry_msgs/Twist",
            "linear": {"x": speed, "y": 0.0, "z": 0.0},
            "angular": {"x": 0.0, "y": 0.0, "z": 0.0},
            "duration": duration,
            "published": self._publisher is not None,
        }

    def _stop(self, params: dict[str, Any]) -> dict[str, Any]:
        message = self._build_twist(linear_x=0.0)
        self._publish_twist(message)
        return {
            "topic": CMD_VEL_TOPIC,
            "type": "geometry_msgs/Twist",
            "linear": {"x": 0.0, "y": 0.0, "z": 0.0},
            "angular": {"x": 0.0, "y": 0.0, "z": 0.0},
            "published": self._publisher is not None,
        }

    def _emergency_stop(self, params: dict[str, Any]) -> dict[str, Any]:
        message = self._build_twist(linear_x=0.0)
        self._publish_twist(message)
        return {
            "topic": CMD_VEL_TOPIC,
            "type": "geometry_msgs/Twist",
            "linear": {"x": 0.0, "y": 0.0, "z": 0.0},
            "angular": {"x": 0.0, "y": 0.0, "z": 0.0},
            "emergency_stop": True,
            "published": self._publisher is not None,
        }

    # ------------------------------------------------------------------
    # Non-motion skills - return dict without publishing.
    # ------------------------------------------------------------------

    @staticmethod
    def _stand_up(params: dict[str, Any]) -> dict[str, Any]:
        # turtlesim has no stand_up service; return success info for the
        # translation contract.
        return {
            "service": "/stand_up",
            "type": "std_srvs/Trigger",
            "success": True,
            "message": "stand_up not applicable in turtlesim; returning success",
            "duration": params.get("duration", 2.0),
        }

    @staticmethod
    def _capture_image(params: dict[str, Any]) -> dict[str, Any]:
        # turtlesim has no camera; return simulated capture data.
        resolution = params.get("resolution", "640x480")
        return {
            "topic": "/image_raw",
            "type": "sensor_msgs/Image",
            "simulated": True,
            "resolution": resolution,
            "message": "capture_image simulated; turtlesim has no camera",
        }

    # ------------------------------------------------------------------
    # Helpers.
    # ------------------------------------------------------------------

    @staticmethod
    def _build_twist(linear_x: float) -> Any:
        """Build a geometry_msgs/Twist message with only linear.x set.

        Returns ``None`` when rospy/geometry_msgs are not available; callers
        should check ``_publisher`` before relying on the return value.
        """
        if not _ROSPY_AVAILABLE or Twist is None:
            return None
        twist = Twist()
        twist.linear.x = linear_x
        twist.linear.y = 0.0
        twist.linear.z = 0.0
        twist.angular.x = 0.0
        twist.angular.y = 0.0
        twist.angular.z = 0.0
        return twist

    def _publish_twist(self, twist_msg: Any) -> None:
        """Publish a Twist message if rospy and the publisher are available."""
        if self._publisher is None or twist_msg is None:
            return
        try:
            self._publisher.publish(twist_msg)
        except Exception as exc:  # noqa: BLE001 - publish failure must not crash adapter
            logger.bind(error=str(exc), topic=CMD_VEL_TOPIC).warning(
                "Failed to publish Twist message"
            )
