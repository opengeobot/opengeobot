# 端到端业务闭环验证与补全 Spec

## Why

AI 开发约束规范（第 61-77 行）定义了完整业务闭环：用户指令 -> 权限校验 -> QwenPaw 意图理解 -> Mission DAG -> Policy 审批 -> Fleet 调度 -> 云边下发 -> Edge Safety -> Skill Executor -> 机器人执行 -> 遥测回传 -> Trace 归档 -> Memory 沉淀。

审计发现：清单 29 个功能虽全部标记 `DONE`，但**各服务之间没有真正串联**。云端 `MissionService.start()` 只改数据库状态并写出 outbox 事件，不调用 QwenPaw、不检查 Policy、不获取控制租约、不下发边缘命令；边缘网关绕过独立 Safety Gateway 直连 sim-adapter；NATS 主题命名空间不一致导致安全网关和技能执行器链路完全孤立。需要补全这些断裂点并编写真实 E2E 测试验证闭环。

## What Changes

### 云端编排（Java）
- 新增 `MissionOrchestrator` 服务，在 `MissionService` 之上编排完整闭环：创建任务 -> 调用 QwenPaw 生成计划 -> Policy 校验 -> 审批门禁 -> 获取控制租约 -> 下发边缘命令 -> 监听执行结果
- 新增 `AgentRuntimeNatsClient`（Java），通过 NATS request-reply 调用 agent-runtime 服务的 `opengeobot.agent.mission.plan_request` 主题
- 新增 `EdgeCommandDispatcher`（Java），通过 NATS JetStream 向边缘网关下发 `opengeobot.dev.edge.command.{robot_id}` 命令
- 新增 `EdgeStateListener`（Java），订阅 `opengeobot.dev.edge.state.>` 接收边缘状态回传，更新任务执行状态
- 修改 `MissionService.start()` 调用 `MissionOrchestrator` 执行编排流程
- 修改 `MissionController`，新增 `POST /api/v1/missions/{id}/plan-with-agent` 端点触发 QwenPaw 规划

### 边缘链路修复（Python）
- 统一 NATS 主题：边缘网关将技能执行请求路由到安全网关拦截主题 `edge.{gateway_id}.skill.execute`，而非直连 ROSClaw bridge
- 修改边缘网关 `CommandHandler`：不再使用进程内 `SafetyStateMachine` 替代独立安全网关，改为通过 NATS 发布到安全网关拦截主题
- 安全网关验证后转发到 `edge.{gateway_id}.skill.execute.approved`，技能执行器消费后转发到 `opengeobot.dev.edge.skill.execute.{robot_id}`（ROSClaw NATS Bridge）
- 确保安全网关的急停状态广播被边缘网关监听并锁存

### ROSClaw NATS Bridge（端侧硬件控制）
- 新增 `services/rosclaw-bridge/` Python 服务，作为 OpenGeoBot 边缘管道与 ROSClaw Edge Runtime 之间的 NATS 桥接器
- 桥接器订阅 `opengeobot.dev.edge.skill.execute.{robot_id}`，接收技能执行器转发的 `SkillExecutionRequest`
- 桥接器将请求翻译为 ROSClaw `SkillExecutor.execute(skill_name, parameters)` 调用，ROSClaw 通过 rosbridge WebSocket 控制真实机器人硬件
- ROSClaw 自身的安全管线（FirewallValidator: e-URDF 软限位 + MuJoCo 碰撞 + 语义安全）作为端侧第二道防线，OpenGeoBot Safety Gateway 仍然是权威的动作级安全门禁
- 桥接器将 ROSClaw 执行结果翻译回 `SkillExecutionResponse` 并通过 NATS reply 回传
- 桥接器同时处理 `emergency_stop` 技能请求，调用 ROSClaw 的 `emergency_stop` MCP 工具
- EXT-ROSCLAW 契约中 "Platform NATS adapter wiring: not claimed DONE" 由本桥接器补全

### QwenPaw 真实镜像服务
- 在 compose `sim` profile 中添加本地已有的 QwenPaw 镜像 `agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/qwenpaw:latest`，暴露 OpenAI 兼容的 `/v1/chat/completions` 端点
- 设置 agent-runtime 的 `QWENPAW_ENDPOINT` 环境变量指向 compose 网络内的 qwenpaw 服务
- 注意：项目规范禁止 `latest` 标签用于生产；此处仅用于本地 E2E 验证，正式部署前 MUST 固化到具体版本标签

### E2E 测试
- 新增 `tests/e2e/test_business_loop.py`，验证完整闭环：
  1. 登录获取 token
  2. 创建任务（自然语言目标）
  3. 触发 QwenPaw 规划，验证计划步骤生成
  4. Policy 校验通过
  5. 启动任务，验证控制租约获取
  6. 验证命令下发到边缘
  7. 验证安全网关拦截和动作级校验
  8. 验证 ROSClaw bridge 调用 ROSClaw 执行技能
  9. 验证执行结果回传
  10. 验证 trace_id 全链路关联

## Impact

- Affected specs: F-MISSION-001, F-MISSION-003, F-POLICY-001, F-SAFETY-001, F-EDGE-002, F-FLEET-001, F-MCP-001, F-TRACE-001, F-ADAPTER-001
- Affected code:
  - `apps/cloud-control/platform-robot/` - 新增编排器、NATS 客户端、边缘分发器、状态监听器
  - `apps/cloud-control/platform-common/` - 新增 NATS request-reply 工具
  - `edge/gateway/src/opengeobot_edge/` - 修改 command_handler 路由到安全网关
  - `edge/safety-gateway/src/opengeobot_safety_gateway/` - 确认转发逻辑正确
  - `edge/local-skill-executor/src/opengeobot_skill_executor/` - 确认消费 approved 主题
  - `services/rosclaw-bridge/` - **新增** ROSClaw NATS 桥接服务，替代 sim-adapter
  - `services/agent-runtime/` - 已存在，无需修改
  - `deploy/compose/compose.yml` - 新增 qwenpaw 真实镜像、rosclaw-bridge 服务
  - `tests/e2e/` - 新增 E2E 测试脚本
- External dependency:
  - ROSClaw 源码位于 `/storage/xy/source/rosclaw`，通过 `pip install -e` 方式安装为 Python 包

## ADDED Requirements

### Requirement: 云端任务编排器
系统 SHALL 提供 `MissionOrchestrator` 服务，在任务启动时自动执行：QwenPaw 计划生成 -> Policy 校验 -> 控制租约获取 -> 边缘命令下发，并将执行结果通过 `trace_id` 关联回任务。

#### Scenario: 正常任务执行闭环
- **WHEN** 用户创建任务并指定自然语言目标和机器人 ID
- **AND** 用户触发 `POST /api/v1/missions/{id}/plan-with-agent`
- **THEN** 系统通过 NATS 调用 agent-runtime 生成不可信计划提案
- **AND** 系统将计划步骤写入 `mission_step` 表
- **AND** 系统调用 PolicyService 校验计划步骤
- **WHEN** 用户调用 `POST /api/v1/missions/{id}/start`
- **THEN** 系统获取控制租约（ControlLease）
- **AND** 系统通过 NATS 向边缘网关下发 `start_mission` 命令
- **AND** 边缘网关将技能执行路由到安全网关拦截
- **AND** 安全网关执行动作级校验后转发到技能执行器
- **AND** 技能执行器调用 ROSClaw bridge 执行技能
- **AND** ROSClaw bridge 通过 ROSClaw SkillExecutor 控制机器人硬件
- **AND** 执行结果通过 NATS 回传到云端
- **AND** 任务状态更新为 `COMPLETED`
- **AND** 全链路 `trace_id` 可在 Trace 系统中回放

#### Scenario: 安全网关阻断
- **WHEN** 计划步骤包含超速参数（线性速度 > 1.5 m/s）
- **AND** 安全网关执行动作级校验
- **THEN** 安全网关返回 `BLOCK` 决策
- **AND** 技能不被执行
- **AND** 任务状态更新为 `FAILED`
- **AND** 阻断事件通过 trace_id 可追溯

### Requirement: 边缘安全链路统一
系统 SHALL 确保边缘网关的所有技能执行请求经过独立安全网关的动作级校验，而非仅使用进程内急停锁存。

#### Scenario: 边缘网关路由到安全网关
- **WHEN** 边缘网关收到 `execute_skill` 命令
- **THEN** 网关将请求发布到安全网关拦截主题 `edge.{gateway_id}.skill.execute`
- **AND** 不直接发布到 ROSClaw bridge 主题
- **AND** 安全网关执行受限区域、速度限制、碰撞风险校验
- **AND** 校验通过后转发到 `edge.{gateway_id}.skill.execute.approved`
- **AND** 技能执行器消费 approved 主题并调用适配器

### Requirement: QwenPaw 真实镜像服务
系统 SHALL 在 compose `sim` profile 中集成真实 QwenPaw 镜像，使 agent-runtime 能够调用真实 LLM 生成任务计划。

#### Scenario: QwenPaw 返回计划
- **WHEN** agent-runtime 调用 QwenPaw 的 `POST /v1/chat/completions` 端点
- **THEN** QwenPaw 返回包含 `choices[0].message.content` 的 JSON
- **AND** content 是合法的 plan JSON，包含 `steps` 数组和 `confidence` 字段
- **AND** 每个 step 的 `skill_id` 是已注册技能（stand_up, move_forward 等）
- **AND** agent-runtime 的 `QWENPAW_ENDPOINT` 环境变量指向 compose 网络内的 qwenpaw 服务

### Requirement: ROSClaw NATS Bridge
系统 SHALL 提供 ROSClaw NATS Bridge 服务，将 OpenGeoBot 边缘管道的技能执行请求桥接到 ROSClaw Edge Runtime，实现对真实机器人硬件的控制。

#### Scenario: 正常技能执行
- **WHEN** 技能执行器将 `SkillExecutionRequest` 转发到 `opengeobot.dev.edge.skill.execute.{robot_id}`
- **THEN** ROSClaw bridge 接收请求并解析 skill_id 和 params
- **AND** bridge 调用 ROSClaw `SkillExecutor.execute(skill_name, parameters)`
- **AND** ROSClaw 通过 rosbridge WebSocket 向机器人发布控制指令
- **AND** ROSClaw 的 FirewallValidator 执行 e-URDF 软限位、碰撞检测和语义安全校验
- **AND** 执行结果翻译为 `SkillExecutionResponse` 通过 NATS reply 回传

#### Scenario: 急停
- **WHEN** bridge 收到 `emergency_stop` 技能请求
- **THEN** bridge 调用 ROSClaw 的 `emergency_stop` MCP 工具
- **AND** ROSClaw 立即停止所有运动
- **AND** 停止状态回传到 OpenGeoBot Safety Gateway 并锁存

#### Scenario: ROSClaw 安全管线阻断
- **WHEN** ROSClaw FirewallValidator 检测到轨迹违反 e-URDF 软限位
- **THEN** ROSClaw 返回 BLOCK 决策
- **AND** bridge 将阻断结果翻译为 `SkillExecutionResponse`（status=BLOCKED）
- **AND** 阻断事件通过 NATS 回传到云端

### Requirement: E2E 业务闭环测试
系统 SHALL 提供自动化 E2E 测试，验证从用户指令到机器人执行的完整业务闭环。

#### Scenario: 全链路自动化测试
- **WHEN** 运行 `tests/e2e/test_business_loop.py`
- **THEN** 测试启动 infra + cloud + sim 服务栈
- **AND** 测试创建任务、触发规划、启动执行
- **AND** 测试验证任务状态最终为 `COMPLETED`
- **AND** 测试验证 trace_id 在云端和边缘日志中均可检索
- **AND** 测试验证安全网关拦截记录存在

## MODIFIED Requirements

### Requirement: Mission 执行控制（F-MISSION-003）
`MissionService.start()` 不仅改变云端状态，还 MUST 调用 `MissionOrchestrator` 执行完整的云边协同流程：获取控制租约、下发边缘命令、监听执行结果。原有的"Physical execution is dispatched to the edge Safety Gateway elsewhere"注释所描述的"elsewhere"现在 MUST 由编排器具体实现。

### Requirement: 边缘网关命令处理（F-EDGE-002）
边缘网关 `CommandHandler` 的 `execute_skill` 分支 MUST 将请求路由到独立安全网关的拦截主题，而非直接调用 ROSClaw bridge。进程内 `SafetyStateMachine` 仅作为急停锁存的快速路径保留，动作级安全校验 MUST 由独立安全网关执行。

### Requirement: ROS2 仿真与主路径适配（F-ADAPTER-001）
ROSClaw NATS Bridge 替代 sim-adapter 作为端侧终端执行器。Bridge 通过 ROSClaw Edge Runtime 的 `SkillExecutor` 控制机器人硬件，使用 rosbridge WebSocket 作为 ROS 通信主路径。EXT-ROSCLAW 契约中 "Platform NATS adapter wiring: not claimed DONE" 由本 bridge 补全为 DONE。
