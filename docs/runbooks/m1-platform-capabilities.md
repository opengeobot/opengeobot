<!--
Function: M1 Platform Capabilities Runbook — authentication, RBAC, dictionary/i18n, config/audit/idempotency operations
Time: 2026-07-05
Author: AxeXie
-->

# M1 平台公共能力 Runbook

本 Runbook 覆盖 M1 阶段（F-PLATFORM-001 ~ F-PLATFORM-004）的平台公共能力运维：认证与会话、用户组织角色权限、数据字典与国际化、配置审计幂等与导出。

## 1. 启动

```bash
# 1. 启动基础设施
./scripts/dev.sh infra-up

# 2. 执行数据库迁移（V1-V6）
./scripts/dev.sh migrate

# 3. 启动云控制面与前端
./scripts/dev.sh dev
```

健康验证：

```bash
curl http://localhost:8080/health/live    # 应返回 200 HEALTHY
curl http://localhost:5173                  # 前端可访问
```

## 2. 登录与会话 (F-PLATFORM-001)

### 2.1 管理员登录

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

响应包含 `access_token`（JWT，HS512 签名，30 分钟过期）、`refresh_token`（`rt_` 前缀）。

### 2.2 验证当前会话

```bash
TOKEN="<access_token>"
curl http://localhost:8080/api/v1/profile \
  -H "Authorization: Bearer $TOKEN"
```

### 2.3 安全说明

- 密码使用 BCrypt 哈希存储，永不返回。
- JWT 包含权限码（非角色名），`@PreAuthorize` 方法级校验。
- 登录动作写入审计日志（含 trace_id、source_ip）。
- 被禁用用户无法登录。

## 3. 用户组织角色权限 (F-PLATFORM-002)

### 3.1 用户管理

```bash
# 创建用户
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","display_name":"New User","email":"new@example.com","password":"Pass@123"}'

# 禁用用户
curl -X PATCH http://localhost:8080/api/v1/users/{userId}/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"DISABLED","reason":"offboarding"}'
```

### 3.2 角色与权限分配

```bash
# 创建角色
curl -X POST http://localhost:8080/api/v1/roles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"role_name":"Operator","role_code":"operator","description":"Robot operator"}'

# 给用户分配角色（全量替换）
curl -X PUT http://localhost:8080/api/v1/users/{userId}/roles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"role_ids":["rol_xxx"]}'
```

### 3.3 权限码

权限码是代码契约，不是可编辑数据字典。主要权限码：

| 权限码 | 说明 |
|--------|------|
| platform.user.read | 查看用户 |
| platform.user.manage | 创建/修改/禁用用户 |
| platform.role.read | 查看角色 |
| platform.role.manage | 创建/修改角色 |
| platform.permission.read | 查看权限 |
| platform.org.manage | 管理组织 |

## 4. 数据字典与国际化 (F-PLATFORM-003)

### 4.1 查询字典

```bash
# 字典类型列表
curl http://localhost:8080/api/v1/dict/types \
  -H "Authorization: Bearer $TOKEN"

# 字典项（含中英文标签）
curl http://localhost:8080/api/v1/dict/types/user_status/items \
  -H "Authorization: Bearer $TOKEN"
```

预置字典：`user_status`、`robot_status`、`mission_status`，均含 `label_zh_cn` 和 `label_en_us`。

### 4.2 i18n 资源

```bash
# 创建 i18n 资源
curl -X POST http://localhost:8080/api/v1/i18n \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"resource_key":"common.confirm","locale":"zh-CN","resource_value":"确认","module":"platform"}'
```

支持 `zh-CN` 和 `en-US` 两种语言环境。

## 5. 配置审计幂等与导出 (F-PLATFORM-004)

### 5.1 平台配置

```bash
curl http://localhost:8080/api/v1/configs \
  -H "Authorization: Bearer $TOKEN"
```

### 5.2 审计日志

```bash
curl "http://localhost:8080/api/v1/audits?page=1&pageSize=20" \
  -H "Authorization: Bearer $TOKEN"
```

审计记录包含：`action`、`actor_id`、`resource_type`、`resource_id`、`result`、`trace_id`、`reason_code`。

### 5.3 trace_id 串联

所有用户/角色/配置变更操作均写入审计日志，每条记录携带 `trace_id`，可串联完整调用链（满足 C19 验收）。

## 6. 故障排查

### 6.1 登录失败

- 检查用户状态是否为 ACTIVE（非 DISABLED/LOCKED）。
- 检查密码是否正确（默认管理员：admin / admin123）。
- 查看 `/tmp/opengeobot-app.log` 中的认证异常。

### 6.2 403 Forbidden

- JWT 中权限码不包含所需权限。
- 检查角色是否已分配正确权限码（`GET /api/v1/roles/{roleId}/permissions`）。

### 6.3 数据库连接失败

- 确认 postgres 容器健康：`docker compose -f deploy/compose/compose.yml ps postgres`
- 确认 DB_PASSWORD 环境变量正确（默认 opengeobot_dev）。

## 7. 相关文档

- [AI 开发约束与平台公共能力规范](../AI开发约束与平台公共能力规范%20V1.0.md)
- [平台功能与数据状态统一实施蓝图](../平台功能与数据状态统一实施蓝图%20V1.0.md)
- [M0 工程基线 Runbook](m0-engineering-base.md)
