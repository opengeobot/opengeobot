# 平台差距修复 Spec

## Why

经全面审计，OpenGeoBot 平台 29 个功能虽在机器清单中标记为 `DONE`，但实际存在构建阻断、架构违规（NATS JetStream 未集成）、安全架构分裂（两套安全锁存未统一）、前端功能缺口（MCP 管理页缺失、构建失败）、测试覆盖不足（集成测试 mock 服务层、前端仅 1 个冒烟测试）等问题。这些问题违反了项目规则中的安全红线和架构约束，需在标记真正完成前修复。

## What Changes

### 前端修复
- 修复 `ProfileView.vue` 类型错误（`string | null` 赋值给 `string`），恢复构建
- 新增 MCP 工具管理视图（`McpManagementView.vue`），接入已有 `mcp.ts` API 和路由
- 为 `MissionManagementView` 添加 WebSocket 实时任务执行进度推送
- 为 `SafetyControlView` 添加 WebSocket 实时安全事件推送
- 完善 `platform.ts` Store 的 `isOnline` 网络状态联动

### Python 服务修复
- 为全部 6 个 Python 服务（agent-runtime、mcp-tool-gateway、ros1-adapter、edge/gateway、edge/safety-gateway、edge/local-skill-executor）的 mission-critical NATS subject 添加 JetStream 持久化流和 durable consumer
- 统一 edge/gateway 与 edge/safety-gateway 的安全锁存实现，使 CommandHandler 委托 SafetyStateMachine
- 为 QwenPawProvider 添加 LLM 输出 schema 校验（skill_id 是否已注册、params 是否匹配 skill input_schema）
- 为 MCP Tool Gateway 添加 input/output schema 校验和审计日志持久化

### Java 后端修复
- 新增 adapter 领域的 Java 侧管理实现（适配器注册、兼容性管理、健康状态路由）
- 为 6 个 `@SpringBootTest` 集成测试补充真实 Service+Repository+DB 端到端路径（移除或补充非 mock 的集成测试）
- 引入 Testcontainers + 真实 PostgreSQL 替代 H2 用于集成测试

### 测试与质量
- 为前端补充视图、API 客户端、Store、路由守卫的单元测试（至少覆盖关键视图和全部 API 客户端）
- 修复部署报告中镜像版本与 compose.yml 不一致的文档同步问题

### 可观测性增强
- 补充 Grafana 告警规则配置
- 为 vmagent 添加 edge/sim 服务指标抓取配置
- 为 Vector 添加日志过滤与脱敏管线配置

## Impact

- Affected specs: F-PLATFORM-001, F-MCP-001, F-MISSION-003, F-SAFETY-001, F-EDGE-002, F-ADAPTER-001, F-ADAPTER-002, F-MONITOR-001, F-OPS-001, F-ENGINEERING-001
- Affected code:
  - `apps/web-console/src/views/ProfileView.vue` — 类型修复
  - `apps/web-console/src/views/McpManagementView.vue` — 新增视图
  - `apps/web-console/src/views/MissionManagementView.vue` — WebSocket 增强
  - `apps/web-console/src/views/SafetyControlView.vue` — WebSocket 增强
  - `apps/web-console/src/stores/platform.ts` — Store 完善
  - `apps/web-console/src/router/index.ts` — 新增 MCP 路由
  - `services/agent-runtime/src/opengeobot_agent/` — JetStream + schema 校验
  - `services/mcp-tool-gateway/src/opengeobot_mcp_gateway/` — JetStream + schema 校验 + 审计持久化
  - `edge/gateway/src/opengeobot_edge/` — JetStream + safety-gateway 集成
  - `edge/safety-gateway/src/opengeobot_safety/` — JetStream
  - `edge/local-skill-executor/src/opengeobot_skill_executor/` — JetStream
  - `services/ros1-adapter/src/` — JetStream
  - `apps/cloud-control/platform-robot/` — adapter 领域新增
  - `apps/cloud-control/bootstrap/src/test/` — 真实集成测试
  - `deploy/observability/` — 告警规则与抓取配置

## ADDED Requirements

### Requirement: NATS JetStream 持久化
系统 SHALL 为 mission-critical 的 NATS subject（agent plan_request、mcp tool invoke、edge command、execution events）使用 JetStream stream 和 durable consumer，确保消息持久化和 at-least-once 投递。

#### Scenario: 消息持久化
- **WHEN** NATS 服务重启或消费端暂时不可用
- **THEN** 未消费的消息在 JetStream stream 中持久保留，消费端恢复后继续投递

#### Scenario: Edge 离线缓存与 JetStream 协作
- **WHEN** 边缘网关断网后重连
- **THEN** JetStream durable consumer 从断点继续投递，与本地离线缓存协作完成对账

### Requirement: 统一安全锁存架构
系统 SHALL 使用唯一的 SafetyStateMachine（SM-SAFETY-001）作为边缘安全锁存的实现，edge/gateway 的 CommandHandler MUST 委托 safety-gateway 的 SafetyStateMachine 进行安全判定，不得维护独立的安全状态。

#### Scenario: 急停通过统一状态机
- **WHEN** 云端下发 emergency_stop 命令到 edge/gateway
- **THEN** CommandHandler 调用 SafetyStateMachine.trigger_emergency_stop()，状态机进入 EMERGENCY_STOPPED 并锁存

#### Scenario: 技能执行通过统一安全检查
- **WHEN** 云端下发 execute_skill 命令
- **THEN** CommandHandler 先查询 SafetyStateMachine 当前状态，非 NORMAL 时拒绝执行

### Requirement: LLM 输出 Schema 校验
AgentRuntimeProvider SHALL 对 QwenPaw 返回的 PlanProposal 中的每个 step 进行 schema 校验，包括 skill_id 是否为已注册技能、params 是否匹配该技能的 input_schema。校验失败的 step SHALL 被标记为 invalid 并附带错误原因。

#### Scenario: 未注册技能被拒绝
- **WHEN** LLM 返回的 plan proposal 包含未注册的 skill_id
- **THEN** 该 step 被标记为 invalid，PlanProposal 中包含错误信息 "skill_id '{id}' is not registered"

#### Scenario: 参数不匹配被拒绝
- **WHEN** LLM 返回的 step 的 params 不匹配该技能的 input_schema
- **THEN** 该 step 被标记为 invalid，PlanProposal 中包含 schema 校验错误详情

### Requirement: MCP Tool Schema 校验
MCP Tool Gateway SHALL 在调用工具前校验 input 是否匹配 ToolDefinition.input_schema，并在调用后校验 output 是否匹配 ToolDefinition.output_schema。校验失败 SHALL 返回结构化错误而非执行工具。

#### Scenario: 输入校验失败
- **WHEN** 工具调用请求的 input 不匹配 input_schema
- **THEN** 返回 400 错误，message_key 为 "mcp.tool.input_schema_invalid"

#### Scenario: 审计日志持久化
- **WHEN** 工具调用完成（成功或失败）
- **THEN** 调用审计日志通过 NATS 发布到审计 subject，并可查询历史调用记录

### Requirement: MCP 管理前端视图
前端 SHALL 提供 MCP 工具管理视图，支持工具列表查看、版本详情、调用测试和调用历史查询。

#### Scenario: 查看 MCP 工具列表
- **WHEN** 用户导航到 MCP 管理页面
- **THEN** 显示已注册的 MCP 工具列表，包含工具名、版本、状态、风险等级

#### Scenario: 测试调用 MCP 工具
- **WHEN** 用户在 MCP 管理页面选择工具并提交输入参数
- **THEN** 系统调用工具并显示返回结果或错误信息

### Requirement: 任务执行实时推送
前端 MissionManagementView SHALL 通过 WebSocket 接收任务执行进度事件（mission.started、mission.step_completed、mission.progress、mission.completed、mission.failed），实时更新任务状态。

#### Scenario: 任务进度实时更新
- **WHEN** 任务在边缘执行中
- **THEN** 前端通过 WebSocket 接收进度事件并更新任务步骤状态，无需手动刷新

### Requirement: 安全事件实时推送
前端 SafetyControlView SHALL 通过 WebSocket 接收安全事件（safety.emergency_stop_triggered、safety.estop_reset_requested、safety.estop_reset_completed），实时更新安全状态。

#### Scenario: 急停状态实时更新
- **WHEN** 机器人触发急停
- **THEN** 前端通过 WebSocket 接收事件并立即显示急停状态，无需手动刷新

### Requirement: Adapter 领域 Java 管理实现
云端 Java 后端 SHALL 提供 adapter 领域的管理能力，包括适配器注册、兼容性配置、健康状态查询和适配器路由，对应 F-ADAPTER-001 和 F-ADAPTER-002 的后端模块。

#### Scenario: 查询适配器兼容性
- **WHEN** 管理员查询某机器人型号的适配器兼容性
- **THEN** 返回该型号支持的适配器类型、ROS 版本和控制协议

#### Scenario: 适配器健康状态
- **WHEN** 适配器健康状态变化
- **THEN** 发布 adapter.health_changed.v1 事件并更新适配器状态投影

### Requirement: 真实集成测试
系统 SHALL 提供使用真实 Service+Repository+PostgreSQL（Testcontainers）的集成测试，覆盖关键业务流程（认证授权、任务生命周期、安全流程、OTA 部署、机器人管理），而非全部 mock 服务层。

#### Scenario: 任务生命周期端到端
- **WHEN** 集成测试创建任务、规划、审批、执行、完成
- **THEN** 真实数据库状态正确更新，真实 Outbox 事件发布，状态机转换符合 SM-MISSION-001

#### Scenario: 真实 PostgreSQL JSONB
- **WHEN** 集成测试写入包含 JSONB 字段的实体
- **THEN** 使用真实 PostgreSQL 验证 JSONB 序列化/反序列化行为与生产一致

### Requirement: 可观测性告警规则
系统 SHALL 提供 Grafana 告警规则配置，覆盖关键指标阈值（服务健康、错误率、急停次数、任务失败率、NATS 连接断开）。

#### Scenario: 服务不健康告警
- **WHEN** 服务健康检查连续 3 次失败
- **THEN** Grafana 触发告警并通过通知渠道发送

## MODIFIED Requirements

### Requirement: 前端构建
前端项目 SHALL 通过 `pnpm build`（含 `vue-tsc` 类型检查）和 `pnpm test`，无 TypeScript 类型错误。

### Requirement: platform.ts Store
`platform.ts` Store SHALL 维护 `isOnline` 状态并联动浏览器 `online`/`offline` 事件，`currentOrg` SHALL 支持组织切换并持久化到 localStorage。

### Requirement: 前端测试覆盖
前端 SHALL 为全部 API 客户端模块提供单元测试，为关键视图（LoginView、MissionManagementView、SafetyControlView、MonitorView）提供组件测试，覆盖核心交互逻辑。

## REMOVED Requirements

### Requirement: H2 内存数据库集成测试
**Reason**: H2 无法验证 PostgreSQL 特有行为（JSONB、数组类型、Flyway 迁移路径），与生产行为不一致
**Migration**: 使用 Testcontainers + 真实 PostgreSQL 替代，H2 仅保留用于纯单元测试的快速反馈路径
