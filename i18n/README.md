# OpenGEO Bot 国际化资源规范（V1）

<!--
作者：AxeXie
创建时间：2026-05-08 13:04:00
-->

## 1. 目录结构

- `i18n/zh-CN.json`：默认语言资源
- `i18n/en-US.json`：英文资源

## 2. Key 命名规范

- 使用 `domain.module.item` 三级结构。
- 例如：
  - `common.button.save`
  - `run.status.success`
  - `report.section.summary`

## 3. 回退策略

- 优先读取用户项目配置语言。
- 若 key 在目标语言缺失，则回退到 `zh-CN`。
- 若 `zh-CN` 也缺失，返回 key 本身并记录告警日志。

## 4. 版本管理

- 每次资源变更必须更新变更记录。
- 每次发布记录资源版本，支持快速回滚到上一个稳定版本。

## 5. 质量门禁

- CI 中执行 i18n key 覆盖检查。
- 禁止硬编码用户可见文案。
- 报告模板、通知模板、错误信息必须走 i18n key。
