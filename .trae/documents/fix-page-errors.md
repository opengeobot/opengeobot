# 修复三类页面报错问题

## 问题概览

| 问题 | 影响页面 | 错误 | 根因 |
|------|---------|------|------|
| 1 | 媒体库、调度编排、OTA升级、备份恢复 | Permission denied | 路由权限码与 Controller @PreAuthorize 不一致 |
| 2 | 系统管理-权限查看 | Resource not found | 前端调用 `/permissions/grouped` 端点不存在 |
| 3 | 系统管理-国际化资源、字典管理 | Internal server error | 前端 API URL 与后端 Controller 路径不匹配 |

## 问题 1: Permission Denied (4 个页面)

### 根因

路由 `meta.permission` 码与 Controller `@PreAuthorize` 码不一致：

| 页面 | 路由码（错误） | Controller 码（正确） |
|------|-------------|-------------------|
| 媒体库 | `media.media.read` | `media.asset.read` |
| 调度编排 | `fleet.fleet.read` | `fleet.schedule.read` |
| OTA升级 | `ota.ota.read` | `ops.ota.read` |
| 备份恢复 | `recovery.recovery.read` | `ops.backup.read` |

数据库中存的是路由码（`media.media.read` 等），但 Controller 校验的是 `media.asset.read` 等。路由守卫通过后，API 调用被 Controller 的 @PreAuthorize 拒绝（403）。

### 修复

**文件**: `apps/web-console/src/router/index.ts`

修改 4 个路由的 `permission` meta：
- media: `media.media.read` -> `media.asset.read`
- fleet: `fleet.fleet.read` -> `fleet.schedule.read`
- ota: `ota.ota.read` -> `ops.ota.read`
- recovery: `recovery.recovery.read` -> `ops.backup.read`

**数据库**: 更新 `sys_permission` 表，将旧的权限码替换为 Controller 使用的码，并更新 `sys_role_permission` 关联：

```sql
-- 插入 Controller 使用的权限码
INSERT INTO platform_iam.sys_permission (permission_code, permission_name, module, description) VALUES
('media.asset.read','View Media Assets','platform-robot','View media assets'),
('media.asset.manage','Manage Media Assets','platform-robot','Manage media assets'),
('fleet.schedule.read','View Fleet Schedules','platform-robot','View fleet schedules'),
('fleet.schedule.manage','Manage Fleet Schedules','platform-robot','Manage fleet schedules'),
('ops.ota.read','View OTA Packages','platform-robot','View OTA packages'),
('ops.ota.manage','Manage OTA Packages','platform-robot','Manage OTA packages'),
('ops.backup.read','View Backups','platform-robot','View backups'),
('ops.backup.manage','Manage Backups','platform-robot','Manage backups')
ON CONFLICT DO NOTHING;

-- 分配给 SYS_ADMIN
INSERT INTO platform_iam.sys_role_permission (role_id, permission_code)
SELECT 'rol_01J00000000000000000000001', permission_code FROM platform_iam.sys_permission
ON CONFLICT DO NOTHING;
```

重启 cloud-control 容器清除 PermissionCache。

## 问题 2: Resource Not Found (权限查看)

### 根因

前端 `permission.ts` 调用 `GET /api/v1/permissions/grouped`（第 15 行），该端点不存在。后端仅暴露 `GET /api/v1/permissions`（返回扁平 `Permission[]`）。同时 `getPermissionsByRole(roleId)` 用 `?role_id=` 查询参数（第 21 行），但后端用路径变量 `/permissions/roles/{roleId}`。

### 修复

**文件**: `apps/web-console/src/api/permission.ts`

1. `listPermissionsByModule()`: 改为调用 `GET /permissions`，在前端按 `module` 字段分组
2. `getPermissionsByRole(roleId)`: 改 URL 为 `/permissions/roles/${roleId}`

```typescript
export async function listPermissionsByModule(): Promise<PermissionGroup[]> {
  const response = await client.get<Permission[]>('/permissions')
  const grouped: Record<string, Permission[]> = {}
  for (const perm of response.data) {
    if (!grouped[perm.module]) grouped[perm.module] = []
    grouped[perm.module].push(perm)
  }
  return Object.entries(grouped).map(([module, permissions]) => ({ module, permissions }))
}

export async function getPermissionsByRole(roleId: string): Promise<Permission[]> {
  const response = await client.get<Permission[]>(`/permissions/roles/${roleId}`)
  return response.data
}
```

## 问题 3: Internal Server Error (国际化资源、字典管理)

### 根因

**I18n**: 前端 API URL 与后端不匹配：

| 操作 | 前端 URL（错误） | 后端 URL（正确） |
|------|---------------|---------------|
| 列表 | `GET /i18n/resources` | `GET /i18n` |
| 创建 | `POST /i18n/resources` | `POST /i18n` |
| 更新 | `PUT /i18n/resources/{id}` | `PUT /i18n/{resourceKey}?locale=xx` |
| 删除 | `DELETE /i18n/resources/{id}` | `DELETE /i18n/{resourceKey}?locale=xx` |

**Dict**: 前端使用数字 `id` 操作，但后端用 `typeCode`（字符串编码）。前端 `listDictItems` 期望 `DictItem[]`，后端返回 `PageResponse`。

### 修复

**文件**: `apps/web-console/src/api/i18n.ts`

修改所有 URL 去掉 `/resources` 后缀：
- `GET /i18n/resources` -> `GET /i18n`
- `POST /i18n/resources` -> `POST /i18n`
- `PUT /i18n/resources/{id}` -> `PUT /i18n/{resourceKey}`
- `DELETE /i18n/resources/{id}` -> `DELETE /i18n/{resourceKey}`
- `POST /i18n/resources/batch-import` -> `POST /i18n/batch`

**文件**: `apps/web-console/src/api/dict.ts`

修改 Dict 操作使用 `typeCode` 代替 `id`，修正 URL 路径：
- `PUT /dict/types/{id}` -> `PUT /dict/types/{typeCode}`
- `DELETE /dict/types/{id}` -> `DELETE /dict/types/{typeCode}`
- `POST /dict/types/{id}/publish` -> `POST /dict/types/{typeCode}/publish`
- `GET /dict/types/{typeId}/items` -> `GET /dict/types/{typeCode}/items`
- `POST /dict/items` -> `POST /dict/types/{typeCode}/items`
- `PUT /dict/items/{id}` -> `PUT /dict/{typeCode}/items/{itemCode}`
- `DELETE /dict/items/{id}` -> `DELETE /dict/{typeCode}/items/{itemCode}`

**文件**: `apps/web-console/src/views/system/DictManagement.vue`

修改视图代码，用 `row.type_code` 代替 `row.id` 作为操作标识符。修改 `listDictItems` 返回类型处理，从 PageResponse 中提取 `items` 数组。

## 验证步骤

1. 重启 cloud-control 后，用 curl 测试所有修复的 API 端点返回 200
2. 前端 Vite HMR 自动更新，硬刷新浏览器
3. 登录后点击每个之前报错的菜单项，确认页面正常加载
4. 特别验证：
   - 媒体库页面加载无 403
   - 权限查看页面显示分组权限列表
   - 国际化资源页面显示资源列表
   - 字典管理页面显示类型和项
