# Function: ROSClaw NATS Bridge skill execution bridge
# Time: 2026-07-16
# Author: AxeXie
"""Skill execution bridge from NATS to the ROSClaw Edge Runtime.

The bridge subscribes to ``opengeobot.dev.edge.skill.execute.{robot_id}``,
dispatches the requested skill through ROSClaw's SkillExecutor (when the
ROSClaw package is importable), and replies with a ``SkillExecutionResponse``.

When ROSClaw is not installed the bridge enters a **degraded fallback mode**:
it still responds to every request so the NATS request/reply pipeline does not
hang, but it never reports success for motion skills. Only ``emergency_stop``
is honoured in fallback because it is a local, latching safety action that
must not depend on the cloud or the ROSClaw runtime.

The bridge never calls ``/cmd_vel``, joints, motors, raw UDP, or vendor SDKs
directly. All physical actions are dispatched through the registered, versioned
Skill/Capability path inside ROSClaw.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from loguru import logger
from nats.aio.msg import Msg
from pydantic import BaseModel, Field

from .config import BridgeConfig
from .nats_client import NatsBridge


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class SkillExecutionRequest(BaseModel):
    """Edge -> bridge skill execution request."""

    request_id: str
    trace_id: str
    robot_id: str
    skill_id: str
    params: dict[str, Any] = Field(default_factory=dict)
    requested_at: str = ""

    model_config = {"extra": "ignore"}


class SkillExecutionResponse(BaseModel):
    """Bridge -> edge skill execution response."""

    request_id: str
    trace_id: str
    skill_id: str
    success: bool
    output: dict[str, Any] = Field(default_factory=dict)
    error: str | None = None
    started_at: str = ""
    completed_at: str = ""


# Statuses that ROSClaw's SkillExecutor returns for successful outcomes.
_SUCCESS_STATUSES = frozenset({"success", "executed", "dispatched"})


class RosclawBridge:
    """NATS-driven bridge dispatching skill execution to the ROSClaw Runtime."""

    def __init__(self, config: BridgeConfig) -> None:
        self._config = config
        self._nats: NatsBridge | None = None
        self._stop_event = asyncio.Event()
        self._ready_file = Path(self._config.ready_file_path)

        # ROSClaw runtime components (populated by _init_rosclaw).
        self._rosclaw_available = False
        self._event_bus: Any = None
        self._skill_registry: Any = None
        self._skill_executor: Any = None
        # ROSClaw classes cached during init so emergency_stop does not re-import.
        self._Event_cls: Any = None
        self._EventPriority_cls: Any = None

        # Local Safety Gateway latch (independent of cloud/network).
        self._safety_latched = False
        self._active_executions = 0

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    @property
    def rosclaw_available(self) -> bool:
        return self._rosclaw_available

    @property
    def safety_latched(self) -> bool:
        return self._safety_latched

    async def start(self, nats: NatsBridge) -> None:
        """Connect NATS, initialize ROSClaw, and subscribe to the skill subject."""
        self._nats = nats
        self._mark_not_ready()
        nats.on_disconnect = self._handle_nats_disconnect
        nats.on_reconnect = self._handle_nats_reconnect
        await nats.connect()
        self._init_rosclaw()
        await nats.subscribe(
            self._config.skill_execute_subject,
            handler=self._handle_request,
        )
        self._mark_ready()
        mode = "rosclaw" if self._rosclaw_available else "fallback"
        logger.bind(
            robot_id=self._config.robot_id,
            subject=self._config.skill_execute_subject,
            mode=mode,
        ).info("ROSClaw bridge started and subscribed to skill execute channel")

    async def stop(self) -> None:
        """Signal shutdown and drain the NATS connection."""
        self._stop_event.set()
        self._mark_not_ready()
        if self._nats is not None:
            await self._nats.drain_and_close()
            self._nats = None

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()

    async def _handle_nats_disconnect(self, _exc: Exception | None) -> None:
        """Reflect a lost NATS connection in the readiness signal."""
        self._mark_not_ready()

    async def _handle_nats_reconnect(self) -> None:
        """Restore readiness once the NATS client reconnects."""
        self._mark_ready()

    # ------------------------------------------------------------------
    # ROSClaw initialization
    # ------------------------------------------------------------------

    def _init_rosclaw(self) -> None:
        """Attempt to import and initialize the ROSClaw runtime.

        On any import or initialization failure the bridge silently falls back
        to degraded mode. The fallback never claims success for motion skills.
        """
        try:
            # Import ROSClaw core components. These are the real contracts;
            # we never guess the API.
            from rosclaw.core.event_bus import EventBus, Event, EventPriority
            from rosclaw.skill_manager.executor import SkillExecutor
            from rosclaw.skill_manager.registry import SkillEntry, SkillRegistry
        except ImportError as exc:
            logger.bind(error=str(exc)).warning(
                "ROSClaw package not available; bridge running in degraded fallback mode"
            )
            self._rosclaw_available = False
            return

        try:
            self._event_bus = EventBus()
            self._skill_registry = SkillRegistry(event_bus=self._event_bus)
            self._skill_registry.initialize()
            self._skill_executor = SkillExecutor(
                event_bus=self._event_bus,
                registry=self._skill_registry,
            )
            self._skill_executor.initialize()
            self._register_bridge_skills(SkillEntry)
            # Cache the classes so emergency_stop can publish without re-importing.
            self._Event_cls = Event
            self._EventPriority_cls = EventPriority
            self._rosclaw_available = True
            logger.info("ROSClaw runtime initialized (EventBus + SkillRegistry + SkillExecutor)")
        except Exception as exc:  # noqa: BLE001 - any init failure means fallback
            logger.bind(error=str(exc)).exception(
                "ROSClaw runtime initialization failed; falling back to degraded mode"
            )
            self._rosclaw_available = False
            self._event_bus = None
            self._skill_registry = None
            self._skill_executor = None
            self._Event_cls = None
            self._EventPriority_cls = None

    def _mark_ready(self) -> None:
        """Write a readiness marker once the bridge is serving requests."""
        try:
            self._ready_file.parent.mkdir(parents=True, exist_ok=True)
            self._ready_file.write_text(
                f"{self._config.skill_execute_subject}\n",
                encoding="utf-8",
            )
        except OSError as exc:
            logger.bind(
                error=str(exc),
                ready_file=str(self._ready_file),
            ).warning("Failed to write rosclaw-bridge readiness marker")

    def _mark_not_ready(self) -> None:
        """Remove the readiness marker when the bridge cannot serve requests."""
        try:
            self._ready_file.unlink(missing_ok=True)
        except OSError as exc:
            logger.bind(
                error=str(exc),
                ready_file=str(self._ready_file),
            ).warning("Failed to remove rosclaw-bridge readiness marker")

    def _register_bridge_skills(self, skill_entry_cls: Any) -> None:
        """Register the platform skills that are executable through ROSClaw."""
        assert self._skill_registry is not None

        self._skill_registry.register(
            skill_entry_cls(
                name="move_forward",
                description="Move the robot forward via the ROSClaw runtime",
                skill_type="programmed",
                parameters={"distance": 1.0, "speed": 0.3},
                handler=self._handle_move_forward_skill,
                metadata={
                    "source": "opengeobot_rosclaw_bridge",
                    "runtime_handler": "navigate_to",
                },
            )
        )
        logger.bind(skills=self._skill_registry.list_skills()).info(
            "Registered ROSClaw bridge skills"
        )

    def _handle_move_forward_skill(self, params: dict[str, Any]) -> dict[str, Any]:
        """Translate ``move_forward`` into ROSClaw's built-in navigation handler."""
        try:
            from rosclaw.runtime.plugin import get_runtime_plugin
        except ImportError as exc:
            return {
                "status": "error",
                "error": f"ROSClaw runtime plugin unavailable: {exc}",
            }

        runtime_handler = get_runtime_plugin().get_handler("navigate_to")
        if runtime_handler is None:
            return {
                "status": "error",
                "error": "ROSClaw runtime handler navigate_to is not registered",
            }

        distance = float(params.get("distance", 1.0))
        speed = float(params.get("speed", 0.3))
        duration = params.get("duration")
        translated_params: dict[str, Any] = {
            "target": f"forward:{distance:.2f}m",
            "distance": distance,
            "speed": speed,
        }
        if duration is not None:
            translated_params["duration"] = duration

        result = runtime_handler(translated_params)
        if isinstance(result, dict):
            response = dict(result)
            response.setdefault("skill", "move_forward")
            response.setdefault("translated_params", translated_params)
            return response
        return {
            "status": "success",
            "skill": "move_forward",
            "translated_params": translated_params,
            "handler_result": result,
        }

    # ------------------------------------------------------------------
    # Request handling
    # ------------------------------------------------------------------

    async def _handle_request(self, msg: Msg) -> None:
        try:
            request = SkillExecutionRequest.model_validate_json(msg.data)
        except ValueError as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed skill request")
            await self._reply_error(
                msg, request_id="", trace_id="", skill_id="", error=str(exc)
            )
            return

        log = logger.bind(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
        )
        log.info("Processing skill request")

        started_at = _now_iso()

        # Refuse all motion while safety is latched (except emergency_stop itself).
        if self._safety_latched and request.skill_id != "emergency_stop":
            response = SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error="Safety latch is engaged; only emergency_stop is accepted",
                started_at=started_at,
                completed_at=_now_iso(),
            )
            await self._respond(msg, response)
            return

        self._active_executions += 1
        try:
            result = await self._execute_skill(request, log)
        except Exception as exc:  # noqa: BLE001 - a crash must not kill the bridge
            log.exception("Skill execution raised unexpectedly")
            result = SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error=f"Skill execution crashed: {exc}",
                started_at=started_at,
                completed_at=_now_iso(),
            )
        finally:
            self._active_executions -= 1

        await self._respond(msg, result)

    async def _execute_skill(
        self, request: SkillExecutionRequest, log: Any
    ) -> SkillExecutionResponse:
        """Dispatch a skill request to ROSClaw or the fallback handler."""
        started_at = _now_iso()

        if request.skill_id == "emergency_stop":
            return await self._execute_emergency_stop(request, started_at, log)

        if self._rosclaw_available:
            return await self._execute_via_rosclaw(request, started_at, log)

        return self._execute_fallback(request, started_at, log)

    # ------------------------------------------------------------------
    # emergency_stop (special: always handled, latches locally)
    # ------------------------------------------------------------------

    async def _execute_emergency_stop(
        self, request: SkillExecutionRequest, started_at: str, log: Any
    ) -> SkillExecutionResponse:
        """Trigger ROSClaw's emergency stop path and latch locally.

        The local latch is independent of ROSClaw or the network. Even if the
        EventBus publish fails, the latch is set so subsequent motion requests
        are refused.
        """
        reason = request.params.get("reason", "edge_skill_request")

        if self._rosclaw_available and self._event_bus is not None:
            try:
                Event = self._Event_cls
                EventPriority = self._EventPriority_cls
                self._event_bus.publish(
                    Event(
                        topic="robot.emergency_stop",
                        payload={
                            "reason": reason,
                            "request_id": request.request_id,
                            "trace_id": request.trace_id,
                        },
                        source="rosclaw_bridge",
                        priority=EventPriority.CRITICAL,
                    )
                )
                log.warning("emergency_stop dispatched via ROSClaw EventBus")
            except Exception as exc:  # noqa: BLE001
                log.bind(error=str(exc)).warning(
                    "ROSClaw EventBus publish failed; latching locally only"
                )
        else:
            log.warning("emergency_stop executed (fallback mode); motion latched")

        # Local, latching emergency stop - never depends on cloud or network.
        self._safety_latched = True

        return SkillExecutionResponse(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
            success=True,
            output={
                "stopped": True,
                "reason": reason,
                "stopped_missions": self._active_executions,
                "mode": "rosclaw" if self._rosclaw_available else "fallback",
            },
            started_at=started_at,
            completed_at=_now_iso(),
        )

    # ------------------------------------------------------------------
    # ROSClaw execution path
    # ------------------------------------------------------------------

    async def _execute_via_rosclaw(
        self, request: SkillExecutionRequest, started_at: str, log: Any
    ) -> SkillExecutionResponse:
        """Execute a skill through ROSClaw's SkillExecutor."""
        assert self._skill_executor is not None
        assert self._skill_registry is not None

        # Check if the skill is registered before calling execute().
        skill_entry = self._skill_registry.get(request.skill_id)
        if skill_entry is None:
            return SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error=f"Unknown skill_id {request.skill_id}",
                started_at=started_at,
                completed_at=_now_iso(),
            )

        # SkillExecutor.execute() is synchronous; run it off the event loop.
        try:
            raw_result = await asyncio.to_thread(
                self._skill_executor.execute,
                request.skill_id,
                request.params,
            )
        except Exception as exc:  # noqa: BLE001
            log.bind(error=str(exc)).exception("ROSClaw SkillExecutor.execute raised")
            return SkillExecutionResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                skill_id=request.skill_id,
                success=False,
                error=f"SkillExecutor error: {exc}",
                started_at=started_at,
                completed_at=_now_iso(),
            )

        status = str(raw_result.get("status", "error"))
        success = status in _SUCCESS_STATUSES

        output: dict[str, Any] = {
            "status": status,
            "skill": request.skill_id,
        }
        # Forward useful fields from the ROSClaw result without leaking internals.
        for key in ("handler_result", "duration_sec", "body_check", "body_sense_check"):
            value = raw_result.get(key)
            if value is not None:
                output[key] = value

        error_msg: str | None = None
        if not success:
            error_msg = str(
                raw_result.get("error")
                or raw_result.get("message")
                or f"Skill execution returned status '{status}'"
            )

        log.bind(status=status, success=success).info("Skill execution completed via ROSClaw")

        return SkillExecutionResponse(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
            success=success,
            output=output,
            error=error_msg,
            started_at=started_at,
            completed_at=_now_iso(),
        )

    # ------------------------------------------------------------------
    # Fallback execution path (ROSClaw not installed)
    # ------------------------------------------------------------------

    def _execute_fallback(
        self, request: SkillExecutionRequest, started_at: str, log: Any
    ) -> SkillExecutionResponse:
        """Degraded response when ROSClaw is not available.

        The fallback never reports success for motion skills. It returns a
        structured failure so the edge pipeline can retry or alert an operator.
        """
        log.warning("Skill request handled in fallback mode (ROSClaw not available)")

        return SkillExecutionResponse(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
            success=False,
            error=(
                f"ROSClaw runtime not available; cannot execute skill '{request.skill_id}'. "
                "Install the rosclaw package or switch to sim-adapter."
            ),
            output={"mode": "fallback", "rosclaw_available": False},
            started_at=started_at,
            completed_at=_now_iso(),
        )

    # ------------------------------------------------------------------
    # NATS reply helpers
    # ------------------------------------------------------------------

    async def _respond(self, msg: Msg, response: SkillExecutionResponse) -> None:
        if msg.reply is None or self._nats is None:
            logger.bind(request_id=response.request_id).warning(
                "No reply subject available; cannot return skill result"
            )
            return
        await self._nats.publish(msg.reply, response.model_dump_json().encode("utf-8"))

    async def _reply_error(
        self,
        msg: Msg,
        *,
        request_id: str,
        trace_id: str,
        skill_id: str,
        error: str,
    ) -> None:
        response = SkillExecutionResponse(
            request_id=request_id,
            trace_id=trace_id,
            skill_id=skill_id,
            success=False,
            error=error,
            started_at=_now_iso(),
            completed_at=_now_iso(),
        )
        await self._respond(msg, response)
