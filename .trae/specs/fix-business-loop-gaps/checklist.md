# Checklist

## 阶段 1：关键基础修复

### 权限码修复
- [ ] Flyway migration 添加 `mission.mission.create`、`.pause`、`.cancel`、`.approve` 权限码
- [ ] 新权限码分配给 SYS_ADMIN 和 OPERATOR 角色
- [ ] admin 用户可以成功创建任务（POST /api/v1/missions 返回 201）

### QwenPaw API 修复
- [ ] `provider.py` 的 `model` 字段从配置读取（不再硬编码 "qwenpaw"）
- [ ] `initializer.py` 创建 agent 时包含 `active_model` 字段
- [ ] system prompt 包含可用技能 ID 和参数 schema 说明
- [ ] `_parse_response` strip markdown code fences 后再解析 JSON
- [ ] `config.py` 新增 `qwenpaw_model`、`qwenpaw_model_provider`、`qwenpaw_model_name` 配置
- [ ] compose agent-runtime 环境变量包含 QWENPAW_MODEL
- [ ] 测试验证 QwenPaw 返回可解析的计划 JSON

### E2E 测试修复
- [ ] `create_mission` 请求体包含 `name`、`steps` 字段
- [ ] mission_id 从 `mission_id` 字段提取（而非 `id`）
- [ ] API 路径正确
- [ ] 轮询等待和超时处理正常

### Compose 配置修复
- [ ] `rosclaw-bridge` 不在 `sim` profile 中（避免与 sim-adapter 冲突）
- [ ] 新增 `rosclaw-sim` profile（包含 rosclaw-bridge + ros2-turtlesim with rosbridge）
- [ ] QwenPaw 健康检查端点正确
- [ ] Java 超时 60s，Python QWENPAW_TIMEOUT 45s

### 技能列表 NATS 响应器
- [ ] 云端 `SkillListNatsResponder` 订阅 `opengeobot.skill.list` 并返回技能列表
- [ ] agent-runtime 的 `NatsSkillRegistry` 成功查询到技能定义
- [ ] 计划步骤验证不再全部标记 invalid

## 阶段 2：ROSClaw 端侧执行修复

### ROSClaw 安装
- [ ] ROSClaw Bridge Dockerfile 安装 rosclaw 包
- [ ] bridge 启动日志不再显示 "degraded fallback mode"
- [ ] bridge 成功调用 ROSClaw SkillExecutor.execute()

### rosbridge_server
- [ ] ros2-turtlesim 容器安装 rosbridge_suite
- [ ] rosbridge WebSocket 端点 9090 可连接
- [ ] turtlesim_node 正常运行

## 阶段 3：边缘执行引擎

### 任务步骤迭代执行
- [ ] 边缘网关收到 start_mission 后按步骤顺序逐个执行技能
- [ ] 每步执行后发布状态更新（step_index、step_status）
- [ ] 所有步骤完成后发布 mission COMPLETED
- [ ] 步骤失败时发布 mission FAILED 并包含失败原因

### EdgeStateListener 修复
- [ ] 字段名从 `state` 修正为 `status`
- [ ] 正确解析 mission_id、step_index、step_status
- [ ] 收到 COMPLETED 时调用 completeMission()
- [ ] 收到 FAILED 时调用 failMission()
- [ ] mission_step.status 正确更新（PENDING -> RUNNING -> COMPLETED/FAILED）

## 阶段 4：实时监控

### WebSocket 事件推送
- [ ] `MonitorEventPublisher` 存在并在任务状态变化时被调用
- [ ] 前端 WebSocket 收到 mission update 事件
- [ ] 前端实时显示任务进度和状态变化

### Trace 记录
- [ ] `TraceRecorder` 在任务、安全、执行事件发生时写入 trace_span 和 fact_event
- [ ] trace REST 端点返回关联事件
- [ ] trace_id 在云端和边缘均可检索

### 安全状态监听
- [ ] 云端订阅 `edge.{gateway_id}.safety.state_changed`
- [ ] 安全状态变化时更新机器人投影并推送 WebSocket

## 阶段 5：动态重规划

### AgentRuntimeProvider 补全
- [ ] `continue_plan` 方法实现（基于失败上下文请求 QwenPaw 重规划）
- [ ] `cancel` 方法实现
- [ ] `health` 方法实现
- [ ] handler.py 新增 `opengeobot.agent.mission.replan` NATS 订阅

### 自动重规划
- [ ] `AgentRuntimeNatsClient.replanMission()` 方法实现
- [ ] `MissionOrchestrator.replanMission()` 构建包含已完成步骤和失败原因的请求
- [ ] `MissionService.failMission()` 后自动触发重规划
- [ ] 重规划成功后更新 mission_step 并重新 start
- [ ] 最大重规划次数限制（默认 3 次）

### E2E 完整闭环测试
- [ ] `rosclaw-sim` profile 启动所有必需服务
- [ ] E2E 测试：创建任务 -> 规划 -> 执行 -> COMPLETED
- [ ] trace_id 全链路关联验证
- [ ] 安全阻断场景验证（超速 -> BLOCK -> FAILED）
- [ ] 动态重规划验证（步骤失败 -> 自动重规划 -> 调整后成功）
