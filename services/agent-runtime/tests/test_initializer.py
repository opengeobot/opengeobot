# Function: AgentInitializer unit tests
# Time: 2026-07-16
# Author: AxeXie
"""Unit tests for the QwenPaw AgentInitializer.

Covers agent creation, update, no-op (same skills), degraded mode on
connection failure, and the auto-create-disabled path. httpx is mocked so no
real network calls are made.
"""

from __future__ import annotations

from typing import Any
from unittest.mock import AsyncMock, MagicMock, patch

import httpx

from opengeobot_agent.config import AgentConfig
from opengeobot_agent.initializer import AgentInitializer

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
        mock_client = _make_mock_client(get_resp=get_resp, post_resp=post_resp)

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
        # POST was called to create
        mock_client.post.assert_awaited_once()
        # PUT was never called
        mock_client.put.assert_not_awaited()

    async def test_create_request_body_has_correct_fields(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        get_resp = _make_response(status_code=404)
        post_resp = _make_response(status_code=200, json_data={})
        mock_client = _make_mock_client(get_resp=get_resp, post_resp=post_resp)

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
    """Agent exists -> update skill_names via PUT."""

    async def test_updates_agent_when_skills_differ(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        existing_agent = {
            "id": "opengeobot-controller",
            "name": "一脑多控",
            "skill_names": ["stand_up", "move_forward"],  # fewer skills
            "description": "old desc",
            "workspace_dir": "/app/working/workspaces/opengeobot-controller",
            "language": "zh",
        }
        get_resp = _make_response(status_code=200, json_data=existing_agent)
        put_resp = _make_response(
            status_code=200,
            json_data={"id": "opengeobot-controller", "skill_names": SKILL_NAMES},
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

    async def test_update_put_body_has_new_skills(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        existing_agent = {
            "id": "opengeobot-controller",
            "name": "一脑多控",
            "skill_names": ["old_skill"],
            "description": "desc",
            "workspace_dir": "/app/ws",
            "language": "zh",
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
        assert body["skill_names"] == SKILL_NAMES


class TestAgentInitializerNoOp:
    """Agent exists with identical skills -> no update needed."""

    async def test_no_update_when_skills_unchanged(self):
        config = _make_config()
        initializer = AgentInitializer(config)

        existing_agent = {
            "id": "opengeobot-controller",
            "name": "一脑多控",
            "skill_names": SKILL_NAMES,  # same skills
            "description": "desc",
            "workspace_dir": "/app/ws",
            "language": "zh",
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
        mock_client = _make_mock_client(get_resp=get_resp, post_resp=post_resp)

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
