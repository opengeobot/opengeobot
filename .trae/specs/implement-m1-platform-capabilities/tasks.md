# Tasks

## Task 1: F-PLATFORM-001 契约和迁移
- [x] SubTask 1.1: OpenAPI 契约 — 认证端点（POST /api/v1/auth/login、POST /api/v1/auth/refresh、POST /api/v1/auth/logout、GET /api/v1/profile、PUT /api/v1/profile）
- [x] SubTask 1.2: AsyncAPI 契约 — user.logged_in.v1、session.refreshed.v1、session.revoked.v1 事件
- [x] SubTask 1.3: Flyway V3 迁移 — sys_user、sys_refresh_token、sys_session 表和索引
- [x] SubTask 1.4: SM-IAM-001 会话状态机定义（ACTIVE/REFRESHING/EXPIRED/REVOKED）

## Task 2: F-PLATFORM-001 后端实现
- [x] SubTask 2.1: 领域模型 — User、Session、RefreshToken 实体和值对象
- [x] SubTask 2.2: 仓储层 — UserRepository、SessionRepository、RefreshTokenRepository（MyBatis-Plus）
- [x] SubTask 2.3: 应用服务 — AuthService（login、refresh、logout）、ProfileService（get、update）
- [x] SubTask 2.4: 密码哈希 — BCrypt 密码编码器
- [x] SubTask 2.5: JWT 生成与验证 — JwtTokenProvider（access_token、refresh_token）
- [x] SubTask 2.6: REST 控制器 — AuthController、ProfileController
- [x] SubTask 2.7: Spring Security 配置 — JWT 过滤器、权限码校验、公开端点配置
- [x] SubTask 2.8: 事件发布 — Outbox 事件写入（user.logged_in.v1 等）
- [x] SubTask 2.9: 审计记录 — 登录/登出/资料修改审计

## Task 3: F-PLATFORM-001 前端实现
- [x] SubTask 3.1: 登录页功能实现 — 表单校验、API 调用、Token 存储、错误处理
- [x] SubTask 3.2: 个人资料页 — 资料查看和修改
- [x] SubTask 3.3: 路由守卫 — 未登录跳转、Token 过期处理
- [x] SubTask 3.4: API 拦截器完善 — Token 自动注入、401 自动刷新或跳转
- [x] SubTask 3.5: 退出登录 — 清除 Token、跳转登录页

## Task 4: F-PLATFORM-001 测试和验证
- [x] SubTask 4.1: Java 单元测试 — AuthService、ProfileService、JwtTokenProvider
- [x] SubTask 4.2: Java 集成测试 — AuthController 端到端（登录/刷新/登出/资料）
- [x] SubTask 4.3: 前端组件测试 — 登录表单、路由守卫
- [x] SubTask 4.4: 安全测试 — 密码不可逆、Token 过期、无效凭据不泄露信息

## Task 5: F-PLATFORM-002 契约和迁移
- [x] SubTask 5.1: OpenAPI 契约 — 用户/组织/角色/权限 CRUD 端点
- [x] SubTask 5.2: AsyncAPI 契约 — user.created.v1、user.status_changed.v1、user.roles_changed.v1、role.changed.v1、authorization.changed.v1 事件
- [x] SubTask 5.3: Flyway V4 迁移 — sys_org、sys_user_org、sys_role、sys_permission、sys_user_role、sys_role_permission 表和索引
- [x] SubTask 5.4: 种子数据 — 默认管理员用户、默认组织、基础角色和权限码

## Task 6: F-PLATFORM-002 后端实现
- [x] SubTask 6.1: 领域模型 — Org、Role、Permission 实体和值对象
- [x] SubTask 6.2: 仓储层 — OrgRepository、RoleRepository、PermissionRepository、UserRoleRepository
- [x] SubTask 6.3: 应用服务 — UserService、OrgService、RoleService、PermissionService
- [x] SubTask 6.4: 数据范围 — DataScopeFilter 基于组织层级查询过滤
- [x] SubTask 6.5: 授权缓存 — PermissionCache 基于角色的权限码缓存，支持事件驱动失效
- [x] SubTask 6.6: REST 控制器 — UserController、OrgController、RoleController、PermissionController
- [x] SubTask 6.7: 事件发布 — 用户/角色变更 Outbox 事件
- [x] SubTask 6.8: 审计记录 — 用户/角色/权限变更审计

## Task 7: F-PLATFORM-002 前端实现
- [x] SubTask 7.1: 用户管理页 — 列表/搜索/创建/编辑/禁用/分配角色
- [x] SubTask 7.2: 组织管理页 — 树形展示/创建/编辑/绑定用户
- [x] SubTask 7.3: 角色管理页 — 列表/创建/编辑/分配权限
- [x] SubTask 7.4: 权限查看页 — 权限码列表/按角色过滤
- [x] SubTask 7.5: 前端权限指令 — v-permission 指令控制按钮/菜单可见性

## Task 8: F-PLATFORM-002 测试和验证
- [x] SubTask 8.1: Java 单元测试 — UserService、RoleService、PermissionCache、DataScopeFilter
- [x] SubTask 8.2: Java 集成测试 — 用户/组织/角色 CRUD 端到端
- [x] SubTask 8.3: 权限测试 — 角色变更实时生效、数据范围过滤
- [x] SubTask 8.4: 前端组件测试 — 用户管理表单、角色分配

## Task 9: F-PLATFORM-003 契约和迁移
- [x] SubTask 9.1: OpenAPI 契约 — 字典类型/项、i18n 资源 CRUD 端点
- [x] SubTask 9.2: AsyncAPI 契约 — platform.dictionary.changed.v1、platform.i18n.changed.v1 事件
- [x] SubTask 9.3: Flyway V5 迁移 — sys_dict_type、sys_dict_item、sys_i18n_resource 表和索引
- [x] SubTask 9.4: 种子数据 — 基础字典（状态、类型、枚举）和 i18n 资源

## Task 10: F-PLATFORM-003 后端实现
- [x] SubTask 10.1: 领域模型 — DictType、DictItem、I18nResource 实体
- [x] SubTask 10.2: 仓储层 — DictRepository、I18nRepository
- [x] SubTask 10.3: 应用服务 — DictService（类型/项 CRUD、版本发布）、I18nService（资源 CRUD、批量导入）
- [x] SubTask 10.4: REST 控制器 — DictController、I18nController
- [x] SubTask 10.5: 缓存 — 字典/i18n 内存缓存，事件驱动失效
- [x] SubTask 10.6: 事件发布 — 字典/i18n 变更 Outbox 事件

## Task 11: F-PLATFORM-003 前端实现
- [x] SubTask 11.1: 字典管理页 — 类型列表、项管理、版本发布
- [x] SubTask 11.2: i18n 资源管理页 — key 列表、多语言编辑、批量导入
- [x] SubTask 11.3: 前端 i18n 集成 — 从后端加载 i18n 资源，缓存更新

## Task 12: F-PLATFORM-003 测试和验证
- [x] SubTask 12.1: Java 单元测试 — DictService、I18nService
- [x] SubTask 12.2: Java 集成测试 — 字典/i18n CRUD 端到端
- [x] SubTask 12.3: C02 验证 — 同一业务代码 zh-CN/en-US 双语显示正确

## Task 13: F-PLATFORM-004 契约和迁移
- [x] SubTask 13.1: OpenAPI 契约 — 配置、审计、导出端点
- [x] SubTask 13.2: AsyncAPI 契约 — platform.config.changed.v1、export.requested.v1、export.completed.v1 事件
- [x] SubTask 13.3: Flyway V6 迁移 — sys_config、export_operation 表和索引

## Task 14: F-PLATFORM-004 后端实现
- [x] SubTask 14.1: 领域模型 — Config、ExportOperation 实体
- [x] SubTask 14.2: 仓储层 — ConfigRepository、ExportOperationRepository
- [x] SubTask 14.3: 应用服务 — ConfigService（类型化配置、版本管理）、AuditService（追加式审计查询）、IdempotencyService（幂等键管理）、ExportService（异步导出任务）
- [x] SubTask 14.4: REST 控制器 — ConfigController、AuditController、ExportController
- [x] SubTask 14.5: 幂等拦截器 — IdempotencyFilter 基于 idempotency_key 的请求去重
- [x] SubTask 14.6: 导出异步处理 — 基于线程池或 NATS 的异步导出任务
- [x] SubTask 14.7: 事件发布 — 配置变更/导出请求/完成 Outbox 事件

## Task 15: F-PLATFORM-004 前端实现
- [x] SubTask 15.1: 配置管理页 — 配置列表/编辑/版本历史
- [x] SubTask 15.2: 审计日志页 — 列表/过滤/详情查看
- [x] SubTask 15.3: 导出功能 — 导出按钮/任务状态轮询/下载

## Task 16: F-PLATFORM-004 测试和验证
- [x] SubTask 16.1: Java 单元测试 — ConfigService、AuditService、IdempotencyService、ExportService
- [x] SubTask 16.2: Java 集成测试 — 配置/审计/导出端到端
- [x] SubTask 16.3: 幂等测试 — 重复请求返回首次结果
- [x] SubTask 16.4: 审计测试 — 所有操作有审计记录，trace_id 串联

## Task 17: 前端公共框架完善
- [x] SubTask 17.1: 公共表格组件 — 分页/排序/筛选/操作列
- [x] SubTask 17.2: 公共表单组件 — 校验/布局/提交
- [x] SubTask 17.3: 公共弹窗组件 — 确认/表单/详情
- [x] SubTask 17.4: 状态标签组件 — 健康状态/任务状态/启用禁用
- [x] SubTask 17.5: 布局完善 — 面包屑、用户菜单、通知占位

## Task 18: C01/C02 验收和端到端测试
- [x] SubTask 18.1: C01 验证 — 创建用户/组织/角色/权限，授权和撤销实时生效，全部有审计
- [x] SubTask 18.2: C02 验证 — 同一业务代码 zh-CN/en-US 双语显示正确，缓存版本更新
- [x] SubTask 18.3: E2E 测试 — 登录 → 用户管理 → 角色分配 → 字典管理 → 配置管理 → 审计查看
- [x] SubTask 18.4: Docker Compose 全栈验证 — 启动并验证所有 M1 功能

## Task 19: 更新机器清单和证据
- [x] SubTask 19.1: 更新 F-PLATFORM-001~004 状态为 DONE
- [x] SubTask 19.2: 添加 evidence 路径
- [x] SubTask 19.3: 验证 validate_platform_manifest.py 通过
- [x] SubTask 19.4: 创建 M1 Runbook 和安全报告

# Task Dependencies
- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 2]
- [Task 4] depends on [Task 2, Task 3]
- [Task 5] depends on [Task 1] (共享契约目录)
- [Task 6] depends on [Task 5, Task 2] (复用 Auth 基础)
- [Task 7] depends on [Task 6, Task 3] (复用前端公共框架)
- [Task 8] depends on [Task 6, Task 7]
- [Task 9] depends on [Task 1]
- [Task 10] depends on [Task 9]
- [Task 11] depends on [Task 10, Task 17]
- [Task 12] depends on [Task 10, Task 11]
- [Task 13] depends on [Task 1]
- [Task 14] depends on [Task 13, Task 2] (复用 Auth 审计)
- [Task 15] depends on [Task 14, Task 17]
- [Task 16] depends on [Task 14, Task 15]
- [Task 17] depends on [Task 3] (基于登录页扩展公共框架)
- [Task 18] depends on [Task 4, Task 8, Task 12, Task 16, Task 17]
- [Task 19] depends on [Task 18]
- [Task 1, Task 9, Task 13] 可并行启动（契约先行）
- [Task 9, Task 13] 与 [Task 1~4] 可并行（不同领域模块）
