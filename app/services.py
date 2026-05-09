"""
功能：OpenGEO Bot MVP 业务服务
时间：2026-05-08 13:29:00
作者：AxeXie
"""

from __future__ import annotations

import logging
import uuid
from datetime import datetime
from typing import Any, Dict, List

from app.engines import EngineContext, EngineManager, EngineRequest, MockEngineAdapter, TemplateEngineAdapter
from app.foundation import ConfigCenter, DataDictionary, I18N, deep_merge_config, get_operator, get_trace_id, log_with_context
from app.models import (
    AuditLog,
    EngineResult,
    Insight,
    Metrics,
    MonitorReport,
    Playbook,
    Project,
    ProjectConfig,
    ProjectCreate,
    ProjectUpdate,
    Prompt,
    PromptImportItem,
    PromptRunResult,
    PromptUpdate,
    Run,
    RunStatus,
    RunType,
    Sentiment,
    StrategyMemory,
    VerificationReport,
)
from app.parser import PARSER_RULE_VERSION, build_engine_result
from app.repository import Repository


def _is_sensitive_key(key: str) -> bool:
    key_lower = key.lower()
    return any(
        token in key_lower
        for token in (
            "password",
            "passwd",
            "secret",
            "token",
            "api_key",
            "apikey",
            "access_key",
            "private_key",
            "dsn",
            "credential",
        )
    )


def sanitize_config(value: Any) -> Any:
    if isinstance(value, dict):
        sanitized: Dict[str, Any] = {}
        for key, item in value.items():
            if _is_sensitive_key(str(key)):
                continue
            sanitized[str(key)] = sanitize_config(item)
        return sanitized
    if isinstance(value, list):
        return [sanitize_config(item) for item in value]
    return value


class OpenGeoBotService:
    def __init__(
        self,
        repository: Repository,
        config_center: ConfigCenter,
        dictionary: DataDictionary,
        i18n: I18N,
        logger: logging.Logger,
    ) -> None:
        self.repository = repository
        self.config_center = config_center
        self.dictionary = dictionary
        self.i18n = i18n
        self.logger = logger
        timeout_ms = self.config_center.get("global.engine.timeoutMs", 45000)
        max_retries = self.config_center.get("global.engine.maxRetries", 2)
        retry_backoff_ms = self.config_center.get("global.engine.retryBackoffMs", 1500)
        rate_limit_per_minute = self.config_center.get("global.engine.rateLimitPerMinute", 60)
        cache_ttl_seconds = self.config_center.get("global.engine.cacheTtlSeconds", 300)

        configured_engines = self.config_center.get("global.engine.adapters")
        if isinstance(configured_engines, list) and configured_engines:
            engine_ids = [str(item) for item in configured_engines if str(item).strip()]
        else:
            engine_ids = ["engine_alpha", "engine_beta"]

        adapters: Dict[str, Any] = {}
        for engine_id in engine_ids:
            if engine_id in ("engine_beta", "template"):
                adapters[engine_id] = TemplateEngineAdapter(engine_id)
            else:
                adapters[engine_id] = MockEngineAdapter(engine_id)

        self.engine_manager = EngineManager(
            adapters=adapters,
            timeout_ms=timeout_ms,
            max_retries=max_retries,
            retry_backoff_ms=retry_backoff_ms,
            rate_limit_per_minute=rate_limit_per_minute,
            cache_ttl_seconds=cache_ttl_seconds,
        )
        self.supported_engines = self.engine_manager.supported_engines()

    def _write_audit_log(
        self,
        *,
        project_id: str,
        module: str,
        event: str,
        message: str,
        run_id: str | None = None,
        prompt_id: str | None = None,
        engine: str | None = None,
        region: str | None = None,
        language: str | None = None,
        duration_ms: int | None = None,
        error_code: str | None = None,
        retry_count: int | None = None,
        attributes: Dict[str, Any] | None = None,
    ) -> AuditLog:
        trace_id = get_trace_id() or uuid.uuid4().hex
        operator = get_operator() or "system"
        audit_log = AuditLog(
            auditId=uuid.uuid4().hex,
            traceId=trace_id,
            projectId=project_id,
            runId=run_id,
            operator=operator,
            module=module,
            event=event,
            level="INFO",
            timestamp=datetime.utcnow(),
            message=message,
            promptId=prompt_id,
            engine=engine,
            region=region,
            language=language,
            durationMs=duration_ms,
            errorCode=error_code,
            retryCount=retry_count,
            attributes=attributes or {},
        )
        self.repository.save_audit_log(audit_log)
        return audit_log

    def create_project(self, payload: ProjectCreate) -> Project:
        project_id = uuid.uuid4().hex
        project = Project(
            project_id=project_id,
            project_name=payload.project_name,
            project_type=payload.project_type,
            source_url=payload.source_url,
            brand_name=payload.brand_name,
            aliases=payload.aliases,
            language=payload.language,
            region=payload.region,
            competitors=payload.competitors,
            created_at=datetime.utcnow(),
        )
        self.repository.save_project(project)
        self._write_audit_log(
            project_id=project_id,
            module="project",
            event="project.created",
            message="project created",
            attributes={
                "projectName": payload.project_name,
                "projectType": payload.project_type.value,
                "language": payload.language,
                "region": payload.region,
            },
        )
        log_with_context(
            self.logger,
            logging.INFO,
            "project created",
            project_id=project_id,
            module="project",
            event="project.created",
        )
        return project

    def get_project(self, project_id: str) -> Project:
        project = self.repository.get_project(project_id)
        if project is None or project.deleted:
            raise ValueError("project not found")
        return project

    def list_projects(self, include_deleted: bool = False) -> List[Project]:
        items = self.repository.list_projects()
        if include_deleted:
            return items
        return [item for item in items if not item.deleted]

    def update_project(self, project_id: str, payload: ProjectUpdate) -> Project:
        project = self.get_project(project_id)
        changed: Dict[str, Any] = {}
        if payload.project_name is not None:
            project.project_name = payload.project_name
            changed["projectName"] = payload.project_name
        if payload.source_url is not None:
            project.source_url = payload.source_url
            changed["sourceUrl"] = payload.source_url
        if payload.brand_name is not None:
            project.brand_name = payload.brand_name
            changed["brandName"] = payload.brand_name
        if payload.aliases is not None:
            project.aliases = payload.aliases
            changed["aliases"] = payload.aliases
        if payload.language is not None:
            project.language = payload.language
            changed["language"] = payload.language
        if payload.region is not None:
            project.region = payload.region
            changed["region"] = payload.region
        if payload.competitors is not None:
            project.competitors = payload.competitors
            changed["competitors"] = payload.competitors
        self.repository.save_project(project)
        if changed:
            self._write_audit_log(
                project_id=project.project_id,
                module="project",
                event="project.updated",
                message="project updated",
                attributes=changed,
            )
        return project

    def delete_project(self, project_id: str) -> Project:
        project = self.get_project(project_id)
        project.deleted = True
        project.deleted_at = datetime.utcnow()
        self.repository.save_project(project)
        self._write_audit_log(
            project_id=project.project_id,
            module="project",
            event="project.deleted",
            message="project deleted",
            attributes={"deletedAt": project.deleted_at.isoformat()},
        )
        return project

    def get_project_config(self, project_id: str) -> ProjectConfig:
        config = self.repository.get_project_config(project_id)
        if config is None:
            return ProjectConfig(project_id=project_id)
        return config

    def update_project_config(self, project_id: str, config: Dict[str, Any]) -> ProjectConfig:
        stored = ProjectConfig(project_id=project_id, config=config, updated_at=datetime.utcnow())
        self.repository.save_project_config(stored)
        return stored

    def get_effective_project_config(self, project_id: str) -> Dict[str, Any]:
        project_config = self.repository.get_project_config(project_id)
        base = self.config_center.config
        if project_config is None or not project_config.config:
            return sanitize_config(base)
        effective = deep_merge_config(base, project_config.config)
        return sanitize_config(effective)

    def import_prompts(self, project_id: str, items: List[PromptImportItem]) -> List[Prompt]:
        prompts: List[Prompt] = []
        for item in items:
            prompt_id = uuid.uuid4().hex
            prompt = Prompt(
                prompt_id=prompt_id,
                project_id=project_id,
                content=item.content,
                language=item.language,
                region=item.region,
                topic=item.topic,
                stage=item.stage,
                priority=item.priority,
            )
            self.repository.save_prompt(prompt)
            prompts.append(prompt)
        self._write_audit_log(
            project_id=project_id,
            module="prompt",
            event="prompt.imported",
            message=f"prompts imported count={len(prompts)}",
            attributes={"count": len(prompts)},
        )
        log_with_context(
            self.logger,
            logging.INFO,
            f"prompts imported count={len(prompts)}",
            project_id=project_id,
            module="prompt",
            event="prompt.imported",
        )
        return prompts

    def list_prompts(self, project_id: str, *, include_disabled: bool = False) -> List[Prompt]:
        return self.repository.list_project_prompts(project_id, include_disabled=include_disabled)

    def get_prompt(self, project_id: str, prompt_id: str) -> Prompt:
        prompt = self.repository.get_prompt(prompt_id)
        if prompt is None or prompt.project_id != project_id:
            raise ValueError("prompt not found")
        return prompt

    def update_prompt(self, project_id: str, prompt_id: str, payload: PromptUpdate) -> Prompt:
        prompt = self.get_prompt(project_id, prompt_id)
        changed = False
        if payload.content is not None:
            prompt.content = payload.content
            changed = True
        if payload.language is not None:
            prompt.language = payload.language
            changed = True
        if payload.region is not None:
            prompt.region = payload.region
            changed = True
        if payload.topic is not None:
            prompt.topic = payload.topic
            changed = True
        if payload.stage is not None:
            prompt.stage = payload.stage
            changed = True
        if payload.priority is not None:
            prompt.priority = payload.priority
            changed = True
        if payload.enabled is not None:
            prompt.enabled = payload.enabled
            changed = True
        if changed:
            prompt.version += 1
            self.repository.save_prompt(prompt)
            self._write_audit_log(
                project_id=project_id,
                module="prompt",
                event="prompt.updated",
                message="prompt updated",
                prompt_id=prompt.prompt_id,
                attributes={"version": prompt.version, "enabled": prompt.enabled},
            )
        return prompt

    def list_runs(self, project_id: str) -> List[Run]:
        items = self.repository.list_project_runs(project_id)
        items.sort(key=lambda item: item.started_at, reverse=True)
        return items

    def get_run(self, project_id: str, run_id: str) -> Run:
        run = self.repository.get_run(run_id)
        if run is None or run.project_id != project_id:
            raise ValueError("run not found")
        return run

    def list_insights(self, project_id: str) -> List[Insight]:
        items = self.repository.list_project_insights(project_id)
        items.sort(key=lambda item: item.priority_score, reverse=True)
        return items

    def get_insight(self, project_id: str, insight_id: str) -> Insight:
        insight = self.repository.get_insight(insight_id)
        if insight is None or insight.project_id != project_id:
            raise ValueError("insight not found")
        return insight

    def generate_initial_prompts(self, project_id: str, count: int = 20) -> List[Prompt]:
        project = self.repository.get_project(project_id)
        if project is None:
            raise ValueError("project not found")
        templates = [
            f"{project.brand_name} 是什么？",
            f"如何快速开始使用 {project.brand_name}？",
            f"{project.brand_name} 与竞品有什么区别？",
            f"{project.brand_name} 是否支持企业级场景？",
            f"{project.brand_name} 的最佳实践有哪些？",
        ]
        generated: List[PromptImportItem] = []
        for index in range(count):
            content = templates[index % len(templates)]
            generated.append(
                PromptImportItem(
                    content=content,
                    language=project.language,
                    region=project.region,
                    topic="generated",
                    stage="awareness",
                    priority=2 if index < 10 else 3,
                )
            )
        return self.import_prompts(project_id, generated)

    def create_run(self, project_id: str, run_type: RunType, engines: List[str]) -> Run:
        if not engines:
            engines = self.supported_engines[:]
        project = self.repository.get_project(project_id)
        if project is None:
            raise ValueError("project not found")
        invalid_engines = [engine for engine in engines if engine not in self.supported_engines]
        if invalid_engines:
            raise ValueError(f"unsupported engines: {invalid_engines}")

        prompts = self.repository.list_project_prompts(project_id)
        run_id = uuid.uuid4().hex
        run = Run(
            run_id=run_id,
            project_id=project_id,
            run_type=run_type,
            status=RunStatus.running,
            prompt_count=len(prompts),
            engines=engines,
            started_at=datetime.utcnow(),
            parser_rule_version=PARSER_RULE_VERSION,
        )
        self.repository.save_run(run)
        self._write_audit_log(
            project_id=project_id,
            module="run",
            event="run.created",
            message="run created",
            run_id=run_id,
            attributes={
                "runType": run_type.value,
                "engineCount": len(engines),
                "promptCount": len(prompts),
            },
        )
        trace_id = log_with_context(
            self.logger,
            logging.INFO,
            "run started",
            project_id=project_id,
            run_id=run_id,
            module="run",
            event="run.started",
        )

        prompt_results: List[PromptRunResult] = []
        total_engine_calls = 0
        success_engine_calls = 0
        for prompt in prompts:
            engine_results: List[EngineResult] = []
            for engine in engines:
                total_engine_calls += 1
                result = self._execute_engine(project, prompt, engine, run_id=run_id)
                if result.status == "success":
                    success_engine_calls += 1
                engine_results.append(result)
            prompt_results.append(
                PromptRunResult(
                    prompt_id=prompt.prompt_id,
                    prompt_content=prompt.content,
                    results=engine_results,
                )
            )

        run.prompt_results = prompt_results
        run.metrics = self._calculate_metrics(prompt_results)
        if total_engine_calls == 0:
            run.status = RunStatus.success
        elif success_engine_calls == 0:
            run.status = RunStatus.failed
        elif success_engine_calls < total_engine_calls:
            run.status = RunStatus.partial_success
        else:
            run.status = RunStatus.success
        run.finished_at = datetime.utcnow()
        self.repository.save_run(run)

        log_with_context(
            self.logger,
            logging.INFO,
            "run finished",
            trace_id=trace_id,
            project_id=project_id,
            run_id=run_id,
            module="run",
            event="run.finished",
        )
        return run

    def _execute_engine(self, project: Project, prompt: Prompt, engine: str, *, run_id: str) -> EngineResult:
        request = EngineRequest(
            prompt=prompt.content,
            language=prompt.language,
            region=prompt.region,
        )
        context = EngineContext(
            project_id=project.project_id,
            brand_name=project.brand_name,
            aliases=project.aliases,
            competitors=project.competitors,
            source_url=project.source_url,
        )
        raw, meta = self.engine_manager.execute(engine, request, context)
        result = build_engine_result(
            project=project,
            prompt=prompt,
            engine=engine,
            raw_answer=raw.raw_answer,
            citations=raw.citations,
            duration_ms=raw.duration_ms,
            status=raw.status,
            failure_reason=raw.failure_reason,
            response_metadata=raw.response_metadata,
        )

        prompt_hash = uuid.uuid5(uuid.NAMESPACE_URL, f"{project.project_id}:{prompt.prompt_id}").hex
        attributes = {
            "cacheHit": bool(meta.get("cacheHit")),
            "retryCount": int(meta.get("retryCount") or 0),
            "promptHash": prompt_hash,
            "citationCount": len(result.citations),
            "riskTags": result.risk_tags,
            "status": result.status,
        }
        if result.failure_reason:
            attributes["failureReason"] = result.failure_reason

        self._write_audit_log(
            project_id=project.project_id,
            run_id=run_id,
            module="engine",
            event="engine.call",
            message="engine executed",
            prompt_id=prompt.prompt_id,
            engine=engine,
            region=prompt.region,
            language=prompt.language,
            duration_ms=result.duration_ms,
            error_code="engine_failed" if result.status != "success" else None,
            retry_count=int(meta.get("retryCount") or 0),
            attributes=attributes,
        )
        return result

    def _calculate_metrics(self, prompt_results: List[PromptRunResult]) -> Metrics:
        total = 0
        mention_count = 0
        citation_count = 0
        position_sum = 0
        positive_count = 0
        negative_count = 0
        for prompt_result in prompt_results:
            for result in prompt_result.results:
                total += 1
                if result.mention:
                    mention_count += 1
                if result.citations:
                    citation_count += 1
                position_sum += result.position
                if result.sentiment == Sentiment.positive:
                    positive_count += 1
                if result.sentiment == Sentiment.negative:
                    negative_count += 1
        if total == 0:
            return Metrics()
        mention_rate = mention_count / total
        citation_rate = citation_count / total
        sentiment_score = (positive_count - negative_count) / total
        average_position = position_sum / total
        share_of_voice = mention_rate
        citation_quality = citation_rate * 0.7 + max(0.0, sentiment_score) * 0.3
        return Metrics(
            mention_rate=round(mention_rate, 4),
            citation_rate=round(citation_rate, 4),
            average_position=round(average_position, 4),
            sentiment_score=round(sentiment_score, 4),
            share_of_voice=round(share_of_voice, 4),
            citation_quality=round(citation_quality, 4),
        )

    def generate_insights(self, project_id: str, run_id: str, limit: int = 20) -> List[Insight]:
        run = self.repository.get_run(run_id)
        if run is None:
            raise ValueError("run not found")
        insights: List[Insight] = []
        for prompt_result in run.prompt_results:
            mention_ratio = sum(1 for result in prompt_result.results if result.mention) / max(len(prompt_result.results), 1)
            citation_ratio = sum(1 for result in prompt_result.results if result.citations) / max(len(prompt_result.results), 1)
            if mention_ratio >= 0.6 and citation_ratio >= 0.5:
                continue
            priority = round((1 - mention_ratio) * 0.6 + (1 - citation_ratio) * 0.4, 4)
            insight = Insight(
                insight_id=uuid.uuid4().hex,
                project_id=project_id,
                title=f"优化提示词：{prompt_result.prompt_content[:24]}",
                description="该提示词在提及或引用表现偏低，建议补充定义、FAQ 与权威引用来源。",
                priority_score=priority,
                affected_prompt_ids=[prompt_result.prompt_id],
                evidence_run_id=run_id,
            )
            self.repository.save_insight(insight)
            insights.append(insight)
        if not insights and run.prompt_results:
            fallback_target = run.prompt_results[0]
            fallback = Insight(
                insight_id=uuid.uuid4().hex,
                project_id=project_id,
                title=f"优化提示词：{fallback_target.prompt_content[:24]}",
                description="该提示词需补充结构化段落与可引用来源以提升稳定性。",
                priority_score=0.25,
                affected_prompt_ids=[fallback_target.prompt_id],
                evidence_run_id=run_id,
            )
            self.repository.save_insight(fallback)
            insights.append(fallback)
        insights.sort(key=lambda item: item.priority_score, reverse=True)
        visible = insights[:limit]
        self._write_audit_log(
            project_id=project_id,
            module="insight",
            event="insight.generated",
            message=f"insights generated count={len(visible)}",
            run_id=run_id,
            attributes={"count": len(visible)},
        )
        return visible

    def generate_playbook(self, project_id: str, insight_id: str) -> Playbook:
        insight = self.repository.get_insight(insight_id)
        if insight is None:
            raise ValueError("insight not found")
        markdown = (
            f"# {insight.title}\n\n"
            "## 优化目标\n"
            "- 提高 Mention Rate 与 Citation Rate\n"
            "- 补齐 FAQ、对比与关键事实块\n\n"
            "## 建议动作\n"
            "1. 增加定义段与 TL;DR\n"
            "2. 补充至少 3 条可引用来源\n"
            "3. 增加对比表与常见问题\n\n"
            "## 验证方式\n"
            "- 执行 After Run 并对比基线指标\n"
        )
        playbook = Playbook(
            playbook_id=uuid.uuid4().hex,
            project_id=project_id,
            insight_id=insight_id,
            markdown_draft=markdown,
            estimated_impact={"mention_rate": 0.08, "citation_rate": 0.1},
            risk_level="medium",
            created_at=datetime.utcnow(),
        )
        self.repository.save_playbook(playbook)
        self._write_audit_log(
            project_id=project_id,
            module="playbook",
            event="playbook.generated",
            message="playbook generated",
            attributes={"insightId": insight_id, "playbookId": playbook.playbook_id},
        )
        return playbook

    def verify_runs(self, project_id: str, baseline_run_id: str, after_run_id: str) -> VerificationReport:
        baseline = self.repository.get_run(baseline_run_id)
        after = self.repository.get_run(after_run_id)
        if baseline is None or after is None or baseline.project_id != project_id or after.project_id != project_id:
            raise ValueError("run not found")
        deltas = {
            "mention_rate": round(after.metrics.mention_rate - baseline.metrics.mention_rate, 4),
            "citation_rate": round(after.metrics.citation_rate - baseline.metrics.citation_rate, 4),
            "average_position": round(after.metrics.average_position - baseline.metrics.average_position, 4),
            "sentiment_score": round(after.metrics.sentiment_score - baseline.metrics.sentiment_score, 4),
        }
        summary = "整体表现提升" if deltas["mention_rate"] >= 0 and deltas["citation_rate"] >= 0 else "部分指标回退，需复盘"
        report = VerificationReport(
            report_id=uuid.uuid4().hex,
            project_id=project_id,
            baseline_run_id=baseline_run_id,
            after_run_id=after_run_id,
            metric_deltas=deltas,
            summary=summary,
            created_at=datetime.utcnow(),
        )
        self.repository.save_verification_report(report)
        self._write_audit_log(
            project_id=project_id,
            module="verification",
            event="verification.generated",
            message="verification report generated",
            attributes={
                "reportId": report.report_id,
                "baselineRunId": baseline_run_id,
                "afterRunId": after_run_id,
            },
        )
        return report

    def build_monitor_report(self, project_id: str, run_id: str, language: str = "zh-CN") -> MonitorReport:
        run = self.repository.get_run(run_id)
        if run is None or run.project_id != project_id:
            raise ValueError("run not found")
        thresholds = self.config_center.get("global.monitoring", {})
        citation_drop_threshold = thresholds.get("citationDropAlertThreshold", 0.15)
        alerts: List[str] = []
        if run.metrics.citation_rate < citation_drop_threshold:
            alerts.append("citation_rate_low")
        if run.metrics.sentiment_score < 0:
            alerts.append("negative_sentiment_risk")

        if alerts:
            summary = self.i18n.t("notification.monitor.alertTriggered", language)
        else:
            summary = self.i18n.t("notification.run.finished", language)

        report = MonitorReport(
            report_id=uuid.uuid4().hex,
            project_id=project_id,
            run_id=run_id,
            alerts=alerts,
            summary=summary,
            created_at=datetime.utcnow(),
        )
        self.repository.save_monitor_report(report)
        return report

    def save_strategy_memory(self, project_id: str, playbook_id: str, verification_report_id: str) -> StrategyMemory:
        playbook = self.repository.get_playbook(playbook_id)
        if playbook is None or playbook.project_id != project_id:
            raise ValueError("playbook not found")
        report = self.repository.get_verification_report(verification_report_id)
        if report is None or report.project_id != project_id:
            raise ValueError("verification report not found")
        success = report.metric_deltas.get("mention_rate", 0) >= 0 and report.metric_deltas.get("citation_rate", 0) >= 0
        memory = StrategyMemory(
            memory_id=uuid.uuid4().hex,
            project_id=project_id,
            playbook_id=playbook_id,
            impact_metrics=report.metric_deltas,
            success=success,
            created_at=datetime.utcnow(),
        )
        self.repository.save_strategy_memory(memory)
        self._write_audit_log(
            project_id=project_id,
            module="strategy",
            event="strategy_memory.saved",
            message="strategy memory saved",
            attributes={
                "memoryId": memory.memory_id,
                "playbookId": playbook_id,
                "verificationReportId": verification_report_id,
                "success": success,
            },
        )
        return memory

    def project_overview(self, project_id: str) -> Dict[str, object]:
        project = self.repository.get_project(project_id)
        if project is None:
            raise ValueError("project not found")
        runs = self.repository.list_project_runs(project_id)
        runs.sort(key=lambda item: item.started_at)
        latest = runs[-1] if runs else None
        previous = runs[-2] if len(runs) > 1 else None
        trend = {}
        if latest and previous:
            trend = {
                "mention_rate_delta": round(latest.metrics.mention_rate - previous.metrics.mention_rate, 4),
                "citation_rate_delta": round(latest.metrics.citation_rate - previous.metrics.citation_rate, 4),
            }
        insights = self.repository.list_project_insights(project_id)
        memories = self.repository.list_project_memories(project_id)
        return {
            "project": project,
            "latest_metrics": latest.metrics if latest else Metrics(),
            "trend": trend,
            "top_opportunities": sorted(insights, key=lambda item: item.priority_score, reverse=True)[:20],
            "strategy_memory_count": len(memories),
            "supported_metrics": self.dictionary.metric_keys(),
        }

    def build_weekly_email_report(self, project_id: str, language: str = "zh-CN") -> Dict[str, object]:
        overview = self.project_overview(project_id)
        latest_metrics = overview["latest_metrics"]
        title = self.i18n.t("report.section.summary", language)
        body = {
            "mention_rate": latest_metrics.mention_rate,
            "citation_rate": latest_metrics.citation_rate,
            "average_position": latest_metrics.average_position,
            "sentiment_score": latest_metrics.sentiment_score,
            "top_opportunities": [
                {
                    "title": insight.title,
                    "priority_score": insight.priority_score,
                }
                for insight in overview["top_opportunities"][:5]
            ],
            "next_actions": self.i18n.t("report.section.nextAction", language),
        }
        return {
            "subject": f"[OpenGEO Bot] Weekly Report - {overview['project'].project_name}",
            "title": title,
            "language": language,
            "body": body,
        }
