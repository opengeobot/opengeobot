# Function: Action-level safety checks before skill execution
# Time: 2026-07-06
# Author: AxeXie
"""Action-level safety validation for skill execution requests.

Before a skill execution request is forwarded to the local skill executor, the
Safety Gateway validates it against three action-level checks:

1. **Restricted zone check** — rejects if the robot's current or target
   position falls inside any configured restricted area.
2. **Speed limit check** — rejects if the requested linear or angular speed
   exceeds the configured maximum.
3. **Collision risk check** — rejects if the robot's target position is within
   the collision proximity threshold of any other known robot.

Each check is independent and auditable. The result is a ``SafetyDecision``
that records which checks ran, which denied the request, and the trace context
for end-to-end correlation.
"""

from __future__ import annotations

import math
from datetime import datetime, timezone

from loguru import logger
from pydantic import BaseModel, Field

from .config import SafetyGatewayConfig


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class SafetyDecision(BaseModel):
    """Result of an action-level safety check."""

    allowed: bool
    reason: str = ""
    robot_id: str = ""
    skill_name: str = ""
    trace_id: str = ""
    checks_run: list[str] = Field(default_factory=list)
    denied_checks: list[str] = Field(default_factory=list)
    timestamp: str = ""

    model_config = {"extra": "ignore"}


class SafetyChecker:
    """Validates skill execution requests against action-level safety policies."""

    def __init__(self, config: SafetyGatewayConfig) -> None:
        self._config = config

    def check_skill_execution(
        self,
        robot_id: str,
        skill_name: str,
        params: dict[str, object],
        robot_positions: dict[str, tuple[float, float]] | None = None,
        trace_id: str = "",
    ) -> SafetyDecision:
        """Run all action-level safety checks and return a SafetyDecision.

        ``params`` may contain:
          * ``position`` — ``{"x": float, "y": float}`` robot current position.
          * ``target_position`` — ``{"x": float, "y": float}`` target position.
          * ``linear_speed`` — float in m/s.
          * ``angular_speed`` — float in rad/s.

        ``robot_positions`` maps other robot IDs to their ``(x, y)`` positions
        for collision proximity checks.
        """
        checks_run: list[str] = []
        denied_checks: list[str] = []

        # --- 1. Restricted zone check ---
        zone_violation = self._check_restricted_zone(params)
        checks_run.append("restricted_zone")
        if zone_violation is not None:
            denied_checks.append("restricted_zone")

        # --- 2. Speed limit check ---
        speed_violation = self._check_speed_limit(params)
        checks_run.append("speed_limit")
        if speed_violation is not None:
            denied_checks.append("speed_limit")

        # --- 3. Collision risk check ---
        collision_violation = self._check_collision_risk(robot_id, params, robot_positions)
        checks_run.append("collision_risk")
        if collision_violation is not None:
            denied_checks.append("collision_risk")

        allowed = len(denied_checks) == 0
        if allowed:
            reason = "All safety checks passed"
        else:
            reasons = [r for r in [zone_violation, speed_violation, collision_violation] if r]
            reason = "; ".join(reasons)

        decision = SafetyDecision(
            allowed=allowed,
            reason=reason,
            robot_id=robot_id,
            skill_name=skill_name,
            trace_id=trace_id,
            checks_run=checks_run,
            denied_checks=denied_checks,
            timestamp=_now_iso(),
        )

        logger.bind(
            robot_id=robot_id,
            skill_name=skill_name,
            allowed=allowed,
            denied_checks=denied_checks,
            trace_id=trace_id,
        ).info("Safety check result: {}", "ALLOWED" if allowed else "DENIED")

        return decision

    # ------------------------------------------------------------------
    # Individual checks — return None if passed, or a denial reason string.
    # ------------------------------------------------------------------
    def _check_restricted_zone(self, params: dict[str, object]) -> str | None:
        """Reject if the robot position or target position is inside a restricted zone."""
        zones = self._config.restricted_zones
        if not zones:
            return None

        for position_key in ("position", "target_position"):
            pos = params.get(position_key)
            if not isinstance(pos, dict):
                continue
            x = float(pos.get("x", 0.0))
            y = float(pos.get("y", 0.0))
            for zone in zones:
                zx = float(zone.get("x", 0.0))
                zy = float(zone.get("y", 0.0))
                zr = float(zone.get("radius", 0.0))
                if zr <= 0:
                    continue
                distance = math.hypot(x - zx, y - zy)
                if distance <= zr:
                    return (
                        f"Position ({x}, {y}) is inside restricted zone "
                        f"at ({zx}, {zy}, r={zr})"
                    )
        return None

    def _check_speed_limit(self, params: dict[str, object]) -> str | None:
        """Reject if the requested speed exceeds configured limits."""
        linear_speed = params.get("linear_speed")
        if linear_speed is not None:
            try:
                ls = float(linear_speed)
                if abs(ls) > self._config.max_linear_speed:
                    return (
                        f"Linear speed {ls} m/s exceeds limit "
                        f"{self._config.max_linear_speed} m/s"
                    )
            except (TypeError, ValueError):
                return "Invalid linear_speed value"

        angular_speed = params.get("angular_speed")
        if angular_speed is not None:
            try:
                as_ = float(angular_speed)
                if abs(as_) > self._config.max_angular_speed:
                    return (
                        f"Angular speed {as_} rad/s exceeds limit "
                        f"{self._config.max_angular_speed} rad/s"
                    )
            except (TypeError, ValueError):
                return "Invalid angular_speed value"

        return None

    def _check_collision_risk(
        self,
        robot_id: str,
        params: dict[str, object],
        robot_positions: dict[str, tuple[float, float]] | None,
    ) -> str | None:
        """Reject if the robot's position is too close to another robot."""
        if not robot_positions:
            return None

        pos = params.get("position")
        if not isinstance(pos, dict):
            return None

        x = float(pos.get("x", 0.0))
        y = float(pos.get("y", 0.0))
        threshold = self._config.collision_proximity_threshold

        for other_id, (ox, oy) in robot_positions.items():
            if other_id == robot_id:
                continue
            distance = math.hypot(x - ox, y - oy)
            if distance < threshold:
                return (
                    f"Collision risk: distance to robot {other_id} "
                    f"is {distance:.2f} m (threshold {threshold} m)"
                )
        return None
