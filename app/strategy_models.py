"""
功能：Phase 3.5 - 跨项目策略复用与行业 Benchmark 模型
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


# ==================== 策略库模型 ====================

class StrategyCategory(str, Enum):
    seo = "seo"
    content = "content"
    technical = "technical"
    link_building = "link_building"
    user_experience = "user_experience"
    social = "social"
    other = "other"


class StrategyEffectiveness(str, Enum):
    highly_effective = "highly_effective"
    effective = "effective"
    moderately_effective = "moderately_effective"
    ineffective = "ineffective"
    unknown = "unknown"


class StrategyTemplate(BaseModel):
    """策略模板"""
    template_id: str
    name: str
    description: str
    category: StrategyCategory
    effectiveness: StrategyEffectiveness = StrategyEffectiveness.unknown
    applicable_scenarios: List[str] = Field(default_factory=list)
    steps: List[str] = Field(default_factory=list)
    required_resources: List[str] = Field(default_factory=list)
    expected_outcomes: Dict[str, Any] = Field(default_factory=dict)
    tags: List[str] = Field(default_factory=list)
    source_project_id: Optional[str] = None
    created_by: str = ""
    created_at: datetime
    is_public: bool = False
    usage_count: int = 0
    success_rate: float = 0.0
    deleted: bool = False


class StrategyTemplateCreate(BaseModel):
    name: str
    description: str
    category: StrategyCategory
    applicable_scenarios: List[str] = Field(default_factory=list)
    steps: List[str] = Field(default_factory=list)
    required_resources: List[str] = Field(default_factory=list)
    expected_outcomes: Dict[str, Any] = Field(default_factory=dict)
    tags: List[str] = Field(default_factory=list)
    is_public: bool = False


class StrategyTemplateUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    category: Optional[StrategyCategory] = None
    effectiveness: Optional[StrategyEffectiveness] = None
    适用场景: Optional[List[str]] = None
    steps: Optional[List[str]] = None
    tags: Optional[List[str]] = None
    is_public: Optional[bool] = None


class StrategyInstance(BaseModel):
    """策略实例（应用到具体项目）"""
    instance_id: str
    template_id: str
    project_id: str
    status: str = Field(default="active", description="active, paused, completed, failed")
    customizations: Dict[str, Any] = Field(default_factory=dict)
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    results: Dict[str, Any] = Field(default_factory=dict)
    created_at: datetime
    created_by: str = ""


class StrategyInstanceCreate(BaseModel):
    template_id: str
    project_id: str
    customizations: Dict[str, Any] = Field(default_factory=dict)


# ==================== 行业 Benchmark 模型 ====================

class Industry(str, Enum):
    technology = "technology"
    ecommerce = "ecommerce"
    finance = "finance"
    healthcare = "healthcare"
    education = "education"
    media = "media"
    government = "government"
    other = "other"


class BenchmarkMetric(BaseModel):
    """Benchmark 指标"""
    metric_name: str
    industry_average: float
    top_quartile: float
    median: float
    bottom_quartile: float
    sample_size: int
    last_updated: datetime


class BenchmarkReport(BaseModel):
    """Benchmark 报告"""
    report_id: str
    industry: Industry
    region: str = "global"
    metrics: List[BenchmarkMetric] = Field(default_factory=list)
    sample_projects: int = 0
    data_collection_period: str = ""
    created_at: datetime
    updated_at: Optional[datetime] = None
    is_public: bool = True


class ProjectBenchmark(BaseModel):
    """项目 Benchmark 对比"""
    project_id: str
    industry: Industry
    metrics_comparison: Dict[str, Dict[str, float]] = Field(
        default_factory=dict,
        description="metric_name -> {project_value, industry_avg, percentile}"
    )
    overall_score: float = Field(default=0.0, ge=0.0, le=100.0)
    strengths: List[str] = Field(default_factory=list)
    weaknesses: List[str] = Field(default_factory=list)
    recommendations: List[str] = Field(default_factory=list)
    analyzed_at: datetime


class BenchmarkAnalysisCreate(BaseModel):
    project_id: str
    industry: Industry
    region: str = "global"


# ==================== 策略推荐模型 ====================

class StrategyRecommendation(BaseModel):
    """策略推荐"""
    recommendation_id: str
    project_id: str
    strategy_template_id: str
    strategy_name: str
    description: str
    category: StrategyCategory
    expected_impact: Dict[str, float] = Field(default_factory=dict)
    implementation_effort: str = Field(default="medium", description="low, medium, high")
    priority: int = Field(default=1, ge=1, le=5)
    rationale: str = ""
    similar_projects_count: int = 0
    average_success_rate: float = 0.0
    created_at: datetime


# ==================== 行业洞察模型 ====================

class IndustryInsight(BaseModel):
    """行业洞察"""
    insight_id: str
    industry: Industry
    title: str
    description: str
    data_points: List[Dict[str, Any]] = Field(default_factory=list)
    trends: List[str] = Field(default_factory=list)
    best_practices: List[str] = Field(default_factory=list)
    source_projects: List[str] = Field(default_factory=list)
    confidence_score: float = Field(default=0.0, ge=0.0, le=1.0)
    published_at: datetime
    expires_at: Optional[datetime] = None
