# M3-M6 剩余功能验证清单

## F-ADAPTER-002: ROS1 真机适配器
- [x] OpenAPI/AsyncAPI 契约包含适配器端点
- [x] Python 适配器项目结构完整 (services/ros1-adapter/)
- [x] Unitree 协议适配功能正常
- [x] 自研协议适配功能正常
- [x] Docker Compose 仿真+真机双模式配置

## F-FLEET-001: 多机器人调度
- [x] OpenAPI/AsyncAPI 契约包含调度和冲突检测端点
- [x] Flyway V17 迁移创建 fleet 调度表
- [x] 调度引擎支持任务分配和路径规划
- [x] 时空冲突检测功能正常
- [x] 故障转移功能正常
- [x] fleet.conflict_detected.v1 和 fleet.failover.v1 事件发布
- [x] 前端调度管理页功能正常

## F-ALARM-001: 告警通知
- [x] OpenAPI/AsyncAPI 契约包含告警端点
- [x] Flyway V18 迁移创建 alarm 表
- [x] 告警规则 CRUD 功能正常
- [x] 阈值触发功能正常
- [x] 通知渠道（邮件/Webhook/站内信）功能正常
- [x] 告警确认/关闭功能正常
- [x] alarm.triggered.v1 事件发布
- [x] 前端告警管理页功能正常

## F-OPS-001: 运维态势
- [x] OpenAPI 契约包含运维面板端点
- [x] Flyway V19 迁移创建 ops 表
- [x] 系统指标采集功能正常
- [x] 健康面板显示系统状态
- [x] 运维报表生成功能正常
- [x] 容量预警功能正常
- [x] 前端运维面板页功能正常

## F-OTA-001: OTA 发布
- [x] OpenAPI/AsyncAPI 契约包含 OTA 端点
- [x] Flyway V20 迁移创建 ota 表
- [x] 固件/技能包管理功能正常
- [x] 灰度发布功能正常
- [x] 回滚机制功能正常
- [x] ota.release_started.v1 和 ota.completed.v1 事件发布
- [x] 前端 OTA 管理页功能正常

## F-RECOVERY-001: 数据备份恢复
- [x] OpenAPI 契约包含备份恢复端点
- [x] Flyway V21 迁移创建 recovery 表
- [x] PostgreSQL pg_dump 备份功能正常
- [x] MinIO 备份功能正常
- [x] 恢复流程功能正常
- [x] 定时备份任务配置
- [x] 过期备份清理功能正常
- [x] 前端备份恢复页功能正常

## F-MEMORY-001: 任务记忆
- [x] OpenAPI/AsyncAPI 契约包含记忆端点
- [x] Flyway V22 迁移创建 memory 表
- [x] 任务执行结果记录功能正常
- [x] 失败案例库功能正常
- [x] 相似案例检索功能正常
- [x] 改进建议生成功能正常
- [x] memory.case_recorded.v1 事件发布
- [x] 前端任务记忆页功能正常

## 验收标准验证
- [x] C13: 多机器人调度冲突检测
- [x] C14: 故障转移正常
- [x] C15: 告警触发和通知
- [x] C16: 运维面板完整
- [x] C17: OTA 灰度发布和回滚
- [x] C20: 数据备份恢复
- [x] C21: 灾难演练
- [x] C22: 任务记忆闭环

## 机器清单
- [x] F-ADAPTER-002 状态标记为 DONE
- [x] F-FLEET-001 状态标记为 DONE
- [x] F-ALARM-001 状态标记为 DONE
- [x] F-OPS-001 状态标记为 DONE
- [x] F-OTA-001 状态标记为 DONE
- [x] F-RECOVERY-001 状态标记为 DONE
- [x] F-MEMORY-001 状态标记为 DONE
- [x] python scripts/validate_platform_manifest.py 验证通过 (DONE=29)
- [x] Runbook 创建（非空）
- [x] 安全报告创建（非空）
