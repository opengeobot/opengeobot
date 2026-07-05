# Function: Shared skill protocol and result types for the simulation adapter
# Time: 2026-07-05
# Author: AxeXie
"""Shared protocol and result types for skill implementations.

Kept in a dedicated module so the individual skill modules can import these
types without creating a circular import with ``skills/__init__``.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol, runtime_checkable


@dataclass(frozen=True)
class SkillContext:
    """Adapter-level context passed into each skill execution.

    ``active_executions`` is the count of in-flight skill executions at the
    moment the skill is invoked; ``safety_latched`` reflects the local Safety
    Gateway latch state.
    """

    active_executions: int = 0
    safety_latched: bool = False
    simulation_step: float = 0.05


@dataclass
class SkillResult:
    """Normalized result of a skill execution."""

    success: bool
    output: dict[str, Any] = field(default_factory=dict)
    error: str | None = None


@runtime_checkable
class Skill(Protocol):
    """Capability port every skill implementation must satisfy."""

    skill_id: str

    async def execute(
        self, params: dict[str, Any], ctx: SkillContext
    ) -> SkillResult: ...
