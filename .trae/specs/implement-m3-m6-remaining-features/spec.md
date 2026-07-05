# M3-M6 剩余功能实现 Spec

## Why

M0-M2 已完成（DONE=22/29）。根据机器清单，剩余 7 个 NOT_STARTED 功能跨 M3-M6：ROS1 适配器、多机器人调度、告警通知、运维态势、OTA 发布、数据备份恢复、任务记忆闭环。完成这些功能后，系统将具备生产级运维能力，所有 29 个功能全部 DONE。

## What Changes

### M3: 真机适配
- **F-ADAPTER-002**: ROS1 Unitree 与自研协议兼容适配器 — 支持 ROS1 机器人接入，Unitree Go1/Go2 协议兼容，自研协议适配层

### M4: 多机器人编队
- **F-FLEET-001**: 多机器人调度冲突与故障转移 — 集群调度器、冲突检测（时空冲突）、任务转移、故障机器人重新分配

### M5: 运维与可靠性
- **F-ALARM-001**: 告警生命周期与通知 — 告警规则、阈值触发、通知渠道（邮件/Webhook/站内信）、告警确认/关闭
- **F-OPS-001**: 指标日志健康与运维态势 — 系统指标采集、健康面板、运维报表、容量预警
- **F-OTA-001**: OTA 发布分批部署与回滚 — 机器人固件/技能包 OTA、灰度发布、回滚机制
- **F-RECOVERY-001**: 数据备份恢复与灾难演练 — 数据库备份、MinIO 备份、恢复流程、定期演练

### M6: 自学习与改进
- **F-MEMORY-001**: 任务记忆失败案例与改进闭环 — 任务执行结果记忆、失败案例库、自动改进建议、闭环反馈

## Impact

- Affected specs: F-ADAPTER-002, F-FLEET-001, F-ALARM-001, F-OPS-001, F-OTA-001, F-RECOVERY-001, F-MEMORY-001
- Affected code:
  - apps/cloud-control/ (新增 fleet, alarm, ops, ota, recovery, memory 模块)
  - services/ (ROS1 适配器 Python 项目)
  - apps/web-console/ (新增调度管理、告警、运维、OTA、备份恢复页面)
  - contracts/ (新增 OpenAPI/AsyncAPI 契约)
  - 数据库迁移: V17-V23

## ADDED Requirements

### Requirement: ROS1 真机适配器（F-ADAPTER-002）
系统必须提供 ROS1 兼容适配器，支持 Unitree 机器人和自研协议机器人接入。

#### Scenario: ROS1 机器人接入
- **WHEN** ROS1 机器人通过适配器连接系统
- **THEN** 适配器转换为统一技能接口，机器人注册到平台，可接收任务

### Requirement: 多机器人调度（F-FLEET-001）
系统必须支持多机器人集群调度，检测时空冲突，实现故障转移。

#### Scenario: 调度冲突检测
- **WHEN** 两个机器人的任务路径在时空上冲突
- **THEN** 调度器检测冲突，调整任务顺序或重新分配，发布 fleet.conflict_detected.v1 事件

#### Scenario: 故障转移
- **WHEN** 执行中机器人发生故障
- **THEN** 系统暂停该机器人任务，将任务转移到可用机器人，发布 fleet.failover.v1 事件

### Requirement: 告警通知（F-ALARM-001）
系统必须支持告警规则、阈值触发和多渠道通知。

#### Scenario: 告警触发
- **WHEN** 机器人离线超过 5 分钟或任务失败率超过阈值
- **THEN** 创建告警，发送通知到配置的渠道，发布 alarm.triggered.v1 事件

### Requirement: 运维态势（F-OPS-001）
系统必须采集运维指标，提供健康面板和运维报表。

#### Scenario: 健康检查
- **WHEN** 管理员查看运维面板
- **THEN** 显示系统健康状态（CPU/内存/磁盘/网络）、机器人在线率、任务成功率、告警统计

### Requirement: OTA 发布（F-OTA-001）
系统必须支持机器人固件和技能包的 OTA 升级、灰度发布和回滚。

#### Scenario: 灰度发布
- **WHEN** 管理员发布新固件版本到 10% 机器人
- **THEN** 系统选择目标机器人、推送固件、监控升级结果、支持自动回滚

### Requirement: 数据备份恢复（F-RECOVERY-001）
系统必须支持数据库和对象存储的定期备份和恢复。

#### Scenario: 数据库备份
- **WHEN** 到达每日备份时间（凌晨 2:00）
- **THEN** 系统自动执行 PostgreSQL pg_dump、上传到 MinIO、记录备份元数据、清理过期备份

### Requirement: 任务记忆闭环（F-MEMORY-001）
系统必须记录任务执行结果，构建失败案例库，提供改进建议。

#### Scenario: 失败案例学习
- **WHEN** 任务执行失败
- **THEN** 系统记录失败原因和环境上下文，关联相似案例，生成改进建议，发布 memory.case_recorded.v1 事件

## MODIFIED Requirements

### Requirement: 机器清单状态
F-ADAPTER-002、F-FLEET-001、F-ALARM-001、F-OPS-001、F-OTA-001、F-RECOVERY-001、F-MEMORY-001 的 `implementation_status` 从 `NOT_STARTED` 改为 `IN_PROGRESS`，验证通过后改为 `DONE`。
