"""
功能：AI 引擎适配器（最小可运行实现：统一输入输出、限流、重试、缓存）
时间：2026-05-08 16:05:00
作者：AxeXie
"""

from __future__ import annotations

import hashlib
import json
import time
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Protocol, Tuple

from pydantic import BaseModel, Field


class EngineContext(BaseModel):
    project_id: str
    brand_name: str
    aliases: List[str] = Field(default_factory=list)
    competitors: List[str] = Field(default_factory=list)
    source_url: str


class EngineRequest(BaseModel):
    prompt: str
    language: str
    region: str
    retrieval: bool = True
    temperature: float = 0.2
    params: Dict[str, Any] = Field(default_factory=dict)


class RawEngineResponse(BaseModel):
    engine: str
    raw_answer: str
    citations: List[str] = Field(default_factory=list)
    duration_ms: int
    status: str
    failure_reason: Optional[str] = None
    response_metadata: Dict[str, Any] = Field(default_factory=dict)


class EngineAdapter(Protocol):
    engine_id: str

    def run(self, request: EngineRequest, context: EngineContext, *, timeout_ms: int) -> RawEngineResponse: ...


class MockEngineAdapter:
    def __init__(self, engine_id: str) -> None:
        self.engine_id = engine_id

    def run(self, request: EngineRequest, context: EngineContext, *, timeout_ms: int) -> RawEngineResponse:
        started = time.perf_counter()
        seed = _stable_seed(self.engine_id, context.project_id, request.prompt, request.language, request.region)
        score = seed % 1000
        mention = score % 10 != 0
        citation_hit = request.retrieval and score % 7 != 0
        raw_answer = (
            f"{context.brand_name} 在该问题中具有相关性。建议优先参考官方资料与权威第三方评测。"
            if mention
            else "该问题未明确命中目标对象，建议补充更具体的背景与约束条件。"
        )
        citations = [context.source_url] if citation_hit else []
        duration_ms = max(1, int((time.perf_counter() - started) * 1000))
        return RawEngineResponse(
            engine=self.engine_id,
            raw_answer=raw_answer,
            citations=citations,
            duration_ms=duration_ms,
            status="success",
            response_metadata={
                "adapter": "mock",
                "seed": seed,
                "timeoutMs": timeout_ms,
            },
        )


class TemplateEngineAdapter:
    def __init__(self, engine_id: str) -> None:
        self.engine_id = engine_id

    def run(self, request: EngineRequest, context: EngineContext, *, timeout_ms: int) -> RawEngineResponse:
        started = time.perf_counter()
        seed = _stable_seed(self.engine_id, context.project_id, request.prompt, request.language, request.region)
        position = (seed % 3) + 1

        candidates: List[str] = []
        competitors = [item for item in context.competitors if item]
        if competitors:
            candidates.extend(competitors[:5])
        if context.brand_name:
            if position <= len(candidates):
                candidates.insert(position - 1, context.brand_name)
            else:
                candidates.append(context.brand_name)

        if not candidates:
            candidates = [context.brand_name] if context.brand_name else ["目标对象"]

        lines = ["下面给出一个可执行的参考清单：", ""]
        for index, name in enumerate(candidates[:6], start=1):
            if name == context.brand_name:
                lines.append(f"{index}. {name}（建议先看官方文档与快速开始）")
            else:
                lines.append(f"{index}. {name}（可作为对比参考）")

        lines.append("")
        lines.append("选择建议：优先满足功能、生态、可维护性与成本约束；必要时做 PoC 对比。")
        raw_answer = "\n".join(lines).strip()

        citations = [context.source_url] if request.retrieval and context.source_url else []
        duration_ms = max(1, int((time.perf_counter() - started) * 1000))
        return RawEngineResponse(
            engine=self.engine_id,
            raw_answer=raw_answer,
            citations=citations,
            duration_ms=duration_ms,
            status="success",
            response_metadata={
                "adapter": "template",
                "rankedList": True,
                "timeoutMs": timeout_ms,
            },
        )


@dataclass
class _CacheEntry:
    expires_at: float
    value: RawEngineResponse


class _TTLCache:
    def __init__(self) -> None:
        self._data: Dict[str, _CacheEntry] = {}

    def get(self, key: str) -> Optional[RawEngineResponse]:
        entry = self._data.get(key)
        if entry is None:
            return None
        if entry.expires_at <= time.monotonic():
            self._data.pop(key, None)
            return None
        return entry.value

    def set(self, key: str, value: RawEngineResponse, *, ttl_seconds: int) -> None:
        if ttl_seconds <= 0:
            return
        self._data[key] = _CacheEntry(expires_at=time.monotonic() + ttl_seconds, value=value)


class _TokenBucketRateLimiter:
    def __init__(self, rate_per_minute: int) -> None:
        self._capacity = max(1.0, float(rate_per_minute))
        self._tokens = self._capacity
        self._refill_per_second = self._capacity / 60.0
        self._last = time.monotonic()

    def acquire(self) -> None:
        while True:
            now = time.monotonic()
            elapsed = max(0.0, now - self._last)
            if elapsed > 0:
                self._tokens = min(self._capacity, self._tokens + elapsed * self._refill_per_second)
                self._last = now
            if self._tokens >= 1.0:
                self._tokens -= 1.0
                return
            time.sleep(0.05)


class EngineManager:
    def __init__(
        self,
        *,
        adapters: Dict[str, EngineAdapter],
        timeout_ms: int,
        max_retries: int,
        retry_backoff_ms: int,
        rate_limit_per_minute: int,
        cache_ttl_seconds: int,
    ) -> None:
        self._adapters = adapters
        self._timeout_ms = max(1, int(timeout_ms))
        self._max_retries = max(0, int(max_retries))
        self._retry_backoff_ms = max(0, int(retry_backoff_ms))
        self._cache_ttl_seconds = max(0, int(cache_ttl_seconds))
        self._cache = _TTLCache()
        self._rate_limiters: Dict[str, _TokenBucketRateLimiter] = {
            engine_id: _TokenBucketRateLimiter(rate_limit_per_minute) for engine_id in adapters.keys()
        }

    def supported_engines(self) -> List[str]:
        return list(self._adapters.keys())

    def execute(
        self,
        engine_id: str,
        request: EngineRequest,
        context: EngineContext,
    ) -> Tuple[RawEngineResponse, Dict[str, Any]]:
        adapter = self._adapters.get(engine_id)
        if adapter is None:
            response = RawEngineResponse(
                engine=engine_id,
                raw_answer="",
                citations=[],
                duration_ms=0,
                status="failed",
                failure_reason="unsupported_engine",
                response_metadata={},
            )
            return response, {"cacheHit": False, "retryCount": 0}

        cache_key = _cache_key(engine_id, request, context)
        cached = self._cache.get(cache_key)
        if cached is not None:
            return cached, {"cacheHit": True, "retryCount": 0}

        limiter = self._rate_limiters.get(engine_id)
        if limiter is not None:
            limiter.acquire()

        last_exc: Optional[BaseException] = None
        for attempt in range(self._max_retries + 1):
            try:
                result = adapter.run(request, context, timeout_ms=self._timeout_ms)
                if result.status == "success":
                    self._cache.set(cache_key, result, ttl_seconds=self._cache_ttl_seconds)
                return result, {"cacheHit": False, "retryCount": attempt}
            except BaseException as exc:
                last_exc = exc
                if attempt >= self._max_retries:
                    break
                if self._retry_backoff_ms > 0:
                    time.sleep(self._retry_backoff_ms / 1000.0)

        failure_reason = str(last_exc) if last_exc is not None else "unknown_error"
        response = RawEngineResponse(
            engine=engine_id,
            raw_answer="",
            citations=[],
            duration_ms=0,
            status="failed",
            failure_reason=failure_reason,
            response_metadata={},
        )
        return response, {"cacheHit": False, "retryCount": self._max_retries}


def _stable_seed(*parts: str) -> int:
    joined = "\n".join([part or "" for part in parts])
    digest = hashlib.sha256(joined.encode("utf-8")).hexdigest()
    return int(digest[:12], 16)


def _cache_key(engine_id: str, request: EngineRequest, context: EngineContext) -> str:
    payload = {
        "engine": engine_id,
        "projectId": context.project_id,
        "prompt": request.prompt,
        "language": request.language,
        "region": request.region,
        "retrieval": request.retrieval,
        "temperature": request.temperature,
        "params": request.params,
    }
    raw = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()

