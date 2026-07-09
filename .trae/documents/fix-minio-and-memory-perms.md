# 修复 MinIO 健康状态和任务记忆权限问题

## 问题 1: MinIO 显示 UNHEALTHY + i18n 状态文案

### 根因 A: Bucket 不存在
`OpsService.checkMinio()` 使用 `bucketExists("opengeobot")` 检查健康状态。该 bucket 从未被创建（只在首次媒体上传时通过 `MediaService.ensureBucketExists()` 懒创建）。

运行时证据：`minio: UNHEALTHY - MinIO bucket 'opengeobot' does not exist`

### 根因 B: i18n key 大小写不匹配
- 后端发送大写状态值：`HEALTHY`、`UNHEALTHY`、`DEGRADED`
- `StatusTag.vue` 查找 key：`status.health.HEALTHY`（大写）
- locale 文件用的是小写：`status.health.healthy`、`status.health.degraded`、`status.health.down`
- 且缺少 `unhealthy` key（用了 `down` 代替）
- 结果 `te()` 返回 false，回退显示后端原始字符串 "UNHEALTHY"

### 修复

**A. 创建 MinIO bucket**:
```bash
docker exec opengeobot-minio-1 mc mb /opengeobot
```

**B. 修复 i18n health 状态 key**:

文件 `apps/web-console/src/i18n/locales/zh-CN/platform.json`，将 `status.health` 段改为：
```json
"health": {
  "HEALTHY": "健康",
  "UNHEALTHY": "不健康",
  "DEGRADED": "降级"
}
```

文件 `apps/web-console/src/i18n/locales/en-US/platform.json`，同步修改：
```json
"health": {
  "HEALTHY": "Healthy",
  "UNHEALTHY": "Unhealthy",
  "DEGRADED": "Degraded"
}
```

## 问题 2: 任务记忆页面 Permission denied

### 根因
页面 `onMounted` 时并行调用两个 API：
- `GET /api/v1/memory/cases` -> 需要 `memory.memory.read` -> admin **有**此权限 -> 200 ✅
- `GET /api/v1/memory/suggestions` -> 需要 `memory.failure_case.read` -> admin **无**此权限 -> 403 ❌

数据库只有 `memory.memory.read` 和 `memory.memory.manage`，缺少 `memory.failure_case.read` 和 `memory.improvement.manage`。

运行时证据：`GET /api/v1/memory/suggestions` 返回 403 `"Permission denied"`

### 修复

向数据库插入缺失的权限码并分配给 SYS_ADMIN：
```sql
INSERT INTO platform_iam.sys_permission (permission_code, permission_name, module, description) VALUES
('memory.failure_case.read', 'View Failure Cases', 'platform-robot', 'View failure cases'),
('memory.failure_case.manage', 'Manage Failure Cases', 'platform-robot', 'Manage failure cases'),
('memory.improvement.read', 'View Improvement Suggestions', 'platform-robot', 'View improvement suggestions'),
('memory.improvement.manage', 'Manage Improvement Suggestions', 'platform-robot', 'Submit improvement feedback')
ON CONFLICT DO NOTHING;

INSERT INTO platform_iam.sys_role_permission (role_id, permission_code)
SELECT 'rol_01J00000000000000000000001', permission_code FROM platform_iam.sys_permission
ON CONFLICT DO NOTHING;
```

重启 cloud-control 清除 PermissionCache。

## 验证步骤
1. `curl /api/v1/ops/health` -> minio 应为 HEALTHY
2. `curl /api/v1/memory/suggestions` -> 应为 200
3. 前端切换中/英文，运维看板状态应显示"健康"/"不健康"（中文）和 "Healthy"/"Unhealthy"（英文）
4. 任务记忆页面不再显示 Permission denied
