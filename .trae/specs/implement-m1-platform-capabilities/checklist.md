# M1 平台公共能力验证清单

## F-PLATFORM-001: 认证与会话
- [x] OpenAPI 契约包含 /api/v1/auth/login、/auth/refresh、/auth/logout、/profile 端点
- [x] AsyncAPI 契约包含 user.logged_in.v1、session.refreshed.v1、session.revoked.v1 事件
- [x] Flyway V3 迁移创建 sys_user、sys_refresh_token、sys_session 表
- [x] sys_user 表包含密码哈希字段（BCrypt），不存储明文密码
- [x] sys_session 表包含状态字段和过期时间
- [x] SM-IAM-001 会话状态机定义存在（ACTIVE/REFRESHING/EXPIRED/REVOKED）
- [x] AuthService.login() 验证密码并返回 JWT access_token 和 refresh_token
- [x] AuthService.refresh() 验证 refresh_token 并返回新的 access_token
- [x] AuthService.logout() 撤销 refresh_token 和会话
- [x] JWT Token 包含用户 ID、权限码、过期时间
- [x] Spring Security JWT 过滤器正确拦截和验证 Token
- [x] 密码使用 BCrypt 哈希，不可逆
- [x] 登录失败返回 401 ProblemDetails，不泄露用户是否存在
- [x] 登录/登出操作写入审计记录
- [x] 登录/登出/刷新事件写入 Outbox
- [x] 前端登录页表单校验和 API 调用正常
- [x] 前端 Token 存储在 localStorage，请求自动注入 Authorization 头
- [x] 前端 401 自动跳转登录页
- [x] 前端路由守卫阻止未登录访问
- [x] 个人资料页查看和修改功能正常
- [x] Java 单元测试覆盖 AuthService、JwtTokenProvider
- [x] Java 集成测试覆盖登录/刷新/登出/资料端到端
- [x] 安全测试验证密码不可逆、Token 过期、无效凭据不泄露信息

## F-PLATFORM-002: 用户组织角色权限
- [x] OpenAPI 契约包含用户/组织/角色/权限 CRUD 端点
- [x] AsyncAPI 契约包含 user.created.v1 等 5 个事件
- [x] Flyway V4 迁移创建 sys_org、sys_role、sys_permission 等表
- [x] 种子数据包含默认管理员、默认组织、基础角色和权限码
- [x] 用户 CRUD 支持创建、编辑、禁用、分配组织和角色
- [x] 组织支持树形层级（parent_id）
- [x] 角色支持多权限码分配
- [x] 权限码为稳定字符串，不使用角色名判断
- [x] DataScopeFilter 基于组织层级过滤查询结果
- [x] PermissionCache 缓存用户权限码，支持事件驱动失效
- [x] 角色变更后，用户后续请求使用新权限（实时生效）
- [x] 用户/角色变更事件写入 Outbox
- [x] 用户/角色/权限变更写入审计记录
- [x] 前端用户管理页支持列表/搜索/创建/编辑/禁用/分配角色
- [x] 前端组织管理页支持树形展示/创建/编辑
- [x] 前端角色管理页支持列表/创建/编辑/分配权限
- [x] 前端权限查看页支持权限码列表/按角色过滤
- [x] 前端 v-permission 指令控制按钮/菜单可见性
- [x] Java 单元测试覆盖 UserService、RoleService、PermissionCache、DataScopeFilter
- [x] Java 集成测试覆盖用户/组织/角色 CRUD 端到端
- [x] 权限测试验证角色变更实时生效
- [x] 数据范围测试验证组织层级过滤

## F-PLATFORM-003: 数据字典与国际化
- [x] OpenAPI 契约包含字典类型/项、i18n 资源 CRUD 端点
- [x] AsyncAPI 契约包含 platform.dictionary.changed.v1、platform.i18n.changed.v1 事件
- [x] Flyway V5 迁移创建 sys_dict_type、sys_dict_item、sys_i18n_resource 表
- [x] 种子数据包含基础字典和 i18n 资源
- [x] 字典类型支持版本化发布
- [x] 字典项支持排序和启用/禁用
- [x] i18n 资源支持 zh-CN 和 en-US
- [x] 字典/i18n 内存缓存，事件驱动失效
- [x] 字典/i18n 变更事件写入 Outbox
- [x] 前端字典管理页支持类型列表、项管理、版本发布
- [x] 前端 i18n 资源管理页支持 key 列表、多语言编辑
- [x] 前端从后端加载 i18n 资源并缓存
- [x] C02 验证：同一业务代码 zh-CN/en-US 双语显示正确
- [x] C02 验证：无重复业务字典
- [x] Java 单元测试覆盖 DictService、I18nService
- [x] Java 集成测试覆盖字典/i18n CRUD 端到端

## F-PLATFORM-004: 配置审计幂等与导出
- [x] OpenAPI 契约包含配置、审计、导出端点
- [x] AsyncAPI 契约包含 platform.config.changed.v1 等 3 个事件
- [x] Flyway V6 迁移创建 sys_config、export_operation 表
- [x] 配置支持类型化（string/number/boolean/json）和版本管理
- [x] 审计日志为追加式（append-only），不可修改
- [x] 审计查询支持按 actor、action、resource、trace_id、时间范围过滤
- [x] 幂等拦截器基于 idempotency_key 去重，返回首次结果
- [x] 幂等键有过期时间，自动清理
- [x] 导出任务异步执行，支持状态轮询和下载
- [x] 配置/导出事件写入 Outbox
- [x] 所有操作写入审计记录，trace_id 串联
- [x] 前端配置管理页支持列表/编辑/版本历史
- [x] 前端审计日志页支持列表/过滤/详情
- [x] 前端导出功能支持导出按钮/状态轮询/下载
- [x] Java 单元测试覆盖 ConfigService、AuditService、IdempotencyService、ExportService
- [x] Java 集成测试覆盖配置/审计/导出端到端
- [x] 幂等测试验证重复请求返回首次结果
- [x] 审计测试验证所有操作有审计记录

## 前端公共框架
- [x] 公共表格组件支持分页/排序/筛选/操作列
- [x] 公共表单组件支持校验/布局/提交
- [x] 公共弹窗组件支持确认/表单/详情
- [x] 状态标签组件支持健康状态/任务状态/启用禁用
- [x] 布局包含面包屑、用户菜单、通知占位
- [x] API 拦截器统一 Token 注入、401 处理、ProblemDetails 解析
- [x] 路由守卫阻止未登录和未授权访问
- [x] v-permission 指令控制元素可见性

## C01/C02 验收
- [x] C01: 创建用户/组织/角色/权限闭环验证通过
- [x] C01: 授权和撤销实时生效
- [x] C01: 全部操作有审计记录
- [x] C02: 同一业务代码 zh-CN/en-US 双语显示正确
- [x] C02: 字典缓存版本更新正常
- [x] C02: 无重复业务字典
- [x] E2E 测试: 登录 → 用户管理 → 角色分配 → 字典 → 配置 → 审计 全链路通过

## Docker Compose 验证
- [x] `docker compose --profile full up -d` 启动成功
- [x] 数据库迁移 V3-V6 执行成功
- [x] 云控制面健康检查通过
- [x] 前端页面可访问

## 机器清单
- [x] F-PLATFORM-001 状态标记为 DONE
- [x] F-PLATFORM-002 状态标记为 DONE
- [x] F-PLATFORM-003 状态标记为 DONE
- [x] F-PLATFORM-004 状态标记为 DONE
- [x] python scripts/validate_platform_manifest.py 验证通过
- [x] M1 Runbook 创建（非空）
- [x] M1 安全报告创建（非空）
