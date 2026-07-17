# Tasks

- [x] Task 1: 扩展 AgentConfig 配置项
  - [x] SubTask 1.1: 新增 `qwenpaw_mcp_gateway_url` 配置项（环境变量 `QWENPAW_MCP_GATEWAY_URL`）
  - [x] SubTask 1.2: 新增 `qwenpaw_mcp_gateway_auth_token` 配置项（环境变量 `QWENPAW_MCP_GATEWAY_AUTH_TOKEN`）
  - [x] SubTask 1.3: 新增 `qwenpaw_persona_dir` 配置项（环境变量 `QWENPAW_PERSONA_DIR`，缺省 None 表示使用内置模板）
  - [x] SubTask 1.4: 新增 `qwenpaw_agent_approval_level` 配置项（环境变量 `QWENPAW_AGENT_APPROVAL_LEVEL`，缺省 `STRICT`）

- [x] Task 2: 定义内置 persona 模板内容
  - [x] SubTask 2.1: 定义 AGENTS.md 内容：智能体行为约束（安全红线、输出格式 PlanProposal JSON Schema、技能使用约束、不可信提案声明）
  - [x] SubTask 2.2: 定义 SOUL.md 内容："一脑多控"智能体角色定义（身份、能力范围、多机协同职责、任务规划连续性）
  - [x] SubTask 2.3: 定义 PROFILE.md 内容：身份档案（平台名称、版本、语言、关联技能列表占位）

- [x] Task 3: 增强 AgentInitializer 初始化逻辑
  - [x] SubTask 3.1: 在 `initialize()` 方法中，POST 创建后追加 PUT 调用写入完整 `AgentProfileConfig`（system_prompt_files + mcp.clients + approval_level）
  - [x] SubTask 3.2: 实现 `_build_agent_profile_config()` 方法，构造包含 persona、mcp、skill_names 的完整配置体
  - [x] SubTask 3.3: 实现 `_build_mcp_clients()` 方法，根据 `qwenpaw_mcp_gateway_url` 构造 MCP 客户端配置（transport=sse，url，headers）
  - [x] SubTask 3.4: 更新 `_update_agent()` 方法，比较 mcp 配置和 skill_names 变更，有变更时才 PUT
  - [x] SubTask 3.5: persona 文件通过 system_prompt_files 配置注册，由 QwenPaw 在工作目录加载

- [x] Task 4: 更新 QwenPawProvider system prompt
  - [x] SubTask 4.1: 将 `_build_system_prompt()` 中的硬编码通用文案替换为与 persona 一致的角色背景（从配置或内置模板读取）
  - [x] SubTask 4.2: 将 `_build_replan_system_prompt()` 中的硬编码文案同步更新
  - [x] SubTask 4.3: 确保 system prompt 包含安全红线（不直接调用 /cmd_vel、输出为不可信提案、只使用已注册 Skill）

- [x] Task 5: 更新 main.py 启动流程
  - [x] SubTask 5.1: 将新增的 MCP gateway URL、persona 配置从 AgentConfig 传递给 AgentInitializer（通过 AgentConfig 自动传递）
  - [x] SubTask 5.2: 确保初始化失败时 provider 仍能获取 persona 内容用于无状态模式（provider 直接导入 persona 模块）

- [x] Task 6: 更新 EXT-QWENPAW 契约文档
  - [x] SubTask 6.1: 补充 `AgentProfileConfig` 的关键字段说明（system_prompt_files、mcp、approval_level）
  - [x] SubTask 6.2: 补充 MCP 客户端配置格式（MCPClientConfig 字段：transport、url、headers、tools）
  - [x] SubTask 6.3: 更新初始化流程说明（POST 创建 -> PUT 配置 persona + mcp）

- [x] Task 7: 更新和新增单元测试
  - [x] SubTask 7.1: 测试 POST 创建后追加 PUT 写入完整配置
  - [x] SubTask 7.2: 测试 PUT 配置体包含正确的 system_prompt_files、mcp.clients、approval_level
  - [x] SubTask 7.3: 测试 MCP gateway URL 未配置时跳过 mcp 配置
  - [x] SubTask 7.4: 测试已存在智能体且 mcp/skill 配置无变更时跳过 PUT
  - [x] SubTask 7.5: 测试已存在智能体且 mcp 配置变更时执行 PUT
  - [x] SubTask 7.6: 测试降级模式（QwenPaw 不可用）仍返回 False
  - [x] SubTask 7.7: 测试 provider system prompt 包含角色背景和安全红线

# Task Dependencies
- [Task 3] depends on [Task 1] 和 [Task 2]
- [Task 4] depends on [Task 2]
- [Task 5] depends on [Task 1] 和 [Task 3]
- [Task 6] 无依赖，可与 Task 1-5 并行
- [Task 7] depends on [Task 3] 和 [Task 4]
