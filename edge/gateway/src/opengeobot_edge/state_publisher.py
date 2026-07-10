# Function: Robot state publisher (edge → cloud)
# Time: 2026-07-05
# Author: AxeXie
"""Publish robot operational status to the cloud.

State updates are published on ``opengeobot.dev.edge.state.{robot_id}``. When the
NATS connection is unavailable the state is appended to the offline cache so it
can be flushed on reconnect (F-EDGE-002 reconciliation). A periodic heartbeat
also publishes the current status so the cloud can detect stale edges.
"""

from __future__ import annotations

import asyncio
import uuid
from datetime import datetime, timezone
from enum import Enum
from typing import TYPE_CHECKING

from loguru import logger
from pydantic import BaseModel, Field

from opengeobot_safety_gateway.safety_state import SafetyStateMachine

from .config import EdgeConfig

if TYPE_CHECKING:
    from .command_handler import CommandResult
    from .nats_client import NatsBridge
    from .offline_cache import OfflineCache


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _new_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex}"


class RobotStatus(str, Enum):
    ONLINE = "ONLINE"
    OFFLINE = "OFFLINE"
    BUSY = "BUSY"
    ERROR = "ERROR"
    MAINTENANCE = "MAINTENANCE"


class EdgeState(BaseModel):
    """Edge → cloud state update."""

    state_id: str
    trace_id: str
    robot_id: str
    status: RobotStatus
    mission_id: str | None = None
    last_command_id: str | None = None
    last_command_success: bool | None = None
    last_command_detail: str = ""
    safety_latched: bool = False
    offline_mode: bool = False
    reported_at: str

    model_config = {"extra": "ignore"}


class StatePublisher:
    """Publishes robot state to the cloud and caches it when offline."""

    def __init__(
        self,
        config: EdgeConfig,
        nats: NatsBridge,
        offline_cache: OfflineCache,
        safety_state: SafetyStateMachine | None = None,
    ) -> None:
        self._config = config
        self._nats = nats
        self._offline_cache = offline_cache
        self._status: RobotStatus = RobotStatus.ONLINE
        self._mission_id: str | None = None
        # Unified safety state machine (shared with CommandHandler). When provided,
        # the safety_latched property reads directly from it.
        self._safety_state = safety_state
        self._safety_latched: bool = False
        self._heartbeat_task: asyncio.Task[None] | None = None

    @property
    def status(self) -> RobotStatus:
        return self._status

    @property
    def safety_latched(self) -> bool:
        """Read from the unified SafetyStateMachine when available."""
        if self._safety_state is not None:
            return not self._safety_state.is_safe()
        return self._safety_latched

    def set_mission(self, mission_id: str | None) -> None:
        self._mission_id = mission_id

    async def set_safety_latched(self, latched: bool) -> None:
        """Update the safety latch.

        When a unified ``SafetyStateMachine`` is provided, the latch is driven
        through the state machine (SM-SAFETY-001 transitions). Otherwise a
        local boolean is used for backward compatibility.
        """
        if self._safety_state is not None:
            if latched:
                await self._safety_state.trigger_emergency_stop()
            else:
                await self._safety_state.request_reset()
                await self._safety_state.complete_reset()
        else:
            self._safety_latched = latched
        if latched:
            self._status = RobotStatus.ERROR

    def mark_online(self) -> None:
        self._status = RobotStatus.ONLINE

    def mark_offline(self) -> None:
        self._status = RobotStatus.OFFLINE

    async def mark_online_after_reconnect(self) -> None:
        """Clear offline-mode status after the NATS connection is restored."""
        if self.safety_latched:
            # Preserve ERROR state when the safety latch is still engaged.
            return
        self._status = RobotStatus.ONLINE
        logger.info("Edge marked online after reconnect")

    def derive_status(
        self, command_result: CommandResult | None, online: bool
    ) -> RobotStatus:
        """Derive the current status from safety, online state and command result."""
        if self.safety_latched:
            return RobotStatus.ERROR
        if not online:
            return RobotStatus.OFFLINE
        if command_result is not None and command_result.success:
            return RobotStatus.BUSY if self._mission_id is not None else RobotStatus.ONLINE
        if command_result is not None and not command_result.success:
            return RobotStatus.ERROR
        return self._status

    async def publish_state(
        self,
        trace_id: str,
        last_command_id: str | None = None,
        command_result: CommandResult | None = None,
    ) -> None:
        online = self._nats.is_connected
        status = self.derive_status(command_result, online)
        self._status = status

        state = EdgeState(
            state_id=_new_id("est"),
            trace_id=trace_id,
            robot_id=self._config.robot_id,
            status=status,
            mission_id=self._mission_id,
            last_command_id=last_command_id,
            last_command_success=command_result.success if command_result else None,
            last_command_detail=command_result.detail if command_result else "",
            safety_latched=self.safety_latched,
            offline_mode=not online,
            reported_at=_now_iso(),
        )

        if not online:
            await self._offline_cache.add_pending_state(state.model_dump())
            logger.bind(state_id=state.state_id).debug("State cached (offline mode)")
            return

        await self._publish(state)

    async def publish_heartbeat(self) -> None:
        """Publish a periodic heartbeat so the cloud can detect stale edges."""
        await self.publish_state(trace_id=_new_id("hb"))

    async def flush_cached_state(self, state: EdgeState) -> bool:
        """Publish a single cached state. Returns True on success."""
        if not self._nats.is_connected:
            return False
        await self._publish(state)
        return True

    async def start_heartbeat(self) -> None:
        if self._heartbeat_task is not None:
            return
        self._heartbeat_task = asyncio.create_task(
            self._heartbeat_loop(), name="edge-state-heartbeat"
        )

    async def stop_heartbeat(self) -> None:
        if self._heartbeat_task is None:
            return
        self._heartbeat_task.cancel()
        try:
            await self._heartbeat_task
        except asyncio.CancelledError:
            pass
        self._heartbeat_task = None

    async def _heartbeat_loop(self) -> None:
        interval = self._config.state_publish_interval
        while True:
            await asyncio.sleep(interval)
            try:
                await self.publish_heartbeat()
            except asyncio.CancelledError:
                raise
            except Exception:  # noqa: BLE001 — heartbeat must survive transient errors
                logger.exception("Heartbeat publish failed; will retry next interval")

    async def _publish(self, state: EdgeState) -> None:
        payload = state.model_dump_json().encode("utf-8")
        await self._nats.publish(self._config.state_subject, payload)
        logger.bind(
            state_id=state.state_id,
            status=state.status.value,
            trace_id=state.trace_id,
        ).debug("Published robot state")
