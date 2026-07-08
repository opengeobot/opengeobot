# 修复实现与设计文档差距 Spec

## Why

机器清单 (`platform-feature-manifest.yaml`) 声明全部 29 个功能为 `DONE`，但源代码审计发现多处违反项目规则的实际差距：安全红线违规、Stub/Mock 伪装完成、关键组件缺失、测试覆盖严重不足。这些差距使得 `DONE` 声明不成立，必须修复后才能合法标记完成。

## What Changes

### 安全红线修复（CRITICAL）
- 为 `platform-robot` 全部 18 个 Controller 添加 `@PreAuthorize` 权限码校验
- 为 `platform-governance` 全部 5 个 Controller 添加 `@PreAuthorize` 权限码校验

### Stub/Mock 替换为真实实现
- **OpsService**：替换 `checkStub()` 为真实的 NATS/MinIO 连通性探测
- **NotificationService**：替换 email stub 为真实邮件发送或明确标注为未实现
- **OtaService**：替换"始终成功"的模拟部署为真实边缘下发或明确标注为未实现
- **McpToolService**：替换 `executeSimulated()` 为真实工具调用或通过 MCP 网关转发
- **Outbox Relay**：实现 Outbox 事件发布器，将数据库事件中继到 NATS JetStream

### 缺失组件实现
- **edge/safety-gateway/**：实现边缘 Safety Gateway Runtime（急停锁存、动作级安全校验）
- **edge/local-skill-executor/**：实现本地技能执行器（接收技能调用、调用适配器、返回结果）
- **services/agent-runtime/**：实现 QwenPaw AgentRuntimeProvider 适配器
- **services/mcp-tool-gateway/**：实现 MCP 工具网关 Python 服务

### 前端修复
- **DefaultLayout**：将 `DefaultLayout.vue` 接入路由作为父布局，使导航栏、顶栏、退出、面包屑、语言切换器正常渲染
- **DashboardView**：替换硬编码占位值为真实 API 调用或重定向到 `OpsDashboardView`

### 测试覆盖补齐
- 为 `platform-robot` 模块添加单元测试（SafetyService、MissionService、RobotService 等关键服务）
- 为 `platform-common` 模块添加单元测试（OutboxRepository、AuditService、PublicIdGenerator 等）
- 为 `edge/gateway`、`services/sim-adapter`、`services/ros1-adapter` 添加 Python 单元测试
- 添加 Java 集成测试覆盖关键端到端流程
- 修改 `dev.sh`/`dev.ps1` 的 `test` 子命令加入 Python 测试执行

### 脚本和验证修复
- **sim-up**：实现 `cmd_sim_up()` 启动仿真栈
- **validate_platform_manifest.py**：增强验证逻辑，检查证据文件内容有效性而非仅文件存在

### 报告真实性
- 将模板化占位报告替换为真实测试运行输出
- HIL 报告如实标注未执行状态，不得在清单中声称 `HIL` 已完成

## Impact

- Affected specs: 全部功能（F-ENGINEERING-001 到 F-RECOVERY-001），重点影响 F-SAFETY-001、F-MCP-001、F-EDGE-002、F-OPS-001、F-OTA-001、F-ALARM-001
- Affected code:
  - `apps/cloud-control/platform-robot/src/main/java/.../controller/`（18 个 Controller）
  - `apps/cloud-control/platform-governance/src/main/java/.../controller/`（5 个 Controller）
  - `apps/cloud-control/platform-robot/src/main/java/.../service/`（OpsService、NotificationService、OtaService、McpToolService）
  - `apps/cloud-control/platform-common/src/main/java/.../event/`（新增 OutboxRelay）
  - `apps/web-console/src/router/index.ts`、`apps/web-console/src/App.vue`
  - `apps/web-console/src/views/DashboardView.vue`
  - `edge/safety-gateway/`、`edge/local-skill-executor/`
  - `services/agent-runtime/`、`services/mcp-tool-gateway/`
  - `scripts/dev.sh`、`scripts/dev.ps1`、`scripts/validate_platform_manifest.py`
  - `reports/` 下全部报告文件

## ADDED Requirements

### Requirement: API 权限码强制校验
所有平台 Controller 端点必须通过 `@PreAuthorize` 或等效机制校验稳定权限码。任何已认证用户不得在缺少权限码的情况下执行敏感操作（注册机器人、创建任务、触发急停、管理 OTA 等）。

#### Scenario: 无权限用户访问机器人管理
- **WHEN** 无 `robot.robot.register` 权限的用户调用 POST `/api/v1/robots`
- **THEN** 返回 403 Forbidden，审计记录该拒绝事件

#### Scenario: 有权限用户访问机器人管理
- **WHEN** 拥有 `robot.robot.register` 权限的用户调用 POST `/api/v1/robots`
- **THEN** 请求正常处理

### Requirement: Outbox 事件中继到 NATS
系统必须提供 Outbox Relay 组件，定期读取未发布的 `outbox_event` 记录并发布到 NATS JetStream。

#### Scenario: 事件发布
- **WHEN** 领域服务写入 outbox 事件
- **THEN** Relay 组件在可配置间隔内将事件发布到 NATS，标记为已发布

#### Scenario: NATS 不可用时
- **WHEN** NATS 连接失败
- **THEN** Relay 保留事件为未发布状态，重连后重试，不丢失事件

### Requirement: 边缘 Safety Gateway
系统必须在边缘侧提供独立的 Safety Gateway Runtime，实现急停锁存和动作级安全校验。

#### Scenario: 急停锁存
- **WHEN** 急停被触发
- **THEN** Safety Gateway 锁存急停状态，阻止后续技能执行，直到显式复位

#### Scenario: 动作级安全校验
- **WHEN** 技能执行请求到达边缘
- **THEN** Safety Gateway 校验禁区、速度限制等规则，拒绝不安全动作

### Requirement: 本地技能执行器
系统必须在边缘侧提供 Local Skill Executor，接收技能调用并转发到适配器。

#### Scenario: 技能执行
- **WHEN** 边缘收到技能执行指令
- **THEN** Local Skill Executor 调用对应适配器，返回执行结果

### Requirement: 真实健康检查
OpsService 必须执行真实的连通性探测（NATS ping、MinIO bucket 检查），不得返回未探测的 HEALTHY 状态。

#### Scenario: NATS 不可用
- **WHEN** NATS 服务不可达
- **THEN** OpsService 记录 NATS 状态为 UNHEALTHY，触发告警

### Requirement: 前端应用外壳
认证后的页面必须渲染 `DefaultLayout`（导航栏、顶栏、退出、面包屑、语言切换器、断网提示）。

#### Scenario: 登录后导航
- **WHEN** 用户登录成功后访问任意认证页面
- **THEN** 页面显示左侧导航、顶栏、用户菜单和断网提示组件

### Requirement: 仪表盘真实数据
DashboardView 必须调用真实 API 获取统计数据，不得使用硬编码占位值。

#### Scenario: 仪表盘加载
- **WHEN** 用户访问仪表盘页面
- **THEN** 页面显示真实的机器人数量、任务状态、告警统计和安全状态

### Requirement: 测试覆盖
系统必须为每个模块提供符合 `required_tests` 声明的测试。

#### Scenario: platform-robot 测试
- **WHEN** 运行 `./scripts/dev.sh test`
- **THEN** platform-robot 模块执行单元测试，覆盖 SafetyService、MissionService、RobotService 等关键服务

#### Scenario: Python 测试
- **WHEN** 运行 `./scripts/dev.sh test`
- **THEN** edge/gateway、sim-adapter、ros1-adapter 执行 pytest 测试

### Requirement: sim-up 实现
`scripts/dev.sh sim-up` 必须启动仿真栈。

#### Scenario: 仿真栈启动
- **WHEN** 执行 `./scripts/dev.sh sim-up`
- **THEN** 仿真适配器和相关服务启动，可通过健康检查验证

### Requirement: 验证脚本增强
`validate_platform_manifest.py` 必须检查证据文件内容有效性，而非仅检查文件存在。

#### Scenario: 空报告检测
- **WHEN** 报告文件存在但内容为纯模板无实际数据
- **THEN** 验证脚本报错，指出该证据无效

## MODIFIED Requirements

### Requirement: 功能完成标准
功能标记为 `DONE` 必须满足：无 Stub/Mock/TODO、权限校验生效、事件实际投递、测试真实运行通过、报告反映真实执行结果。现有已标记 `DONE` 但不满足此标准的功能，应回退为 `IN_PROGRESS`。
