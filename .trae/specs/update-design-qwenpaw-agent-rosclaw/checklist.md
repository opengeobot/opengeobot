# Checklist

## EXT-QWENPAW 契约更新

- [x] `contracts/external/EXT-QWENPAW.md` 补充 QwenPaw 管理 API 契约
- [x] 文档包含 `POST /api/agents` 端点和 CreateAgentRequest 格式
- [x] 文档包含 `GET /api/agents/{id}` 查询端点
- [x] 文档包含 `PUT /api/agents/{id}` 更新端点
- [x] 说明智能体初始化通过 RESTful 管理 API，不违反 "no direct SDK call" 约束
- [x] 保留 `AgentRuntimeProvider` 作为唯一规划调用接口

## EXT-ROSCLAW 契约更新

- [x] `contracts/external/EXT-ROSCLAW.md` 将 "Platform NATS adapter wiring: not claimed DONE" 更新为已实现
- [x] 补充 ROSClaw Bridge NATS 主题（`opengeobot.dev.edge.skill.execute.{robot_id}`）
- [x] 补充请求/响应格式（SkillExecutionRequest/SkillExecutionResponse）
- [x] 补充降级模式描述

## AI 开发约束规范更新

- [x] 第 7.3 节补充 QwenPaw 智能体创建与初始化设计
- [x] 描述平台启动时创建"一脑多控"智能体
- [x] 描述 agent-runtime 启动阶段初始化流程
- [x] 补充 NATS 作为云端到 agent-runtime 传输协议
- [x] 第 8 节将 ROSClaw 更新为独立终端执行器服务
- [x] 补充完整端侧执行链路描述

## 实施蓝图更新

- [x] 第 7.3 节补充 NATS request-reply 传输描述
- [x] 补充智能体初始化在任务规划流程中的位置
- [x] 第 5.8 节补充 ROSClaw Bridge 在边缘执行管道中的位置
- [x] 补充端侧执行用例与 ROSClaw SkillExecutor 的映射

## 功能清单更新

- [x] F-MISSION-001 backend_modules 包含 `agent-runtime`（已存在）
- [x] F-EDGE-002 backend_modules 包含 `rosclaw-bridge`
- [x] F-ADAPTER-001 backend_modules 包含 `rosclaw-bridge`

## QwenPaw 智能体初始化实现

- [x] `config.py` 新增 `qwenpaw_admin_base_url`、`qwenpaw_agent_id`、`qwenpaw_agent_name`、`qwenpaw_agent_create_on_start` 配置
- [x] `initializer.py` 实现 `AgentInitializer` 类
- [x] `initialize()` 调用 `GET /api/agents/{id}` 检查智能体是否存在
- [x] 智能体不存在时调用 `POST /api/agents` 创建
- [x] 智能体已存在时调用 `PUT /api/agents/{id}` 更新 skill_names
- [x] QwenPaw 不可用时降级为无状态模式，不阻塞启动
- [x] `main.py` 启动流程中调用 `AgentInitializer.initialize()`
- [x] `provider.py` 在规划调用中携带智能体上下文
- [x] compose agent-runtime 服务添加 `QWENPAW_ADMIN_BASE_URL` 环境变量
- [x] 测试覆盖创建、已存在、降级场景（12 个新测试，50 总测试通过）
