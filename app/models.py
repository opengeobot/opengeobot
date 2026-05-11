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


class AssetType(str, Enum):
    website = "website"
    repository = "repository"


class AssetStatus(str, Enum):
    pending = "pending"
    success = "success"
    failed = "failed"


class Asset(BaseModel):
    asset_id: str
    project_id: str
    asset_type: AssetType
    source_url: str
    title: str = ""
    summary: str = ""
    discovered_urls: List[str] = Field(default_factory=list)
    status: AssetStatus = AssetStatus.pending
    last_crawled_at: Optional[datetime] = None
    content_version: str = "0"
    error_message: Optional[str] = None
    deleted: bool = False
    deleted_at: Optional[datetime] = None


class AssetCreate(BaseModel):
    asset_type: AssetType
    source_url: str


class AssetUpdate(BaseModel):
    source_url: Optional[str] = None
    deleted: Optional[bool] = None


class AssetChange(BaseModel):
    change_id: str
    project_id: str
    asset_id: str
    detected_at: datetime
    previous_version: str
    new_version: str
    summary: str


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
    deleted: bool = False
    deleted_at: Optional[datetime] = None


class ProjectUpdate(BaseModel):
    project_name: Optional[str] = None
    source_url: Optional[str] = None
    brand_name: Optional[str] = None
    aliases: Optional[List[str]] = None
    language: Optional[str] = None
    region: Optional[str] = None
    competitors: Optional[List[str]] = None


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
    canonical_prompt_id: Optional[str] = None
    similarity_group_id: Optional[str] = None


class PromptUpdate(BaseModel):
    content: Optional[str] = None
    language: Optional[str] = None
    region: Optional[str] = None
    topic: Optional[str] = None
    stage: Optional[str] = None
    priority: Optional[int] = None
    enabled: Optional[bool] = None


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
    risk_tags: List[str] = Field(default_factory=list)
    response_metadata: Dict[str, Any] = Field(default_factory=dict)


class PromptRunResult(BaseModel):
    prompt_id: str
    prompt_content: str
    results: List[EngineResult]


class Metrics(BaseModel):
    mention_rate: float = 0.0
    citation_rate: float = 0.0
    official_citation_rate: float = 0.0
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
    asset_versions: Dict[str, str] = Field(default_factory=dict)


class Insight(BaseModel):
    insight_id: str
    project_id: str
    title: str
    description: str
    category: str = "general"
    priority_score: float
    affected_prompt_ids: List[str]
    evidence_run_id: str
    evidence: Dict[str, Any] = Field(default_factory=dict)


class Playbook(BaseModel):
    playbook_id: str
    project_id: str
    insight_id: str
    playbook_type: str = "general"
    markdown_draft: str
    estimated_impact: Dict[str, float]
    risk_level: str
    created_at: datetime
    evidence: Dict[str, Any] = Field(default_factory=dict)


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
    alert_ids: List[str] = Field(default_factory=list)
    summary: str
    created_at: datetime


class AlertStatus(str, Enum):
    open = "open"
    acknowledged = "acknowledged"
    in_progress = "in_progress"
    resolved = "resolved"
    closed = "closed"


class Alert(BaseModel):
    alert_id: str
    project_id: str
    report_id: str
    run_id: str
    alert_type: str
    fingerprint: str
    status: AlertStatus = AlertStatus.open
    assignee: Optional[str] = None
    notes: List[str] = Field(default_factory=list)
    closed_reason: Optional[str] = None
    created_at: datetime
    updated_at: datetime
    last_seen_at: datetime


class AlertUpdate(BaseModel):
    status: Optional[AlertStatus] = None
    assignee: Optional[str] = None
    note: Optional[str] = None
    closed_reason: Optional[str] = None


class StrategyMemory(BaseModel):
    memory_id: str
    project_id: str
    playbook_id: str
    playbook_type: str = "general"
    impact_metrics: Dict[str, float]
    success: bool
    created_at: datetime


class CitationSource(BaseModel):
    domain: str
    example_url: str
    count: int
    prompt_count: int
    last_seen_at: datetime
    is_official: bool
    quality_score: float
    matched_asset_ids: List[str] = Field(default_factory=list)


class GitHubPRDraftStatus(str, Enum):
    pending_approval = "pending_approval"
    approved = "approved"
    rejected = "rejected"


class ApprovalDecision(str, Enum):
    approve = "approve"
    reject = "reject"


class ApprovalEvent(BaseModel):
    event_id: str
    operator: str
    decision: ApprovalDecision
    comment: str = ""
    decided_at: datetime


class GitHubPRDraft(BaseModel):
    draft_id: str
    project_id: str
    playbook_id: str
    repo_url: str
    base_branch: str
    head_branch: str
    title: str
    body_markdown: str
    files: List[str] = Field(default_factory=list)
    status: GitHubPRDraftStatus = GitHubPRDraftStatus.pending_approval
    approvals: List[ApprovalEvent] = Field(default_factory=list)
    created_at: datetime
    updated_at: datetime


class GitHubPRDraftCreate(BaseModel):
    playbook_id: str
    repo_url: Optional[str] = None
    base_branch: str = "main"
    head_branch: Optional[str] = None
    title: Optional[str] = None


class GitHubPRDraftApprove(BaseModel):
    comment: str = ""


class MetricDistribution(BaseModel):
    metric: str
    values: List[float] = Field(default_factory=list)
    mean: float = 0.0
    stdev: float = 0.0
    ci95_low: float = 0.0
    ci95_high: float = 0.0


class StabilityReport(BaseModel):
    report_id: str
    project_id: str
    run_type: RunType
    engines: List[str]
    repeats: int
    run_ids: List[str] = Field(default_factory=list)
    metrics: Dict[str, MetricDistribution] = Field(default_factory=dict)
    created_at: datetime


class StabilityReportCreate(BaseModel):
    run_type: RunType = RunType.on_demand
    engines: List[str] = Field(default_factory=list)
    repeats: int = Field(default=3, ge=2, le=20)


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
