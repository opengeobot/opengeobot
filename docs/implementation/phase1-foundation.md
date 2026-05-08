# Phase 1：平台基础能力实施说明

<!--
作者：AxeXie
创建时间：2026-05-08 13:00:00
-->

## 1. 阶段目标

落地统一数据字典、统一日志、统一系统配置、国际化能力，形成全平台复用底座。

## 2. 已落地交付

### 2.1 统一数据字典中心

- 提供模板：`schemas/data-dictionary.template.json`
- 覆盖能力：
  - 指标定义（`metricDefinitions`）
  - 字段口径（`fieldDefinitions`）
  - 枚举值（`enumerations`）
  - 状态机（`stateMachines`）
  - 单位换算（`unitConversions`）
  - 版本变更（`version`、`changeLog`）

### 2.2 统一日志规范

- 提供规范：`observability/log-field-spec.md`
- 强制字段：
  - `traceId`
  - `projectId`
  - `runId`
  - `operator`
- 统一日志级别、脱敏规则、采样规则和留存策略。

### 2.3 统一系统配置中心

- 提供模板：`configs/examples/system-config.default.yaml`
- 支持能力：
  - 环境级配置（`global`）
  - 租户级覆盖（`tenants`）
  - 特性开关（`featureFlags`）
  - 灰度发布与回滚（`release`、`rollback`）
  - 变更审计（`audit`）

### 2.4 国际化（i18n）基础能力

- 资源目录：`i18n/`
- 文档规范：`i18n/README.md`
- 默认语言：`zh-CN`
- 二语示例：`en-US`
- 支持回退、命名空间和版本追踪。

## 3. 门禁验收结论

- [x] 新增指标/状态/枚举可集中维护并复用
- [x] 日志支持 trace 端到端关联
- [x] 配置支持统一读取、灰度发布与回滚
- [x] 用户级默认语言与报告/通知国际化能力具备

## 4. 下一阶段输入

Phase 2 所有模块必须通过标准底座接入，不允许绕过：

- 不允许业务模块定义私有指标口径。
- 不允许业务模块绕过日志 SDK 直接写日志。
- 不允许业务模块私有化关键配置。
- 不允许新增 UI 文案绕过 i18n key。
