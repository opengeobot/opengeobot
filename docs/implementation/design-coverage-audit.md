# 设计闭环与架构一致性审计

审计日期：2026-07-03

## 1. 结论

审计结论为：**设计治理有条件通过，平台实现未开始。**

修订后的事实源已经能够指导 AI 编程 IDE 从 M0 开始，按“需求/契约/数据/实现/测试/部署/运维/证据”完成纵向闭环，并通过机器门禁防止技术栈、公共能力、阶段和证据漂移。业务集成功能仍有一个有意保留的前置门禁：QwenPaw、ROSClaw、ROS/Zenoh 和厂商 SDK 的实际版本尚未锁定；对应 `external_contracts` 为 `UNPINNED`，其消费者不得进入 `IN_PROGRESS`。

当前真实实施状态：

```text
29 个基线功能
42 个页面 ID
107 个后端用例 ID
31 个状态机 ID
C01-C24 全部覆盖
29 个功能全部 NOT_STARTED
```

因此，日常设计校验必须通过，完整实施门禁必须失败。不能把“文档可执行”表述为“平台已交付”。

## 2. 审计方法

本次核查覆盖：

1. 上游 PDF 32 页的章节、技术候选、业务流程和版式抽检。
2. AI 开发规范的工程、安全、公共能力、测试、部署和运维要求。
3. 实施蓝图的 42 个页面、107 个应用用例、31 个状态机、事务和断网恢复流程。
4. 机器清单、JSON Schema 和校验器的双向追踪与完成证据规则。
5. QwenPaw、ROSClaw、ROS2 和 Zenoh 的官方资料边界。

PDF 抽检页面未发现裁切、重叠、乱码或不可读表格。PDF 第 4、15 节保留多候选技术，属于上游业务设计的历史选择空间，已由 ADR-0001 收敛，不直接作为工程选型事实源。

## 3. 发现与修复

| 原缺口 | 风险 | 修复 |
| --- | --- | --- |
| PDF 与规范存在多套候选技术，前端“Vue / React”含义不明确 | AI 可能自行选择 Go/React/Kafka 等并复制双栈 | 新增 ADR-0001；Vue 3 为唯一默认主栈，React 仅 ADR 例外 |
| 技术基线只存在于自然语言 | 文档改一处即可静默漂移 | 清单新增机器可校验的 `architecture`，Schema 使用固定编码 |
| 公共能力只有文字归属，没有功能复用声明 | 各领域容易复制用户、权限、字典、i18n、审计和幂等 | 新增 `platform_capabilities` 唯一所有者与 `platform_capability_profiles` |
| 机器清单没有部署目标 | 功能可能只做到源码和接口 | 每个功能新增 `deployment_targets` |
| `DONE` 只要求功能自选证据，部分功能没有 Runbook | 测试通过但不可部署、不可观察、不可运维 | 新增全局 `mandatory_done_evidence`：部署、可观测、安全、测试、Runbook |
| 证据校验只判断路径存在 | 空文件、根目录或共享报告可伪造完成 | 校验器新增类别目录、非空文件、过宽路径禁用及功能 ID 范围检查 |
| 页面/用例/状态机仅做“清单到蓝图”单向检查 | 蓝图新增功能可能未进入清单 | 改为双向精确 ID 校验，并拒绝 `UC-X-001-004` 形式的歧义范围 |
| M2 仿真与 M3 硬件适配合并，且 Safety 被强制要求 HIL | M2 仿真闭环永远无法在无硬件环境完成 | 拆为 F-ADAPTER-001（M2 ROS2/仿真）与 F-ADAPTER-002（M3 兼容/HIL）；Safety M2 不强制 HIL |
| 外部 API 只靠文字提醒不得猜测 | AI 仍可能在未锁版本时开始实现 | 新增 `external_contracts`；消费者活跃时必须 `PINNED` 且锁定参考非空 |
| 后续阶段可提前标记进行中 | AI 可能绕过 M0/M1 横向铺空壳 | 校验器新增阶段门禁 |

## 4. 统一技术架构判定

| 层级 | 生效基线 | 判定 |
| --- | --- | --- |
| 云端后端 | Java 21 + Spring Boot 3.x + MyBatis-Plus；Agent/适配用 Python 3.12 | 已固化 |
| 前端 | Vue 3 + TypeScript + Vite + WebSocket；React 仅隔离 ADR 例外 | 已固化 |
| Agent Runtime | QwenPaw，经 `AgentRuntimeProvider` | 已固化，版本待 M0 锁定 |
| 工具协议 | MCP 优先，HTTP/gRPC 辅助 | 已固化 |
| 边缘运行时 | ROSClaw Edge Runtime + 平台 Safety/Executor | 已固化，版本待 M0 锁定 |
| ROS2 / ROS1 | ROS2 主路径；ROS1 隔离兼容 | 已固化 |
| 弱网 | Zenoh / ROS2DDS bridge | 已固化 |
| 数据 | PostgreSQL；TimescaleDB 可选；pgvector | 已固化 |
| 对象 | MinIO / S3 | 已固化 |
| 消息 | NATS + JetStream | 已固化 |
| 监控 | vmagent + VictoriaMetrics + Grafana | 已固化 |
| 日志审计 | Vector + Loki + PostgreSQL 审计表 | 已固化 |
| 告警 | Grafana Alerting / 平台告警服务 | 已固化 |

QwenPaw 官方文档将其定义为基于 AgentScope Runtime 的个人助理产品，并支持 stdio/HTTP/SSE MCP 客户端；这支持“平台向 QwenPaw 暴露受控 MCP Server”的集成方向，但不等于存在稳定的机器人控制 API。ROSClaw 官方 Runtime 页面把能力区分为 Stable、Experimental 和 Research；规范已要求后两类默认关闭。

## 5. 开发到运维闭环

| 生命周期 | AI 可执行输入 | 完成门禁 |
| --- | --- | --- |
| 需求与设计 | 功能 ID、页面、用例、状态机、C01-C24 | 双向追踪无遗漏 |
| 契约 | REST、WebSocket、MCP、NATS、gRPC、Skill/Capability | 契约先行与兼容检查 |
| 数据 | 唯一 owner schema、表、约束、状态历史 | Flyway 空库/升级测试 |
| 实现 | Java/Python/Vue/Edge 部署目标与依赖方向 | 禁止 Controller/Handler 直连 Mapper/SDK |
| 公共能力 | profile 与唯一 owner | 架构测试禁止重复实现 |
| 测试 | Unit/Contract/Integration/Component/Simulation/E2E/Security/Recovery/HIL | 适用层级全部有真实报告 |
| 部署 | Compose/Kubernetes、配置、Secret、健康、迁移 | 部署/升级/回滚非空证据 |
| 运维 | 指标、日志、Trace、告警、Runbook、备份恢复 | 可观测与恢复证据 |
| 完成声明 | 功能证据 + 全局强制证据 | `--require-complete` 通过 |

## 6. 平台公共能力一致性

清单已固定以下唯一所有者：

- F-ENGINEERING-001：契约治理、错误模型、事件信封、Trace 上下文、时间/ID 和可观测基础。
- F-PLATFORM-001：身份。
- F-PLATFORM-002：授权与数据范围。
- F-PLATFORM-003：字典与国际化。
- F-PLATFORM-004：配置、审计与幂等。
- F-MEDIA-001：对象元数据。
- F-SAFETY-001：边缘安全执行。
- F-OPS-001：运维治理。

所有功能必须选择一个公共能力 profile。任何 `requires_edge_safety: true` 的功能，其 profile 必须包含 `SAFETY_ENFORCEMENT`，并要求仿真和安全测试。这样既阻止复制公共模块，也阻止云端动作功能遗漏边缘安全依赖。

## 7. 当前门禁结果

日常设计校验：

```text
python scripts/validate_platform_manifest.py
PASS: features=29; pages=42; use_cases=107; state_machines=31; C01-C24 covered; NOT_STARTED=29
```

完整实施门禁：

```text
python scripts/validate_platform_manifest.py --require-complete
```

当前必须失败 29 项，因为没有任何平台代码、迁移、部署、测试、安全、可观测或 Runbook 完成证据。

## 8. 仍需在实施阶段完成

1. M0 创建锁文件、统一开发脚本、Compose/Kubernetes、CI 和报告目录。
2. 把所有外部契约从 `UNPINNED` 变为带非空锁定参考的 `PINNED`；不得预填虚假版本。
3. 为 C01-C24 建立可执行测试计划与真实报告。
4. 从 F-ENGINEERING-001 和 F-DEPLOY-001 开始，不得直接跳到业务页面或机器人接入。
5. 真实设备/HIL 只在受控环境执行；未执行时不得生成 HIL 通过报告。

最终判定：文档现在足以驱动诚实、分阶段、可验证的闭环开发；它不会也不应让 AI 在外部版本未锁定、代码未实现或真实测试未执行时宣称平台完成。
