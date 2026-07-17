# Function: MCP tool gateway asyncio entry point
# Time: 2026-07-06
# Author: AxeXie
"""MCP tool gateway runtime entry point.

Wires configuration, NATS connection, tool registry, router and handler. The
gateway subscribes to tool registration, invocation, list and unregister
subjects.

Run directly:
    python -m opengeobot_mcp_gateway.main
or:
    python src/opengeobot_mcp_gateway/main.py
"""

from __future__ import annotations

import asyncio
import signal
import sys

from loguru import logger
import uvicorn

from .config import GatewayConfig
from .handler import ToolGatewayHandler
from .http_server import McpSseServer
from .nats_client import NatsBridge
from .registry import ToolRegistry
from .router import ToolRouter


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


class McpToolGateway:
    """Top-level orchestrator for the MCP tool gateway runtime."""

    def __init__(self, config: GatewayConfig) -> None:
        self._config = config
        self._nats = NatsBridge(config)
        self._registry = ToolRegistry()
        self._router = ToolRouter(config, self._nats, self._registry)
        self._handler = ToolGatewayHandler(
            config, self._nats, self._registry, self._router
        )
        self._stop_event = asyncio.Event()
        self._http_server: uvicorn.Server | None = None
        self._http_task: asyncio.Task[None] | None = None

    @property
    def registry(self) -> ToolRegistry:
        return self._registry

    @property
    def router(self) -> ToolRouter:
        return self._router

    async def start(self) -> None:
        await self._nats.connect()
        await self._nats.ensure_stream()
        await self._nats.subscribe(
            self._config.register_subject, self._handler.handle_register
        )
        # JetStream durable consumer for tool invocations so messages survive
        # consumer disconnect/reconnect scenarios.
        await self._nats.subscribe_js(
            self._config.invoke_subject,
            self._handler.handle_invoke,
            durable=self._config.js_durable_consumer,
        )
        await self._nats.subscribe(
            self._config.list_subject, self._handler.handle_list
        )
        await self._nats.subscribe(
            self._config.unregister_subject, self._handler.handle_unregister
        )
        self._start_http_server()
        logger.info("MCP tool gateway started - subscribed to tool subjects")

    def _start_http_server(self) -> None:
        app = McpSseServer(self._config, self._registry, self._router).build_app()
        config = uvicorn.Config(
            app,
            host=self._config.http_host,
            port=self._config.http_port,
            log_level="warning",
            access_log=False,
        )
        server = uvicorn.Server(config)
        self._http_server = server
        self._http_task = asyncio.create_task(server.serve())

    async def stop(self) -> None:
        logger.info("MCP tool gateway stopping...")
        if self._http_server is not None:
            self._http_server.should_exit = True
        if self._http_task is not None:
            try:
                await asyncio.wait_for(self._http_task, timeout=5.0)
            except TimeoutError:
                pass
        await self._nats.drain_and_close()
        self._stop_event.set()

    async def wait_for_shutdown(self) -> None:
        await self._stop_event.wait()


async def _run() -> None:
    config = GatewayConfig.from_env()
    _configure_logging(config.log_level)
    gateway = McpToolGateway(config)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda: asyncio.create_task(gateway.stop()))
        except NotImplementedError:
            # Signals are not available on all platforms (e.g. Windows Proactor).
            pass

    await gateway.start()
    await gateway.wait_for_shutdown()


def main() -> None:
    try:
        asyncio.run(_run())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
