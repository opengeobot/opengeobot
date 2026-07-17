# Checklist

## 配置项
- [x] AgentConfig 新增 `qwenpaw_mcp_gateway_url` 字段，从环境变量 `QWENPAW_MCP_GATEWAY_URL` 读取
- [x] AgentConfig 新增 `qwenpaw_mcp_gateway_auth_token` 字段，从环境变量 `QWENPAW_MCP_GATEWAY_AUTH_TOKEN` 读取
- [x] AgentConfig 新增 `qwenpaw_persona_dir` 字段，从环境变量 `QWENPAW_PERSONA_DIR` 读取（缺省 None）
- [x] AgentConfig 新增 `qwenpaw_agent_approval_level` 字段，从环境变量 `QWENPAW_AGENT_APPROVAL_LEVEL` 读取（缺省 `STRICT`）

## Persona 模板
- [x] AGENTS.md 模板包含安全红线（不直接调用 /cmd_vel/关节/电机、不绕过 Safety Gateway、输出为不可信提案）
- [x] AGENTS.md 模板包含输出格式约束（PlanProposal JSON Schema：steps 数组、confidence、skill_id/params/description）
- [x] AGENTS.md 模板包含技能使用约束（只使用已注册 Skill/Capability）
- [x] SOUL.md 模板定义"一脑多控"智能体角色（身份、能力范围、多机协同、任务规划连续性）
- [x] PROFILE.md 模板包含身份档案（平台名称、版本、语言、关联技能列表占位）

## 初始化逻辑
- [x] POST 创建智能体后追加 PUT 写入完整 `AgentProfileConfig`
- [x] PUT 配置体包含 `system_prompt_files: ["AGENTS.md", "SOUL.md", "PROFILE.md"]`
- [x] PUT 配置体包含 `mcp.clients`（当 MCP gateway URL 已配置时）
- [x] PUT 配置体包含 `approval_level`（从配置读取，缺省 STRICT）
- [x] MCP 客户端配置的 transport 为 `sse` 或 `streamable_http`
- [x] MCP 客户端配置的 url 指向平台 MCP Tool Gateway 端点
- [x] MCP 客户端配置包含认证 headers（当 auth token 已配置时）
- [x] 已存在智能体且 mcp/skill 配置无变更时跳过 PUT
- [x] 已存在智能体且配置有变更时执行 PUT 更新
- [x] QwenPaw 管理 API 不可用时降级为无状态模式，不阻塞启动
- [x] 降级模式记录警告日志

## Provider system prompt
- [x] `_build_system_prompt()` 使用与 persona 一致的角色背景，而非硬编码通用文案
- [x] `_build_replan_system_prompt()` 同步使用角色背景
- [x] system prompt 包含安全红线声明（不直接调用 /cmd_vel、输出为不可信提案）
- [x] system prompt 包含输出格式约束（PlanProposal JSON Schema）
- [x] 无状态模式下 system prompt 仍包含角色背景

## main.py 启动流程
- [x] MCP gateway URL、persona 配置从 AgentConfig 传递给 AgentInitializer
- [x] 初始化失败时 provider 仍能获取 persona 内容用于无状态模式

## EXT-QWENPAW 契约文档
- [x] 补充 `AgentProfileConfig` 关键字段说明（system_prompt_files、mcp、approval_level）
- [x] 补充 MCPClientConfig 格式说明（transport、url、headers、tools）
- [x] 更新初始化流程说明（POST 创建 -> PUT 配置 persona + mcp）

## 单元测试
- [x] 测试 POST 创建后追加 PUT 写入完整配置
- [x] 测试 PUT 配置体包含正确的 system_prompt_files、mcp.clients、approval_level
- [x] 测试 MCP gateway URL 未配置时跳过 mcp 配置且记录警告
- [x] 测试已存在智能体且 mcp/skill 配置无变更时跳过 PUT
- [x] 测试已存在智能体且 mcp 配置变更时执行 PUT
- [x] 测试降级模式（QwenPaw 不可用）返回 False
- [x] 测试 provider system prompt 包含角色背景和安全红线
- [x] 全部现有测试仍通过（无回归，71 passed）
