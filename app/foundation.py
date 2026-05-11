"""
功能：统一配置、日志、数据字典、国际化基础能力
时间：2026-05-08 13:38:00
作者：AxeXie
"""

from __future__ import annotations

import json
import logging
import os
import uuid
from contextvars import ContextVar
from pathlib import Path
from typing import Any, Dict, List, Optional

import yaml
from pydantic import BaseModel, ConfigDict, Field

ROOT = Path(__file__).resolve().parent.parent

trace_id_context: ContextVar[str | None] = ContextVar("trace_id_context", default=None)
span_id_context: ContextVar[str | None] = ContextVar("span_id_context", default=None)
operator_context: ContextVar[str | None] = ContextVar("operator_context", default=None)


def get_trace_id() -> str | None:
    return trace_id_context.get()


def set_trace_id(trace_id: str | None):
    return trace_id_context.set(trace_id)


def reset_trace_id(token) -> None:
    trace_id_context.reset(token)


def get_span_id() -> str | None:
    return span_id_context.get()


def set_span_id(span_id: str | None):
    return span_id_context.set(span_id)


def reset_span_id(token) -> None:
    span_id_context.reset(token)


def get_operator() -> str | None:
    return operator_context.get()


def set_operator(operator: str | None):
    return operator_context.set(operator)


def reset_operator(token) -> None:
    operator_context.reset(token)


def _read_json(file_path: Path) -> Dict[str, Any]:
    if not file_path.exists():
        return {}
    return json.loads(file_path.read_text(encoding="utf-8"))


def _read_yaml(file_path: Path) -> Dict[str, Any]:
    if not file_path.exists():
        return {}
    return yaml.safe_load(file_path.read_text(encoding="utf-8")) or {}


def deep_merge_config(base: Any, override: Any) -> Any:
    if isinstance(base, dict) and isinstance(override, dict):
        merged: Dict[str, Any] = dict(base)
        for key, override_value in override.items():
            if key in merged:
                merged[key] = deep_merge_config(merged[key], override_value)
            else:
                merged[key] = override_value
        return merged
    return override



class ConfigCenter:
    def __init__(self, tenant_id: str | None = None) -> None:
        default_path = ROOT / "configs" / "examples" / "system-config.default.yaml"
        base = _read_yaml(default_path)
        if not isinstance(base, dict):
            base = {}

        override_path_raw = os.getenv("OPEN_GEOBOT_CONFIG_PATH")
        override: Dict[str, Any] = {}
        self.sources: Dict[str, str] = {"default": str(default_path)}
        if override_path_raw:
            override_path = Path(override_path_raw)
            if not override_path.is_absolute():
                override_path = (ROOT / override_path).resolve()
            if override_path.suffix.lower() == ".json":
                override = _read_json(override_path)
            else:
                override = _read_yaml(override_path)
            if isinstance(override, dict) and override:
                self.sources["override"] = str(override_path)
            else:
                override = {}

        self.config = deep_merge_config(base, override)
        self.tenant_id = tenant_id
    
    def set_tenant_context(self, tenant_id: str | None) -> None:
        """设置租户上下文"""
        self.tenant_id = tenant_id
    
    def get(self, key: str, default: Any = None) -> Any:
        current: Any = self.config
        for part in key.split("."):
            if not isinstance(current, dict) or part not in current:
                return default
            current = current[part]
        
        # 检查是否启用租户覆盖
        if self.tenant_id:
            enable_override = self.config.get("featureFlags", {}).get(
                "enableMultiTenantConfigOverride", False
            )
            if enable_override:
                # 查找租户覆盖配置
                tenant_override = self._get_tenant_override(self.tenant_id)
                if tenant_override:
                    # 应用租户覆盖
                    tenant_value = tenant_override
                    for part in key.split("."):
                        if isinstance(tenant_value, dict) and part in tenant_value:
                            tenant_value = tenant_value[part]
                        else:
                            tenant_value = None
                            break
                    if tenant_value is not None:
                        return tenant_value
        
        return current
    
    def _get_tenant_override(self, tenant_id: str) -> Dict[str, Any] | None:
        """获取指定租户的覆盖配置"""
        tenants = self.config.get("tenants", [])
        if not isinstance(tenants, list):
            return None
        
        for tenant in tenants:
            if isinstance(tenant, dict) and tenant.get("tenantId") == tenant_id:
                return tenant.get("overrides", {})
        
        return None


class MetricDefinition(BaseModel):
    model_config = ConfigDict(frozen=True)

    key: str
    displayName: Dict[str, str] = Field(default_factory=dict)
    description: Dict[str, str] = Field(default_factory=dict)
    formula: Optional[str] = None
    unit: Optional[str] = None
    precision: Optional[int] = None


class EnumDefinition(BaseModel):
    model_config = ConfigDict(frozen=True)

    key: str
    items: List[str] = Field(default_factory=list)


class DataDictionary:
    def __init__(self) -> None:
        self.data = _read_json(ROOT / "schemas" / "data-dictionary.template.json")
        self._dictionary_version = str(self.data.get("version") or "unknown")
        self._metric_definitions = self._parse_metric_definitions()
        self._enum_definitions = self._parse_enum_definitions()
        self._metric_definitions_by_key = {item.key: item for item in self._metric_definitions}
        self._enum_definitions_by_key = {item.key: item for item in self._enum_definitions}

    def _parse_metric_definitions(self) -> List[MetricDefinition]:
        raw = self.data.get("metricDefinitions", [])
        if not isinstance(raw, list):
            return []
        definitions: List[MetricDefinition] = []
        for item in raw:
            if isinstance(item, dict):
                definitions.append(MetricDefinition.model_validate(item))
        return definitions

    def _parse_enum_definitions(self) -> List[EnumDefinition]:
        raw = self.data.get("enumerations", [])
        if not isinstance(raw, list):
            return []
        definitions: List[EnumDefinition] = []
        for item in raw:
            if isinstance(item, dict):
                definitions.append(EnumDefinition.model_validate(item))
        return definitions

    @property
    def dictionary_version(self) -> str:
        return self._dictionary_version

    @property
    def metricDefinitions(self) -> List[MetricDefinition]:
        return self._metric_definitions

    @property
    def enumDefinitions(self) -> List[EnumDefinition]:
        return self._enum_definitions

    def get_metric_definition(self, key: str) -> Optional[MetricDefinition]:
        return self._metric_definitions_by_key.get(key)

    def get_enum_definition(self, key: str) -> Optional[EnumDefinition]:
        return self._enum_definitions_by_key.get(key)

    def metric_keys(self) -> list[str]:
        return [item.key for item in self._metric_definitions]


class I18N:
    def __init__(self) -> None:
        self.zh = _read_json(ROOT / "i18n" / "zh-CN.json")
        self.en = _read_json(ROOT / "i18n" / "en-US.json")

    def t(self, key: str, language: str = "zh-CN") -> str:
        if language == "en-US":
            return self.en.get(key) or self.zh.get(key) or key
        return self.zh.get(key) or self.en.get(key) or key


def build_logger() -> logging.Logger:
    logger = logging.getLogger("opengeobot")
    if logger.handlers:
        return logger
    logger.setLevel(logging.INFO)
    handler = logging.StreamHandler()
    formatter = logging.Formatter(
        "%(asctime)s %(levelname)s traceId=%(traceId)s spanId=%(spanId)s projectId=%(projectId)s "
        "runId=%(runId)s operator=%(operator)s module=%(logModule)s event=%(event)s %(message)s"
    )
    handler.setFormatter(formatter)
    handler.addFilter(_ContextLogFilter())
    logger.addHandler(handler)
    return logger


class _ContextLogFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.traceId = getattr(record, "traceId", None) or get_trace_id() or "-"
        record.spanId = getattr(record, "spanId", None) or get_span_id() or "-"
        record.projectId = getattr(record, "projectId", None) or "-"
        record.runId = getattr(record, "runId", None) or "-"
        record.operator = getattr(record, "operator", None) or get_operator() or "system"
        record.logModule = getattr(record, "logModule", None) or "core"
        record.event = getattr(record, "event", None) or "event"
        return True


def log_with_context(
    logger: logging.Logger,
    level: int,
    message: str,
    *,
    trace_id: str | None = None,
    span_id: str | None = None,
    project_id: str = "-",
    run_id: str = "-",
    operator: str | None = None,
    module: str = "core",
    event: str = "event",
) -> str:
    actual_trace_id = trace_id or get_trace_id() or uuid.uuid4().hex
    actual_span_id = span_id or get_span_id() or "-"
    actual_operator = operator or get_operator() or "system"
    logger.log(
        level,
        message,
        extra={
            "traceId": actual_trace_id,
            "spanId": actual_span_id,
            "projectId": project_id,
            "runId": run_id,
            "operator": actual_operator,
            "logModule": module,
            "event": event,
        },
    )
    return actual_trace_id
