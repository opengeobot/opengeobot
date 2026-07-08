# Function: ROS1 adapter configuration unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the ROS1 adapter configuration (F-ADAPTER-002)."""

from __future__ import annotations

import pytest

from opengeobot_ros1.config import DEFAULT_NATS_URL, Ros1Config


class TestRos1ConfigDefaults:
    def test_from_env_uses_defaults_when_no_env(self, monkeypatch: pytest.MonkeyPatch) -> None:
        for key in (
            "ADAPTER_ID", "ROBOT_ID", "PROTOCOL_TYPE", "ADAPTER_VERSION",
            "NATS_URL", "NATS_MAX_RECONNECT", "ROS1_NATS_RECONNECT_WAIT",
            "NATS_CONNECT_TIMEOUT", "ROS_MASTER_URI", "ROS1_NODE_NAME", "LOG_LEVEL",
        ):
            monkeypatch.delenv(key, raising=False)

        config = Ros1Config.from_env()

        assert config.adapter_id == "adp_01J00000000000000000000001"
        assert config.robot_id == "rbt_01J00000000000000000000001"
        assert config.protocol_type == "ROS1"
        assert config.version == "0.1.0"
        assert config.nats_url == DEFAULT_NATS_URL
        assert config.nats_max_reconnect == -1
        assert config.nats_reconnect_wait == 2.0
        assert config.nats_connect_timeout == 5.0
        assert config.ros_master_uri == "http://localhost:11311"
        assert config.node_name == "opengeobot_ros1"
        assert config.log_level == "INFO"


class TestRos1ConfigEnvOverrides:
    def test_adapter_id_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ADAPTER_ID", "adp_custom")
        assert Ros1Config.from_env().adapter_id == "adp_custom"

    def test_robot_id_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROBOT_ID", "rbt_custom")
        assert Ros1Config.from_env().robot_id == "rbt_custom"

    def test_protocol_type_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("PROTOCOL_TYPE", "UNITREE")
        assert Ros1Config.from_env().protocol_type == "UNITREE"

    def test_version_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ADAPTER_VERSION", "1.0.0")
        assert Ros1Config.from_env().version == "1.0.0"

    def test_nats_url_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_URL", "nats://remote:4222")
        assert Ros1Config.from_env().nats_url == "nats://remote:4222"

    def test_max_reconnect_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_MAX_RECONNECT", "10")
        assert Ros1Config.from_env().nats_max_reconnect == 10

    def test_reconnect_wait_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROS1_NATS_RECONNECT_WAIT", "0.5")
        assert Ros1Config.from_env().nats_reconnect_wait == 0.5

    def test_connect_timeout_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_CONNECT_TIMEOUT", "15.0")
        assert Ros1Config.from_env().nats_connect_timeout == 15.0

    def test_ros_master_uri_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROS_MASTER_URI", "http://robot-host:11311")
        assert Ros1Config.from_env().ros_master_uri == "http://robot-host:11311"

    def test_node_name_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROS1_NODE_NAME", "custom_node")
        assert Ros1Config.from_env().node_name == "custom_node"

    def test_log_level_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("LOG_LEVEL", "DEBUG")
        assert Ros1Config.from_env().log_level == "DEBUG"


class TestRos1ConfigSubject:
    def test_translate_subject_contains_adapter_id(self) -> None:
        config = Ros1Config(
            adapter_id="adp_abc", robot_id="rbt_1", protocol_type="ROS1",
            version="0.1.0", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            ros_master_uri="", node_name="", log_level="INFO",
        )
        assert config.translate_subject == "opengeobot.dev.adapter.translate.adp_abc"


class TestRos1ConfigImmutable:
    def test_frozen_dataclass_cannot_be_mutated(self) -> None:
        config = Ros1Config(
            adapter_id="adp_1", robot_id="rbt_1", protocol_type="ROS1",
            version="0.1.0", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            ros_master_uri="", node_name="", log_level="INFO",
        )
        with pytest.raises(Exception):
            config.adapter_id = "mutated"  # type: ignore[misc]
