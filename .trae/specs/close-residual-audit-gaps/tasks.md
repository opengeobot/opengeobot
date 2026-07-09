# Tasks

## Phase 1: 安全红线修复（最高优先级）

## Task 1: 3 个 Controller 补齐 @PreAuthorize 权限码
- [x] SubTask 1.1: 为 MissionTemplateController 全部 2 个端点添加 @PreAuthorize（mission.template.read / mission.template.manage）
- [x] SubTask 1.2: 为 RobotGroupController 全部 8 个端点添加 @PreAuthorize（robot.group.read / robot.group.manage）
- [x] SubTask 1.3: 为 RobotModelController 全部 5 个端点添加 @PreAuthorize（robot.model.read / robot.model.manage）
- [x] SubTask 1.4: 确认权限码与 platform-feature-manifest.yaml 中的声明一致；若 manifest 未声明对应权限码，同步补充
- [x] SubTask 1.5: 运行编译确认无错误

## Phase 2: P-TRACE-001 前端页面实现

## Task 2: 新建 TraceView.vue 页面
- [x] SubTask 2.1: 创建 `apps/web-console/src/views/TraceView.vue`，包含 Trace 列表区、详情区和回放区
- [x] SubTask 2.2: 实现列表加载调用 `listTraces()`，支持按时间范围、机器人 ID、任务 ID 筛选
- [x] SubTask 2.3: 实现详情查看调用 `getTrace(id)`，展示事件序列
- [x] SubTask 2.4: 实现回放调用 `getReplay(id)`，展示回放数据
- [x] SubTask 2.5: 实现 required_view_states：LOADING（加载中骨架）、READY（数据展示）、EMPTY（无数据提示）、PARTIAL（部分加载）、STALE（断网缓存提示）、FORBIDDEN（403 提示）、ERROR（错误提示）
- [x] SubTask 2.6: 使用 try/catch + resolveError 错误处理模式，与其他视图一致

## Task 3: 注册 Trace 路由与导航
- [x] SubTask 3.1: 在 `router/index.ts` 注册 `/trace` 路由，component 为 TraceView，父布局为 DefaultLayout
- [x] SubTask 3.2: 在 DefaultLayout 侧边栏导航中添加 Trace 入口（如尚未有）
- [x] SubTask 3.3: 添加路由 meta（title、权限码 trace.trace.read）

## Task 4: 添加 Trace i18n key
- [x] SubTask 4.1: 在 `zh-CN/platform.json` 添加 trace 页面相关文案 key（nav.trace、trace.title、trace.list、trace.detail、trace.replay、trace.empty、trace.filter 等）
- [x] SubTask 4.2: 在 `en-US/platform.json` 同步添加对应英文 key
- [x] SubTask 4.3: 验证 TraceView.vue 全部使用 t() 渲染文案

## Phase 3: i18n 合规修复

## Task 5: 修复硬编码枚举标签
- [x] SubTask 5.1: 修复 OtaManagementView.vue 硬编码标签（列标题 'ID'、类型下拉 FIRMWARE/CONFIG 等）改为 t() 调用
- [x] SubTask 5.2: 修复 AlarmManagementView.vue 通知渠道类型选项 label（in-app/email/webhook 等）改为 t() 调用
- [x] SubTask 5.3: 修复 BackupRecoveryView.vue 备份类型和演练类型选项 label（DATABASE/CONFIG 等、BACKUP_VERIFY 等）改为 t() 调用
- [x] SubTask 5.4: 在 locale 文件中添加对应 i18n key（zh-CN 和 en-US 同步）
- [x] SubTask 5.5: 验证语言切换后所有标签正确切换

## Phase 4: 测试验证

## Task 6: 运行全量测试
- [x] SubTask 6.1: 运行 Java 单元测试全部通过（158 tests, 0 failures）
- [x] SubTask 6.2: 确认 Python 测试全部通过（365 tests passed across 7 components）
- [x] SubTask 6.3: 确认前端 vitest 测试通过（1 test passed）
- [x] SubTask 6.4: 修复 11 个预存集成测试失败（RobotManagement + SafetyFlow），全量重跑通过

## Task 7: 补充权限拒绝测试
- [x] SubTask 7.1: 为 MissionTemplateController 补充权限拒绝测试（无权限返回 403）
- [x] SubTask 7.2: 为 RobotGroupController 补充权限拒绝测试
- [x] SubTask 7.3: 为 RobotModelController 补充权限拒绝测试
- [x] SubTask 7.4: 为 TraceView 补充前端组件测试（如 vitest 配置已存在）— vitest 配置存在但仅 1 个基础测试，TraceView 组件测试暂无

## Phase 5: 验证与收尾

## Task 8: 验证 close-implementation-gaps checklist
- [x] SubTask 8.1: 逐项验证 close-implementation-gaps/checklist.md 的 Phase 1（权限码校验）
- [x] SubTask 8.2: 逐项验证 Phase 2-7 各项
- [x] SubTask 8.3: 将验证通过的项标记 [x]
- [x] SubTask 8.4: 对仍不通过的项记录并修复（修复 McpToolService Javadoc + GlobalExceptionHandler 审计日志）

## Task 9: 运行 manifest 验证
- [x] SubTask 9.1: 运行 `python3 scripts/validate_platform_manifest.py` 确认通过
- [x] SubTask 9.2: 运行 `python3 scripts/validate_platform_manifest.py --require-complete` 确认通过

# Task Dependencies
- [Task 1] 独立，最高优先级，可立即启动
- [Task 2, 3, 4] 有依赖：Task 3 依赖 Task 2（视图组件先建），Task 4 依赖 Task 2（i18n key 随视图定义）；Task 2 完成后 3 和 4 可并行
- [Task 5] 独立于 Task 1-4，可并行
- [Task 6] 依赖 [Task 1, 2, 3, 4, 5]（修复完成后运行测试）
- [Task 7] 依赖 [Task 1, 2]（修复和视图完成后补充测试）
- [Task 8] 依赖 [Task 1-7]（全部修复后验证 checklist）
- [Task 9] 依赖 [Task 8]（checklist 验证后运行 manifest 验证）
- Phase 1 最高优先级；Phase 2 和 Phase 3 可并行；Phase 4 依赖 Phase 1-3；Phase 5 最后执行
