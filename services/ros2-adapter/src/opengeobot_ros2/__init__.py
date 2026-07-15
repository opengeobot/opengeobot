# Function: OpenGeoBot ROS2 Jazzy adapter package
# Time: 2026-07-15
# Author: AxeXie
"""OpenGeoBot ROS2 Jazzy Adapter (F-ADAPTER-003).

Connects to a real ROS2 Jazzy environment via rclpy and executes registered
skills over NATS JetStream. The adapter subscribes to
``opengeobot.dev.edge.ros2.skill.execute.{robot_id}`` and replies with a
``SkillExecutionResponse``. It never publishes ``/cmd_vel`` directly from an
Agent/LLM - all motion goes through registered, versioned Skills after Safety
Gateway ALLOW. The adapter does not maintain a local safety latch; safety is
delegated to the SafetyStateMachine.

When rclpy is unavailable (e.g. unit-test host) the skills fall back to
simulation mode, returning success without touching a real robot.
"""

__version__ = "0.1.0"
