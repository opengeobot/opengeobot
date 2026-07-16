# 设计文档更新：QwenPaw 智能体初始化与 ROSClaw 端侧控制 Spec

## Why

审计发现设计文档与实现存在显著差距：设计文档将 QwenPaw 描述为外部 LLM API 端点（无状态调用），但实际 QwenPaw 容器是多智能体平台，支持通过 `POST /api/agents` 创建持久化智能体。当前 agent-runtime 仅做无状态 `/v1/chat/completions` 调用，没有在 QwenPaw 中创建与平台打通的"一脑多控"智能体。同时，ROSClaw NATS Bridge 已实现但设计文档和 EXT-ROSCLAW 契约未更新。需要更新设计文档以反映实际架构，并新增 QwenPaw 智能体初始化和 ROSClaw 端侧控制的设计。

## What Changes

### 设计文档更新
- 更新 `docs/AI开发约束与平台公共能力规范 V1.0.md` 第 7.3 节：补充 QwenPaw 智能体创建与初始化设计，描述平台启动时通过 QwenPaw 管理 API 创建"一脑多控"智能体，绑定平台已注册技能作为工具
- 更新 `docs/AI开发约束与平台公共能力规范 V1.0.md` 第 8 节：补充 ROSClaw NATS Bridge 作为独立边缘终端执行器服务的设计，替代原"ROSClaw Edge Runtime Adapter 作为 Edge Gateway 组件"的描述
- 更新 `docs/平台功能与数据状态统一实施蓝图 V1.0.md` 第 7.3 节：补充 NATS 作为云端到 agent-runtime 的传输协议描述
- 更新 `docs/平台功能与数据状态统一实施蓝图 V1.0.md` 第 5.8 节：补充 ROSClaw Bridge 在边缘执行管道中的位置
- 更新 `docs/implementation/platform-feature-manifest.yaml`：在 F-MISSION-001 和 F-EDGE-002 的 backend_modules 中添加 `agent-runtime` 和 `rosclaw-bridge`
- 更新 `contracts/external/EXT-QWENPAW.md`：补充 QwenPaw 管理 API 契约（`/api/agents` 端点、CreateAgentRequest 格式）
- 更新 `contracts/external/EXT-ROSCLAW.md`：将 "Platform NATS adapter wiring: not claimed DONE" 更新为已实现

### QwenPaw 智能体初始化（新增功能）
- agent-runtime 启动时通过 QwenPaw 管理 API `POST /api/agents` 创建（或验证已存在）"一脑多控"智能体
- 智能体绑定平台已注册技能（stand_up, move_forward, stop 等）作为 skill_names
- 智能体使用专用 workspace_dir 存储任务记忆和上下文
- 后续规划调用使用该智能体的上下文（而非无状态 chat-completions），实现任务规划的连续性
- 新增 `AgentInitializer` 组件，在 agent-runtime 启动阶段执行智能体初始化

### ROSClaw 端侧控制（设计确认）
- 确认 ROSClaw NATS Bridge 作为边缘终端执行器的设计
- 端侧执行链路：边缘网关 -> 安全网关 -> 技能执行器 -> ROSClaw Bridge -> ROSClaw SkillExecutor -> rosbridge -> 机器人硬件
- ROSClaw 自身安全管线（FirewallValidator）作为端侧第二道防线

## Impact

- Affected specs: F-MISSION-001, F-EDGE-002, F-ADAPTER-001, F-SAFETY-001
- Affected code:
  - `docs/AI开发约束与平台公共能力规范 V1.0.md` - 更新第 7.3 和 8 节
  - `docs/平台功能与数据状态统一实施蓝图 V1.0.md` - 更新第 7.3 和 5.8 节
  - `docs/implementation/platform-feature-manifest.yaml` - 更新 backend_modules
  - `contracts/external/EXT-QWENPAW.md` - 补充管理 API 契约
  - `contracts/external/EXT-ROSCLAW.md` - 更新 NATS 适配器状态
  - `services/agent-runtime/src/opengeobot_agent/` - 新增 AgentInitializer，修改 provider 使用智能体上下文
  - `services/agent-runtime/src/opengeobot_agent/config.py` - 新增 QwenPaw 管理 API 配置

## ADDED Requirements

### Requirement: QwenPaw 智能体初始化
系统 SHALL 在 agent-runtime 启动时通过 QwenPaw 管理 API 创建（或验证已存在）一个名为"一脑多控"的持久化智能体，该智能体绑定平台已注册技能作为工具，实现任务规划的连续性和上下文感知。

#### Scenario: 首次启动创建智能体
- **WHEN** agent-runtime 首次启动且 QwenPaw 中不存在 ID 为 `opengeobot-controller` 的智能体
- **THEN** agent-runtime 调用 `POST /api/agents` 创建智能体
- **AND** 请求体包含 `name: "一脑多控"`, `description: "OpenGeoBot 平台统一任务规划与控制智能体"`
- **AND** `skill_names` 包含平台已注册技能列表
- **AND** `workspace_dir` 指向专用工作目录
- **AND** 创建成功后存储智能体 ID 用于后续规划调用

#### Scenario: 重启时验证智能体已存在
- **WHEN** agent-runtime 启动且 QwenPaw 中已存在 ID 为 `opengeobot-controller` 的智能体
- **THEN** agent-runtime 调用 `GET /api/agents/opengeobot-controller` 验证智能体存在
- **AND** 如有新注册技能，调用 `PUT /api/agents/opengeobot-controller` 更新 skill_names
- **AND** 不重复创建智能体

#### Scenario: QwenPaw 不可用时降级
- **WHEN** agent-runtime 启动时 QwenPaw 管理 API 不可用
- **THEN** agent-runtime 记录警告日志
- **AND** 降级为无状态 chat-completions 模式（当前行为）
- **AND** 不阻塞 agent-runtime 启动和 NATS 订阅

### Requirement: 智能体上下文感知规划
系统 SHALL 在调用 QwenPaw 进行任务规划时使用已创建智能体的上下文，而非无状态调用，使规划结果能够利用历史任务记忆和平台技能定义。

#### Scenario: 基于智能体上下文的规划
- **WHEN** agent-runtime 收到任务规划请求
- **AND** 智能体已成功初始化
- **THEN** 规划调用包含智能体 ID 和会话上下文
- **AND** QwenPaw 可基于智能体的 skill_names 理解可用技能
- **AND** 规划结果仍标记为 `is_trusted=False`（不可信提案）

### Requirement: ROSClaw 端侧设备控制设计确认
系统设计文档 SHALL 确认 ROSClaw NATS Bridge 作为边缘终端执行器，通过 ROSClaw SkillExecutor 和 rosbridge WebSocket 实现对真实机器人硬件的控制。

#### Scenario: 端侧控制链路
- **WHEN** 安全网关批准技能执行请求
- **THEN** 技能执行器将请求转发到 ROSClaw Bridge（`opengeobot.dev.edge.skill.execute.{robot_id}`）
- **AND** ROSClaw Bridge 调用 ROSClaw `SkillExecutor.execute(skill_name, parameters)`
- **AND** ROSClaw 通过 rosbridge WebSocket 向机器人发布控制指令
- **AND** ROSClaw FirewallValidator 执行 e-URDF 软限位和碰撞检测
- **AND** 执行结果通过 NATS 回传到云端

## MODIFIED Requirements

### Requirement: QwenPaw 集成（EXT-QWENPAW 契约）
原契约仅描述 `AgentRuntimeProvider` 适配接口，不包含直接 SDK 调用。修改为：
- 保留 `AgentRuntimeProvider` 作为唯一规划调用接口
- 新增 QwenPaw 管理 API 契约：`POST /api/agents`（创建智能体）、`GET /api/agents/{id}`（查询智能体）、`PUT /api/agents/{id}`（更新智能体）
- 智能体初始化不视为"直接 SDK 调用"，而是通过 QwenPaw 的 RESTful 管理 API 进行，与 LLM 推理 API 分离

### Requirement: ROSClaw NATS 适配（EXT-ROSCLAW 契约）
原契约声明 "Platform NATS adapter wiring: not claimed DONE (sandbox parallel only)"。修改为：
- Platform NATS adapter wiring: **DONE** - 通过 `services/rosclaw-bridge/` 实现
- ROSClaw Bridge 订阅 NATS `opengeobot.dev.edge.skill.execute.{robot_id}`，翻译请求并调用 ROSClaw SkillExecutor
- 桥接器在 ROSClaw 不可用时进入降级模式，仍响应 NATS 请求以防管道阻塞
