# Function: QwenPawProvider unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for the QwenPawProvider."""

from __future__ import annotations

import json
from typing import Any

import pytest

from opengeobot_agent.config import AgentConfig
from opengeobot_agent.provider import (
    AgentRuntimeProvider,
    MissionContext,
    NatsSkillRegistry,
    PlanStep,
    QwenPawProvider,
    QwenPawProviderError,
    ReplanRequest,
    SkillDefinition,
    SkillRegistry,
    StaticSkillRegistry,
)


def _make_config() -> AgentConfig:
    return AgentConfig(
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        qwenpaw_endpoint="http://localhost:8000/api/agents/opengeobot-controller/console/chat",
        qwenpaw_api_key="test-key",
        qwenpaw_timeout=30.0,
        plan_request_subject="opengeobot.agent.mission.plan_request",
        log_level="DEBUG",
    )


def _make_mission(**overrides: Any) -> MissionContext:
    base = {
        "mission_id": "msn_001",
        "trace_id": "trace_001",
        "robot_id": "rbt_01",
        "objective": "Move to location B and capture an image",
        "constraints": {"max_speed": 1.0},
        "requested_at": "2026-01-01T00:00:00Z",
    }
    base.update(overrides)
    return MissionContext.model_validate(base)


def _make_replan_request(**overrides: Any) -> ReplanRequest:
    base = {
        "mission_id": "msn_001",
        "trace_id": "trace_001",
        "robot_id": "rbt_01",
        "original_objective": "Move to location B and capture an image",
        "completed_steps": [
            {"skill_id": "move_forward", "params": {"distance": 3.0}, "result": "ok"},
        ],
        "failed_step": {"skill_id": "capture_image", "params": {}, "error": "camera offline"},
        "failure_reason": "Camera device not responding",
        "remaining_steps": [
            {"skill_id": "move_forward", "params": {"distance": 2.0}},
        ],
    }
    base.update(overrides)
    return ReplanRequest.model_validate(base)


def _make_qwenpaw_response(steps: list[dict], confidence: float = 0.9) -> dict[str, Any]:
    """Build a mock QwenPaw chat-completions response."""
    content = json.dumps({"steps": steps, "confidence": confidence})
    return {
        "choices": [
            {"message": {"role": "assistant", "content": content}}
        ]
    }


class _FakeSseResponse:
    def __init__(self, lines: list[str]) -> None:
        self._lines = lines

    async def aiter_lines(self):
        for line in self._lines:
            yield line


class TestAgentRuntimeProvider:
    def test_is_abstract(self):
        """AgentRuntimeProvider should be an abstract class."""
        with pytest.raises(TypeError):
            AgentRuntimeProvider()  # type: ignore[abstract]


class TestQwenPawProviderSuccess:
    def test_builds_console_chat_payload(self):
        config = _make_config()
        provider = QwenPawProvider(config)
        provider.set_agent_context("opengeobot-controller", ["move_forward"])

        payload = provider._build_request_payload(_make_mission())

        assert payload["tool_choice"] == "none"
        assert payload["stream"] is True
        assert payload["session_id"] == "trace_001"
        assert payload["metadata"]["request_kind"] == "plan"
        assert payload["input"][0]["role"] == "user"
        assert payload["input"][0]["content"][0]["type"] == "text"
        assert "Move to location B and capture an image" in payload["input"][0]["content"][0]["text"]

    def test_resolves_legacy_endpoint_to_console_chat(self):
        config = _make_config()
        config = AgentConfig(
            **{
                **config.__dict__,
                "qwenpaw_endpoint": "http://localhost:8000/v1/chat/completions",
                "qwenpaw_agent_id": "agent-42",
            }
        )
        provider = QwenPawProvider(config)

        assert (
            provider._resolve_runtime_endpoint()
            == "http://localhost:8000/api/agents/agent-42/console/chat"
        )

    async def test_reads_assistant_text_from_sse_stream(self):
        config = _make_config()
        provider = QwenPawProvider(config)
        normalized = await provider._read_sse_response(
            _FakeSseResponse(
                [
                    'data: {"type":"message","role":"assistant","content":[{"type":"output_text","text":"{\\"steps\\": [], \\"confidence\\": 0.7}"}]}',
                ]
            )
        )

        assert (
            normalized["choices"][0]["message"]["content"]
            == '{"steps": [], "confidence": 0.7}'
        )

    async def test_generates_untrusted_proposal(self):
        """The generated proposal must be UNTRUSTED."""
        config = _make_config()
        provider = QwenPawProvider(config)
        mock_response = _make_qwenpaw_response(
            steps=[
                {"skill_id": "move_forward", "params": {"distance": 5.0, "speed": 0.5}},
                {"skill_id": "capture_image", "params": {}},
            ],
            confidence=0.85,
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        assert proposal.is_trusted is False
        assert proposal.mission_id == "msn_001"
        assert proposal.trace_id == "trace_001"
        assert proposal.robot_id == "rbt_01"
        assert proposal.error is None
        assert len(proposal.steps) == 2
        assert proposal.steps[0].skill_id == "move_forward"
        assert proposal.steps[1].skill_id == "capture_image"
        assert proposal.confidence == pytest.approx(0.85)

    async def test_proposal_has_unique_plan_id(self):
        """Each proposal should have a unique plan_id."""
        config = _make_config()
        provider = QwenPawProvider(config)
        mock_response = _make_qwenpaw_response(steps=[], confidence=0.5)

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal1 = await provider.generate_plan(_make_mission())
        proposal2 = await provider.generate_plan(_make_mission())

        assert proposal1.plan_id != proposal2.plan_id
        assert proposal1.plan_id.startswith("plan_")

    async def test_steps_have_unique_step_ids(self):
        """Each step should have a unique step_id."""
        config = _make_config()
        provider = QwenPawProvider(config)
        mock_response = _make_qwenpaw_response(
            steps=[
                {"skill_id": "stop", "params": {}},
                {"skill_id": "stand_up", "params": {}},
            ],
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())
        step_ids = [s.step_id for s in proposal.steps]
        assert len(set(step_ids)) == 2


class TestQwenPawProviderConfidenceClamping:
    async def test_confidence_above_1_clamped(self):
        config = _make_config()
        provider = QwenPawProvider(config)
        mock_response = _make_qwenpaw_response(steps=[], confidence=1.5)

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())
        assert proposal.confidence == pytest.approx(1.0)

    async def test_confidence_below_0_clamped(self):
        config = _make_config()
        provider = QwenPawProvider(config)
        mock_response = _make_qwenpaw_response(steps=[], confidence=-0.5)

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())
        assert proposal.confidence == pytest.approx(0.0)


class TestQwenPawProviderErrors:
    async def test_mixed_prose_plus_json_is_parsed(self):
        """Assistant prose before the JSON payload should not break parsing."""
        config = _make_config()
        provider = QwenPawProvider(config)

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return {
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": (
                                "I will produce a valid plan JSON now.\n"
                                '{"steps":[{"skill_id":"stand_up","params":{}},'
                                '{"skill_id":"move_forward","params":{"distance":2.0}}],'
                                '"confidence":0.95}'
                            ),
                        }
                    }
                ]
            }

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())
        assert proposal.error is None
        assert len(proposal.steps) == 2
        assert proposal.steps[0].skill_id == "stand_up"
        assert proposal.steps[1].skill_id == "move_forward"
        assert proposal.confidence == pytest.approx(0.95)

    async def test_api_failure_returns_error_proposal(self):
        """When the API fails, the proposal should have an error message."""
        config = _make_config()
        provider = QwenPawProvider(config)

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            raise QwenPawProviderError("QwenPaw API timed out")

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        assert proposal.is_trusted is False
        assert proposal.error is not None
        assert "timed out" in proposal.error
        assert len(proposal.steps) == 0

    async def test_no_choices_returns_error(self):
        """When the API returns no choices, an error proposal is returned."""
        config = _make_config()
        provider = QwenPawProvider(config)

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return {"choices": []}

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())
        assert proposal.error is not None
        assert "no choices" in (proposal.error or "")

    async def test_malformed_content_returns_error(self):
        """When the content is not valid JSON, an error proposal is returned."""
        config = _make_config()
        provider = QwenPawProvider(config)

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return {
                "choices": [
                    {"message": {"role": "assistant", "content": "not json"}}
                ]
            }

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())
        assert proposal.error is not None
        assert "parse" in (proposal.error or "").lower()

    async def test_content_not_object_returns_error(self):
        """When the content is a JSON array instead of an object, error."""
        config = _make_config()
        provider = QwenPawProvider(config)

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return {
                "choices": [
                    {"message": {"role": "assistant", "content": "[1, 2, 3]"}}
                ]
            }

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())
        assert proposal.error is not None


class TestQwenPawProviderNoDirectHardwareAccess:
    async def test_provider_does_not_access_motors(self):
        """The provider should only produce proposals, not call /cmd_vel."""
        config = _make_config()
        provider = QwenPawProvider(config)
        mock_response = _make_qwenpaw_response(
            steps=[{"skill_id": "move_forward", "params": {"distance": 1.0}}],
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        # The proposal should contain skill references, not raw motor commands.
        for step in proposal.steps:
            assert "cmd_vel" not in step.skill_id
            assert "motor" not in step.skill_id.lower()
            assert "joint" not in step.skill_id.lower()


# ------------------------------------------------------------------
# Skill registry and schema validation tests.
# ------------------------------------------------------------------


def _make_skill_registry(
    skills: dict[str, SkillDefinition] | None = None,
) -> StaticSkillRegistry:
    return StaticSkillRegistry(skills or {})


class TestSkillRegistry:
    def test_is_abstract(self):
        """SkillRegistry should be an abstract class."""
        with pytest.raises(TypeError):
            SkillRegistry()  # type: ignore[abstract]

    async def test_static_registry_returns_known_skill(self):
        skill = SkillDefinition(
            skill_id="move_forward",
            input_schema={
                "type": "object",
                "properties": {
                    "distance": {"type": "number"},
                    "speed": {"type": "number"},
                },
                "required": ["distance"],
            },
        )
        registry = _make_skill_registry({"move_forward": skill})
        result = await registry.get_skill("move_forward")
        assert result is not None
        assert result.skill_id == "move_forward"
        assert "distance" in result.input_schema["properties"]

    async def test_static_registry_returns_none_for_unknown(self):
        registry = _make_skill_registry({})
        result = await registry.get_skill("unknown_skill")
        assert result is None


class TestSchemaValidationUnregisteredSkill:
    """Steps with unregistered skill_ids should be marked invalid."""

    async def test_unregistered_skill_marked_invalid(self):
        config = _make_config()
        registry = _make_skill_registry({})  # no skills registered
        provider = QwenPawProvider(config, skill_registry=registry)
        mock_response = _make_qwenpaw_response(
            steps=[{"skill_id": "phantom_skill", "params": {}}],
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        assert len(proposal.steps) == 1
        step = proposal.steps[0]
        assert step.skill_id == "phantom_skill"
        assert step.valid is False
        assert step.validation_error is not None
        assert "not registered" in step.validation_error

    async def test_unregistered_skill_does_not_discard_proposal(self):
        """The entire proposal should not be discarded for one bad step."""
        config = _make_config()
        registry = _make_skill_registry({})
        provider = QwenPawProvider(config, skill_registry=registry)
        mock_response = _make_qwenpaw_response(
            steps=[
                {"skill_id": "phantom_skill", "params": {}},
            ],
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        assert proposal.error is None
        assert len(proposal.steps) == 1
        assert proposal.is_trusted is False


class _StubNatsBridge:
    def __init__(self, payload: Any) -> None:
        self._payload = payload

    async def request(self, subject: str, data: bytes, timeout: float):
        class _Reply:
            def __init__(self, raw: bytes) -> None:
                self.data = raw

        return _Reply(json.dumps(self._payload).encode("utf-8"))


class TestNatsSkillRegistry:
    async def test_lookup_accepts_skill_name_alias_from_platform_response(self):
        registry = NatsSkillRegistry(
            _StubNatsBridge(
                [
                    {
                        "skill_id": "skl_001",
                        "name": "move_forward",
                        "input_schema": {"type": "object"},
                    }
                ]
            )  # type: ignore[arg-type]
        )

        by_name = await registry.get_skill("move_forward")
        by_id = await registry.get_skill("skl_001")

        assert by_name is not None
        assert by_id is not None
        assert by_name.skill_id == "skl_001"
        assert by_name.name == "move_forward"
        assert by_id.skill_id == "skl_001"


class TestSchemaValidationParamsMismatch:
    """Steps with params that don't match the skill schema should be invalid."""

    async def test_params_type_mismatch_marked_invalid(self):
        config = _make_config()
        skill = SkillDefinition(
            skill_id="move_forward",
            input_schema={
                "type": "object",
                "properties": {
                    "distance": {"type": "number"},
                },
                "required": ["distance"],
            },
        )
        registry = _make_skill_registry({"move_forward": skill})
        provider = QwenPawProvider(config, skill_registry=registry)
        # distance is a string, not a number
        mock_response = _make_qwenpaw_response(
            steps=[
                {"skill_id": "move_forward", "params": {"distance": "far"}},
            ],
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        assert len(proposal.steps) == 1
        step = proposal.steps[0]
        assert step.valid is False
        assert step.validation_error is not None

    async def test_missing_required_param_marked_invalid(self):
        config = _make_config()
        skill = SkillDefinition(
            skill_id="move_forward",
            input_schema={
                "type": "object",
                "properties": {
                    "distance": {"type": "number"},
                },
                "required": ["distance"],
            },
        )
        registry = _make_skill_registry({"move_forward": skill})
        provider = QwenPawProvider(config, skill_registry=registry)
        # distance is required but missing
        mock_response = _make_qwenpaw_response(
            steps=[
                {"skill_id": "move_forward", "params": {"speed": 0.5}},
            ],
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        step = proposal.steps[0]
        assert step.valid is False
        assert "distance" in (step.validation_error or "")

    async def test_valid_params_remain_valid(self):
        """A step with params that match the schema should remain valid."""
        config = _make_config()
        skill = SkillDefinition(
            skill_id="move_forward",
            input_schema={
                "type": "object",
                "properties": {
                    "distance": {"type": "number"},
                    "speed": {"type": "number"},
                },
                "required": ["distance"],
            },
        )
        registry = _make_skill_registry({"move_forward": skill})
        provider = QwenPawProvider(config, skill_registry=registry)
        mock_response = _make_qwenpaw_response(
            steps=[
                {"skill_id": "move_forward", "params": {"distance": 5.0, "speed": 0.5}},
            ],
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        step = proposal.steps[0]
        assert step.valid is True
        assert step.validation_error is None


class TestSchemaValidationPartialInvalid:
    """A proposal with a mix of valid and invalid steps should retain all."""

    async def test_mixed_valid_and_invalid_steps(self):
        config = _make_config()
        move_skill = SkillDefinition(
            skill_id="move_forward",
            input_schema={
                "type": "object",
                "properties": {"distance": {"type": "number"}},
                "required": ["distance"],
            },
        )
        capture_skill = SkillDefinition(
            skill_id="capture_image",
            input_schema={
                "type": "object",
                "properties": {"resolution": {"type": "string"}},
            },
        )
        registry = _make_skill_registry({
            "move_forward": move_skill,
            "capture_image": capture_skill,
        })
        provider = QwenPawProvider(config, skill_registry=registry)
        mock_response = _make_qwenpaw_response(
            steps=[
                # Valid step
                {"skill_id": "move_forward", "params": {"distance": 3.0}},
                # Invalid: wrong param type
                {"skill_id": "capture_image", "params": {"resolution": 1080}},
                # Invalid: unregistered skill
                {"skill_id": "unknown_skill", "params": {}},
            ],
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        assert len(proposal.steps) == 3
        assert proposal.error is None

        # Step 0: valid
        assert proposal.steps[0].valid is True
        assert proposal.steps[0].validation_error is None

        # Step 1: invalid params
        assert proposal.steps[1].valid is False
        assert proposal.steps[1].validation_error is not None

        # Step 2: unregistered skill
        assert proposal.steps[2].valid is False
        assert "not registered" in (proposal.steps[2].validation_error or "")


class TestSchemaValidationNoRegistry:
    """Without a skill registry, all steps should remain valid (backward compat)."""

    async def test_no_registry_all_steps_valid(self):
        config = _make_config()
        provider = QwenPawProvider(config)  # no skill_registry
        mock_response = _make_qwenpaw_response(
            steps=[
                {"skill_id": "any_skill", "params": {"whatever": True}},
                {"skill_id": "another_skill", "params": {}},
            ],
        )

        async def _mock_call(mission: MissionContext) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_api = _mock_call  # type: ignore[method-assign]

        proposal = await provider.generate_plan(_make_mission())

        assert len(proposal.steps) == 2
        for step in proposal.steps:
            assert step.valid is True
            assert step.validation_error is None


class TestPlanStepValidationFields:
    """Verify PlanStep default field values."""

    def test_plan_step_defaults_valid_true(self):
        step = PlanStep(step_id="s1", skill_id="move")
        assert step.valid is True
        assert step.validation_error is None

    def test_plan_step_with_validation_error(self):
        step = PlanStep(
            step_id="s1",
            skill_id="move",
            valid=False,
            validation_error="bad params",
        )
        assert step.valid is False
        assert step.validation_error == "bad params"


# ------------------------------------------------------------------
# continue_plan (replan) tests.
# ------------------------------------------------------------------


class TestContinuePlanSuccess:
    async def test_replan_generates_untrusted_proposal(self):
        """The replanned proposal must be UNTRUSTED."""
        config = _make_config()
        provider = QwenPawProvider(config)
        mock_response = _make_qwenpaw_response(
            steps=[
                {"skill_id": "move_forward", "params": {"distance": 2.0, "speed": 0.3}},
                {"skill_id": "capture_image", "params": {}},
            ],
            confidence=0.7,
        )

        async def _mock_replan_call(request: ReplanRequest) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_replan_api = _mock_replan_call  # type: ignore[method-assign]

        proposal = await provider.continue_plan(_make_replan_request())

        assert proposal.is_trusted is False
        assert proposal.mission_id == "msn_001"
        assert proposal.trace_id == "trace_001"
        assert proposal.robot_id == "rbt_01"
        assert proposal.error is None
        assert len(proposal.steps) == 2
        assert proposal.steps[0].skill_id == "move_forward"
        assert proposal.steps[1].skill_id == "capture_image"
        assert proposal.confidence == pytest.approx(0.7)

    async def test_replan_has_unique_plan_id(self):
        """Each replan should have a unique plan_id."""
        config = _make_config()
        provider = QwenPawProvider(config)
        mock_response = _make_qwenpaw_response(steps=[], confidence=0.5)

        async def _mock_replan_call(request: ReplanRequest) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_replan_api = _mock_replan_call  # type: ignore[method-assign]

        proposal1 = await provider.continue_plan(_make_replan_request())
        proposal2 = await provider.continue_plan(_make_replan_request())

        assert proposal1.plan_id != proposal2.plan_id
        assert proposal1.plan_id.startswith("plan_")


class TestContinuePlanErrors:
    async def test_replan_api_failure_returns_error_proposal(self):
        """When the replan API fails, an error proposal is returned."""
        config = _make_config()
        provider = QwenPawProvider(config)

        async def _mock_replan_call(request: ReplanRequest) -> dict[str, Any]:
            raise QwenPawProviderError("QwenPaw replan API timed out")

        provider._call_qwenpaw_replan_api = _mock_replan_call  # type: ignore[method-assign]

        proposal = await provider.continue_plan(_make_replan_request())

        assert proposal.is_trusted is False
        assert proposal.error is not None
        assert "timed out" in proposal.error
        assert len(proposal.steps) == 0

    async def test_replan_no_choices_returns_error(self):
        config = _make_config()
        provider = QwenPawProvider(config)

        async def _mock_replan_call(request: ReplanRequest) -> dict[str, Any]:
            return {"choices": []}

        provider._call_qwenpaw_replan_api = _mock_replan_call  # type: ignore[method-assign]

        proposal = await provider.continue_plan(_make_replan_request())
        assert proposal.error is not None
        assert "no choices" in (proposal.error or "")

    async def test_replan_malformed_content_returns_error(self):
        config = _make_config()
        provider = QwenPawProvider(config)

        async def _mock_replan_call(request: ReplanRequest) -> dict[str, Any]:
            return {
                "choices": [
                    {"message": {"role": "assistant", "content": "not json"}}
                ]
            }

        provider._call_qwenpaw_replan_api = _mock_replan_call  # type: ignore[method-assign]

        proposal = await provider.continue_plan(_make_replan_request())
        assert proposal.error is not None
        assert "parse" in (proposal.error or "").lower()


class TestContinuePlanSchemaValidation:
    async def test_replan_unregistered_skill_marked_invalid(self):
        config = _make_config()
        registry = StaticSkillRegistry({})
        provider = QwenPawProvider(config, skill_registry=registry)
        mock_response = _make_qwenpaw_response(
            steps=[{"skill_id": "phantom_skill", "params": {}}],
        )

        async def _mock_replan_call(request: ReplanRequest) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_replan_api = _mock_replan_call  # type: ignore[method-assign]

        proposal = await provider.continue_plan(_make_replan_request())

        assert len(proposal.steps) == 1
        assert proposal.steps[0].valid is False
        assert "not registered" in (proposal.steps[0].validation_error or "")


class TestContinuePlanNoDirectHardwareAccess:
    async def test_replan_does_not_access_motors(self):
        config = _make_config()
        provider = QwenPawProvider(config)
        mock_response = _make_qwenpaw_response(
            steps=[{"skill_id": "move_forward", "params": {"distance": 1.0}}],
        )

        async def _mock_replan_call(request: ReplanRequest) -> dict[str, Any]:
            return mock_response

        provider._call_qwenpaw_replan_api = _mock_replan_call  # type: ignore[method-assign]

        proposal = await provider.continue_plan(_make_replan_request())

        for step in proposal.steps:
            assert "cmd_vel" not in step.skill_id
            assert "motor" not in step.skill_id.lower()
            assert "joint" not in step.skill_id.lower()


# ------------------------------------------------------------------
# cancel and health tests.
# ------------------------------------------------------------------


class TestCancel:
    async def test_cancel_is_noop(self):
        """cancel should be a no-op for the stateless provider."""
        config = _make_config()
        provider = QwenPawProvider(config)

        # Should not raise and should return None.
        result = await provider.cancel("invocation_001")
        assert result is None

    async def test_cancel_default_in_abstract(self):
        """The abstract class provides a default cancel no-op."""
        # AgentRuntimeProvider.cancel is concrete, so a subclass that does
        # not override it still works.
        class MinimalProvider(AgentRuntimeProvider):
            async def generate_plan(self, mission):
                ...

            async def continue_plan(self, request):
                ...

        provider = MinimalProvider()
        result = await provider.cancel("any_id")
        assert result is None


class TestHealth:
    def test_health_returns_healthy_status(self):
        config = _make_config()
        provider = QwenPawProvider(config)

        result = provider.health()

        assert result["status"] == "healthy"
        assert result["provider"] == "qwenpaw"
        assert (
            result["endpoint"]
            == "http://localhost:8000/api/agents/opengeobot-controller/console/chat"
        )

    def test_health_default_in_abstract(self):
        """The abstract class provides a default health status."""
        class MinimalProvider(AgentRuntimeProvider):
            async def generate_plan(self, mission):
                ...

            async def continue_plan(self, request):
                ...

        provider = MinimalProvider()
        result = provider.health()
        assert result["status"] == "unknown"


class TestQwenPawProviderSystemPrompt:
    """Verify the system prompt carries the persona and safety red lines."""

    def test_system_prompt_contains_persona_and_safety(self):
        config = _make_config()
        provider = QwenPawProvider(config)

        prompt = provider._build_system_prompt()

        # Persona identity from AGENTS.md
        assert "一脑多控" in prompt
        # Untrusted-proposal safety clause
        assert "不可信提案" in prompt or "UNTRUSTED" in prompt
        # Safety red line referencing the forbidden direct motor interface
        assert "/cmd_vel" in prompt
        # Output format keys
        assert "steps" in prompt
        assert "confidence" in prompt
