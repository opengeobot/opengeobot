# Tasks

## 阶段 1：关键基础修复（阻断性缺陷）

- [x] Task 1: 修复权限码缺失
  - [x] SubTask 1.1: V26 migration 添加 4 个权限码
  - [x] SubTask 1.2: 分配给 SYS_ADMIN 和 OPERATOR 角色
  - [x] SubTask 1.3: 验证权限码生效（POST /api/v1/missions 返回 201）

- [x] Task 2: 修复 QwenPaw API 调用
  - [x] SubTask 2.1: model 字段从配置读取
  - [x] SubTask 2.2: initializer 添加 active_model
  - [x] SubTask 2.3: system prompt 注入技能 ID 和 schema
  - [x] SubTask 2.4: strip markdown code fences
  - [x] SubTask 2.5: config 新增 model 配置项
  - [x] SubTask 2.6: compose 添加 QWENPAW_MODEL 等环境变量
  - [x] SubTask 2.7: 50 个测试通过

- [x] Task 3: 修复 E2E 测试
  - [x] SubTask 3.1: 修正 create_mission 请求体
  - [x] SubTask 3.2: 修正 mission_id 字段提取
  - [x] SubTask 3.3: 修正 API 路径和 access_token
  - [x] SubTask 3.4: 添加轮询和重试

- [x] Task 4: 修复 Compose 配置
  - [x] SubTask 4.1: rosclaw-bridge 移至 rosclaw-sim profile
  - [x] SubTask 4.2: 新增 rosclaw-sim profile + ros2-rosbridge 服务
  - [x] SubTask 4.3: 修正 QwenPaw 健康检查为 /api/healthz
  - [x] SubTask 4.4: 调整超时 Java 60s Python 45s

- [x] Task 5: 新增技能列表 NATS 响应器
  - [x] SubTask 5.1: SkillListNatsResponder 订阅 opengeobot.skill.list
  - [x] SubTask 5.2: 返回已注册技能定义

## 阶段 2：ROSClaw 端侧执行修复

- [x] Task 6: 修复 ROSClaw Bridge 安装
  - [x] SubTask 6.1: entrypoint.sh 运行时安装 ROSClaw 包
  - [x] SubTask 6.2: ROSClaw 成功导入（不再降级模式）
  - [x] SubTask 6.3: bridge 日志显示 rosclaw mode

- [x] Task 7: 安装 rosbridge_server
  - [x] SubTask 7.1: ros2-rosbridge 服务安装 rosbridge_suite
  - [x] SubTask 7.2: 暴露 9090 端口
  - [x] SubTask 7.3: turtlesim 共享 ROS_DOMAIN_ID

## 阶段 3：边缘执行引擎

- [x] Task 8: 实现边缘任务步骤迭代执行
  - [x] SubTask 8.1: _start_mission 按步骤顺序逐个执行
  - [x] SubTask 8.2: 每步发布状态更新
  - [x] SubTask 8.3: 完成后发布 COMPLETED
  - [x] SubTask 8.4: 失败时发布 FAILED

- [x] Task 9: 修复 EdgeStateListener
  - [x] SubTask 9.1: 字段名 state -> status
  - [x] SubTask 9.2: 解析 mission_id、step_index、step_status
  - [x] SubTask 9.3: COMPLETED -> completeMission()
  - [x] SubTask 9.4: FAILED -> failMission()
  - [x] SubTask 9.5: updateStepStatus 更新步骤状态

## 阶段 4：实时监控

- [x] Task 10: 实现 WebSocket 事件推送
  - [x] SubTask 10.1: MonitorEventPublisher 创建
  - [x] SubTask 10.2: MissionService 状态转换时调用
  - [x] SubTask 10.3: EdgeStateListener 收到状态时调用
  - [x] SubTask 10.4: 42 个 Java 测试通过

- [x] Task 11: 实现 Trace 记录
  - [x] SubTask 11.1: TraceRecorder 写入 trace_span 和 fact_event
  - [x] SubTask 11.2: MissionService 和 EdgeStateListener 中调用
  - [x] SubTask 11.3: trace REST 端点可返回数据

- [x] Task 12: 云端安全状态监听
  - [x] SubTask 12.1: SafetyStateListener 订阅安全状态变化
  - [x] SubTask 12.2: 安全状态变化时推送 WebSocket

## 阶段 5：动态重规划

- [x] Task 13: 补全 AgentRuntimeProvider 接口
  - [x] SubTask 13.1: continue_plan 方法实现
  - [x] SubTask 13.2: cancel 方法实现
  - [x] SubTask 13.3: health 方法实现
  - [x] SubTask 13.4: handler.py 新增 replan NATS 订阅
  - [x] SubTask 13.5: 66 个 Python 测试通过

- [x] Task 14: 实现自动重规划
  - [x] SubTask 14.1: AgentRuntimeNatsClient.replanMission()
  - [x] SubTask 14.2: MissionOrchestrator.replanMission()
  - [x] SubTask 14.3: MissionService.failMission() 自动触发重规划
  - [x] SubTask 14.4: V27 migration 添加 replan_count 列
  - [x] SubTask 14.5: 最大重规划次数 3 次

- [ ] Task 15: E2E 完整闭环测试
  - [ ] SubTask 15.1: 启动 rosclaw-sim profile 验证服务栈
  - [ ] SubTask 15.2: E2E 测试：创建 -> 规划 -> 执行 -> COMPLETED
  - [ ] SubTask 15.3: trace_id 全链路关联验证
  - [ ] SubTask 15.4: 安全阻断场景验证
  - [ ] SubTask 15.5: 动态重规划验证

# Task Dependencies
- [Task 1-5] 阶段 1 ✓ 全部完成
- [Task 6-7] 阶段 2 ✓ 全部完成
- [Task 8-9] 阶段 3 ✓ 全部完成
- [Task 10-12] 阶段 4 ✓ 全部完成
- [Task 13-14] 阶段 5 ✓ 全部完成
- [Task 15] 待运行 E2E 测试验证
