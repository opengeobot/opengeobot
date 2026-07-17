from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock

from opengeobot_agent.config import AgentConfig
from opengeobot_agent.main import DEFAULT_SKILL_NAMES, AgentRuntime


def _make_config() -> AgentConfig:
    return AgentConfig(
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        qwenpaw_endpoint="http://localhost:8000/api/agents/opengeobot-controller/console/chat",
        qwenpaw_api_key="",
        qwenpaw_timeout=30.0,
        plan_request_subject="opengeobot.agent.mission.plan_request",
        replan_request_subject="opengeobot.agent.mission.replan",
        log_level="DEBUG",
    )


class TestAgentRuntimeStartup:
    async def test_runtime_uses_core_nats_for_request_reply_subjects(self):
        config = _make_config()
        runtime = AgentRuntime(config)

        runtime._nats.connect = AsyncMock()
        runtime._nats.ensure_stream = AsyncMock()
        runtime._nats.subscribe = AsyncMock()
        runtime._nats.subscribe_js = AsyncMock()
        runtime._resolve_skill_names = AsyncMock(return_value=["move_forward"])
        runtime._initializer.initialize = AsyncMock(return_value=True)
        runtime._initializer._agent_initialized = True
        runtime._initializer._agent_id = "opengeobot-controller"
        runtime._provider.set_agent_context = MagicMock()

        await runtime.start()

        runtime._nats.connect.assert_awaited_once()
        runtime._nats.ensure_stream.assert_awaited_once()
        assert runtime._nats.subscribe.await_count == 2
        runtime._nats.subscribe.assert_any_await(
            config.plan_request_subject,
            runtime._handler.handle_plan_request,
        )
        runtime._nats.subscribe.assert_any_await(
            config.replan_request_subject,
            runtime._handler.handle_replan_request,
        )
        runtime._nats.subscribe_js.assert_not_awaited()
        runtime._provider.set_agent_context.assert_called_once_with(
            "opengeobot-controller",
            ["move_forward"],
        )

    async def test_resolve_skill_names_from_platform_skill_list_array(self):
        config = _make_config()
        runtime = AgentRuntime(config)

        reply = MagicMock()
        reply.data = json.dumps(
            [
                {"skill_id": "skl_001", "name": "stand_up"},
                {"skill_id": "skl_002", "name": "move_forward"},
            ]
        ).encode("utf-8")
        runtime._nats.request = AsyncMock(return_value=reply)

        skills = await runtime._resolve_skill_names()

        assert skills == ["move_forward"]

    async def test_resolve_skill_names_falls_back_when_platform_skills_not_executable(self):
        config = _make_config()
        runtime = AgentRuntime(config)

        reply = MagicMock()
        reply.data = json.dumps(
            [
                {"skill_id": "skl_001", "name": "stand_up"},
            ]
        ).encode("utf-8")
        runtime._nats.request = AsyncMock(return_value=reply)

        skills = await runtime._resolve_skill_names()

        assert skills == DEFAULT_SKILL_NAMES
