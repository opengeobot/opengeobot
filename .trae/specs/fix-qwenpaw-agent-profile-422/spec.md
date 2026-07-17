# QwenPaw 智能体配置 422 最小修复 Spec

## 背景

现有 `enhance-qwenpaw-agent-init` 已完成 QwenPaw 智能体初始化增强，但在服务重启后的运行时验证中暴露出新的兼容性问题：

- `agent-runtime` 启动时会调用 `PUT /api/agents/opengeobot-controller`
- 实际运行日志显示该请求返回 `422 Unprocessable Entity`
- 当前初始化器在 `PUT AgentProfileConfig` 请求体中发送了 `skill_names`
- 但 `GET /api/agents/{id}` 的返回中并不包含 `skill_names`
- 结合线上行为可以确认，当前运行中的 QwenPaw `PUT /api/agents/{id}` 并不接受 `skill_names` 作为 `AgentProfileConfig` 字段
- 现有漂移检测使用 `current.get("skill_names", [])` 参与比较，因为 GET 永远不返回该字段，启动时总会误判存在漂移并重复触发 PUT
- 重复 PUT 命中 422 后，`agent-runtime` 在重启场景下降级为无状态模式，破坏既有有状态智能体上下文

该问题属于已上线初始化链路的运行时兼容性修复，目标应限制为最小变更：仅让更新载荷与线上可接受的 `AgentProfileConfig` 契约对齐，并消除由错误漂移检测导致的无意义 PUT。

## 变更目标

- 将 QwenPaw 智能体更新载荷收敛到线上 `PUT /api/agents/{id}` 已接受的 `AgentProfileConfig` 字段
- 明确 `PUT` 更新不得再发送不受支持字段，尤其是 `skill_names`
- 将已存在智能体的漂移检测范围限制为 OpenGeoBot 明确管理、且 `GET /api/agents/{id}` 实际返回的字段
- 当上述受管字段已经对齐时，启动流程不得再次执行 PUT
- 保持首次创建后的有状态智能体能力不变，并确保重启后不会因为 422 退化到无状态模式

## 非目标

- 不调整 `POST /api/agents` 的创建字段集合
- 不扩展 persona、MCP、审批等级或模型选择能力
- 不引入新的环境变量、配置入口或额外降级策略
- 不尝试为 QwenPaw 推断未在运行时证据中确认的 `AgentProfileConfig` 新字段

## 影响范围

- 关联功能：`F-MISSION-001`
- 关联外部契约：`EXT-QWENPAW`
- 预期受影响代码：
  - `services/agent-runtime/src/opengeobot_agent/initializer.py`
  - `services/agent-runtime/tests/test_initializer.py`
  - 如需契约说明同步，更新 `contracts/external/EXT-QWENPAW.md`

## 新增需求

### 需求：PUT 更新载荷必须匹配线上接受的 AgentProfileConfig

对于已存在的 QwenPaw 智能体，OpenGeoBot 发往 `PUT /api/agents/{id}` 的请求体 SHALL 只包含线上运行时已接受的 `AgentProfileConfig` 管理字段，不得包含未被该接口接受的扩展字段。

当前最小受管字段集合 SHALL 为：

- `name`
- `description`
- `workspace_dir`
- `language`
- `system_prompt_files`
- `mcp.clients`
- `approval_level`
- `active_model`

#### 场景：已存在智能体时发送兼容 PUT
- **WHEN** `agent-runtime` 启动并发现 `opengeobot-controller` 已存在
- **THEN** 若需要更新，发送到 `PUT /api/agents/opengeobot-controller` 的请求体只包含最小受管字段集合
- **AND** 请求体 SHALL NOT 包含 `skill_names`

#### 场景：不再向 PUT 发送不受支持字段
- **WHEN** 初始化器构造 `AgentProfileConfig` 更新载荷
- **THEN** SHALL 排除所有未被线上 `PUT` 接受的字段
- **AND** 至少包括排除 `skill_names`

### 需求：漂移检测只比较 GET 可见且由 OpenGeoBot 管理的字段

对于已存在智能体，OpenGeoBot 的漂移检测 SHALL 只比较 `GET /api/agents/{id}` 响应中可观察、且 OpenGeoBot 明确负责维护的字段，不得基于 GET 不返回的字段推断漂移。

允许参与漂移检测的字段 SHALL 仅限：

- `name`
- `description`
- `workspace_dir`
- `language`
- `system_prompt_files`
- `mcp.clients`
- `approval_level`
- `active_model`

#### 场景：GET 不返回 skill_names 时不视为漂移
- **WHEN** `GET /api/agents/opengeobot-controller` 响应中缺少 `skill_names`
- **THEN** 漂移检测 SHALL 忽略 `skill_names`
- **AND** SHALL NOT 因该字段缺失而判定需要 PUT

#### 场景：已存在智能体且受管字段一致
- **WHEN** `GET /api/agents/opengeobot-controller` 返回的受管字段与 OpenGeoBot 期望值一致
- **THEN** 初始化器 SHALL 跳过 PUT 更新
- **AND** 启动日志中不得出现因伪漂移触发的更新尝试

### 需求：重启场景保持有状态启动成功

在 `opengeobot-controller` 已成功创建且受管字段已对齐的前提下，`agent-runtime` 重启后 SHALL 保持有状态模式启动成功，不得因错误更新请求退化为无状态模式。

#### 场景：重启后不再出现 422
- **WHEN** 服务完成一次成功初始化后再次重启 `agent-runtime`
- **THEN** 不再出现 `PUT /api/agents/opengeobot-controller` 返回 `422 Unprocessable Entity`
- **AND** `agent-runtime` 不再因该问题降级为无状态模式

#### 场景：重启后既有智能体继续存在
- **WHEN** `agent-runtime` 在已存在智能体的环境中重启
- **THEN** `opengeobot-controller` 仍可通过 `GET /api/agents/opengeobot-controller` 查询到
- **AND** 其受管字段保持不变

## 修改需求

### 需求：智能体初始化更新策略

原策略存在两个问题：

1. 更新载荷沿用了创建阶段字段，把 `skill_names` 一并发送到 `PUT`
2. 漂移检测把 `skill_names` 作为对比字段，即使 GET 永不返回该字段也会误判漂移

修改后策略如下：

1. `GET /api/agents/{id}` 用于确认智能体是否存在，并读取线上实际返回的受管字段
2. 若智能体不存在，继续沿用既有创建流程
3. 若智能体已存在，仅基于受管字段集合执行漂移检测
4. 仅在受管字段存在真实差异时，才发送兼容的 `PUT AgentProfileConfig`
5. `PUT` 请求体不得包含 `skill_names` 等未被线上接受的字段
6. 若受管字段已对齐，启动流程直接复用现有有状态智能体，不做额外 PUT

## 验证要求

### 场景：先复现当前缺陷
- **GIVEN** 已存在 `opengeobot-controller` 智能体，且当前代码仍会在 PUT 中发送 `skill_names`
- **WHEN** 重启 `agent-runtime`
- **THEN** 运行日志可复现 `PUT /api/agents/opengeobot-controller` 返回 `422 Unprocessable Entity`
- **AND** 可观察到服务退化为无状态模式的证据

### 场景：修复后消除 422
- **GIVEN** 已应用本最小修复
- **WHEN** 在同样环境下再次重启 `agent-runtime`
- **THEN** 不再出现该 422
- **AND** 若受管字段已对齐，则不再发送多余 PUT
- **AND** 服务保持有状态模式启动成功

### 场景：关键健康端点保持正常
- **WHEN** 完成修复并重启相关服务
- **THEN** `GET /api/agents/opengeobot-controller` 可正常返回
- **AND** QwenPaw 管理 API 相关健康检查保持正常
- **AND** `agent-runtime` 自身关键健康端点保持正常
