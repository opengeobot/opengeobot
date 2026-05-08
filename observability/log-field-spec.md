# OpenGEO Bot 统一日志字段规范（V1）

<!--
作者：AxeXie
创建时间：2026-05-08 13:02:00
-->

## 1. 目标

统一应用日志、任务日志、审计日志和调用日志字段，支持跨模块追踪、问题排查、审计合规与成本分析。

## 2. 强制字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| traceId | string | 是 | 链路追踪标识 |
| spanId | string | 否 | 当前子调用标识 |
| projectId | string | 是 | 项目标识 |
| runId | string | 运行链路必填 | 评测任务标识 |
| operator | string | 是 | 操作者（用户或系统） |
| module | string | 是 | 模块名，如 `engine-adapter` |
| event | string | 是 | 事件名，如 `run.started` |
| level | string | 是 | `DEBUG/INFO/WARN/ERROR` |
| timestamp | string | 是 | ISO8601 时间 |
| message | string | 是 | 事件文本描述 |

## 3. 推荐字段

- `promptId`
- `engine`
- `region`
- `language`
- `durationMs`
- `errorCode`
- `retryCount`
- `cost`

## 4. 脱敏规则

- 禁止记录原始密钥与 Token。
- 敏感字段（邮箱、手机号、身份证号、访问令牌）必须脱敏或哈希化。
- 对外导出日志前必须执行二次脱敏。

## 5. 日志等级使用约束

- `DEBUG`：仅开发与测试环境默认开启。
- `INFO`：业务关键状态变化。
- `WARN`：可恢复异常与重试事件。
- `ERROR`：影响结果正确性或可用性的失败事件。

## 6. 留存策略

- 审计日志：保留至少 180 天。
- 运行日志：保留至少 90 天。
- 错误日志：保留至少 180 天。
- 导出日志需要保留审计痕迹。
