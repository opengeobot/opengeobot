# M2 单机器人仿真闭环验证清单

## F-ROBOT-001: 机器人注册与状态
- [ ] OpenAPI 契约包含机器人 CRUD 和状态查询端点
- [ ] AsyncAPI 契约包含 robot.registered.v1、robot.status_changed.v1 事件
- [ ] Flyway V7 迁移创建 robot_registry.robot 等表
- [ ] 机器人 CRUD 支持注册、查询、更新、删除
- [ ] 机器人状态支持 ONLINE/OFFLINE/BUSY/ERROR/MAINTENANCE
- [ ] 状态变更发布事件和写入审计
- [ ] 能力声明支持多维度（导航/视觉/操作/通信）
- [ ] Java 单元测试覆盖 RobotService
- [ ] 前端机器人管理页功能正常

## F-ROBOT-002: 型号与分组
- [ ] Flyway V8 迁移创建 robot_model、robot_group 表
- [ ] 机器人型号 CRUD 功能正常
- [ ] 机器人分组支持树形层级
- [ ] 机器人绑定到型号和分组

## F-SKILL-001: 技能生命周期
- [ ] OpenAPI 契约包含技能 CRUD 和版本管理端点
- [ ] Flyway V9 迁移创建 skill_registry.skill 等表
- [ ] 技能支持版本管理（DRAFT/PUBLISHED/DEPRECATED）
- [ ] 技能启用/禁用功能正常
- [ ] 技能注册发布 skill.registered.v1 事件
- [ ] Java 单元测试覆盖 SkillService
- [ ] 前端技能管理页功能正常

## F-MCP-001: MCP 工具网关
- [ ] OpenAPI 契约包含 MCP 工具注册和调用端点
- [ ] Flyway V10 迁移创建 mcp_tool 等表
- [ ] MCP 工具注册功能正常
- [ ] MCP 工具调用记录日志
- [ ] 灰度发布支持（canary 百分比）

## F-MISSION-001/002/003: 任务管理
- [ ] OpenAPI 契约包含任务 CRUD、规划、执行控制、模板端点
- [ ] AsyncAPI 契约包含任务生命周期事件
- [ ] Flyway V11 迁移创建 mission 等表
- [ ] SM-MISSION-001 状态机定义存在
- [ ] 任务创建支持步骤规划和模板
- [ ] 任务执行控制（开始/暂停/恢复/取消）
- [ ] 任务终态管理（COMPLETED/FAILED/CANCELLED）
- [ ] 任务模板和审批功能正常
- [ ] 任务生命周期事件写入 Outbox
- [ ] Java 单元测试覆盖 MissionService
- [ ] 前端任务管理页功能正常

## F-POLICY-001: 策略管理
- [ ] OpenAPI 契约包含策略 CRUD 和发布端点
- [ ] Flyway V12 迁移创建 policy 等表
- [ ] 策略版本管理（DRAFT/PUBLISHED/ARCHIVED）
- [ ] 策略规则评估在任务创建时执行
- [ ] Java 单元测试覆盖 PolicyService

## F-SAFETY-001: 安全拦截
- [ ] OpenAPI 契约包含紧急停止和重置端点
- [ ] AsyncAPI 契约包含 safety.emergency_stop.v1 事件
- [ ] Flyway V13 迁移创建 safety_state 等表
- [ ] 紧急停止功能立即生效
- [ ] 安全重置功能正常
- [ ] 任务执行前安全检查（禁区/速度/碰撞）
- [ ] Java 单元测试覆盖 SafetyService
- [ ] 前端安全控制页功能正常

## F-EDGE-001/002: 边缘网关
- [ ] 边缘网关 Python 项目结构完整
- [ ] NATS 连接管理正常
- [ ] 边缘命令处理器接收和执行指令
- [ ] 离线缓存功能正常
- [ ] 重连对账功能正常

## F-ADAPTER-001: ROS2 仿真适配器
- [ ] 仿真适配器 Python 项目结构完整
- [ ] 基础技能实现（stand_up/stop/move_forward/capture_image/emergency_stop）
- [ ] 适配器接口正确返回执行结果
- [ ] Docker Compose 仿真服务配置完成

## F-MAP-001: 地图场景
- [ ] OpenAPI 契约包含地图/场景/区域/禁区端点
- [ ] Flyway V14 迁移创建 map_scene 等表
- [ ] 地图版本管理功能正常
- [ ] 禁区管理功能正常
- [ ] 前端地图场景页功能正常

## F-MONITOR-001: 实时监控
- [ ] WebSocket 推送机器人状态和任务进度
- [ ] 人工接管功能正常
- [ ] 前端实时监控页功能正常

## F-MEDIA-001: 媒体对象
- [ ] OpenAPI 契约包含媒体上传/查看/删除端点
- [ ] Flyway V15 迁移创建 media_object 表
- [ ] MinIO 上传/下载功能正常
- [ ] 媒体保留策略配置

## F-TRACE-001: 全链路追踪
- [ ] OpenAPI 契约包含追踪查询和事实回放端点
- [ ] Flyway V16 迁移创建 trace_span、fact_event 表
- [ ] 追踪查询支持按 trace_id/时间范围过滤
- [ ] 事实回放功能正常

## 单机器人仿真闭环验证
- [ ] C03: 机器人注册并上线
- [ ] C04: 技能注册并可用
- [ ] C05: 任务创建并规划
- [ ] C06: 任务安全检查通过
- [ ] C07: 任务执行并完成
- [ ] C08: 紧急停止生效
- [ ] C09: 安全重置恢复
- [ ] C10: 离线缓存正常
- [ ] C11: 重连对账正常
- [ ] C12: 全链路追踪可查
- [ ] C18: 仿真环境闭环
- [ ] C19: 事实回放可用

## 机器清单
- [ ] F-ROBOT-001~002 状态标记为 DONE
- [ ] F-SKILL-001 状态标记为 DONE
- [ ] F-MCP-001 状态标记为 DONE
- [ ] F-MISSION-001~003 状态标记为 DONE
- [ ] F-POLICY-001 状态标记为 DONE
- [ ] F-SAFETY-001 状态标记为 DONE
- [ ] F-EDGE-001~002 状态标记为 DONE
- [ ] F-ADAPTER-001 状态标记为 DONE
- [ ] F-MAP-001 状态标记为 DONE
- [ ] F-MONITOR-001 状态标记为 DONE
- [ ] F-MEDIA-001 状态标记为 DONE
- [ ] F-TRACE-001 状态标记为 DONE
- [ ] python scripts/validate_platform_manifest.py 验证通过
- [ ] M2 Runbook 创建（非空）
- [ ] M2 安全报告创建（非空）
