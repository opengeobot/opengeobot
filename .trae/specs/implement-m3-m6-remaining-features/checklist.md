# M3-M6 剩余功能验证清单

## F-ADAPTER-002: ROS1 真机适配器
- [ ] OpenAPI/AsyncAPI 契约包含适配器端点
- [ ] Python 适配器项目结构完整 (services/ros1-adapter/)
- [ ] Unitree 协议适配功能正常
- [ ] 自研协议适配功能正常
- [ ] Docker Compose 仿真+真机双模式配置

## F-FLEET-001: 多机器人调度
- [ ] OpenAPI/AsyncAPI 契约包含调度和冲突检测端点
- [ ] Flyway V17 迁移创建 fleet 调度表
- [ ] 调度引擎支持任务分配和路径规划
- [ ] 时空冲突检测功能正常
- [ ] 故障转移功能正常
- [ ] fleet.conflict_detected.v1 和 fleet.failover.v1 事件发布
- [ ] 前端调度管理页功能正常

## F-ALARM-001: 告警通知
- [ ] OpenAPI/AsyncAPI 契约包含告警端点
- [ ] Flyway V18 迁移创建 alarm 表
- [ ] 告警规则 CRUD 功能正常
- [ ] 阈值触发功能正常
- [ ] 通知渠道（邮件/Webhook/站内信）功能正常
- [ ] 告警确认/关闭功能正常
- [ ] alarm.triggered.v1 事件发布
- [ ] 前端告警管理页功能正常

## F-OPS-001: 运维态势
- [ ] OpenAPI 契约包含运维面板端点
- [ ] Flyway V19 迁移创建 ops 表
- [ ] 系统指标采集功能正常
- [ ] 健康面板显示系统状态
- [ ] 运维报表生成功能正常
- [ ] 容量预警功能正常
- [ ] 前端运维面板页功能正常

## F-OTA-001: OTA 发布
- [ ] OpenAPI/AsyncAPI 契约包含 OTA 端点
- [ ] Flyway V20 迁移创建 ota 表
- [ ] 固件/技能包管理功能正常
- [ ] 灰度发布功能正常
- [ ] 回滚机制功能正常
- [ ] ota.release_started.v1 和 ota.completed.v1 事件发布
- [ ] 前端 OTA 管理页功能正常

## F-RECOVERY-001: 数据备份恢复
- [ ] OpenAPI 契约包含备份恢复端点
- [ ] Flyway V21 迁移创建 recovery 表
- [ ] PostgreSQL pg_dump 备份功能正常
- [ ] MinIO 备份功能正常
- [ ] 恢复流程功能正常
- [ ] 定时备份任务配置
- [ ] 过期备份清理功能正常
- [ ] 前端备份恢复页功能正常

## F-MEMORY-001: 任务记忆
- [ ] OpenAPI/AsyncAPI 契约包含记忆端点
- [ ] Flyway V22 迁移创建 memory 表
- [ ] 任务执行结果记录功能正常
- [ ] 失败案例库功能正常
- [ ] 相似案例检索功能正常
- [ ] 改进建议生成功能正常
- [ ] memory.case_recorded.v1 事件发布
- [ ] 前端任务记忆页功能正常

## 验收标准验证
- [ ] C13: 多机器人调度冲突检测
- [ ] C14: 故障转移正常
- [ ] C15: 告警触发和通知
- [ ] C16: 运维面板完整
- [ ] C17: OTA 灰度发布和回滚
- [ ] C20: 数据备份恢复
- [ ] C21: 灾难演练
- [ ] C22: 任务记忆闭环

## 机器清单
- [ ] F-ADAPTER-002 状态标记为 DONE
- [ ] F-FLEET-001 状态标记为 DONE
- [ ] F-ALARM-001 状态标记为 DONE
- [ ] F-OPS-001 状态标记为 DONE
- [ ] F-OTA-001 状态标记为 DONE
- [ ] F-RECOVERY-001 状态标记为 DONE
- [ ] F-MEMORY-001 状态标记为 DONE
- [ ] python scripts/validate_platform_manifest.py 验证通过 (DONE=29)
- [ ] Runbook 创建（非空）
- [ ] 安全报告创建（非空）
