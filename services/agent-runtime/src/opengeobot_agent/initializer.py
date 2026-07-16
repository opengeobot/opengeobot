# Function: QwenPaw agent initializer for startup agent creation
# Time: 2026-07-16
# Author: AxeXie
"""QwenPaw agent initializer.

On agent-runtime startup, creates (or verifies) a persistent agent in QwenPaw
via the management API. This agent binds the platform's registered skills as
tool bindings, enabling context-aware mission planning.

The initializer MUST NOT block startup if QwenPaw is unavailable - it degrades
gracefully to stateless mode so the agent runtime can still serve plan requests
without a persistent agent context.
"""

from __future__ import annotations

from typing import Any, cast

import httpx
from loguru import logger

from .config import AgentConfig

# HTTP timeouts for the QwenPaw management API (seconds).
_GET_TIMEOUT = 10.0
_WRITE_TIMEOUT = 15.0

AGENT_DESCRIPTION = "OpenGeoBot 平台统一任务规划与控制智能体"


class AgentInitializer:
    """Creates or verifies the persistent QwenPaw agent on startup.

    The initializer queries ``GET /api/agents/{id}``; if the agent exists it
    updates the ``skill_names`` binding (only when changed), otherwise it
    creates the agent via ``POST /api/agents``. Any failure degrades to
    stateless mode rather than blocking startup.
    """

    def __init__(self, config: AgentConfig) -> None:
        self._config = config
        self._agent_initialized = False
        self._agent_id: str | None = None

    @property
    def is_initialized(self) -> bool:
        """True when a persistent QwenPaw agent is ready for use."""
        return self._agent_initialized

    @property
    def agent_id(self) -> str | None:
        """The QwenPaw agent id, or None when not initialized."""
        return self._agent_id

    async def initialize(self, skill_names: list[str] | None = None) -> bool:
        """Initialize the QwenPaw agent.

        Returns True if the agent is ready, False if degraded to stateless mode.
        """
        if not self._config.qwenpaw_agent_create_on_start:
            logger.info("QwenPaw agent auto-create disabled; using stateless mode")
            return False

        skills = skill_names or []

        try:
            existing = await self._get_agent(self._config.qwenpaw_agent_id)
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code != 404:
                logger.bind(
                    agent_id=self._config.qwenpaw_agent_id,
                    status_code=exc.response.status_code,
                ).warning(
                    "QwenPaw management API error; degrading to stateless mode"
                )
                return False
            existing = None
        except Exception as exc:  # noqa: BLE001 - any failure degrades to stateless
            logger.bind(
                agent_id=self._config.qwenpaw_agent_id,
                error=str(exc),
            ).warning(
                "QwenPaw management API unreachable; degrading to stateless mode"
            )
            return False

        if existing is not None:
            await self._update_agent(existing, skills)
            self._agent_initialized = True
            self._agent_id = self._config.qwenpaw_agent_id
            logger.info(
                "QwenPaw agent '{}' updated with {} skills",
                self._agent_id,
                len(skills),
            )
            return True

        # Agent does not exist - create it.
        try:
            await self._create_agent(skills)
            self._agent_initialized = True
            self._agent_id = self._config.qwenpaw_agent_id
            logger.info(
                "QwenPaw agent '{}' created with {} skills",
                self._agent_id,
                len(skills),
            )
            return True
        except Exception as exc:  # noqa: BLE001 - creation failure degrades to stateless
            logger.bind(
                agent_id=self._config.qwenpaw_agent_id,
                error=str(exc),
            ).warning(
                "Failed to create QwenPaw agent; degrading to stateless mode"
            )
            return False

    async def _get_agent(self, agent_id: str) -> dict[str, Any] | None:
        """Return the existing agent config, or None if not found (404)."""
        async with httpx.AsyncClient(timeout=_GET_TIMEOUT) as client:
            resp = await client.get(
                f"{self._config.qwenpaw_admin_base_url}/api/agents/{agent_id}"
            )
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
            return cast(dict[str, Any], resp.json())

    async def _create_agent(self, skill_names: list[str]) -> dict[str, Any]:
        """Create the QwenPaw agent via POST /api/agents."""
        body = {
            "id": self._config.qwenpaw_agent_id,
            "name": self._config.qwenpaw_agent_name,
            "description": AGENT_DESCRIPTION,
            "workspace_dir": f"/app/working/workspaces/{self._config.qwenpaw_agent_id}",
            "language": "zh",
            "skill_names": skill_names,
        }
        async with httpx.AsyncClient(timeout=_WRITE_TIMEOUT) as client:
            resp = await client.post(
                f"{self._config.qwenpaw_admin_base_url}/api/agents",
                json=body,
            )
            resp.raise_for_status()
            return cast(dict[str, Any], resp.json())

    async def _update_agent(
        self, current: dict[str, Any], skill_names: list[str]
    ) -> dict[str, Any]:
        """Update the agent's skill_names binding via PUT /api/agents/{id}.

        Skips the update when the skill set is already current.
        """
        current_skills = set(current.get("skill_names", []))
        new_skills = set(skill_names)
        if current_skills == new_skills and new_skills:
            logger.info(
                "QwenPaw agent '{}' skills already up to date",
                self._config.qwenpaw_agent_id,
            )
            return current

        body = {
            "name": self._config.qwenpaw_agent_name,
            "description": current.get("description", AGENT_DESCRIPTION),
            "workspace_dir": current.get(
                "workspace_dir",
                f"/app/working/workspaces/{self._config.qwenpaw_agent_id}",
            ),
            "language": current.get("language", "zh"),
            "skill_names": skill_names,
        }
        async with httpx.AsyncClient(timeout=_WRITE_TIMEOUT) as client:
            resp = await client.put(
                f"{self._config.qwenpaw_admin_base_url}/api/agents/"
                f"{self._config.qwenpaw_agent_id}",
                json=body,
            )
            resp.raise_for_status()
            return cast(dict[str, Any], resp.json())
