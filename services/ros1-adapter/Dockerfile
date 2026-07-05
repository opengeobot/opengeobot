# Function: ROS1 Protocol Adapter container image (F-ADAPTER-002)
# Time: 2026-07-05
# Author: AxeXie
#
# F-ADAPTER-002 scope: protocol translation only — no ROS1 installation is
# required for the translation layer. The adapter produces protocol-specific
# command payloads that the edge Safety Gateway validates. When the ROS1
# Noetic contract is pinned, the base image may switch to a ROS1 Noetic
# runtime behind the same ProtocolAdapter interface.

FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install the project as a package so `python -m opengeobot_ros1.main` resolves.
COPY pyproject.toml ./
COPY src ./src

RUN pip install --no-cache-dir .

ENV ADAPTER_ID=adp_01J00000000000000000000001 \
    ROBOT_ID=rbt_01J00000000000000000000001 \
    PROTOCOL_TYPE=ROS1 \
    NATS_URL=nats://nats:4222 \
    LOG_LEVEL=INFO

# Non-root user for the runtime.
RUN useradd --create-home --uid 1001 ros1
USER ros1

CMD ["python", "-m", "opengeobot_ros1.main"]
