"""
功能：Phase 3.3 - 外部引用建设任务管理模型
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


# ==================== 引用任务模型 ====================

class ReferenceTaskStatus(str, Enum):
    pending = "pending"
    in_progress = "in_progress"
    completed = "completed"
    failed = "failed"
    cancelled = "cancelled"


class ReferenceTaskPriority(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"
    critical = "critical"


class ReferenceType(str, Enum):
    backlink = "backlink"
    citation = "citation"
    mention = "mention"
    integration = "integration"
    partnership = "partnership"


class ReferenceTarget(BaseModel):
    """引用目标"""
    url: str
    domain: str
    title: str = ""
    description: str = ""
    is_official: bool = False
    quality_score: float = Field(default=0.0, ge=0.0, le=1.0)


class ReferenceTask(BaseModel):
    """外部引用建设任务"""
    task_id: str
    project_id: str
    tenant_id: Optional[str] = None
    target: ReferenceTarget
    reference_type: ReferenceType
    priority: ReferenceTaskPriority = ReferenceTaskPriority.medium
    status: ReferenceTaskStatus = ReferenceTaskStatus.pending
    assigned_to: Optional[str] = None  # user_id
    outreach_count: int = 0
    max_outreach_attempts: int = 5
    notes: str = ""
    metadata: Dict[str, Any] = Field(default_factory=dict)
    created_at: datetime
    updated_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    deadline: Optional[datetime] = None
    deleted: bool = False


class ReferenceTaskCreate(BaseModel):
    project_id: str
    url: str
    domain: str
    title: str = ""
    description: str = ""
    reference_type: ReferenceType
    priority: ReferenceTaskPriority = ReferenceTaskPriority.medium
    is_official: bool = False
    quality_score: float = Field(default=0.0, ge=0.0, le=1.0)
    notes: str = ""
    max_outreach_attempts: int = 5
    deadline: Optional[datetime] = None


class ReferenceTaskUpdate(BaseModel):
    priority: Optional[ReferenceTaskPriority] = None
    status: Optional[ReferenceTaskStatus] = None
    assigned_to: Optional[str] = None
    notes: Optional[str] = None
    max_outreach_attempts: Optional[int] = None
    deadline: Optional[datetime] = None


# ==================== 外联记录模型 ====================

class OutreachStatus(str, Enum):
    pending = "pending"
    sent = "sent"
    responded = "responded"
    accepted = "accepted"
    declined = "declined"
    no_response = "no_response"


class OutreachChannel(str, Enum):
    email = "email"
    github_issue = "github_issue"
    github_pr = "github_pr"
    twitter = "twitter"
    linkedin = "linkedin"
    other = "other"


class OutreachRecord(BaseModel):
    """外联记录"""
    outreach_id: str
    task_id: str
    project_id: str
    channel: OutreachChannel
    status: OutreachStatus = OutreachStatus.pending
    contact_email: Optional[str] = None
    contact_name: Optional[str] = None
    message_template: str = ""
    custom_message: str = ""
    sent_at: Optional[datetime] = None
    responded_at: Optional[datetime] = None
    response_notes: str = ""
    created_at: datetime
    created_by: str  # user_id


class OutreachRecordCreate(BaseModel):
    task_id: str
    channel: OutreachChannel
    contact_email: Optional[str] = None
    contact_name: Optional[str] = None
    message_template: str = ""
    custom_message: str = ""


class OutreachRecordUpdate(BaseModel):
    status: Optional[OutreachStatus] = None
    response_notes: Optional[str] = None
    responded_at: Optional[datetime] = None


# ==================== 引用追踪模型 ====================

class ReferenceTracking(BaseModel):
    """引用追踪记录"""
    tracking_id: str
    task_id: str
    project_id: str
    reference_url: str
    referring_domain: str
    target_url: str
    anchor_text: str = ""
    first_detected_at: datetime
    last_checked_at: datetime
    is_active: bool = True
    broken: bool = False
    nofollow: bool = False
    domain_authority: int = Field(default=0, ge=0, le=100)
    check_count: int = 0


class ReferenceTrackingCreate(BaseModel):
    task_id: str
    reference_url: str
    referring_domain: str
    target_url: str
    anchor_text: str = ""
    is_active: bool = True
    nofollow: bool = False
    domain_authority: int = Field(default=0, ge=0, le=100)


# ==================== 引用统计模型 ====================

class ReferenceStatistics(BaseModel):
    """引用统计"""
    total_tasks: int = 0
    pending_tasks: int = 0
    in_progress_tasks: int = 0
    completed_tasks: int = 0
    failed_tasks: int = 0
    total_outreach: int = 0
    successful_outreach: int = 0
    acceptance_rate: float = 0.0
    active_references: int = 0
    broken_references: int = 0
    average_quality_score: float = 0.0
    total_domain_authority: int = 0
