# Tasks

- [x] Task 1: 更新 EXT-QWENPAW 外部契约
  - [x] SubTask 1.1: 补充 QwenPaw 管理 API 契约：`POST /api/agents`、`GET /api/agents/{id}`、`PUT /api/agents/{id}`
  - [x] SubTask 1.2: 描述 CreateAgentRequest 字段
  - [x] SubTask 1.3: 说明智能体初始化不违反"no direct SDK call"约束

- [x] Task 2: 更新 EXT-ROSCLAW 外部契约
  - [x] SubTask 2.1: 将 "Platform NATS adapter wiring: not claimed DONE" 更新为已实现
  - [x] SubTask 2.2: 补充 ROSClaw Bridge NATS 主题、请求/响应格式和降级模式

- [x] Task 3: 更新 AI 开发约束与平台公共能力规范
  - [x] SubTask 3.1: 更新第 7.3 节：补充 QwenPaw 智能体创建与初始化设计
  - [x] SubTask 3.2: 描述平台启动时创建"一脑多控"智能体
  - [x] SubTask 3.3: 描述 agent-runtime 启动阶段初始化流程
  - [x] SubTask 3.4: 补充 NATS 传输协议描述
  - [x] SubTask 3.5: 更新第 8 节：ROSClaw 为独立终端执行器服务
  - [x] SubTask 3.6: 补充完整端侧执行链路

- [x] Task 4: 更新平台功能与数据状态统一实施蓝图
  - [x] SubTask 4.1: 补充 NATS request-reply 传输描述
  - [x] SubTask 4.2: 补充智能体初始化在规划流程中的位置
  - [x] SubTask 4.3: 补充 ROSClaw Bridge 在边缘执行管道中的位置
  - [x] SubTask 4.4: 补充端侧执行用例与 ROSClaw SkillExecutor 的映射

- [x] Task 5: 更新功能清单 manifest
  - [x] SubTask 5.1: F-MISSION-001 已包含 agent-runtime（已存在）
  - [x] SubTask 5.2: F-EDGE-002 backend_modules 添加 rosclaw-bridge
  - [x] SubTask 5.3: F-ADAPTER-001 backend_modules 添加 rosclaw-bridge

- [x] Task 6: 实现 QwenPaw 智能体初始化（AgentInitializer）
  - [x] SubTask 6.1: config.py 新增管理 API 配置
  - [x] SubTask 6.2: 创建 initializer.py 实现 AgentInitializer 类
  - [x] SubTask 6.3: main.py 启动流程中调用初始化
  - [x] SubTask 6.4: provider.py 携带智能体上下文
  - [x] SubTask 6.5: compose 添加 QWENPAW_ADMIN_BASE_URL
  - [x] SubTask 6.6: 12 个测试全部通过（50 总测试无回归）

# Task Dependencies
- [Task 1] ✓ [Task 2] ✓ 并行完成
- [Task 3] ✓ 依赖 [Task 1]
- [Task 4] ✓ 依赖 [Task 1], [Task 2]
- [Task 5] ✓ 并行完成
- [Task 6] ✓ 依赖 [Task 1]
