# Function: Custom adapter unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the custom protocol adapter (F-ADAPTER-002)."""

from __future__ import annotations

import pytest

from opengeobot_ros1.adapter import TranslationError
from opengeobot_ros1.custom_adapter import SUPPORTED_SKILLS, CustomAdapter


class TestStandUp:
    def test_translates_stand_up(self) -> None:
        adapter = CustomAdapter()
        result = adapter.translate("stand_up", {"duration": 3.0})
        assert result["command"] == "stand_up"
        assert result["topic"] == "/custom/stand_up"
        assert result["type"] == "opengeobot/CustomCommand"
        assert result["params"] == {"duration": 3.0}
        assert result["seq"] == 1
        assert result["expected_response"]["type"] == "opengeobot/CustomResponse"
        assert result["expected_response"]["success_field"] == "ok"


class TestMoveForward:
    def test_translates_move_forward(self) -> None:
        adapter = CustomAdapter()
        result = adapter.translate("move_forward", {"speed": 0.5, "distance": 1.0})
        assert result["command"] == "move_forward"
        assert result["topic"] == "/custom/move_forward"
        assert result["params"] == {"speed": 0.5, "distance": 1.0}


class TestStop:
    def test_translates_stop(self) -> None:
        adapter = CustomAdapter()
        result = adapter.translate("stop", {})
        assert result["command"] == "stop"
        assert result["topic"] == "/custom/stop"


class TestEmergencyStop:
    def test_translates_emergency_stop(self) -> None:
        adapter = CustomAdapter()
        result = adapter.translate("emergency_stop", {})
        assert result["command"] == "emergency_stop"
        assert result["topic"] == "/custom/emergency_stop"


class TestCaptureImage:
    def test_translates_capture_image(self) -> None:
        adapter = CustomAdapter()
        result = adapter.translate("capture_image", {"resolution": "640x480"})
        assert result["command"] == "capture_image"
        assert result["topic"] == "/custom/capture_image"
        assert result["params"] == {"resolution": "640x480"}


class TestSequenceNumber:
    def test_sequence_increments(self) -> None:
        adapter = CustomAdapter()
        r1 = adapter.translate("stop", {})
        r2 = adapter.translate("stop", {})
        r3 = adapter.translate("stop", {})
        assert r1["seq"] == 1
        assert r2["seq"] == 2
        assert r3["seq"] == 3

    def test_sequence_starts_at_one(self) -> None:
        adapter = CustomAdapter()
        result = adapter.translate("stop", {})
        assert result["seq"] == 1


class TestUnsupportedSkill:
    def test_unsupported_skill_raises(self) -> None:
        adapter = CustomAdapter()
        with pytest.raises(TranslationError, match="Unsupported skill_id"):
            adapter.translate("nonexistent_skill", {})


class TestSupportedSkills:
    def test_supported_skills_set(self) -> None:
        assert "stand_up" in SUPPORTED_SKILLS
        assert "move_forward" in SUPPORTED_SKILLS
        assert "stop" in SUPPORTED_SKILLS
        assert "emergency_stop" in SUPPORTED_SKILLS
        assert "capture_image" in SUPPORTED_SKILLS

    def test_supported_skills_is_frozenset(self) -> None:
        assert isinstance(SUPPORTED_SKILLS, frozenset)


class TestParamsCopied:
    def test_params_not_mutated(self) -> None:
        adapter = CustomAdapter()
        params = {"distance": 1.0, "speed": 0.5}
        result = adapter.translate("move_forward", params)
        # Mutating the result's params should not affect the original.
        result["params"]["distance"] = 999.0
        assert params["distance"] == 1.0


class TestProtocolType:
    def test_protocol_type_is_custom(self) -> None:
        assert CustomAdapter().protocol_type == "CUSTOM"
