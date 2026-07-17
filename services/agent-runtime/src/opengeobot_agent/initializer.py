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
from .persona import PERSONA_FILE_NAMES

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

        if not self._config.qwenpaw_mcp_gateway_url:
            logger.warning(
                "QWENPAW_MCP_GATEWAY_URL not configured; "
                "skipping MCP client binding for QwenPaw agent"
            )

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
            try:
                await self._update_agent(existing, skills)
                self._agent_initialized = True
                self._agent_id = self._config.qwenpaw_agent_id
                logger.info(
                    "QwenPaw agent '{}' updated with {} skills",
                    self._agent_id,
                    len(skills),
                )
                return True
            except Exception as exc:  # noqa: BLE001 - update failure degrades
                logger.bind(
                    agent_id=self._config.qwenpaw_agent_id,
                    error=str(exc),
                ).warning(
                    "Failed to update QwenPaw agent; degrading to stateless mode"
                )
                return False

        # Agent does not exist - create it.
        try:
            await self._create_agent(skills)
            # After POST creates the basic agent, write the full
            # AgentProfileConfig (persona files, mcp clients, approval_level)
            # via PUT so QwenPaw loads the "一脑多控" persona and tool gateway.
            profile = self._build_agent_profile_config()
            await self._put_agent_profile(profile)
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
            "active_model": {
                "provider_id": self._config.qwenpaw_model_provider,
                "model": self._config.qwenpaw_model_name,
            },
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
        """Update the managed AgentProfileConfig fields via PUT /api/agents/{id}.

        The drift check only compares fields that are both visible in
        ``GET /api/agents/{id}`` and managed by OpenGeoBot, so missing
        ``skill_names`` in GET responses does not trigger a false update.
        """
        del skill_names  # skill_names is only supported during POST create.

        desired = self._build_agent_profile_config()
        if self._managed_profile_view(current) == self._managed_profile_view(desired):
            logger.info(
                "QwenPaw agent '{}' managed profile already up to date",
                self._config.qwenpaw_agent_id,
            )
            return current

        return await self._put_agent_profile(desired)

    def _build_mcp_clients(self) -> dict[str, Any]:
        """Build the mcp.clients dict for the QwenPaw agent profile.

        Returns a single SSE client pointing at the platform MCP Tool Gateway
        when ``qwenpaw_mcp_gateway_url`` is configured, otherwise an empty
        dict (no MCP client binding).
        """
        url = self._config.qwenpaw_mcp_gateway_url
        if not url:
            return {}
        token = self._config.qwenpaw_mcp_gateway_auth_token
        headers: dict[str, str] = (
            {"Authorization": f"Bearer {token}"} if token else {}
        )
        return {
            "opengeobot-mcp-gateway": {
                "name": "OpenGeoBot MCP Tool Gateway",
                "description": "Platform MCP Tool Gateway for registered skills",
                "enabled": True,
                "transport": "sse",
                "url": url,
                "headers": headers,
            }
        }

    def _build_agent_profile_config(self) -> dict[str, Any]:
        """Construct the full AgentProfileConfig dict for PUT /api/agents/{id}.

        Only includes the fields confirmed to be accepted by the runtime PUT
        contract. ``skill_names`` is intentionally excluded.
        """
        return {
            "id": self._config.qwenpaw_agent_id,
            "name": self._config.qwenpaw_agent_name,
            "description": AGENT_DESCRIPTION,
            "workspace_dir": (
                f"/app/working/workspaces/{self._config.qwenpaw_agent_id}"
            ),
            "language": "zh",
            "active_model": {
                "provider_id": self._config.qwenpaw_model_provider,
                "model": self._config.qwenpaw_model_name,
            },
            "system_prompt_files": list(PERSONA_FILE_NAMES),
            "mcp": {"clients": self._build_mcp_clients()},
            "approval_level": self._config.qwenpaw_agent_approval_level,
        }

    def _managed_profile_view(self, profile: dict[str, Any]) -> dict[str, Any]:
        """Project a profile to the GET-visible fields managed by OpenGeoBot."""
        mcp = profile.get("mcp") or {}
        active_model = profile.get("active_model") or {}
        return {
            "name": profile.get("name"),
            "description": profile.get("description"),
            "workspace_dir": profile.get("workspace_dir"),
            "language": profile.get("language"),
            "system_prompt_files": profile.get("system_prompt_files") or [],
            "mcp": {"clients": mcp.get("clients", {}) or {}},
            "approval_level": profile.get("approval_level"),
            "active_model": {
                "provider_id": active_model.get("provider_id"),
                "model": active_model.get("model"),
            },
        }

    async def _put_agent_profile(
        self, body: dict[str, Any]
    ) -> dict[str, Any]:
        """Write the full AgentProfileConfig via PUT /api/agents/{id}."""
        async with httpx.AsyncClient(timeout=_WRITE_TIMEOUT) as client:
            resp = await client.put(
                f"{self._config.qwenpaw_admin_base_url}/api/agents/"
                f"{self._config.qwenpaw_agent_id}",
                json=body,
            )
            resp.raise_for_status()
            return cast(dict[str, Any], resp.json())
