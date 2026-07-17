# Debug Session: qwenpaw-init-chain

## Status
- [OPEN] 已启动调试，会先做运行时证据采集，再决定最小修复。

## Symptoms
- `agent-runtime` 在初始化 QwenPaw 一脑多控智能体时失败。
- 已观测到 `PUT /api/agents/opengeobot-controller` 返回 `422 Unprocessable Entity`。
- 用户要求继续检查并验证：角色、技能、MCP 配置完整，且与一脑多控平台联通，并验证通过 QwenPaw 规划任务下发到端侧 ROSClaw 执行的全链路。

## Hypotheses
1. `PUT /api/agents/{id}` 仍携带了 QwenPaw 运行时不接受的字段或字段值，导致 `422`。
2. `agent-runtime` 与运行中的 QwenPaw 契约存在版本漂移，字段名或嵌套结构与源码假设不一致。
3. MCP/技能/角色配置在创建与更新阶段采用了不同 schema，导致“创建成功、更新失败”。
4. 一脑多控平台到 QwenPaw 的联通已基本正常，但 `agent-runtime` 启动时的幂等更新策略触发了错误分支。
5. 即使初始化成功，任务规划到 ROSClaw 的全链路仍可能缺少消息流、权限或端侧执行条件，需单独验证。

## Evidence Log
- 已确认：`agent-runtime` 运行日志显示 `QWENPAW_MCP_GATEWAY_URL not configured`，随后对 `PUT /api/agents/opengeobot-controller` 发起更新。
- 已确认：QwenPaw 运行时 `PUT /api/agents/{id}` 至少要求请求体包含 `id` 和 `name`；缺少 `id` 时稳定返回 `422`，响应体含 `Field required`。
- 已确认：当前源码已移除 `skill_names`，但先前仍缺少 `id`，这是 422 的直接根因。
- 已确认：`GET /api/agents/opengeobot-controller` 可成功返回，且 persona 文件 `AGENTS.md`、`SOUL.md`、`PROFILE.md` 已存在于 workspace。
- 已确认：当前 compose 栈未为 `agent-runtime` 注入 `QWENPAW_MCP_GATEWAY_URL`，因此初始化后的 `mcp.clients` 被收敛为 `{}`。
- 待采集：ROSClaw 端侧链路日志与端到端任务执行证据。

## Hypothesis Status
| ID | 假设 | 状态 | 证据摘要 |
|----|------|------|----------|
| A | `PUT` 仍含不兼容字段或字段值导致 422 | 部分确认 | `skill_names` 已被移除，但真正稳定触发 422 的直接原因是请求体缺少必填 `id` |
| B | QwenPaw 运行契约与源码假设不一致 | 已确认 | 运行时 `PUT` 不是部分更新，至少要求 `id` 和 `name` |
| C | MCP/技能/角色配置在创建与更新阶段 schema 不一致 | 已确认 | `POST` 可接受 `skill_names`，`PUT` 不接受部分更新风格，且 compose 未注入 MCP 网关 URL |
| D | 幂等更新策略误触发失败分支 | 已确认 | 缺少 `QWENPAW_MCP_GATEWAY_URL` 导致期望配置与现存配置不一致，从而触发更新 |
| E | 规划到 ROSClaw 全链路仍有独立阻塞点 | 待验证 | 正在切换到 `rosclaw-sim` 栈并准备执行闭环验证 |

## Next Step
- 继续执行 `rosclaw-sim` 运行栈验证，确认 QwenPaw 规划结果是否能经云边链路下发到 ROSClaw Bridge 并返回执行结果。
