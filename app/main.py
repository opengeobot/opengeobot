"""
功能：OpenGEO Bot MVP FastAPI 入口
时间：2026-05-08 13:33:00
作者：AxeXie
"""

from __future__ import annotations

import os
import uuid
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Any, Dict, List, Optional

from fastapi import BackgroundTasks, FastAPI, HTTPException, Query, Request
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from app.foundation import (
    ConfigCenter,
    DataDictionary,
    EnumDefinition,
    I18N,
    MetricDefinition,
    build_logger,
    reset_operator,
    reset_span_id,
    reset_trace_id,
    set_operator,
    set_span_id,
    set_trace_id,
)
from app.models import (
    AlertStatus,
    AlertUpdate,
    AssetCreate,
    AssetUpdate,
    AuditLog,
    CitationSource,
    GitHubPRDraftApprove,
    GitHubPRDraftCreate,
    GitHubPRDraftStatus,
    ProjectConfig,
    ProjectCreate,
    ProjectUpdate,
    PromptImportItem,
    PromptUpdate,
    RunType,
    StabilityReportCreate,
)
from app.repository import MemoryRepository, PostgreSQLRepository, SQLiteRepository
from app.services import OpenGeoBotService
from app.feature_flags import FeatureFlag, FeatureFlagChecker
from app.pagination import PaginationParams, apply_pagination, build_paginated_response
from app.scheduler import scheduler_manager, lifespan
from app.tenant_manager import tenant_manager, ROLE_PERMISSIONS
from app.phase3_models import (
    AuditAction,
    TenantCreate,
    TenantUpdate,
    UserCreate,
    UserUpdate,
    UserRole,
)
from app.reference_manager import reference_manager
from app.reference_models import (
    OutreachChannel,
    OutreachRecordCreate,
    OutreachRecordUpdate,
    OutreachStatus,
    ReferenceStatistics,
    ReferenceTaskCreate,
    ReferenceTaskPriority,
    ReferenceTaskStatus,
    ReferenceTaskUpdate,
    ReferenceTracking,
    ReferenceTrackingCreate,
    ReferenceType,
)
from app.experiment_manager import experiment_manager
from app.experiment_models import (
    AttributionAnalysisCreate,
    AttributionModel,
    ExperimentDesignCreate,
    ExperimentDesignUpdate,
    ExperimentResult,
    ExperimentStatus,
    ExperimentSuggestionCreate,
    ExperimentType,
    VariantResult,
)
from app.strategy_manager import strategy_manager
from app.strategy_models import (
    BenchmarkAnalysisCreate,
    Industry,
    StrategyCategory,
    StrategyEffectiveness,
    StrategyInstanceCreate,
    StrategyTemplateCreate,
    StrategyTemplateUpdate,
)


@asynccontextmanager
async def app_lifespan(app):
    """应用生命周期管理"""
    async with lifespan(app):
        yield


app = FastAPI(title="OpenGEO Bot MVP", version="1.0.0", lifespan=app_lifespan)

storage_backend = os.getenv("OPEN_GEOBOT_STORAGE", "sqlite").lower()
sqlite_path = os.getenv("OPEN_GEOBOT_SQLITE_PATH", "data/opengeobot.db")
postgres_dsn = os.getenv("OPEN_GEOBOT_POSTGRES_DSN", "postgresql://postgres:postgres@127.0.0.1:5432/opengeobot")
if storage_backend == "memory":
    repository = MemoryRepository()
elif storage_backend == "postgres":
    repository = PostgreSQLRepository(postgres_dsn)
else:
    repository = SQLiteRepository(sqlite_path)
config_center = ConfigCenter()
dictionary = DataDictionary()
i18n = I18N()
logger = build_logger()
service = OpenGeoBotService(repository, config_center, dictionary, i18n, logger)
feature_flag_checker = FeatureFlagChecker(config_center)


@app.middleware("http")
async def trace_context_middleware(request: Request, call_next):
    trace_id = (
        request.headers.get("x-trace-id")
        or request.headers.get("x-traceid")
        or request.headers.get("traceid")
        or request.headers.get("traceId")
    )
    if trace_id:
        trace_id = trace_id.strip()
    if not trace_id:
        trace_id = uuid.uuid4().hex
    
    span_id = uuid.uuid4().hex[:16]

    operator = request.headers.get("x-operator") or request.headers.get("x-user") or request.headers.get("x-user-id")
    if operator:
        operator = operator.strip()
    if not operator:
        operator = "system"
    
    tenant_id = request.headers.get("x-tenant-id")
    if tenant_id:
        config_center.set_tenant_context(tenant_id.strip())

    trace_token = set_trace_id(trace_id)
    span_token = set_span_id(span_id)
    operator_token = set_operator(operator)
    try:
        response = await call_next(request)
        response.headers["X-Trace-Id"] = trace_id
        response.headers["X-Span-Id"] = span_id
        return response
    finally:
        reset_trace_id(trace_token)
        reset_span_id(span_token)
        reset_operator(operator_token)
        config_center.set_tenant_context(None)


class PromptImportRequest(BaseModel):
    items: List[PromptImportItem] = Field(default_factory=list)


class PromptGenerateRequest(BaseModel):
    count: int = 20


class RunCreateRequest(BaseModel):
    run_type: RunType
    engines: List[str] = Field(default_factory=list)


class InsightGenerateRequest(BaseModel):
    run_id: str
    limit: int = 20


class PlaybookGenerateRequest(BaseModel):
    insight_id: str


class VerificationRequest(BaseModel):
    baseline_run_id: str
    after_run_id: str


class StrategyMemoryRequest(BaseModel):
    playbook_id: str
    verification_report_id: str


class ProjectConfigUpdateRequest(BaseModel):
    config: Dict[str, Any] = Field(default_factory=dict)


class AssetSyncRequest(BaseModel):
    force: bool = False
    asset_ids: List[str] = Field(default_factory=list)


class ScheduleCreateRequest(BaseModel):
    """创建调度任务请求"""
    project_id: str
    run_type: RunType
    engines: List[str] = Field(default_factory=list)
    trigger_type: str = Field(default="cron", description="cron, interval, or date")
    # Cron 参数
    hour: str | None = Field(default=None, description="Cron hour (e.g., '0' for midnight)")
    minute: str | None = Field(default=None, description="Cron minute (e.g., '0')")
    day_of_week: str | None = Field(default=None, description="Cron day of week (e.g., 'mon-fri')")
    # Interval 参数
    days: int | None = Field(default=None, description="Interval days")
    hours: int | None = Field(default=None, description="Interval hours")
    minutes: int | None = Field(default=None, description="Interval minutes")
    # Date 参数 (一次性)
    run_date: str | None = Field(default=None, description="Run date for one-time execution (ISO format)")


class ScheduleUpdateRequest(BaseModel):
    """更新调度任务请求"""
    pause: bool | None = Field(default=None, description="Pause/resume the schedule")


# ==================== Phase 3: 多租户与用户管理请求模型 ====================

class UserListQuery(BaseModel):
    """用户列表查询参数"""
    page: int = Query(default=1, ge=1)
    page_size: int = Query(default=20, ge=1, le=100)
    role: UserRole | None = Query(default=None)


# ==================== Phase 3: 多租户管理 API ====================

@app.post("/tenants")
def create_tenant(payload: TenantCreate):
    """创建新租户"""
    try:
        tenant = tenant_manager.create_tenant(payload)
        tenant_manager.log_action(
            tenant_id=tenant.tenant_id,
            user_id="system",
            action=AuditAction.create,
            resource="tenant",
            resource_id=tenant.tenant_id,
            details={"tenant_name": tenant.tenant_name, "tier": tenant.tier.value},
        )
        return tenant
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/tenants")
def list_tenants(include_deleted: bool = False, page: int = Query(default=1, ge=1), page_size: int = Query(default=20, ge=1, le=100)):
    """列出所有租户"""
    tenants = tenant_manager.list_tenants(include_deleted=include_deleted)
    items, total = apply_pagination([t.dict() for t in tenants], page, page_size)
    return build_paginated_response(items, total, page, page_size)


@app.get("/tenants/{tenant_id}")
def get_tenant(tenant_id: str):
    """获取租户详情"""
    tenant = tenant_manager.get_tenant(tenant_id)
    if tenant is None:
        raise HTTPException(status_code=404, detail="tenant not found")
    return tenant


@app.patch("/tenants/{tenant_id}")
def update_tenant(tenant_id: str, payload: TenantUpdate):
    """更新租户配置"""
    existing = tenant_manager.get_tenant(tenant_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="tenant not found")
    
    try:
        tenant = tenant_manager.update_tenant(tenant_id, payload)
        tenant_manager.log_action(
            tenant_id=tenant_id,
            user_id="system",
            action=AuditAction.update,
            resource="tenant",
            resource_id=tenant_id,
            details=payload.dict(exclude_unset=True),
        )
        return tenant
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.delete("/tenants/{tenant_id}")
def delete_tenant(tenant_id: str):
    """删除租户（软删除）"""
    existing = tenant_manager.get_tenant(tenant_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="tenant not found")
    
    try:
        tenant_manager.delete_tenant(tenant_id)
        tenant_manager.log_action(
            tenant_id=tenant_id,
            user_id="system",
            action=AuditAction.delete,
            resource="tenant",
            resource_id=tenant_id,
            details={"reason": "soft_delete"},
        )
        return {"tenant_id": tenant_id, "status": "deleted"}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


# ==================== Phase 3: 用户管理 API ====================

@app.post("/users")
def create_user(payload: UserCreate):
    """创建新用户"""
    # 验证租户存在
    tenant = tenant_manager.get_tenant(payload.tenant_id)
    if tenant is None:
        raise HTTPException(status_code=404, detail="tenant not found")
    
    try:
        user = tenant_manager.create_user(payload)
        tenant_manager.log_action(
            tenant_id=user.tenant_id,
            user_id=user.user_id,
            action=AuditAction.create,
            resource="user",
            resource_id=user.user_id,
            details={"username": user.username, "role": user.role.value},
        )
        return user
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/users")
def list_users(
    tenant_id: str | None = Query(default=None),
    role: UserRole | None = Query(default=None),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
):
    """列出用户（可按租户过滤）"""
    users = tenant_manager.list_users(tenant_id=tenant_id, role=role)
    items, total = apply_pagination([u.dict() for u in users], page, page_size)
    return build_paginated_response(items, total, page, page_size)


@app.get("/users/{user_id}")
def get_user(user_id: str):
    """获取用户详情"""
    user = tenant_manager.get_user(user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="user not found")
    return user


@app.patch("/users/{user_id}")
def update_user(user_id: str, payload: UserUpdate):
    """更新用户信息"""
    existing = tenant_manager.get_user(user_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="user not found")
    
    try:
        user = tenant_manager.update_user(user_id, payload)
        tenant_manager.log_action(
            tenant_id=user.tenant_id,
            user_id=user_id,
            action=AuditAction.update,
            resource="user",
            resource_id=user_id,
            details=payload.dict(exclude_unset=True),
        )
        return user
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.delete("/users/{user_id}")
def delete_user(user_id: str):
    """删除用户（软删除）"""
    existing = tenant_manager.get_user(user_id)
    if existing is None:
        raise HTTPException(status_code=404, detail="user not found")
    
    try:
        tenant_manager.delete_user(user_id)
        tenant_manager.log_action(
            tenant_id=existing.tenant_id,
            user_id=user_id,
            action=AuditAction.delete,
            resource="user",
            resource_id=user_id,
            details={"reason": "soft_delete"},
        )
        return {"user_id": user_id, "status": "deleted"}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


# ==================== Phase 3: 权限与审计 API ====================

@app.get("/users/{user_id}/permissions")
def get_user_permissions(user_id: str):
    """获取用户权限列表"""
    user = tenant_manager.get_user(user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="user not found")
    
    permissions = tenant_manager.get_user_permissions(user_id)
    return {
        "user_id": user_id,
        "role": user.role.value,
        "permissions": [p.dict() for p in permissions],
    }


@app.get("/roles/{role}/permissions")
def get_role_permissions(role: UserRole):
    """获取角色权限定义"""
    permissions = ROLE_PERMISSIONS.get(role)
    if permissions is None:
        raise HTTPException(status_code=404, detail="role not found")
    return {
        "role": role.value,
        "permissions": [p.dict() for p in permissions],
    }


@app.get("/audit-logs")
def list_audit_logs_global(
    tenant_id: str | None = Query(default=None),
    user_id: str | None = Query(default=None),
    action: AuditAction | None = Query(default=None),
    resource: str | None = Query(default=None),
    start_time: datetime | None = Query(default=None),
    end_time: datetime | None = Query(default=None),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=100),
):
    """查询全局审计日志（支持多维度过滤）"""
    logs = tenant_manager.get_audit_logs(
        tenant_id=tenant_id,
        user_id=user_id,
        action=action,
        resource=resource,
        start_time=start_time,
        end_time=end_time,
    )
    items, total = apply_pagination([log.dict() for log in logs], page, page_size)
    return build_paginated_response(items, total, page, page_size)


def _require_project(project_id: str):
    project = repository.get_project(project_id)
    if project is None or project.deleted:
        raise HTTPException(status_code=404, detail="project not found")
    return project


@app.get("/health")
def health() -> Dict[str, str]:
    return {"status": "ok"}


@app.get("/foundation")
def foundation_info() -> Dict[str, object]:
    return {
        "config_version": config_center.get("version", "unknown"),
        "supported_languages": config_center.get("global.supportedLanguages", []),
        "dictionary_version": dictionary.dictionary_version,
        "dictionary_metrics": dictionary.metric_keys(),
        "dictionary_enums": [item.key for item in dictionary.enumDefinitions],
        "feature_flags": feature_flag_checker.get_all_flags(),
    }


@app.get("/dictionary/metrics", response_model=List[MetricDefinition])
def list_metric_definitions():
    return dictionary.metricDefinitions


@app.get("/dictionary/metrics/{metric_key}", response_model=MetricDefinition)
def get_metric_definition(metric_key: str):
    metric = dictionary.get_metric_definition(metric_key)
    if metric is None:
        raise HTTPException(status_code=404, detail="metric not found")
    return metric


@app.get("/dictionary/enums", response_model=List[EnumDefinition])
def list_enum_definitions():
    return dictionary.enumDefinitions


@app.get("/dictionary/enums/{enum_key}", response_model=EnumDefinition)
def get_enum_definition(enum_key: str):
    definition = dictionary.get_enum_definition(enum_key)
    if definition is None:
        raise HTTPException(status_code=404, detail="enum not found")
    return definition


@app.post("/projects")
def create_project(payload: ProjectCreate):
    return service.create_project(payload)


@app.get("/projects")
def list_projects(include_deleted: bool = False, page: int = 1, page_size: int = 20):
    projects = service.list_projects(include_deleted=include_deleted)
    items, total = apply_pagination(projects, page, page_size)
    return build_paginated_response(items, total, page, page_size)


@app.get("/projects/{project_id}")
def get_project(project_id: str):
    try:
        return service.get_project(project_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.patch("/projects/{project_id}")
def update_project(project_id: str, payload: ProjectUpdate):
    _require_project(project_id)
    try:
        return service.update_project(project_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.delete("/projects/{project_id}")
def delete_project(project_id: str):
    _require_project(project_id)
    try:
        return service.delete_project(project_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/engines")
def list_engines():
    return {"engines": service.supported_engines}


@app.get("/projects/{project_id}/assets")
def list_assets(project_id: str, include_deleted: bool = False, page: int = 1, page_size: int = 20):
    _require_project(project_id)
    assets = service.list_assets(project_id, include_deleted=include_deleted)
    items, total = apply_pagination(assets, page, page_size)
    return build_paginated_response(items, total, page, page_size)


@app.post("/projects/{project_id}/assets")
def create_asset(project_id: str, payload: AssetCreate):
    _require_project(project_id)
    try:
        return service.create_asset(project_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/assets/{asset_id}")
def get_asset(project_id: str, asset_id: str):
    _require_project(project_id)
    try:
        return service.get_asset(project_id, asset_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.patch("/projects/{project_id}/assets/{asset_id}")
def update_asset(project_id: str, asset_id: str, payload: AssetUpdate):
    _require_project(project_id)
    try:
        return service.update_asset(project_id, asset_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.delete("/projects/{project_id}/assets/{asset_id}")
def delete_asset(project_id: str, asset_id: str):
    _require_project(project_id)
    try:
        return service.delete_asset(project_id, asset_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/projects/{project_id}/asset-changes")
def list_asset_changes(project_id: str, asset_id: str | None = None):
    _require_project(project_id)
    return service.list_asset_changes(project_id, asset_id=asset_id)


@app.post("/projects/{project_id}/assets/sync")
def sync_assets(project_id: str, payload: AssetSyncRequest):
    _require_project(project_id)
    try:
        asset_ids = [item for item in payload.asset_ids if item]
        return service.sync_assets(project_id, force=payload.force, asset_ids=asset_ids or None)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/assets/{asset_id}/sync")
def sync_asset(project_id: str, asset_id: str, payload: AssetSyncRequest):
    _require_project(project_id)
    try:
        return service.sync_assets(project_id, force=payload.force, asset_ids=[asset_id])
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/projects/{project_id}/config", response_model=ProjectConfig)
def get_project_config(project_id: str):
    _require_project(project_id)
    return service.get_project_config(project_id)


@app.put("/projects/{project_id}/config", response_model=ProjectConfig)
def update_project_config(project_id: str, payload: ProjectConfigUpdateRequest):
    _require_project(project_id)
    return service.update_project_config(project_id, payload.config)


@app.get("/projects/{project_id}/config/effective")
def get_effective_project_config(project_id: str) -> Dict[str, Any]:
    _require_project(project_id)
    return service.get_effective_project_config(project_id)


@app.post("/projects/{project_id}/prompts/import")
def import_prompts(project_id: str, payload: PromptImportRequest):
    _require_project(project_id)
    return service.import_prompts(project_id, payload.items)


@app.post("/projects/{project_id}/prompts/generate")
def generate_prompts(project_id: str, payload: PromptGenerateRequest):
    _require_project(project_id)
    return service.generate_initial_prompts(project_id, payload.count)


@app.get("/projects/{project_id}/prompts")
def list_prompts(
    project_id: str,
    include_disabled: bool = False,
    language: str | None = None,
    region: str | None = None,
    topic: str | None = None,
    stage: str | None = None,
    enabled: bool | None = None,
    min_priority: int | None = Query(default=None, ge=1, le=5),
    max_priority: int | None = Query(default=None, ge=1, le=5),
    page: int = 1,
    page_size: int = 20,
):
    _require_project(project_id)
    prompts = service.list_prompts(
        project_id,
        include_disabled=include_disabled,
        language=language,
        region=region,
        topic=topic,
        stage=stage,
        enabled=enabled,
        min_priority=min_priority,
        max_priority=max_priority,
    )
    items, total = apply_pagination(prompts, page, page_size)
    return build_paginated_response(items, total, page, page_size)


@app.get("/projects/{project_id}/prompts/{prompt_id}")
def get_prompt(project_id: str, prompt_id: str):
    _require_project(project_id)
    try:
        return service.get_prompt(project_id, prompt_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.patch("/projects/{project_id}/prompts/{prompt_id}")
def update_prompt(project_id: str, prompt_id: str, payload: PromptUpdate):
    _require_project(project_id)
    try:
        return service.update_prompt(project_id, prompt_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/runs")
async def create_run(project_id: str, payload: RunCreateRequest, background_tasks: BackgroundTasks, async_mode: bool = Query(default=False, description="Enable async execution")):
    _require_project(project_id)
    try:
        if async_mode:
            # 异步模式：立即返回 run_id，后台执行
            run_id = uuid.uuid4().hex
            # 创建 pending 状态的 run
            from app.models import Run, RunStatus
            from datetime import datetime
            run = Run(
                run_id=run_id,
                project_id=project_id,
                run_type=payload.run_type,
                status=RunStatus.pending,
                prompt_count=0,
                engines=payload.engines or service.supported_engines[:],
                started_at=datetime.utcnow(),
            )
            service.repository.save_run(run)
            
            # 在后台执行异步任务
            background_tasks.add_task(
                _run_async_execution,
                project_id=project_id,
                run_type=payload.run_type,
                engines=payload.engines,
            )
            
            return {"run_id": run_id, "status": "pending", "message": "Run scheduled for async execution"}
        else:
            # 同步模式：使用异步执行但阻塞等待完成
            return await service.create_run_async(project_id, payload.run_type, payload.engines)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


async def _run_async_execution(project_id: str, run_type: RunType, engines: List[str]):
    """后台异步任务执行函数"""
    try:
        await service.create_run_async(project_id, run_type, engines)
    except Exception as exc:
        logger.error(f"Async run execution failed: {exc}")


@app.get("/projects/{project_id}/runs")
def list_runs(project_id: str):
    _require_project(project_id)
    return service.list_runs(project_id)


@app.get("/projects/{project_id}/runs/{run_id}")
def get_run(project_id: str, run_id: str):
    _require_project(project_id)
    try:
        return service.get_run(project_id, run_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/insights")
def generate_insights(project_id: str, payload: InsightGenerateRequest):
    _require_project(project_id)
    try:
        return service.generate_insights(project_id, payload.run_id, payload.limit)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/projects/{project_id}/insights")
def list_insights(project_id: str, page: int = 1, page_size: int = 20):
    _require_project(project_id)
    insights = service.list_insights(project_id)
    items, total = apply_pagination(insights, page, page_size)
    return build_paginated_response(items, total, page, page_size)


@app.get("/projects/{project_id}/insights/{insight_id}")
def get_insight(project_id: str, insight_id: str):
    _require_project(project_id)
    try:
        return service.get_insight(project_id, insight_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/playbooks")
def generate_playbook(project_id: str, payload: PlaybookGenerateRequest):
    _require_project(project_id)
    try:
        return service.generate_playbook(project_id, payload.insight_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/verification")
def verify(project_id: str, payload: VerificationRequest):
    _require_project(project_id)
    try:
        return service.verify_runs(project_id, payload.baseline_run_id, payload.after_run_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/monitor/{run_id}")
def monitor(project_id: str, run_id: str, language: str = "zh-CN"):
    if not feature_flag_checker.is_enabled(FeatureFlag.ENABLE_AUTO_MONITOR_REPORT, default=True):
        raise HTTPException(status_code=501, detail="Feature enableAutoMonitorReport is not enabled")
    _require_project(project_id)
    try:
        return service.build_monitor_report(project_id, run_id, language)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/projects/{project_id}/alerts")
def list_alerts(project_id: str, status: AlertStatus | None = None):
    _require_project(project_id)
    return service.list_alerts(project_id, status=status)


@app.get("/projects/{project_id}/alerts/{alert_id}")
def get_alert(project_id: str, alert_id: str):
    _require_project(project_id)
    try:
        return service.get_alert(project_id, alert_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.patch("/projects/{project_id}/alerts/{alert_id}")
def update_alert(project_id: str, alert_id: str, payload: AlertUpdate):
    _require_project(project_id)
    try:
        return service.update_alert(project_id, alert_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/strategy-memory")
def strategy_memory(project_id: str, payload: StrategyMemoryRequest):
    if not feature_flag_checker.is_enabled(FeatureFlag.ENABLE_STRATEGY_MEMORY_RANKING, default=False):
        raise HTTPException(status_code=501, detail="Feature enableStrategyMemoryRanking is not enabled")
    _require_project(project_id)
    try:
        return service.save_strategy_memory(project_id, payload.playbook_id, payload.verification_report_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/projects/{project_id}/strategy-memories")
def list_strategy_memories(project_id: str):
    _require_project(project_id)
    return repository.list_project_memories(project_id)


@app.get("/projects/{project_id}/overview")
def overview(project_id: str):
    _require_project(project_id)
    return service.project_overview(project_id)


@app.get("/projects/{project_id}/citations/sources", response_model=List[CitationSource])
def citation_sources(project_id: str, run_id: str | None = None, limit: int = Query(default=50, ge=1, le=500)):
    _require_project(project_id)
    try:
        return service.list_citation_sources(project_id, run_id=run_id, limit=limit)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/github/pr-drafts")
def create_github_pr_draft(project_id: str, payload: GitHubPRDraftCreate):
    if not feature_flag_checker.is_enabled(FeatureFlag.ENABLE_BOT_DRAFT_GENERATION, default=True):
        raise HTTPException(status_code=501, detail="Feature enableBotDraftGeneration is not enabled")
    _require_project(project_id)
    try:
        return service.create_github_pr_draft(project_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/github/pr-drafts")
def list_github_pr_drafts(project_id: str, status: GitHubPRDraftStatus | None = None):
    _require_project(project_id)
    return service.list_github_pr_drafts(project_id, status=status)


@app.get("/projects/{project_id}/github/pr-drafts/{draft_id}")
def get_github_pr_draft(project_id: str, draft_id: str):
    _require_project(project_id)
    try:
        return service.get_github_pr_draft(project_id, draft_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/github/pr-drafts/{draft_id}/approve")
def approve_github_pr_draft(project_id: str, draft_id: str, payload: GitHubPRDraftApprove):
    _require_project(project_id)
    try:
        return service.approve_github_pr_draft(project_id, draft_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/projects/{project_id}/github/pr-drafts/{draft_id}/reject")
def reject_github_pr_draft(project_id: str, draft_id: str, payload: GitHubPRDraftApprove):
    _require_project(project_id)
    try:
        return service.reject_github_pr_draft(project_id, draft_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/projects/{project_id}/stability-reports")
def create_stability_report(project_id: str, payload: StabilityReportCreate):
    _require_project(project_id)
    try:
        return service.create_stability_report(project_id, payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/stability-reports")
def list_stability_reports(project_id: str):
    _require_project(project_id)
    return service.list_stability_reports(project_id)


@app.get("/projects/{project_id}/stability-reports/{report_id}")
def get_stability_report(project_id: str, report_id: str):
    _require_project(project_id)
    try:
        return service.get_stability_report(project_id, report_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/projects/{project_id}/weekly-report")
def weekly_report(project_id: str, language: str = "zh-CN"):
    _require_project(project_id)
    return service.build_weekly_email_report(project_id, language)


@app.get("/projects/{project_id}/audit-logs")
def list_audit_logs(
    project_id: str,
    page: int = 1,
    page_size: int = 20,
    start_time: datetime | None = Query(default=None),
    end_time: datetime | None = Query(default=None),
):
    _require_project(project_id)
    if start_time is not None and end_time is not None and start_time > end_time:
        raise HTTPException(status_code=400, detail="invalid time range")
    logs = repository.list_project_audit_logs(project_id, start_time=start_time, end_time=end_time, limit=1000)
    total = repository.count_project_audit_logs(project_id, start_time=start_time, end_time=end_time)
    items, _ = apply_pagination(logs, page, page_size)
    return build_paginated_response(items, total, page, page_size)


# ==================== 调度器管理接口 ====================

@app.post("/schedules")
def create_schedule(payload: ScheduleCreateRequest):
    """创建调度任务"""
    _require_project(payload.project_id)
    
    job_id = f"run_{payload.project_id}_{uuid.uuid4().hex[:8]}"
    
    # 构建触发器参数
    trigger_kwargs = {}
    if payload.trigger_type == "cron":
        if payload.hour is not None:
            trigger_kwargs["hour"] = payload.hour
        if payload.minute is not None:
            trigger_kwargs["minute"] = payload.minute
        if payload.day_of_week is not None:
            trigger_kwargs["day_of_week"] = payload.day_of_week
        # 默认每天午夜执行
        if not trigger_kwargs:
            trigger_kwargs = {"hour": "0", "minute": "0"}
    
    elif payload.trigger_type == "interval":
        if payload.days:
            trigger_kwargs["days"] = payload.days
        if payload.hours:
            trigger_kwargs["hours"] = payload.hours
        if payload.minutes:
            trigger_kwargs["minutes"] = payload.minutes
        # 默认每天执行
        if not trigger_kwargs:
            trigger_kwargs = {"days": 1}
    
    elif payload.trigger_type == "date":
        if payload.run_date:
            trigger_kwargs["run_date"] = payload.run_date
        else:
            raise HTTPException(status_code=400, detail="run_date required for date trigger")
    
    else:
        raise HTTPException(status_code=400, detail=f"Unsupported trigger type: {payload.trigger_type}")
    
    # 添加调度任务
    try:
        from app.scheduler import schedule_project_run
        
        scheduler_manager.add_job(
            job_id=job_id,
            func=schedule_project_run,
            trigger_type=payload.trigger_type,
            kwargs={
                "project_id": payload.project_id,
                "run_type": payload.run_type,
                "engines": payload.engines,
                "service": service,
            },
            **trigger_kwargs,
        )
        
        return {
            "job_id": job_id,
            "project_id": payload.project_id,
            "trigger_type": payload.trigger_type,
            "status": "active",
            "message": "Schedule created successfully",
        }
    
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Failed to create schedule: {str(exc)}") from exc


@app.get("/schedules")
def list_schedules():
    """列出所有调度任务"""
    jobs = scheduler_manager.get_all_jobs()
    result = []
    
    for job in jobs:
        metadata = scheduler_manager.get_job_metadata(job.id)
        result.append({
            "job_id": job.id,
            "trigger": str(job.trigger),
            "next_run_time": job.next_run_time.isoformat() if job.next_run_time else None,
            "metadata": metadata,
        })
    
    return {"schedules": result, "total": len(result)}


@app.get("/schedules/{job_id}")
def get_schedule(job_id: str):
    """获取调度任务详情"""
    job = scheduler_manager.get_job(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Schedule not found")
    
    metadata = scheduler_manager.get_job_metadata(job_id)
    return {
        "job_id": job_id,
        "trigger": str(job.trigger),
        "next_run_time": job.next_run_time.isoformat() if job.next_run_time else None,
        "metadata": metadata,
    }


@app.patch("/schedules/{job_id}")
def update_schedule(job_id: str, payload: ScheduleUpdateRequest):
    """更新调度任务 (暂停/恢复)"""
    job = scheduler_manager.get_job(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Schedule not found")
    
    if payload.pause is not None:
        if payload.pause:
            scheduler_manager.pause_job(job_id)
        else:
            scheduler_manager.resume_job(job_id)
    
    return {"job_id": job_id, "status": "updated"}


@app.delete("/schedules/{job_id}")
def delete_schedule(job_id: str):
    """删除调度任务"""
    try:
        scheduler_manager.remove_job(job_id)
        return {"job_id": job_id, "status": "deleted"}
    except Exception as exc:
        raise HTTPException(status_code=404, detail=f"Failed to delete schedule: {str(exc)}") from exc


# ==================== Phase 3.3: 外部引用建设任务管理 API ====================

@app.post("/projects/{project_id}/reference-tasks")
def create_reference_task(project_id: str, payload: ReferenceTaskCreate):
    """创建外部引用建设任务"""
    _require_project(project_id)
    try:
        task = reference_manager.create_task(payload)
        return task
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/reference-tasks")
def list_reference_tasks(
    project_id: str,
    status: ReferenceTaskStatus | None = Query(default=None),
    priority: ReferenceTaskPriority | None = Query(default=None),
    reference_type: ReferenceType | None = Query(default=None),
    assigned_to: str | None = Query(default=None),
):
    """列出项目的引用建设任务"""
    _require_project(project_id)
    tasks = reference_manager.list_tasks(
        project_id=project_id,
        status=status,
        priority=priority,
        reference_type=reference_type,
        assigned_to=assigned_to,
    )
    return {"tasks": [t.dict() for t in tasks], "total": len(tasks)}


@app.get("/projects/{project_id}/reference-tasks/{task_id}")
def get_reference_task(project_id: str, task_id: str):
    """获取引用建设任务详情"""
    _require_project(project_id)
    task = reference_manager.get_task(task_id)
    if task is None or task.project_id != project_id:
        raise HTTPException(status_code=404, detail="task not found")
    return task


@app.patch("/projects/{project_id}/reference-tasks/{task_id}")
def update_reference_task(project_id: str, task_id: str, payload: ReferenceTaskUpdate):
    """更新引用建设任务"""
    _require_project(project_id)
    task = reference_manager.get_task(task_id)
    if task is None or task.project_id != project_id:
        raise HTTPException(status_code=404, detail="task not found")
    
    try:
        task = reference_manager.update_task(task_id, payload)
        return task
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.delete("/projects/{project_id}/reference-tasks/{task_id}")
def delete_reference_task(project_id: str, task_id: str):
    """删除引用建设任务"""
    _require_project(project_id)
    task = reference_manager.get_task(task_id)
    if task is None or task.project_id != project_id:
        raise HTTPException(status_code=404, detail="task not found")
    
    try:
        reference_manager.delete_task(task_id)
        return {"task_id": task_id, "status": "deleted"}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


# ==================== 外联记录 API ====================

@app.post("/projects/{project_id}/reference-tasks/{task_id}/outreach")
def create_outreach_record(project_id: str, task_id: str, payload: OutreachRecordCreate):
    """创建外联记录"""
    _require_project(project_id)
    task = reference_manager.get_task(task_id)
    if task is None or task.project_id != project_id:
        raise HTTPException(status_code=404, detail="task not found")
    
    try:
        record = reference_manager.create_outreach(payload, created_by="system")
        return record
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/reference-tasks/{task_id}/outreach")
def list_outreach_records(project_id: str, task_id: str):
    """列出任务的外联记录"""
    _require_project(project_id)
    task = reference_manager.get_task(task_id)
    if task is None or task.project_id != project_id:
        raise HTTPException(status_code=404, detail="task not found")
    
    records = reference_manager.list_outreach_records(task_id=task_id)
    return {"records": [r.dict() for r in records], "total": len(records)}


@app.patch("/projects/{project_id}/outreach/{outreach_id}")
def update_outreach_record(project_id: str, outreach_id: str, payload: OutreachRecordUpdate):
    """更新外联记录"""
    _require_project(project_id)
    record = reference_manager.get_outreach(outreach_id)
    if record is None or record.project_id != project_id:
        raise HTTPException(status_code=404, detail="record not found")
    
    try:
        record = reference_manager.update_outreach(outreach_id, payload)
        return record
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


# ==================== 引用追踪 API ====================

@app.post("/projects/{project_id}/reference-tasks/{task_id}/tracking")
def create_tracking_record(project_id: str, task_id: str, payload: ReferenceTrackingCreate):
    """创建引用追踪记录"""
    _require_project(project_id)
    task = reference_manager.get_task(task_id)
    if task is None or task.project_id != project_id:
        raise HTTPException(status_code=404, detail="task not found")
    
    try:
        tracking = reference_manager.create_tracking(payload)
        return tracking
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/reference-tasks/{task_id}/tracking")
def list_tracking_records(project_id: str, task_id: str):
    """列出任务的引用追踪记录"""
    _require_project(project_id)
    task = reference_manager.get_task(task_id)
    if task is None or task.project_id != project_id:
        raise HTTPException(status_code=404, detail="task not found")
    
    records = reference_manager.list_tracking_records(task_id=task_id)
    return {"records": [r.dict() for r in records], "total": len(records)}


@app.patch("/projects/{project_id}/tracking/{tracking_id}")
def update_tracking_status(
    project_id: str,
    tracking_id: str,
    is_active: bool = Query(default=True),
    broken: bool = Query(default=False),
):
    """更新引用追踪状态"""
    _require_project(project_id)
    tracking = reference_manager.get_tracking(tracking_id)
    if tracking is None or tracking.project_id != project_id:
        raise HTTPException(status_code=404, detail="tracking record not found")
    
    try:
        tracking = reference_manager.update_tracking_status(tracking_id, is_active, broken)
        return tracking
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


# ==================== 引用统计 API ====================

@app.get("/projects/{project_id}/reference-statistics")
def get_reference_statistics(project_id: str):
    """获取项目引用建设统计"""
    _require_project(project_id)
    stats = reference_manager.get_project_statistics(project_id)
    return stats.dict()


# ==================== Phase 3.4: 自动实验设计与效果归因 API ====================

@app.post("/projects/{project_id}/experiments")
def create_experiment(project_id: str, payload: ExperimentDesignCreate):
    """创建实验设计"""
    _require_project(project_id)
    try:
        experiment = experiment_manager.create_experiment(payload)
        return experiment
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/experiments")
def list_experiments(
    project_id: str,
    status: ExperimentStatus | None = Query(default=None),
    experiment_type: ExperimentType | None = Query(default=None),
):
    """列出项目的实验"""
    _require_project(project_id)
    experiments = experiment_manager.list_experiments(
        project_id=project_id,
        status=status,
        experiment_type=experiment_type,
    )
    return {"experiments": [e.dict() for e in experiments], "total": len(experiments)}


@app.get("/projects/{project_id}/experiments/{experiment_id}")
def get_experiment(project_id: str, experiment_id: str):
    """获取实验详情"""
    _require_project(project_id)
    experiment = experiment_manager.get_experiment(experiment_id)
    if experiment is None or experiment.project_id != project_id:
        raise HTTPException(status_code=404, detail="experiment not found")
    return experiment


@app.patch("/projects/{project_id}/experiments/{experiment_id}")
def update_experiment(project_id: str, experiment_id: str, payload: ExperimentDesignUpdate):
    """更新实验"""
    _require_project(project_id)
    experiment = experiment_manager.get_experiment(experiment_id)
    if experiment is None or experiment.project_id != project_id:
        raise HTTPException(status_code=404, detail="experiment not found")
    
    try:
        experiment = experiment_manager.update_experiment(experiment_id, payload)
        return experiment
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.delete("/projects/{project_id}/experiments/{experiment_id}")
def delete_experiment(project_id: str, experiment_id: str):
    """删除实验"""
    _require_project(project_id)
    experiment = experiment_manager.get_experiment(experiment_id)
    if experiment is None or experiment.project_id != project_id:
        raise HTTPException(status_code=404, detail="experiment not found")
    
    try:
        experiment_manager.delete_experiment(experiment_id)
        return {"experiment_id": experiment_id, "status": "deleted"}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


# ==================== 实验结果 API ====================

@app.post("/projects/{project_id}/experiments/{experiment_id}/results")
def create_experiment_result(project_id: str, experiment_id: str, variants_results: List[VariantResult]):
    """创建实验结果"""
    _require_project(project_id)
    experiment = experiment_manager.get_experiment(experiment_id)
    if experiment is None or experiment.project_id != project_id:
        raise HTTPException(status_code=404, detail="experiment not found")
    
    try:
        result = experiment_manager.create_result(experiment_id, variants_results)
        return result
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/experiments/{experiment_id}/results")
def list_experiment_results(project_id: str, experiment_id: str):
    """列出实验结果"""
    _require_project(project_id)
    experiment = experiment_manager.get_experiment(experiment_id)
    if experiment is None or experiment.project_id != project_id:
        raise HTTPException(status_code=404, detail="experiment not found")
    
    results = experiment_manager.list_results(experiment_id=experiment_id)
    return {"results": [r.dict() for r in results], "total": len(results)}


# ==================== 归因分析 API ====================

@app.post("/projects/{project_id}/attribution")
def create_attribution(project_id: str, payload: AttributionAnalysisCreate, touchpoints: List[Dict[str, Any]]):
    """创建归因分析"""
    _require_project(project_id)
    try:
        attribution = experiment_manager.create_attribution(payload, touchpoints)
        return attribution
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/attribution")
def list_attributions(project_id: str):
    """列出项目的归因分析"""
    _require_project(project_id)
    attributions = experiment_manager.list_attributions(project_id=project_id)
    return {"attributions": [a.dict() for a in attributions], "total": len(attributions)}


@app.get("/projects/{project_id}/attribution/{attribution_id}")
def get_attribution(project_id: str, attribution_id: str):
    """获取归因分析详情"""
    _require_project(project_id)
    attribution = experiment_manager.get_attribution(attribution_id)
    if attribution is None or attribution.project_id != project_id:
        raise HTTPException(status_code=404, detail="attribution not found")
    return attribution


# ==================== 自动实验建议 API ====================

@app.post("/projects/{project_id}/experiment-suggestions")
def create_experiment_suggestion(project_id: str, payload: ExperimentSuggestionCreate):
    """创建实验建议"""
    _require_project(project_id)
    try:
        suggestion = experiment_manager.create_suggestion(payload)
        return suggestion
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/experiment-suggestions")
def list_experiment_suggestions(project_id: str, include_dismissed: bool = Query(default=False)):
    """列出项目的实验建议"""
    _require_project(project_id)
    suggestions = experiment_manager.list_suggestions(
        project_id=project_id,
        include_dismissed=include_dismissed,
    )
    return {"suggestions": [s.dict() for s in suggestions], "total": len(suggestions)}


@app.post("/projects/{project_id}/experiment-suggestions/{suggestion_id}/dismiss")
def dismiss_experiment_suggestion(project_id: str, suggestion_id: str):
    """忽略实验建议"""
    _require_project(project_id)
    suggestion = experiment_manager.get_suggestion(suggestion_id)
    if suggestion is None or suggestion.project_id != project_id:
        raise HTTPException(status_code=404, detail="suggestion not found")
    
    try:
        experiment_manager.dismiss_suggestion(suggestion_id)
        return {"suggestion_id": suggestion_id, "status": "dismissed"}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/projects/{project_id}/experiment-suggestions/auto-generate")
def auto_generate_suggestions(project_id: str):
    """自动生成实验建议"""
    _require_project(project_id)
    try:
        suggestions = experiment_manager.auto_generate_suggestions(project_id)
        return {"suggestions": [s.dict() for s in suggestions], "total": len(suggestions)}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


# ==================== Phase 3.5: 跨项目策略复用与行业 Benchmark API ====================

@app.post("/strategies/templates")
def create_strategy_template(payload: StrategyTemplateCreate):
    """创建策略模板"""
    try:
        template = strategy_manager.create_template(payload, created_by="system")
        return template
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/strategies/templates")
def list_strategy_templates(
    category: StrategyCategory | None = Query(default=None),
    effectiveness: StrategyEffectiveness | None = Query(default=None),
    is_public: bool | None = Query(default=None),
    tags: str | None = Query(default=None, description="Comma-separated tags"),
):
    """列出策略模板"""
    tag_list = [t.strip() for t in tags.split(",")] if tags else None
    templates = strategy_manager.list_templates(
        category=category,
        effectiveness=effectiveness,
        is_public=is_public,
        tags=tag_list,
    )
    return {"templates": [t.dict() for t in templates], "total": len(templates)}


@app.get("/strategies/templates/{template_id}")
def get_strategy_template(template_id: str):
    """获取策略模板详情"""
    template = strategy_manager.get_template(template_id)
    if template is None:
        raise HTTPException(status_code=404, detail="template not found")
    return template


@app.patch("/strategies/templates/{template_id}")
def update_strategy_template(template_id: str, payload: StrategyTemplateUpdate):
    """更新策略模板"""
    try:
        template = strategy_manager.update_template(template_id, payload)
        if template is None:
            raise HTTPException(status_code=404, detail="template not found")
        return template
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.delete("/strategies/templates/{template_id}")
def delete_strategy_template(template_id: str):
    """删除策略模板"""
    try:
        if not strategy_manager.delete_template(template_id):
            raise HTTPException(status_code=404, detail="template not found")
        return {"template_id": template_id, "status": "deleted"}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


# ==================== 策略实例 API ====================

@app.post("/projects/{project_id}/strategies/instances")
def create_strategy_instance(project_id: str, payload: StrategyInstanceCreate):
    """创建策略实例"""
    _require_project(project_id)
    try:
        instance = strategy_manager.create_instance(payload, created_by="system")
        return instance
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/strategies/instances")
def list_strategy_instances(project_id: str, status: str | None = Query(default=None)):
    """列出项目的策略实例"""
    _require_project(project_id)
    instances = strategy_manager.list_instances(project_id=project_id, status=status)
    return {"instances": [i.dict() for i in instances], "total": len(instances)}


@app.get("/projects/{project_id}/strategies/instances/{instance_id}")
def get_strategy_instance(project_id: str, instance_id: str):
    """获取策略实例详情"""
    _require_project(project_id)
    instance = strategy_manager.get_instance(instance_id)
    if instance is None or instance.project_id != project_id:
        raise HTTPException(status_code=404, detail="instance not found")
    return instance


@app.patch("/projects/{project_id}/strategies/instances/{instance_id}")
def update_strategy_instance_status(
    project_id: str,
    instance_id: str,
    status: str = Query(..., description="active, paused, completed, failed"),
):
    """更新策略实例状态"""
    _require_project(project_id)
    instance = strategy_manager.get_instance(instance_id)
    if instance is None or instance.project_id != project_id:
        raise HTTPException(status_code=404, detail="instance not found")
    
    try:
        instance = strategy_manager.update_instance_status(instance_id, status)
        return instance
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


# ==================== 行业 Benchmark API ====================

@app.post("/benchmarks/analysis")
def analyze_project_benchmark(payload: BenchmarkAnalysisCreate, project_metrics: Dict[str, float]):
    """分析项目与行业基准的对比"""
    try:
        benchmark = strategy_manager.analyze_project_benchmark(payload, project_metrics)
        return benchmark
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/benchmark")
def get_project_benchmark(project_id: str):
    """获取项目的 Benchmark 分析"""
    _require_project(project_id)
    benchmark = strategy_manager.get_project_benchmark(project_id)
    if benchmark is None:
        raise HTTPException(status_code=404, detail="benchmark analysis not found")
    return benchmark


@app.get("/benchmarks/reports")
def list_benchmark_reports(industry: Industry | None = Query(default=None)):
    """列出行业 Benchmark 报告"""
    reports = strategy_manager.list_benchmark_reports(industry=industry)
    return {"reports": [r.dict() for r in reports], "total": len(reports)}


# ==================== 策略推荐 API ====================

@app.post("/projects/{project_id}/strategy-recommendations")
def generate_strategy_recommendations(project_id: str, industry: Industry = Query(...)):
    """生成策略推荐"""
    _require_project(project_id)
    try:
        recommendations = strategy_manager.generate_recommendations(project_id, industry)
        return {"recommendations": [r.dict() for r in recommendations], "total": len(recommendations)}
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/projects/{project_id}/strategy-recommendations")
def list_strategy_recommendations(project_id: str):
    """列出项目的策略推荐"""
    _require_project(project_id)
    recommendations = strategy_manager.get_recommendations(project_id)
    return {"recommendations": [r.dict() for r in recommendations], "total": len(recommendations)}


# ==================== 行业洞察 API ====================

@app.post("/insights/industry")
def create_industry_insight(
    industry: Industry = Query(...),
    title: str = Query(...),
    description: str = Query(...),
    trends: str = Query(default="", description="Comma-separated trends"),
    best_practices: str = Query(default="", description="Comma-separated best practices"),
):
    """创建行业洞察"""
    trend_list = [t.strip() for t in trends.split(",")] if trends else []
    practice_list = [p.strip() for p in best_practices.split(",")] if best_practices else []
    
    try:
        insight = strategy_manager.create_industry_insight(
            industry=industry,
            title=title,
            description=description,
            data_points=[],
            trends=trend_list,
            best_practices=practice_list,
        )
        return insight
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get("/insights/industry")
def list_industry_insights(industry: Industry | None = Query(default=None)):
    """列出行业洞察"""
    insights = strategy_manager.list_industry_insights(industry=industry)
    return {"insights": [i.dict() for i in insights], "total": len(insights)}


# ==================== 前端静态文件服务 ====================

# 挂载静态文件目录（前端构建产物）
import os as _os
static_dir = _os.path.join(_os.path.dirname(_os.path.dirname(__file__)), "static")
if _os.path.exists(static_dir):
    # 挂载 /assets 目录（Vite 构建产物）
    assets_dir = _os.path.join(static_dir, "assets")
    if _os.path.exists(assets_dir):
        from fastapi.staticfiles import StaticFiles as _StaticFiles
        app.mount("/assets", _StaticFiles(directory=assets_dir), name="assets")
    # 挂载 /static 目录（其他静态资源）
    app.mount("/static", _StaticFiles(directory=static_dir), name="static")


@app.get("/{full_path:path}")
async def serve_spa(full_path: str):
    """SPA 路由回退 - 为所有非 API 路由提供 index.html"""
    if _os.path.exists(static_dir):
        index_file = _os.path.join(static_dir, "index.html")
        if _os.path.exists(index_file):
            return FileResponse(index_file)
    return {"error": "Frontend not built. Run 'cd frontend && npm run build' first."}
