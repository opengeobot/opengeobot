# Checklist

## 边缘链路修复

- [x] 边缘网关 `CommandHandler._call_executor` 将技能执行请求发布到安全网关拦截主题 `edge.{gateway_id}.skill.execute`，而非直连 `opengeobot.dev.edge.skill.execute.{robot_id}`
- [x] 边缘网关监听安全网关状态变更广播 `edge.{gateway_id}.safety.state_changed`，同步本地急停锁存状态
- [x] 安全网关收到技能执行请求后执行动作级校验（受限区域、速度限制、碰撞风险），通过后转发到 `edge.{gateway_id}.skill.execute.approved`
- [x] 技能执行器消费 `edge.{gateway_id}.skill.execute.approved` 主题，调用终端执行器的 `opengeobot.dev.edge.skill.execute.{robot_id}`
- [x] 终端执行器收到请求并执行技能，返回 `SkillExecutionResponse`
- [x] 执行结果沿原路回传：终端执行器 -> 技能执行器 -> 安全网关 -> 边缘网关 -> 云端
- [x] 边缘网关使用 `subscribe`（plain NATS）而非 JetStream 保留 reply subject
- [x] 安全网关使用 `subscribe`（plain NATS）而非 JetStream 保留 reply subject
- [x] 技能执行器使用 `subscribe`（plain NATS）而非 JetStream 保留 reply subject
- [x] 进程内 `SafetyStateMachine` 仅保留为急停快速锁存路径，动作级校验由独立安全网关执行

## QwenPaw 真实镜像服务

- [x] compose `sim` profile 包含 `qwenpaw` 服务，使用镜像 `agentscope-registry.ap-southeast-1.cr.aliyuncs.com/agentscope/qwenpaw:latest`
- [x] qwenpaw 服务暴露 8000 端口，有健康检查
- [x] agent-runtime 的 `QWENPAW_ENDPOINT` 环境变量为 `http://qwenpaw:8000/v1/chat/completions`
- [x] compose `sim` profile 包含 `agent-runtime` 服务

## ROSClaw NATS Bridge

- [x] `services/rosclaw-bridge/` 存在 Python 项目（pyproject.toml + Dockerfile）
- [x] bridge 订阅 `opengeobot.dev.edge.skill.execute.{robot_id}`，接收 `SkillExecutionRequest`
- [x] bridge 将请求翻译为 ROSClaw `SkillExecutor.execute(skill_name, parameters)` 调用
- [x] bridge 配置 `ROBOT_ROS_ENDPOINT`（rosbridge WebSocket URL）
- [x] bridge 处理 `emergency_stop` 技能请求
- [x] bridge 将执行结果翻译回 `SkillExecutionResponse` 并通过 NATS reply 回传
- [x] ROSClaw 不可用时 bridge 进入降级模式，仍响应请求（不挂起管道）
- [x] 24 个单元测试全部通过
- [x] compose `sim` profile 包含 `rosclaw-bridge` 服务，挂载 ROSClaw 源码目录

## 云端 AgentRuntime NATS 客户端

- [x] `AgentRuntimeNatsClient` 通过 NATS request-reply 调用 `opengeobot.agent.mission.plan_request`
- [x] 客户端正确序列化 `MissionContextDto`（mission_id, trace_id, robot_id, objective, constraints）
- [x] 客户端正确反序列化 `PlanProposalDto`（plan_id, steps, confidence, is_trusted, error）
- [x] DTO 单元测试覆盖序列化/反序列化（7 个测试通过）

## 云端边缘命令分发器

- [x] `EdgeCommandDispatcher` 通过 NATS JetStream 发布 `EdgeCommandDto` 到 `opengeobot.dev.edge.command.{robot_id}`
- [x] `EdgeCommandDto` 包含 command_type、mission_id、trace_id、skill_id、params 字段
- [x] 幂等 messageId 用于至少一次投递

## 云端边缘状态监听器

- [x] `EdgeStateListener` 订阅 `opengeobot.dev.edge.state.>` 接收边缘状态更新
- [x] 收到 COMPLETED 状态时调用 `MissionService.completeMission()`
- [x] 收到 FAILED 状态时调用 `MissionService.failMission()`
- [x] `@PostConstruct` 启动订阅

## 云端任务编排器

- [x] `MissionOrchestrator.planWithAgent(missionId)` 调用 QwenPaw 生成计划并写入 mission_step 表
- [x] `MissionOrchestrator.planWithAgent(missionId)` 调用 `PolicyService.evaluate()` 校验计划步骤
- [x] `MissionOrchestrator.executeMission(missionId)` 获取控制租约（ControlLeaseService.acquire）
- [x] `MissionOrchestrator.executeMission(missionId)` 调用 `EdgeCommandDispatcher` 下发 start_mission 命令
- [x] `MissionController` 新增 `POST /api/v1/missions/{id}/plan-with-agent` 端点
- [x] `MissionService.start()` 调用 `MissionOrchestrator.executeMission()` 而非仅改 DB 状态
- [x] 编排器全程传递 `trace_id`
- [x] 集成测试验证编排器流程（6 个测试通过），Java 编译 BUILD SUCCESS

## E2E 业务闭环测试

- [x] `tests/e2e/test_business_loop.py` 存在并可运行
- [x] 测试启动服务栈（含 qwenpaw、agent-runtime、rosclaw-bridge）
- [x] 测试完成完整流程：登录 -> 创建任务 -> plan-with-agent -> start -> 等待 COMPLETED
- [x] 测试验证 trace_id 在云端任务记录中存在
- [x] 测试验证安全网关拦截记录存在
- [x] 测试包含安全阻断场景：超速参数 -> BLOCK -> FAILED
- [x] `scripts/dev.sh` 的 `e2e` 子命令运行 E2E 测试

## 文档与配置

- [x] `deploy/compose/compose.yml` sim profile 包含 qwenpaw、agent-runtime、rosclaw-bridge 服务
- [x] `scripts/dev.sh` 的 `sim-up` 子命令包含 qwenpaw、agent-runtime、rosclaw-bridge
- [x] `scripts/dev.sh` 的 `test` 子命令包含 rosclaw-bridge 的 Python 测试
- [x] `scripts/dev.sh` 的 `e2e` 子命令运行 E2E 业务闭环测试
