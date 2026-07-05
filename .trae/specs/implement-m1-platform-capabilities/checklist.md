# M1 平台公共能力验证清单

## F-PLATFORM-001: 认证与会话
- [ ] OpenAPI 契约包含 /api/v1/auth/login、/auth/refresh、/auth/logout、/profile 端点
- [ ] AsyncAPI 契约包含 user.logged_in.v1、session.refreshed.v1、session.revoked.v1 事件
- [ ] Flyway V3 迁移创建 sys_user、sys_refresh_token、sys_session 表
- [ ] sys_user 表包含密码哈希字段（BCrypt），不存储明文密码
- [ ] sys_session 表包含状态字段和过期时间
- [ ] SM-IAM-001 会话状态机定义存在（ACTIVE/REFRESHING/EXPIRED/REVOKED）
- [ ] AuthService.login() 验证密码并返回 JWT access_token 和 refresh_token
- [ ] AuthService.refresh() 验证 refresh_token 并返回新的 access_token
- [ ] AuthService.logout() 撤销 refresh_token 和会话
- [ ] JWT Token 包含用户 ID、权限码、过期时间
- [ ] Spring Security JWT 过滤器正确拦截和验证 Token
- [ ] 密码使用 BCrypt 哈希，不可逆
- [ ] 登录失败返回 401 ProblemDetails，不泄露用户是否存在
- [ ] 登录/登出操作写入审计记录
- [ ] 登录/登出/刷新事件写入 Outbox
- [ ] 前端登录页表单校验和 API 调用正常
- [ ] 前端 Token 存储在 localStorage，请求自动注入 Authorization 头
- [ ] 前端 401 自动跳转登录页
- [ ] 前端路由守卫阻止未登录访问
- [ ] 个人资料页查看和修改功能正常
- [ ] Java 单元测试覆盖 AuthService、JwtTokenProvider
- [ ] Java 集成测试覆盖登录/刷新/登出/资料端到端
- [ ] 安全测试验证密码不可逆、Token 过期、无效凭据不泄露信息

## F-PLATFORM-002: 用户组织角色权限
- [ ] OpenAPI 契约包含用户/组织/角色/权限 CRUD 端点
- [ ] AsyncAPI 契约包含 user.created.v1 等 5 个事件
- [ ] Flyway V4 迁移创建 sys_org、sys_role、sys_permission 等表
- [ ] 种子数据包含默认管理员、默认组织、基础角色和权限码
- [ ] 用户 CRUD 支持创建、编辑、禁用、分配组织和角色
- [ ] 组织支持树形层级（parent_id）
- [ ] 角色支持多权限码分配
- [ ] 权限码为稳定字符串，不使用角色名判断
- [ ] DataScopeFilter 基于组织层级过滤查询结果
- [ ] PermissionCache 缓存用户权限码，支持事件驱动失效
- [ ] 角色变更后，用户后续请求使用新权限（实时生效）
- [ ] 用户/角色变更事件写入 Outbox
- [ ] 用户/角色/权限变更写入审计记录
- [ ] 前端用户管理页支持列表/搜索/创建/编辑/禁用/分配角色
- [ ] 前端组织管理页支持树形展示/创建/编辑
- [ ] 前端角色管理页支持列表/创建/编辑/分配权限
- [ ] 前端权限查看页支持权限码列表/按角色过滤
- [ ] 前端 v-permission 指令控制按钮/菜单可见性
- [ ] Java 单元测试覆盖 UserService、RoleService、PermissionCache、DataScopeFilter
- [ ] Java 集成测试覆盖用户/组织/角色 CRUD 端到端
- [ ] 权限测试验证角色变更实时生效
- [ ] 数据范围测试验证组织层级过滤

## F-PLATFORM-003: 数据字典与国际化
- [ ] OpenAPI 契约包含字典类型/项、i18n 资源 CRUD 端点
- [ ] AsyncAPI 契约包含 platform.dictionary.changed.v1、platform.i18n.changed.v1 事件
- [ ] Flyway V5 迁移创建 sys_dict_type、sys_dict_item、sys_i18n_resource 表
- [ ] 种子数据包含基础字典和 i18n 资源
- [ ] 字典类型支持版本化发布
- [ ] 字典项支持排序和启用/禁用
- [ ] i18n 资源支持 zh-CN 和 en-US
- [ ] 字典/i18n 内存缓存，事件驱动失效
- [ ] 字典/i18n 变更事件写入 Outbox
- [ ] 前端字典管理页支持类型列表、项管理、版本发布
- [ ] 前端 i18n 资源管理页支持 key 列表、多语言编辑
- [ ] 前端从后端加载 i18n 资源并缓存
- [ ] C02 验证：同一业务代码 zh-CN/en-US 双语显示正确
- [ ] C02 验证：无重复业务字典
- [ ] Java 单元测试覆盖 DictService、I18nService
- [ ] Java 集成测试覆盖字典/i18n CRUD 端到端

## F-PLATFORM-004: 配置审计幂等与导出
- [ ] OpenAPI 契约包含配置、审计、导出端点
- [ ] AsyncAPI 契约包含 platform.config.changed.v1 等 3 个事件
- [ ] Flyway V6 迁移创建 sys_config、export_operation 表
- [ ] 配置支持类型化（string/number/boolean/json）和版本管理
- [ ] 审计日志为追加式（append-only），不可修改
- [ ] 审计查询支持按 actor、action、resource、trace_id、时间范围过滤
- [ ] 幂等拦截器基于 idempotency_key 去重，返回首次结果
- [ ] 幂等键有过期时间，自动清理
- [ ] 导出任务异步执行，支持状态轮询和下载
- [ ] 配置/导出事件写入 Outbox
- [ ] 所有操作写入审计记录，trace_id 串联
- [ ] 前端配置管理页支持列表/编辑/版本历史
- [ ] 前端审计日志页支持列表/过滤/详情
- [ ] 前端导出功能支持导出按钮/状态轮询/下载
- [ ] Java 单元测试覆盖 ConfigService、AuditService、IdempotencyService、ExportService
- [ ] Java 集成测试覆盖配置/审计/导出端到端
- [ ] 幂等测试验证重复请求返回首次结果
- [ ] 审计测试验证所有操作有审计记录

## 前端公共框架
- [ ] 公共表格组件支持分页/排序/筛选/操作列
- [ ] 公共表单组件支持校验/布局/提交
- [ ] 公共弹窗组件支持确认/表单/详情
- [ ] 状态标签组件支持健康状态/任务状态/启用禁用
- [ ] 布局包含面包屑、用户菜单、通知占位
- [ ] API 拦截器统一 Token 注入、401 处理、ProblemDetails 解析
- [ ] 路由守卫阻止未登录和未授权访问
- [ ] v-permission 指令控制元素可见性

## C01/C02 验收
- [ ] C01: 创建用户/组织/角色/权限闭环验证通过
- [ ] C01: 授权和撤销实时生效
- [ ] C01: 全部操作有审计记录
- [ ] C02: 同一业务代码 zh-CN/en-US 双语显示正确
- [ ] C02: 字典缓存版本更新正常
- [ ] C02: 无重复业务字典
- [ ] E2E 测试: 登录 → 用户管理 → 角色分配 → 字典 → 配置 → 审计 全链路通过

## Docker Compose 验证
- [ ] `docker compose --profile full up -d` 启动成功
- [ ] 数据库迁移 V3-V6 执行成功
- [ ] 云控制面健康检查通过
- [ ] 前端页面可访问

## 机器清单
- [ ] F-PLATFORM-001 状态标记为 DONE
- [ ] F-PLATFORM-002 状态标记为 DONE
- [ ] F-PLATFORM-003 状态标记为 DONE
- [ ] F-PLATFORM-004 状态标记为 DONE
- [ ] python scripts/validate_platform_manifest.py 验证通过
- [ ] M1 Runbook 创建（非空）
- [ ] M1 安全报告创建（非空）
