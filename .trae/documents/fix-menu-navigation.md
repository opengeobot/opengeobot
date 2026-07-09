# 修复菜单导航无响应问题

## 问题现象

用户登录后，点击左侧菜单项（如机器人管理、任务管理等）页面没有反应，URL 不变。点击右上角"用户中心"也无响应。

## 根因分析

### 已排除的原因

1. **后端 API 正常**：`/profile` 端点返回 57 个权限，包含路由守卫所需的全部 24 个权限码（`robot.robot.read`、`skill.skill.read` 等）
2. **路由权限码已修复**：`router/index.ts` 中的权限码已与 Controller `@PreAuthorize` 一致
3. **视图组件文件齐全**：所有 26 个 `.vue` 视图文件均存在
4. **Pinia 初始化顺序正确**：`createPinia()` 在 `app.use(router)` 之前调用

### 确认的根因

**Vite 开发服务器模块缓存陈旧**。Vite 日志显示 `2:16:01 AM [vite] (client) page reload src/router/index.ts`，表明检测到了路由文件变更并触发了页面重载。但：

1. Vite 的依赖优化缓存（`node_modules/.vite/`）可能仍提供旧的 router 模块代码（使用 `platform.robot.read` 等旧权限码）
2. 浏览器可能从 HTTP 缓存中加载了旧的 JS 模块
3. 旧的 localStorage 中存储了不含 `permissions` 字段的 user 对象

这三个因素叠加导致：即使用户重新登录获取了正确的 permissions，Vite 提供的旧路由代码仍使用旧权限码进行校验，导致匹配失败，路由守卫静默重定向到 `/dashboard`。

### 次要问题

`UserProfile` 前端类型与后端响应字段名不匹配：
- 前端 `id` vs 后端 `user_id`（不影响权限，但是真实 bug）
- 前端缺少 `status` 字段（不影响功能）

## 修复方案

### 步骤 1: 重启 Vite 开发服务器（清除缓存）

- 停止当前 Vite dev server（terminal 4）
- 删除 `apps/web-console/node_modules/.vite/` 缓存目录
- 重新启动 Vite dev server
- 这确保浏览器获取最新的 router/index.ts 代码

### 步骤 2: 修复 UserProfile 类型字段名不匹配

**文件**: `apps/web-console/src/types/api.ts`

将 `UserProfile` 接口的 `id` 字段改为 `user_id`，并添加 `status` 字段，与后端 `UserProfileResponse` 的 JSON 序列化结果一致：

```typescript
export interface UserProfile {
  user_id: string        // 原: id
  username: string
  display_name: string
  email: string | null   // 后端可能为 null
  phone: string | null
  avatar: string | null
  status: string          // 新增
  permissions: string[]
}
```

同时检查 `ProfileView.vue` 中对 `user.id` 的引用，改为 `user.user_id`。

### 步骤 3: 清除浏览器 localStorage

在登录页面添加自动清除旧数据的逻辑。当用户访问 `/login` 时，清除可能过期的 localStorage user 数据：

**文件**: `apps/web-console/src/views/LoginView.vue`

在 `handleSubmit` 函数开头，在调用 `authStore.login()` 之前，调用 `authStore.clearAuth()` 清除旧的 localStorage 数据，确保登录时从全新状态开始。

### 步骤 4: 增强路由守卫可观测性

**文件**: `apps/web-console/src/router/index.ts`

在路由守卫的权限拒绝分支添加 `console.warn`，便于调试：

```typescript
if (to.meta.permission && authStore.user) {
  const required = to.meta.permission
  if (!authStore.permissions.includes(required)) {
    console.warn(`[Router] Permission denied: ${required} not in [${authStore.permissions.slice(0, 5).join(', ')}...]")
    next('/dashboard')
    return
  }
}
```

## 验证步骤

1. 重启 Vite 后，打开浏览器开发者工具 Console
2. 访问 `http://localhost:5173/`，应看到登录页
3. 登录（admin/admin123）
4. 点击左侧菜单项（如"机器人管理"），URL 应变为 `/robots`
5. 检查 Console 中无 `Permission denied` 警告
6. 点击"用户中心"，应跳转到 `/profile`
7. 验证所有菜单项均可正常导航
