# Function: ROS2 Simulation Adapter container image (M2 simulated execution)
# Time: 2026-07-05
# Author: AxeXie
#
# M2 scope: simulated skill execution only — no ROS2 installation is required.
# When EXT-ROS2-JAZZY is pinned, the base image and skill implementations will
# switch to a ROS2 Jazzy runtime behind the same Skill protocol.

FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install the project as a package so `python -m opengeobot_sim.main` resolves.
COPY pyproject.toml ./
COPY src ./src

RUN pip install --no-cache-dir .

ENV ROBOT_ID=rbt_01J00000000000000000000001 \
    NATS_URL=nats://nats:4222 \
    LOG_LEVEL=INFO

# Non-root user for the runtime.
RUN useradd --create-home --uid 1001 sim
USER sim

CMD ["python", "-m", "opengeobot_sim.main"]
