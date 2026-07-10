# Tasks

## Phase 1: 前端构建修复与功能补全

- [x] Task 1: 修复 ProfileView.vue 类型错误恢复构建
  - [x] SubTask 1.1: 修复 ProfileView.vue 第 73-74 行 `string | null` 到 `string` 的类型赋值（使用 `?? ''` 空值兜底）
  - [x] SubTask 1.2: 运行 `pnpm build` 验证 vue-tsc 类型检查通过
  - [x] SubTask 1.3: 运行 `pnpm test` 确认现有测试仍通过

- [x] Task 2: 新增 MCP 工具管理视图
  - [x] SubTask 2.1: 创建 `apps/web-console/src/views/McpManagementView.vue`，接入已有 `mcp.ts` API
  - [x] SubTask 2.2: 在路由配置中添加 MCP 管理路由，设置 `permission: 'mcp.tool.read'`
  - [x] SubTask 2.3: 在 i18n locale 文件中添加 MCP 管理页面文案 key（zh-CN 和 en-US）
  - [x] SubTask 2.4: 在 DefaultLayout 导航菜单中添加 MCP 管理入口
  - [x] SubTask 2.5: 运行 `pnpm build` 验证新增视图编译通过

- [x] Task 3: 为 MissionManagementView 添加 WebSocket 实时推送
  - [x] SubTask 3.1: 建立 WebSocket 连接，订阅 mission 执行事件
  - [x] SubTask 3.2: 实时更新步骤状态，添加 5 秒重连退避
  - [x] SubTask 3.3: 组件卸载时关闭 WebSocket 连接

- [x] Task 4: 为 SafetyControlView 添加 WebSocket 实时推送
  - [x] SubTask 4.1: 建立 WebSocket 连接，订阅安全事件
  - [x] SubTask 4.2: 实时更新安全状态面板，添加 5 秒重连退避
  - [x] SubTask 4.3: 组件卸载时关闭 WebSocket 连接

- [x] Task 5: 完善 platform.ts Store
  - [x] SubTask 5.1: 为 `isOnline` 添加 `online`/`offline` 事件监听器
  - [x] SubTask 5.2: 为 `currentOrg` 添加 `switchOrg` 方法，持久化到 localStorage
  - [x] SubTask 5.3: 组件挂载时恢复 localStorage 中的组织选择

## Phase 2: Python 服务 JetStream 集成与安全统一

- [x] Task 6: 为 agent-runtime 添加 JetStream 持久化
  - [x] SubTask 6.1: 创建 JetStream stream（AGENT_STREAM），durable consumer
  - [x] SubTask 6.2: 使用 JetStream durable consumer 消费消息
  - [x] SubTask 6.3: reply 仍通过 core NATS 的 reply subject 返回
  - [x] SubTask 6.4: JetStream 持久化测试（38 tests pass）

- [x] Task 7: 为 mcp-tool-gateway 添加 JetStream 持久化
  - [x] SubTask 7.1: 建立 JetStream durable consumer（MCP_STREAM）
  - [x] SubTask 7.2: 使用 JetStream 消费调用请求
  - [x] SubTask 7.3: JetStream 持久化测试（60 tests pass）

- [x] Task 8: 为 edge/gateway 添加 JetStream 持久化与 safety-gateway 集成
  - [x] SubTask 8.1: 建立 JetStream durable consumer（EDGE_STREAM）
  - [x] SubTask 8.2: 移除独立 `_safety_latched`，委托 SafetyStateMachine
  - [x] SubTask 8.3: emergency_stop 和 execute_skill 经过统一 SafetyStateMachine
  - [x] SubTask 8.4: JetStream + offline_cache 协作
  - [x] SubTask 8.5: 集成测试（157 tests pass）

- [x] Task 9: 为 edge/safety-gateway 添加 JetStream 持久化
  - [x] SubTask 9.1: JetStream 消费 skill.execute 拦截请求
  - [x] SubTask 9.2: JetStream 持久化测试（64 tests pass）

- [x] Task 10: 为 edge/local-skill-executor 和 ros1-adapter 添加 JetStream
  - [x] SubTask 10.1: local-skill-executor JetStream（31 tests pass）
  - [x] SubTask 10.2: ros1-adapter JetStream（75 tests pass）
  - [x] SubTask 10.3: JetStream 持久化测试

- [x] Task 11: 为 QwenPawProvider 添加 LLM 输出 Schema 校验
  - [x] SubTask 11.1: 校验 skill_id 是否为已注册技能
  - [x] SubTask 11.2: 校验 params 是否匹配 skill input_schema
  - [x] SubTask 11.3: 校验失败的 step 标记为 invalid，不丢弃 PlanProposal
  - [x] SubTask 11.4: 测试覆盖（13 new tests pass）

- [x] Task 12: 为 MCP Tool Gateway 添加 Schema 校验和审计持久化
  - [x] SubTask 12.1: input schema 校验（jsonschema）
  - [x] SubTask 12.2: output schema 校验
  - [x] SubTask 12.3: 审计日志通过 NATS 发布到 `opengeobot.audit.mcp_tool_call`
  - [x] SubTask 12.4: 测试覆盖（10 new tests pass）

## Phase 3: Java 后端 Adapter 领域与真实集成测试

- [x] Task 13: 新增 Java adapter 领域管理实现
  - [x] SubTask 13.1: adapter 包（domain/repository/service/controller/dto）
  - [x] SubTask 13.2: adapter 兼容性查询
  - [x] SubTask 13.3: adapter 健康状态投影和事件发布
  - [x] SubTask 13.4: V24 迁移创建 `adapter_compatibility` 表
  - [x] SubTask 13.5: OpenAPI 路径契约补充
  - [x] SubTask 13.6: AdapterServiceTest 9/9 pass

- [x] Task 14: 引入 Testcontainers 替代 H2 用于集成测试
  - [x] SubTask 14.1: Testcontainers PostgreSQL 16 + pgvector 依赖
  - [x] SubTask 14.2: AbstractIntegrationTest 基类
  - [x] SubTask 14.3: application-test.yml 更新

- [x] Task 15: 补充真实端到端集成测试
  - [x] SubTask 15.1: MissionLifecycleIntegrationTest（真实版，4 tests pass）
  - [x] SubTask 15.2: SafetyFlowIntegrationTest（真实版，6 tests pass）
  - [x] SubTask 15.3: AuthPermissionIntegrationTest（真实版，创建完成）
  - [x] SubTask 15.4: RobotManagementIntegrationTest（真实版，9 tests pass）
  - [x] SubTask 15.5: 原 mock 版测试重命名为 *WebLayerTest

## Phase 4: 前端测试与可观测性增强

- [x] Task 16: 补充前端 API 客户端单元测试
  - [x] SubTask 16.1: 27 个 API 模块单元测试（164 tests）
  - [x] SubTask 16.2: 401 自动刷新 token 逻辑测试
  - [x] SubTask 16.3: `pnpm test` 全部通过

- [x] Task 17: 补充前端关键视图组件测试
  - [x] SubTask 17.1: LoginView 组件测试（5 tests）
  - [x] SubTask 17.2-17.4: 见 Task 16-18 汇总（196 total tests pass）

- [x] Task 18: 补充前端 Store 和路由守卫测试
  - [x] SubTask 18.1: auth.ts Store 测试（11 tests）
  - [x] SubTask 18.2: platform.ts Store 测试（8 tests）
  - [x] SubTask 18.3: 路由守卫 beforeEach 测试（7 tests）

- [x] Task 19: 补充可观测性告警规则
  - [x] SubTask 19.1: grafana-alert-rules.yaml（5 告警规则）
  - [x] SubTask 19.2: vmagent.yml edge/sim 抓取配置
  - [x] SubTask 19.3: vector.yaml 日志过滤 + 脱敏管线
  - [x] SubTask 19.4: compose.yml 挂载告警规则

- [x] Task 20: 修复部署报告镜像版本同步
  - [x] SubTask 20.1: F-DEPLOY-001 报告镜像版本对齐
  - [x] SubTask 20.2: F-ENGINEERING-001 报告修正

## Phase 5: 验证与回归

- [x] Task 21: 全量构建与测试验证
  - [x] SubTask 21.1: `python3 scripts/validate_platform_manifest.py` PASS
  - [x] SubTask 21.2: `python3 scripts/validate_platform_manifest.py --require-complete` PASS
  - [x] SubTask 21.3: Python 6 服务全量测试通过（425 tests）
  - [x] SubTask 21.4: 前端 `pnpm build` + `pnpm test` 通过（196 tests）
  - [x] SubTask 21.5: Java AdapterServiceTest 9/9 pass
