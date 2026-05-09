"""
功能：OpenGEO Bot MVP 业务服务
时间：2026-05-08 13:29:00
作者：AxeXie
"""

from __future__ import annotations

import hashlib
import logging
import uuid
from datetime import datetime
from html.parser import HTMLParser
from typing import Any, Dict, List
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen

from app.engines import EngineContext, EngineManager, EngineRequest, MockEngineAdapter, TemplateEngineAdapter
from app.foundation import ConfigCenter, DataDictionary, I18N, deep_merge_config, get_operator, get_trace_id, log_with_context
from app.models import (
    Asset,
    AssetChange,
    AssetCreate,
    AssetStatus,
    AssetType,
    AssetUpdate,
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


class _HtmlExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.title: str = ""
        self._in_title = False
        self.links: List[str] = []
        self.text_chunks: List[str] = []

    def handle_starttag(self, tag: str, attrs):
        tag_lower = (tag or "").lower()
        if tag_lower == "title":
            self._in_title = True
            return
        if tag_lower == "a":
            href = None
            for k, v in attrs:
                if (k or "").lower() == "href":
                    href = v
                    break
            if href:
                self.links.append(str(href))

    def handle_endtag(self, tag: str):
        if (tag or "").lower() == "title":
            self._in_title = False

    def handle_data(self, data: str):
        if not data:
            return
        if self._in_title:
            if not self.title:
                self.title = data.strip()
        else:
            value = data.strip()
            if value:
                self.text_chunks.append(value)


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
        asset = Asset(
            asset_id=uuid.uuid4().hex,
            project_id=project_id,
            asset_type=AssetType.website if payload.project_type.value == "website" else AssetType.repository,
            source_url=payload.source_url,
            status=AssetStatus.pending,
            content_version="0",
        )
        self.repository.save_asset(asset)
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
        self._write_audit_log(
            project_id=project_id,
            module="asset",
            event="asset.created",
            message="default asset created",
            attributes={"assetId": asset.asset_id, "assetType": asset.asset_type.value, "sourceUrl": asset.source_url},
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

    def create_asset(self, project_id: str, payload: AssetCreate) -> Asset:
        project = self.get_project(project_id)
        source_url = payload.source_url.strip()
        if not source_url:
            raise ValueError("invalid source_url")
        for existing in self.repository.list_project_assets(project_id):
            if not existing.deleted and existing.source_url == source_url and existing.asset_type == payload.asset_type:
                return existing
        asset = Asset(
            asset_id=uuid.uuid4().hex,
            project_id=project_id,
            asset_type=payload.asset_type,
            source_url=source_url,
            status=AssetStatus.pending,
            content_version="0",
        )
        self.repository.save_asset(asset)
        self._write_audit_log(
            project_id=project_id,
            module="asset",
            event="asset.created",
            message="asset created",
            attributes={"assetId": asset.asset_id, "assetType": asset.asset_type.value, "sourceUrl": asset.source_url},
        )
        if project.project_type.value == "website" and payload.asset_type != AssetType.website:
            self._write_audit_log(
                project_id=project_id,
                module="asset",
                event="asset.type.mismatch",
                message="asset type differs from project type",
                attributes={"projectType": project.project_type.value, "assetType": payload.asset_type.value},
            )
        return asset

    def get_asset(self, project_id: str, asset_id: str) -> Asset:
        asset = self.repository.get_asset(asset_id)
        if asset is None or asset.project_id != project_id or asset.deleted:
            raise ValueError("asset not found")
        return asset

    def update_asset(self, project_id: str, asset_id: str, payload: AssetUpdate) -> Asset:
        asset = self.get_asset(project_id, asset_id)
        changed = False
        if payload.source_url is not None:
            source_url = payload.source_url.strip()
            if not source_url:
                raise ValueError("invalid source_url")
            if asset.source_url != source_url:
                asset.source_url = source_url
                asset.status = AssetStatus.pending
                asset.error_message = None
                changed = True
        if payload.deleted is not None:
            if payload.deleted and not asset.deleted:
                asset.deleted = True
                asset.deleted_at = datetime.utcnow()
                changed = True
            if not payload.deleted and asset.deleted:
                asset.deleted = False
                asset.deleted_at = None
                changed = True
        if changed:
            self.repository.save_asset(asset)
            self._write_audit_log(
                project_id=project_id,
                module="asset",
                event="asset.updated",
                message="asset updated",
                attributes={"assetId": asset.asset_id, "deleted": asset.deleted, "sourceUrl": asset.source_url},
            )
        return asset

    def delete_asset(self, project_id: str, asset_id: str) -> Asset:
        asset = self.get_asset(project_id, asset_id)
        asset.deleted = True
        asset.deleted_at = datetime.utcnow()
        self.repository.save_asset(asset)
        self._write_audit_log(
            project_id=project_id,
            module="asset",
            event="asset.deleted",
            message="asset deleted",
            attributes={"assetId": asset.asset_id, "deletedAt": asset.deleted_at.isoformat()},
        )
        return asset

    def list_assets(self, project_id: str, *, include_deleted: bool = False) -> List[Asset]:
        items = self.repository.list_project_assets(project_id)
        if not include_deleted:
            items = [item for item in items if not item.deleted]
        items.sort(key=lambda item: (item.last_crawled_at or datetime.min), reverse=True)
        return items

    def list_asset_changes(self, project_id: str, *, asset_id: str | None = None) -> List[AssetChange]:
        items = self.repository.list_project_asset_changes(project_id)
        if asset_id:
            items = [item for item in items if item.asset_id == asset_id]
        items.sort(key=lambda item: item.detected_at, reverse=True)
        return items

    def sync_assets(self, project_id: str, *, force: bool = False, asset_ids: List[str] | None = None) -> List[Asset]:
        project = self.get_project(project_id)
        candidates = self.repository.list_project_assets(project_id)
        candidates = [item for item in candidates if not item.deleted]

        if asset_ids:
            selected: List[Asset] = []
            for asset_id in asset_ids:
                for item in candidates:
                    if item.asset_id == asset_id:
                        selected.append(item)
                        break
            if not selected:
                raise ValueError("asset not found")
            candidates = selected

        if not candidates and not asset_ids:
            assets = [
                Asset(
                    asset_id=uuid.uuid4().hex,
                    project_id=project_id,
                    asset_type=AssetType.website if project.project_type.value == "website" else AssetType.repository,
                    source_url=project.source_url,
                )
            ]
            for asset in assets:
                self.repository.save_asset(asset)
            candidates = assets

        updated: List[Asset] = []
        for asset in candidates:
            updated.append(self._sync_one_asset(project, asset, force=force))
        self._write_audit_log(
            project_id=project_id,
            module="asset",
            event="asset.synced",
            message=f"assets synced count={len(updated)}",
            attributes={"count": len(updated), "force": bool(force), "assetIds": [item.asset_id for item in updated]},
        )
        return updated

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
        asset_versions = {item.asset_id: item.content_version for item in self.repository.list_project_assets(project_id)}
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
            asset_versions=asset_versions,
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
            evidence = _build_prompt_evidence(run, prompt_result, mention_ratio=mention_ratio, citation_ratio=citation_ratio)
            insight = Insight(
                insight_id=uuid.uuid4().hex,
                project_id=project_id,
                title=f"优化提示词：{prompt_result.prompt_content[:24]}",
                description="该提示词在提及或引用表现偏低，建议补充定义、FAQ 与权威引用来源。",
                priority_score=priority,
                affected_prompt_ids=[prompt_result.prompt_id],
                evidence_run_id=run_id,
                evidence=evidence,
            )
            self.repository.save_insight(insight)
            insights.append(insight)
        if not insights and run.prompt_results:
            fallback_target = run.prompt_results[0]
            evidence = _build_prompt_evidence(
                run,
                fallback_target,
                mention_ratio=0.0,
                citation_ratio=0.0,
            )
            fallback = Insight(
                insight_id=uuid.uuid4().hex,
                project_id=project_id,
                title=f"优化提示词：{fallback_target.prompt_content[:24]}",
                description="该提示词需补充结构化段落与可引用来源以提升稳定性。",
                priority_score=0.25,
                affected_prompt_ids=[fallback_target.prompt_id],
                evidence_run_id=run_id,
                evidence=evidence,
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
        evidence = insight.evidence or {}
        evidence_lines = _render_evidence_markdown(evidence)
        markdown = (
            f"# {insight.title}\n\n"
            "## 证据\n"
            f"{evidence_lines}\n\n"
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
            evidence=evidence,
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
        assets = self.repository.list_project_assets(project_id)
        assets = [item for item in assets if not item.deleted]
        assets.sort(key=lambda item: (item.last_crawled_at or datetime.min), reverse=True)
        return {
            "project": project,
            "assets": assets,
            "latest_metrics": latest.metrics if latest else Metrics(),
            "trend": trend,
            "top_opportunities": sorted(insights, key=lambda item: item.priority_score, reverse=True)[:20],
            "strategy_memory_count": len(memories),
            "supported_metrics": self.dictionary.metric_keys(),
        }

    def build_weekly_email_report(self, project_id: str, language: str = "zh-CN") -> Dict[str, object]:
        overview = self.project_overview(project_id)
        latest_metrics = overview["latest_metrics"]
        assets = overview.get("assets") or []
        asset_summaries = []
        if isinstance(assets, list):
            for asset in assets[:3]:
                if not isinstance(asset, Asset):
                    continue
                asset_summaries.append(
                    {
                        "asset_id": asset.asset_id,
                        "asset_type": asset.asset_type.value,
                        "status": asset.status.value,
                        "last_crawled_at": asset.last_crawled_at,
                        "content_version": asset.content_version,
                    }
                )
        title = self.i18n.t("report.section.summary", language)
        body = {
            "mention_rate": latest_metrics.mention_rate,
            "citation_rate": latest_metrics.citation_rate,
            "average_position": latest_metrics.average_position,
            "sentiment_score": latest_metrics.sentiment_score,
            "assets": asset_summaries,
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

    def _sync_one_asset(self, project: Project, asset: Asset, *, force: bool) -> Asset:
        now = datetime.utcnow()
        try:
            content, title, summary, discovered = _fetch_asset_content(asset)
            content_version = _hash_version(content)
            previous_version = asset.content_version or "0"
            changed = force or (content_version != previous_version)

            asset.title = title or asset.title
            asset.summary = summary or asset.summary
            asset.discovered_urls = discovered
            asset.last_crawled_at = now
            asset.status = AssetStatus.success
            asset.error_message = None

            if changed:
                asset.content_version = content_version
                change = AssetChange(
                    change_id=uuid.uuid4().hex,
                    project_id=project.project_id,
                    asset_id=asset.asset_id,
                    detected_at=now,
                    previous_version=previous_version,
                    new_version=content_version,
                    summary=f"content_version changed {previous_version} -> {content_version}",
                )
                self.repository.save_asset_change(change)

            self.repository.save_asset(asset)
            self._write_audit_log(
                project_id=project.project_id,
                module="asset",
                event="asset.crawled",
                message="asset crawled",
                attributes={
                    "assetId": asset.asset_id,
                    "assetType": asset.asset_type.value,
                    "status": asset.status.value,
                    "contentVersion": asset.content_version,
                    "changed": bool(changed),
                    "discoveredUrlCount": len(discovered),
                },
            )
            return asset
        except Exception as exc:
            asset.last_crawled_at = now
            asset.status = AssetStatus.failed
            asset.error_message = str(exc)
            self.repository.save_asset(asset)
            self._write_audit_log(
                project_id=project.project_id,
                module="asset",
                event="asset.crawled",
                message="asset crawl failed",
                error_code="asset_crawl_failed",
                attributes={
                    "assetId": asset.asset_id,
                    "assetType": asset.asset_type.value,
                    "status": asset.status.value,
                    "error": asset.error_message,
                },
            )
            return asset


_README_CANDIDATES = (
    "README.md",
    "Readme.md",
    "readme.md",
)


def _fetch_asset_content(asset: Asset) -> tuple[str, str, str, List[str]]:
    if asset.asset_type == AssetType.repository:
        return _fetch_repository(asset.source_url)
    return _fetch_website(asset.source_url)


def _fetch_website(url: str) -> tuple[str, str, str, List[str]]:
    req = Request(url=url, headers={"User-Agent": "opengeobot/1.0"})
    with urlopen(req, timeout=12) as resp:
        raw = resp.read()
    text = raw.decode("utf-8", errors="replace")
    parser = _HtmlExtractor()
    parser.feed(text)
    title = (parser.title or "").strip()
    summary = " ".join(parser.text_chunks[:80]).strip()
    if len(summary) > 400:
        summary = summary[:400].rstrip() + "..."
    base = url
    discovered = _normalize_links(base, parser.links)
    return text, title, summary, discovered


def _fetch_repository(url: str) -> tuple[str, str, str, List[str]]:
    parsed = urlparse(url)
    if parsed.netloc.lower() != "github.com":
        raise ValueError("only github.com repository url is supported in MVP")
    parts = [p for p in parsed.path.split("/") if p]
    if len(parts) < 2:
        raise ValueError("invalid github repository url")
    owner, repo = parts[0], parts[1]

    last_error: str | None = None
    for branch in ("HEAD", "main", "master"):
        for name in _README_CANDIDATES:
            raw_url = f"https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{name}"
            try:
                req = Request(url=raw_url, headers={"User-Agent": "opengeobot/1.0"})
                with urlopen(req, timeout=12) as resp:
                    content = resp.read().decode("utf-8", errors="replace")
                title = _markdown_title(content) or f"{owner}/{repo}"
                summary = _markdown_summary(content)
                discovered = [url]
                return content, title, summary, discovered
            except Exception as exc:
                last_error = str(exc)
                continue
    raise RuntimeError(f"failed to fetch README from GitHub: {last_error or 'unknown'}")


def _markdown_title(content: str) -> str:
    for line in (content or "").splitlines():
        value = line.strip()
        if value.startswith("#"):
            return value.lstrip("#").strip()
    return ""


def _markdown_summary(content: str) -> str:
    lines = []
    for line in (content or "").splitlines():
        value = line.strip()
        if not value:
            continue
        if value.startswith("#"):
            continue
        lines.append(value)
        if len(lines) >= 5:
            break
    summary = " ".join(lines).strip()
    if len(summary) > 400:
        summary = summary[:400].rstrip() + "..."
    return summary


def _normalize_links(base_url: str, links: List[str]) -> List[str]:
    base = urlparse(base_url)
    normalized: List[str] = []
    seen = set()
    for href in links:
        value = (href or "").strip()
        if not value or value.startswith("#"):
            continue
        if value.startswith("javascript:") or value.startswith("mailto:"):
            continue
        absolute = urljoin(base_url, value)
        parsed = urlparse(absolute)
        if parsed.scheme not in ("http", "https"):
            continue
        if parsed.netloc and parsed.netloc != base.netloc:
            continue
        cleaned = parsed._replace(fragment="").geturl()
        if cleaned not in seen:
            normalized.append(cleaned)
            seen.add(cleaned)
        if len(normalized) >= 50:
            break
    return normalized


def _hash_version(content: str) -> str:
    digest = hashlib.sha256((content or "").encode("utf-8", errors="ignore")).hexdigest()
    return digest[:12]


def _build_prompt_evidence(run: Run, prompt_result: PromptRunResult, *, mention_ratio: float, citation_ratio: float) -> Dict[str, Any]:
    per_engine = []
    domains = []
    for result in prompt_result.results:
        per_engine.append(
            {
                "engine": result.engine,
                "status": result.status,
                "mention": result.mention,
                "position": result.position,
                "sentiment": result.sentiment.value,
                "risk_tags": result.risk_tags,
                "citation_count": len(result.citations),
                "citations": result.citations[:5],
                "answer_snippet": (result.raw_answer or "")[:220],
            }
        )
        for c in result.citations:
            try:
                domains.append(urlparse(c).netloc)
            except Exception:
                continue
    top_domains: List[str] = []
    for d in domains:
        if d and d not in top_domains:
            top_domains.append(d)
        if len(top_domains) >= 5:
            break
    return {
        "run_id": run.run_id,
        "run_type": run.run_type.value,
        "parser_rule_version": run.parser_rule_version,
        "asset_versions": run.asset_versions,
        "prompt": {"prompt_id": prompt_result.prompt_id, "content": prompt_result.prompt_content},
        "signals": {
            "mention_ratio": round(float(mention_ratio), 4),
            "citation_ratio": round(float(citation_ratio), 4),
            "top_citation_domains": top_domains,
        },
        "per_engine": per_engine,
    }


def _render_evidence_markdown(evidence: Dict[str, Any]) -> str:
    if not evidence:
        return "- 无（未找到可用证据）"
    prompt = evidence.get("prompt") if isinstance(evidence.get("prompt"), dict) else {}
    signals = evidence.get("signals") if isinstance(evidence.get("signals"), dict) else {}
    domains = signals.get("top_citation_domains") if isinstance(signals.get("top_citation_domains"), list) else []
    mention_ratio = signals.get("mention_ratio")
    citation_ratio = signals.get("citation_ratio")
    lines = [
        f"- 关联 Run: {evidence.get('run_id')}",
        f"- 提示词: {prompt.get('content')}",
        f"- Mention/Citation 比例: {mention_ratio}/{citation_ratio}",
    ]
    if domains:
        lines.append(f"- Top 引用域名: {', '.join([str(d) for d in domains])}")
    return "\n".join(lines)
