# Function: AgentInitializer unit tests
# Time: 2026-07-16
# Author: AxeXie
"""Unit tests for the QwenPaw AgentInitializer.

Covers agent creation, managed profile drift detection, no-op behavior,
degraded mode on connection failure, and the auto-create-disabled path. httpx
is mocked so no real network calls are made.
"""

from __future__ import annotations

from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import httpx

from opengeobot_agent.config import AgentConfig
from opengeobot_agent.initializer import AGENT_DESCRIPTION, AgentInitializer

SKILL_NAMES = ["stand_up", "move_forward", "stop", "capture_image"]


def _make_config(**overrides: Any) -> AgentConfig:
    base: dict[str, Any] = {
        "nats_url": "nats://localhost:4222",
        "nats_max_reconnect": -1,
        "nats_reconnect_wait": 2.0,
        "nats_connect_timeout": 5.0,
        "qwenpaw_endpoint": "http://localhost:8000/v1/chat/completions",
        "qwenpaw_api_key": "",
        "qwenpaw_timeout": 30.0,
        "plan_request_subject": "opengeobot.agent.mission.plan_request",
        "log_level": "DEBUG",
        "qwenpaw_admin_base_url": "http://qwenpaw:8088",
        "qwenpaw_agent_id": "opengeobot-controller",
        "qwenpaw_agent_name": "一脑多控",
        "qwenpaw_agent_create_on_start": True,
    }
    base.update(overrides)
    return AgentConfig(**base)


def _make_response(
    status_code: int = 200,
    json_data: dict | None = None,
    text: str = "",
) -> MagicMock:
    """Create a mock httpx.Response-like object."""
    resp = MagicMock()
    resp.status_code = status_code
    resp.json.return_value = json_data if json_data is not None else {}
    resp.raise_for_status.return_value = None
    resp.text = text
    return resp


def _make_error_response(status_code: int = 500) -> MagicMock:
    """Create a response whose raise_for_status raises HTTPStatusError."""
    resp = MagicMock()
    resp.status_code = status_code
    resp.text = f"error {status_code}"
    resp.raise_for_status.side_effect = httpx.HTTPStatusError(
        f"HTTP {status_code}",
        request=httpx.Request("GET", "http://qwenpaw:8088/api/agents/test"),
        response=httpx.Response(status_code=status_code, text="error"),
    )
    return resp


def _make_mock_client(
    *,
    get_resp: MagicMock | None = None,
    get_side_effect: BaseException | None = None,
    post_resp: MagicMock | None = None,
    put_resp: MagicMock | None = None,
) -> AsyncMock:
    """Build a mock httpx.AsyncClient usable as an async context manager.

    The patched ``httpx.AsyncClient`` class should return this mock from its
    call, and ``async with mock as client:`` yields the mock itself.
    """
    client = AsyncMock()
    client.__aenter__.return_value = client
    client.__aexit__.return_value = None
    if get_resp is not None:
        client.get.return_value = get_resp
    if get_side_effect is not None:
        client.get.side_effect = get_side_effect
    if post_resp is not None:
        client.post.return_value = post_resp
    if put_resp is not None:
        client.put.return_value = put_resp
    return client


class TestAgentInitializerCreate:
    """Agent does not exist (404) -> create via POST."""

    async def test_creates_agent_when_not_found(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        get_resp = _make_response(status_code=404)
        post_resp = _make_response(
            status_code=200,
            json_data={"id": "opengeobot-controller", "name": "一脑多控"},
        )
        put_resp = _make_response(
            status_code=200,
            json_data={"id": "opengeobot-controller"},
        )
        mock_client = _make_mock_client(
            get_resp=get_resp, post_resp=post_resp, put_resp=put_resp
        )

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is True
        assert initializer.is_initialized is True
        assert initializer.agent_id == "opengeobot-controller"

        # GET was called to check existence
        mock_client.get.assert_awaited_once()
        # POST was called to create the basic agent
        mock_client.post.assert_awaited_once()
        # PUT was called to write the full AgentProfileConfig (persona + mcp)
        mock_client.put.assert_awaited_once()

    async def test_create_request_body_has_correct_fields(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        get_resp = _make_response(status_code=404)
        post_resp = _make_response(status_code=200, json_data={})
        put_resp = _make_response(status_code=200, json_data={})
        mock_client = _make_mock_client(
            get_resp=get_resp, post_resp=post_resp, put_resp=put_resp
        )

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            await initializer.initialize(skill_names=SKILL_NAMES)

        # Inspect the POST call arguments
        call_args = mock_client.post.call_args
        url = call_args.args[0]
        body = call_args.kwargs["json"]

        assert "/api/agents" in url
        assert body["id"] == "opengeobot-controller"
        assert body["name"] == "一脑多控"
        assert body["language"] == "zh"
        assert body["skill_names"] == SKILL_NAMES
        assert body["workspace_dir"] == "/app/working/workspaces/opengeobot-controller"


class TestAgentInitializerUpdate:
    """Agent exists -> update managed AgentProfileConfig fields via PUT."""

    async def test_updates_agent_when_managed_fields_differ(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        existing_agent = {
            "id": "opengeobot-controller",
            "name": "一脑多控",
            "description": AGENT_DESCRIPTION,
            "workspace_dir": "/app/working/workspaces/opengeobot-controller",
            "language": "zh",
            "system_prompt_files": ["AGENTS.md", "SOUL.md", "PROFILE.md"],
            "mcp": {"clients": {}},
            "approval_level": "STRICT",
            "active_model": {
                "provider_id": "legacy-provider",
                "model": "legacy-model",
            },
        }
        get_resp = _make_response(status_code=200, json_data=existing_agent)
        put_resp = _make_response(
            status_code=200,
            json_data={"id": "opengeobot-controller"},
        )
        mock_client = _make_mock_client(get_resp=get_resp, put_resp=put_resp)

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is True
        assert initializer.is_initialized is True
        assert initializer.agent_id == "opengeobot-controller"

        # GET was called
        mock_client.get.assert_awaited_once()
        # PUT was called to update
        mock_client.put.assert_awaited_once()
        # POST was never called (agent already exists)
        mock_client.post.assert_not_awaited()

    async def test_update_put_body_excludes_skill_names(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        existing_agent = {
            "id": "opengeobot-controller",
            "name": "一脑多控",
            "description": "old desc",
            "workspace_dir": "/app/working/workspaces/opengeobot-controller",
            "language": "zh",
            "system_prompt_files": ["AGENTS.md", "SOUL.md", "PROFILE.md"],
            "mcp": {"clients": {}},
            "approval_level": "STRICT",
            "active_model": {
                "provider_id": config.qwenpaw_model_provider,
                "model": config.qwenpaw_model_name,
            },
        }
        get_resp = _make_response(status_code=200, json_data=existing_agent)
        put_resp = _make_response(status_code=200, json_data={})
        mock_client = _make_mock_client(get_resp=get_resp, put_resp=put_resp)

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            await initializer.initialize(skill_names=SKILL_NAMES)

        call_args = mock_client.put.call_args
        url = call_args.args[0]
        body = call_args.kwargs["json"]

        assert "/api/agents/opengeobot-controller" in url
        assert body["id"] == "opengeobot-controller"
        assert "skill_names" not in body
        assert body["description"] == AGENT_DESCRIPTION


class TestAgentInitializerNoOp:
    """Agent exists with aligned managed fields -> no update needed."""

    async def test_no_update_when_get_omits_skill_names(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        existing_agent = {
            "id": "opengeobot-controller",
            "name": "一脑多控",
            "description": AGENT_DESCRIPTION,
            "workspace_dir": "/app/working/workspaces/opengeobot-controller",
            "language": "zh",
            "system_prompt_files": ["AGENTS.md", "SOUL.md", "PROFILE.md"],
            "mcp": {"clients": {}},
            "approval_level": "STRICT",
            "active_model": {
                "provider_id": config.qwenpaw_model_provider,
                "model": config.qwenpaw_model_name,
            },
        }
        get_resp = _make_response(status_code=200, json_data=existing_agent)
        mock_client = _make_mock_client(get_resp=get_resp)

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is True
        assert initializer.is_initialized is True
        assert initializer.agent_id == "opengeobot-controller"

        # GET was called
        mock_client.get.assert_awaited_once()
        # Neither PUT nor POST was called
        mock_client.put.assert_not_awaited()
        mock_client.post.assert_not_awaited()


class TestAgentInitializerDegraded:
    """QwenPaw unavailable -> degraded (stateless) mode."""

    async def test_connection_error_returns_false(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        mock_client = _make_mock_client(
            get_side_effect=httpx.ConnectError("Connection refused"),
        )

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is False
        assert initializer.is_initialized is False
        assert initializer.agent_id is None

    async def test_server_error_returns_false(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        # GET returns 500 -> raise_for_status raises HTTPStatusError
        get_resp = _make_error_response(status_code=500)
        mock_client = _make_mock_client(get_resp=get_resp)

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is False
        assert initializer.is_initialized is False

    async def test_create_failure_returns_false(self):
        """If GET succeeds (404) but POST fails, degrade to stateless."""
        config = _make_config()
        initializer = AgentInitializer(config)

        get_resp = _make_response(status_code=404)
        # POST raises a connection error
        mock_client = _make_mock_client(
            get_resp=get_resp,
            post_resp=None,
        )
        mock_client.post.side_effect = httpx.ConnectError("post failed")

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is False
        assert initializer.is_initialized is False
        assert initializer.agent_id is None


class TestAgentInitializerDisabled:
    """Auto-create disabled -> stateless mode immediately."""

    async def test_disabled_returns_false_without_api_call(self):
        config = _make_config(qwenpaw_agent_create_on_start=False)
        initializer = AgentInitializer(config)

        mock_client = _make_mock_client()

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is False
        assert initializer.is_initialized is False
        assert initializer.agent_id is None

        # No HTTP calls should have been made
        mock_client.get.assert_not_awaited()
        mock_client.post.assert_not_awaited()
        mock_client.put.assert_not_awaited()

    async def test_disabled_with_no_skills(self):
        config = _make_config(qwenpaw_agent_create_on_start=False)
        initializer = AgentInitializer(config)

        result = await initializer.initialize(skill_names=None)

        assert result is False
        assert initializer.is_initialized is False


class TestAgentInitializerDefaults:
    """Default state before initialize() is called."""

    def test_initial_state_not_initialized(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        assert initializer.is_initialized is False
        assert initializer.agent_id is None

    async def test_initialize_with_none_skills(self):
        """Passing None for skill_names should not crash."""
        config = _make_config()
        initializer = AgentInitializer(config)

        get_resp = _make_response(status_code=404)
        post_resp = _make_response(status_code=200, json_data={})
        put_resp = _make_response(status_code=200, json_data={})
        mock_client = _make_mock_client(
            get_resp=get_resp, post_resp=post_resp, put_resp=put_resp
        )

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=None)

        assert result is True
        assert initializer.is_initialized is True

        # The create body should have an empty skill_names list
        call_args = mock_client.post.call_args
        body = call_args.kwargs["json"]
        assert body["skill_names"] == []


class TestAgentInitializerPutBody:
    """Verify the PUT body carries persona files, mcp clients and approval_level."""

    async def test_put_body_contains_persona_and_mcp_config(self):
        config = _make_config(
            qwenpaw_mcp_gateway_url="http://mcp-gateway:8090/sse",
            qwenpaw_agent_approval_level="STRICT",
        )
        initializer = AgentInitializer(config)

        get_resp = _make_response(status_code=404)
        post_resp = _make_response(status_code=200, json_data={})
        put_resp = _make_response(status_code=200, json_data={})
        mock_client = _make_mock_client(
            get_resp=get_resp, post_resp=post_resp, put_resp=put_resp
        )

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            await initializer.initialize(skill_names=SKILL_NAMES)

        call_args = mock_client.put.call_args
        body = call_args.kwargs["json"]

        assert body["id"] == "opengeobot-controller"
        assert "skill_names" not in body
        assert body["system_prompt_files"] == [
            "AGENTS.md",
            "SOUL.md",
            "PROFILE.md",
        ]
        assert "opengeobot-mcp-gateway" in body["mcp"]["clients"]
        mcp_client = body["mcp"]["clients"]["opengeobot-mcp-gateway"]
        assert mcp_client["transport"] == "sse"
        assert mcp_client["url"] == "http://mcp-gateway:8090/sse"
        assert body["approval_level"] == "STRICT"

    async def test_mcp_skipped_when_gateway_url_empty(self):
        config = _make_config()  # qwenpaw_mcp_gateway_url defaults to ""
        initializer = AgentInitializer(config)

        get_resp = _make_response(status_code=404)
        post_resp = _make_response(status_code=200, json_data={})
        put_resp = _make_response(status_code=200, json_data={})
        mock_client = _make_mock_client(
            get_resp=get_resp, post_resp=post_resp, put_resp=put_resp
        )

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            await initializer.initialize(skill_names=SKILL_NAMES)

        call_args = mock_client.put.call_args
        body = call_args.kwargs["json"]

        assert body["mcp"]["clients"] == {}


class TestAgentInitializerMcpUpdate:
    """PUT behavior driven by mcp.clients changes on an existing agent."""

    async def test_no_put_when_managed_fields_aligned(self):
        config = _make_config(
            qwenpaw_mcp_gateway_url="http://mcp-gateway:8090/sse",
        )
        initializer = AgentInitializer(config)

        # The exact mcp.clients structure that _build_mcp_clients() produces
        # when qwenpaw_mcp_gateway_auth_token is empty.
        matching_mcp_clients = {
            "opengeobot-mcp-gateway": {
                "name": "OpenGeoBot MCP Tool Gateway",
                "description": "Platform MCP Tool Gateway for registered skills",
                "enabled": True,
                "transport": "sse",
                "url": "http://mcp-gateway:8090/sse",
                "headers": {},
            }
        }
        existing_agent = {
            "id": "opengeobot-controller",
            "name": "一脑多控",
            "description": AGENT_DESCRIPTION,
            "workspace_dir": "/app/working/workspaces/opengeobot-controller",
            "language": "zh",
            "system_prompt_files": ["AGENTS.md", "SOUL.md", "PROFILE.md"],
            "mcp": {"clients": matching_mcp_clients},
            "approval_level": "STRICT",
            "active_model": {
                "provider_id": config.qwenpaw_model_provider,
                "model": config.qwenpaw_model_name,
            },
        }
        get_resp = _make_response(status_code=200, json_data=existing_agent)
        mock_client = _make_mock_client(get_resp=get_resp)

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is True
        mock_client.get.assert_awaited_once()
        # No PUT/POST since all managed fields already match.
        mock_client.put.assert_not_awaited()
        mock_client.post.assert_not_awaited()

    async def test_put_when_mcp_config_changed(self):
        config = _make_config(
            qwenpaw_mcp_gateway_url="http://mcp-gateway:8090/sse",
        )
        initializer = AgentInitializer(config)

        # Same skills but mcp.clients is empty (different from desired).
        existing_agent = {
            "id": "opengeobot-controller",
            "name": "一脑多控",
            "description": AGENT_DESCRIPTION,
            "workspace_dir": "/app/working/workspaces/opengeobot-controller",
            "language": "zh",
            "system_prompt_files": ["AGENTS.md", "SOUL.md", "PROFILE.md"],
            "mcp": {"clients": {}},
            "approval_level": "STRICT",
            "active_model": {
                "provider_id": config.qwenpaw_model_provider,
                "model": config.qwenpaw_model_name,
            },
        }
        get_resp = _make_response(status_code=200, json_data=existing_agent)
        put_resp = _make_response(status_code=200, json_data={})
        mock_client = _make_mock_client(get_resp=get_resp, put_resp=put_resp)

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is True
        mock_client.get.assert_awaited_once()
        # PUT is called because mcp.clients differs.
        mock_client.put.assert_awaited_once()
        mock_client.post.assert_not_awaited()

        # The PUT body should carry the updated mcp config.
        call_args = mock_client.put.call_args
        body = call_args.kwargs["json"]
        assert body["id"] == "opengeobot-controller"
        assert "opengeobot-mcp-gateway" in body["mcp"]["clients"]
        assert (
            body["mcp"]["clients"]["opengeobot-mcp-gateway"]["url"]
            == "http://mcp-gateway:8090/sse"
        )

    async def test_update_failure_returns_false_instead_of_raising(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        existing_agent = {
            "id": "opengeobot-controller",
            "name": "一脑多控",
            "description": "old desc",
            "workspace_dir": "/app/working/workspaces/opengeobot-controller",
            "language": "zh",
            "system_prompt_files": ["AGENTS.md", "SOUL.md", "PROFILE.md"],
            "mcp": {"clients": {}},
            "approval_level": "STRICT",
            "active_model": {
                "provider_id": config.qwenpaw_model_provider,
                "model": config.qwenpaw_model_name,
            },
        }
        get_resp = _make_response(status_code=200, json_data=existing_agent)
        put_resp = _make_error_response(status_code=422)
        mock_client = _make_mock_client(get_resp=get_resp, put_resp=put_resp)

        with patch(
            "opengeobot_agent.initializer.httpx.AsyncClient",
            return_value=mock_client,
        ):
            result = await initializer.initialize(skill_names=SKILL_NAMES)

        assert result is False
        assert initializer.is_initialized is False
        assert initializer.agent_id is None
        mock_client.get.assert_awaited_once()
        mock_client.put.assert_awaited_once()
