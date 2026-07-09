# 修复实现与设计文档差距验证清单

## Phase 1: 安全红线修复

### platform-robot 权限码校验
- [x] RobotController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] SkillController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] MissionController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] PolicyController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] SafetyController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] FleetController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] MapController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] MonitorController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] MediaController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] TraceController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] McpToolController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] AlarmController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] OpsController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] OtaController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] RecoveryController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] MemoryController 所有端点有 @PreAuthorize 且权限码与清单一致
- [x] MissionTemplateController 所有端点有 @PreAuthorize 且权限码与清单一致（close-residual-audit-gaps 修复）
- [x] RobotGroupController 所有端点有 @PreAuthorize 且权限码与清单一致（close-residual-audit-gaps 修复）
- [x] RobotModelController 所有端点有 @PreAuthorize 且权限码与清单一致（close-residual-audit-gaps 修复）

### platform-governance 权限码校验
- [x] DictController 所有端点有 @PreAuthorize
- [x] I18nController 所有端点有 @PreAuthorize
- [x] ConfigController 所有端点有 @PreAuthorize
- [x] AuditController 所有端点有 @PreAuthorize
- [x] ExportController 所有端点有 @PreAuthorize

### 权限校验验证
- [x] 无权限用户调用受保护端点返回 403
- [x] 权限拒绝事件有审计记录（close-residual-audit-gaps 增强 GlobalExceptionHandler 审计日志）
- [x] 有权限用户正常访问

## Phase 2: Stub/Mock 替换

### Outbox Relay
- [x] OutboxRelay 组件存在且定时轮询未发布事件
- [x] 事件成功发布到 NATS JetStream
- [x] 发布后事件标记为已发布
- [x] NATS 不可用时事件保留为未发布
- [x] 重连后自动重试发布
- [x] OutboxRelay 有单元测试和集成测试

### OpsService 健康检查
- [x] checkStub() 方法已移除
- [x] NATS 连通性探测真实执行
- [x] MinIO 连通性探测真实执行
- [x] 服务不可达时状态为 UNHEALTHY（非 HEALTHY）
- [x] 健康检查有单元测试

### McpToolService 工具调用
- [x] executeSimulated() 方法已移除
- [x] 工具调用通过 MCP 网关或技能执行器真实执行
- [x] canary 路由逻辑保留且真实路由
- [x] 工具调用有单元测试和集成测试
- [x] 过时 Javadoc 已更新（close-residual-audit-gaps 修复）

### OtaService 部署
- [x] "始终成功"的模拟逻辑已移除
- [x] 通过边缘网关真实下发固件
- [x] 部署进度真实跟踪
- [x] 部署可失败（非始终成功）
- [x] OTA 部署有单元测试（成功、失败、超时）

### NotificationService 通知
- [x] email stub 标记已移除
- [x] email 通知真实发送或明确标注为未实现
- [x] webhook 通知真实发送
- [x] 应用内通知真实发送
- [x] 通知状态如实反映发送结果
- [x] 通知发送有单元测试

## Phase 3: 缺失组件实现

### edge/safety-gateway/
- [x] Python 项目结构存在（pyproject.toml、src 目录）
- [x] 急停锁存逻辑实现（触发后阻止技能执行）
- [x] 动作级安全校验实现（禁区、速度限制、碰撞风险）
- [x] NATS 订阅安全指令、发布安全状态
- [x] 本地急停不依赖云端或网络
- [x] 单元测试存在

### edge/local-skill-executor/
- [x] Python 项目结构存在
- [x] 技能调用接收和分发实现
- [x] 通过 Safety Gateway 校验后执行
- [x] 适配器调用接口实现
- [x] 执行结果回传和审计实现
- [x] 单元测试存在

### services/agent-runtime/
- [x] Python 项目结构存在（src 目录非空）
- [x] AgentRuntimeProvider 接口适配 QwenPaw 实现
- [x] 意图理解和计划生成提案输出
- [x] Agent 输出经 Schema/权限/状态机校验
- [x] 单元测试存在

### services/mcp-tool-gateway/
- [x] Python 项目结构存在
- [x] MCP 工具协议处理和工具注册
- [x] 工具调用路由（canary/stable）和执行日志
- [x] NATS 通信与云端 McpToolService 对接
- [x] 单元测试存在

## Phase 4: 前端修复

### DefaultLayout 接入
- [x] router/index.ts 为认证路由添加 DefaultLayout 父布局
- [x] 登录后页面显示左侧导航栏
- [x] 登录后页面显示顶栏和用户菜单
- [x] 退出按钮正常工作
- [x] 面包屑正常显示
- [x] 语言切换器正常工作
- [x] AppOfflineIndicator 在布局内渲染

### DashboardView 修复
- [x] 硬编码占位值已移除
- [x] 调用真实 API 获取统计数据
- [x] 或重定向到 OpsDashboardView
- [x] 加载状态显示
- [x] 错误处理实现

### 前端错误处理
- [x] FleetManagementView 有 catch 块和本地化错误提示
- [x] AlarmManagementView 有 catch 块和本地化错误提示
- [x] OpsDashboardView 有 catch 块和本地化错误提示
- [x] OtaManagementView 有 catch 块和本地化错误提示
- [x] BackupRecoveryView 有 catch 块和本地化错误提示
- [x] TaskMemoryView 有 catch 块和本地化错误提示

### P-TRACE-001 前端页面（close-residual-audit-gaps 新增）
- [x] TraceView.vue 组件已创建
- [x] /trace 路由已注册到 DefaultLayout
- [x] 7 种视图状态已实现
- [x] i18n key 已添加

### i18n 合规（close-residual-audit-gaps 新增）
- [x] OtaManagementView 枚举标签已改用 t()
- [x] AlarmManagementView 枚举标签已改用 t()
- [x] BackupRecoveryView 枚举标签已改用 t()

## Phase 5: 测试覆盖

### platform-robot 单元测试
- [x] SafetyService 单元测试存在且通过
- [x] MissionService 单元测试存在且通过
- [x] RobotService 单元测试存在且通过
- [x] SkillService 单元测试存在且通过
- [x] PolicyService 单元测试存在且通过
- [x] FleetService 单元测试存在且通过
- [x] MonitorService 单元测试存在且通过
- [x] MediaService 单元测试存在且通过
- [x] TraceService 单元测试存在且通过
- [x] MemoryService 单元测试存在且通过

### platform-common 单元测试
- [x] OutboxRepository 单元测试存在且通过
- [x] AuditService 单元测试存在且通过
- [x] PublicIdGenerator 单元测试存在且通过
- [x] ClockProvider 单元测试存在且通过
- [x] ErrorEnvelope/ProblemDetails 单元测试存在且通过

### Java 集成测试
- [x] 认证端到端集成测试存在且通过
- [x] 权限校验集成测试存在且通过（含 12 个新增权限拒绝测试）
- [x] 机器人管理端到端测试存在且通过
- [x] 任务全生命周期端到端测试存在且通过
- [x] 安全流程端到端测试存在且通过
- [x] OTA 部署端到端测试存在且通过

### Python 测试
- [x] edge/gateway 单元测试存在且通过（114 passed）
- [x] edge/safety-gateway 单元测试存在且通过（51 passed）
- [x] edge/local-skill-executor 单元测试存在且通过（18 passed）
- [x] services/sim-adapter 单元测试存在且通过（63 passed）
- [x] services/ros1-adapter 单元测试存在且通过（62 passed）
- [x] services/agent-runtime 单元测试存在且通过（17 passed）
- [x] services/mcp-tool-gateway 单元测试存在且通过（40 passed）

### dev.sh test 子命令
- [x] cmd_test() 包含 Python pytest 执行
- [x] dev.ps1 Invoke-Test 包含 Python 测试
- [x] `./scripts/dev.sh test` 同时运行 Java 和 Python 测试

## Phase 6: 脚本和验证

### sim-up 实现
- [x] dev.sh cmd_sim_up() 实现启动仿真栈
- [x] dev.ps1 Invoke-SimUp 实现同步逻辑
- [x] compose.yml 包含仿真 profile
- [x] `./scripts/dev.sh sim-up` 启动仿真栈

### validate_platform_manifest.py 增强
- [x] jsonschema 依赖已安装
- [x] 验证逻辑检查报告内容有效性（非纯模板）
- [x] 验证逻辑检查测试文件存在性
- [x] `python3 scripts/validate_platform_manifest.py` 通过

## Phase 7: 报告和证据

### 报告真实性
- [x] reports/tests/ 下 HTML 报告为真实测试运行输出
- [x] reports/security/ 下安全清单为真实安全检查结果
- [x] reports/deployment/ 下部署摘要为真实部署记录
- [x] reports/observability/ 下可观测性摘要为真实配置验证
- [x] HIL 报告如实标注未执行状态
- [x] HIL 未执行的功能不在清单 required_tests 中声称完成

### 机器清单状态
- [x] 不满足完成标准的功能已回退为 IN_PROGRESS
- [x] 修复完成的功能验证后更新为 DONE
- [x] validate_platform_manifest.py 验证通过
