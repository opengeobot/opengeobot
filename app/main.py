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
from app.models import AuditLog, ProjectConfig, ProjectCreate, PromptImportItem, RunType
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
    engines: List[str] = Field(default_factory=lambda: ["engine_alpha", "engine_beta"])


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
def list_projects():
    return service.list_projects()


@app.get("/projects/{project_id}/config", response_model=ProjectConfig)
def get_project_config(project_id: str):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    return service.get_project_config(project_id)


@app.put("/projects/{project_id}/config", response_model=ProjectConfig)
def update_project_config(project_id: str, payload: ProjectConfigUpdateRequest):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    return service.update_project_config(project_id, payload.config)


@app.get("/projects/{project_id}/config/effective")
def get_effective_project_config(project_id: str) -> Dict[str, Any]:
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    return service.get_effective_project_config(project_id)


@app.post("/projects/{project_id}/prompts/import")
def import_prompts(project_id: str, payload: PromptImportRequest):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    return service.import_prompts(project_id, payload.items)


@app.post("/projects/{project_id}/prompts/generate")
def generate_prompts(project_id: str, payload: PromptGenerateRequest):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    return service.generate_initial_prompts(project_id, payload.count)


@app.post("/projects/{project_id}/runs")
def create_run(project_id: str, payload: RunCreateRequest):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    try:
        return service.create_run(project_id, payload.run_type, payload.engines)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/projects/{project_id}/insights")
def generate_insights(project_id: str, payload: InsightGenerateRequest):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    if not repository.has_run(payload.run_id):
        raise HTTPException(status_code=404, detail="run not found")
    return service.generate_insights(project_id, payload.run_id, payload.limit)


@app.post("/projects/{project_id}/playbooks")
def generate_playbook(project_id: str, payload: PlaybookGenerateRequest):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    if not repository.has_insight(payload.insight_id):
        raise HTTPException(status_code=404, detail="insight not found")
    return service.generate_playbook(project_id, payload.insight_id)


@app.post("/projects/{project_id}/verification")
def verify(project_id: str, payload: VerificationRequest):
    if not repository.has_run(payload.baseline_run_id) or not repository.has_run(payload.after_run_id):
        raise HTTPException(status_code=404, detail="run not found")
    return service.verify_runs(project_id, payload.baseline_run_id, payload.after_run_id)


@app.post("/projects/{project_id}/monitor/{run_id}")
def monitor(project_id: str, run_id: str, language: str = "zh-CN"):
    if not repository.has_run(run_id):
        raise HTTPException(status_code=404, detail="run not found")
    return service.build_monitor_report(project_id, run_id, language)


@app.post("/projects/{project_id}/strategy-memory")
def strategy_memory(project_id: str, payload: StrategyMemoryRequest):
    if not repository.has_playbook(payload.playbook_id):
        raise HTTPException(status_code=404, detail="playbook not found")
    if not repository.has_verification_report(payload.verification_report_id):
        raise HTTPException(status_code=404, detail="verification report not found")
    return service.save_strategy_memory(project_id, payload.playbook_id, payload.verification_report_id)


@app.get("/projects/{project_id}/overview")
def overview(project_id: str):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    return service.project_overview(project_id)


@app.get("/projects/{project_id}/weekly-report")
def weekly_report(project_id: str, language: str = "zh-CN"):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    return service.build_weekly_email_report(project_id, language)


@app.get("/projects/{project_id}/audit-logs", response_model=List[AuditLog])
def list_audit_logs(
    project_id: str,
    limit: int = Query(default=100, ge=1, le=1000),
    start_time: datetime | None = Query(default=None),
    end_time: datetime | None = Query(default=None),
):
    if not repository.has_project(project_id):
        raise HTTPException(status_code=404, detail="project not found")
    if start_time is not None and end_time is not None and start_time > end_time:
        raise HTTPException(status_code=400, detail="invalid time range")
    return repository.list_project_audit_logs(project_id, start_time=start_time, end_time=end_time, limit=limit)
