# Function: MCP tool gateway container image (F-MCP-001)
# Time: 2026-07-06
# Author: AxeXie
#
# MCP Tool Gateway — tool registry, canary/stable routing, and invocation handling.
# Subscribes to tool registration, invocation, list and unregister subjects via NATS.

FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install the project as a package so `python -m opengeobot_mcp_gateway.main` resolves.
COPY pyproject.toml ./
COPY src ./src

RUN pip install --no-cache-dir .

ENV NATS_URL=nats://nats:4222 \
    LOG_LEVEL=INFO

# Non-root user for the runtime.
RUN useradd --create-home --uid 1001 mcp
USER mcp

CMD ["python", "-m", "opengeobot_mcp_gateway.main"]
