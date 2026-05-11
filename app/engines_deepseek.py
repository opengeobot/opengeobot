"""
功能：DeepSeek AI 引擎适配器
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

import time
from typing import Any, Dict, List, Optional

import httpx

from app.engines import (
    EngineAdapter,
    EngineContext,
    EngineRequest,
    RawEngineResponse,
)


class DeepSeekEngineAdapter:
    """DeepSeek API 引擎适配器"""
    
    def __init__(
        self,
        engine_id: str,
        api_key: str,
        base_url: str = "https://api.deepseek.com/v1",
        model: str = "deepseek-chat",
    ) -> None:
        self.engine_id = engine_id
        self.api_key = api_key
        self.base_url = base_url.rstrip("/")
        self.model = model
    
    def run(
        self,
        request: EngineRequest,
        context: EngineContext,
        *,
        timeout_ms: int,
    ) -> RawEngineResponse:
        """执行引擎调用"""
        started = time.perf_counter()
        
        try:
            # 构建系统提示词
            system_prompt = self._build_system_prompt(context, request)
            
            # 构建消息
            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": request.prompt},
            ]
            
            # 调用 DeepSeek API
            response = self._call_api(messages, timeout_ms)
            
            # 解析响应
            raw_answer = response.get("choices", [{}])[0].get("message", {}).get("content", "")
            
            # 提取引用 (从响应中提取URL)
            citations = self._extract_citations(raw_answer, context.source_url)
            
            duration_ms = max(1, int((time.perf_counter() - started) * 1000))
            
            return RawEngineResponse(
                engine=self.engine_id,
                raw_answer=raw_answer,
                citations=citations,
                duration_ms=duration_ms,
                status="success",
                response_metadata={
                    "adapter": "deepseek",
                    "model": self.model,
                    "usage": response.get("usage", {}),
                },
            )
        
        except httpx.TimeoutException:
            duration_ms = max(1, int((time.perf_counter() - started) * 1000))
            return RawEngineResponse(
                engine=self.engine_id,
                raw_answer="",
                citations=[],
                duration_ms=duration_ms,
                status="failed",
                failure_reason="timeout",
                response_metadata={"adapter": "deepseek"},
            )
        
        except Exception as exc:
            duration_ms = max(1, int((time.perf_counter() - started) * 1000))
            return RawEngineResponse(
                engine=self.engine_id,
                raw_answer="",
                citations=[],
                duration_ms=duration_ms,
                status="failed",
                failure_reason=str(exc),
                response_metadata={"adapter": "deepseek"},
            )
    
    async def run_async(
        self,
        request: EngineRequest,
        context: EngineContext,
        *,
        timeout_ms: int,
    ) -> RawEngineResponse:
        """异步执行引擎调用"""
        started = time.perf_counter()
        
        try:
            # 构建系统提示词
            system_prompt = self._build_system_prompt(context, request)
            
            # 构建消息
            messages = [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": request.prompt},
            ]
            
            # 异步调用 DeepSeek API
            response = await self._call_api_async(messages, timeout_ms)
            
            # 解析响应
            raw_answer = response.get("choices", [{}])[0].get("message", {}).get("content", "")
            
            # 提取引用
            citations = self._extract_citations(raw_answer, context.source_url)
            
            duration_ms = max(1, int((time.perf_counter() - started) * 1000))
            
            return RawEngineResponse(
                engine=self.engine_id,
                raw_answer=raw_answer,
                citations=citations,
                duration_ms=duration_ms,
                status="success",
                response_metadata={
                    "adapter": "deepseek",
                    "model": self.model,
                    "usage": response.get("usage", {}),
                },
            )
        
        except httpx.TimeoutException:
            duration_ms = max(1, int((time.perf_counter() - started) * 1000))
            return RawEngineResponse(
                engine=self.engine_id,
                raw_answer="",
                citations=[],
                duration_ms=duration_ms,
                status="failed",
                failure_reason="timeout",
                response_metadata={"adapter": "deepseek"},
            )
        
        except Exception as exc:
            duration_ms = max(1, int((time.perf_counter() - started) * 1000))
            return RawEngineResponse(
                engine=self.engine_id,
                raw_answer="",
                citations=[],
                duration_ms=duration_ms,
                status="failed",
                failure_reason=str(exc),
                response_metadata={"adapter": "deepseek"},
            )
    
    def _build_system_prompt(self, context: EngineContext, request: EngineRequest) -> str:
        """构建系统提示词"""
        parts = [
            f"你是一个专业的搜索引擎优化(GEO)分析助手。",
            f"品牌名称: {context.brand_name}",
        ]
        
        if context.aliases:
            parts.append(f"品牌别名: {', '.join(context.aliases)}")
        
        if context.competitors:
            parts.append(f"竞争对手: {', '.join(context.competitors)}")
        
        parts.append(
            "请根据用户的问题提供准确、相关的回答。"
            "如果可能，请提供官方或权威的引用来源。"
            "回答语言应该与用户问题一致。"
        )
        
        if request.retrieval:
            parts.append(
                "如果需要，可以引用网络搜索的结果。"
            )
        
        return "\n".join(parts)
    
    def _call_api(
        self,
        messages: List[Dict[str, str]],
        timeout_ms: int,
    ) -> Dict[str, Any]:
        """调用 DeepSeek API"""
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        
        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": 0.2,
            "max_tokens": 2048,
        }
        
        with httpx.Client(timeout=timeout_ms / 1000.0) as client:
            response = client.post(
                f"{self.base_url}/chat/completions",
                headers=headers,
                json=payload,
            )
            response.raise_for_status()
            return response.json()
    
    async def _call_api_async(
        self,
        messages: List[Dict[str, str]],
        timeout_ms: int,
    ) -> Dict[str, Any]:
        """异步调用 DeepSeek API"""
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        
        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": 0.2,
            "max_tokens": 2048,
        }
        
        async with httpx.AsyncClient(timeout=timeout_ms / 1000.0) as client:
            response = await client.post(
                f"{self.base_url}/chat/completions",
                headers=headers,
                json=payload,
            )
            response.raise_for_status()
            return response.json()
    
    def _extract_citations(self, text: str, source_url: str) -> List[str]:
        """从响应文本中提取引用URL"""
        import re
        
        # 匹配URL
        urls = re.findall(r'https?://[^\s\)>]+', text)
        
        # 去重
        unique_urls = list(dict.fromkeys(urls))
        
        # 如果source_url存在且未在响应中提及，添加到引用列表
        if source_url and source_url not in unique_urls:
            unique_urls.append(source_url)
        
        return unique_urls
