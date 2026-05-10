"""
功能：APScheduler 调度器集成
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Any, Dict, List, Optional

from apscheduler.events import EVENT_JOB_ERROR, EVENT_JOB_EXECUTED
from apscheduler.job import Job
from apscheduler.jobstores.memory import MemoryJobStore
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.date import DateTrigger
from apscheduler.triggers.interval import IntervalTrigger

from app.models import RunType


class SchedulerManager:
    """调度器管理器 - 管理 APScheduler 生命周期"""
    
    def __init__(self) -> None:
        self.scheduler = AsyncIOScheduler(
            jobstores={"default": MemoryJobStore()},
            job_defaults={
                "coalesce": True,
                "max_instances": 3,
            },
        )
        self._logger = logging.getLogger("opengeobot.scheduler")
        self._job_metadata: Dict[str, Dict[str, Any]] = {}
    
    def start(self) -> None:
        """启动调度器"""
        if not self.scheduler.running:
            self.scheduler.start()
            self.scheduler.add_listener(
                self._job_listener,
                EVENT_JOB_EXECUTED | EVENT_JOB_ERROR,
            )
            self._logger.info("Scheduler started")
    
    def shutdown(self, wait: bool = True) -> None:
        """关闭调度器"""
        if self.scheduler.running:
            self.scheduler.shutdown(wait=wait)
            self._logger.info("Scheduler shut down")
    
    def add_job(
        self,
        job_id: str,
        func,
        trigger_type: str = "cron",
        args: Optional[list] = None,
        kwargs: Optional[Dict[str, Any]] = None,
        **trigger_kwargs,
    ) -> Job:
        """
        添加调度任务
        
        Args:
            job_id: 任务唯一标识
            func: 执行函数
            trigger_type: 触发器类型 (cron, interval, date)
            args: 位置参数
            kwargs: 关键字参数
            **trigger_kwargs: 触发器参数 (cron表达式或interval参数)
        """
        if trigger_type == "cron":
            trigger = CronTrigger(**trigger_kwargs)
        elif trigger_type == "interval":
            trigger = IntervalTrigger(**trigger_kwargs)
        elif trigger_type == "date":
            trigger = DateTrigger(**trigger_kwargs)
        else:
            raise ValueError(f"Unsupported trigger type: {trigger_type}")
        
        job = self.scheduler.add_job(
            func,
            trigger=trigger,
            args=args or [],
            kwargs=kwargs or {},
            id=job_id,
            replace_existing=True,
        )
        
        self._job_metadata[job_id] = {
            "trigger_type": trigger_type,
            "trigger_kwargs": trigger_kwargs,
            "created_at": datetime.utcnow().isoformat(),
        }
        
        self._logger.info(f"Job {job_id} added with {trigger_type} trigger")
        return job
    
    def remove_job(self, job_id: str) -> None:
        """移除调度任务"""
        try:
            self.scheduler.remove_job(job_id)
            self._job_metadata.pop(job_id, None)
            self._logger.info(f"Job {job_id} removed")
        except Exception as exc:
            self._logger.warning(f"Failed to remove job {job_id}: {exc}")
    
    def get_job(self, job_id: str) -> Optional[Job]:
        """获取任务信息"""
        try:
            return self.scheduler.get_job(job_id)
        except Exception:
            return None
    
    def get_all_jobs(self) -> List[Job]:
        """获取所有任务"""
        return self.scheduler.get_jobs()
    
    def get_job_metadata(self, job_id: str) -> Optional[Dict[str, Any]]:
        """获取任务元数据"""
        return self._job_metadata.get(job_id)
    
    def pause_job(self, job_id: str) -> None:
        """暂停任务"""
        self.scheduler.pause_job(job_id)
        self._logger.info(f"Job {job_id} paused")
    
    def resume_job(self, job_id: str) -> None:
        """恢复任务"""
        self.scheduler.resume_job(job_id)
        self._logger.info(f"Job {job_id} resumed")
    
    def _job_listener(self, event):
        """任务执行事件监听器"""
        if event.exception:
            self._logger.error(
                f"Job {event.job_id} failed: {event.exception}",
                exc_info=event.exception,
            )
        else:
            self._logger.info(f"Job {event.job_id} executed successfully")


# 全局调度器实例
scheduler_manager = SchedulerManager()


async def schedule_project_run(
    project_id: str,
    run_type: RunType,
    engines: List[str] | None = None,
    service=None,
) -> None:
    """
    调度项目执行任务 - 供调度器调用的包装函数
    
    Args:
        project_id: 项目ID
        run_type: 运行类型
        engines: 引擎列表
        service: OpenGeoBotService 实例
    """
    if service is None:
        from app.main import service as global_service
        service = global_service
    
    try:
        run = await service.create_run_async(project_id, run_type, engines or [])
        scheduler_manager._logger.info(
            f"Scheduled run {run.run_id} completed for project {project_id}"
        )
    except Exception as exc:
        scheduler_manager._logger.error(
            f"Scheduled run failed for project {project_id}: {exc}"
        )
        raise


@asynccontextmanager
async def lifespan(app):
    """FastAPI 生命周期管理 - 在应用启动时启动调度器，关闭时停止"""
    # 启动时
    scheduler_manager.start()
    app.state.scheduler = scheduler_manager
    yield
    # 关闭时
    scheduler_manager.shutdown(wait=True)
