# Function: ROS1 native adapter unit tests
# Time: 2026-07-15
# Author: AxeXie
"""Unit tests for the ROS1 rospy native protocol adapter (F-ADAPTER-002)."""

from __future__ import annotations

import pytest

from opengeobot_ros1.adapter import ProtocolAdapter, TranslationError
from opengeobot_ros1.custom_adapter import CustomAdapter
from opengeobot_ros1.main import _select_protocol_handler
from opengeobot_ros1.ros1_native_adapter import (
    CMD_VEL_TOPIC,
    DEFAULT_FORWARD_SPEED,
    Ros1NativeAdapter,
)
import opengeobot_ros1.ros1_native_adapter as _native_module


@pytest.fixture(autouse=True)
def _disable_rospy(monkeypatch: pytest.MonkeyPatch) -> None:
    """Force rospy to be unavailable so tests are deterministic across
    environments and never attempt to connect to a real ROS master.
    """
    monkeypatch.setattr(_native_module, "_ROSPY_AVAILABLE", False)


def _make_adapter(
    ros_master_uri: str = "http://localhost:11311",
    node_name: str = "test_ros1_native",
) -> Ros1NativeAdapter:
    return Ros1NativeAdapter(ros_master_uri, node_name)


class TestProtocolSatisfaction:
    def test_satisfies_protocol_adapter(self) -> None:
        adapter = _make_adapter()
        assert isinstance(adapter, ProtocolAdapter)

    def test_protocol_type_is_ros1_native(self) -> None:
        adapter = _make_adapter()
        assert adapter.protocol_type == "ROS1_NATIVE"

    def test_has_translate_method(self) -> None:
        adapter = _make_adapter()
        assert callable(getattr(adapter, "translate", None))


class TestMoveForward:
    def test_translates_move_forward(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("move_forward", {"speed": 0.8, "duration": 2.0})
        assert result["topic"] == CMD_VEL_TOPIC
        assert result["type"] == "geometry_msgs/Twist"
        assert result["linear"]["x"] == 0.8
        assert result["linear"]["y"] == 0.0
        assert result["linear"]["z"] == 0.0
        assert result["angular"]["x"] == 0.0
        assert result["angular"]["y"] == 0.0
        assert result["angular"]["z"] == 0.0
        assert result["duration"] == 2.0

    def test_move_forward_default_speed(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("move_forward", {})
        assert result["linear"]["x"] == DEFAULT_FORWARD_SPEED
        assert result["duration"] == 1.0

    def test_move_forward_linear_x_positive(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("move_forward", {"speed": 0.3})
        assert result["linear"]["x"] > 0


class TestStop:
    def test_translates_stop(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("stop", {})
        assert result["topic"] == CMD_VEL_TOPIC
        assert result["type"] == "geometry_msgs/Twist"
        assert result["linear"]["x"] == 0.0
        assert result["linear"]["y"] == 0.0
        assert result["linear"]["z"] == 0.0
        assert result["angular"]["x"] == 0.0
        assert result["angular"]["y"] == 0.0
        assert result["angular"]["z"] == 0.0


class TestEmergencyStop:
    def test_translates_emergency_stop(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("emergency_stop", {})
        assert result["topic"] == CMD_VEL_TOPIC
        assert result["type"] == "geometry_msgs/Twist"
        assert result["linear"]["x"] == 0.0
        assert result["linear"]["y"] == 0.0
        assert result["linear"]["z"] == 0.0
        assert result["angular"]["x"] == 0.0
        assert result["angular"]["y"] == 0.0
        assert result["angular"]["z"] == 0.0
        assert result["emergency_stop"] is True


class TestStandUp:
    def test_translates_stand_up(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("stand_up", {"duration": 3.0})
        assert result["service"] == "/stand_up"
        assert result["type"] == "std_srvs/Trigger"
        assert result["success"] is True
        assert result["duration"] == 3.0

    def test_stand_up_default_duration(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("stand_up", {})
        assert result["duration"] == 2.0


class TestCaptureImage:
    def test_translates_capture_image(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("capture_image", {"resolution": "1280x720"})
        assert result["topic"] == "/image_raw"
        assert result["type"] == "sensor_msgs/Image"
        assert result["simulated"] is True
        assert result["resolution"] == "1280x720"

    def test_capture_image_default_resolution(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("capture_image", {})
        assert result["resolution"] == "640x480"
        assert result["simulated"] is True


class TestUnsupportedSkill:
    def test_unsupported_skill_raises(self) -> None:
        adapter = _make_adapter()
        with pytest.raises(TranslationError, match="Unsupported skill_id"):
            adapter.translate("nonexistent_skill", {})

    def test_unsupported_skill_message_contains_protocol(self) -> None:
        adapter = _make_adapter()
        with pytest.raises(TranslationError, match="ROS1_NATIVE"):
            adapter.translate("flying_skill", {})


class TestSelectProtocolHandler:
    def test_ros1_native_returns_native_adapter(self) -> None:
        handler = _select_protocol_handler("ROS1_NATIVE")
        assert isinstance(handler, Ros1NativeAdapter)

    def test_ros1_native_returns_correct_protocol_type(self) -> None:
        handler = _select_protocol_handler("ROS1_NATIVE")
        assert handler.protocol_type == "ROS1_NATIVE"

    def test_ros1_native_lowercase(self) -> None:
        handler = _select_protocol_handler("ros1_native")
        assert isinstance(handler, Ros1NativeAdapter)


class TestBackwardCompatibility:
    def test_ros1_returns_custom_adapter(self) -> None:
        """ROS1 (without _NATIVE) still maps to CustomAdapter for backward
        compatibility until the ROS1 Jazzy contract is pinned.
        """
        handler = _select_protocol_handler("ROS1")
        assert isinstance(handler, CustomAdapter)

    def test_ros1_lowercase_returns_custom_adapter(self) -> None:
        handler = _select_protocol_handler("ros1")
        assert isinstance(handler, CustomAdapter)

    def test_ros1_protocol_type_is_custom(self) -> None:
        handler = _select_protocol_handler("ROS1")
        assert handler.protocol_type == "CUSTOM"


class TestGracefulRospyFallback:
    def test_rospy_not_available_in_test_env(self) -> None:
        assert _native_module._ROSPY_AVAILABLE is False

    def test_publisher_is_none_when_rospy_unavailable(self) -> None:
        adapter = _make_adapter()
        assert adapter._publisher is None

    def test_move_forward_published_false(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("move_forward", {"speed": 0.5})
        assert result["published"] is False

    def test_stop_published_false(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("stop", {})
        assert result["published"] is False

    def test_emergency_stop_published_false(self) -> None:
        adapter = _make_adapter()
        result = adapter.translate("emergency_stop", {})
        assert result["published"] is False

    def test_translate_does_not_raise_without_rospy(self) -> None:
        adapter = _make_adapter()
        for skill_id in ("move_forward", "stop", "emergency_stop", "stand_up", "capture_image"):
            result = adapter.translate(skill_id, {})
            assert isinstance(result, dict)
