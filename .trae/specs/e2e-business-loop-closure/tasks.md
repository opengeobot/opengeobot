# Tasks

- [x] Task 1: 修复边缘 NATS 主题统一与安全网关路由
  - [x] SubTask 1.1: 修改 `edge/gateway/src/opengeobot_edge/config.py`，新增 `gateway_id` 和 `safety_gateway_skill_subject` 属性
  - [x] SubTask 1.2: 修改 `edge/gateway/src/opengeobot_edge/command_handler.py` 的 `_call_executor`，将技能执行请求发布到安全网关拦截主题
  - [x] SubTask 1.3: 修改边缘网关监听安全网关的状态变更广播 `edge.{gateway_id}.safety.state_changed`，同步本地急停锁存状态
  - [x] SubTask 1.4: 更新边缘网关 main.py 添加 `_subscribe_safety_state_changes` 和 `_handle_safety_state_change`
  - [x] SubTask 1.5: 验证安全网关 -> 技能执行器 -> 终端执行器 链路主题一致

- [x] Task 2: 集成 QwenPaw 真实镜像服务
  - [x] SubTask 2.1: 在 `deploy/compose/compose.yml` 的 `sim` profile 添加 `qwenpaw` 服务
  - [x] SubTask 2.2: 设置 agent-runtime 服务的 `QWENPAW_ENDPOINT` 环境变量为 `http://qwenpaw:8000/v1/chat/completions`
  - [x] SubTask 2.3: 添加 agent-runtime 服务到 compose
  - [x] SubTask 2.4: 配置 qwenpaw 和 agent-runtime 的健康检查和依赖关系

- [x] Task 3: 新增 ROSClaw NATS Bridge 服务
  - [x] SubTask 3.1: 创建 `services/rosclaw-bridge/` Python 项目（pyproject.toml + Dockerfile）
  - [x] SubTask 3.2: 实现 NATS 订阅器，订阅 `opengeobot.dev.edge.skill.execute.{robot_id}`
  - [x] SubTask 3.3: 实现请求翻译器：将 SkillExecutionRequest 映射到 ROSClaw SkillExecutor.execute 调用
  - [x] SubTask 3.4: 初始化 ROSClaw Runtime，连接 rosbridge WebSocket
  - [x] SubTask 3.5: 实现 emergency_stop 处理
  - [x] SubTask 3.6: 实现结果翻译器
  - [x] SubTask 3.7: 添加 config.py 配置
  - [x] SubTask 3.8: 编写 24 个单元测试（全部通过）
  - [x] SubTask 3.9: 在 compose 添加 rosclaw-bridge 服务

- [x] Task 4: 新增云端 AgentRuntime NATS 客户端（Java）
  - [x] SubTask 4.1: 创建 `AgentRuntimeNatsClient`，通过 NATS request-reply 调用 `opengeobot.agent.mission.plan_request`
  - [x] SubTask 4.2: 创建 DTOs（MissionContextDto, PlanProposalDto, PlanStepDto, EdgeCommandDto）
  - [x] SubTask 4.3: 编写单元测试验证序列化/反序列化（7 个测试通过）

- [x] Task 5: 新增云端边缘命令分发器（Java）
  - [x] SubTask 5.1: 创建 `EdgeCommandDispatcher`，通过 NATS JetStream 发布到 `opengeobot.dev.edge.command.{robot_id}`
  - [x] SubTask 5.2: 定义 EdgeCommandDto（command_type, mission_id, trace_id, skill_id, params）
  - [x] SubTask 5.3: 幂等 messageId 用于至少一次投递

- [x] Task 6: 新增云端边缘状态监听器（Java）
  - [x] SubTask 6.1: 创建 `EdgeStateListener`，订阅 `opengeobot.dev.edge.state.>`
  - [x] SubTask 6.2: 解析状态消息，更新机器人运行时投影
  - [x] SubTask 6.3: 收到 COMPLETED/FAILED 时调用 MissionService.completeMission/failMission
  - [x] SubTask 6.4: @PostConstruct 启动订阅

- [x] Task 7: 新增云端任务编排器（Java）
  - [x] SubTask 7.1: 创建 `MissionOrchestrator` 服务
  - [x] SubTask 7.2: 实现 `planWithAgent(missionId)`：QwenPaw 规划 -> 写入 mission_step -> Policy 校验
  - [x] SubTask 7.3: 实现 `executeMission(missionId)`：获取控制租约 -> 下发 EdgeCommand
  - [x] SubTask 7.4: 修改 `MissionController` 新增 `POST /api/v1/missions/{id}/plan-with-agent`
  - [x] SubTask 7.5: 修改 `MissionService.start()` 调用 `MissionOrchestrator.executeMission()`
  - [x] SubTask 7.6: 编写 6 个集成测试（全部通过），Java 编译成功

- [x] Task 8: 编写 E2E 业务闭环测试
  - [x] SubTask 8.1: 创建 `tests/e2e/test_business_loop.py`
  - [x] SubTask 8.2: 测试步骤：登录 -> 创建任务 -> plan-with-agent -> start -> 等待 COMPLETED
  - [x] SubTask 8.3: 测试验证 trace_id 在云端任务记录中存在
  - [x] SubTask 8.4: 测试包含安全阻断场景：超速参数 -> BLOCK -> FAILED
  - [x] SubTask 8.5: 在 `scripts/dev.sh` 的 `e2e` 子命令中添加运行 E2E 测试

- [x] Task 9: 更新 compose 配置与文档
  - [x] SubTask 9.1: compose sim profile 包含 qwenpaw、agent-runtime、rosclaw-bridge
  - [x] SubTask 9.2: agent-runtime 的 QWENPAW_ENDPOINT 指向 qwenpaw 服务
  - [x] SubTask 9.3: rosclaw-bridge 挂载 ROSClaw 源码目录
  - [x] SubTask 9.4: scripts/dev.sh sim-up 包含 qwenpaw、agent-runtime、rosclaw-bridge
  - [x] SubTask 9.5: scripts/dev.sh test 包含 rosclaw-bridge Python 测试
  - [x] SubTask 9.6: scripts/dev.sh e2e 运行 E2E 业务闭环测试

# Task Dependencies
- [Task 2] 无依赖，可与 Task 1 并行 ✓
- [Task 3] 无依赖，可与 Task 1/2 并行 ✓
- [Task 4] 无依赖，可与 Task 1/2/3 并行 ✓
- [Task 5] 依赖 [Task 4] ✓
- [Task 6] 依赖 [Task 4] ✓
- [Task 7] 依赖 [Task 4], [Task 5], [Task 6] ✓
- [Task 8] 依赖 [Task 1], [Task 2], [Task 3], [Task 7] ✓
- [Task 9] 依赖 [Task 2], [Task 3], [Task 7] ✓
