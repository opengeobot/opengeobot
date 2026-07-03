# OpenGeoBot Project Rules

必须共同读取：

- `docs/AI开发约束与平台公共能力规范 V1.0.md`：工程和安全规则。
- `docs/平台功能与数据状态统一实施蓝图 V1.0.md`：页面、后端用例和数据状态。
- `docs/implementation/platform-feature-manifest.yaml`：功能 ID 与端到端制品清单。
- `docs/一脑多控平台详细设计说明书 V1.0.pdf`：业务来源。

开始任务前定位功能 ID 并读取完整映射。冲突时停止并用 ADR 解决；安全采用更严格约束。

## Mandatory architecture

- 云端管理使用 Java 21 + Spring Boot 3.x + MyBatis-Plus，优先模块化单体。
- Agent/机器人适配使用 Python 3.12 + uv。QwenPaw 只能通过 `AgentRuntimeProvider` 适配。
- 前端统一 Vue 3 + TypeScript + Vite + WebSocket，不复制 React 前端。
- 工具协议 MCP 优先；HTTP/gRPC 辅助。
- 边缘为 ROSClaw Edge Runtime + Safety Gateway + Local Skill Executor。
- ROS2 是主路径；ROS1 仅隔离兼容；弱网/ROS2DDS 桥接使用 Zenoh。
- 数据使用 PostgreSQL、可选 TimescaleDB、pgvector、MinIO/S3、NATS JetStream。
- 监控使用 vmagent + VictoriaMetrics + Grafana；日志使用 Vector + Loki，关键审计进入 PostgreSQL。

## Safety red lines

1. Agent/LLM 不得直接调用 `/cmd_vel`、关节/电机、原始 UDP 或厂商 SDK。
2. 物理动作必须是已注册且版本化的 Skill/Capability，并经过 IAM、Mission、Policy、Fleet、Edge Safety。
3. 边缘安全判定最终有效；本地急停不得依赖云端或网络，且必须锁存。
4. Agent 输出是不可信提案，必须经 Schema、权限、状态机、资源和安全的确定性校验。
5. 未通过仿真和安全测试的运动变更不得连接真实机器人。
6. 不得猜测 QwenPaw、ROSClaw、ROS、Zenoh 或设备 SDK API；查锁定契约/官方版本文档。

## Common platform capabilities

用户/组织、角色/权限、数据字典、国际化、平台配置、审计、幂等、错误、分页、时间、ID、事件 Outbox/Inbox 和对象元数据都必须复用公共模块。业务域不得建立重复实现，不得硬编码角色名或展示文案。

- 代码检查稳定权限码，不检查 `admin` 等角色名。
- 状态机、安全决策、权限码、事件类型是代码契约，不是可编辑数据字典。
- 前端可见文案使用 i18n key；后端错误返回稳定 code/message_key。
- 业务模块不得读写其他领域的表，不得绕过公开应用接口。

## Development workflow

1. 先检查工作区，保护已有修改。
2. 在机器清单定位功能 ID，核对页面、用例、状态机、表、权限、契约、事件和验收，开始时标记 `IN_PROGRESS`。
3. 明确验收场景、领域所有者、风险、权限、契约和数据迁移。
4. 按“契约/状态机 -> 安全分析 -> migration -> 领域/应用 -> 适配/事件 -> 前端 -> 测试/文档”实施。
5. REST/OpenAPI、NATS/AsyncAPI、gRPC/protobuf、MCP/Skill/Capability JSON Schema 契约先行。
6. 同时处理 i18n、审计、幂等、错误、日志、指标和 Trace。
7. 测试成功、失败、超时、取消、重复、乱序、断网、重连和恢复。
8. 如实报告已运行和未运行的测试；证据齐全后才标记 `DONE`。

禁止：

- 用 Mock、TODO、硬编码成功响应宣称完成。
- Controller/MCP Handler 直接访问 Mapper、ROS 或 SDK。
- 修改已发布 Flyway migration。
- 使用 `latest`、浮动依赖、真实 Secret 或生产数据。
- 关闭 TLS、鉴权、Schema 校验或 Safety Gateway 来让测试通过。
- 一次生成大量空服务/空页面后宣称平台完成。

## Current repository state

当前实施状态只以机器清单和真实证据为准。若 `F-ENGINEERING-001`、`F-DEPLOY-001` 尚未 `DONE`，不要假装 M0 脚本或服务已存在；按详细规范第 18 节从未完成的最早阶段继续，优先交付可运行垂直闭环。

当前设计追踪校验：

```text
python scripts/validate_platform_manifest.py
```

M0 完成后统一入口必须为：

```text
pwsh ./scripts/dev.ps1 doctor|bootstrap|infra-up|migrate|dev|sim-up|test|e2e|down
./scripts/dev.sh doctor|bootstrap|infra-up|migrate|dev|sim-up|test|e2e|down
```

功能完成必须同时包含契约、实现、迁移、权限、i18n、审计、测试、部署和文档。整个平台必须满足机器清单全部适用项 `DONE`、详细规范 C01-C24 全部通过、实施蓝图映射无缺项，并通过 `python scripts/validate_platform_manifest.py --require-complete`。
