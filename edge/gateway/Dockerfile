# Function: Edge gateway container image (F-EDGE-001/002)
# Time: 2026-07-06
# Author: AxeXie
#
# Edge Gateway — NATS bridge, command handler, offline cache and reconciliation.
# Forwards skill executions to the local skill executor and publishes state back
# to the cloud. On NATS reconnect the reconciler flushes cached states.

FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install safety-gateway dependency first (edge-gateway depends on it).
COPY safety-gateway/pyproject.toml ./safety-gateway/
COPY safety-gateway/src ./safety-gateway/src
RUN pip install --no-cache-dir ./safety-gateway

# Install the edge gateway project as a package.
COPY gateway/pyproject.toml ./gateway/
COPY gateway/src ./gateway/src
RUN pip install --no-cache-dir ./gateway

ENV ROBOT_ID=rbt_01J00000000000000000000001 \
    NATS_URL=nats://nats:4222 \
    GATEWAY_ID=edge_01 \
    LOG_LEVEL=INFO

# Non-root user for the runtime.
RUN useradd --create-home --uid 1001 edge
USER edge

CMD ["python", "-m", "opengeobot_edge.main"]
