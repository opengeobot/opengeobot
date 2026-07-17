# Tasks

- [x] Task 1: 复现重启后的 422 问题
  - [x] SubTask 1.1: 在已存在 `opengeobot-controller` 智能体的环境中重启 `agent-runtime`
  - [x] SubTask 1.2: 采集运行日志，确认存在 `PUT /api/agents/opengeobot-controller` 返回 `422 Unprocessable Entity`
  - [x] SubTask 1.3: 记录当前退化为无状态模式的运行时证据，作为修复前基线

- [x] Task 2: 收敛 PUT AgentProfileConfig 更新载荷
  - [x] SubTask 2.1: 梳理 `PUT /api/agents/{id}` 当前实际发送字段
  - [x] SubTask 2.2: 将更新载荷限制为 OpenGeoBot 受管且线上已接受的字段：`name`、`description`、`workspace_dir`、`language`、`system_prompt_files`、`mcp.clients`、`approval_level`、`active_model`
  - [x] SubTask 2.3: 明确移除 `skill_names` 等不受支持字段，确保其不再出现在 PUT 请求体中

- [x] Task 3: 修正已存在智能体的漂移检测逻辑
  - [x] SubTask 3.1: 将漂移检测字段集合限制为 `GET /api/agents/{id}` 可返回且由 OpenGeoBot 管理的字段
  - [x] SubTask 3.2: 删除基于 `current.get("skill_names", [])` 的漂移判定
  - [x] SubTask 3.3: 确保当受管字段已对齐时跳过 PUT，不再产生伪更新

- [x] Task 4: 补充针对最小修复的自动化测试
  - [x] SubTask 4.1: 新增测试覆盖 PUT 请求体不包含 `skill_names`
  - [x] SubTask 4.2: 新增测试覆盖 GET 缺少 `skill_names` 时不会触发伪漂移
  - [x] SubTask 4.3: 新增测试覆盖受管字段一致时初始化器跳过 PUT
  - [x] SubTask 4.4: 保留测试覆盖受管字段真实变化时仍会执行 PUT

- [ ] Task 5: 进行运行时回归验证
  - [x] SubTask 5.1: 在修复后再次重启 `agent-runtime`
  - [ ] SubTask 5.2: 验证不再出现 `PUT /api/agents/opengeobot-controller` 的 422 日志
  - [ ] SubTask 5.3: 验证服务不再降级为无状态模式
  - [x] SubTask 5.4: 验证 `opengeobot-controller` 在重启后仍然存在
  - [ ] SubTask 5.5: 验证相关关键健康端点保持正常

- [ ] Task 6: 诊断并收敛剩余的 QwenPaw PUT 契约差异
  - [ ] SubTask 6.1: 采集 `PUT /api/agents/{id}` 返回 422 时的完整响应体与请求体摘要
  - [ ] SubTask 6.2: 基于运行中的 QwenPaw 实际返回 schema，识别仍不被接受的字段或字段值
  - [ ] SubTask 6.3: 仅对剩余不兼容字段做最小修复，不扩大初始化能力范围
  - [ ] SubTask 6.4: 重新执行自动化测试与运行时重启回归

# Task Dependencies

- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 1]
- [Task 4] depends on [Task 2] 和 [Task 3]
- [Task 5] depends on [Task 2]、[Task 3] 和 [Task 4]
- [Task 6] depends on [Task 5]
