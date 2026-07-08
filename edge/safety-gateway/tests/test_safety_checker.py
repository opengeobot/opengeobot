# Function: Safety checker unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for action-level safety checks (restricted zones, speed, collision)."""

from __future__ import annotations

from opengeobot_safety_gateway.config import SafetyGatewayConfig
from opengeobot_safety_gateway.safety_checker import SafetyChecker


def _make_config(
    max_linear_speed: float = 1.5,
    max_angular_speed: float = 1.0,
    collision_proximity_threshold: float = 0.5,
    restricted_zones: list[dict[str, float]] | None = None,
) -> SafetyGatewayConfig:
    return SafetyGatewayConfig(
        gateway_id="test_edge",
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        max_linear_speed=max_linear_speed,
        max_angular_speed=max_angular_speed,
        collision_proximity_threshold=collision_proximity_threshold,
        restricted_zones=restricted_zones or [],
        health_check_port=8081,
        skill_forward_subject_suffix="execute.approved",
        log_level="DEBUG",
    )


class TestRestrictedZone:
    def test_allow_when_no_zones_configured(self):
        config = _make_config()
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 0.0, "y": 0.0}},
        )
        assert decision.allowed is True

    def test_allow_when_outside_zone(self):
        config = _make_config(
            restricted_zones=[{"x": 5.0, "y": 5.0, "radius": 2.0}]
        )
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 0.0, "y": 0.0}},
        )
        assert decision.allowed is True
        assert "restricted_zone" in decision.checks_run
        assert "restricted_zone" not in decision.denied_checks

    def test_deny_when_current_position_in_zone(self):
        config = _make_config(
            restricted_zones=[{"x": 5.0, "y": 5.0, "radius": 2.0}]
        )
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 5.5, "y": 5.0}},
        )
        assert decision.allowed is False
        assert "restricted_zone" in decision.denied_checks
        assert "restricted zone" in decision.reason.lower()

    def test_deny_when_target_position_in_zone(self):
        config = _make_config(
            restricted_zones=[{"x": 10.0, "y": 10.0, "radius": 1.5}]
        )
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={
                "position": {"x": 0.0, "y": 0.0},
                "target_position": {"x": 10.5, "y": 10.0},
            },
        )
        assert decision.allowed is False
        assert "restricted_zone" in decision.denied_checks

    def test_deny_on_zone_boundary(self):
        """Position exactly on the boundary should be denied (distance <= radius)."""
        config = _make_config(
            restricted_zones=[{"x": 0.0, "y": 0.0, "radius": 2.0}]
        )
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 2.0, "y": 0.0}},
        )
        assert decision.allowed is False


class TestSpeedLimit:
    def test_allow_when_speed_within_limit(self):
        config = _make_config(max_linear_speed=1.5, max_angular_speed=1.0)
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"linear_speed": 1.0, "angular_speed": 0.5},
        )
        assert decision.allowed is True

    def test_deny_linear_speed_exceeds_limit(self):
        config = _make_config(max_linear_speed=1.5)
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"linear_speed": 2.0},
        )
        assert decision.allowed is False
        assert "speed_limit" in decision.denied_checks
        assert "linear" in decision.reason.lower()

    def test_deny_angular_speed_exceeds_limit(self):
        config = _make_config(max_angular_speed=1.0)
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="rotate",
            params={"angular_speed": 1.5},
        )
        assert decision.allowed is False
        assert "speed_limit" in decision.denied_checks
        assert "angular" in decision.reason.lower()

    def test_deny_negative_linear_speed_exceeds_limit(self):
        """Negative speed (reverse) exceeding the limit should also be denied."""
        config = _make_config(max_linear_speed=1.5)
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"linear_speed": -2.0},
        )
        assert decision.allowed is False
        assert "speed_limit" in decision.denied_checks

    def test_allow_when_no_speed_in_params(self):
        config = _make_config(max_linear_speed=1.5)
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="pickup",
            params={},
        )
        assert decision.allowed is True


class TestCollisionRisk:
    def test_allow_when_no_other_robots(self):
        config = _make_config(collision_proximity_threshold=0.5)
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 0.0, "y": 0.0}},
            robot_positions=None,
        )
        assert decision.allowed is True

    def test_deny_when_too_close_to_another_robot(self):
        config = _make_config(collision_proximity_threshold=0.5)
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 0.0, "y": 0.0}},
            robot_positions={"rbt_02": (0.3, 0.0)},
        )
        assert decision.allowed is False
        assert "collision_risk" in decision.denied_checks
        assert "collision" in decision.reason.lower()

    def test_allow_when_far_from_other_robots(self):
        config = _make_config(collision_proximity_threshold=0.5)
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 0.0, "y": 0.0}},
            robot_positions={"rbt_02": (5.0, 5.0)},
        )
        assert decision.allowed is True

    def test_ignore_self_position(self):
        config = _make_config(collision_proximity_threshold=0.5)
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 0.0, "y": 0.0}},
            robot_positions={"rbt_01": (0.0, 0.0)},
        )
        assert decision.allowed is True


class TestMultipleChecks:
    def test_deny_with_multiple_violations(self):
        config = _make_config(
            max_linear_speed=1.5,
            restricted_zones=[{"x": 0.0, "y": 0.0, "radius": 5.0}],
            collision_proximity_threshold=1.0,
        )
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 0.0, "y": 0.0}, "linear_speed": 3.0},
            robot_positions={"rbt_02": (0.1, 0.0)},
        )
        assert decision.allowed is False
        assert len(decision.denied_checks) == 3
        assert "restricted_zone" in decision.denied_checks
        assert "speed_limit" in decision.denied_checks
        assert "collision_risk" in decision.denied_checks

    def test_decision_has_trace_context(self):
        config = _make_config()
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={},
            trace_id="trace_abc_123",
        )
        assert decision.robot_id == "rbt_01"
        assert decision.skill_name == "move_to"
        assert decision.trace_id == "trace_abc_123"
        assert decision.timestamp != ""

    def test_all_checks_run_even_on_failure(self):
        """All checks should run even if one fails, for full audit trail."""
        config = _make_config(
            max_linear_speed=1.5,
            restricted_zones=[{"x": 0.0, "y": 0.0, "radius": 5.0}],
        )
        checker = SafetyChecker(config)
        decision = checker.check_skill_execution(
            robot_id="rbt_01",
            skill_name="move_to",
            params={"position": {"x": 0.0, "y": 0.0}, "linear_speed": 3.0},
        )
        assert "restricted_zone" in decision.checks_run
        assert "speed_limit" in decision.checks_run
        assert "collision_risk" in decision.checks_run
