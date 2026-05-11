"""
功能：Feature Flags 实现
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

from enum import Enum
from typing import Dict

from fastapi import HTTPException

from app.foundation import ConfigCenter


class FeatureFlag(str, Enum):
    """Feature Flag 枚举"""
    ENABLE_BOT_DRAFT_GENERATION = "enableBotDraftGeneration"
    ENABLE_AUTO_MONITOR_REPORT = "enableAutoMonitorReport"
    ENABLE_STRATEGY_MEMORY_RANKING = "enableStrategyMemoryRanking"
    ENABLE_MULTI_TENANT_CONFIG_OVERRIDE = "enableMultiTenantConfigOverride"


class FeatureFlagChecker:
    """Feature Flag 检查器"""
    
    def __init__(self, config_center: ConfigCenter) -> None:
        self.config_center = config_center
    
    def is_enabled(self, flag_name: str, *, default: bool = False) -> bool:
        """检查指定 flag 是否启用"""
        return self.config_center.get(f"featureFlags.{flag_name}", default)
    
    def get_all_flags(self) -> Dict[str, bool]:
        """获取所有 feature flags 的状态"""
        flags = self.config_center.get("featureFlags", {})
        if not isinstance(flags, dict):
            return {}
        return {
            FeatureFlag.ENABLE_BOT_DRAFT_GENERATION: flags.get(
                FeatureFlag.ENABLE_BOT_DRAFT_GENERATION, False
            ),
            FeatureFlag.ENABLE_AUTO_MONITOR_REPORT: flags.get(
                FeatureFlag.ENABLE_AUTO_MONITOR_REPORT, False
            ),
            FeatureFlag.ENABLE_STRATEGY_MEMORY_RANKING: flags.get(
                FeatureFlag.ENABLE_STRATEGY_MEMORY_RANKING, False
            ),
            FeatureFlag.ENABLE_MULTI_TENANT_CONFIG_OVERRIDE: flags.get(
                FeatureFlag.ENABLE_MULTI_TENANT_CONFIG_OVERRIDE, False
            ),
        }


def require_feature(flag_name: str) -> None:
    """
    FastAPI 依赖函数：检查 feature flag 是否启用
    如果未启用，抛出 HTTPException(501)
    """
    # 这个函数会在 main.py 中被实例化的 checker 调用
    # 这里只是定义接口，实际检查在 main.py 中进行
    pass
