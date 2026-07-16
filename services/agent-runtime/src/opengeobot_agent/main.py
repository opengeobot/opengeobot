# Function: Agent runtime asyncio entry point
# Time: 2026-07-06
# Author: AxeXie
"""Agent runtime entry point.

Wires configuration, NATS connection, QwenPaw provider and the planning request
handler. The runtime subscribes to the mission planning request subject and
generates UNTRUSTED plan proposals via the QwenPaw API.

Run directly:
    python -m opengeobot_agent.main
or:
    python src/opengeobot_agent/main.py
"""

from __future__ import annotations

import asyncio
import json
import signal
import sys

from loguru import logger

from .config import AgentConfig
from .handler import PlanningRequestHandler
from .initializer import AgentInitializer
from .nats_client import NatsBridge
from .provider import NatsSkillRegistry, QwenPawProvider

# Default skill names bound to the QwenPaw agent when the platform skill
# registry cannot be enumerated (e.g. NATS skill.list has no responders).
DEFAULT_SKILL_NAMES: list[str] = [
    "stand_up",
    "move_forward",
    "stop",
    "capture_image",
]


def _configure_logging(level: str) -> None:
    logger.remove()
    logger.add(
        sys.stderr,
        level=level,
        format=(
            "<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | "
            "<level>{level: <8}</level> | "
            "<cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - "
            "<level>{message}</level>"
        ),
        backtrace=False,
        diagnose=False,
    )


class AgentRuntime:
    """Top-level orchestrator for the agent runtime."""

    def __init__(self, config: AgentConfig) -> None:
        self._config = config
        self._nats = NatsBridge(config)
        skill_registry = NatsSkillRegistry(
            self._nats,
            subject=config.skill_list_subject,
            timeout=config.skill_request_timeout,
        )
        self._provider = QwenPawProvider(config, skill_registry=skill_registry)
        self._handler = PlanningRequestHandler(config, self._nats, self._provider)
        self._initializer = AgentInitializer(config)
        self._stop_event = asyncio.Event()

    async def start(self) -> None:
        await self._nats.connect()
        await self._nats.ensure_stream()

        # Initialize (or verify) the persistent QwenPaw agent before subscribing
        # so plan requests carry agent context. This MUST NOT block startup on
        # failure - the initializer degrades to stateless mode instead.
        skills = await self._resolve_skill_names()
        await self._initializer.initialize(skill_names=skills)
        if self._initializer.is_initialized:
            logger.info(
                "QwenPaw agent initialized: {}", self._initializer.agent_id
            )
            self._provider.set_agent_context(
                self._initializer.agent_id, skills
            )
        else:
            logger.warning(
                "Running in stateless mode (QwenPaw agent not initialized)"
            )

        await self._nats.subscribe_js(
            self._config.plan_request_subject,
            self._handler.handle_plan_request,
            durable=self._config.js_durable_consumer,
        )
        await self._nats.subscribe_js(
            self._config.replan_request_subject,
            self._handler.handle_replan_request,
            durable=self._config.js_durable_consumer + "-replan",
        )
        logger.info(
            "Agent runtime started - JetStream durable consumer '{}' "
            "subscribed to {} and {}",
            self._config.js_durable_consumer,
            self._config.plan_request_subject,
            self._config.replan_request_subject,
        )

    async def _resolve_skill_names(self) -> list[str]:
        """Resolve the skill names to bind to the QwenPaw agent.

        Attempts to enumerate skills via the NATS skill.list subject; falls
        back to DEFAULT_SKILL_NAMES when no responder is available.
        """
        try:
            reply = await self._nats.request(
                self._config.skill_list_subject,
                b"{}",
                self._config.skill_request_timeout,
            )
            data = json.loads(reply.data)
            skills = data.get("skill_names") or data.get("skills") or []
            if isinstance(skills, list) and skills:
                return [str(s) for s in skills]
        except Exception as exc:  # noqa: BLE001 - fallback to defaults
            logger.bind(error=str(exc)).debug(
                "Skill list query failed; using default skill names"
            )
        return DEFAULT_SKILL_NAMES

    async def stop(self) -> None:
        logger.info("Agent runtime stopping...")
        await self._nats.drain_and_close()
        self._stop_event.set()

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()


async def _run() -> None:
    config = AgentConfig.from_env()
    _configure_logging(config.log_level)
    runtime = AgentRuntime(config)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.create_task(runtime.stop()))
        except NotImplementedError:
            # Signals are not available on all platforms (e.g. Windows Proactor).
            pass

    await runtime.start()
    await runtime.wait_for_shutdown()


def main() -> None:
    try:
        asyncio.run(_run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
