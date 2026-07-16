# Function: ROSClaw NATS Bridge container image
# Time: 2026-07-16
# Author: AxeXie
#
# Bridges the OpenGeoBot edge NATS pipeline to the ROSClaw Edge Runtime.
# When the rosclaw Python package is installed in the image, skill requests
# are dispatched through ROSClaw's SkillExecutor. Otherwise the bridge runs
# in degraded fallback mode and responds with structured failure messages.

FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install uv for fast dependency resolution.
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

# Copy project metadata and source.
COPY pyproject.toml ./
COPY src ./src

# Install the project as a package so `python -m opengeobot_rosclaw_bridge.main` resolves.
RUN uv pip install --system .

ENV ROBOT_ID=rbt_01J00000000000000000000001 \
    NATS_URL=nats://nats:4222 \
    GATEWAY_ID=edge_01 \
    ROBOT_ROS_ENDPOINT=ws://ros2-turtlesim:9090 \
    ROSCLAW_PROFILE=offline \
    ROSCLAW_SANDBOX=true \
    LOG_LEVEL=INFO \
    SKILL_REQUEST_TIMEOUT=30.0

# Non-root user for the runtime.
RUN useradd --create-home --uid 1001 bridge
USER bridge

CMD ["python", "-m", "opengeobot_rosclaw_bridge.main"]
