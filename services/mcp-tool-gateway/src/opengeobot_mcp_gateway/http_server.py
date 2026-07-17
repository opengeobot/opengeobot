from __future__ import annotations

import asyncio
import json
import uuid
from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import Response, StreamingResponse
from loguru import logger

from .config import GatewayConfig
from .registry import ToolRegistry
from .router import ToolInvocation, ToolRouter


@dataclass
class _Session:
    session_id: str
    queue: asyncio.Queue[str]


def _sse_event(event: str, data: str) -> str:
    return f"event: {event}\ndata: {data}\n\n"


class McpSseServer:
    def __init__(self, config: GatewayConfig, registry: ToolRegistry, router: ToolRouter) -> None:
        self._config = config
        self._registry = registry
        self._router = router
        self._sessions: dict[str, _Session] = {}
        self._lock = asyncio.Lock()

    def build_app(self) -> FastAPI:
        app = FastAPI()

        @app.get("/sse")
        async def sse(request: Request, origin: str | None = Header(default=None)) -> StreamingResponse:
            self._validate_origin(origin)
            self._validate_auth(request)
            session_id = uuid.uuid4().hex
            session = _Session(session_id=session_id, queue=asyncio.Queue(maxsize=100))
            async with self._lock:
                self._sessions[session_id] = session

            endpoint = f"/messages?sessionId={session_id}"
            await session.queue.put(_sse_event("endpoint", endpoint))

            async def stream() -> AsyncIterator[bytes]:
                try:
                    while True:
                        if await request.is_disconnected():
                            break
                        try:
                            event = await asyncio.wait_for(session.queue.get(), timeout=15.0)
                        except asyncio.TimeoutError:
                            yield b": keep-alive\n\n"
                            continue
                        yield event.encode("utf-8")
                finally:
                    async with self._lock:
                        self._sessions.pop(session_id, None)

            return StreamingResponse(
                stream(),
                media_type="text/event-stream",
                headers={
                    "Cache-Control": "no-cache",
                    "Connection": "keep-alive",
                },
            )

        @app.post("/messages")
        async def messages(
            request: Request,
            sessionId: str,
            origin: str | None = Header(default=None),
        ) -> Response:
            self._validate_origin(origin)
            self._validate_auth(request)
            async with self._lock:
                session = self._sessions.get(sessionId)
            if session is None:
                raise HTTPException(status_code=404, detail="Unknown session")

            try:
                payload = await request.json()
            except Exception as exc:  # noqa: BLE001
                raise HTTPException(status_code=400, detail=str(exc)) from exc

            responses = await self._handle_jsonrpc(payload)
            for resp in responses:
                await session.queue.put(_sse_event("message", json.dumps(resp, ensure_ascii=False)))
            return Response(status_code=202)

        return app

    def _validate_origin(self, origin: str | None) -> None:
        if origin is None or origin.strip() == "":
            return
        allow = self._config.allowed_origins
        if not allow:
            raise HTTPException(status_code=403, detail="Origin not allowed")
        if origin not in allow:
            raise HTTPException(status_code=403, detail="Origin not allowed")

    def _validate_auth(self, request: Request) -> None:
        token = self._config.http_auth_token
        if not token:
            return
        header = request.headers.get("authorization", "")
        if header != f"Bearer {token}":
            raise HTTPException(status_code=401, detail="Unauthorized")

    async def _handle_jsonrpc(self, message: dict[str, Any]) -> list[dict[str, Any]]:
        method = str(message.get("method", "")).strip()
        msg_id = message.get("id")
        if not method:
            return [self._jsonrpc_error(msg_id, -32600, "Invalid Request")]

        if method == "initialize":
            return [self._jsonrpc_result(msg_id, self._initialize_result())]

        if method == "tools/list":
            tools = await self._list_tools()
            return [self._jsonrpc_result(msg_id, {"tools": tools})]

        if method == "tools/call":
            params = message.get("params") or {}
            if not isinstance(params, dict):
                return [self._jsonrpc_error(msg_id, -32602, "Invalid params")]
            name = str(params.get("name", "")).strip()
            args = params.get("arguments") or {}
            if not name:
                return [self._jsonrpc_error(msg_id, -32602, "Missing tool name")]
            if not isinstance(args, dict):
                return [self._jsonrpc_error(msg_id, -32602, "Invalid arguments")]
            result = await self._call_tool(name, args)
            return [self._jsonrpc_result(msg_id, result)]

        return [self._jsonrpc_error(msg_id, -32601, f"Method not found: {method}")]

    def _initialize_result(self) -> dict[str, Any]:
        return {
            "protocolVersion": "2024-11-05",
            "capabilities": {"tools": {"listChanged": False}},
            "serverInfo": {"name": "opengeobot-mcp-tool-gateway", "version": "0.1.0"},
        }

    async def _list_tools(self) -> list[dict[str, Any]]:
        entries = await self._registry.list_tools()
        tools: list[dict[str, Any]] = []
        for entry in entries:
            try:
                full = await self._registry.lookup(entry.tool_name)
            except Exception as exc:  # noqa: BLE001
                logger.bind(tool_name=entry.tool_name, error=str(exc)).warning(
                    "Failed to resolve tool definition"
                )
                continue
            reg = full.stable or full.canary
            if reg is None:
                continue
            definition = reg.definition
            tools.append(
                {
                    "name": definition.tool_name,
                    "description": definition.description,
                    "inputSchema": definition.input_schema or {"type": "object"},
                }
            )
        return tools

    async def _call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        invocation = ToolInvocation(tool_name=name, input=arguments)
        result = await self._router.invoke(invocation)
        payload = result.output if result.success else {"error": result.error or "unknown"}
        return {
            "content": [{"type": "text", "text": json.dumps(payload, ensure_ascii=False)}],
            "isError": not result.success,
        }

    @staticmethod
    def _jsonrpc_result(msg_id: Any, result: Any) -> dict[str, Any]:
        return {"jsonrpc": "2.0", "id": msg_id, "result": result}

    @staticmethod
    def _jsonrpc_error(msg_id: Any, code: int, message: str) -> dict[str, Any]:
        return {"jsonrpc": "2.0", "id": msg_id, "error": {"code": code, "message": message}}

