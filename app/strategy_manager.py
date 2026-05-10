"""
功能：Phase 3.5 - 跨项目策略复用与行业 Benchmark 管理器
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional

from app.strategy_models import (
    BenchmarkAnalysisCreate,
    BenchmarkMetric,
    BenchmarkReport,
    Industry,
    IndustryInsight,
    ProjectBenchmark,
    StrategyCategory,
    StrategyEffectiveness,
    StrategyInstance,
    StrategyInstanceCreate,
    StrategyRecommendation,
    StrategyTemplate,
    StrategyTemplateCreate,
    StrategyTemplateUpdate,
)


class StrategyManager:
    """跨项目策略复用与行业 Benchmark 管理器"""
    
    def __init__(self) -> None:
        self._templates: Dict[str, StrategyTemplate] = {}
        self._instances: Dict[str, StrategyInstance] = {}
        self._benchmark_reports: Dict[str, BenchmarkReport] = {}
        self._project_benchmarks: Dict[str, ProjectBenchmark] = {}
        self._industry_insights: Dict[str, IndustryInsight] = {}
        self._recommendations: Dict[str, StrategyRecommendation] = {}
        self._project_strategies: Dict[str, List[str]] = {}  # project_id -> [instance_ids]
    
    # ==================== 策略模板管理 ====================
    
    def create_template(self, payload: StrategyTemplateCreate, created_by: str = "") -> StrategyTemplate:
        template_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        template = StrategyTemplate(
            template_id=template_id,
            name=payload.name,
            description=payload.description,
            category=payload.category,
            applicable_scenarios=payload.applicable_scenarios,
            steps=payload.steps,
            required_resources=payload.required_resources,
            expected_outcomes=payload.expected_outcomes,
            tags=payload.tags,
            created_by=created_by,
            is_public=payload.is_public,
            created_at=now,
        )
        
        self._templates[template_id] = template
        return template
    
    def get_template(self, template_id: str) -> Optional[StrategyTemplate]:
        template = self._templates.get(template_id)
        if template and not template.deleted:
            return template
        return None
    
    def list_templates(
        self,
        category: Optional[StrategyCategory] = None,
        effectiveness: Optional[StrategyEffectiveness] = None,
        is_public: Optional[bool] = None,
        tags: Optional[List[str]] = None,
    ) -> List[StrategyTemplate]:
        templates = [t for t in self._templates.values() if not t.deleted]
        
        if category:
            templates = [t for t in templates if t.category == category]
        if effectiveness:
            templates = [t for t in templates if t.effectiveness == effectiveness]
        if is_public is not None:
            templates = [t for t in templates if t.is_public == is_public]
        if tags:
            templates = [t for t in templates if any(tag in t.tags for tag in tags)]
        
        templates.sort(key=lambda x: x.success_rate, reverse=True)
        return templates
    
    def update_template(self, template_id: str, payload: StrategyTemplateUpdate) -> Optional[StrategyTemplate]:
        template = self.get_template(template_id)
        if not template:
            return None
        
        if payload.name is not None:
            template.name = payload.name
        if payload.description is not None:
            template.description = payload.description
        if payload.category is not None:
            template.category = payload.category
        if payload.effectiveness is not None:
            template.effectiveness = payload.effectiveness
        if payload.applicable_scenarios is not None:
            template.applicable_scenarios = payload.applicable_scenarios
        if payload.steps is not None:
            template.steps = payload.steps
        if payload.tags is not None:
            template.tags = payload.tags
        if payload.is_public is not None:
            template.is_public = payload.is_public
        
        return template
    
    def delete_template(self, template_id: str) -> bool:
        template = self.get_template(template_id)
        if not template:
            return False
        
        template.deleted = True
        return True
    
    def record_template_success(self, template_id: str, success: bool) -> bool:
        """记录策略使用结果"""
        template = self.get_template(template_id)
        if not template:
            return False
        
        template.usage_count += 1
        if success:
            current_success = template.success_rate * (template.usage_count - 1) + 1
            template.success_rate = current_success / template.usage_count
        else:
            current_success = template.success_rate * (template.usage_count - 1)
            template.success_rate = current_success / template.usage_count
        
        # 更新效果评估
        if template.success_rate >= 0.8:
            template.effectiveness = StrategyEffectiveness.highly_effective
        elif template.success_rate >= 0.6:
            template.effectiveness = StrategyEffectiveness.effective
        elif template.success_rate >= 0.4:
            template.effectiveness = StrategyEffectiveness.moderately_effective
        else:
            template.effectiveness = StrategyEffectiveness.ineffective
        
        return True
    
    # ==================== 策略实例管理 ====================
    
    def create_instance(self, payload: StrategyInstanceCreate, created_by: str = "") -> StrategyInstance:
        template = self.get_template(payload.template_id)
        if not template:
            raise ValueError("Strategy template not found")
        
        instance_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        instance = StrategyInstance(
            instance_id=instance_id,
            template_id=payload.template_id,
            project_id=payload.project_id,
            customizations=payload.customizations,
            started_at=now,
            created_at=now,
            created_by=created_by,
        )
        
        self._instances[instance_id] = instance
        if payload.project_id not in self._project_strategies:
            self._project_strategies[payload.project_id] = []
        self._project_strategies[payload.project_id].append(instance_id)
        
        return instance
    
    def get_instance(self, instance_id: str) -> Optional[StrategyInstance]:
        return self._instances.get(instance_id)
    
    def list_instances(self, project_id: Optional[str] = None, status: Optional[str] = None) -> List[StrategyInstance]:
        instances = list(self._instances.values())
        
        if project_id:
            instances = [i for i in instances if i.project_id == project_id]
        if status:
            instances = [i for i in instances if i.status == status]
        
        return instances
    
    def update_instance_status(self, instance_id: str, status: str, results: Optional[Dict[str, Any]] = None) -> Optional[StrategyInstance]:
        instance = self.get_instance(instance_id)
        if not instance:
            return None
        
        instance.status = status
        if status == "completed":
            instance.completed_at = datetime.utcnow()
        if results:
            instance.results = results
        
        return instance
    
    # ==================== 行业 Benchmark ====================
    
    def create_benchmark_report(self, industry: Industry, metrics: List[Dict[str, Any]], region: str = "global") -> BenchmarkReport:
        report_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        benchmark_metrics = [
            BenchmarkMetric(
                metric_name=m["metric_name"],
                industry_average=m["industry_average"],
                top_quartile=m["top_quartile"],
                median=m["median"],
                bottom_quartile=m["bottom_quartile"],
                sample_size=m.get("sample_size", 100),
                last_updated=now,
            )
            for m in metrics
        ]
        
        report = BenchmarkReport(
            report_id=report_id,
            industry=industry,
            region=region,
            metrics=benchmark_metrics,
            sample_projects=len(metrics),
            data_collection_period="Last 90 days",
            created_at=now,
        )
        
        self._benchmark_reports[report_id] = report
        return report
    
    def get_benchmark_report(self, report_id: str) -> Optional[BenchmarkReport]:
        return self._benchmark_reports.get(report_id)
    
    def list_benchmark_reports(self, industry: Optional[Industry] = None) -> List[BenchmarkReport]:
        reports = list(self._benchmark_reports.values())
        if industry:
            reports = [r for r in reports if r.industry == industry]
        return reports
    
    def analyze_project_benchmark(self, payload: BenchmarkAnalysisCreate, project_metrics: Dict[str, float]) -> ProjectBenchmark:
        """分析项目与行业基准的对比"""
        # 获取行业基准报告
        reports = self.list_benchmark_reports(industry=payload.industry)
        if not reports:
            raise ValueError(f"No benchmark data for industry {payload.industry.value}")
        
        report = reports[0]  # 使用最新的报告
        now = datetime.utcnow()
        
        metrics_comparison = {}
        strengths = []
        weaknesses = []
        total_score = 0.0
        metric_count = 0
        
        for metric in report.metrics:
            if metric.metric_name in project_metrics:
                project_value = project_metrics[metric.metric_name]
                industry_avg = metric.industry_average
                
                # 计算百分位（简化）
                if project_value >= metric.top_quartile:
                    percentile = 90.0
                elif project_value >= metric.median:
                    percentile = 70.0
                elif project_value >= metric.bottom_quartile:
                    percentile = 50.0
                else:
                    percentile = 25.0
                
                metrics_comparison[metric.metric_name] = {
                    "project_value": project_value,
                    "industry_avg": industry_avg,
                    "percentile": percentile,
                }
                
                # 识别强弱项
                if project_value > industry_avg * 1.1:
                    strengths.append(f"{metric.metric_name} 高于行业平均水平")
                elif project_value < industry_avg * 0.9:
                    weaknesses.append(f"{metric.metric_name} 低于行业平均水平")
                
                total_score += percentile
                metric_count += 1
        
        overall_score = total_score / metric_count if metric_count > 0 else 0.0
        
        # 生成建议
        recommendations = []
        for weakness in weaknesses:
            recommendations.append(f"改进{weakness.split(' ')[0]}")
        
        benchmark = ProjectBenchmark(
            project_id=payload.project_id,
            industry=payload.industry,
            metrics_comparison=metrics_comparison,
            overall_score=min(100.0, overall_score),
            strengths=strengths,
            weaknesses=weaknesses,
            recommendations=recommendations,
            analyzed_at=now,
        )
        
        self._project_benchmarks[payload.project_id] = benchmark
        return benchmark
    
    def get_project_benchmark(self, project_id: str) -> Optional[ProjectBenchmark]:
        return self._project_benchmarks.get(project_id)
    
    # ==================== 策略推荐 ====================
    
    def generate_recommendations(self, project_id: str, industry: Industry) -> List[StrategyRecommendation]:
        """基于项目情况和行业生成策略推荐"""
        now = datetime.utcnow()
        recommendations = []
        
        # 获取公开策略模板
        public_templates = self.list_templates(is_public=True)
        
        for template in public_templates:
            # 计算推荐分数
            priority = 3
            if template.effectiveness in [StrategyEffectiveness.highly_effective, StrategyEffectiveness.effective]:
                priority = min(5, priority + 1)
            
            recommendation = StrategyRecommendation(
                recommendation_id=uuid.uuid4().hex,
                project_id=project_id,
                strategy_template_id=template.template_id,
                strategy_name=template.name,
                description=template.description,
                category=template.category,
                expected_impact=template.expected_outcomes,
                implementation_effort="medium" if len(template.steps) <= 5 else "high",
                priority=priority,
                rationale=f"基于{industry.value}行业最佳实践推荐",
                similar_projects_count=template.usage_count,
                average_success_rate=template.success_rate,
                created_at=now,
            )
            
            recommendations.append(recommendation)
            self._recommendations[recommendation.recommendation_id] = recommendation
        
        recommendations.sort(key=lambda x: x.priority, reverse=True)
        return recommendations
    
    def get_recommendations(self, project_id: str) -> List[StrategyRecommendation]:
        recs = [r for r in self._recommendations.values() if r.project_id == project_id]
        recs.sort(key=lambda x: x.priority, reverse=True)
        return recs
    
    # ==================== 行业洞察 ====================
    
    def create_industry_insight(self, industry: Industry, title: str, description: str, data_points: List[Dict[str, Any]], trends: List[str], best_practices: List[str]) -> IndustryInsight:
        insight_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        insight = IndustryInsight(
            insight_id=insight_id,
            industry=industry,
            title=title,
            description=description,
            data_points=data_points,
            trends=trends,
            best_practices=best_practices,
            confidence_score=0.85,
            published_at=now,
        )
        
        self._industry_insights[insight_id] = insight
        return insight
    
    def list_industry_insights(self, industry: Optional[Industry] = None) -> List[IndustryInsight]:
        insights = list(self._industry_insights.values())
        if industry:
            insights = [i for i in insights if i.industry == industry]
        insights.sort(key=lambda x: x.published_at, reverse=True)
        return insights


# 全局策略管理器实例
strategy_manager = StrategyManager()
