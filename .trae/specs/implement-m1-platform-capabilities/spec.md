# M1 平台公共能力 Spec

## Why

M0 工程底座已完成（F-ENGINEERING-001、F-DEPLOY-001 均 DONE）。根据 AI 开发约束第 18 节，M1 必须交付平台公共能力：用户/组织/角色/权限/认证、数据字典/国际化、配置/审计/幂等/导出和前端公共框架。M1 退出条件是 C01（公共能力闭环）和 C02（字典/国际化闭环）通过，且后续业务模块不再自建公共能力。

## What Changes

### F-PLATFORM-001: 认证与会话
- 后端：platform-iam 模块实现登录、会话管理、Token 刷新/撤销、个人资料
- 数据库：sys_user、sys_refresh_token、sys_session 表和索引
- 契约：OpenAPI 认证端点、AsyncAPI user.logged_in.v1 / session.refreshed.v1 / session.revoked.v1 事件
- 前端：登录页功能实现、个人资料页、路由守卫
- 权限码：platform.profile.read、platform.profile.manage
- 状态机：SM-IAM-001（会话状态）

### F-PLATFORM-002: 用户组织角色权限
- 后端：platform-iam 模块实现用户/组织/角色/权限 CRUD、数据范围、授权缓存与实时撤销
- 数据库：sys_org、sys_user_org、sys_role、sys_permission、sys_user_role、sys_role_permission 表
- 契约：OpenAPI 用户/组织/角色/权限端点、AsyncAPI user.created.v1 / user.status_changed.v1 / user.roles_changed.v1 / role.changed.v1 / authorization.changed.v1 事件
- 前端：用户管理页、组织管理页、角色管理页、权限查看页
- 权限码：platform.user.read/manage、platform.org.manage、platform.role.read/manage、platform.permission.read
- 数据范围：基于组织层级的查询过滤

### F-PLATFORM-003: 数据字典与国际化
- 后端：platform-governance 模块实现数据字典类型/项管理、i18n 资源管理、版本化发布
- 数据库：sys_dict_type、sys_dict_item、sys_i18n_resource 表
- 契约：OpenAPI 字典/i18n 端点、AsyncAPI platform.dictionary.changed.v1 / platform.i18n.changed.v1 事件
- 前端：字典管理页、i18n 资源管理页
- 权限码：platform.dictionary.read/manage、platform.i18n.read/manage

### F-PLATFORM-004: 配置审计幂等与导出
- 后端：platform-governance 模块实现类型化配置管理、追加式审计日志、幂等键管理、异步导出
- 数据库：sys_config、export_operation 表（sys_operation_audit、sys_idempotency_record 已在 M0 创建）
- 契约：OpenAPI 配置/审计/导出端点、AsyncAPI platform.config.changed.v1 / export.requested.v1 / export.completed.v1 事件
- 前端：配置管理页、审计日志查看页
- 权限码：platform.config.read/manage、audit.audit.read/export

### 前端公共框架
- API 拦截器：统一 Token 注入、401 自动刷新/跳转、ProblemDetails 解析
- 路由守卫：未登录跳转、权限码校验
- 布局完善：面包屑、用户菜单、通知中心占位
- 公共组件：表格（分页/排序/筛选）、表单（校验）、弹窗、状态标签

## Impact

- Affected specs: F-PLATFORM-001、F-PLATFORM-002、F-PLATFORM-003、F-PLATFORM-004
- Affected code:
  - apps/cloud-control/platform-iam/（认证、用户、组织、角色、权限）
  - apps/cloud-control/platform-governance/（字典、i18n、配置、审计、幂等、导出）
  - apps/cloud-control/bootstrap/（安全配置、数据源、Flyway 迁移）
  - apps/web-console/src/（登录、用户管理、组织、角色、字典、i18n、配置、审计页面）
  - contracts/openapi/、contracts/asyncapi/（新增端点和事件契约）
- 数据库迁移：新增 V3-V8 迁移文件
- M1 不涉及边缘安全、Agent 或真实设备

## ADDED Requirements

### Requirement: 认证与会话（F-PLATFORM-001）
系统必须提供基于用户名密码的认证机制，支持 JWT Token 和刷新令牌。

#### Scenario: 用户登录
- **WHEN** 用户提交有效的用户名和密码
- **THEN** 返回 access_token 和 refresh_token，记录会话，发布 user.logged_in.v1 事件

#### Scenario: Token 刷新
- **WHEN** access_token 过期，前端使用有效的 refresh_token 请求刷新
- **THEN** 返回新的 access_token，发布 session.refreshed.v1 事件

#### Scenario: 会话撤销
- **WHEN** 用户主动退出或管理员强制下线
- **THEN** 撤销 refresh_token，发布 session.revoked.v1 事件

#### Scenario: 登录失败
- **WHEN** 用户提交无效凭据
- **THEN** 返回 401 ProblemDetails，不泄露用户是否存在

### Requirement: 用户组织角色权限（F-PLATFORM-002）
系统必须提供 RBAC 权限模型，支持数据范围和实时授权撤销。

#### Scenario: 创建用户
- **WHEN** 管理员创建新用户并分配组织和角色
- **THEN** 用户创建成功，发布 user.created.v1 事件，审计记录写入

#### Scenario: 角色变更实时生效
- **WHEN** 管理员修改用户角色
- **THEN** 发布 user.roles_changed.v1 和 authorization.changed.v1 事件，后续请求使用新权限

#### Scenario: 数据范围过滤
- **WHEN** 用户查询数据列表
- **THEN** 根据用户组织层级和数据范围权限自动过滤

### Requirement: 数据字典与国际化（F-PLATFORM-003）
系统必须提供版本化数据字典和多语言资源管理。

#### Scenario: 字典变更
- **WHEN** 管理员发布新的字典版本
- **THEN** 发布 platform.dictionary.changed.v1 事件，前端缓存更新

#### Scenario: 多语言显示
- **WHEN** 同一业务代码在 zh-CN 和 en-US 环境下显示
- **THEN** 返回对应语言的文案，无重复业务字典

### Requirement: 配置审计幂等与导出（F-PLATFORM-004）
系统必须提供类型化配置、追加式审计、幂等键和异步导出。

#### Scenario: 配置变更
- **WHEN** 管理员修改平台配置
- **THEN** 发布 platform.config.changed.v1 事件，审计记录写入

#### Scenario: 幂等请求
- **WHEN** 客户端使用相同 idempotency_key 重复请求
- **THEN** 返回首次请求的结果，不重复执行业务操作

#### Scenario: 审计查询
- **WHEN** 管理员查询操作审计日志
- **THEN** 返回按时间倒序的审计记录，支持按 actor、action、resource、trace_id 过滤

## MODIFIED Requirements

### Requirement: 机器清单状态
F-PLATFORM-001、F-PLATFORM-002、F-PLATFORM-003、F-PLATFORM-004 的 `implementation_status` 从 `NOT_STARTED` 改为 `IN_PROGRESS`，全部验证通过后改为 `DONE`。

### Requirement: 前端公共框架
M0 前端骨架升级为完整的公共框架，包含路由守卫、API 拦截器、公共组件库和布局完善。
