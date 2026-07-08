# Function: Local skill executor container image (F-EDGE-002 edge)
# Time: 2026-07-06
# Author: AxeXie
#
# Local Skill Executor — receives Safety-Gateway-approved skill requests and
# dispatches to the appropriate adapter (simulation / ROS2 / ROS1).

FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install the project as a package so `python -m opengeobot_skill_executor.main` resolves.
COPY pyproject.toml ./
COPY src ./src

RUN pip install --no-cache-dir .

ENV GATEWAY_ID=edge_01 \
    NATS_URL=nats://nats:4222 \
    DEFAULT_ADAPTER_TYPE=simulation \
    LOG_LEVEL=INFO

# Non-root user for the runtime.
RUN useradd --create-home --uid 1001 executor
USER executor

CMD ["python", "-m", "opengeobot_skill_executor.main"]
