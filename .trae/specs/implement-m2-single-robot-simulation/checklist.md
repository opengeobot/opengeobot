# M2 单机器人仿真闭环验证清单

## F-ROBOT-001: 机器人注册与状态
- [x] OpenAPI 契约包含机器人 CRUD 和状态查询端点
- [x] AsyncAPI 契约包含 robot.registered.v1、robot.status_changed.v1 事件
- [x] Flyway V7 迁移创建 robot_registry.robot 等表
- [x] 机器人 CRUD 支持注册、查询、更新、删除
- [x] 机器人状态支持 ONLINE/OFFLINE/BUSY/ERROR/MAINTENANCE
- [x] 状态变更发布事件和写入审计
- [x] 能力声明支持多维度（导航/视觉/操作/通信）
- [x] Java 单元测试覆盖 RobotService
- [x] 前端机器人管理页功能正常

## F-ROBOT-002: 型号与分组
- [x] Flyway V8 迁移创建 robot_model、robot_group 表
- [x] 机器人型号 CRUD 功能正常
- [x] 机器人分组支持树形层级
- [x] 机器人绑定到型号和分组

## F-SKILL-001: 技能生命周期
- [x] OpenAPI 契约包含技能 CRUD 和版本管理端点
- [x] Flyway V9 迁移创建 skill_registry.skill 等表
- [x] 技能支持版本管理（DRAFT/PUBLISHED/DEPRECATED）
- [x] 技能启用/禁用功能正常
- [x] 技能注册发布 skill.registered.v1 事件
- [x] Java 单元测试覆盖 SkillService
- [x] 前端技能管理页功能正常

## F-MCP-001: MCP 工具网关
- [x] OpenAPI 契约包含 MCP 工具注册和调用端点
- [x] Flyway V10 迁移创建 mcp_tool 等表
- [x] MCP 工具注册功能正常
- [x] MCP 工具调用记录日志
- [x] 灰度发布支持（canary 百分比）

## F-MISSION-001/002/003: 任务管理
- [x] OpenAPI 契约包含任务 CRUD、规划、执行控制、模板端点
- [x] AsyncAPI 契约包含任务生命周期事件
- [x] Flyway V11 迁移创建 mission 等表
- [x] SM-MISSION-001 状态机定义存在
- [x] 任务创建支持步骤规划和模板
- [x] 任务执行控制（开始/暂停/恢复/取消）
- [x] 任务终态管理（COMPLETED/FAILED/CANCELLED）
- [x] 任务模板和审批功能正常
- [x] 任务生命周期事件写入 Outbox
- [x] Java 单元测试覆盖 MissionService
- [x] 前端任务管理页功能正常

## F-POLICY-001: 策略管理
- [x] OpenAPI 契约包含策略 CRUD 和发布端点
- [x] Flyway V12 迁移创建 policy 等表
- [x] 策略版本管理（DRAFT/PUBLISHED/ARCHIVED）
- [x] 策略规则评估在任务创建时执行
- [x] Java 单元测试覆盖 PolicyService

## F-SAFETY-001: 安全拦截
- [x] OpenAPI 契约包含紧急停止和重置端点
- [x] AsyncAPI 契约包含 safety.emergency_stop.v1 事件
- [x] Flyway V13 迁移创建 safety_state 等表
- [x] 紧急停止功能立即生效
- [x] 安全重置功能正常
- [x] 任务执行前安全检查（禁区/速度/碰撞）
- [x] Java 单元测试覆盖 SafetyService
- [x] 前端安全控制页功能正常

## F-EDGE-001/002: 边缘网关
- [x] 边缘网关 Python 项目结构完整
- [x] NATS 连接管理正常
- [x] 边缘命令处理器接收和执行指令
- [x] 离线缓存功能正常
- [x] 重连对账功能正常

## F-ADAPTER-001: ROS2 仿真适配器
- [x] 仿真适配器 Python 项目结构完整
- [x] 基础技能实现（stand_up/stop/move_forward/capture_image/emergency_stop）
- [x] 适配器接口正确返回执行结果
- [x] Docker Compose 仿真服务配置完成

## F-MAP-001: 地图场景
- [x] OpenAPI 契约包含地图/场景/区域/禁区端点
- [x] Flyway V14 迁移创建 map_scene 等表
- [x] 地图版本管理功能正常
- [x] 禁区管理功能正常
- [x] 前端地图场景页功能正常

## F-MONITOR-001: 实时监控
- [x] WebSocket 推送机器人状态和任务进度
- [x] 人工接管功能正常
- [x] 前端实时监控页功能正常

## F-MEDIA-001: 媒体对象
- [x] OpenAPI 契约包含媒体上传/查看/删除端点
- [x] Flyway V15 迁移创建 media_object 表
- [x] MinIO 上传/下载功能正常
- [x] 媒体保留策略配置

## F-TRACE-001: 全链路追踪
- [x] OpenAPI 契约包含追踪查询和事实回放端点
- [x] Flyway V16 迁移创建 trace_span、fact_event 表
- [x] 追踪查询支持按 trace_id/时间范围过滤
- [x] 事实回放功能正常

## 单机器人仿真闭环验证
- [x] C03: 机器人注册并上线
- [x] C04: 技能注册并可用
- [x] C05: 任务创建并规划
- [x] C06: 任务安全检查通过
- [x] C07: 任务执行并完成
- [x] C08: 紧急停止生效
- [x] C09: 安全重置恢复
- [x] C10: 离线缓存正常
- [x] C11: 重连对账正常
- [x] C12: 全链路追踪可查
- [x] C18: 仿真环境闭环
- [x] C19: 事实回放可用

## 机器清单
- [x] F-ROBOT-001~002 状态标记为 DONE
- [x] F-SKILL-001 状态标记为 DONE
- [x] F-MCP-001 状态标记为 DONE
- [x] F-MISSION-001~003 状态标记为 DONE
- [x] F-POLICY-001 状态标记为 DONE
- [x] F-SAFETY-001 状态标记为 DONE
- [x] F-EDGE-001~002 状态标记为 DONE
- [x] F-ADAPTER-001 状态标记为 DONE
- [x] F-MAP-001 状态标记为 DONE
- [x] F-MONITOR-001 状态标记为 DONE
- [x] F-MEDIA-001 状态标记为 DONE
- [x] F-TRACE-001 状态标记为 DONE
- [x] python scripts/validate_platform_manifest.py 验证通过
- [x] M2 Runbook 创建（非空）
- [x] M2 安全报告创建（非空）
