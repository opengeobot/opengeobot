"""
功能：OpenGEO Bot MVP FastAPI 入口
时间：2026-05-08 13:33:00
作者：AxeXie
"""

from __future__ import annotations

import os
import uuid
from datetime import datetime
from typing import Any, Dict, List

from fastapi import FastAPI, HTTPException, Query, Request
from pydantic import BaseModel, Field

from app.foundation import (
    ConfigCenter,
    DataDictionary,
    EnumDefinition,
    I18N,
    MetricDefinition,
    build_logger,
    reset_operator,
    reset_trace_id,
    set_operator,
    set_trace_id,
)
from app.models import AssetCreate, AssetUpdate, AuditLog, ProjectConfig, ProjectCreate, ProjectUpdate, PromptImportItem, PromptUpdate, RunType
from app.repository import MemoryRepository, PostgreSQLRepository, SQLiteRepository
from app.services import OpenGeoBotService

app = FastAPI(title="OpenGEO Bot MVP", version="1.0.0")

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

    operator = request.headers.get("x-operator") or request.headers.get("x-user") or request.headers.get("x-user-id")
    if operator:
        operator = operator.strip()
    if not operator:
        operator = "system"

    trace_token = set_trace_id(trace_id)
    operator_token = set_operator(operator)
    try:
        response = await call_next(request)
        response.headers["X-Trace-Id"] = trace_id
        return response
    finally:
        reset_trace_id(trace_token)
        reset_operator(operator_token)


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
def list_projects(include_deleted: bool = False):
    return service.list_projects(include_deleted=include_deleted)


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
def list_assets(project_id: str, include_deleted: bool = False):
    _require_project(project_id)
    return service.list_assets(project_id, include_deleted=include_deleted)


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
def list_prompts(project_id: str, include_disabled: bool = False):
    _require_project(project_id)
    return service.list_prompts(project_id, include_disabled=include_disabled)


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
def create_run(project_id: str, payload: RunCreateRequest):
    _require_project(project_id)
    try:
        return service.create_run(project_id, payload.run_type, payload.engines)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


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
def list_insights(project_id: str):
    _require_project(project_id)
    return service.list_insights(project_id)


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
    _require_project(project_id)
    try:
        return service.build_monitor_report(project_id, run_id, language)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.post("/projects/{project_id}/strategy-memory")
def strategy_memory(project_id: str, payload: StrategyMemoryRequest):
    _require_project(project_id)
    try:
        return service.save_strategy_memory(project_id, payload.playbook_id, payload.verification_report_id)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


@app.get("/projects/{project_id}/overview")
def overview(project_id: str):
    _require_project(project_id)
    return service.project_overview(project_id)


@app.get("/projects/{project_id}/weekly-report")
def weekly_report(project_id: str, language: str = "zh-CN"):
    _require_project(project_id)
    return service.build_weekly_email_report(project_id, language)


@app.get("/projects/{project_id}/audit-logs", response_model=List[AuditLog])
def list_audit_logs(
    project_id: str,
    limit: int = Query(default=100, ge=1, le=1000),
    start_time: datetime | None = Query(default=None),
    end_time: datetime | None = Query(default=None),
):
    _require_project(project_id)
    if start_time is not None and end_time is not None and start_time > end_time:
        raise HTTPException(status_code=400, detail="invalid time range")
    return repository.list_project_audit_logs(project_id, start_time=start_time, end_time=end_time, limit=limit)
