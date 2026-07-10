# Checklist

## Phase 1: 前端构建修复与功能补全

- [x] `pnpm build` 通过，vue-tsc 类型检查无错误
- [x] `pnpm test` 通过，现有测试不受影响
- [x] ProfileView.vue 第 73-74 行 `string | null` 类型错误已修复（使用 `?? ''` 兜底）
- [x] McpManagementView.vue 已创建，包含工具列表、详情、调用测试、历史查询
- [x] MCP 管理路由已配置，权限码为 `mcp.tool.read`
- [x] MCP 管理页面 i18n 文案已添加（zh-CN 和 en-US）
- [x] DefaultLayout 导航菜单包含 MCP 管理入口
- [x] MissionManagementView 通过 WebSocket 接收任务执行事件并实时更新
- [x] MissionManagementView WebSocket 有 5 秒重连退避，组件卸载时关闭连接
- [x] SafetyControlView 通过 WebSocket 接收安全事件并实时更新
- [x] SafetyControlView WebSocket 有 5 秒重连退避，组件卸载时关闭连接
- [x] platform.ts Store 的 `isOnline` 联动 `online`/`offline` 事件
- [x] platform.ts Store 的 `currentOrg` 支持 `switchOrg` 并持久化到 localStorage

## Phase 2: Python 服务 JetStream 集成与安全统一

- [x] agent-runtime 使用 JetStream durable consumer 消费 plan_request，消息在消费端断开-恢复后不丢失
- [x] agent-runtime 的 reply 仍通过 core NATS reply subject 返回
- [x] mcp-tool-gateway 使用 JetStream durable consumer 消费 tool invoke 请求
- [x] mcp-tool-gateway 的 input schema 校验在调用前执行，校验失败返回结构化错误
- [x] mcp-tool-gateway 的 output schema 校验在调用后执行
- [x] mcp-tool-gateway 审计日志通过 NATS 发布而非仅内存 list
- [x] edge/gateway 使用 JetStream durable consumer 消费 edge command
- [x] edge/gateway 的 CommandHandler 委托 safety-gateway 的 SafetyStateMachine，不再维护独立 `_safety_latched`
- [x] emergency_stop 命令经过统一 SafetyStateMachine.trigger_emergency_stop()
- [x] execute_skill 命令先查询 SafetyStateMachine 状态，非 NORMAL 时拒绝
- [x] JetStream durable consumer 与 offline_cache.py 协作，断网期间缓存、重连后从断点继续
- [x] edge/safety-gateway 使用 JetStream 消费 skill.execute 拦截请求
- [x] edge/local-skill-executor 使用 JetStream 消费执行请求
- [x] ros1-adapter 使用 JetStream 消费翻译请求
- [x] QwenPawProvider 对 PlanProposal 中每个 step 校验 skill_id 是否已注册
- [x] QwenPawProvider 对每个 step 的 params 校验是否匹配 skill input_schema
- [x] 校验失败的 step 标记为 invalid 并附带错误原因，不丢弃整个 PlanProposal
- [x] Python 服务全部测试通过（425 tests pass across 6 services）
- [x] JetStream 持久化测试覆盖消费端断开-恢复场景

## Phase 3: Java 后端 Adapter 领域与真实集成测试

- [x] platform-robot 模块包含 adapter 包（domain/repository/service/controller）
- [x] adapter 兼容性查询按 robot_model 返回适配器类型、ROS 版本、控制协议
- [x] adapter 健康状态投影和 `adapter.health_changed.v1` 事件发布已实现
- [x] V24 迁移创建 `adapter_compatibility` 表
- [x] OpenAPI 契约 `contracts/openapi/paths/adapter.yaml` 存在且路径定义完整
- [x] adapter 领域单元测试覆盖核心查询和状态变更逻辑（9/9 pass）
- [x] bootstrap 模块引入 Testcontainers PostgreSQL 16 + pgvector 测试依赖
- [x] AbstractIntegrationTest 基类启动 PostgreSQL 容器并执行 Flyway 迁移
- [x] application-test.yml 使用 Testcontainers 数据源
- [x] MissionLifecycleIntegrationTest（真实版）覆盖创建->执行->完成，验证数据库和 Outbox 事件（4 pass）
- [x] SafetyFlowIntegrationTest（真实版）覆盖急停->锁存->复位，验证安全状态和事件（6 pass）
- [x] AuthPermissionIntegrationTest（真实版）覆盖登录->RBAC->权限检查
- [x] RobotManagementIntegrationTest（真实版）覆盖注册->状态更新->查询->删除（9 pass）
- [x] 原有 mock 版测试已重命名为 `*WebLayerTest` 并保留

## Phase 4: 前端测试与可观测性增强

- [x] 全部 `src/api/*.ts` 模块有单元测试，验证请求 URL/方法/参数/响应处理
- [x] 401 自动刷新 token 逻辑有测试覆盖
- [x] LoginView 组件测试覆盖表单验证、提交、错误处理（5 tests）
- [x] auth.ts Store 测试覆盖 login/logout、token 过期、权限计算（11 tests）
- [x] platform.ts Store 测试覆盖 isOnline 联动、org 切换持久化（8 tests）
- [x] 路由守卫 beforeEach 测试覆盖未登录跳转、权限码校验、token 过期（7 tests）
- [x] `pnpm test` 通过，测试数量显著增加（196 tests across 32 files）
- [x] grafana-alert-rules.yaml 定义服务不健康、错误率、急停、任务失败率、NATS 断连告警规则
- [x] vmagent.yml 包含 edge/sim 服务指标抓取配置
- [x] vector.yaml 包含日志过滤和敏感字段脱敏管线
- [x] compose.yml 挂载告警规则文件到 Grafana
- [x] 部署报告中镜像版本与 compose.yml 完全一致

## Phase 5: 验证与回归

- [x] `python3 scripts/validate_platform_manifest.py` 通过（设计追踪校验）
- [x] `python3 scripts/validate_platform_manifest.py --require-complete` 通过（完整实施门禁）
- [x] Python 6 服务全量测试通过（425 tests: agent-runtime 38, mcp-gateway 60, edge/gateway 157, safety-gateway 64, local-skill-executor 31, ros1-adapter 75）
- [x] `pnpm build` 前端构建通过
- [x] `pnpm test` 前端测试通过（196 tests）
- [x] Java AdapterServiceTest 9/9 pass
- [x] 无新增 TODO/FIXME/stub/placeholder 在生产代码中
- [x] 无安全红线违反（Agent 不直接调硬件、急停锁存本地优先、Agent 输出为不可信提案）
