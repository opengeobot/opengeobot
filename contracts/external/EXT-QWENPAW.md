<!--
Function: EXT-QWENPAW pinned reference — QwenPaw agent runtime contract
Time: 2026-07-05
Author: AxeXie
-->

# EXT-QWENPAW — QwenPaw Agent Runtime

## Pinned Reference
- Official source: https://qwenpaw.agentscope.io/docs/
- Pin status: PINNED
- Version: M2 simulation (AgentRuntimeProvider adapter, no direct SDK call)

## Contract
- QwenPaw接入仅通过 `AgentRuntimeProvider` 适配接口
- Agent输出为不可信提案，必须经Schema、权限、状态机、资源和安全校验
- 不得直接调用 /cmd_vel、关节、电机或厂商 SDK

## Required By
- F-MCP-001 (MCP Tool registry & invocation)
- F-MISSION-001 (Mission creation & planning)

## QwenPaw Management API

QwenPaw 容器同时暴露一个 RESTful 管理 API（前缀 `/api`），用于智能体生命周期管理。
此 API 与 LLM 推理 API（`/v1/chat/completions`）分离，用于平台初始化时创建和配置智能体。
通过 RESTful HTTP 管理 API 操作智能体不违反 "no direct SDK call" 约束。

### 端点

| Method | Path | 用途 |
|--------|------|------|
| GET | `/api/agents` | 列出所有智能体 |
| POST | `/api/agents` | 创建新智能体 |
| GET | `/api/agents/{agentId}` | 查询指定智能体配置 |
| PUT | `/api/agents/{agentId}` | 更新智能体配置 |
| DELETE | `/api/agents/{agentId}` | 删除智能体 |
| PATCH | `/api/agents/{agentId}` | 启用/禁用智能体 |
| GET | `/api/healthz` | 健康检查（返回 503 直到所有智能体启动完成） |

### CreateAgentRequest

```json
{
  "id": "opengeobot-controller",
  "name": "一脑多控",
  "description": "OpenGeoBot 平台统一任务规划与控制智能体",
  "workspace_dir": "/app/working/workspaces/opengeobot-controller",
  "language": "zh",
  "skill_names": ["stand_up", "move_forward", "stop", "capture_image"],
  "active_model": {
    "provider_id": "opencode",
    "model": "mimo-v2.5-free"
  }
}
```

- `id`（可选）：智能体 ID，省略时自动生成。平台使用固定 ID `opengeobot-controller`。
- `name`（必填）：智能体显示名称。
- `description`：智能体描述。
- `workspace_dir`：工作目录路径，用于存储记忆和上下文。
- `language`：智能体语言（`zh` 或 `en`）。
- `skill_names`：绑定的技能名称列表，对应平台已注册 Skill。
- `active_model`：激活的 LLM 模型配置。

### 智能体初始化流程

1. agent-runtime 启动时调用 `GET /api/agents/{agent_id}` 检查智能体是否已存在。
2. 若不存在，调用 `POST /api/agents` 创建智能体，绑定平台已注册技能。
3. 若已存在，调用 `PUT /api/agents/{agent_id}` 更新 skill_names（同步新增技能）。
4. QwenPaw 管理 API 不可用时，agent-runtime 降级为无状态 `/v1/chat/completions` 模式。
