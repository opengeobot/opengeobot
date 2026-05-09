"""
功能：MVP 仓储实现（内存 + SQLite）
时间：2026-05-08 13:39:00
作者：AxeXie
"""

from __future__ import annotations

import sqlite3
from datetime import datetime
from pathlib import Path
from typing import Dict, Generic, List, Optional, Protocol, Type, TypeVar

from pydantic import BaseModel

from app.models import AuditLog, Insight, MonitorReport, Playbook, Project, ProjectConfig, Prompt, Run, StrategyMemory, VerificationReport

ModelT = TypeVar("ModelT", bound=BaseModel)


class Repository(Protocol):
    def save_project(self, project: Project) -> None: ...
    def get_project(self, project_id: str) -> Optional[Project]: ...
    def has_project(self, project_id: str) -> bool: ...
    def list_projects(self) -> List[Project]: ...
    def save_project_config(self, config: ProjectConfig) -> None: ...
    def get_project_config(self, project_id: str) -> Optional[ProjectConfig]: ...
    def save_prompt(self, prompt: Prompt) -> None: ...
    def get_prompt(self, prompt_id: str) -> Optional[Prompt]: ...
    def has_prompt(self, prompt_id: str) -> bool: ...
    def list_project_prompts(self, project_id: str, include_disabled: bool = False) -> List[Prompt]: ...
    def save_run(self, run: Run) -> None: ...
    def get_run(self, run_id: str) -> Optional[Run]: ...
    def has_run(self, run_id: str) -> bool: ...
    def list_project_runs(self, project_id: str) -> List[Run]: ...
    def save_insight(self, insight: Insight) -> None: ...
    def get_insight(self, insight_id: str) -> Optional[Insight]: ...
    def has_insight(self, insight_id: str) -> bool: ...
    def list_project_insights(self, project_id: str) -> List[Insight]: ...
    def save_playbook(self, playbook: Playbook) -> None: ...
    def get_playbook(self, playbook_id: str) -> Optional[Playbook]: ...
    def has_playbook(self, playbook_id: str) -> bool: ...
    def save_verification_report(self, report: VerificationReport) -> None: ...
    def get_verification_report(self, report_id: str) -> Optional[VerificationReport]: ...
    def has_verification_report(self, report_id: str) -> bool: ...
    def save_monitor_report(self, report: MonitorReport) -> None: ...
    def save_strategy_memory(self, memory: StrategyMemory) -> None: ...
    def list_project_memories(self, project_id: str) -> List[StrategyMemory]: ...
    def save_audit_log(self, audit_log: AuditLog) -> None: ...
    def list_project_audit_logs(
        self,
        project_id: str,
        *,
        start_time: datetime | None = None,
        end_time: datetime | None = None,
        limit: int = 100,
    ) -> List[AuditLog]: ...


class _MemoryTable(Generic[ModelT]):
    def __init__(self) -> None:
        self.data: Dict[str, ModelT] = {}

    def save(self, key: str, value: ModelT) -> None:
        self.data[key] = value

    def get(self, key: str) -> Optional[ModelT]:
        return self.data.get(key)

    def has(self, key: str) -> bool:
        return key in self.data

    def list(self) -> List[ModelT]:
        return list(self.data.values())


class MemoryRepository:
    def __init__(self) -> None:
        self.projects = _MemoryTable[Project]()
        self.project_configs = _MemoryTable[ProjectConfig]()
        self.prompts = _MemoryTable[Prompt]()
        self.runs = _MemoryTable[Run]()
        self.insights = _MemoryTable[Insight]()
        self.playbooks = _MemoryTable[Playbook]()
        self.verification_reports = _MemoryTable[VerificationReport]()
        self.monitor_reports = _MemoryTable[MonitorReport]()
        self.strategy_memories = _MemoryTable[StrategyMemory]()
        self.audit_logs = _MemoryTable[AuditLog]()

    def save_project(self, project: Project) -> None:
        self.projects.save(project.project_id, project)

    def get_project(self, project_id: str) -> Optional[Project]:
        return self.projects.get(project_id)

    def has_project(self, project_id: str) -> bool:
        return self.projects.has(project_id)

    def list_projects(self) -> List[Project]:
        return self.projects.list()

    def save_project_config(self, config: ProjectConfig) -> None:
        self.project_configs.save(config.project_id, config)

    def get_project_config(self, project_id: str) -> Optional[ProjectConfig]:
        return self.project_configs.get(project_id)

    def save_prompt(self, prompt: Prompt) -> None:
        self.prompts.save(prompt.prompt_id, prompt)

    def get_prompt(self, prompt_id: str) -> Optional[Prompt]:
        return self.prompts.get(prompt_id)

    def has_prompt(self, prompt_id: str) -> bool:
        return self.prompts.has(prompt_id)

    def list_project_prompts(self, project_id: str, include_disabled: bool = False) -> List[Prompt]:
        items = [item for item in self.prompts.list() if item.project_id == project_id]
        if include_disabled:
            return items
        return [item for item in items if item.enabled]

    def save_run(self, run: Run) -> None:
        self.runs.save(run.run_id, run)

    def get_run(self, run_id: str) -> Optional[Run]:
        return self.runs.get(run_id)

    def has_run(self, run_id: str) -> bool:
        return self.runs.has(run_id)

    def list_project_runs(self, project_id: str) -> List[Run]:
        return [item for item in self.runs.list() if item.project_id == project_id]

    def save_insight(self, insight: Insight) -> None:
        self.insights.save(insight.insight_id, insight)

    def get_insight(self, insight_id: str) -> Optional[Insight]:
        return self.insights.get(insight_id)

    def has_insight(self, insight_id: str) -> bool:
        return self.insights.has(insight_id)

    def list_project_insights(self, project_id: str) -> List[Insight]:
        return [item for item in self.insights.list() if item.project_id == project_id]

    def save_playbook(self, playbook: Playbook) -> None:
        self.playbooks.save(playbook.playbook_id, playbook)

    def get_playbook(self, playbook_id: str) -> Optional[Playbook]:
        return self.playbooks.get(playbook_id)

    def has_playbook(self, playbook_id: str) -> bool:
        return self.playbooks.has(playbook_id)

    def save_verification_report(self, report: VerificationReport) -> None:
        self.verification_reports.save(report.report_id, report)

    def get_verification_report(self, report_id: str) -> Optional[VerificationReport]:
        return self.verification_reports.get(report_id)

    def has_verification_report(self, report_id: str) -> bool:
        return self.verification_reports.has(report_id)

    def save_monitor_report(self, report: MonitorReport) -> None:
        self.monitor_reports.save(report.report_id, report)

    def save_strategy_memory(self, memory: StrategyMemory) -> None:
        self.strategy_memories.save(memory.memory_id, memory)

    def list_project_memories(self, project_id: str) -> List[StrategyMemory]:
        return [item for item in self.strategy_memories.list() if item.project_id == project_id]

    def save_audit_log(self, audit_log: AuditLog) -> None:
        self.audit_logs.save(audit_log.auditId, audit_log)

    def list_project_audit_logs(
        self,
        project_id: str,
        *,
        start_time: datetime | None = None,
        end_time: datetime | None = None,
        limit: int = 100,
    ) -> List[AuditLog]:
        filtered = [item for item in self.audit_logs.list() if item.projectId == project_id]
        if start_time is not None:
            filtered = [item for item in filtered if item.timestamp >= start_time]
        if end_time is not None:
            filtered = [item for item in filtered if item.timestamp <= end_time]
        filtered.sort(key=lambda item: item.timestamp, reverse=True)
        return filtered[: max(limit, 0)]


class SQLiteRepository:
    def __init__(self, db_path: str) -> None:
        db_file = Path(db_path)
        db_file.parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(str(db_file), check_same_thread=False)
        self.connection.execute("PRAGMA journal_mode=WAL;")
        self._init_schema()

    def _init_schema(self) -> None:
        with self.connection:
            self.connection.execute(
                """
                CREATE TABLE IF NOT EXISTS records (
                    table_name TEXT NOT NULL,
                    id TEXT NOT NULL,
                    project_id TEXT,
                    payload TEXT NOT NULL,
                    PRIMARY KEY (table_name, id)
                )
                """
            )
            self.connection.execute("CREATE INDEX IF NOT EXISTS idx_records_project ON records(table_name, project_id)")

    def _save(self, table_name: str, record_id: str, payload: BaseModel, project_id: Optional[str] = None) -> None:
        with self.connection:
            self.connection.execute(
                """
                INSERT INTO records(table_name, id, project_id, payload)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(table_name, id) DO UPDATE SET
                    project_id=excluded.project_id,
                    payload=excluded.payload
                """,
                (table_name, record_id, project_id, payload.model_dump_json()),
            )

    def _get(self, table_name: str, record_id: str, model_type: Type[ModelT]) -> Optional[ModelT]:
        cursor = self.connection.execute(
            "SELECT payload FROM records WHERE table_name = ? AND id = ?",
            (table_name, record_id),
        )
        row = cursor.fetchone()
        if row is None:
            return None
        return model_type.model_validate_json(row[0])

    def _has(self, table_name: str, record_id: str) -> bool:
        cursor = self.connection.execute(
            "SELECT 1 FROM records WHERE table_name = ? AND id = ?",
            (table_name, record_id),
        )
        return cursor.fetchone() is not None

    def _list(self, table_name: str, model_type: Type[ModelT], project_id: Optional[str] = None) -> List[ModelT]:
        if project_id is None:
            cursor = self.connection.execute(
                "SELECT payload FROM records WHERE table_name = ?",
                (table_name,),
            )
        else:
            cursor = self.connection.execute(
                "SELECT payload FROM records WHERE table_name = ? AND project_id = ?",
                (table_name, project_id),
            )
        return [model_type.model_validate_json(row[0]) for row in cursor.fetchall()]

    def save_project(self, project: Project) -> None:
        self._save("projects", project.project_id, project, project.project_id)

    def get_project(self, project_id: str) -> Optional[Project]:
        return self._get("projects", project_id, Project)

    def has_project(self, project_id: str) -> bool:
        return self._has("projects", project_id)

    def list_projects(self) -> List[Project]:
        return self._list("projects", Project)

    def save_project_config(self, config: ProjectConfig) -> None:
        self._save("project_configs", config.project_id, config, config.project_id)

    def get_project_config(self, project_id: str) -> Optional[ProjectConfig]:
        return self._get("project_configs", project_id, ProjectConfig)

    def save_prompt(self, prompt: Prompt) -> None:
        self._save("prompts", prompt.prompt_id, prompt, prompt.project_id)

    def get_prompt(self, prompt_id: str) -> Optional[Prompt]:
        return self._get("prompts", prompt_id, Prompt)

    def has_prompt(self, prompt_id: str) -> bool:
        return self._has("prompts", prompt_id)

    def list_project_prompts(self, project_id: str, include_disabled: bool = False) -> List[Prompt]:
        items = self._list("prompts", Prompt, project_id)
        if include_disabled:
            return items
        return [item for item in items if item.enabled]

    def save_run(self, run: Run) -> None:
        self._save("runs", run.run_id, run, run.project_id)

    def get_run(self, run_id: str) -> Optional[Run]:
        return self._get("runs", run_id, Run)

    def has_run(self, run_id: str) -> bool:
        return self._has("runs", run_id)

    def list_project_runs(self, project_id: str) -> List[Run]:
        return self._list("runs", Run, project_id)

    def save_insight(self, insight: Insight) -> None:
        self._save("insights", insight.insight_id, insight, insight.project_id)

    def get_insight(self, insight_id: str) -> Optional[Insight]:
        return self._get("insights", insight_id, Insight)

    def has_insight(self, insight_id: str) -> bool:
        return self._has("insights", insight_id)

    def list_project_insights(self, project_id: str) -> List[Insight]:
        return self._list("insights", Insight, project_id)

    def save_playbook(self, playbook: Playbook) -> None:
        self._save("playbooks", playbook.playbook_id, playbook, playbook.project_id)

    def get_playbook(self, playbook_id: str) -> Optional[Playbook]:
        return self._get("playbooks", playbook_id, Playbook)

    def has_playbook(self, playbook_id: str) -> bool:
        return self._has("playbooks", playbook_id)

    def save_verification_report(self, report: VerificationReport) -> None:
        self._save("verification_reports", report.report_id, report, report.project_id)

    def get_verification_report(self, report_id: str) -> Optional[VerificationReport]:
        return self._get("verification_reports", report_id, VerificationReport)

    def has_verification_report(self, report_id: str) -> bool:
        return self._has("verification_reports", report_id)

    def save_monitor_report(self, report: MonitorReport) -> None:
        self._save("monitor_reports", report.report_id, report, report.project_id)

    def save_strategy_memory(self, memory: StrategyMemory) -> None:
        self._save("strategy_memories", memory.memory_id, memory, memory.project_id)

    def list_project_memories(self, project_id: str) -> List[StrategyMemory]:
        return self._list("strategy_memories", StrategyMemory, project_id)

    def save_audit_log(self, audit_log: AuditLog) -> None:
        self._save("audit_logs", audit_log.auditId, audit_log, audit_log.projectId)

    def list_project_audit_logs(
        self,
        project_id: str,
        *,
        start_time: datetime | None = None,
        end_time: datetime | None = None,
        limit: int = 100,
    ) -> List[AuditLog]:
        items = self._list("audit_logs", AuditLog, project_id)
        if start_time is not None:
            items = [item for item in items if item.timestamp >= start_time]
        if end_time is not None:
            items = [item for item in items if item.timestamp <= end_time]
        items.sort(key=lambda item: item.timestamp, reverse=True)
        return items[: max(limit, 0)]


class PostgreSQLRepository:
    def __init__(self, dsn: str) -> None:
        try:
            import psycopg
        except ModuleNotFoundError as exc:
            raise RuntimeError("psycopg is required for PostgreSQLRepository") from exc
        self.psycopg = psycopg
        self.dsn = dsn
        self._init_schema()

    def _connection(self):
        return self.psycopg.connect(self.dsn, autocommit=True)

    def _init_schema(self) -> None:
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    CREATE TABLE IF NOT EXISTS records (
                        table_name TEXT NOT NULL,
                        id TEXT NOT NULL,
                        project_id TEXT,
                        payload JSONB NOT NULL,
                        PRIMARY KEY (table_name, id)
                    )
                    """
                )
                cursor.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_records_project
                    ON records(table_name, project_id)
                    """
                )

    def _save(self, table_name: str, record_id: str, payload: BaseModel, project_id: Optional[str] = None) -> None:
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO records(table_name, id, project_id, payload)
                    VALUES(%s, %s, %s, %s::jsonb)
                    ON CONFLICT(table_name, id) DO UPDATE SET
                        project_id = EXCLUDED.project_id,
                        payload = EXCLUDED.payload
                    """,
                    (table_name, record_id, project_id, payload.model_dump_json()),
                )

    def _get(self, table_name: str, record_id: str, model_type: Type[ModelT]) -> Optional[ModelT]:
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    "SELECT payload FROM records WHERE table_name = %s AND id = %s",
                    (table_name, record_id),
                )
                row = cursor.fetchone()
        if row is None:
            return None
        return model_type.model_validate(row[0])

    def _has(self, table_name: str, record_id: str) -> bool:
        with self._connection() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    "SELECT 1 FROM records WHERE table_name = %s AND id = %s",
                    (table_name, record_id),
                )
                return cursor.fetchone() is not None

    def _list(self, table_name: str, model_type: Type[ModelT], project_id: Optional[str] = None) -> List[ModelT]:
        with self._connection() as connection:
            with connection.cursor() as cursor:
                if project_id is None:
                    cursor.execute(
                        "SELECT payload FROM records WHERE table_name = %s",
                        (table_name,),
                    )
                else:
                    cursor.execute(
                        "SELECT payload FROM records WHERE table_name = %s AND project_id = %s",
                        (table_name, project_id),
                    )
                rows = cursor.fetchall()
        return [model_type.model_validate(row[0]) for row in rows]

    def save_project(self, project: Project) -> None:
        self._save("projects", project.project_id, project, project.project_id)

    def get_project(self, project_id: str) -> Optional[Project]:
        return self._get("projects", project_id, Project)

    def has_project(self, project_id: str) -> bool:
        return self._has("projects", project_id)

    def list_projects(self) -> List[Project]:
        return self._list("projects", Project)

    def save_project_config(self, config: ProjectConfig) -> None:
        self._save("project_configs", config.project_id, config, config.project_id)

    def get_project_config(self, project_id: str) -> Optional[ProjectConfig]:
        return self._get("project_configs", project_id, ProjectConfig)

    def save_prompt(self, prompt: Prompt) -> None:
        self._save("prompts", prompt.prompt_id, prompt, prompt.project_id)

    def get_prompt(self, prompt_id: str) -> Optional[Prompt]:
        return self._get("prompts", prompt_id, Prompt)

    def has_prompt(self, prompt_id: str) -> bool:
        return self._has("prompts", prompt_id)

    def list_project_prompts(self, project_id: str, include_disabled: bool = False) -> List[Prompt]:
        items = self._list("prompts", Prompt, project_id)
        if include_disabled:
            return items
        return [item for item in items if item.enabled]

    def save_run(self, run: Run) -> None:
        self._save("runs", run.run_id, run, run.project_id)

    def get_run(self, run_id: str) -> Optional[Run]:
        return self._get("runs", run_id, Run)

    def has_run(self, run_id: str) -> bool:
        return self._has("runs", run_id)

    def list_project_runs(self, project_id: str) -> List[Run]:
        return self._list("runs", Run, project_id)

    def save_insight(self, insight: Insight) -> None:
        self._save("insights", insight.insight_id, insight, insight.project_id)

    def get_insight(self, insight_id: str) -> Optional[Insight]:
        return self._get("insights", insight_id, Insight)

    def has_insight(self, insight_id: str) -> bool:
        return self._has("insights", insight_id)

    def list_project_insights(self, project_id: str) -> List[Insight]:
        return self._list("insights", Insight, project_id)

    def save_playbook(self, playbook: Playbook) -> None:
        self._save("playbooks", playbook.playbook_id, playbook, playbook.project_id)

    def get_playbook(self, playbook_id: str) -> Optional[Playbook]:
        return self._get("playbooks", playbook_id, Playbook)

    def has_playbook(self, playbook_id: str) -> bool:
        return self._has("playbooks", playbook_id)

    def save_verification_report(self, report: VerificationReport) -> None:
        self._save("verification_reports", report.report_id, report, report.project_id)

    def get_verification_report(self, report_id: str) -> Optional[VerificationReport]:
        return self._get("verification_reports", report_id, VerificationReport)

    def has_verification_report(self, report_id: str) -> bool:
        return self._has("verification_reports", report_id)

    def save_monitor_report(self, report: MonitorReport) -> None:
        self._save("monitor_reports", report.report_id, report, report.project_id)

    def save_strategy_memory(self, memory: StrategyMemory) -> None:
        self._save("strategy_memories", memory.memory_id, memory, memory.project_id)

    def list_project_memories(self, project_id: str) -> List[StrategyMemory]:
        return self._list("strategy_memories", StrategyMemory, project_id)

    def save_audit_log(self, audit_log: AuditLog) -> None:
        self._save("audit_logs", audit_log.auditId, audit_log, audit_log.projectId)

    def list_project_audit_logs(
        self,
        project_id: str,
        *,
        start_time: datetime | None = None,
        end_time: datetime | None = None,
        limit: int = 100,
    ) -> List[AuditLog]:
        items = self._list("audit_logs", AuditLog, project_id)
        if start_time is not None:
            items = [item for item in items if item.timestamp >= start_time]
        if end_time is not None:
            items = [item for item in items if item.timestamp <= end_time]
        items.sort(key=lambda item: item.timestamp, reverse=True)
        return items[: max(limit, 0)]
