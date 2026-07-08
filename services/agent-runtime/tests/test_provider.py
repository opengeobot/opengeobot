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
    PlanProposal,
    QwenPawProvider,
    QwenPawProviderError,
)


def _make_config() -> AgentConfig:
    return AgentConfig(
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        qwenpaw_endpoint="http://localhost:8000/v1/chat/completions",
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


def _make_qwenpaw_response(steps: list[dict], confidence: float = 0.9) -> dict[str, Any]:
    """Build a mock QwenPaw chat-completions response."""
    content = json.dumps({"steps": steps, "confidence": confidence})
    return {
        "choices": [
            {"message": {"role": "assistant", "content": content}}
        ]
    }


class TestAgentRuntimeProvider:
    def test_is_abstract(self):
        """AgentRuntimeProvider should be an abstract class."""
        with pytest.raises(TypeError):
            AgentRuntimeProvider()  # type: ignore[abstract]


class TestQwenPawProviderSuccess:
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
