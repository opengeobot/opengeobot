"""
功能：Phase 3.4 - 自动实验设计与效果归因模型
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


# ==================== 实验设计模型 ====================

class ExperimentStatus(str, Enum):
    draft = "draft"
    running = "running"
    completed = "completed"
    paused = "paused"
    cancelled = "cancelled"


class ExperimentType(str, Enum):
    ab_test = "ab_test"
    multivariate = "multivariate"
    split_url = "split_url"
    before_after = "before_after"


class VariantConfig(BaseModel):
    """实验变体配置"""
    variant_id: str
    name: str
    description: str = ""
    weight: float = Field(default=1.0, gt=0.0, le=1.0)
    is_control: bool = False
    metadata: Dict[str, Any] = Field(default_factory=dict)


class MetricGoal(BaseModel):
    """实验指标目标"""
    metric_name: str
    target_value: float
    direction: str = Field(default="increase", description="increase or decrease")
    priority: int = Field(default=1, ge=1, le=5)


class ExperimentDesign(BaseModel):
    """实验设计"""
    experiment_id: str
    project_id: str
    name: str
    description: str = ""
    experiment_type: ExperimentType = ExperimentType.ab_test
    status: ExperimentStatus = ExperimentStatus.draft
    hypothesis: str = ""
    variants: List[VariantConfig] = Field(default_factory=list)
    goals: List[MetricGoal] = Field(default_factory=list)
    sample_size: int = 0
    duration_days: int = 14
    confidence_level: float = Field(default=0.95, ge=0.90, le=0.99)
    min_detectable_effect: float = Field(default=0.05, gt=0.0, le=1.0)
    start_date: Optional[datetime] = None
    end_date: Optional[datetime] = None
    created_at: datetime
    updated_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    deleted: bool = False


class ExperimentDesignCreate(BaseModel):
    project_id: str
    name: str
    description: str = ""
    experiment_type: ExperimentType = ExperimentType.ab_test
    hypothesis: str = ""
    variants: List[VariantConfig] = Field(default_factory=list)
    goals: List[MetricGoal] = Field(default_factory=list)
    duration_days: int = 14
    confidence_level: float = Field(default=0.95, ge=0.90, le=0.99)
    min_detectable_effect: float = Field(default=0.05, gt=0.0, le=1.0)


class ExperimentDesignUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    hypothesis: Optional[str] = None
    variants: Optional[List[VariantConfig]] = None
    goals: Optional[List[MetricGoal]] = None
    duration_days: Optional[int] = None
    status: Optional[ExperimentStatus] = None


# ==================== 实验结果模型 ====================

class VariantResult(BaseModel):
    """变体结果"""
    variant_id: str
    variant_name: str
    sample_size: int
    conversion_rate: float = 0.0
    metric_values: Dict[str, float] = Field(default_factory=dict)
    is_winner: bool = False
    statistical_significance: float = 0.0
    confidence_interval: Optional[List[float]] = None


class ExperimentResult(BaseModel):
    """实验结果"""
    result_id: str
    experiment_id: str
    status: str = "analyzing"
    winner_variant_id: Optional[str] = None
    variants: List[VariantResult] = Field(default_factory=list)
    statistical_power: float = 0.0
    p_value: Optional[float] = None
    confidence_level: float = 0.95
    recommendations: List[str] = Field(default_factory=list)
    analyzed_at: Optional[datetime] = None
    created_at: datetime


# ==================== 归因分析模型 ====================

class AttributionModel(str, Enum):
    first_touch = "first_touch"
    last_touch = "last_touch"
    linear = "linear"
    time_decay = "time_decay"
    position_based = "position_based"
    data_driven = "data_driven"


class Touchpoint(BaseModel):
    """接触点"""
    touchpoint_id: str
    channel: str
    action: str
    timestamp: datetime
    weight: float = 1.0
    metadata: Dict[str, Any] = Field(default_factory=dict)


class AttributionResult(BaseModel):
    """归因分析结果"""
    attribution_id: str
    project_id: str
    experiment_id: Optional[str] = None
    model: AttributionModel
    touchpoints: List[Touchpoint] = Field(default_factory=list)
    channel_contributions: Dict[str, float] = Field(default_factory=dict)
    total_conversions: int = 0
    total_value: float = 0.0
    analyzed_at: datetime


class AttributionAnalysisCreate(BaseModel):
    project_id: str
    experiment_id: Optional[str] = None
    model: AttributionModel = AttributionModel.last_touch


# ==================== 自动实验建议模型 ====================

class ExperimentSuggestion(BaseModel):
    """自动实验建议"""
    suggestion_id: str
    project_id: str
    title: str
    description: str
    rationale: str
    expected_impact: str
    priority: int = Field(default=1, ge=1, le=5)
    suggested_type: ExperimentType = ExperimentType.ab_test
    suggested_variants: List[str] = Field(default_factory=list)
    suggested_metrics: List[str] = Field(default_factory=list)
    created_at: datetime
    dismissed: bool = False


class ExperimentSuggestionCreate(BaseModel):
    project_id: str
    title: str
    description: str
    rationale: str
    expected_impact: str
    priority: int = Field(default=1, ge=1, le=5)
    suggested_type: ExperimentType = ExperimentType.ab_test
    suggested_variants: List[str] = Field(default_factory=list)
    suggested_metrics: List[str] = Field(default_factory=list)
