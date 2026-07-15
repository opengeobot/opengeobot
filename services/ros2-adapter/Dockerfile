# Function: ROS2 Jazzy Adapter container image (F-ADAPTER-003)
# Time: 2026-07-15
# Author: AxeXie
#
# Connects to a real ROS2 Jazzy environment via rclpy. The base image is pinned
# to ros:jazzy-ros-core (no latest). turtlesim and geometry_msgs are installed
# for the default skill set.

FROM ros:jazzy-ros-core AS base

# Install Python packages and ROS2 message dependencies.
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3-pip \
    python3-venv \
    ros-jazzy-turtlesim \
    ros-jazzy-geometry-msgs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install the project as a package so `python -m opengeobot_ros2.main` resolves.
COPY pyproject.toml ./
COPY src ./src
RUN pip3 install --no-cache-dir .

ENV ROBOT_ID=rbt_01J00000000000000000000001 \
    NATS_URL=nats://nats:4222 \
    ROS_DOMAIN_ID=42 \
    LOG_LEVEL=INFO

# Non-root user for the runtime.
RUN useradd --create-home --uid 1001 ros2
USER ros2

CMD ["python3", "-m", "opengeobot_ros2.main"]
