# Function: Edge gateway configuration unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the edge gateway configuration (F-EDGE-001)."""

from __future__ import annotations

import os

import pytest

from opengeobot_edge.config import (
    DEFAULT_CLOUD_API,
    DEFAULT_NATS_URL,
    DEFAULT_ROBOT_ID,
    EdgeConfig,
    _env_float,
    _env_int,
    _env_str,
)


class TestEnvStr:
    def test_returns_default_when_unset(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.delenv("OG_TEST_STR", raising=False)
        assert _env_str("OG_TEST_STR", "fallback") == "fallback"

    def test_returns_value_when_set(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_STR", "hello")
        assert _env_str("OG_TEST_STR", "fallback") == "hello"

    def test_returns_empty_string_when_set_empty(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_STR", "")
        # _env_str distinguishes None (unset) from "" (set to empty).
        assert _env_str("OG_TEST_STR", "fallback") == ""


class TestEnvInt:
    def test_returns_default_when_unset(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.delenv("OG_TEST_INT", raising=False)
        assert _env_int("OG_TEST_INT", 42) == 42

    def test_returns_default_when_empty(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_INT", "  ")
        assert _env_int("OG_TEST_INT", 42) == 42

    def test_parses_valid_int(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_INT", "7")
        assert _env_int("OG_TEST_INT", 42) == 7

    def test_parses_negative_int(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_INT", "-1")
        assert _env_int("OG_TEST_INT", 42) == -1

    def test_raises_on_invalid_int(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_INT", "not-a-number")
        with pytest.raises(ValueError):
            _env_int("OG_TEST_INT", 42)


class TestEnvFloat:
    def test_returns_default_when_unset(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.delenv("OG_TEST_FLOAT", raising=False)
        assert _env_float("OG_TEST_FLOAT", 3.14) == 3.14

    def test_returns_default_when_empty(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_FLOAT", "")
        assert _env_float("OG_TEST_FLOAT", 3.14) == 3.14

    def test_parses_valid_float(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_FLOAT", "2.5")
        assert _env_float("OG_TEST_FLOAT", 3.14) == 2.5

    def test_parses_int_string_as_float(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_FLOAT", "10")
        assert _env_float("OG_TEST_FLOAT", 3.14) == 10.0

    def test_raises_on_invalid_float(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("OG_TEST_FLOAT", "abc")
        with pytest.raises(ValueError):
            _env_float("OG_TEST_FLOAT", 3.14)


class TestEdgeConfigDefaults:
    def test_from_env_uses_defaults_when_no_env(self, monkeypatch: pytest.MonkeyPatch) -> None:
        for key in (
            "ROBOT_ID", "NATS_URL", "NATS_MAX_RECONNECT", "EDGE_NATS_RECONNECT_WAIT",
            "NATS_CONNECT_TIMEOUT", "CLOUD_API_BASE_URL", "EDGE_STATE_PUBLISH_INTERVAL",
            "EDGE_SKILL_REQUEST_TIMEOUT", "EDGE_OFFLINE_CACHE_PATH", "LOG_LEVEL",
            "EDGE_JETSTREAM_STREAM_NAME", "EDGE_JETSTREAM_STREAM_SUBJECTS",
            "EDGE_JETSTREAM_CONSUMER_PREFIX",
        ):
            monkeypatch.delenv(key, raising=False)

        config = EdgeConfig.from_env()

        assert config.robot_id == DEFAULT_ROBOT_ID
        assert config.nats_url == DEFAULT_NATS_URL
        assert config.nats_max_reconnect == -1
        assert config.nats_reconnect_wait == 2.0
        assert config.nats_connect_timeout == 5.0
        assert config.cloud_api_base_url == DEFAULT_CLOUD_API
        assert config.state_publish_interval == 5.0
        assert config.skill_request_timeout == 10.0
        assert config.offline_cache_path == "./.edge-data/offline-cache.json"
        assert config.log_level == "INFO"
        assert config.jetstream_stream_name == "EDGE_STREAM"
        assert config.jetstream_stream_subjects == "opengeobot.dev.edge.>"
        assert config.jetstream_consumer_prefix == "edge-cmd"


class TestEdgeConfigEnvOverrides:
    def test_robot_id_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROBOT_ID", "rbt_custom_001")
        config = EdgeConfig.from_env()
        assert config.robot_id == "rbt_custom_001"

    def test_nats_url_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_URL", "nats://remote:4223")
        config = EdgeConfig.from_env()
        assert config.nats_url == "nats://remote:4223"

    def test_max_reconnect_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_MAX_RECONNECT", "10")
        config = EdgeConfig.from_env()
        assert config.nats_max_reconnect == 10

    def test_reconnect_wait_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("EDGE_NATS_RECONNECT_WAIT", "0.5")
        config = EdgeConfig.from_env()
        assert config.nats_reconnect_wait == 0.5

    def test_connect_timeout_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_CONNECT_TIMEOUT", "15.0")
        config = EdgeConfig.from_env()
        assert config.nats_connect_timeout == 15.0

    def test_offline_cache_path_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("EDGE_OFFLINE_CACHE_PATH", "/tmp/custom-cache.json")
        config = EdgeConfig.from_env()
        assert config.offline_cache_path == "/tmp/custom-cache.json"

    def test_log_level_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("LOG_LEVEL", "DEBUG")
        config = EdgeConfig.from_env()
        assert config.log_level == "DEBUG"

    def test_cloud_api_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("CLOUD_API_BASE_URL", "http://cloud:9090")
        config = EdgeConfig.from_env()
        assert config.cloud_api_base_url == "http://cloud:9090"

    def test_jetstream_stream_name_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("EDGE_JETSTREAM_STREAM_NAME", "CUSTOM_EDGE_STREAM")
        config = EdgeConfig.from_env()
        assert config.jetstream_stream_name == "CUSTOM_EDGE_STREAM"

    def test_jetstream_stream_subjects_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("EDGE_JETSTREAM_STREAM_SUBJECTS", "custom.edge.>")
        config = EdgeConfig.from_env()
        assert config.jetstream_stream_subjects == "custom.edge.>"

    def test_jetstream_consumer_prefix_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("EDGE_JETSTREAM_CONSUMER_PREFIX", "edge-cmd-custom")
        config = EdgeConfig.from_env()
        assert config.jetstream_consumer_prefix == "edge-cmd-custom"


class TestEdgeConfigSubjects:
    def test_command_subject_contains_robot_id(self) -> None:
        config = EdgeConfig(
            robot_id="rbt_abc", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            cloud_api_base_url="", state_publish_interval=5.0,
            skill_request_timeout=10.0, offline_cache_path="", log_level="INFO",
        )
        assert config.command_subject == "opengeobot.dev.edge.command.rbt_abc"

    def test_state_subject_contains_robot_id(self) -> None:
        config = EdgeConfig(
            robot_id="rbt_xyz", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            cloud_api_base_url="", state_publish_interval=5.0,
            skill_request_timeout=10.0, offline_cache_path="", log_level="INFO",
        )
        assert config.state_subject == "opengeobot.dev.edge.state.rbt_xyz"

    def test_skill_execute_subject_contains_robot_id(self) -> None:
        config = EdgeConfig(
            robot_id="rbt_123", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            cloud_api_base_url="", state_publish_interval=5.0,
            skill_request_timeout=10.0, offline_cache_path="", log_level="INFO",
        )
        assert config.skill_execute_subject == "opengeobot.dev.edge.skill.execute.rbt_123"

    def test_reconciliation_subject_contains_robot_id(self) -> None:
        config = EdgeConfig(
            robot_id="rbt_456", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            cloud_api_base_url="", state_publish_interval=5.0,
            skill_request_timeout=10.0, offline_cache_path="", log_level="INFO",
        )
        assert config.reconciliation_subject == "opengeobot.dev.edge.reconcile.rbt_456"

    def test_jetstream_consumer_name_contains_robot_id(self) -> None:
        config = EdgeConfig(
            robot_id="rbt_789", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            cloud_api_base_url="", state_publish_interval=5.0,
            skill_request_timeout=10.0, offline_cache_path="", log_level="INFO",
        )
        assert config.jetstream_consumer_name == "edge-cmd-rbt_789"

    def test_jetstream_consumer_name_with_custom_prefix(self) -> None:
        config = EdgeConfig(
            robot_id="rbt_789", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            cloud_api_base_url="", state_publish_interval=5.0,
            skill_request_timeout=10.0, offline_cache_path="", log_level="INFO",
            jetstream_consumer_prefix="gw-cmd",
        )
        assert config.jetstream_consumer_name == "gw-cmd-rbt_789"


class TestEdgeConfigImmutable:
    def test_frozen_dataclass_cannot_be_mutated(self) -> None:
        config = EdgeConfig(
            robot_id="rbt_1", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            cloud_api_base_url="", state_publish_interval=5.0,
            skill_request_timeout=10.0, offline_cache_path="", log_level="INFO",
        )
        with pytest.raises(Exception):
            config.robot_id = "mutated"  # type: ignore[misc]
