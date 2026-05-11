"""
功能：Phase 3.4 - 自动实验设计与效果归因管理器
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

import uuid
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional

from app.experiment_models import (
    AttributionAnalysisCreate,
    AttributionModel,
    AttributionResult,
    ExperimentDesign,
    ExperimentDesignCreate,
    ExperimentDesignUpdate,
    ExperimentResult,
    ExperimentSuggestion,
    ExperimentSuggestionCreate,
    ExperimentStatus,
    ExperimentType,
    MetricGoal,
    Touchpoint,
    VariantConfig,
    VariantResult,
)


class ExperimentManager:
    """实验设计与归因管理器"""
    
    def __init__(self) -> None:
        self._experiments: Dict[str, ExperimentDesign] = {}
        self._results: Dict[str, ExperimentResult] = {}
        self._attributions: Dict[str, AttributionResult] = {}
        self._suggestions: Dict[str, ExperimentSuggestion] = {}
        self._project_experiments: Dict[str, List[str]] = {}  # project_id -> [experiment_ids]
    
    # ==================== 实验设计管理 ====================
    
    def create_experiment(self, payload: ExperimentDesignCreate) -> ExperimentDesign:
        experiment_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        # 验证变体权重总和为 1.0
        total_weight = sum(v.weight for v in payload.variants)
        if abs(total_weight - 1.0) > 0.01:
            raise ValueError("Variant weights must sum to 1.0")
        
        # 确保有控制组
        has_control = any(v.is_control for v in payload.variants)
        if not has_control and payload.experiment_type == ExperimentType.ab_test:
            raise ValueError("A/B test requires a control variant")
        
        experiment = ExperimentDesign(
            experiment_id=experiment_id,
            project_id=payload.project_id,
            name=payload.name,
            description=payload.description,
            experiment_type=payload.experiment_type,
            hypothesis=payload.hypothesis,
            variants=payload.variants,
            goals=payload.goals,
            duration_days=payload.duration_days,
            confidence_level=payload.confidence_level,
            min_detectable_effect=payload.min_detectable_effect,
            start_date=now,
            created_at=now,
        )
        
        self._experiments[experiment_id] = experiment
        if payload.project_id not in self._project_experiments:
            self._project_experiments[payload.project_id] = []
        self._project_experiments[payload.project_id].append(experiment_id)
        
        return experiment
    
    def get_experiment(self, experiment_id: str) -> Optional[ExperimentDesign]:
        experiment = self._experiments.get(experiment_id)
        if experiment and not experiment.deleted:
            return experiment
        return None
    
    def list_experiments(
        self,
        project_id: Optional[str] = None,
        status: Optional[ExperimentStatus] = None,
        experiment_type: Optional[ExperimentType] = None,
    ) -> List[ExperimentDesign]:
        experiments = [e for e in self._experiments.values() if not e.deleted]
        
        if project_id:
            experiments = [e for e in experiments if e.project_id == project_id]
        if status:
            experiments = [e for e in experiments if e.status == status]
        if experiment_type:
            experiments = [e for e in experiments if e.experiment_type == experiment_type]
        
        return experiments
    
    def update_experiment(self, experiment_id: str, payload: ExperimentDesignUpdate) -> Optional[ExperimentDesign]:
        experiment = self.get_experiment(experiment_id)
        if not experiment:
            return None
        
        if payload.name is not None:
            experiment.name = payload.name
        if payload.description is not None:
            experiment.description = payload.description
        if payload.hypothesis is not None:
            experiment.hypothesis = payload.hypothesis
        if payload.variants is not None:
            experiment.variants = payload.variants
        if payload.goals is not None:
            experiment.goals = payload.goals
        if payload.duration_days is not None:
            experiment.duration_days = payload.duration_days
        if payload.status is not None:
            experiment.status = payload.status
            if payload.status == ExperimentStatus.running:
                experiment.start_date = datetime.utcnow()
            elif payload.status in [ExperimentStatus.completed, ExperimentStatus.cancelled]:
                experiment.completed_at = datetime.utcnow()
        
        experiment.updated_at = datetime.utcnow()
        return experiment
    
    def delete_experiment(self, experiment_id: str) -> bool:
        experiment = self.get_experiment(experiment_id)
        if not experiment:
            return False
        
        experiment.deleted = True
        experiment.updated_at = datetime.utcnow()
        return True
    
    # ==================== 实验结果管理 ====================
    
    def create_result(self, experiment_id: str, variants_results: List[VariantResult]) -> ExperimentResult:
        experiment = self.get_experiment(experiment_id)
        if not experiment:
            raise ValueError("Experiment not found")
        
        result_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        # 确定获胜变体
        winner_variant = None
        max_conversion = -1.0
        for vr in variants_results:
            if vr.conversion_rate > max_conversion:
                max_conversion = vr.conversion_rate
                winner_variant = vr
        
        # 计算统计显著性（简化模拟）
        total_sample = sum(vr.sample_size for vr in variants_results)
        statistical_power = min(0.99, total_sample / 10000.0)
        
        # 生成建议
        recommendations = self._generate_recommendations(experiment, variants_results, winner_variant)
        
        result = ExperimentResult(
            result_id=result_id,
            experiment_id=experiment_id,
            status="completed",
            winner_variant_id=winner_variant.variant_id if winner_variant else None,
            variants=variants_results,
            statistical_power=statistical_power,
            p_value=0.05 if statistical_power > 0.8 else 0.10,
            confidence_level=experiment.confidence_level,
            recommendations=recommendations,
            analyzed_at=now,
            created_at=now,
        )
        
        self._results[result_id] = result
        experiment.status = ExperimentStatus.completed
        experiment.completed_at = now
        
        return result
    
    def get_result(self, result_id: str) -> Optional[ExperimentResult]:
        return self._results.get(result_id)
    
    def list_results(self, experiment_id: Optional[str] = None) -> List[ExperimentResult]:
        if experiment_id:
            return [r for r in self._results.values() if r.experiment_id == experiment_id]
        return list(self._results.values())
    
    # ==================== 归因分析 ====================
    
    def create_attribution(self, payload: AttributionAnalysisCreate, touchpoints: List[Dict[str, Any]]) -> AttributionResult:
        attribution_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        # 转换接触点
        converted_touchpoints = [
            Touchpoint(
                touchpoint_id=uuid.uuid4().hex,
                channel=tp.get("channel", "unknown"),
                action=tp.get("action", "view"),
                timestamp=datetime.fromisoformat(tp["timestamp"]) if isinstance(tp.get("timestamp"), str) else tp.get("timestamp", now),
                weight=tp.get("weight", 1.0),
                metadata=tp.get("metadata", {}),
            )
            for tp in touchpoints
        ]
        
        # 计算渠道贡献
        channel_contributions = self._calculate_attribution(converted_touchpoints, payload.model)
        
        attribution = AttributionResult(
            attribution_id=attribution_id,
            project_id=payload.project_id,
            experiment_id=payload.experiment_id,
            model=payload.model,
            touchpoints=converted_touchpoints,
            channel_contributions=channel_contributions,
            total_conversions=len(converted_touchpoints),
            total_value=sum(tp.weight for tp in converted_touchpoints),
            analyzed_at=now,
        )
        
        self._attributions[attribution_id] = attribution
        return attribution
    
    def get_attribution(self, attribution_id: str) -> Optional[AttributionResult]:
        return self._attributions.get(attribution_id)
    
    def list_attributions(self, project_id: Optional[str] = None) -> List[AttributionResult]:
        if project_id:
            return [a for a in self._attributions.values() if a.project_id == project_id]
        return list(self._attributions.values())
    
    def _calculate_attribution(self, touchpoints: List[Touchpoint], model: AttributionModel) -> Dict[str, float]:
        """计算渠道归因"""
        if not touchpoints:
            return {}
        
        channel_touchpoints: Dict[str, List[Touchpoint]] = {}
        for tp in touchpoints:
            if tp.channel not in channel_touchpoints:
                channel_touchpoints[tp.channel] = []
            channel_touchpoints[tp.channel].append(tp)
        
        total_weight = sum(tp.weight for tp in touchpoints)
        contributions = {}
        
        if model == AttributionModel.first_touch:
            first_tp = min(touchpoints, key=lambda x: x.timestamp)
            contributions[first_tp.channel] = 1.0
        
        elif model == AttributionModel.last_touch:
            last_tp = max(touchpoints, key=lambda x: x.timestamp)
            contributions[last_tp.channel] = 1.0
        
        elif model == AttributionModel.linear:
            for channel, tps in channel_touchpoints.items():
                channel_weight = sum(tp.weight for tp in tps)
                contributions[channel] = channel_weight / total_weight if total_weight > 0 else 0.0
        
        elif model == AttributionModel.time_decay:
            now = datetime.utcnow()
            for channel, tps in channel_touchpoints.items():
                decayed_weight = sum(
                    tp.weight * (0.5 ** ((now - tp.timestamp).total_seconds() / 86400))
                    for tp in tps
                )
                contributions[channel] = decayed_weight / total_weight if total_weight > 0 else 0.0
        
        elif model == AttributionModel.position_based:
            if len(touchpoints) == 1:
                contributions[touchpoints[0].channel] = 1.0
            else:
                first_tp = min(touchpoints, key=lambda x: x.timestamp)
                last_tp = max(touchpoints, key=lambda x: x.timestamp)
                contributions[first_tp.channel] = 0.4
                contributions[last_tp.channel] = contributions.get(last_tp.channel, 0) + 0.4
                for tp in touchpoints:
                    if tp != first_tp and tp != last_tp:
                        contributions[tp.channel] = contributions.get(tp.channel, 0) + (0.2 / (len(touchpoints) - 2))
        
        return contributions
    
    # ==================== 自动实验建议 ====================
    
    def create_suggestion(self, payload: ExperimentSuggestionCreate) -> ExperimentSuggestion:
        suggestion_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        suggestion = ExperimentSuggestion(
            suggestion_id=suggestion_id,
            project_id=payload.project_id,
            title=payload.title,
            description=payload.description,
            rationale=payload.rationale,
            expected_impact=payload.expected_impact,
            priority=payload.priority,
            suggested_type=payload.suggested_type,
            suggested_variants=payload.suggested_variants,
            suggested_metrics=payload.suggested_metrics,
            created_at=now,
        )
        
        self._suggestions[suggestion_id] = suggestion
        return suggestion
    
    def get_suggestion(self, suggestion_id: str) -> Optional[ExperimentSuggestion]:
        return self._suggestions.get(suggestion_id)
    
    def list_suggestions(self, project_id: Optional[str] = None, include_dismissed: bool = False) -> List[ExperimentSuggestion]:
        suggestions = [s for s in self._suggestions.values()]
        
        if project_id:
            suggestions = [s for s in suggestions if s.project_id == project_id]
        if not include_dismissed:
            suggestions = [s for s in suggestions if not s.dismissed]
        
        suggestions.sort(key=lambda x: x.priority, reverse=True)
        return suggestions
    
    def dismiss_suggestion(self, suggestion_id: str) -> bool:
        suggestion = self.get_suggestion(suggestion_id)
        if not suggestion:
            return False
        
        suggestion.dismissed = True
        return True
    
    # ==================== 辅助方法 ====================
    
    def _generate_recommendations(
        self,
        experiment: ExperimentDesign,
        variants_results: List[VariantResult],
        winner: Optional[VariantResult],
    ) -> List[str]:
        """生成实验建议"""
        recommendations = []
        
        if winner:
            recommendations.append(f"推荐采用变体 '{winner.variant_name}'，转化率为 {winner.conversion_rate:.2%}")
        
        if len(variants_results) >= 2:
            max_diff = max(vr.conversion_rate for vr in variants_results) - min(vr.conversion_rate for vr in variants_results)
            if max_diff < experiment.min_detectable_effect:
                recommendations.append("变体间差异未达到最小可检测效应，建议增加样本量")
        
        for goal in experiment.goals:
            if winner and goal.metric_name in winner.metric_values:
                actual_value = winner.metric_values[goal.metric_name]
                if goal.direction == "increase" and actual_value < goal.target_value:
                    recommendations.append(f"指标 '{goal.metric_name}' 未达到目标值 {goal.target_value}")
        
        return recommendations
    
    def auto_generate_suggestions(self, project_id: str) -> List[ExperimentSuggestion]:
        """自动生成实验建议（基于项目数据）"""
        suggestions = []
        
        # 这里可以接入实际的项目数据分析逻辑
        # 示例：基于现有运行数据生成建议
        
        suggestions.append(
            ExperimentSuggestionCreate(
                project_id=project_id,
                title="测试新引擎效果",
                description="对比不同 SEO 引擎对网站收录的影响",
                rationale="现有数据表明引擎表现存在差异，建议通过 A/B 测试验证",
                expected_impact="提升 10-20% 收录率",
                priority=3,
                suggested_type=ExperimentType.ab_test,
                suggested_variants=["当前引擎", "新引擎"],
                suggested_metrics=["indexed_pages", "crawl_rate"],
            )
        )
        
        suggestions.append(
            ExperimentSuggestionCreate(
                project_id=project_id,
                title="优化提示词模板",
                description="测试不同提示词模板对内容质量的影响",
                rationale="提示词是影响生成质量的关键因素，值得系统测试",
                expected_impact="提升内容质量评分",
                priority=4,
                suggested_type=ExperimentType.multivariate,
                suggested_variants=["模板 A", "模板 B", "模板 C"],
                suggested_metrics=["content_score", "engagement_rate"],
            )
        )
        
        created = []
        for suggestion in suggestions:
            created.append(self.create_suggestion(suggestion))
        
        return created


# 全局实验管理器实例
experiment_manager = ExperimentManager()
