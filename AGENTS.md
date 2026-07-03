# OpenGeoBot / 一脑多控平台开发指令

本文件适用于整个仓库。详细且唯一的工程事实源是：

- `docs/AI开发约束与平台公共能力规范 V1.0.md`
- `docs/平台功能与数据状态统一实施蓝图 V1.0.md`
- `docs/implementation/platform-feature-manifest.yaml`
- 上游业务设计：`docs/一脑多控平台详细设计说明书 V1.0.pdf`

工程/安全以 AI 开发约束为准；页面、后端用例和数据状态以实施蓝图为准；端到端制品范围以机器清单为准。开始任务前必须读取对应功能 ID 的全部映射。冲突时停止并用 ADR 解决；涉及安全时先取更严格规则。

## 当前阶段

当前实施状态只以机器清单的 `implementation_status` 和真实证据为准。若 `F-ENGINEERING-001`、`F-DEPLOY-001` 尚未 `DONE`，不得假装 M0 统一脚本、服务或测试已经存在；应按详细规范第 18 节从未完成的最早阶段继续，按可运行垂直闭环交付。

## 不可违反

1. Agent/LLM 禁止直接调用 `/cmd_vel`、关节、电机、原始 UDP 或厂商 SDK。
2. 所有物理动作必须是已注册 Skill/Capability，并经过权限、任务策略、调度和边缘 Safety Gateway。
3. 边缘安全判定和本地急停优先；云端、QwenPaw 和管理员都不能绕过。
4. QwenPaw、ROSClaw、ROS、Zenoh、设备 SDK 的 API 不得凭记忆编造；只使用仓库锁定版本的真实契约/官方文档。
5. 用户、组织、角色、权限、数据字典、国际化、配置、审计、错误、幂等、事件和对象元数据必须复用平台公共能力。
6. 服务/模块不得直接读写其他领域的表；通过公开应用接口或版本化事件协作。
7. REST、MCP、NATS、gRPC、Skill、Capability 必须契约先行。
8. 任务、审批、调度、安全判定、工具调用、人工干预和真实动作必须用 `trace_id` 串联。
9. 未经仿真与安全回归的运动变更禁止连接真实机器人。
10. 禁止提交 Secret、生产数据、浮动依赖、`latest` 镜像、伪造测试结果或用 Mock/TODO 宣称功能完成。

## 固定技术方向

- 云端管理：Java 21 + Spring Boot 3.x + MyBatis-Plus，模块化单体优先。
- Agent/适配：Python 3.12 + uv；QwenPaw 通过 `AgentRuntimeProvider` 接入。
- 前端：Vue 3 + TypeScript + Vite + WebSocket，不并行建设 React 版本。
- 工具：MCP 优先；HTTP/gRPC 辅助。
- 边缘：ROSClaw Edge Runtime + Safety Gateway + Local Skill Executor。
- ROS2 主路径；ROS1 仅隔离兼容；弱网使用 Zenoh。
- PostgreSQL/可选 TimescaleDB、pgvector、MinIO/S3、NATS JetStream。
- vmagent + VictoriaMetrics + Grafana；Vector + Loki + PostgreSQL 审计。

## 工作顺序

1. 检查工作区和已有修改。
2. 在机器清单中定位功能 ID，核对页面、用例、状态机、表、权限、契约、事件和验收。
3. 定位领域所有者、公共能力和安全级别，将功能标记为 `IN_PROGRESS`。
4. 先写验收场景和契约，再写迁移、领域/应用、适配、前端。
5. 同时实现权限、i18n、审计、幂等、错误和可观测性。
6. 覆盖成功、失败、超时、取消、重复、乱序、断网和恢复。
7. 运行实际可用的测试，明确未运行的 HIL/真实设备测试。
8. 同步 README、Runbook、ADR 和证据；全部门禁通过后才标记 `DONE`。

禁止为赶进度复制一套公共用户/权限/字典/i18n，禁止 Controller/MCP Handler 直接访问 Mapper/SDK，禁止修改已发布迁移。

## 预期统一命令

当前即可运行设计追踪校验：

```text
python scripts/validate_platform_manifest.py
```

M0 完成后，开发入口必须是：

```text
pwsh ./scripts/dev.ps1 doctor|bootstrap|infra-up|migrate|dev|sim-up|test|e2e|down
./scripts/dev.sh doctor|bootstrap|infra-up|migrate|dev|sim-up|test|e2e|down
```

若脚本尚不存在，按规范实现它们；不要另造长期入口。`down` 不得删除数据，任何 reset 必须独立且显式确认。

## 完成标准

只有契约、实现、迁移、权限、i18n、审计、测试、部署和文档共同闭环，功能才算完成。整个平台完成必须同时满足：机器清单全部适用功能为 `DONE`，详细规范 C01-C24 全部有直接证据，实施蓝图中的页面/后端/状态映射无缺项，并且 `python scripts/validate_platform_manifest.py --require-complete` 通过。
