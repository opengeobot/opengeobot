# Function: ROS1 Protocol Adapter container image (F-ADAPTER-002)
# Time: 2026-07-15
# Author: AxeXie
#
# F-ADAPTER-002 scope: protocol translation with ROS1 native rospy support.
# The base image provides ROS1 Noetic so the adapter can publish real
# geometry_msgs/Twist messages to /turtle1/cmd_vel. Motion commands still
# flow through registered Skills validated by the edge Safety Gateway before
# reaching this adapter.

FROM ros:noetic-ros-core AS base

RUN apt-get update && apt-get install -y --no-install-recommends \
    python3-pip \
    ros-noetic-turtlesim \
    ros-noetic-geometry-msgs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY pyproject.toml ./
COPY src ./src
RUN pip3 install --no-cache-dir .

ENV ADAPTER_ID=adp_01J00000000000000000000001 \
    ROBOT_ID=rbt_01J00000000000000000000001 \
    NATS_URL=nats://nats:4222 \
    PROTOCOL_TYPE=ROS1_NATIVE \
    ROS_MASTER_URI=http://ros1-roscore:11311 \
    LOG_LEVEL=INFO

RUN useradd --create-home --uid 1001 ros1
USER ros1

CMD ["python3", "-m", "opengeobot_ros1.main"]
