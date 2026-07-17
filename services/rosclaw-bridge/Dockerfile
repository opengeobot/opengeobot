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

# Install build tools (for any C-extension deps) and gosu (for privilege
# dropping in the entrypoint). The entrypoint installs ROSClaw from the
# mounted source as root, then drops to the non-root bridge user.
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        gosu \
    && rm -rf /var/lib/apt/lists/*

# Copy project metadata and source.
COPY pyproject.toml ./pyproject.toml
COPY src ./src
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Install the bridge package.
RUN pip install --no-cache-dir \
        "numpy>=1.24.0" \
        "pyyaml>=6.0" \
        "requests>=2.25.0" \
    && pip install --no-cache-dir .

ENV ROBOT_ID=rbt_01J00000000000000000000001 \
    NATS_URL=nats://nats:4222 \
    GATEWAY_ID=edge_01 \
    ROBOT_ROS_ENDPOINT=ws://ros2-turtlesim:9090 \
    ROSCLAW_PROFILE=offline \
    ROSCLAW_SANDBOX=true \
    LOG_LEVEL=INFO \
    SKILL_REQUEST_TIMEOUT=30.0

# Non-root user for the runtime. The entrypoint runs as root to install
# ROSClaw from the mounted source, then drops to this user via gosu.
RUN useradd --create-home --uid 1001 bridge

# The entrypoint verifies ROSClaw availability, optionally switches to the
# mounted source tree, and drops to bridge.
ENTRYPOINT ["/entrypoint.sh"]
CMD ["python", "-m", "opengeobot_rosclaw_bridge.main"]
