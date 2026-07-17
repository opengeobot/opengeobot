# QwenPaw 智能体角色背景与 MCP 服务配置 Spec

## Why

当前 `AgentInitializer` 仅通过 `POST /api/agents` 创建 QwenPaw 智能体，只设置了基础字段（name、description、skill_names、active_model）。缺少两个关键配置：

1. **角色背景（persona）**：智能体没有"一脑多控"的角色定义、行为约束和安全边界。provider.py 中的 system prompt 是硬编码的通用文案，未与 QwenPaw 智能体的 persona 文件关联。
2. **MCP 服务配置**：智能体未配置连接平台 MCP Tool Gateway，无法通过 MCP 协议调用平台已注册的 Skill/Capability。

这导致 QwenPaw 智能体在任务规划时缺少角色上下文和工具能力，不符合 AI 规范 7.3.x 的完整初始化要求。

## What Changes

- 在 `AgentInitializer` 创建智能体后，通过 `PUT /api/agents/{id}` 写入完整 `AgentProfileConfig`，包含：
  - `system_prompt_files`: persona 文件列表（AGENTS.md、SOUL.md、PROFILE.md）
  - `mcp.clients`: 配置平台 MCP Tool Gateway 为 SSE/HTTP MCP 客户端
  - `approval_level`: 设置为 `STRICT` 以强制工具执行审批
- 向 QwenPaw 智能体工作目录写入 persona 文件内容（AGENTS.md、SOUL.md、PROFILE.md），定义"一脑多控"角色背景、行为约束和安全红线
- 将 provider.py 中硬编码的 system prompt 替换为从配置驱动的角色背景，保持 `_build_system_prompt` 与 persona 文件内容一致
- 在 `AgentConfig` 中新增 MCP Tool Gateway URL、persona 文件路径等环境变量配置
- 更新 EXT-QWENPAW 契约文档，补充 `AgentProfileConfig` 的 mcp 和 system_prompt_files 字段说明
- 新增/更新单元测试覆盖 persona 写入、MCP 配置、PUT 更新流程

## Impact

- Affected specs: F-MISSION-001（任务创建规划）、F-MCP-001（MCP 工具注册与调用）、EXT-QWENPAW 外部契约
- Affected code:
  - `services/agent-runtime/src/opengeobot_agent/initializer.py` — 核心初始化逻辑
  - `services/agent-runtime/src/opengeobot_agent/config.py` — 新增配置项
  - `services/agent-runtime/src/opengeobot_agent/provider.py` — system prompt 对齐 persona
  - `services/agent-runtime/src/opengeobot_agent/main.py` — 传递 persona/MCP 配置
  - `services/agent-runtime/tests/test_initializer.py` — 测试覆盖
  - `contracts/external/EXT-QWENPAW.md` — 契约补充

## ADDED Requirements

### Requirement: 智能体角色背景配置

智能体初始化时 SHALL 向 QwenPaw 智能体工作目录写入 persona 文件（AGENTS.md、SOUL.md、PROFILE.md），并通过 `AgentProfileConfig.system_prompt_files` 注册这些文件。persona 内容 SHALL 定义"一脑多控"智能体的角色、能力边界、安全红线和输出格式约束。

#### Scenario: 首次创建智能体时写入 persona
- **WHEN** agent-runtime 启动且 QwenPaw 中不存在 `opengeobot-controller` 智能体
- **THEN** 通过 `POST /api/agents` 创建智能体后，再通过 `PUT /api/agents/{id}` 写入包含 `system_prompt_files` 和 `mcp` 的完整 `AgentProfileConfig`
- **AND** persona 文件内容被写入智能体 `workspace_dir` 下

#### Scenario: 已存在智能体时更新 persona
- **WHEN** agent-runtime 启动且 QwenPaw 中已存在 `opengeobot-controller` 智能体
- **THEN** 通过 `PUT /api/agents/{id}` 更新 `system_prompt_files`、`mcp.clients` 和 `skill_names`
- **AND** 当 persona 配置或 MCP 配置发生变化时才执行 PUT，避免无变更的重复写入

#### Scenario: QwenPaw 管理 API 不可用时降级
- **WHEN** QwenPaw 管理 API 不可达或返回错误
- **THEN** agent-runtime 降级为无状态 `/v1/chat/completions` 模式，不阻塞启动

### Requirement: MCP 服务技能配置

智能体初始化时 SHALL 通过 `AgentProfileConfig.mcp.clients` 配置平台 MCP Tool Gateway 为 MCP 客户端，传输方式为 SSE 或 streamable_http。MCP 客户端配置 SHALL 包含平台 MCP Tool Gateway 的 URL 和认证头。

#### Scenario: 配置 MCP Tool Gateway 连接
- **WHEN** 智能体创建或更新时
- **THEN** `AgentProfileConfig.mcp.clients` 包含一个指向平台 MCP Tool Gateway 的客户端配置
- **AND** 该配置的 `transport` 为 `sse` 或 `streamable_http`
- **AND** 该配置的 `url` 指向平台 MCP Tool Gateway 的 HTTP 端点

#### Scenario: MCP Tool Gateway URL 未配置
- **WHEN** 环境变量未设置 MCP Tool Gateway URL
- **THEN** 跳过 MCP 客户端配置，仅写入 persona 和 skill_names
- **AND** 记录警告日志说明 MCP 未配置

### Requirement: Provider system prompt 与 persona 对齐

`QwenPawProvider._build_system_prompt` SHALL 使用与 persona 文件一致的角色背景文案，而非硬编码的通用文案。角色背景 SHALL 包含"一脑多控"智能体的身份、安全红线（不直接调用 /cmd_vel、不绕过 Safety Gateway）和输出格式（PlanProposal JSON Schema）。

#### Scenario: 无状态模式下使用角色背景
- **WHEN** provider 在无状态模式下运行（QwenPaw 智能体未初始化）
- **THEN** system prompt 仍包含角色背景和安全约束，通过配置注入而非硬编码

## MODIFIED Requirements

### Requirement: 智能体初始化流程

原流程：GET 检查 -> POST 创建 或 PUT 更新 skill_names。

修改后流程：
1. GET `/api/agents/{id}` 检查智能体是否存在
2. 若不存在：POST `/api/agents` 创建（基础字段）-> PUT `/api/agents/{id}` 写入完整配置（persona + mcp + skill_names）
3. 若已存在：比较 skill_names、mcp 配置和 persona 版本，有变更时 PUT `/api/agents/{id}` 更新
4. QwenPaw 管理 API 不可用时降级为无状态模式

### Requirement: AgentConfig 配置项

新增以下环境变量配置：
- `QWENPAW_MCP_GATEWAY_URL`: 平台 MCP Tool Gateway 的 SSE/HTTP 端点 URL
- `QWENPAW_MCP_GATEWAY_AUTH_TOKEN`: MCP 客户端认证 token（可选）
- `QWENPAW_PERSONA_DIR`: persona 文件模板目录（可选，缺省使用内置模板）
- `QWENPAW_AGENT_APPROVAL_LEVEL`: 工具执行审批级别（缺省 `STRICT`）
