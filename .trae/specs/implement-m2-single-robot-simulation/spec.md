# M2 单机器人仿真闭环 Spec

## Why

M0 工程底座和 M1 平台公共能力均已完成（DONE=6）。根据 AI 开发约束第 18 节，M2 必须交付单机器人仿真闭环：机器人/技能注册、MCP 工具网关、任务创建/规划/执行/模板、策略版本、安全拦截与紧急停止、边缘网关连接与本地执行、ROS2 仿真适配器、地图场景、实时监控、媒体对象、全链路追踪。M2 退出条件是 C03-C12、C18、C19 单机器人范围通过。

## What Changes

### 第一批：机器人注册与技能管理
- **F-ROBOT-001**: 机器人注册、能力声明与多维状态（在线/离线/忙碌/故障/维护）
- **F-ROBOT-002**: 机器人型号、分组、绑定与维护诊断
- **F-SKILL-001**: 能力与技能生命周期管理（声明/注册/版本/启用/禁用）
- **F-MCP-001**: MCP 工具注册、灰度发布与调用

### 第二批：任务管理与策略
- **F-MISSION-001**: 任务创建、规划与计划修订
- **F-MISSION-002**: 任务模板与审批
- **F-MISSION-003**: 任务执行控制与终态（执行/暂停/完成/失败/取消）
- **F-POLICY-001**: 策略版本、发布与任务评估

### 第三批：安全、边缘与仿真
- **F-SAFETY-001**: 动作安全、紧急停止与重置
- **F-EDGE-001**: 边缘网关身份、连接与证书
- **F-EDGE-002**: 本地执行、离线缓存与重连对账
- **F-ADAPTER-001**: ROS2 仿真与主路径适配器

### 第四批：地图、监控、媒体与追踪
- **F-MAP-001**: 地图、场景、区域与禁区版本管理
- **F-MONITOR-001**: 实时态势感知与人工接管
- **F-MEDIA-001**: 媒体对象上传、查看与保留
- **F-TRACE-001**: 全链路追踪与事实回放

## Impact

- Affected specs: F-ROBOT-001, F-ROBOT-002, F-SKILL-001, F-MCP-001, F-MISSION-001~003, F-POLICY-001, F-SAFETY-001, F-EDGE-001~002, F-ADAPTER-001, F-MAP-001, F-MONITOR-001, F-MEDIA-001, F-TRACE-001
- Affected code:
  - apps/cloud-control/ (新增 robot_registry, skill_registry, mission, fleet, policy, safety, map_scene, media, trace 模块)
  - edge/ (边缘网关 Python 运行时)
  - services/ (MCP 工具网关, ROS2 仿真适配器)
  - apps/web-console/ (新增机器人管理、任务管理、策略管理、安全控制、地图、监控页面)
  - contracts/ (新增 OpenAPI/AsyncAPI 契约)
- 数据库迁移: V7-V16 (各模块表结构)

## ADDED Requirements

### Requirement: 机器人注册与状态管理（F-ROBOT-001）
系统必须支持机器人注册，声明能力，维护多维状态。

#### Scenario: 注册机器人
- **WHEN** 管理员注册新机器人（名称、型号、能力声明）
- **THEN** 机器人创建成功，发布 robot.registered.v1 事件，审计记录

#### Scenario: 状态变更
- **WHEN** 机器人状态从在线变为忙碌
- **THEN** 发布 robot.status_changed.v1 事件，实时监控更新

### Requirement: 能力与技能管理（F-SKILL-001）
系统必须支持技能声明、版本管理和生命周期控制。

#### Scenario: 注册技能
- **WHEN** 管理员注册新技能（名称、版本、输入输出声明）
- **THEN** 技能创建成功，发布 skill.registered.v1 事件

### Requirement: 任务创建与执行（F-MISSION-001/003）
系统必须支持任务创建、规划、执行控制和终态管理。

#### Scenario: 创建并执行任务
- **WHEN** 用户创建任务并分配给机器人，任务通过安全检查后执行
- **THEN** 任务按计划执行，状态流转可追踪，完成后发布 mission.completed.v1 事件

### Requirement: 安全拦截与紧急停止（F-SAFETY-001）
系统必须提供动作安全检查和紧急停止能力。

#### Scenario: 紧急停止
- **WHEN** 安全系统或用户触发紧急停止
- **THEN** 所有运动立即停止，任务暂停，发布 safety.emergency_stop.v1 事件

### Requirement: 边缘网关连接（F-EDGE-001）
系统必须支持边缘网关通过 NATS 与云端通信。

#### Scenario: 边缘上线
- **WHEN** 边缘网关启动并连接云端
- **THEN** 建立双向通信通道，同步机器人状态，发布 edge.connected.v1 事件

### Requirement: ROS2 仿真适配器（F-ADAPTER-001）
系统必须提供 ROS2 仿真环境适配器，支持基础技能执行。

#### Scenario: 仿真执行
- **WHEN** 任务在仿真环境中执行 stand_up 技能
- **THEN** 适配器调用 ROS2 服务，返回执行结果，记录执行轨迹

## MODIFIED Requirements

### Requirement: 机器清单状态
F-ROBOT-001、F-SKILL-001、F-MISSION-001~003、F-POLICY-001、F-SAFETY-001、F-EDGE-001~002、F-ADAPTER-001、F-MAP-001、F-MONITOR-001、F-MEDIA-001、F-TRACE-001 的 `implementation_status` 从 `NOT_STARTED` 改为 `IN_PROGRESS`，验证通过后改为 `DONE`。
