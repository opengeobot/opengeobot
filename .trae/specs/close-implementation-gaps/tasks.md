# Tasks

## Phase 1: 安全红线修复（最高优先级）

## Task 1: platform-robot Controller 添加权限码校验
- [x] SubTask 1.1: 为 RobotController 添加 @PreAuthorize（robot.robot.read / robot.robot.register）
- [x] SubTask 1.2: 为 SkillController 添加 @PreAuthorize（skill.skill.read / skill.skill.manage / skill.skill.publish）
- [x] SubTask 1.3: 为 MissionController 添加 @PreAuthorize（mission.mission.read / mission.mission.create / mission.mission.pause / mission.mission.approve）
- [x] SubTask 1.4: 为 PolicyController 添加 @PreAuthorize（policy.policy.read / policy.policy.manage / policy.policy.publish）
- [x] SubTask 1.5: 为 SafetyController 添加 @PreAuthorize（safety.decision.read / safety.emergency_stop.execute / safety.emergency_stop.reset）
- [x] SubTask 1.6: 为 FleetController 添加 @PreAuthorize（fleet.schedule.read / fleet.schedule.manage）
- [x] SubTask 1.7: 为 MapController 添加 @PreAuthorize（map.map.read / map.map.manage / map.scene.manage / map.restricted_area.manage）
- [x] SubTask 1.8: 为 MonitorController 添加 @PreAuthorize（monitor.fleet.view / monitor.robot.view / robot.robot.control）
- [x] SubTask 1.9: 为 MediaController 添加 @PreAuthorize（media.asset.read / media.asset.upload / media.asset.download / media.asset.delete）
- [x] SubTask 1.10: 为 TraceController 添加 @PreAuthorize（trace.trace.read / trace.trace.replay）
- [x] SubTask 1.11: 为 McpToolController 添加 @PreAuthorize（mcp.tool.read / mcp.tool.manage / mcp.tool.invoke）
- [x] SubTask 1.12: 为 AlarmController 添加 @PreAuthorize（ops.alarm.read / ops.alarm.manage / ops.alarm.suppress）
- [x] SubTask 1.13: 为 OpsController 添加 @PreAuthorize（ops.health.read / dashboard.view）
- [x] SubTask 1.14: 为 OtaController 添加 @PreAuthorize（ops.ota.read / ops.ota.manage / ops.ota.approve）
- [x] SubTask 1.15: 为 RecoveryController 添加 @PreAuthorize（ops.backup.read / ops.backup.manage / ops.restore.execute）
- [x] SubTask 1.16: 为 MemoryController 添加 @PreAuthorize（memory.memory.read / memory.failure_case.read / memory.failure_case.manage / memory.improvement.manage / memory.improvement.approve）
- [x] SubTask 1.17: 验证权限拒绝返回 403 且有审计记录

## Task 2: platform-governance Controller 添加权限码校验
- [x] SubTask 2.1: 为 DictController 添加 @PreAuthorize（platform.dictionary.read / platform.dictionary.manage）
- [x] SubTask 2.2: 为 I18nController 添加 @PreAuthorize（platform.i18n.read / platform.i18n.manage）
- [x] SubTask 2.3: 为 ConfigController 添加 @PreAuthorize（platform.config.read / platform.config.manage）
- [x] SubTask 2.4: 为 AuditController 添加 @PreAuthorize（audit.audit.read / audit.audit.export）
- [x] SubTask 2.5: 为 ExportController 添加 @PreAuthorize（audit.audit.export）

## Phase 2: Stub/Mock 替换

## Task 3: 实现 Outbox Relay 中继到 NATS
- [x] SubTask 3.1: 在 platform-common 中实现 OutboxRelay 组件（定时轮询未发布事件、发布到 NATS JetStream、标记已发布）
- [x] SubTask 3.2: 配置 NATS JetStream 连接和 Stream/Consumer
- [x] SubTask 3.3: 实现失败重试和重连恢复逻辑
- [x] SubTask 3.4: 添加 OutboxRelay 单元测试和集成测试

## Task 4: OpsService 真实健康检查
- [x] SubTask 4.1: 实现 NATS 连通性探测（ping 或 health check 请求）
- [x] SubTask 4.2: 实现 MinIO 连通性探测（bucket list 或 head request）
- [x] SubTask 4.3: 移除 checkStub() 方法，替换为真实探测
- [x] SubTask 4.4: 添加健康检查单元测试

## Task 5: McpToolService 真实工具调用
- [x] SubTask 5.1: 实现 McpToolService 通过 MCP 网关转发工具调用（或通过已注册的 Skill 执行器）
- [x] SubTask 5.2: 移除 executeSimulated() 方法
- [x] SubTask 5.3: 保留 canary 路由逻辑，真实路由到 canary/stable 执行器
- [x] SubTask 5.4: 添加工具调用单元测试和集成测试

## Task 6: OtaService 真实部署逻辑
- [x] SubTask 6.1: 实现 OTA 通过边缘网关下发固件包到目标机器人
- [x] SubTask 6.2: 实现部署进度跟踪和真实成功/失败状态
- [x] SubTask 6.3: 移除"始终成功"的模拟逻辑
- [x] SubTask 6.4: 添加 OTA 部署单元测试（成功、失败、超时场景）

## Task 7: NotificationService 真实通知发送
- [x] SubTask 7.1: 实现 email 通知渠道（SMTP 或邮件服务 API）
- [x] SubTask 7.2: 实现 webhook 通知渠道
- [x] SubTask 7.3: 实现应用内通知渠道
- [x] SubTask 7.4: 移除 stub 标记，通知状态如实反映发送结果
- [x] SubTask 7.5: 添加通知发送单元测试

## Phase 3: 缺失组件实现

## Task 8: 实现 edge/safety-gateway/
- [x] SubTask 8.1: 创建 Python 项目结构（pyproject.toml、src 目录）
- [x] SubTask 8.2: 实现急停锁存逻辑（触发后阻止所有技能执行、必须显式复位）
- [x] SubTask 8.3: 实现动作级安全校验（禁区检查、速度限制、碰撞风险评估）
- [x] SubTask 8.4: 实现 NATS 订阅安全指令、发布安全状态
- [x] SubTask 8.5: 实现本地急停不依赖云端或网络
- [x] SubTask 8.6: 添加单元测试

## Task 9: 实现 edge/local-skill-executor/
- [x] SubTask 9.1: 创建 Python 项目结构（pyproject.toml、src 目录）
- [x] SubTask 9.2: 实现技能调用接收和分发（通过 Safety Gateway 校验后执行）
- [x] SubTask 9.3: 实现适配器调用接口（转发到 ROS2/ROS1/仿真适配器）
- [x] SubTask 9.4: 实现执行结果回传和审计
- [x] SubTask 9.5: 添加单元测试

## Task 10: 实现 services/agent-runtime/（QwenPaw 适配器）
- [x] SubTask 10.1: 创建 Python 项目结构（src 目录、pyproject.toml 添加真实依赖）
- [x] SubTask 10.2: 实现 AgentRuntimeProvider 接口适配 QwenPaw
- [x] SubTask 10.3: 实现意图理解、计划生成提案输出
- [x] SubTask 10.4: 确保 Agent 输出为不可信提案，经 Schema/权限/状态机校验
- [x] SubTask 10.5: 添加单元测试

## Task 11: 实现 services/mcp-tool-gateway/
- [x] SubTask 11.1: 创建 Python 项目结构（src 目录、pyproject.toml）
- [x] SubTask 11.2: 实现 MCP 工具协议处理和工具注册
- [x] SubTask 11.3: 实现工具调用路由（canary/stable）和执行日志
- [x] SubTask 11.4: 实现 NATS 通信与云端 McpToolService 对接
- [x] SubTask 11.5: 添加单元测试

## Phase 4: 前端修复

## Task 12: 修复 DefaultLayout 接入路由
- [x] SubTask 12.1: 修改 router/index.ts，为认证路由添加 DefaultLayout 作为父布局
- [x] SubTask 12.2: 确保 App.vue 或路由配置正确渲染 DefaultLayout 包裹的子路由
- [x] SubTask 12.3: 验证导航栏、顶栏、退出、面包屑、语言切换器正常显示
- [x] SubTask 12.4: 验证 AppOfflineIndicator 组件在布局内渲染

## Task 13: 修复 DashboardView 真实数据
- [x] SubTask 13.1: 移除硬编码占位值
- [x] SubTask 13.2: 调用真实 API 获取仪表盘统计（机器人数量、任务状态、告警统计、安全状态）
- [x] SubTask 13.3: 或将 /dashboard 路由重定向到 OpsDashboardView
- [x] SubTask 13.4: 添加加载状态和错误处理

## Task 14: 修复前端错误处理
- [x] SubTask 14.1: 为 FleetManagementView 添加 catch 块和本地化错误提示
- [x] SubTask 14.2: 为 AlarmManagementView 添加 catch 块和本地化错误提示
- [x] SubTask 14.3: 为 OpsDashboardView 添加 catch 块和本地化错误提示
- [x] SubTask 14.4: 为 OtaManagementView 添加 catch 块和本地化错误提示
- [x] SubTask 14.5: 为 BackupRecoveryView 添加 catch 块和本地化错误提示
- [x] SubTask 14.6: 为 TaskMemoryView 添加 catch 块和本地化错误提示

## Phase 5: 测试覆盖补齐

## Task 15: platform-robot Java 单元测试
- [x] SubTask 15.1: SafetyService 单元测试（急停、复位、安全检查、状态机转换）
- [x] SubTask 15.2: MissionService 单元测试（创建、规划、执行、暂停、取消、审批、状态机）
- [x] SubTask 15.3: RobotService 单元测试（CRUD、状态更新、能力管理）
- [x] SubTask 15.4: SkillService 单元测试（CRUD、版本管理、发布）
- [x] SubTask 15.5: PolicyService 单元测试（CRUD、版本发布、策略评估）
- [x] SubTask 15.6: FleetService 单元测试（调度、冲突检测、故障转移）
- [x] SubTask 15.7: MonitorService 单元测试（快照、WebSocket、人工接管）
- [x] SubTask 15.8: MediaService 单元测试（上传、下载、删除）
- [x] SubTask 15.9: TraceService 单元测试（追踪查询、事实回放）
- [x] SubTask 15.10: MemoryService 单元测试（案例记录、相似检索、改进建议）

## Task 16: platform-common Java 单元测试
- [x] SubTask 16.1: OutboxRepository 单元测试
- [x] SubTask 16.2: AuditService 单元测试
- [x] SubTask 16.3: PublicIdGenerator 单元测试
- [x] SubTask 16.4: ClockProvider 单元测试
- [x] SubTask 16.5: ErrorEnvelope/ProblemDetails 单元测试

## Task 17: Java 集成测试
- [x] SubTask 17.1: 认证端到端集成测试（登录、刷新、登出、资料）
- [x] SubTask 17.2: 权限校验集成测试（有权限/无权限访问各端点）
- [x] SubTask 17.3: 机器人管理端到端测试（注册、状态、能力）
- [x] SubTask 17.4: 任务全生命周期端到端测试（创建、规划、审批、执行、完成）
- [x] SubTask 17.5: 安全流程端到端测试（急停、复位、恢复）
- [x] SubTask 17.6: OTA 部署端到端测试（创建发布、部署、回滚）

## Task 18: Python 测试
- [x] SubTask 18.1: edge/gateway 单元测试（命令处理、NATS 客户端、离线缓存、重连对账）
- [x] SubTask 18.2: edge/safety-gateway 单元测试（急停锁存、安全校验）
- [x] SubTask 18.3: edge/local-skill-executor 单元测试（技能分发、结果回传）
- [x] SubTask 18.4: services/sim-adapter 单元测试（技能执行、参数校验）
- [x] SubTask 18.5: services/ros1-adapter 单元测试（协议转换、Unitree 适配）
- [x] SubTask 18.6: services/agent-runtime 单元测试（QwenPaw 适配）
- [x] SubTask 18.7: services/mcp-tool-gateway 单元测试（工具路由、调用日志）

## Task 19: 修改 dev.sh/dev.ps1 test 子命令
- [x] SubTask 19.1: 在 cmd_test() 中添加 Python pytest 执行（edge/gateway、sim-adapter、ros1-adapter、agent-runtime、mcp-tool-gateway）
- [x] SubTask 19.2: 在 dev.ps1 Invoke-Test 中同步添加 Python 测试
- [x] SubTask 19.3: 验证 `./scripts/dev.sh test` 同时运行 Java 和 Python 测试

## Phase 6: 脚本和验证修复

## Task 20: 实现 sim-up 子命令
- [x] SubTask 20.1: 在 dev.sh 中实现 cmd_sim_up() 启动仿真适配器和相关服务
- [x] SubTask 20.2: 在 dev.ps1 中实现 Invoke-SimUp 同步逻辑
- [x] SubTask 20.3: 在 compose.yml 中添加仿真 profile 服务定义
- [x] SubTask 20.4: 验证 `./scripts/dev.sh sim-up` 启动仿真栈

## Task 21: 增强 validate_platform_manifest.py
- [x] SubTask 21.1: 安装 jsonschema 依赖（或添加到 requirements）
- [x] SubTask 21.2: 增强证据验证逻辑：检查报告文件内容是否为真实测试输出（非纯模板）
- [x] SubTask 21.3: 检查测试文件是否存在（对应 required_tests 声明）
- [x] SubTask 21.4: 验证 `python3 scripts/validate_platform_manifest.py` 通过

## Phase 7: 报告和证据修复

## Task 22: 修复报告真实性
- [x] SubTask 22.1: 将 reports/tests/ 下的 HTML 报告替换为真实测试运行输出
- [x] SubTask 22.2: 将 reports/security/ 下的安全清单替换为真实安全检查结果
- [x] SubTask 22.3: 将 reports/deployment/ 下的部署摘要替换为真实部署记录
- [x] SubTask 22.4: 将 reports/observability/ 下的可观测性摘要替换为真实配置验证
- [x] SubTask 22.5: HIL 报告如实标注未执行，不得在清单 required_tests 中声称 HIL 已完成

## Task 23: 更新机器清单状态
- [x] SubTask 23.1: 将不满足完成标准的功能从 DONE 回退为 IN_PROGRESS
- [x] SubTask 23.2: 修复完成后逐个验证并更新为 DONE
- [x] SubTask 23.3: 验证 validate_platform_manifest.py 通过

# Task Dependencies
- [Task 2] 可与 [Task 1] 并行（不同模块）
- [Task 3] 独立（platform-common 新增组件）
- [Task 4, 5, 6, 7] 依赖 [Task 3] 可并行（都涉及 platform-robot service 修改，Outbox Relay 先行）
- [Task 8, 9] 可并行（edge 新组件，互相独立但 9 依赖 8 的安全校验）
- [Task 9] depends on [Task 8]
- [Task 10, 11] 可并行（services 新组件）
- [Task 12, 13, 14] 可并行（前端不同文件）
- [Task 15, 16] 依赖 [Task 1, 2, 3, 4, 5, 6, 7]（先修复再测试）
- [Task 17] 依赖 [Task 15, 16]
- [Task 18] 依赖 [Task 8, 9, 10, 11]（先实现再测试）
- [Task 19] 依赖 [Task 15, 16, 18]（测试存在后修改脚本）
- [Task 20] 独立
- [Task 21] 依赖 [Task 22, 23]（报告和清单更新后增强验证）
- [Task 22] 依赖 [Task 15, 16, 17, 18]（测试运行后生成真实报告）
- [Task 23] 依赖 [Task 1-22 全部完成]
- Phase 1（Task 1-2）最高优先级，可立即并行启动
- Phase 2（Task 3-7）第二优先级
- Phase 3（Task 8-11）可与 Phase 2 部分并行
- Phase 4（Task 12-14）可与 Phase 2/3 并行
- Phase 5（Task 15-19）依赖 Phase 1-3 完成
- Phase 6（Task 20-21）可与 Phase 5 部分并行
- Phase 7（Task 22-23）最后执行
