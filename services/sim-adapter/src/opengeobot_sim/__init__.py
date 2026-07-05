# Function: OpenGeoBot ROS2 Simulation Adapter package
# Time: 2026-07-05
# Author: AxeXie
"""OpenGeoBot ROS2 Simulation Adapter (F-ADAPTER-001).

M2 scope: skills are simulated — no real ROS2 installation is required. The
adapter exposes the same capability/skill execution interface the real ROS2
adapter will use, so the cloud → edge → executor loop can be validated
end-to-end. When the ROS2 Jazzy contract is pinned (EXT-ROS2-JAZZY), the skill
implementations will be swapped for rclpy-backed ones behind this same
interface without changing the adapter or the edge gateway.
"""

__version__ = "0.1.0"
