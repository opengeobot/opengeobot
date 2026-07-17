# Checklist

## 修复范围

- [x] 修复保持为最小范围，仅调整 QwenPaw 已存在智能体的 PUT 更新载荷与漂移检测
- [x] 不新增与本次 422 修复无关的能力、配置项或初始化流程扩展
- [x] 不猜测未被运行时证据确认的 QwenPaw PUT 字段

## PUT 载荷

- [x] `PUT /api/agents/{id}` 请求体只包含 OpenGeoBot 受管且线上已接受的字段
- [x] `PUT /api/agents/{id}` 请求体不包含 `skill_names`
- [x] `PUT /api/agents/{id}` 请求体仍可覆盖 `name`
- [x] `PUT /api/agents/{id}` 请求体仍可覆盖 `description`
- [x] `PUT /api/agents/{id}` 请求体仍可覆盖 `workspace_dir`
- [x] `PUT /api/agents/{id}` 请求体仍可覆盖 `language`
- [x] `PUT /api/agents/{id}` 请求体仍可覆盖 `system_prompt_files`
- [x] `PUT /api/agents/{id}` 请求体仍可覆盖 `mcp.clients`
- [x] `PUT /api/agents/{id}` 请求体仍可覆盖 `approval_level`
- [x] `PUT /api/agents/{id}` 请求体仍可覆盖 `active_model`

## 漂移检测

- [x] 已存在智能体的漂移检测只比较 `GET /api/agents/{id}` 返回且由 OpenGeoBot 管理的字段
- [x] 漂移检测字段集合仅包括 `name`、`description`、`workspace_dir`、`language`、`system_prompt_files`、`mcp.clients`、`approval_level`、`active_model`
- [x] `GET /api/agents/{id}` 不返回 `skill_names` 时，不会触发伪漂移
- [x] 已存在智能体且受管字段已对齐时，不会再触发多余 PUT
- [x] 已存在智能体且受管字段真实变化时，仍会触发 PUT

## 重启回归

- [x] 修复前可稳定复现重启后 `PUT /api/agents/opengeobot-controller` 返回 422
- [ ] 修复后再次重启，不再出现该 422
- [ ] 重启后不再因为该问题降级为无状态模式
- [x] `opengeobot-controller` 在重启后仍然存在
- [ ] 已对齐场景下启动保持有状态模式成功

## 健康检查

- [x] `GET /api/agents/opengeobot-controller` 可正常返回
- [x] QwenPaw 管理 API 关键健康端点保持正常
- [ ] `agent-runtime` 关键健康端点保持正常
