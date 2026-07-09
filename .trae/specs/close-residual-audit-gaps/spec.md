# 审计残留差距修复 Spec

## Why

对 `close-implementation-gaps` 规格标记完成后进行独立审计，发现仍有 3 类残留差距违反项目规则：安全红线（3 个 Controller 共 15 个端点缺少 `@PreAuthorize`）、功能完整性（P-TRACE-001 前端页面缺失）和 i18n 合规性（多处枚举标签硬编码）。这些差距使对应功能的 `DONE` 声明不完全成立，必须修复后才能合法标记完成。

## 审计方法

对仓库 `/storage/xy/source/opengeobot` 执行四路并行只读审计：
1. Java 后端（Controller 权限码、Stub/Mock 清理、Outbox Relay、安全红线）
2. Python 服务与边缘组件（safety-gateway、local-skill-executor、agent-runtime、mcp-tool-gateway）
3. 前端（DefaultLayout、DashboardView、错误处理、i18n、路由完整性）
4. 测试/脚本/报告/契约（测试文件存在性、dev.sh 子命令、validate 脚本、报告真实性、契约完整性）

## 审计结果概览

| 审计域 | PASS | FAIL | 需验证 |
|--------|------|------|--------|
| Java 后端 | Stub/Mock 清理、Outbox Relay、Controller 不直连 Mapper、安全红线 | 3 个 Controller 缺 @PreAuthorize | AuditController 注入具体类 |
| Python 服务/边缘 | safety-gateway、local-skill-executor、agent-runtime、mcp-tool-gateway 全部实现完整 | 无 | 测试未实际运行 |
| 前端 | DefaultLayout、DashboardView、错误处理、API 层 | P-TRACE-001 路由/视图缺失 | 路由多对一映射合理性 |
| 测试/脚本/报告 | 测试文件存在性、dev.sh 子命令、validate 脚本、契约完整性、安全/HIL 报告 | 无硬性 FAIL | HTML 报告为人工摘要、根 tests/ 为空 |

## What Changes

### 安全红线修复（CRITICAL）
- 为 `MissionTemplateController` 全部 2 个端点添加 `@PreAuthorize`（`mission.template.read` / `mission.template.manage`）
- 为 `RobotGroupController` 全部 8 个端点添加 `@PreAuthorize`（`robot.group.read` / `robot.group.manage`）
- 为 `RobotModelController` 全部 5 个端点添加 `@PreAuthorize`（`robot.model.read` / `robot.model.manage`）

### 前端功能补齐
- 新建 `apps/web-console/src/views/TraceView.vue`，实现 P-TRACE-001 全链路 Trace 查询与事实回放页面
- 在 `apps/web-console/src/router/index.ts` 注册 `/trace` 路由，接入 DefaultLayout
- 页面须支持 `required_view_states`：LOADING、READY、EMPTY、PARTIAL、STALE、FORBIDDEN、ERROR
- 页面须调用已有 `src/api/trace.ts` 的 `listTraces`、`getTrace`、`getReplay` 接口
- 添加对应 i18n key 到 `zh-CN/platform.json` 和 `en-US/platform.json`

### i18n 合规修复
- `OtaManagementView.vue`：将硬编码列标题和类型下拉选项 label 改为 `t()` 调用
- `AlarmManagementView.vue`：将通知渠道类型选项 label 改为 `t()` 调用
- `BackupRecoveryView.vue`：将备份类型和演练类型选项 label 改为 `t()` 调用
- 在 locale 文件中添加对应 i18n key

### 测试验证
- 实际运行 `./scripts/dev.sh test` 确认 Java 和 Python 测试全部通过
- 为 3 个修复的 Controller 补充权限拒绝单元测试（如尚未覆盖）

## Impact

- Affected specs: F-ROBOT-001（RobotGroup/RobotModel 权限）、F-MISSION-002/003（MissionTemplate 权限）、F-TRACE-001（前端页面）、F-OTA-001/F-ALARM-001/F-RECOVERY-001（i18n）
- Affected code:
  - `apps/cloud-control/platform-robot/src/main/java/.../controller/MissionTemplateController.java`
  - `apps/cloud-control/platform-robot/src/main/java/.../controller/RobotGroupController.java`
  - `apps/cloud-control/platform-robot/src/main/java/.../controller/RobotModelController.java`
  - `apps/web-console/src/views/TraceView.vue`（新建）
  - `apps/web-console/src/router/index.ts`
  - `apps/web-console/src/views/OtaManagementView.vue`
  - `apps/web-console/src/views/AlarmManagementView.vue`
  - `apps/web-console/src/views/BackupRecoveryView.vue`
  - `apps/web-console/src/i18n/locales/zh-CN/platform.json`
  - `apps/web-console/src/i18n/locales/en-US/platform.json`

## ADDED Requirements

### Requirement: 遗漏 Controller 权限码强制校验
`MissionTemplateController`、`RobotGroupController`、`RobotModelController` 的所有端点必须通过 `@PreAuthorize` 校验稳定权限码。当前这 3 个 Controller 的 Javadoc 中已声明权限码但未实际强制执行。

#### Scenario: 无权限用户访问机器人分组管理
- **WHEN** 无 `robot.group.manage` 权限的用户调用 POST `/api/v1/robot-groups`
- **THEN** 返回 403 Forbidden，审计记录该拒绝事件

#### Scenario: 有权限用户访问机器人分组管理
- **WHEN** 拥有 `robot.group.manage` 权限的用户调用 POST `/api/v1/robot-groups`
- **THEN** 请求正常处理

#### Scenario: 无权限用户访问机器人型号管理
- **WHEN** 无 `robot.model.manage` 权限的用户调用 POST `/api/v1/robot-models`
- **THEN** 返回 403 Forbidden

#### Scenario: 无权限用户访问任务模板管理
- **WHEN** 无 `mission.template.manage` 权限的用户调用 POST `/api/v1/mission-templates`
- **THEN** 返回 403 Forbidden

### Requirement: P-TRACE-001 前端页面
系统必须提供全链路 Trace 查询与事实回放页面，包含 Trace 列表、Trace 详情、回放视图。

#### Scenario: Trace 列表加载
- **WHEN** 用户访问 `/trace` 页面
- **THEN** 页面显示 Trace 列表，支持按时间范围、机器人、任务筛选

#### Scenario: Trace 详情与回放
- **WHEN** 用户点击某条 Trace
- **THEN** 页面显示 Trace 详情和事件序列，可触发回放

#### Scenario: 无数据
- **WHEN** 查询结果为空
- **THEN** 页面显示空状态（EMPTY）

#### Scenario: 权限不足
- **WHEN** 无 `trace.trace.read` 权限的用户访问 `/trace`
- **THEN** 页面显示禁止访问状态（FORBIDDEN）

### Requirement: 枚举标签 i18n 合规
前端所有用户可见的枚举选项标签（OTA 类型、通知渠道、备份类型、演练类型等）必须使用 i18n key 渲染，不得硬编码英文。

#### Scenario: 语言切换
- **WHEN** 用户将语言从中文切换为英文
- **THEN** 所有枚举选项标签随之切换为对应语言

## MODIFIED Requirements

### Requirement: 功能完成标准验证
`close-implementation-gaps` 规格的 checklist 未执行验证。本次修复后，须逐项验证 checklist 并标记通过状态。对验证中发现的残留问题，在本规格中一并修复。
