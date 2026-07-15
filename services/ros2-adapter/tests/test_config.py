# Function: ROS2 adapter configuration unit tests
# Time: 2026-07-15
# Author: AxeXie
"""Unit tests for the ROS2 adapter configuration (F-ADAPTER-003)."""

from __future__ import annotations

import pytest

from opengeobot_ros2.config import (
    DEFAULT_JETSTREAM_STREAM,
    DEFAULT_NATS_URL,
    DEFAULT_ROBOT_ID,
    Ros2Config,
)


class TestRos2ConfigDefaults:
    def test_from_env_uses_defaults_when_no_env(self, monkeypatch: pytest.MonkeyPatch) -> None:
        for key in (
            "ROBOT_ID", "NATS_URL", "NATS_MAX_RECONNECT", "ROS2_NATS_RECONNECT_WAIT",
            "NATS_CONNECT_TIMEOUT", "ROS_DOMAIN_ID", "LOG_LEVEL", "ROS2_JETSTREAM_STREAM",
        ):
            monkeypatch.delenv(key, raising=False)

        config = Ros2Config.from_env()

        assert config.robot_id == DEFAULT_ROBOT_ID
        assert config.nats_url == DEFAULT_NATS_URL
        assert config.nats_max_reconnect == -1
        assert config.nats_reconnect_wait == 2.0
        assert config.nats_connect_timeout == 5.0
        assert config.dds_domain_id == 42
        assert config.log_level == "INFO"
        assert config.jetstream_stream_name == DEFAULT_JETSTREAM_STREAM


class TestRos2ConfigEnvOverrides:
    def test_robot_id_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROBOT_ID", "rbt_custom")
        assert Ros2Config.from_env().robot_id == "rbt_custom"

    def test_nats_url_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_URL", "nats://remote:4222")
        assert Ros2Config.from_env().nats_url == "nats://remote:4222"

    def test_max_reconnect_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_MAX_RECONNECT", "10")
        assert Ros2Config.from_env().nats_max_reconnect == 10

    def test_reconnect_wait_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROS2_NATS_RECONNECT_WAIT", "0.5")
        assert Ros2Config.from_env().nats_reconnect_wait == 0.5

    def test_connect_timeout_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_CONNECT_TIMEOUT", "15.0")
        assert Ros2Config.from_env().nats_connect_timeout == 15.0

    def test_dds_domain_id_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROS_DOMAIN_ID", "7")
        assert Ros2Config.from_env().dds_domain_id == 7

    def test_log_level_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("LOG_LEVEL", "DEBUG")
        assert Ros2Config.from_env().log_level == "DEBUG"

    def test_jetstream_stream_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROS2_JETSTREAM_STREAM", "CUSTOM_STREAM")
        assert Ros2Config.from_env().jetstream_stream_name == "CUSTOM_STREAM"


class TestRos2ConfigSubject:
    def test_skill_execute_subject_contains_robot_id(self) -> None:
        config = Ros2Config(
            robot_id="rbt_abc", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            dds_domain_id=42, log_level="INFO",
            jetstream_stream_name=DEFAULT_JETSTREAM_STREAM,
        )
        assert config.skill_execute_subject == "opengeobot.dev.edge.ros2.skill.execute.rbt_abc"

    def test_jetstream_stream_subjects_contains_skill_subject(self) -> None:
        config = Ros2Config(
            robot_id="rbt_abc", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            dds_domain_id=42, log_level="INFO",
            jetstream_stream_name=DEFAULT_JETSTREAM_STREAM,
        )
        assert config.skill_execute_subject in config.jetstream_stream_subjects

    def test_jetstream_durable_name_contains_robot_id(self) -> None:
        config = Ros2Config(
            robot_id="rbt_abc", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            dds_domain_id=42, log_level="INFO",
            jetstream_stream_name=DEFAULT_JETSTREAM_STREAM,
        )
        assert config.jetstream_durable_name == "ros2-adapter-rbt_abc"


class TestRos2ConfigImmutable:
    def test_frozen_dataclass_cannot_be_mutated(self) -> None:
        config = Ros2Config(
            robot_id="rbt_1", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            dds_domain_id=42, log_level="INFO",
            jetstream_stream_name=DEFAULT_JETSTREAM_STREAM,
        )
        with pytest.raises(AttributeError):
            config.robot_id = "mutated"  # type: ignore[misc]
