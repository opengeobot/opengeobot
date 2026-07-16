# Tasks

## 阶段 1：关键基础修复（阻断性缺陷）

- [ ] Task 1: 修复权限码缺失
  - [ ] SubTask 1.1: 新增 Flyway migration 添加 `mission.mission.create`、`mission.mission.pause`、`mission.mission.cancel`、`mission.mission.approve` 权限码
  - [ ] SubTask 1.2: 在 migration 中将新权限码分配给 SYS_ADMIN 和 OPERATOR 角色
  - [ ] SubTask 1.3: 运行 migration 验证权限码生效

- [ ] Task 2: 修复 QwenPaw API 调用
  - [ ] SubTask 2.1: 在 `provider.py` 的 `_build_request_payload` 中将 `model` 字段从硬编码 "qwenpaw" 改为从 config 读取（`QWENPAW_MODEL` 环境变量，默认使用 agent_id）
  - [ ] SubTask 2.2: 在 `initializer.py` 的 `_create_agent` 中添加 `active_model` 字段（从 config 读取 `QWENPAW_MODEL_PROVIDER` 和 `QWENPAW_MODEL_NAME`）
  - [ ] SubTask 2.3: 在 `provider.py` 的 system prompt 中注入可用技能 ID 列表和每个技能的 input_schema 参数说明
  - [ ] SubTask 2.4: 在 `provider.py` 的 `_parse_response` 中 strip markdown code fences（` ```json ` 和 ` ``` `）后再 `json.loads`
  - [ ] SubTask 2.5: 在 `config.py` 新增 `qwenpaw_model`、`qwenpaw_model_provider`、`qwenpaw_model_name` 配置项
  - [ ] SubTask 2.6: 在 compose agent-runtime 环境变量中添加 QWENPAW_MODEL 等配置
  - [ ] SubTask 2.7: 更新测试验证修复

- [ ] Task 3: 修复 E2E 测试
  - [ ] SubTask 3.1: 修正 `test_business_loop.py` 的 `create_mission` 请求体（添加 `name`、`steps` 字段，移除 `objective`、`priority`）
  - [ ] SubTask 3.2: 修正 mission_id 字段提取（`mission_id` 而非 `id`）
  - [ ] SubTask 3.3: 修正 `plan_with_agent` 和 `start_mission` 的 API 路径
  - [ ] SubTask 3.4: 添加轮询等待和超时处理

- [ ] Task 4: 修复 Compose 配置
  - [ ] SubTask 4.1: 从 `sim` profile 移除 `rosclaw-bridge`（仅保留在 `rosclaw-sim` 和 `full` profile）
  - [ ] SubTask 4.2: 新增 `rosclaw-sim` profile（infra + sim 边缘服务栈 + rosclaw-bridge + ros2-turtlesim with rosbridge，不包含 sim-adapter）
  - [ ] SubTask 4.3: 修正 QwenPaw 健康检查为 `/api/healthz`（或确认 `/healthz` 可用）
  - [ ] SubTask 4.4: 调整超时：Java 端 60s，Python QWENPAW_TIMEOUT 45s

- [ ] Task 5: 新增技能列表 NATS 响应器
  - [ ] SubTask 5.1: 在云端新增 `SkillListNatsResponder`（Java），订阅 `opengeobot.skill.list`，返回已注册技能定义列表（skill_id, input_schema）
  - [ ] SubTask 5.2: 编写测试验证响应器返回正确技能列表

## 阶段 2：ROSClaw 端侧执行修复

- [ ] Task 6: 修复 ROSClaw Bridge 安装
  - [ ] SubTask 6.1: 修改 `services/rosclaw-bridge/Dockerfile` 安装 ROSClaw 包（`uv pip install --system /opt/rosclaw-source` 或从 git 安装）
  - [ ] SubTask 6.2: 在 compose `rosclaw-bridge` 服务中确保 ROSClaw 源码挂载可写（安装需要写入 site-packages）
  - [ ] SubTask 6.3: 验证 ROSClaw 包成功导入（bridge 日志不再显示 degraded mode）

- [ ] Task 7: 安装 rosbridge_server
  - [ ] SubTask 7.1: 创建 `services/ros2-turtlesim/Dockerfile`（基于 ros:jazzy-ros-core，安装 ros-jazzy-rosbridge-suite 和 ros-jazzy-turtlesim）
  - [ ] SubTask 7.2: 修改 compose `ros2-turtlesim` 服务使用自定义 Dockerfile，启动 rosbridge_server 和 turtlesim_node
  - [ ] SubTask 7.3: 暴露 9090 端口
  - [ ] SubTask 7.4: 验证 rosbridge WebSocket 端点可连接

## 阶段 3：边缘执行引擎

- [ ] Task 8: 实现边缘任务步骤迭代执行
  - [ ] SubTask 8.1: 在 `edge/gateway/src/opengeobot_edge/command_handler.py` 的 `_start_mission` 中，加载 mission plan 步骤，按顺序逐个调用 `_call_executor` 执行技能
  - [ ] SubTask 8.2: 每步执行后发布状态更新（包含 step_index、step_status、mission_id）
  - [ ] SubTask 8.3: 所有步骤完成后发布 mission COMPLETED 状态
  - [ ] SubTask 8.4: 某步失败时发布 mission FAILED 状态并包含失败原因

- [ ] Task 9: 修复 EdgeStateListener
  - [ ] SubTask 9.1: 修正 `EdgeStateListener.java` 字段名从 `state` 改为 `status`
  - [ ] SubTask 9.2: 修正状态值映射：解析 mission_id、step_index、step_status 字段
  - [ ] SubTask 9.3: 收到 mission COMPLETED 时调用 `MissionService.completeMission()`
  - [ ] SubTask 9.4: 收到 mission FAILED 时调用 `MissionService.failMission()`
  - [ ] SubTask 9.5: 更新 mission_step.status（PENDING -> RUNNING -> COMPLETED/FAILED）

## 阶段 4：实时监控

- [ ] Task 10: 实现 WebSocket 事件推送
  - [ ] SubTask 10.1: 创建 `MonitorEventPublisher` 类，在任务状态变化时调用 `broadcastMissionUpdate`
  - [ ] SubTask 10.2: 在 `MissionService` 的状态转换方法中调用 `MonitorEventPublisher`
  - [ ] SubTask 10.3: 在 `EdgeStateListener` 收到状态更新时调用 `MonitorEventPublisher.broadcastRobotUpdate`
  - [ ] SubTask 10.4: 验证前端 WebSocket 收到实时推送

- [ ] Task 11: 实现 Trace 记录
  - [ ] SubTask 11.1: 创建 `TraceRecorder` 类，在任务、安全、执行事件发生时写入 trace_span 和 fact_event 表
  - [ ] SubTask 11.2: 在 `MissionService`、`SafetyService`、`EdgeStateListener` 中调用 `TraceRecorder`
  - [ ] SubTask 11.3: 验证 trace REST 端点返回关联事件

- [ ] Task 12: 云端安全状态监听
  - [ ] SubTask 12.1: 在云端新增对 `edge.{gateway_id}.safety.state_changed` 的 NATS 订阅
  - [ ] SubTask 12.2: 安全状态变化时更新机器人投影并推送 WebSocket 通知

## 阶段 5：动态重规划

- [ ] Task 13: 补全 AgentRuntimeProvider 接口
  - [ ] SubTask 13.1: 在 `provider.py` 实现 `continue_plan` 方法（基于已完成步骤和失败原因请求 QwenPaw 重新规划）
  - [ ] SubTask 13.2: 在 `provider.py` 实现 `cancel` 方法（取消进行中的规划请求）
  - [ ] SubTask 13.3: 在 `provider.py` 实现 `health` 方法（返回 provider 健康状态）
  - [ ] SubTask 13.4: 在 `handler.py` 新增 `opengeobot.agent.mission.replan` NATS 订阅处理

- [ ] Task 14: 实现自动重规划
  - [ ] SubTask 14.1: 在 `AgentRuntimeNatsClient` 新增 `replanMission` 方法（NATS request-reply 到 `opengeobot.agent.mission.replan`）
  - [ ] SubTask 14.2: 在 `MissionOrchestrator` 新增 `replanMission` 方法（构建包含已完成步骤和失败原因的 replan 请求）
  - [ ] SubTask 14.3: 修改 `MissionService.failMission()` 在标记 FAILED 后自动调用 `MissionOrchestrator.replanMission()`
  - [ ] SubTask 14.4: 重规划成功后更新 mission_step 表并重新 start
  - [ ] SubTask 14.5: 设置最大重规划次数限制（默认 3 次）

- [ ] Task 15: E2E 完整闭环测试
  - [ ] SubTask 15.1: 启动 `rosclaw-sim` profile（infra + cloud + qwenpaw + agent-runtime + edge + rosclaw-bridge + ros2-turtlesim with rosbridge）
  - [ ] SubTask 15.2: 执行 E2E 测试：登录 -> 创建任务 -> plan-with-agent -> start -> 监控执行 -> COMPLETED
  - [ ] SubTask 15.3: 验证 trace_id 全链路关联
  - [ ] SubTask 15.4: 验证安全阻断场景（超速参数 -> BLOCK -> FAILED）
  - [ ] SubTask 15.5: 验证动态重规划场景（模拟步骤失败 -> 自动重规划 -> 调整后执行成功）

# Task Dependencies
- [Task 1-5] 阶段 1，可部分并行（Task 1/3/4 独立，Task 2 依赖 Task 5 的技能列表用于 prompt）
- [Task 6-7] 阶段 2，依赖 [Task 4] compose 修复
- [Task 8-9] 阶段 3，依赖 [Task 1-5] 阶段 1 完成
- [Task 10-12] 阶段 4，依赖 [Task 8-9] 边缘执行引擎
- [Task 13-15] 阶段 5，依赖 [Task 10-12] 监控完成
