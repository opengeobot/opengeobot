# Function: ROS1 protocol adapter asyncio entry point (F-ADAPTER-002)
# Time: 2026-07-05
# Author: AxeXie
"""ROS1 protocol adapter runtime entry point.

The adapter subscribes to ``opengeobot.dev.adapter.translate.{adapter_id}``,
translates the requested platform skill command into the robot-native
protocol message, and replies with a ``TranslateCommandResponse``. It never
publishes ``/cmd_vel`` or calls vendor SDKs directly — all translations are
proposals validated by the edge Safety Gateway.

Run directly:
    python -m opengeobot_ros1.main
or:
    python src/opengeobot_ros1/main.py
"""

from __future__ import annotations

import asyncio
import signal
import sys
from datetime import datetime, timezone
from typing import Any

import nats
from loguru import logger
from nats.aio.client import Client as NatsClient
from nats.aio.msg import Msg
from pydantic import BaseModel, Field

from .adapter import ProtocolAdapter, TranslationError
from .config import Ros1Config
from .custom_adapter import CustomAdapter
from .unitree_adapter import UnitreeAdapter


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


class TranslateRequest(BaseModel):
    """Cloud → adapter command translation request."""

    request_id: str
    trace_id: str
    adapter_id: str
    skill_id: str
    params: dict[str, Any] = Field(default_factory=dict)
    requested_at: str = ""

    model_config = {"extra": "ignore"}


class TranslateResponse(BaseModel):
    """Adapter → cloud command translation response."""

    request_id: str
    trace_id: str
    adapter_id: str
    skill_id: str
    translated_command: dict[str, Any] = Field(default_factory=dict)
    success: bool
    error: str | None = None
    translated_at: str


def _select_protocol_handler(protocol_type: str) -> ProtocolAdapter:
    """Select the protocol handler based on the configured protocol type."""
    upper = protocol_type.upper()
    if upper == "UNITREE":
        return UnitreeAdapter()
    if upper == "CUSTOM":
        return CustomAdapter()
    if upper == "ROS1":
        # ROS1 native uses the same translation shape as custom (JSON command)
        # until the ROS1 Jazzy contract is pinned. This keeps the adapter
        # functional without guessing rclpy APIs.
        return CustomAdapter()
    raise ValueError(f"Unsupported protocol_type '{protocol_type}'")


class Ros1Adapter:
    """NATS-driven ROS1 protocol adapter executing command translations."""

    def __init__(self, config: Ros1Config) -> None:
        self._config = config
        self._nc: NatsClient | None = None
        self._handler: ProtocolAdapter = _select_protocol_handler(config.protocol_type)
        self._stop_event = asyncio.Event()

    @property
    def protocol_type(self) -> str:
        return self._handler.protocol_type

    async def start(self) -> None:
        self._nc = await nats.connect(
            servers=self._config.nats_url,
            name=f"ros1-adapter-{self._config.adapter_id}",
            max_reconnect_attempts=self._config.nats_max_reconnect,
            reconnect_time_wait=self._config.nats_reconnect_wait,
            connect_timeout=self._config.nats_connect_timeout,
            allow_reconnect=True,
        )
        await self._nc.subscribe(
            self._config.translate_subject, cb=self._handle_request
        )
        logger.bind(
            adapter_id=self._config.adapter_id,
            robot_id=self._config.robot_id,
            protocol_type=self.protocol_type,
        ).info("ROS1 adapter started and subscribed to translate channel")

    async def stop(self) -> None:
        self._stop_event.set()
        if self._nc is not None:
            try:
                await self._nc.drain()
            finally:
                self._nc = None

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()

    async def _handle_request(self, msg: Msg) -> None:
        try:
            request = TranslateRequest.model_validate_json(msg.data)
        except ValueError as exc:
            logger.bind(error=str(exc)).warning("Rejected malformed translate request")
            await self._reply_error(
                msg, request_id="", trace_id="", adapter_id="", skill_id="", error=str(exc)
            )
            return

        logger.bind(
            request_id=request.request_id,
            trace_id=request.trace_id,
            skill_id=request.skill_id,
        ).info("Translating skill command")

        try:
            translated = self._handler.translate(request.skill_id, request.params)
            response = TranslateResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                adapter_id=request.adapter_id,
                skill_id=request.skill_id,
                translated_command=translated,
                success=True,
                error=None,
                translated_at=_now_iso(),
            )
        except TranslationError as exc:
            logger.bind(skill=request.skill_id, error=str(exc)).warning(
                "Translation failed"
            )
            response = TranslateResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                adapter_id=request.adapter_id,
                skill_id=request.skill_id,
                translated_command={},
                success=False,
                error=str(exc),
                translated_at=_now_iso(),
            )
        except Exception as exc:  # noqa: BLE001 — a handler crash must not kill the adapter
            logger.bind(skill=request.skill_id, error=str(exc)).exception(
                "Translation raised"
            )
            response = TranslateResponse(
                request_id=request.request_id,
                trace_id=request.trace_id,
                adapter_id=request.adapter_id,
                skill_id=request.skill_id,
                translated_command={},
                success=False,
                error=f"Translation crashed: {exc}",
                translated_at=_now_iso(),
            )

        await self._respond(msg, response)

    async def _respond(self, msg: Msg, response: TranslateResponse) -> None:
        if msg.reply is None or self._nc is None:
            logger.bind(request_id=response.request_id).warning(
                "No reply subject available; cannot return translation result"
            )
            return
        await self._nc.publish(msg.reply, response.model_dump_json().encode("utf-8"))

    async def _reply_error(
        self,
        msg: Msg,
        *,
        request_id: str,
        trace_id: str,
        adapter_id: str,
        skill_id: str,
        error: str,
    ) -> None:
        response = TranslateResponse(
            request_id=request_id,
            trace_id=trace_id,
            adapter_id=adapter_id,
            skill_id=skill_id,
            translated_command={},
            success=False,
            error=error,
            translated_at=_now_iso(),
        )
        await self._respond(msg, response)


def _configure_logging(level: str) -> None:
    logger.remove()
    logger.add(
        sys.stderr,
        level=level,
        format=(
            "<green>{time:YYYY-MM-DD HH:mm:ss.SSS}</green> | "
            "<level>{level: <8}</level> | "
            "<cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - "
            "<level>{message}</level>"
        ),
        backtrace=False,
        diagnose=False,
    )


async def _run() -> None:
    config = Ros1Config.from_env()
    _configure_logging(config.log_level)
    adapter = Ros1Adapter(config)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.create_task(adapter.stop()))
        except NotImplementedError:
            pass

    await adapter.start()
    await adapter.wait_for_shutdown()


def main() -> None:
    try:
        asyncio.run(_run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
