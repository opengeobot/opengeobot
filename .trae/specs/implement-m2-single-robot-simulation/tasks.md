# Tasks

## Task 1: 机器人注册契约和迁移
- [x] SubTask 1.1: OpenAPI 契约 — 机器人 CRUD、状态查询、能力声明端点
- [x] SubTask 1.2: AsyncAPI 契约 — robot.registered.v1、robot.status_changed.v1 事件
- [x] SubTask 1.3: Flyway V7 迁移 — robot_registry.robot、robot_registry.robot_capability、robot_registry.robot_status_history 表
- [x] SubTask 1.4: 种子数据 — 默认机器人型号和示例机器人

## Task 2: 机器人注册后端实现
- [x] SubTask 2.1: 领域模型 — Robot、RobotCapability、RobotStatusHistory 实体
- [x] SubTask 2.2: 仓储层 — RobotRepository、RobotCapabilityRepository
- [x] SubTask 2.3: 应用服务 — RobotService（CRUD、状态更新、能力管理）
- [x] SubTask 2.4: REST 控制器 — RobotController
- [x] SubTask 2.5: 事件发布 — robot.registered.v1、robot.status_changed.v1 Outbox 事件
- [x] SubTask 2.6: 审计记录 — 机器人注册/状态变更审计

## Task 3: 机器人型号与分组后端
- [x] SubTask 3.1: Flyway V8 迁移 — robot_registry.robot_model、robot_registry.robot_group、robot_registry.robot_group_member 表
- [x] SubTask 3.2: 领域模型和服务 — RobotModel、RobotGroup CRUD
- [x] SubTask 3.3: REST 控制器 — RobotModelController、RobotGroupController

## Task 4: 技能注册契约和后端
- [x] SubTask 4.1: OpenAPI 契约 — 技能 CRUD、版本管理、启用/禁用端点
- [x] SubTask 4.2: AsyncAPI 契约 — skill.registered.v1、skill.status_changed.v1 事件
- [x] SubTask 4.3: Flyway V9 迁移 — skill_registry.skill、skill_registry.skill_version 表
- [x] SubTask 4.4: 后端实现 — SkillService、SkillController、领域模型、仓储

## Task 5: MCP 工具网关契约和后端
- [x] SubTask 5.1: OpenAPI 契约 — MCP 工具注册、查询、调用端点
- [x] SubTask 5.2: Flyway V10 迁移 — skill_registry.mcp_tool、skill_registry.mcp_invocation_log 表
- [x] SubTask 5.3: 后端实现 — McpToolService、McpToolController、调用日志

## Task 6: 任务管理契约和迁移
- [x] SubTask 6.1: OpenAPI 契约 — 任务 CRUD、规划、执行控制、模板端点
- [x] SubTask 6.2: AsyncAPI 契约 — mission.created.v1、mission.plan_revised.v1、mission.started.v1、mission.completed.v1、mission.failed.v1、mission.cancelled.v1 事件
- [x] SubTask 6.3: Flyway V11 迁移 — mission.mission、mission.mission_step、mission.mission_template、mission.mission_approval 表
- [x] SubTask 6.4: SM-MISSION-001 任务状态机（PENDING/PLANNING/READY/EXECUTING/PAUSED/COMPLETED/FAILED/CANCELLED）

## Task 7: 任务管理后端实现
- [x] SubTask 7.1: 领域模型 — Mission、MissionStep、MissionTemplate、MissionApproval
- [x] SubTask 7.2: 仓储层 — MissionRepository、MissionTemplateRepository
- [x] SubTask 7.3: 应用服务 — MissionService（创建、规划、执行、暂停、取消）
- [x] SubTask 7.4: REST 控制器 — MissionController、MissionTemplateController
- [x] SubTask 7.5: 事件发布 — 任务生命周期 Outbox 事件
- [x] SubTask 7.6: 审计记录 — 任务创建/执行/完成审计

## Task 8: 策略管理契约和后端
- [x] SubTask 8.1: OpenAPI 契约 — 策略 CRUD、版本发布、任务评估端点
- [x] SubTask 8.2: Flyway V12 迁移 — policy.policy、policy.policy_rule、policy.policy_assignment 表
- [x] SubTask 8.3: 后端实现 — PolicyService、PolicyController
- [x] SubTask 8.4: 策略评估 — 任务创建时检查策略规则

## Task 9: 安全拦截契约和后端
- [x] SubTask 9.1: OpenAPI 契约 — 紧急停止、安全状态查询、重置端点
- [x] SubTask 9.2: AsyncAPI 契约 — safety.emergency_stop.v1、safety.reset.v1 事件
- [x] SubTask 9.3: Flyway V13 迁移 — policy.safety_state、policy.safety_event 表
- [x] SubTask 9.4: 后端实现 — SafetyService（紧急停止、重置、安全检查）、SafetyController
- [x] SubTask 9.5: 安全拦截器 — 任务执行前安全检查（禁区、速度限制、碰撞风险）

## Task 10: 边缘网关骨架
- [x] SubTask 10.1: 边缘网关 Python 项目结构 — edge/gateway/
- [x] SubTask 10.2: NATS 连接管理 — 订阅云端指令、发布状态更新
- [x] SubTask 10.3: 边缘命令处理器 — 接收任务执行指令、调用本地执行器
- [x] SubTask 10.4: 离线缓存与重连对账 — 本地状态缓存、重连后同步

## Task 11: ROS2 仿真适配器骨架
- [x] SubTask 11.1: 仿真适配器 Python 项目结构 — services/sim-adapter/
- [x] SubTask 11.2: 基础技能实现 — stand_up、stop、move_forward（受限）、capture_image、emergency_stop
- [x] SubTask 11.3: 适配器接口 — 接收技能调用、返回执行结果
- [x] SubTask 11.4: 仿真环境配置 — Docker Compose 仿真服务

## Task 12: 地图场景契约和后端
- [x] SubTask 12.1: OpenAPI 契约 — 地图/场景/区域/禁区 CRUD 端点
- [x] SubTask 12.2: Flyway V14 迁移 — map_scene.map、map_scene.scene、map_scene.area、map_scene.restricted_area 表
- [x] SubTask 12.3: 后端实现 — MapService、MapController

## Task 13: 实时监控后端
- [x] SubTask 13.1: OpenAPI 契约 — 监控数据查询、人工接管端点
- [x] SubTask 13.2: WebSocket 推送 — 机器人状态、任务进度实时推送
- [x] SubTask 13.3: 后端实现 — MonitorService、MonitorController

## Task 14: 媒体对象后端
- [x] SubTask 14.1: OpenAPI 契约 — 媒体上传/查看/删除端点
- [x] SubTask 14.2: Flyway V15 迁移 — media.media_object 表
- [x] SubTask 14.3: 后端实现 — MediaService（MinIO 上传/下载）、MediaController

## Task 15: 全链路追踪后端
- [x] SubTask 15.1: OpenAPI 契约 — 追踪查询、事实回放端点
- [x] SubTask 15.2: Flyway V16 迁移 — trace.trace_span、trace.fact_event 表
- [x] SubTask 15.3: 后端实现 — TraceService（追踪查询、事实回放）、TraceController

## Task 16: 前端页面实现
- [x] SubTask 16.1: 机器人管理页 — 列表/详情/注册/状态/能力
- [x] SubTask 16.2: 技能管理页 — 列表/版本/启用禁用
- [x] SubTask 16.3: 任务管理页 — 创建/列表/详情/执行控制/模板
- [x] SubTask 16.4: 策略管理页 — 列表/编辑/发布
- [x] SubTask 16.5: 安全控制页 — 紧急停止/状态/重置
- [x] SubTask 16.6: 地图场景页 — 地图列表/区域管理/禁区
- [x] SubTask 16.7: 实时监控页 — 机器人状态/任务进度/WebSocket
- [x] SubTask 16.8: 媒体库页 — 上传/查看/删除

## Task 17: 单机器人仿真闭环验证
- [x] SubTask 17.1: 注册机器人 → 注册技能 → 创建任务 → 安全检查 → 执行 → 完成
- [x] SubTask 17.2: 紧急停止 → 重置 → 恢复执行
- [x] SubTask 17.3: 离线缓存 → 重连对账
- [x] SubTask 17.4: 全链路追踪 → 事实回放

## Task 18: 测试和验证
- [x] SubTask 18.1: Java 单元测试 — RobotService、MissionService、SafetyService、PolicyService
- [x] SubTask 18.2: Java 集成测试 — 机器人/任务/安全端到端
- [x] SubTask 18.3: C03-C12 验证 — 单机器人范围全部通过
- [x] SubTask 18.4: Docker Compose 全栈验证

## Task 19: 更新机器清单和证据
- [x] SubTask 19.1: 更新 F-ROBOT-001~002、F-SKILL-001、F-MCP-001、F-MISSION-001~003、F-POLICY-001、F-SAFETY-001、F-EDGE-001~002、F-ADAPTER-001、F-MAP-001、F-MONITOR-001、F-MEDIA-001、F-TRACE-001 状态为 DONE
- [x] SubTask 19.2: 添加 evidence 路径
- [x] SubTask 19.3: 验证 validate_platform_manifest.py 通过
- [x] SubTask 19.4: 创建 M2 Runbook 和安全报告

# Task Dependencies
- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 2]
- [Task 4] depends on [Task 1] (契约先行)
- [Task 5] depends on [Task 4]
- [Task 6] depends on [Task 1]
- [Task 7] depends on [Task 6, Task 2] (复用机器人)
- [Task 8] depends on [Task 6]
- [Task 9] depends on [Task 7]
- [Task 10] depends on [Task 2] (边缘需要机器人注册)
- [Task 11] depends on [Task 4, Task 10] (仿真需要技能和边缘)
- [Task 12] depends on [Task 1]
- [Task 13] depends on [Task 2, Task 7]
- [Task 14] depends on [Task 1]
- [Task 15] depends on [Task 7]
- [Task 16] depends on [Task 2, 4, 7, 8, 9, 12, 13, 14]
- [Task 17] depends on [Task 11, 16]
- [Task 18] depends on [Task 17]
- [Task 19] depends on [Task 18]
- [Task 1, 4, 6, 12, 14] 可并行启动（契约先行）
- [Task 3, 5, 8, 15] 可与主链并行
