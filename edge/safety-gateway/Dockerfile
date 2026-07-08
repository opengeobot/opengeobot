# Function: Safety Gateway container image (F-SAFETY-001 edge)
# Time: 2026-07-06
# Author: AxeXie
#
# Edge Safety Gateway — emergency stop latching, action-level safety checks,
# skill execution interception. Operates entirely local; NATS is optional for
# state broadcast but never required for safety decisions.

FROM python:3.12-slim AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Install the project as a package so `python -m opengeobot_safety_gateway.main` resolves.
COPY pyproject.toml ./
COPY src ./src

RUN pip install --no-cache-dir .

ENV GATEWAY_ID=edge_01 \
    NATS_URL=nats://nats:4222 \
    SAFETY_HEALTH_CHECK_PORT=8081 \
    LOG_LEVEL=INFO

# Non-root user for the runtime.
RUN useradd --create-home --uid 1001 safety
USER safety

EXPOSE 8081

CMD ["python", "-m", "opengeobot_safety_gateway.main"]
