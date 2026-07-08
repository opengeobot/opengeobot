# Function: Simulation adapter configuration unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the simulation adapter configuration (F-ADAPTER-001)."""

from __future__ import annotations

import pytest

from opengeobot_sim.config import (
    DEFAULT_NATS_URL,
    DEFAULT_ROBOT_ID,
    SimConfig,
)


class TestSimConfigDefaults:
    def test_from_env_uses_defaults_when_no_env(self, monkeypatch: pytest.MonkeyPatch) -> None:
        for key in (
            "ROBOT_ID", "NATS_URL", "NATS_MAX_RECONNECT", "SIM_NATS_RECONNECT_WAIT",
            "NATS_CONNECT_TIMEOUT", "SIMULATION_STEP", "LOG_LEVEL",
        ):
            monkeypatch.delenv(key, raising=False)

        config = SimConfig.from_env()

        assert config.robot_id == DEFAULT_ROBOT_ID
        assert config.nats_url == DEFAULT_NATS_URL
        assert config.nats_max_reconnect == -1
        assert config.nats_reconnect_wait == 2.0
        assert config.nats_connect_timeout == 5.0
        assert config.simulation_step == 0.05
        assert config.log_level == "INFO"


class TestSimConfigEnvOverrides:
    def test_robot_id_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("ROBOT_ID", "rbt_custom")
        assert SimConfig.from_env().robot_id == "rbt_custom"

    def test_nats_url_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_URL", "nats://remote:4222")
        assert SimConfig.from_env().nats_url == "nats://remote:4222"

    def test_max_reconnect_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_MAX_RECONNECT", "10")
        assert SimConfig.from_env().nats_max_reconnect == 10

    def test_reconnect_wait_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("SIM_NATS_RECONNECT_WAIT", "0.5")
        assert SimConfig.from_env().nats_reconnect_wait == 0.5

    def test_connect_timeout_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("NATS_CONNECT_TIMEOUT", "15.0")
        assert SimConfig.from_env().nats_connect_timeout == 15.0

    def test_simulation_step_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("SIMULATION_STEP", "0.01")
        assert SimConfig.from_env().simulation_step == 0.01

    def test_log_level_override(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setenv("LOG_LEVEL", "DEBUG")
        assert SimConfig.from_env().log_level == "DEBUG"


class TestSimConfigSubject:
    def test_skill_execute_subject_contains_robot_id(self) -> None:
        config = SimConfig(
            robot_id="rbt_abc", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            simulation_step=0.05, log_level="INFO",
        )
        assert config.skill_execute_subject == "opengeobot.dev.edge.skill.execute.rbt_abc"


class TestSimConfigImmutable:
    def test_frozen_dataclass_cannot_be_mutated(self) -> None:
        config = SimConfig(
            robot_id="rbt_1", nats_url="", nats_max_reconnect=-1,
            nats_reconnect_wait=2.0, nats_connect_timeout=5.0,
            simulation_step=0.05, log_level="INFO",
        )
        with pytest.raises(Exception):
            config.robot_id = "mutated"  # type: ignore[misc]
