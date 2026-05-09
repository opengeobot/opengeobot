"""
功能：回答解析与信号提取（提及/引用/位置/情感/风险标签）
时间：2026-05-08 16:05:00
作者：AxeXie
"""

from __future__ import annotations

import re
from typing import Any, Dict, List, Tuple

from app.models import EngineResult, Prompt, Project, Sentiment

PARSER_RULE_VERSION = "1.0.0"

_URL_RE = re.compile(r"(https?://[^\s\]\)】}]+)", re.IGNORECASE)
_RANK_LINE_RE = re.compile(r"^\s*(\d+)[\.\)\、]\s*(.+?)\s*$")


def build_engine_result(
    *,
    project: Project,
    prompt: Prompt,
    engine: str,
    raw_answer: str,
    citations: List[str],
    duration_ms: int,
    status: str,
    failure_reason: str | None,
    response_metadata: Dict[str, Any],
) -> EngineResult:
    normalized_answer = raw_answer or ""
    merged_citations = _merge_citations(citations, normalized_answer)
    mention = _detect_mention(project, normalized_answer)
    position = _detect_position(project, normalized_answer, mention)
    sentiment = _detect_sentiment(normalized_answer)
    risk_tags = _detect_risk_tags(normalized_answer)

    return EngineResult(
        engine=engine,
        raw_answer=normalized_answer,
        citations=merged_citations,
        mention=mention,
        position=position,
        sentiment=sentiment,
        duration_ms=max(0, int(duration_ms)),
        status=status,
        failure_reason=failure_reason,
        risk_tags=risk_tags,
        response_metadata=response_metadata,
    )


def _merge_citations(citations: List[str], raw_answer: str) -> List[str]:
    merged: List[str] = []
    seen = set()
    for item in citations:
        value = (item or "").strip()
        if value and value not in seen:
            merged.append(value)
            seen.add(value)
    for match in _URL_RE.findall(raw_answer or ""):
        value = (match or "").strip().rstrip(".,;")
        if value and value not in seen:
            merged.append(value)
            seen.add(value)
    return merged


def _detect_mention(project: Project, raw_answer: str) -> bool:
    haystack = (raw_answer or "").lower()
    candidates = [project.brand_name, *project.aliases]
    for token in candidates:
        value = (token or "").strip().lower()
        if value and value in haystack:
            return True
    return False


def _detect_position(project: Project, raw_answer: str, mention: bool) -> int:
    if not mention:
        return 999
    ranked = _parse_ranked_lines(raw_answer)
    if ranked:
        candidates = [project.brand_name, *project.aliases]
        for rank, text in ranked:
            lowered = text.lower()
            for token in candidates:
                value = (token or "").strip().lower()
                if value and value in lowered:
                    return max(1, rank)
    return 1


def _parse_ranked_lines(raw_answer: str) -> List[Tuple[int, str]]:
    items: List[Tuple[int, str]] = []
    for line in (raw_answer or "").splitlines():
        matched = _RANK_LINE_RE.match(line)
        if matched:
            try:
                rank = int(matched.group(1))
            except ValueError:
                continue
            text = matched.group(2) or ""
            items.append((rank, text))
    return items


def _detect_sentiment(raw_answer: str) -> Sentiment:
    text = raw_answer or ""
    positive = _count_keywords(
        text,
        [
            "推荐",
            "优秀",
            "稳定",
            "可靠",
            "强大",
            "易用",
            "适合",
            "值得",
            "recommended",
            "great",
            "excellent",
            "reliable",
            "stable",
            "powerful",
        ],
    )
    negative = _count_keywords(
        text,
        [
            "不推荐",
            "差",
            "不稳定",
            "不可靠",
            "问题",
            "风险",
            "过时",
            "deprecated",
            "vulnerable",
            "unsafe",
            "scam",
            "not recommended",
        ],
    )
    if positive > negative and positive > 0:
        return Sentiment.positive
    if negative > positive and negative > 0:
        return Sentiment.negative
    return Sentiment.neutral


def _count_keywords(text: str, keywords: List[str]) -> int:
    lowered = (text or "").lower()
    total = 0
    for kw in keywords:
        token = (kw or "").strip().lower()
        if token and token in lowered:
            total += 1
    return total


def _detect_risk_tags(raw_answer: str) -> List[str]:
    text = (raw_answer or "").lower()
    tags: List[str] = []
    if any(token in text for token in ("过期", "deprecated", "outdated")):
        tags.append("outdated_information")
    if any(token in text for token in ("漏洞", "vulnerable", "cve", "unsafe")):
        tags.append("security_risk")
    if any(token in text for token in ("合规", "license", "licensing", "侵权")):
        tags.append("compliance_risk")
    if any(token in text for token in ("竞品", "替代", "alternative", "instead")):
        tags.append("competitor_alternative")
    return tags

