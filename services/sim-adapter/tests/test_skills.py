# Function: Simulation skill unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the simulated skill implementations (F-ADAPTER-001)."""

from __future__ import annotations

from typing import Any

import pytest

from opengeobot_sim.skills import (
    CaptureImageSkill,
    EmergencyStopSkill,
    MoveForwardSkill,
    Skill,
    StandUpSkill,
    StopSkill,
    default_skills,
)
from opengeobot_sim.skills.base import SkillContext, SkillResult
from opengeobot_sim.skills.move_forward import MAX_DISTANCE_METERS, MAX_SPEED_MPS


def _ctx(**overrides: Any) -> SkillContext:
    defaults: dict[str, Any] = {
        "active_executions": 0,
        "safety_latched": False,
        "simulation_step": 0.0,
    }
    defaults.update(overrides)
    return SkillContext(**defaults)


class TestSkillProtocol:
    def test_all_skills_satisfy_protocol(self) -> None:
        for skill in [StandUpSkill(), StopSkill(), MoveForwardSkill(), CaptureImageSkill(), EmergencyStopSkill()]:
            assert isinstance(skill, Skill)

    def test_default_skills_returns_all_skills(self) -> None:
        skills = default_skills()
        assert "stand_up" in skills
        assert "stop" in skills
        assert "move_forward" in skills
        assert "capture_image" in skills
        assert "emergency_stop" in skills

    def test_default_skills_no_duplicates(self) -> None:
        skills = default_skills()
        assert len(skills) == len(set(skills.keys()))


class TestMoveForwardSkill:
    async def test_success(self) -> None:
        skill = MoveForwardSkill()
        result = await skill.execute({"distance": 2.0, "speed": 1.0}, _ctx())
        assert result.success is True
        assert result.output["distance"] == 2.0
        assert result.output["duration"] == 2.0

    async def test_safety_latched_refuses_motion(self) -> None:
        skill = MoveForwardSkill()
        result = await skill.execute(
            {"distance": 2.0, "speed": 1.0}, _ctx(safety_latched=True)
        )
        assert result.success is False
        assert "Safety latch" in result.error

    async def test_negative_distance_rejected(self) -> None:
        skill = MoveForwardSkill()
        result = await skill.execute({"distance": -1.0, "speed": 1.0}, _ctx())
        assert result.success is False
        assert "distance" in result.error

    async def test_zero_speed_rejected(self) -> None:
        skill = MoveForwardSkill()
        result = await skill.execute({"distance": 1.0, "speed": 0.0}, _ctx())
        assert result.success is False
        assert "speed" in result.error

    async def test_exceeds_max_distance(self) -> None:
        skill = MoveForwardSkill()
        result = await skill.execute(
            {"distance": MAX_DISTANCE_METERS + 1.0, "speed": 1.0}, _ctx()
        )
        assert result.success is False
        assert "exceeds bounded limit" in result.error

    async def test_exceeds_max_speed(self) -> None:
        skill = MoveForwardSkill()
        result = await skill.execute(
            {"distance": 1.0, "speed": MAX_SPEED_MPS + 0.1}, _ctx()
        )
        assert result.success is False
        assert "exceeds bounded limit" in result.error

    async def test_invalid_distance_type(self) -> None:
        skill = MoveForwardSkill()
        result = await skill.execute({"distance": "abc", "speed": 1.0}, _ctx())
        assert result.success is False
        assert "Invalid" in result.error

    async def test_missing_params_uses_defaults(self) -> None:
        skill = MoveForwardSkill()
        # distance defaults to 0.0, speed defaults to 0.0 → speed <= 0 fails.
        result = await skill.execute({}, _ctx())
        assert result.success is False
        assert "speed" in result.error

    async def test_skill_id(self) -> None:
        assert MoveForwardSkill.skill_id == "move_forward"


class TestStopSkill:
    async def test_success(self) -> None:
        skill = StopSkill()
        result = await skill.execute({}, _ctx())
        assert result.success is True
        assert result.output["success"] is True

    async def test_ignores_extra_params(self) -> None:
        skill = StopSkill()
        result = await skill.execute({"unexpected": True}, _ctx())
        assert result.success is True

    def test_skill_id(self) -> None:
        assert StopSkill.skill_id == "stop"


class TestStandUpSkill:
    async def test_success(self) -> None:
        skill = StandUpSkill()
        result = await skill.execute({"duration": 2.0}, _ctx())
        assert result.success is True
        assert result.output["duration"] == 2.0

    async def test_invalid_duration_type(self) -> None:
        skill = StandUpSkill()
        result = await skill.execute({"duration": "abc"}, _ctx())
        assert result.success is False
        assert "Invalid duration" in result.error

    async def test_negative_duration(self) -> None:
        skill = StandUpSkill()
        result = await skill.execute({"duration": -1.0}, _ctx())
        assert result.success is False
        assert "duration" in result.error

    async def test_missing_duration_defaults_to_zero(self) -> None:
        skill = StandUpSkill()
        result = await skill.execute({}, _ctx())
        assert result.success is True
        assert result.output["duration"] == 0.0

    def test_skill_id(self) -> None:
        assert StandUpSkill.skill_id == "stand_up"


class TestCaptureImageSkill:
    async def test_success(self) -> None:
        skill = CaptureImageSkill()
        result = await skill.execute({"resolution": "640x480"}, _ctx())
        assert result.success is True
        assert result.output["image_path"].startswith("/sim/captures/")
        assert result.output["image_path"].endswith(".jpg")
        assert result.output["size_bytes"] > 0

    async def test_default_resolution(self) -> None:
        skill = CaptureImageSkill()
        result = await skill.execute({}, _ctx())
        assert result.success is True
        # 640*480*3*0.7 = 645120
        assert result.output["size_bytes"] == 645120

    async def test_invalid_resolution_format(self) -> None:
        skill = CaptureImageSkill()
        result = await skill.execute({"resolution": "invalid"}, _ctx())
        assert result.success is False
        assert "Invalid resolution" in result.error

    async def test_resolution_with_uppercase_x(self) -> None:
        skill = CaptureImageSkill()
        result = await skill.execute({"resolution": "800X600"}, _ctx())
        assert result.success is True

    async def test_zero_dimension_rejected(self) -> None:
        skill = CaptureImageSkill()
        result = await skill.execute({"resolution": "0x480"}, _ctx())
        assert result.success is False
        assert "dimensions" in result.error

    async def test_size_calculation(self) -> None:
        skill = CaptureImageSkill()
        result = await skill.execute({"resolution": "100x100"}, _ctx())
        assert result.success is True
        # 100*100*3*0.7 = 21000
        assert result.output["size_bytes"] == 21000

    def test_skill_id(self) -> None:
        assert CaptureImageSkill.skill_id == "capture_image"


class TestEmergencyStopSkill:
    async def test_success_reports_active_executions(self) -> None:
        skill = EmergencyStopSkill()
        result = await skill.execute({}, _ctx(active_executions=3))
        assert result.success is True
        assert result.output["stopped_missions"] == 3

    async def test_success_with_zero_executions(self) -> None:
        skill = EmergencyStopSkill()
        result = await skill.execute({}, _ctx(active_executions=0))
        assert result.success is True
        assert result.output["stopped_missions"] == 0

    async def test_ignores_params(self) -> None:
        skill = EmergencyStopSkill()
        result = await skill.execute({"unexpected": True}, _ctx())
        assert result.success is True

    def test_skill_id(self) -> None:
        assert EmergencyStopSkill.skill_id == "emergency_stop"


class TestSkillResultDefaults:
    def test_default_output_is_empty_dict(self) -> None:
        result = SkillResult(success=True)
        assert result.output == {}

    def test_default_error_is_none(self) -> None:
        result = SkillResult(success=True)
        assert result.error is None

    def test_with_error_and_output(self) -> None:
        result = SkillResult(success=False, error="failed", output={"code": 1})
        assert result.success is False
        assert result.error == "failed"
        assert result.output == {"code": 1}
