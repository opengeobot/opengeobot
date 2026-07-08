# Function: Agent runtime container image (QwenPaw adapter)
# Time: 2026-07-06
# Author: AxeXie
#
# Agent Runtime — QwenPaw AgentRuntimeProvider adapter for mission planning.
# Subscribes to mission planning requests via NATS and generates UNTRUSTED plan
# proposals via the QwenPaw API. All proposals must be validated by the cloud
# (Schema, permission, state machine, resource, safety) before execution.

FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install the project as a package so `python -m opengeobot_agent.main` resolves.
COPY pyproject.toml ./
COPY src ./src

RUN pip install --no-cache-dir .

ENV NATS_URL=nats://nats:4222 \
    QWENPAW_ENDPOINT=http://localhost:8000/v1/chat/completions \
    QWENPAW_API_KEY="" \
    LOG_LEVEL=INFO

# Non-root user for the runtime.
RUN useradd --create-home --uid 1001 agent
USER agent

CMD ["python", "-m", "opengeobot_agent.main"]
