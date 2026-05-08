"""
功能：定义 OpenGEO Bot MVP 领域模型
时间：2026-05-08 13:36:00
作者：AxeXie
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ProjectType(str, Enum):
    website = "website"
    repository = "repository"


class RunType(str, Enum):
    baseline = "baseline"
    after = "after"
    scheduled = "scheduled"
    on_demand = "on-demand"


class RunStatus(str, Enum):
    pending = "pending"
    running = "running"
    partial_success = "partial_success"
    success = "success"
    failed = "failed"


class Sentiment(str, Enum):
    positive = "positive"
    neutral = "neutral"
    negative = "negative"


class ProjectCreate(BaseModel):
    project_name: str
    project_type: ProjectType
    source_url: str
    brand_name: str
    aliases: List[str] = Field(default_factory=list)
    language: str = "zh-CN"
    region: str = "global"
    competitors: List[str] = Field(default_factory=list)


class Project(BaseModel):
    project_id: str
    project_name: str
    project_type: ProjectType
    source_url: str
    brand_name: str
    aliases: List[str]
    language: str
    region: str
    competitors: List[str]
    created_at: datetime


class PromptImportItem(BaseModel):
    content: str
    language: str = "zh-CN"
    region: str = "global"
    topic: str = "general"
    stage: str = "consideration"
    priority: int = 3


class Prompt(BaseModel):
    prompt_id: str
    project_id: str
    content: str
    language: str
    region: str
    topic: str
    stage: str
    priority: int
    enabled: bool = True
    version: int = 1


class EngineResult(BaseModel):
    engine: str
    raw_answer: str
    citations: List[str]
    mention: bool
    position: int
    sentiment: Sentiment
    duration_ms: int
    status: str
    failure_reason: Optional[str] = None


class PromptRunResult(BaseModel):
    prompt_id: str
    prompt_content: str
    results: List[EngineResult]


class Metrics(BaseModel):
    mention_rate: float = 0.0
    citation_rate: float = 0.0
    average_position: float = 0.0
    sentiment_score: float = 0.0
    share_of_voice: float = 0.0
    citation_quality: float = 0.0


class Run(BaseModel):
    run_id: str
    project_id: str
    run_type: RunType
    status: RunStatus
    prompt_count: int
    engines: List[str]
    started_at: datetime
    finished_at: Optional[datetime] = None
    prompt_results: List[PromptRunResult] = Field(default_factory=list)
    metrics: Metrics = Field(default_factory=Metrics)
    parser_rule_version: str = "1.0.0"


class Insight(BaseModel):
    insight_id: str
    project_id: str
    title: str
    description: str
    priority_score: float
    affected_prompt_ids: List[str]
    evidence_run_id: str


class Playbook(BaseModel):
    playbook_id: str
    project_id: str
    insight_id: str
    markdown_draft: str
    estimated_impact: Dict[str, float]
    risk_level: str
    created_at: datetime


class VerificationReport(BaseModel):
    report_id: str
    project_id: str
    baseline_run_id: str
    after_run_id: str
    metric_deltas: Dict[str, float]
    summary: str
    created_at: datetime


class MonitorReport(BaseModel):
    report_id: str
    project_id: str
    run_id: str
    alerts: List[str]
    summary: str
    created_at: datetime


class StrategyMemory(BaseModel):
    memory_id: str
    project_id: str
    playbook_id: str
    impact_metrics: Dict[str, float]
    success: bool
    created_at: datetime


class ProjectConfig(BaseModel):
    project_id: str
    config: Dict[str, Any] = Field(default_factory=dict)
    updated_at: Optional[datetime] = None


class AuditLog(BaseModel):
    auditId: str
    traceId: str
    spanId: Optional[str] = None
    projectId: str
    runId: Optional[str] = None
    operator: str
    module: str
    event: str
    level: str
    timestamp: datetime
    message: str
    promptId: Optional[str] = None
    engine: Optional[str] = None
    region: Optional[str] = None
    language: Optional[str] = None
    durationMs: Optional[int] = None
    errorCode: Optional[str] = None
    retryCount: Optional[int] = None
    cost: Optional[float] = None
    attributes: Dict[str, Any] = Field(default_factory=dict)
