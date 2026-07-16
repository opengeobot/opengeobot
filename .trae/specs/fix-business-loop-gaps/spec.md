# 业务闭环差距修复与迭代开发 Spec

## Why

全面审计发现当前实现存在约 30 个差距，分布在 QwenPaw 规划流程、ROSClaw 端侧执行、实时监控和动态重规划四个关键领域。这些差距导致端到端业务闭环完全不可用：权限码缺失阻塞所有任务写操作、QwenPaw API 调用参数错误、ROSClaw 包未安装导致 bridge 永远降级、边缘执行引擎缺失导致任务无法迭代执行、WebSocket 监控失效、反馈回路断裂。需要分阶段迭代修复所有差距，实现完整的业务闭环。

## What Changes

### 阶段 1：关键基础修复（阻断性缺陷）
- **权限码修复**：新增 Flyway migration 添加 `mission.mission.create`、`mission.mission.pause`、`mission.mission.cancel`、`mission.mission.approve` 权限码并分配给 SYS_ADMIN 和 OPERATOR 角色
- **QwenPaw API 修复**：修正 `model` 字段（从硬编码 "qwenpaw" 改为配置化或使用 agent ID）、在 agent 创建时添加 `active_model`、在 system prompt 中提供技能 ID 和 input schema
- **LLM 输出解析修复**：strip markdown code fences 后再 `json.loads`
- **E2E 测试修复**：修正 `create_mission` 请求字段（name、steps 而非 objective）、修正 mission_id 字段提取
- **Compose 修复**：移除 rosclaw-bridge 从 sim profile（避免与 sim-adapter 冲突）、修正 QwenPaw 健康检查端点
- **超时配置**：Java 端 60s，Python QwenPaw 端 45s，留出余量

### 阶段 2：ROSClaw 端侧执行修复
- **ROSClaw 安装**：修改 ROSClaw Bridge Dockerfile 安装 rosclaw 包
- **rosbridge_server**：修改 ros2-turtlesim 服务安装 rosbridge_suite 并启动
- **Compose profile 修复**：新增 `rosclaw-sim` profile（sim 服务栈 + ROSClaw bridge + rosbridge，不包含 sim-adapter）
- **技能列表 NATS 响应**：在云端新增 `opengeobot.skill.list` NATS 响应器，返回已注册技能定义

### 阶段 3：边缘执行引擎
- **任务步骤迭代执行**：边缘网关收到 `start_mission` 后，按 plan 步骤顺序逐个执行技能，报告每步状态
- **任务状态回传**：边缘网关发布包含 mission_id 和 step 状态的 EdgeState，云端 EdgeStateListener 正确解析并更新 mission_step.status
- **EdgeStateListener 修复**：修正字段名（status 而非 state）和状态值映射（COMPLETED/FAILED 映射）

### 阶段 4：实时监控
- **WebSocket 事件推送**：实现 `MonitorEventPublisher`，在任务状态变化、机器人状态变化时通过 WebSocket 推送
- **Trace 记录**：实现 `TraceRecorder`，在任务、安全、执行事件发生时写入 trace_span 和 fact_event 表
- **安全状态监听**：云端新增对 `edge.{gateway_id}.safety.state_changed` 的订阅

### 阶段 5：动态重规划
- **AgentRuntimeProvider 补全**：实现 `continue_plan`、`cancel`、`health` 方法
- **NATS 反馈主题**：新增 `opengeobot.agent.mission.replan` 主题，云端在任务失败时通过 NATS 请求 agent-runtime 重规划
- **自动重规划**：`MissionService.failMission()` 后，自动调用 `MissionOrchestrator.replanMission()` 通过 NATS 请求 agent-runtime 基于失败上下文重新生成计划
- **执行反馈传递**：重规划请求中包含已完成步骤和失败原因，QwenPaw 基于上下文调整后续计划

## Impact

- Affected specs: F-MISSION-001, F-MISSION-003, F-POLICY-001, F-SAFETY-001, F-EDGE-002, F-ADAPTER-001, F-TRACE-001, F-MONITOR-001, F-MEMORY-001
- Affected code:
  - `apps/cloud-control/` - 权限 migration、MissionOrchestrator、EdgeStateListener、MonitorEventPublisher、TraceRecorder、SkillListNatsResponder
  - `services/agent-runtime/` - provider.py 修复、initializer.py 修复、handler.py 新增 replan 处理、provider.py 实现 continue_plan/cancel/health
  - `services/rosclaw-bridge/` - Dockerfile 安装 rosclaw
  - `deploy/compose/compose.yml` - profile 修复、rosbridge_server、超时配置
  - `edge/gateway/` - 任务步骤迭代执行引擎、状态回传
  - `tests/e2e/` - E2E 测试修复

## ADDED Requirements

### Requirement: QwenPaw 规划流程可用
系统 SHALL 确保 QwenPaw 规划调用使用正确的 model 字段、包含技能 schema 的 system prompt，并正确解析 LLM 输出（包括 markdown code fence stripping）。

#### Scenario: QwenPaw 返回可解析的计划
- **WHEN** agent-runtime 调用 QwenPaw `/v1/chat/completions`
- **THEN** 请求中 model 字段使用配置的模型名或 agent ID
- **AND** system prompt 包含可用技能 ID 和参数 schema
- **AND** 响应中的 markdown code fence 被正确 strip 后解析为 JSON
- **AND** 返回的 PlanProposal 包含有效步骤

### Requirement: ROSClaw 端侧执行可用
系统 SHALL 在 ROSClaw Bridge 容器中安装 ROSClaw 包，并通过 rosbridge_server 连接到 ROS 仿真环境，实现对端侧设备的真实控制。

#### Scenario: ROSClaw 执行技能
- **WHEN** 安全网关批准技能执行请求
- **THEN** ROSClaw Bridge 调用 ROSClaw SkillExecutor.execute()
- **AND** ROSClaw 通过 rosbridge WebSocket 控制 turtlesim
- **AND** 执行结果成功回传

### Requirement: 边缘任务执行引擎
系统 SHALL 在边缘网关实现任务步骤迭代执行，按 plan 步骤顺序逐个执行技能，并实时报告执行进度。

#### Scenario: 多步骤任务执行
- **WHEN** 边缘网关收到 start_mission 命令
- **THEN** 网关按 plan 步骤顺序逐个调用技能执行
- **AND** 每步执行后发布状态更新（step_index、step_status）
- **AND** 所有步骤完成后发布 mission COMPLETED 状态
- **AND** 某步失败时发布 mission FAILED 状态并包含失败原因

### Requirement: 实时监控推送
系统 SHALL 通过 WebSocket 在任务状态变化、机器人状态变化时实时推送事件到前端。

#### Scenario: 任务状态实时更新
- **WHEN** 任务状态从 EXECUTING 变为 COMPLETED
- **THEN** MonitorEventPublisher 通过 WebSocket 推送 mission update 事件
- **AND** 前端 MonitorView 实时更新任务进度和状态

### Requirement: 动态重规划
系统 SHALL 在任务执行失败时自动触发 QwenPaw 重规划，基于已完成步骤和失败原因生成调整后的计划。

#### Scenario: 执行失败自动重规划
- **WHEN** 任务执行失败（某步骤执行错误或超时）
- **THEN** MissionService.failMission() 标记任务 FAILED
- **AND** MissionOrchestrator 自动调用 agent-runtime 的 continue_plan
- **AND** 重规划请求包含已完成步骤和失败原因
- **AND** QwenPaw 基于上下文生成调整后的计划
- **AND** 新计划经 Policy 和 Safety 校验后继续执行

## MODIFIED Requirements

### Requirement: AgentRuntimeProvider 接口
原实现仅 `generate_plan` 方法。修改为实现设计规范要求的全部 4 个方法：`plan`（已实现）、`continue_plan`（新增）、`cancel`（新增）、`health`（新增）。

### Requirement: EdgeStateListener
原实现读取错误的字段名（`state` 而非 `status`）和错误的状态值。修改为正确解析边缘状态消息中的 `status` 字段和 mission 执行状态。

### Requirement: MissionController 权限码
原实现使用数据库中不存在的权限码。修改为添加缺失的权限码 migration 并确保 SYS_ADMIN 角色拥有所有任务操作权限。
