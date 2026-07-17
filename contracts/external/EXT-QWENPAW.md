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
2. 若不存在：
   - 调用 `POST /api/agents` 创建智能体（基础字段）。
   - 调用 `PUT /api/agents/{agent_id}` 写入完整 `AgentProfileConfig`（`system_prompt_files` + `mcp.clients` + `approval_level`）。
3. 若已存在：仅比较 `GET /api/agents/{agent_id}` 可见且由平台管理的字段（`name`、`description`、`workspace_dir`、`language`、`system_prompt_files`、`mcp.clients`、`approval_level`、`active_model`），存在真实差异时才调用 `PUT /api/agents/{agent_id}` 更新。
4. QwenPaw 管理 API 不可用时，agent-runtime 降级为无状态 `/v1/chat/completions` 模式。

### AgentProfileConfig (PUT /api/agents/{agentId})

`PUT /api/agents/{agentId}` 接收当前运行时已验证可接受的 `AgentProfileConfig` 字段集合，用于写入智能体的完整配置（人设文件、MCP 客户端、审批级别等）。该端点与 `POST /api/agents`（仅写入基础字段）配合使用：先创建再写入完整配置。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | str | 智能体 ID（更新时只读） |
| `name` | str | 智能体显示名称 |
| `description` | str | 智能体描述 |
| `workspace_dir` | str | 工作目录路径，用于存储记忆、上下文与人设文件 |
| `language` | str | 语言（`zh` 或 `en`） |
| `system_prompt_files` | List[str] | 人设文件列表（默认：`["AGENTS.md", "SOUL.md", "PROFILE.md"]`） |
| `mcp` | MCPConfig | MCP 客户端配置，见下节 |
| `approval_level` | str | 工具执行安全级别：`STRICT` / `SMART` / `AUTO` / `OFF` |
| `active_model` | ModelSlotConfig | 激活的 LLM 模型配置 |

- `approval_level` 决定 QwenPaw 调用工具前的审批策略；平台默认 `STRICT`，所有物理动作技能必须经过 IAM、Mission、Policy、Fleet 与 Edge Safety 校验后才执行。
- `active_model` 与 `CreateAgentRequest.active_model` 结构一致，可在更新时切换模型。
- Agent 输出始终为不可信提案，`approval_level` 不绕过平台确定性安全校验。
- 当前运行时兼容性证据表明，`PUT /api/agents/{agentId}` 不应发送 `skill_names`。

### MCPConfig & MCPClientConfig

`AgentProfileConfig.mcp` 字段使用 `MCPConfig` 结构，通过 `clients` 字典按客户端名称管理多个 MCP 客户端。QwenPaw 仅通过 MCP 协议调用工具，不直接访问 `/cmd_vel`、关节、电机或厂商 SDK。

```
MCPConfig:
  clients: Dict[str, MCPClientConfig]  # 按客户端名称索引

MCPClientConfig:
  name: str                    # 必填
  description: str = ""
  enabled: bool = True
  transport: "stdio" | "streamable_http" | "sse"  # 默认：stdio
  url: str = ""                # streamable_http / sse 必填
  headers: Dict[str, str] = {} # HTTP 请求头
  command: str = ""            # stdio 必填
  args: List[str] = []
  env: Dict[str, str] = {}
  tools: List[str] | None = None  # 工具白名单（None = 加载全部）
```

- `clients`：以客户端名称为键的字典，每个条目为一个 `MCPClientConfig`。
- `transport`：传输方式。`stdio` 用于本地进程；`streamable_http` / `sse` 用于远程 HTTP 网关。
- `url`：当 `transport` 为 `streamable_http` 或 `sse` 时必填。
- `command` / `args` / `env`：当 `transport` 为 `stdio` 时必填，用于启动本地 MCP 进程。
- `headers`：HTTP 请求头，用于携带鉴权信息（如 `Authorization: Bearer <token>`）。
- `tools`：工具白名单。为 `None` 时加载该客户端全部工具；为列表时仅加载指定工具。
- 平台默认接入 OpenGeoBot MCP Tool Gateway（`transport: sse`），所有物理动作技能通过已注册 Skill 经网关暴露。

MCP 客户端配置示例（通过 `PUT /api/agents/{agentId}` 写入 `AgentProfileConfig.mcp`）：

```json
{
  "mcp": {
    "clients": {
      "opengeobot-mcp-gateway": {
        "name": "OpenGeoBot MCP Tool Gateway",
        "description": "Platform MCP Tool Gateway for registered skills",
        "enabled": true,
        "transport": "sse",
        "url": "http://mcp-tool-gateway:8090/sse",
        "headers": {
          "Authorization": "Bearer <token>"
        }
      }
    }
  }
}
```

### Persona File Mechanism

QwenPaw 采用基于文件的人设（system prompt）机制，将系统提示拆分为多个可维护的人设文件：

- 人设文件（`AGENTS.md`、`SOUL.md`、`PROFILE.md`）存储在智能体的 `workspace_dir` 目录下。
- `system_prompt_files` 字段指定加载哪些人设文件，并按列表顺序组装为最终系统提示。
- `AGENTS.md`：智能体行为约束与安全规则。
- `SOUL.md`：智能体人格与角色定义。
- `PROFILE.md`：智能体身份档案。
- 默认人设文件列表为 `["AGENTS.md", "SOUL.md", "PROFILE.md"]`；可通过 `PUT /api/agents/{agentId}` 调整 `system_prompt_files` 增减或重排。
- 人设文件内容为不可信输入，最终系统提示与 Agent 输出仍须经过平台 Schema、权限、状态机、资源与安全校验，不得绕过 Safety Gateway。
