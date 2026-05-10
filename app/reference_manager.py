"""
功能：Phase 3.3 - 外部引用建设任务管理器
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional

from app.reference_models import (
    OutreachChannel,
    OutreachRecord,
    OutreachRecordCreate,
    OutreachRecordUpdate,
    OutreachStatus,
    ReferenceStatistics,
    ReferenceTarget,
    ReferenceTask,
    ReferenceTaskCreate,
    ReferenceTaskPriority,
    ReferenceTaskStatus,
    ReferenceTracking,
    ReferenceTrackingCreate,
    ReferenceType,
)


class ReferenceManager:
    """外部引用建设任务管理器"""
    
    def __init__(self) -> None:
        self._tasks: Dict[str, ReferenceTask] = {}
        self._outreach_records: Dict[str, OutreachRecord] = {}
        self._tracking_records: Dict[str, ReferenceTracking] = {}
        self._project_tasks: Dict[str, List[str]] = {}  # project_id -> [task_ids]
        self._task_outreach: Dict[str, List[str]] = {}  # task_id -> [outreach_ids]
        self._task_tracking: Dict[str, List[str]] = {}  # task_id -> [tracking_ids]
    
    # ==================== 引用任务管理 ====================
    
    def create_task(self, payload: ReferenceTaskCreate) -> ReferenceTask:
        task_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        target = ReferenceTarget(
            url=payload.url,
            domain=payload.domain,
            title=payload.title,
            description=payload.description,
            is_official=payload.is_official,
            quality_score=payload.quality_score,
        )
        
        task = ReferenceTask(
            task_id=task_id,
            project_id=payload.project_id,
            target=target,
            reference_type=payload.reference_type,
            priority=payload.priority,
            notes=payload.notes,
            max_outreach_attempts=payload.max_outreach_attempts,
            deadline=payload.deadline,
            created_at=now,
        )
        
        self._tasks[task_id] = task
        if payload.project_id not in self._project_tasks:
            self._project_tasks[payload.project_id] = []
        self._project_tasks[payload.project_id].append(task_id)
        self._task_outreach[task_id] = []
        self._task_tracking[task_id] = []
        
        return task
    
    def get_task(self, task_id: str) -> Optional[ReferenceTask]:
        task = self._tasks.get(task_id)
        if task and not task.deleted:
            return task
        return None
    
    def list_tasks(
        self,
        project_id: Optional[str] = None,
        status: Optional[ReferenceTaskStatus] = None,
        priority: Optional[ReferenceTaskPriority] = None,
        reference_type: Optional[ReferenceType] = None,
        assigned_to: Optional[str] = None,
    ) -> List[ReferenceTask]:
        tasks = [t for t in self._tasks.values() if not t.deleted]
        
        if project_id:
            tasks = [t for t in tasks if t.project_id == project_id]
        if status:
            tasks = [t for t in tasks if t.status == status]
        if priority:
            tasks = [t for t in tasks if t.priority == priority]
        if reference_type:
            tasks = [t for t in tasks if t.reference_type == reference_type]
        if assigned_to:
            tasks = [t for t in tasks if t.assigned_to == assigned_to]
        
        return tasks
    
    def update_task(self, task_id: str, payload: ReferenceTaskUpdate) -> Optional[ReferenceTask]:
        task = self.get_task(task_id)
        if not task:
            return None
        
        if payload.priority is not None:
            task.priority = payload.priority
        if payload.status is not None:
            task.status = payload.status
            if payload.status == ReferenceTaskStatus.completed:
                task.completed_at = datetime.utcnow()
        if payload.assigned_to is not None:
            task.assigned_to = payload.assigned_to
        if payload.notes is not None:
            task.notes = payload.notes
        if payload.max_outreach_attempts is not None:
            task.max_outreach_attempts = payload.max_outreach_attempts
        if payload.deadline is not None:
            task.deadline = payload.deadline
        
        task.updated_at = datetime.utcnow()
        return task
    
    def delete_task(self, task_id: str) -> bool:
        task = self.get_task(task_id)
        if not task:
            return False
        
        task.deleted = True
        task.updated_at = datetime.utcnow()
        return True
    
    # ==================== 外联记录管理 ====================
    
    def create_outreach(self, payload: OutreachRecordCreate, created_by: str) -> OutreachRecord:
        task = self.get_task(payload.task_id)
        if not task:
            raise ValueError("Task not found")
        
        outreach_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        record = OutreachRecord(
            outreach_id=outreach_id,
            task_id=payload.task_id,
            project_id=task.project_id,
            channel=payload.channel,
            contact_email=payload.contact_email,
            contact_name=payload.contact_name,
            message_template=payload.message_template,
            custom_message=payload.custom_message,
            created_at=now,
            created_by=created_by,
        )
        
        self._outreach_records[outreach_id] = record
        self._task_outreach[payload.task_id].append(outreach_id)
        
        # 更新任务的外联计数
        task.outreach_count += 1
        task.updated_at = now
        
        return record
    
    def get_outreach(self, outreach_id: str) -> Optional[OutreachRecord]:
        return self._outreach_records.get(outreach_id)
    
    def list_outreach_records(self, task_id: Optional[str] = None) -> List[OutreachRecord]:
        if task_id:
            outreach_ids = self._task_outreach.get(task_id, [])
            return [self._outreach_records[oid] for oid in outreach_ids if oid in self._outreach_records]
        return list(self._outreach_records.values())
    
    def update_outreach(self, outreach_id: str, payload: OutreachRecordUpdate) -> Optional[OutreachRecord]:
        record = self.get_outreach(outreach_id)
        if not record:
            return None
        
        if payload.status is not None:
            record.status = payload.status
        if payload.response_notes is not None:
            record.response_notes = payload.response_notes
        if payload.responded_at is not None:
            record.responded_at = payload.responded_at
        
        return record
    
    # ==================== 引用追踪管理 ====================
    
    def create_tracking(self, payload: ReferenceTrackingCreate) -> ReferenceTracking:
        task = self.get_task(payload.task_id)
        if not task:
            raise ValueError("Task not found")
        
        tracking_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        tracking = ReferenceTracking(
            tracking_id=tracking_id,
            task_id=payload.task_id,
            project_id=task.project_id,
            reference_url=payload.reference_url,
            referring_domain=payload.referring_domain,
            target_url=payload.target_url,
            anchor_text=payload.anchor_text,
            first_detected_at=now,
            last_checked_at=now,
            is_active=payload.is_active,
            nofollow=payload.nofollow,
            domain_authority=payload.domain_authority,
            check_count=1,
        )
        
        self._tracking_records[tracking_id] = tracking
        self._task_tracking[payload.task_id].append(tracking_id)
        
        return tracking
    
    def get_tracking(self, tracking_id: str) -> Optional[ReferenceTracking]:
        return self._tracking_records.get(tracking_id)
    
    def list_tracking_records(self, task_id: Optional[str] = None) -> List[ReferenceTracking]:
        if task_id:
            tracking_ids = self._task_tracking.get(task_id, [])
            return [self._tracking_records[tid] for tid in tracking_ids if tid in self._tracking_records]
        return list(self._tracking_records.values())
    
    def update_tracking_status(self, tracking_id: str, is_active: bool, broken: bool = False) -> Optional[ReferenceTracking]:
        tracking = self.get_tracking(tracking_id)
        if not tracking:
            return None
        
        tracking.is_active = is_active
        tracking.broken = broken
        tracking.last_checked_at = datetime.utcnow()
        tracking.check_count += 1
        
        return tracking
    
    # ==================== 统计分析 ====================
    
    def get_project_statistics(self, project_id: str) -> ReferenceStatistics:
        tasks = self.list_tasks(project_id=project_id)
        
        stats = ReferenceStatistics()
        stats.total_tasks = len(tasks)
        
        for task in tasks:
            if task.status == ReferenceTaskStatus.pending:
                stats.pending_tasks += 1
            elif task.status == ReferenceTaskStatus.in_progress:
                stats.in_progress_tasks += 1
            elif task.status == ReferenceTaskStatus.completed:
                stats.completed_tasks += 1
            elif task.status == ReferenceTaskStatus.failed:
                stats.failed_tasks += 1
            
            stats.average_quality_score += task.target.quality_score
        
        if tasks:
            stats.average_quality_score /= len(tasks)
        
        # 外联统计
        for task in tasks:
            outreach_ids = self._task_outreach.get(task.task_id, [])
            stats.total_outreach += len(outreach_ids)
            
            for oid in outreach_ids:
                record = self._outreach_records.get(oid)
                if record and record.status in [OutreachStatus.accepted, OutreachStatus.responded]:
                    stats.successful_outreach += 1
        
        if stats.total_outreach > 0:
            stats.acceptance_rate = stats.successful_outreach / stats.total_outreach
        
        # 引用追踪统计
        for task in tasks:
            tracking_ids = self._task_tracking.get(task.task_id, [])
            for tid in tracking_ids:
                tracking = self._tracking_records.get(tid)
                if tracking:
                    if tracking.is_active:
                        stats.active_references += 1
                    if tracking.broken:
                        stats.broken_references += 1
                    stats.total_domain_authority += tracking.domain_authority
        
        return stats


# 全局引用管理器实例
reference_manager = ReferenceManager()
