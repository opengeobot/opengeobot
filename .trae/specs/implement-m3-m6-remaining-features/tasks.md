# Tasks

## Task 1: ROS1 真机适配器（F-ADAPTER-002）
- [ ] SubTask 1.1: OpenAPI/AsyncAPI 契约 — 适配器注册、协议转换、状态上报端点
- [ ] SubTask 1.2: Python 适配器项目 — services/ros1-adapter/，ROS1 rospy 兼容层
- [ ] SubTask 1.3: Unitree 协议适配 — Unitree Go1/Go2 高级控制接口适配
- [ ] SubTask 1.4: 自研协议适配 — 通用自研协议适配层
- [ ] SubTask 1.5: Docker Compose 集成 — 仿真+真机双模式配置

## Task 2: 多机器人调度契约和迁移（F-FLEET-001）
- [ ] SubTask 2.1: OpenAPI 契约 — 集群调度、冲突检测、任务转移端点
- [ ] SubTask 2.2: AsyncAPI 契约 — fleet.conflict_detected.v1、fleet.failover.v1 事件
- [ ] SubTask 2.3: Flyway V17 迁移 — fleet.fleet_schedule、fleet.conflict_record、fleet.failover_event 表
- [ ] SubTask 2.4: SM-FLEET-001 调度状态机

## Task 3: 多机器人调度后端
- [ ] SubTask 3.1: 领域模型 — FleetSchedule、ConflictRecord、FailoverEvent
- [ ] SubTask 3.2: 调度引擎 — FleetScheduler（任务分配、路径规划、冲突检测）
- [ ] SubTask 3.3: 故障转移 — FailoverService（故障检测、任务转移、重新分配）
- [ ] SubTask 3.4: REST 控制器 — FleetController
- [ ] SubTask 3.5: 事件发布和审计

## Task 4: 告警系统契约和迁移（F-ALARM-001）
- [ ] SubTask 4.1: OpenAPI 契约 — 告警规则 CRUD、告警查询、确认/关闭端点
- [ ] SubTask 4.2: AsyncAPI 契约 — alarm.triggered.v1、alarm.acknowledged.v1、alarm.resolved.v1 事件
- [ ] SubTask 4.3: Flyway V18 迁移 — alarm.alarm_rule、alarm.alarm_event、alarm.notification_channel 表

## Task 5: 告警系统后端
- [ ] SubTask 5.1: 领域模型 — AlarmRule、AlarmEvent、NotificationChannel
- [ ] SubTask 5.2: AlarmService — 规则评估、告警触发、通知发送
- [ ] SubTask 5.3: 通知渠道 — EmailSender、WebhookSender、InAppNotifier
- [ ] SubTask 5.4: REST 控制器 — AlarmController
- [ ] SubTask 5.5: 事件发布和审计

## Task 6: 运维态势契约和后端（F-OPS-001）
- [ ] SubTask 6.1: OpenAPI 契约 — 健康面板、运维报表、容量预警端点
- [ ] SubTask 6.2: Flyway V19 迁移 — ops.metric_snapshot、ops.health_check 表
- [ ] SubTask 6.3: 后端实现 — OpsService（指标采集、健康检查、报表生成）
- [ ] SubTask 6.4: REST 控制器 — OpsController

## Task 7: OTA 发布契约和后端（F-OTA-001）
- [ ] SubTask 7.1: OpenAPI 契约 — 固件/技能包管理、发布、回滚端点
- [ ] SubTask 7.2: AsyncAPI 契约 — ota.release_started.v1、ota.completed.v1、ota.rollback.v1 事件
- [ ] SubTask 7.3: Flyway V20 迁移 — ota.firmware_package、ota.release_campaign、ota.deployment_record 表
- [ ] SubTask 7.4: 后端实现 — OtaService（包管理、灰度发布、回滚）
- [ ] SubTask 7.5: REST 控制器 — OtaController

## Task 8: 数据备份恢复契约和后端（F-RECOVERY-001）
- [ ] SubTask 8.1: OpenAPI 契约 — 备份管理、恢复操作、演练记录端点
- [ ] SubTask 8.2: Flyway V21 迁移 — recovery.backup_record、restore_record、drill_record 表
- [ ] SubTask 8.3: 后端实现 — BackupService（pg_dump、MinIO 备份）、RestoreService、DrillService
- [ ] SubTask 8.4: REST 控制器 — RecoveryController
- [ ] SubTask 8.5: 定时任务 — 每日备份、过期清理

## Task 9: 任务记忆契约和后端（F-MEMORY-001）
- [ ] SubTask 9.1: OpenAPI 契约 — 案例查询、改进建议、闭环反馈端点
- [ ] SubTask 9.2: AsyncAPI 契约 — memory.case_recorded.v1、memory.improvement_suggested.v1 事件
- [ ] SubTask 9.3: Flyway V22 迁移 — memory.task_case、memory.failure_case、memory.improvement_suggestion 表
- [ ] SubTask 9.4: 后端实现 — MemoryService（案例记录、相似检索、改进建议生成）
- [ ] SubTask 9.5: REST 控制器 — MemoryController

## Task 10: 前端页面
- [ ] SubTask 10.1: 调度管理页 — 集群视图/冲突列表/故障转移
- [ ] SubTask 10.2: 告警管理页 — 规则配置/告警列表/通知渠道
- [ ] SubTask 10.3: 运维面板页 — 健康指标/报表/容量预警
- [ ] SubTask 10.4: OTA 管理页 — 固件列表/发布活动/部署记录
- [ ] SubTask 10.5: 备份恢复页 — 备份列表/恢复操作/演练记录
- [ ] SubTask 10.6: 任务记忆页 — 案例库/改进建议/反馈

## Task 11: 测试和验证
- [ ] SubTask 11.1: Java 单元测试 — FleetScheduler、AlarmService、OtaService、MemoryService
- [ ] SubTask 11.2: C13-C17、C20-C22 验证 — 多机器人/告警/运维/OTA/备份/记忆
- [ ] SubTask 11.3: Docker Compose 全栈验证

## Task 12: 更新机器清单和证据
- [ ] SubTask 12.1: 更新 7 个功能状态为 DONE
- [ ] SubTask 12.2: 添加 evidence 路径
- [ ] SubTask 12.3: 验证 validate_platform_manifest.py 通过
- [ ] SubTask 12.4: 创建 Runbook 和安全报告

# Task Dependencies
- [Task 3] depends on [Task 2]
- [Task 5] depends on [Task 4]
- [Task 11] depends on [Task 1-10]
- [Task 12] depends on [Task 11]
- [Task 1, 2, 4, 6, 7, 8, 9] 可并行启动（契约先行）
- [Task 3, 5] 依赖各自契约完成后并行
- [Task 10] 依赖后端完成后并行
