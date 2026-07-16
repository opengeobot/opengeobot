# Function: Cloud command handler for the edge gateway
# Time: 2026-07-05
# Author: AxeXie
"""Receive and process cloud commands on the edge.

Commands arrive on ``opengeobot.dev.edge.command.{robot_id}`` via a JetStream
durable consumer so that messages are not lost during transient disconnects.
Mission lifecycle commands (start/pause/resume/cancel) drive the local mission
state, while ``execute_skill`` is forwarded to the local skill executor
(sim-adapter for M2) as a registered skill invocation.

Safety is enforced through the unified ``SafetyStateMachine`` (SM-SAFETY-001)
from the safety-gateway module:
  * ``emergency_stop`` latches the state machine to EMERGENCY_STOPPED.
  * ``execute_skill`` and mission ``start``/``resume`` are refused unless the
    state machine is in NORMAL.
  * ``reset_safety`` transitions EMERGENCY_STOPPED -> RESETTING -> NORMAL.

The safety state machine is **entirely local** - it does not depend on the cloud
or NATS, satisfying the safety red line that local emergency stop must not rely
on network connectivity.

Every command carries a ``trace_id`` that is propagated through skill requests,
safety transitions and state updates so the whole flow is traceable end-to-end.
"""

from __future__ import annotations

import asyncio
import json
import uuid
from datetime import datetime, timezone
from enum import Enum
from typing import TYPE_CHECKING

from loguru import logger
from pydantic import BaseModel, Field

from opengeobot_safety_gateway.safety_state import SafetyState, SafetyStateMachine

from .config import EdgeConfig

if TYPE_CHECKING:
    from .nats_client import NatsBridge
    from .offline_cache import OfflineCache
    from .state_publisher import StatePublisher


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _new_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex}"


class CommandType(str, Enum):
    START_MISSION = "start_mission"
    PAUSE_MISSION = "pause_mission"
    RESUME_MISSION = "resume_mission"
    CANCEL_MISSION = "cancel_mission"
    EMERGENCY_STOP = "emergency_stop"
    EXECUTE_SKILL = "execute_skill"
    RESET_SAFETY = "reset_safety"


class EdgeCommand(BaseModel):
    """Cloud -> edge command envelope."""

    command_id: str
    trace_id: str
    command_type: CommandType
    mission_id: str | None = None
    skill_id: str | None = None
    params: dict[str, object] = Field(default_factory=dict)
    issued_at: str = ""

    model_config = {"extra": "ignore"}


class SkillExecutionRequest(BaseModel):
    """Edge -> local skill executor request (sim-adapter for M2)."""

    request_id: str
    trace_id: str
    robot_id: str
    skill_id: str
    params: dict[str, object] = Field(default_factory=dict)
    requested_at: str


class SkillExecutionResponse(BaseModel):
    """Local skill executor -> edge response."""

    request_id: str
    trace_id: str
    skill_id: str
    success: bool
    output: dict[str, object] = Field(default_factory=dict)
    error: str | None = None
    started_at: str = ""
    completed_at: str = ""

    model_config = {"extra": "ignore"}


class CommandResult(BaseModel):
    """Local outcome of a command, used to drive state updates."""

    command_id: str
    trace_id: str
    command_type: CommandType
    accepted: bool
    success: bool
    detail: str = ""
    output: dict[str, object] = Field(default_factory=dict)


class CommandHandler:
    """Processes inbound cloud commands and forwards skill calls locally.

    Safety is enforced through the unified ``SafetyStateMachine`` from the
    safety-gateway module (SM-SAFETY-001), not a local boolean. The same
    state machine instance is shared with the ``StatePublisher`` so that state
    updates accurately reflect the safety latch.
    """

    def __init__(
        self,
        config: EdgeConfig,
        nats: NatsBridge,
        state_publisher: StatePublisher,
        offline_cache: OfflineCache,
        safety_state: SafetyStateMachine | None = None,
    ) -> None:
        self._config = config
        self._nats = nats
        self._state_publisher = state_publisher
        self._offline_cache = offline_cache
        # Unified safety state machine from the safety-gateway module (SM-SAFETY-001).
        # Local-first: does not depend on cloud or NATS.
        self._safety_state = safety_state or SafetyStateMachine()
        # In-flight mission tracking (local view; authoritative state lives in cloud).
        self._active_mission_id: str | None = None
        self._active_skill_count: int = 0

    @property
    def safety_latched(self) -> bool:
        """True when the safety state machine is not in NORMAL (i.e. unsafe)."""
        return not self._safety_state.is_safe()

    @property
    def safety_state(self) -> SafetyStateMachine:
        """Expose the unified safety state machine."""
        return self._safety_state

    @property
    def active_mission_id(self) -> str | None:
        return self._active_mission_id

    async def handle_command(self, msg: object) -> None:
        """NATS / JetStream subscription callback: parse, validate, dispatch, ack."""
        raw = getattr(msg, "data", b"")
        try:
            payload = json.loads(raw)
            command = EdgeCommand.model_validate(payload)
        except (json.JSONDecodeError, ValueError) as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed command payload")
            # Ack the JetStream message so it is not redelivered.
            await self._ack_msg(msg)
            return

        logger.bind(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type.value,
        ).info("Received cloud command")

        # Persist the command as pending until it reaches a terminal outcome.
        await self._offline_cache.add_pending_command(command.model_dump())

        result = await self._dispatch(command)

        # Publish a state update reflecting the outcome (cached if offline).
        await self._state_publisher.publish_state(
            trace_id=command.trace_id,
            last_command_id=command.command_id,
            command_result=result,
        )

        if result.accepted and result.success:
            await self._offline_cache.mark_command_done(command.command_id)

        # Ack the JetStream message after processing so the durable consumer
        # does not redeliver it. For plain NATS messages this is a no-op.
        await self._ack_msg(msg)

    async def _ack_msg(self, msg: object) -> None:
        """Acknowledge a JetStream message. No-op for plain NATS messages."""
        ack = getattr(msg, "ack", None)
        if callable(ack):
            try:
                await ack()
            except Exception:  # noqa: BLE001 - ack failure should not crash the handler
                logger.warning("Failed to ack JetStream message")

    async def _dispatch(self, command: EdgeCommand) -> CommandResult:
        handler = {
            CommandType.START_MISSION: self._start_mission,
            CommandType.PAUSE_MISSION: self._pause_mission,
            CommandType.RESUME_MISSION: self._resume_mission,
            CommandType.CANCEL_MISSION: self._cancel_mission,
            CommandType.EMERGENCY_STOP: self._emergency_stop,
            CommandType.EXECUTE_SKILL: self._execute_skill,
            CommandType.RESET_SAFETY: self._reset_safety,
        }.get(command.command_type)

        if handler is None:
            return CommandResult(
                command_id=command.command_id,
                trace_id=command.trace_id,
                command_type=command.command_type,
                accepted=False,
                success=False,
                detail=f"Unsupported command type {command.command_type}",
            )
        return await handler(command)

    # ------------------------------------------------------------------
    # Mission lifecycle (local view; cloud remains authoritative).
    # ------------------------------------------------------------------
    async def _start_mission(self, command: EdgeCommand) -> CommandResult:
        if not self._safety_state.is_safe():
            return CommandResult(
                command_id=command.command_id,
                trace_id=command.trace_id,
                command_type=command.command_type,
                accepted=False,
                success=False,
                detail="Safety latch engaged; mission start refused",
            )
        if command.mission_id is None:
            return self._missing_mission(command)
        self._active_mission_id = command.mission_id
        logger.bind(mission_id=command.mission_id).info("Mission started")
        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=True,
            success=True,
            detail=f"Mission {command.mission_id} executing",
        )

    async def _pause_mission(self, command: EdgeCommand) -> CommandResult:
        if self._active_mission_id is None:
            return self._no_active_mission(command)
        logger.bind(mission_id=self._active_mission_id).info("Mission paused")
        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=True,
            success=True,
            detail=f"Mission {self._active_mission_id} paused",
        )

    async def _resume_mission(self, command: EdgeCommand) -> CommandResult:
        if not self._safety_state.is_safe():
            return self._safety_refused(command)
        if self._active_mission_id is None:
            return self._no_active_mission(command)
        logger.bind(mission_id=self._active_mission_id).info("Mission resumed")
        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=True,
            success=True,
            detail=f"Mission {self._active_mission_id} resumed",
        )

    async def _cancel_mission(self, command: EdgeCommand) -> CommandResult:
        if self._active_mission_id is None:
            return self._no_active_mission(command)
        mission_id = self._active_mission_id
        self._active_mission_id = None
        logger.bind(mission_id=mission_id).info("Mission cancelled")
        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=True,
            success=True,
            detail=f"Mission {mission_id} cancelled",
        )

    # ------------------------------------------------------------------
    # Safety state machine integration (SM-SAFETY-001, cloud-independent).
    # ------------------------------------------------------------------
    async def _emergency_stop(self, command: EdgeCommand) -> CommandResult:
        await self._safety_state.trigger_emergency_stop(
            reason="Cloud emergency_stop command",
            trace_id=command.trace_id,
        )
        stopped = self._active_skill_count
        if self._active_mission_id is not None:
            logger.bind(mission_id=self._active_mission_id).warning(
                "Emergency stop engaged; active mission halted"
            )
        logger.bind(stopped_missions=stopped).warning("Safety latch engaged")
        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=True,
            success=True,
            detail="Emergency stop engaged and latched",
            output={"stopped_missions": stopped},
        )

    async def _reset_safety(self, command: EdgeCommand) -> CommandResult:
        current = self._safety_state.state
        if current is SafetyState.EMERGENCY_STOPPED:
            await self._safety_state.request_reset(trace_id=command.trace_id)
            await self._safety_state.complete_reset(trace_id=command.trace_id)
            logger.info("Safety reset completed; state machine back to NORMAL")
            return CommandResult(
                command_id=command.command_id,
                trace_id=command.trace_id,
                command_type=command.command_type,
                accepted=True,
                success=True,
                detail="Safety latch cleared",
            )
        if current is SafetyState.NORMAL:
            return CommandResult(
                command_id=command.command_id,
                trace_id=command.trace_id,
                command_type=command.command_type,
                accepted=True,
                success=True,
                detail="Safety already in NORMAL; no reset needed",
            )
        # In RESETTING state - complete the reset.
        await self._safety_state.complete_reset(trace_id=command.trace_id)
        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=True,
            success=True,
            detail="Safety reset completed from RESETTING",
        )

    # ------------------------------------------------------------------
    # Skill execution -> local skill executor (sim-adapter).
    # ------------------------------------------------------------------
    async def _execute_skill(self, command: EdgeCommand) -> CommandResult:
        if not self._safety_state.is_safe():
            return self._safety_refused(command)
        if not command.skill_id:
            return CommandResult(
                command_id=command.command_id,
                trace_id=command.trace_id,
                command_type=command.command_type,
                accepted=False,
                success=False,
                detail="execute_skill requires skill_id",
            )

        request = SkillExecutionRequest(
            request_id=_new_id("skreq"),
            trace_id=command.trace_id,
            robot_id=self._config.robot_id,
            skill_id=command.skill_id,
            params=command.params,
            requested_at=_now_iso(),
        )

        self._active_skill_count += 1
        try:
            response = await self._call_executor(request)
        except asyncio.TimeoutError:
            return CommandResult(
                command_id=command.command_id,
                trace_id=command.trace_id,
                command_type=command.command_type,
                accepted=True,
                success=False,
                detail=f"Skill {command.skill_id} timed out at the local executor",
            )
        except Exception as exc:  # noqa: BLE001 - executor unreachable must not crash gateway
            logger.bind(error=str(exc)).warning(
                "Local skill executor unreachable; skill request cached for retry"
            )
            return CommandResult(
                command_id=command.command_id,
                trace_id=command.trace_id,
                command_type=command.command_type,
                accepted=False,
                success=False,
                detail=f"Local executor unavailable: {exc}",
            )
        finally:
            self._active_skill_count -= 1

        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=True,
            success=response.success,
            detail=response.error or f"Skill {command.skill_id} executed",
            output=response.output,
        )

    async def _call_executor(self, request: SkillExecutionRequest) -> SkillExecutionResponse:
        """Forward the skill request through the Safety Gateway via NATS request/reply.

        The request is sent to the Safety Gateway's interception subject
        ``edge.{gateway_id}.skill.execute``. The Safety Gateway validates the
        request (action-level safety checks) and, if allowed, forwards it to
        the skill executor which dispatches to the terminal adapter (sim-adapter
        or ROSClaw bridge). The response is a ``SkillExecutionResponse``.
        """
        payload = request.model_dump_json().encode("utf-8")
        reply = await self._nats.request(
            self._config.safety_gateway_skill_subject,
            payload,
            timeout=self._config.skill_request_timeout,
        )
        return SkillExecutionResponse.model_validate_json(reply.data)

    # ------------------------------------------------------------------
    # Shared failure helpers.
    # ------------------------------------------------------------------
    @staticmethod
    def _missing_mission(command: EdgeCommand) -> CommandResult:
        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=False,
            success=False,
            detail="mission_id is required",
        )

    def _no_active_mission(self, command: EdgeCommand) -> CommandResult:
        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=False,
            success=False,
            detail="No active mission on this edge",
        )

    def _safety_refused(self, command: EdgeCommand) -> CommandResult:
        return CommandResult(
            command_id=command.command_id,
            trace_id=command.trace_id,
            command_type=command.command_type,
            accepted=False,
            success=False,
            detail="Safety latch engaged; motion refused",
        )
