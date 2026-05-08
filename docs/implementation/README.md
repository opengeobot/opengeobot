# OpenGEO Bot V1 实施交付总览

<!--
作者：AxeXie
创建时间：2026-05-08 12:55:00
-->

## 1. 说明

本目录用于承接 `docs/opengeobot-v1.md` 的分阶段实施结果，按 Phase 0~5 组织交付物、门禁标准和落地清单。

## 2. 阶段交付索引

- [Phase 0 启动与技术基线](./phase0-baseline.md)
- [Phase 1 平台基础能力](./phase1-foundation.md)
- [Phase 2 核心数据采集与评测链路](./phase2-core-run.md)
- [Phase 3 解析、洞察与 Bot 工作台](./phase3-insight-bot.md)
- [Phase 4 验证、监控、复盘与策略进化](./phase4-verify-monitor.md)
- [Phase 5 稳定性强化与上线验收](./phase5-go-live.md)

## 3. 关键规范与模板

- [需求追踪矩阵](./traceability-matrix.md)
- [统一日志字段规范](../../observability/log-field-spec.md)
- [统一数据字典模板](../../schemas/data-dictionary.template.json)
- [统一系统配置模板](../../configs/examples/system-config.default.yaml)
- [国际化资源规范](../../i18n/README.md)

## 4. 执行约束

- 必须遵循“底座先行，业务模块复用”的实施顺序。
- 任一阶段未通过门禁，不进入下一阶段。
- 所有新增能力都需要同步更新规范文档、测试清单和验收记录。
