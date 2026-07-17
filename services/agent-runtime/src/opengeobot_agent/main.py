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

# Skill names currently verified to execute through the edge ROSClaw chain.
# The platform registry may publish a broader catalogue, but the QwenPaw agent
# must only see skills that the live edge execution path can actually handle.
DEFAULT_SKILL_NAMES: list[str] = [
    "move_forward",
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

        await self._nats.subscribe(
            self._config.plan_request_subject,
            self._handler.handle_plan_request,
        )
        await self._nats.subscribe(
            self._config.replan_request_subject,
            self._handler.handle_replan_request,
        )
        logger.info(
            "Agent runtime started - core NATS request-reply subscribed to {} and {}",
            self._config.plan_request_subject,
            self._config.replan_request_subject,
        )

    async def _resolve_skill_names(self) -> list[str]:
        """Resolve the skill names to bind to the QwenPaw agent.

        Attempts to enumerate skills via the NATS skill.list subject; falls
        back to DEFAULT_SKILL_NAMES when no responder is available. Published
        skills are filtered to the subset currently verified as executable via
        the edge ROSClaw path so planning does not emit unsupported actions.
        """
        try:
            reply = await self._nats.request(
                self._config.skill_list_subject,
                b"{}",
                self._config.skill_request_timeout,
            )
            data = json.loads(reply.data)
            if isinstance(data, list):
                names = [
                    str(item.get("name", "")).strip()
                    for item in data
                    if isinstance(item, dict)
                    and str(item.get("name", "")).strip()
                ]
                filtered = self._filter_executable_skill_names(names)
                if filtered:
                    return filtered
            skills = data.get("skill_names") or data.get("skills") or []
            if isinstance(skills, list) and skills:
                filtered = self._filter_executable_skill_names(
                    [str(s).strip() for s in skills if str(s).strip()]
                )
                if filtered:
                    return filtered
        except Exception as exc:  # noqa: BLE001 - fallback to defaults
            logger.bind(error=str(exc)).debug(
                "Skill list query failed; using default skill names"
            )
        return DEFAULT_SKILL_NAMES

    def _filter_executable_skill_names(
        self, skill_names: list[str]
    ) -> list[str]:
        """Keep only the skills verified to execute on the current edge path."""
        allowed = set(DEFAULT_SKILL_NAMES)
        filtered: list[str] = []
        skipped: list[str] = []
        seen: set[str] = set()

        for name in skill_names:
            normalized = str(name).strip()
            if not normalized or normalized in seen:
                continue
            seen.add(normalized)
            if normalized in allowed:
                filtered.append(normalized)
            else:
                skipped.append(normalized)

        if skipped:
            logger.bind(skipped_skills=skipped).warning(
                "Filtered out platform skills not yet executable on the edge path"
            )
        return filtered

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
