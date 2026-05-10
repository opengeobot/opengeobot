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
    Alert,
    AlertStatus,
    AlertUpdate,
    ApprovalDecision,
    ApprovalEvent,
    Asset,
    AssetChange,
    AssetCreate,
    AssetStatus,
    AssetType,
    AssetUpdate,
    AuditLog,
    CitationSource,
    EngineResult,
    GitHubPRDraft,
    GitHubPRDraftApprove,
    GitHubPRDraftCreate,
    GitHubPRDraftStatus,
    Insight,
    Metrics,
    MetricDistribution,
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
    StabilityReport,
    StabilityReportCreate,
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
            elif engine_id == "deepseek":
                # 尝试加载 DeepSeek 适配器
                deepseek_api_key = os.environ.get("DEEPSEEK_API_KEY")
                if deepseek_api_key:
                    from app.engines_deepseek import DeepSeekEngineAdapter
                    adapters[engine_id] = DeepSeekEngineAdapter(
                        engine_id=engine_id,
                        api_key=deepseek_api_key,
                    )
                    self.logger.info(f"DeepSeek engine adapter loaded for {engine_id}")
                else:
                    self.logger.warning(
                        "DEEPSEEK_API_KEY not set, skipping DeepSeek adapter. "
                        "API key must be provided via environment variable."
                    )
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

    def list_prompts(
        self,
        project_id: str,
        *,
        include_disabled: bool = False,
        language: str | None = None,
        region: str | None = None,
        topic: str | None = None,
        stage: str | None = None,
        enabled: bool | None = None,
        min_priority: int | None = None,
        max_priority: int | None = None,
    ) -> List[Prompt]:
        items = self.repository.list_project_prompts(project_id, include_disabled=include_disabled)
        if language is not None:
            items = [item for item in items if item.language == language]
        if region is not None:
            items = [item for item in items if item.region == region]
        if topic is not None:
            items = [item for item in items if item.topic == topic]
        if stage is not None:
            items = [item for item in items if item.stage == stage]
        if enabled is not None:
            items = [item for item in items if item.enabled == enabled]
        if min_priority is not None:
            items = [item for item in items if item.priority >= min_priority]
        if max_priority is not None:
            items = [item for item in items if item.priority <= max_priority]
        items.sort(key=lambda item: (item.priority, item.prompt_id))
        return items

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

    def list_citation_sources(self, project_id: str, *, run_id: str | None = None, limit: int = 50) -> List[CitationSource]:
        project = self.repository.get_project(project_id)
        if project is None:
            raise ValueError("project not found")

        target_run: Run | None = None
        if run_id is not None:
            target_run = self.repository.get_run(run_id)
            if target_run is None or target_run.project_id != project_id:
                raise ValueError("run not found")
        else:
            runs = self.repository.list_project_runs(project_id)
            runs.sort(key=lambda item: item.started_at, reverse=True)
            target_run = runs[0] if runs else None

        if target_run is None:
            return []

        def _normalize_host(raw_url: str) -> str:
            try:
                host = urlparse(raw_url).netloc or ""
            except Exception:
                host = ""
            host = host.split("@")[-1]
            host = host.split(":")[0].lower()
            if host.startswith("www."):
                host = host[4:]
            return host

        def _normalize_prefix(raw_url: str) -> str:
            raw_url = (raw_url or "").strip()
            if not raw_url:
                return ""
            parsed = urlparse(raw_url)
            if not parsed.scheme or not parsed.netloc:
                return ""
            prefix = f"{parsed.scheme.lower()}://{parsed.netloc}{parsed.path}"
            if not prefix.endswith("/"):
                prefix += "/"
            return prefix

        official_prefixes: Dict[str, str] = {}
        project_prefix = _normalize_prefix(project.source_url)
        if project_prefix:
            official_prefixes[project_prefix] = "project"

        assets = self.repository.list_project_assets(project_id)
        for asset in assets:
            if asset.deleted:
                continue
            prefix = _normalize_prefix(asset.source_url)
            if prefix:
                official_prefixes[prefix] = asset.asset_id

        if "github.com" in (urlparse(project.source_url).netloc or "") and project_prefix:
            github_prefix = project_prefix.rstrip("/")
            official_prefixes[f"{github_prefix}/blob/"] = "project"
            official_prefixes[f"{github_prefix}/tree/"] = "project"

        per_domain: Dict[str, Dict[str, Any]] = {}
        now = datetime.utcnow()
        last_seen_at = target_run.finished_at or now

        def _match_asset_ids(citation_url: str) -> List[str]:
            normalized = _normalize_prefix(citation_url)
            if not normalized:
                return []
            matched: List[str] = []
            for prefix, asset_id in official_prefixes.items():
                if normalized.startswith(prefix):
                    if asset_id != "project":
                        matched.append(asset_id)
            return matched

        def _is_official(citation_url: str) -> bool:
            normalized = _normalize_prefix(citation_url)
            if not normalized:
                return False
            return any(normalized.startswith(prefix) for prefix in official_prefixes.keys())

        for pr in target_run.prompt_results:
            prompt_id = pr.prompt_id
            for er in pr.results:
                for c in er.citations:
                    domain = _normalize_host(c)
                    if not domain:
                        continue
                    bucket = per_domain.get(domain)
                    if bucket is None:
                        bucket = {
                            "count": 0,
                            "example_url": c,
                            "prompt_ids": set(),
                            "last_seen_at": last_seen_at,
                            "matched_asset_ids": set(),
                            "is_official": False,
                        }
                        per_domain[domain] = bucket
                    bucket["count"] += 1
                    bucket["prompt_ids"].add(prompt_id)
                    for asset_id in _match_asset_ids(c):
                        bucket["matched_asset_ids"].add(asset_id)
                    if _is_official(c):
                        bucket["is_official"] = True

        def _quality_score(domain: str, is_official: bool) -> float:
            if is_official:
                return 1.0
            if domain.endswith("github.com") or domain.endswith("readthedocs.io"):
                return 0.75
            if domain.endswith("wikipedia.org") or domain.endswith("stackoverflow.com"):
                return 0.6
            return 0.4

        sources: List[CitationSource] = []
        for domain, bucket in per_domain.items():
            is_official = bool(bucket.get("is_official"))
            sources.append(
                CitationSource(
                    domain=domain,
                    example_url=bucket["example_url"],
                    count=int(bucket["count"]),
                    prompt_count=len(bucket["prompt_ids"]),
                    last_seen_at=bucket["last_seen_at"],
                    is_official=is_official,
                    quality_score=_quality_score(domain, is_official),
                    matched_asset_ids=sorted(list(bucket.get("matched_asset_ids") or [])),
                )
            )

        sources.sort(key=lambda item: (not item.is_official, -item.count, item.domain))
        return sources[: max(limit, 0)]

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
        assets = self.repository.list_project_assets(project_id)
        asset_versions = {item.asset_id: item.content_version for item in assets}
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
        run.metrics = self._calculate_metrics(project, assets, prompt_results)
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

    async def create_run_async(self, project_id: str, run_type: RunType, engines: List[str]) -> Run:
        """异步执行引擎调用，使用并行执行提升性能"""
        import asyncio
        
        if not engines:
            engines = self.supported_engines[:]
        project = self.repository.get_project(project_id)
        if project is None:
            raise ValueError("project not found")
        invalid_engines = [engine for engine in engines if engine not in self.supported_engines]
        if invalid_engines:
            raise ValueError(f"unsupported engines: {invalid_engines}")

        prompts = self.repository.list_project_prompts(project_id)
        assets = self.repository.list_project_assets(project_id)
        asset_versions = {item.asset_id: item.content_version for item in assets}
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
            message="run created (async)",
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
            "run started (async)",
            project_id=project_id,
            run_id=run_id,
            module="run",
            event="run.started",
        )

        prompt_results: List[PromptRunResult] = []
        total_engine_calls = 0
        success_engine_calls = 0
        
        # 并行执行所有 prompt + engine 组合
        async def _execute_prompt_engine(prompt: Prompt, engine: str) -> EngineResult:
            nonlocal total_engine_calls, success_engine_calls
            total_engine_calls += 1
            result = await self._execute_engine_async(project, prompt, engine, run_id=run_id)
            if result.status == "success":
                success_engine_calls += 1
            return result
        
        for prompt in prompts:
            # 对同一个 prompt 的多个 engine 并行执行
            engine_tasks = [_execute_prompt_engine(prompt, engine) for engine in engines]
            engine_results = await asyncio.gather(*engine_tasks, return_exceptions=True)
            
            # 处理可能的异常
            processed_results: List[EngineResult] = []
            for i, result in enumerate(engine_results):
                if isinstance(result, BaseException):
                    processed_results.append(EngineResult(
                        engine=engines[i],
                        raw_answer="",
                        citations=[],
                        duration_ms=0,
                        status="failed",
                        failure_reason=str(result),
                    ))
                    total_engine_calls += 1
                else:
                    processed_results.append(result)
            
            prompt_results.append(
                PromptRunResult(
                    prompt_id=prompt.prompt_id,
                    prompt_content=prompt.content,
                    results=processed_results,
                )
            )

        run.prompt_results = prompt_results
        run.metrics = self._calculate_metrics(project, assets, prompt_results)
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
            "run finished (async)",
            trace_id=trace_id,
            project_id=project_id,
            run_id=run_id,
            module="run",
            event="run.finished",
        )
        return run

    def create_stability_report(self, project_id: str, payload: StabilityReportCreate) -> StabilityReport:
        if payload.repeats < 2:
            raise ValueError("repeats must be >= 2")
        engines = payload.engines[:]
        if not engines:
            engines = self.supported_engines[:]

        runs: List[Run] = []
        for _ in range(payload.repeats):
            runs.append(self.create_run(project_id, payload.run_type, engines))

        def _t_critical_95(df: int) -> float:
            table = {
                1: 12.706,
                2: 4.303,
                3: 3.182,
                4: 2.776,
                5: 2.571,
                6: 2.447,
                7: 2.365,
                8: 2.306,
                9: 2.262,
                10: 2.228,
                11: 2.201,
                12: 2.179,
                13: 2.160,
                14: 2.145,
                15: 2.131,
                16: 2.120,
                17: 2.110,
                18: 2.101,
                19: 2.093,
                20: 2.086,
                21: 2.080,
                22: 2.074,
                23: 2.069,
                24: 2.064,
                25: 2.060,
                26: 2.056,
                27: 2.052,
                28: 2.048,
                29: 2.045,
                30: 2.042,
            }
            if df <= 0:
                return 0.0
            if df >= 30:
                return 1.96
            return table.get(df, 2.0)

        metric_keys = [
            "mention_rate",
            "citation_rate",
            "official_citation_rate",
            "average_position",
            "sentiment_score",
            "share_of_voice",
            "citation_quality",
        ]
        distributions: Dict[str, MetricDistribution] = {}
        n = len(runs)
        df = n - 1
        t95 = _t_critical_95(df)
        for key in metric_keys:
            values = [float(getattr(r.metrics, key, 0.0)) for r in runs]
            mean = sum(values) / max(n, 1)
            if n < 2:
                stdev = 0.0
            else:
                var = sum((v - mean) ** 2 for v in values) / df
                stdev = var ** 0.5
            se = (stdev / (n ** 0.5)) if n > 0 else 0.0
            half = t95 * se
            distributions[key] = MetricDistribution(
                metric=key,
                values=[round(v, 4) for v in values],
                mean=round(mean, 4),
                stdev=round(stdev, 4),
                ci95_low=round(mean - half, 4),
                ci95_high=round(mean + half, 4),
            )

        now = datetime.utcnow()
        report = StabilityReport(
            report_id=uuid.uuid4().hex,
            project_id=project_id,
            run_type=payload.run_type,
            engines=engines,
            repeats=payload.repeats,
            run_ids=[r.run_id for r in runs],
            metrics=distributions,
            created_at=now,
        )
        self.repository.save_stability_report(report)
        self._write_audit_log(
            project_id=project_id,
            module="stability",
            event="stability_report.created",
            message="stability report created",
            attributes={"reportId": report.report_id, "repeats": payload.repeats, "runIds": report.run_ids},
        )
        return report

    def list_stability_reports(self, project_id: str) -> List[StabilityReport]:
        items = self.repository.list_project_stability_reports(project_id)
        items.sort(key=lambda item: item.created_at, reverse=True)
        return items

    def get_stability_report(self, project_id: str, report_id: str) -> StabilityReport:
        report = self.repository.get_stability_report(report_id)
        if report is None or report.project_id != project_id:
            raise ValueError("report not found")
        return report

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

    async def _execute_engine_async(self, project: Project, prompt: Prompt, engine: str, *, run_id: str) -> EngineResult:
        """异步执行单个引擎调用"""
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
        raw, meta = await self.engine_manager.execute_async(engine, request, context)
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
            message="engine executed (async)",
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

    def _calculate_metrics(self, project: Project, assets: List[Asset], prompt_results: List[PromptRunResult]) -> Metrics:
        def _normalize_prefix(raw_url: str) -> str:
            raw_url = (raw_url or "").strip()
            if not raw_url:
                return ""
            parsed = urlparse(raw_url)
            if not parsed.scheme or not parsed.netloc:
                return ""
            prefix = f"{parsed.scheme.lower()}://{parsed.netloc}{parsed.path}"
            if not prefix.endswith("/"):
                prefix += "/"
            return prefix

        official_prefixes: List[str] = []
        project_prefix = _normalize_prefix(project.source_url)
        if project_prefix:
            official_prefixes.append(project_prefix)
            if "github.com" in (urlparse(project.source_url).netloc or ""):
                github_prefix = project_prefix.rstrip("/")
                official_prefixes.append(f"{github_prefix}/blob/")
                official_prefixes.append(f"{github_prefix}/tree/")

        for asset in assets:
            if asset.deleted:
                continue
            prefix = _normalize_prefix(asset.source_url)
            if prefix:
                official_prefixes.append(prefix)

        total = 0
        mention_count = 0
        citation_count = 0
        official_citation_count = 0
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
                    if official_prefixes:
                        for c in result.citations:
                            normalized = _normalize_prefix(c)
                            if normalized and any(normalized.startswith(prefix) for prefix in official_prefixes):
                                official_citation_count += 1
                                break
                position_sum += result.position
                if result.sentiment == Sentiment.positive:
                    positive_count += 1
                if result.sentiment == Sentiment.negative:
                    negative_count += 1
        if total == 0:
            return Metrics()
        mention_rate = mention_count / total
        citation_rate = citation_count / total
        official_citation_rate = official_citation_count / total
        sentiment_score = (positive_count - negative_count) / total
        average_position = position_sum / total
        share_of_voice = mention_rate
        citation_quality = citation_rate * 0.7 + max(0.0, sentiment_score) * 0.3
        return Metrics(
            mention_rate=round(mention_rate, 4),
            citation_rate=round(citation_rate, 4),
            official_citation_rate=round(official_citation_rate, 4),
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
            has_reputation_risk = any(
                (result.sentiment == Sentiment.negative) or bool(result.risk_tags) for result in prompt_result.results
            )
            if has_reputation_risk:
                category = "reputation_risk"
                description = "该提示词存在负面情感或风险标签，建议进行口碑修复与错误信息治理，并补齐权威引用来源。"
            elif mention_ratio < 0.6 and citation_ratio >= 0.5:
                category = "mention_gap"
                description = "该提示词提及偏低，建议补充定义、对比与关键事实块，提升被稳定提及的概率。"
            elif citation_ratio < 0.5 and mention_ratio >= 0.6:
                category = "citation_gap"
                description = "该提示词引用偏低，建议补充可被 AI 引用的官方来源与第三方权威来源，并增强可抽取结构。"
            else:
                category = "mixed_gap"
                description = "该提示词在提及或引用表现偏低，建议补充定义、FAQ 与权威引用来源。"
            priority = round((1 - mention_ratio) * 0.6 + (1 - citation_ratio) * 0.4, 4)
            evidence = _build_prompt_evidence(run, prompt_result, mention_ratio=mention_ratio, citation_ratio=citation_ratio)
            insight = Insight(
                insight_id=uuid.uuid4().hex,
                project_id=project_id,
                title=f"优化提示词：{prompt_result.prompt_content[:24]}",
                description=description,
                category=category,
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
                category="mixed_gap",
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
            playbook_type=insight.category,
            markdown_draft=markdown,
            estimated_impact={"mention_rate": 0.08, "citation_rate": 0.1},
            risk_level="high" if insight.category == "reputation_risk" else "medium",
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
            "official_citation_rate": round(after.metrics.official_citation_rate - baseline.metrics.official_citation_rate, 4),
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

        now = datetime.utcnow()
        report_id = uuid.uuid4().hex
        alert_ids: List[str] = []
        if alerts:
            existing = self.repository.list_project_alerts(project_id)
            active = [item for item in existing if item.status not in (AlertStatus.resolved, AlertStatus.closed)]
            for alert_type in alerts:
                fingerprint = f"{project_id}:{alert_type}"
                matched: Alert | None = None
                for item in active:
                    if item.fingerprint == fingerprint:
                        matched = item
                        break
                if matched is None:
                    created = Alert(
                        alert_id=uuid.uuid4().hex,
                        project_id=project_id,
                        report_id=report_id,
                        run_id=run_id,
                        alert_type=alert_type,
                        fingerprint=fingerprint,
                        status=AlertStatus.open,
                        created_at=now,
                        updated_at=now,
                        last_seen_at=now,
                    )
                    self.repository.save_alert(created)
                    alert_ids.append(created.alert_id)
                else:
                    matched.report_id = report_id
                    matched.run_id = run_id
                    matched.last_seen_at = now
                    matched.updated_at = now
                    self.repository.save_alert(matched)
                    alert_ids.append(matched.alert_id)

        report = MonitorReport(
            report_id=report_id,
            project_id=project_id,
            run_id=run_id,
            alerts=alerts,
            alert_ids=alert_ids,
            summary=summary,
            created_at=now,
        )
        self.repository.save_monitor_report(report)
        if alert_ids:
            self._write_audit_log(
                project_id=project_id,
                module="monitor",
                event="alert.upserted",
                message=f"alerts upserted count={len(alert_ids)}",
                run_id=run_id,
                attributes={"count": len(alert_ids), "alertIds": alert_ids, "alertTypes": alerts},
            )
        return report

    def list_alerts(self, project_id: str, *, status: AlertStatus | None = None) -> List[Alert]:
        items = self.repository.list_project_alerts(project_id)
        if status is not None:
            items = [item for item in items if item.status == status]
        items.sort(key=lambda item: (item.status.value, item.last_seen_at), reverse=True)
        return items

    def get_alert(self, project_id: str, alert_id: str) -> Alert:
        alert = self.repository.get_alert(alert_id)
        if alert is None or alert.project_id != project_id:
            raise ValueError("alert not found")
        return alert

    def update_alert(self, project_id: str, alert_id: str, payload: AlertUpdate) -> Alert:
        alert = self.get_alert(project_id, alert_id)
        changed = False
        if payload.status is not None and alert.status != payload.status:
            alert.status = payload.status
            changed = True
        if payload.assignee is not None and alert.assignee != payload.assignee:
            alert.assignee = payload.assignee
            changed = True
        if payload.note is not None:
            note = payload.note.strip()
            if note:
                alert.notes.append(note)
                changed = True
        if payload.closed_reason is not None and alert.closed_reason != payload.closed_reason:
            alert.closed_reason = payload.closed_reason
            changed = True
        if changed:
            alert.updated_at = datetime.utcnow()
            self.repository.save_alert(alert)
            self._write_audit_log(
                project_id=project_id,
                module="monitor",
                event="alert.updated",
                message="alert updated",
                attributes={"alertId": alert.alert_id, "status": alert.status.value, "assignee": alert.assignee},
            )
        return alert

    def create_github_pr_draft(self, project_id: str, payload: GitHubPRDraftCreate) -> GitHubPRDraft:
        project = self.repository.get_project(project_id)
        if project is None:
            raise ValueError("project not found")
        playbook = self.repository.get_playbook(payload.playbook_id)
        if playbook is None or playbook.project_id != project_id:
            raise ValueError("playbook not found")

        repo_url = (payload.repo_url or project.source_url or "").strip()
        if not repo_url:
            raise ValueError("repo_url required")
        if "github.com" not in repo_url:
            raise ValueError("repo_url must be a GitHub repository URL")

        base_branch = (payload.base_branch or "main").strip() or "main"
        head_branch = (payload.head_branch or f"opengeobot/{playbook.playbook_id[:8]}").strip()
        if not head_branch:
            head_branch = f"opengeobot/{playbook.playbook_id[:8]}"

        title = (payload.title or "").strip()
        if not title:
            insight = self.repository.get_insight(playbook.insight_id)
            if insight is not None:
                title = insight.title
            else:
                title = f"OpenGEO: Playbook {playbook.playbook_id[:8]}"

        body = (
            f"{playbook.markdown_draft}\n\n"
            "## 审批说明\n"
            "- 该 PR 为草稿，仅用于评审与协作\n"
            "- 需完成审批后方可执行后续自动化提交/发布\n\n"
            "## 追踪信息\n"
            f"- projectId: {project_id}\n"
            f"- playbookId: {playbook.playbook_id}\n"
            f"- playbookType: {playbook.playbook_type}\n"
        )

        now = datetime.utcnow()
        draft = GitHubPRDraft(
            draft_id=uuid.uuid4().hex,
            project_id=project_id,
            playbook_id=playbook.playbook_id,
            repo_url=repo_url,
            base_branch=base_branch,
            head_branch=head_branch,
            title=title,
            body_markdown=body,
            status=GitHubPRDraftStatus.pending_approval,
            created_at=now,
            updated_at=now,
        )
        self.repository.save_github_pr_draft(draft)
        self._write_audit_log(
            project_id=project_id,
            module="github",
            event="pr_draft.created",
            message="github pr draft created",
            attributes={
                "draftId": draft.draft_id,
                "playbookId": playbook.playbook_id,
                "repoUrl": repo_url,
                "baseBranch": base_branch,
                "headBranch": head_branch,
            },
        )
        return draft

    def list_github_pr_drafts(self, project_id: str, *, status: GitHubPRDraftStatus | None = None) -> List[GitHubPRDraft]:
        items = self.repository.list_project_github_pr_drafts(project_id)
        if status is not None:
            items = [item for item in items if item.status == status]
        items.sort(key=lambda item: item.created_at, reverse=True)
        return items

    def get_github_pr_draft(self, project_id: str, draft_id: str) -> GitHubPRDraft:
        draft = self.repository.get_github_pr_draft(draft_id)
        if draft is None or draft.project_id != project_id:
            raise ValueError("draft not found")
        return draft

    def approve_github_pr_draft(self, project_id: str, draft_id: str, payload: GitHubPRDraftApprove) -> GitHubPRDraft:
        draft = self.get_github_pr_draft(project_id, draft_id)
        if draft.status != GitHubPRDraftStatus.pending_approval:
            raise ValueError("draft not pending approval")
        now = datetime.utcnow()
        operator = get_operator()
        event = ApprovalEvent(
            event_id=uuid.uuid4().hex,
            operator=operator,
            decision=ApprovalDecision.approve,
            comment=(payload.comment or "").strip(),
            decided_at=now,
        )
        draft.status = GitHubPRDraftStatus.approved
        draft.approvals.append(event)
        draft.updated_at = now
        self.repository.save_github_pr_draft(draft)
        self._write_audit_log(
            project_id=project_id,
            module="github",
            event="pr_draft.approved",
            message="github pr draft approved",
            attributes={"draftId": draft.draft_id, "operator": operator},
        )
        return draft

    def reject_github_pr_draft(self, project_id: str, draft_id: str, payload: GitHubPRDraftApprove) -> GitHubPRDraft:
        draft = self.get_github_pr_draft(project_id, draft_id)
        if draft.status != GitHubPRDraftStatus.pending_approval:
            raise ValueError("draft not pending approval")
        now = datetime.utcnow()
        operator = get_operator()
        event = ApprovalEvent(
            event_id=uuid.uuid4().hex,
            operator=operator,
            decision=ApprovalDecision.reject,
            comment=(payload.comment or "").strip(),
            decided_at=now,
        )
        draft.status = GitHubPRDraftStatus.rejected
        draft.approvals.append(event)
        draft.updated_at = now
        self.repository.save_github_pr_draft(draft)
        self._write_audit_log(
            project_id=project_id,
            module="github",
            event="pr_draft.rejected",
            message="github pr draft rejected",
            attributes={"draftId": draft.draft_id, "operator": operator},
        )
        return draft

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
            playbook_type=playbook.playbook_type,
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
        type_stats: Dict[str, List[bool]] = {}
        for memory in memories:
            stats = type_stats.get(memory.playbook_type)
            if stats is None:
                stats = []
                type_stats[memory.playbook_type] = stats
            stats.append(bool(memory.success))
        type_success_rate: Dict[str, float] = {}
        for key, stats in type_stats.items():
            if not stats:
                continue
            type_success_rate[key] = sum(1 for s in stats if s) / len(stats)
        overall_success_rate = 0.0
        if memories:
            overall_success_rate = sum(1 for m in memories if m.success) / len(memories)
        assets = self.repository.list_project_assets(project_id)
        assets = [item for item in assets if not item.deleted]
        assets.sort(key=lambda item: (item.last_crawled_at or datetime.min), reverse=True)

        def _opportunity_score(item: Insight) -> float:
            rate = type_success_rate.get(item.category, overall_success_rate)
            return item.priority_score * (1.0 + 0.5 * rate)

        return {
            "project": project,
            "assets": assets,
            "latest_metrics": latest.metrics if latest else Metrics(),
            "trend": trend,
            "top_opportunities": sorted(insights, key=_opportunity_score, reverse=True)[:20],
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
