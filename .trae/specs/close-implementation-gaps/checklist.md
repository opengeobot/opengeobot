# 修复实现与设计文档差距验证清单

## Phase 1: 安全红线修复

### platform-robot 权限码校验
- [ ] RobotController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] SkillController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] MissionController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] PolicyController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] SafetyController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] FleetController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] MapController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] MonitorController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] MediaController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] TraceController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] McpToolController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] AlarmController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] OpsController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] OtaController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] RecoveryController 所有端点有 @PreAuthorize 且权限码与清单一致
- [ ] MemoryController 所有端点有 @PreAuthorize 且权限码与清单一致

### platform-governance 权限码校验
- [ ] DictController 所有端点有 @PreAuthorize
- [ ] I18nController 所有端点有 @PreAuthorize
- [ ] ConfigController 所有端点有 @PreAuthorize
- [ ] AuditController 所有端点有 @PreAuthorize
- [ ] ExportController 所有端点有 @PreAuthorize

### 权限校验验证
- [ ] 无权限用户调用受保护端点返回 403
- [ ] 权限拒绝事件有审计记录
- [ ] 有权限用户正常访问

## Phase 2: Stub/Mock 替换

### Outbox Relay
- [ ] OutboxRelay 组件存在且定时轮询未发布事件
- [ ] 事件成功发布到 NATS JetStream
- [ ] 发布后事件标记为已发布
- [ ] NATS 不可用时事件保留为未发布
- [ ] 重连后自动重试发布
- [ ] OutboxRelay 有单元测试和集成测试

### OpsService 健康检查
- [ ] checkStub() 方法已移除
- [ ] NATS 连通性探测真实执行
- [ ] MinIO 连通性探测真实执行
- [ ] 服务不可达时状态为 UNHEALTHY（非 HEALTHY）
- [ ] 健康检查有单元测试

### McpToolService 工具调用
- [ ] executeSimulated() 方法已移除
- [ ] 工具调用通过 MCP 网关或技能执行器真实执行
- [ ] canary 路由逻辑保留且真实路由
- [ ] 工具调用有单元测试和集成测试

### OtaService 部署
- [ ] "始终成功"的模拟逻辑已移除
- [ ] 通过边缘网关真实下发固件
- [ ] 部署进度真实跟踪
- [ ] 部署可失败（非始终成功）
- [ ] OTA 部署有单元测试（成功、失败、超时）

### NotificationService 通知
- [ ] email stub 标记已移除
- [ ] email 通知真实发送或明确标注为未实现
- [ ] webhook 通知真实发送
- [ ] 应用内通知真实发送
- [ ] 通知状态如实反映发送结果
- [ ] 通知发送有单元测试

## Phase 3: 缺失组件实现

### edge/safety-gateway/
- [ ] Python 项目结构存在（pyproject.toml、src 目录）
- [ ] 急停锁存逻辑实现（触发后阻止技能执行）
- [ ] 动作级安全校验实现（禁区、速度限制、碰撞风险）
- [ ] NATS 订阅安全指令、发布安全状态
- [ ] 本地急停不依赖云端或网络
- [ ] 单元测试存在

### edge/local-skill-executor/
- [ ] Python 项目结构存在
- [ ] 技能调用接收和分发实现
- [ ] 通过 Safety Gateway 校验后执行
- [ ] 适配器调用接口实现
- [ ] 执行结果回传和审计实现
- [ ] 单元测试存在

### services/agent-runtime/
- [ ] Python 项目结构存在（src 目录非空）
- [ ] AgentRuntimeProvider 接口适配 QwenPaw 实现
- [ ] 意图理解和计划生成提案输出
- [ ] Agent 输出经 Schema/权限/状态机校验
- [ ] 单元测试存在

### services/mcp-tool-gateway/
- [ ] Python 项目结构存在
- [ ] MCP 工具协议处理和工具注册
- [ ] 工具调用路由（canary/stable）和执行日志
- [ ] NATS 通信与云端 McpToolService 对接
- [ ] 单元测试存在

## Phase 4: 前端修复

### DefaultLayout 接入
- [ ] router/index.ts 为认证路由添加 DefaultLayout 父布局
- [ ] 登录后页面显示左侧导航栏
- [ ] 登录后页面显示顶栏和用户菜单
- [ ] 退出按钮正常工作
- [ ] 面包屑正常显示
- [ ] 语言切换器正常工作
- [ ] AppOfflineIndicator 在布局内渲染

### DashboardView 修复
- [ ] 硬编码占位值已移除
- [ ] 调用真实 API 获取统计数据
- [ ] 或重定向到 OpsDashboardView
- [ ] 加载状态显示
- [ ] 错误处理实现

### 前端错误处理
- [ ] FleetManagementView 有 catch 块和本地化错误提示
- [ ] AlarmManagementView 有 catch 块和本地化错误提示
- [ ] OpsDashboardView 有 catch 块和本地化错误提示
- [ ] OtaManagementView 有 catch 块和本地化错误提示
- [ ] BackupRecoveryView 有 catch 块和本地化错误提示
- [ ] TaskMemoryView 有 catch 块和本地化错误提示

## Phase 5: 测试覆盖

### platform-robot 单元测试
- [ ] SafetyService 单元测试存在且通过
- [ ] MissionService 单元测试存在且通过
- [ ] RobotService 单元测试存在且通过
- [ ] SkillService 单元测试存在且通过
- [ ] PolicyService 单元测试存在且通过
- [ ] FleetService 单元测试存在且通过
- [ ] MonitorService 单元测试存在且通过
- [ ] MediaService 单元测试存在且通过
- [ ] TraceService 单元测试存在且通过
- [ ] MemoryService 单元测试存在且通过

### platform-common 单元测试
- [ ] OutboxRepository 单元测试存在且通过
- [ ] AuditService 单元测试存在且通过
- [ ] PublicIdGenerator 单元测试存在且通过
- [ ] ClockProvider 单元测试存在且通过
- [ ] ErrorEnvelope/ProblemDetails 单元测试存在且通过

### Java 集成测试
- [ ] 认证端到端集成测试存在且通过
- [ ] 权限校验集成测试存在且通过
- [ ] 机器人管理端到端测试存在且通过
- [ ] 任务全生命周期端到端测试存在且通过
- [ ] 安全流程端到端测试存在且通过
- [ ] OTA 部署端到端测试存在且通过

### Python 测试
- [ ] edge/gateway 单元测试存在且通过
- [ ] edge/safety-gateway 单元测试存在且通过
- [ ] edge/local-skill-executor 单元测试存在且通过
- [ ] services/sim-adapter 单元测试存在且通过
- [ ] services/ros1-adapter 单元测试存在且通过
- [ ] services/agent-runtime 单元测试存在且通过
- [ ] services/mcp-tool-gateway 单元测试存在且通过

### dev.sh test 子命令
- [ ] cmd_test() 包含 Python pytest 执行
- [ ] dev.ps1 Invoke-Test 包含 Python 测试
- [ ] `./scripts/dev.sh test` 同时运行 Java 和 Python 测试

## Phase 6: 脚本和验证

### sim-up 实现
- [ ] dev.sh cmd_sim_up() 实现启动仿真栈
- [ ] dev.ps1 Invoke-SimUp 实现同步逻辑
- [ ] compose.yml 包含仿真 profile
- [ ] `./scripts/dev.sh sim-up` 启动仿真栈

### validate_platform_manifest.py 增强
- [ ] jsonschema 依赖已安装
- [ ] 验证逻辑检查报告内容有效性（非纯模板）
- [ ] 验证逻辑检查测试文件存在性
- [ ] `python3 scripts/validate_platform_manifest.py` 通过

## Phase 7: 报告和证据

### 报告真实性
- [ ] reports/tests/ 下 HTML 报告为真实测试运行输出
- [ ] reports/security/ 下安全清单为真实安全检查结果
- [ ] reports/deployment/ 下部署摘要为真实部署记录
- [ ] reports/observability/ 下可观测性摘要为真实配置验证
- [ ] HIL 报告如实标注未执行状态
- [ ] HIL 未执行的功能不在清单 required_tests 中声称完成

### 机器清单状态
- [ ] 不满足完成标准的功能已回退为 IN_PROGRESS
- [ ] 修复完成的功能验证后更新为 DONE
- [ ] validate_platform_manifest.py 验证通过
