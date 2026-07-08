# Function: Unitree adapter unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the Unitree Go1/Go2 protocol adapter (F-ADAPTER-002)."""

from __future__ import annotations

import pytest

from opengeobot_ros1.adapter import TranslationError
from opengeobot_ros1.unitree_adapter import DEFAULT_FORWARD_SPEED, UnitreeAdapter


class TestStandUp:
    def test_translates_stand_up(self) -> None:
        adapter = UnitreeAdapter()
        result = adapter.translate("stand_up", {"duration": 3.0})
        assert result["topic"] == "/standUpCmd"
        assert result["type"] == "std_msgs/Bool"
        assert result["data"] is True
        assert result["duration"] == 3.0

    def test_stand_up_default_duration(self) -> None:
        adapter = UnitreeAdapter()
        result = adapter.translate("stand_up", {})
        assert result["duration"] == 2.0


class TestMoveForward:
    def test_translates_move_forward(self) -> None:
        adapter = UnitreeAdapter()
        result = adapter.translate("move_forward", {"speed": 0.8, "distance": 2.0})
        assert result["topic"] == "/walkCmd"
        assert result["type"] == "geometry_msgs/Twist"
        assert result["linear"]["x"] == 0.8
        assert result["linear"]["y"] == 0.0
        assert result["linear"]["z"] == 0.0
        assert result["angular"]["x"] == 0.0
        assert result["angular"]["y"] == 0.0
        assert result["angular"]["z"] == 0.0
        assert result["distance"] == 2.0

    def test_move_forward_default_speed(self) -> None:
        adapter = UnitreeAdapter()
        result = adapter.translate("move_forward", {})
        assert result["linear"]["x"] == DEFAULT_FORWARD_SPEED
        assert result["distance"] == 1.0


class TestStop:
    def test_translates_stop(self) -> None:
        adapter = UnitreeAdapter()
        result = adapter.translate("stop", {})
        assert result["topic"] == "/stopCmd"
        assert result["type"] == "geometry_msgs/Twist"
        assert result["linear"]["x"] == 0.0
        assert result["linear"]["y"] == 0.0
        assert result["linear"]["z"] == 0.0
        assert result["angular"]["x"] == 0.0
        assert result["angular"]["y"] == 0.0
        assert result["angular"]["z"] == 0.0


class TestEmergencyStop:
    def test_translates_emergency_stop(self) -> None:
        adapter = UnitreeAdapter()
        result = adapter.translate("emergency_stop", {})
        assert result["topic"] == "/emergencyStopCmd"
        assert result["type"] == "std_msgs/Bool"
        assert result["data"] is True


class TestUnsupportedSkill:
    def test_unsupported_skill_raises(self) -> None:
        adapter = UnitreeAdapter()
        with pytest.raises(TranslationError, match="Unsupported skill_id"):
            adapter.translate("nonexistent_skill", {})

    def test_capture_image_not_supported(self) -> None:
        adapter = UnitreeAdapter()
        with pytest.raises(TranslationError):
            adapter.translate("capture_image", {})


class TestProtocolType:
    def test_protocol_type_is_unitree(self) -> None:
        assert UnitreeAdapter().protocol_type == "UNITREE"
