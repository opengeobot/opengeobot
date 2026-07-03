# 平台实施清单

本目录把平台设计转换为 AI 可核查的实施清单。

## 文件

- `platform-feature-manifest.yaml`：每个功能的阶段、页面、后端用例、表、状态机、权限、契约、事件、观测和验收映射。
- `platform-feature-manifest.schema.json`：清单 JSON Schema。
- `design-coverage-audit.md`：用户目标、PDF 章节、技术栈和状态一致性的覆盖审计。
- `../平台功能与数据状态统一实施蓝图 V1.0.md`：映射项的完整业务说明。
- `../adr/ADR-0001-统一技术基线与前端主栈.md`：PDF 候选项与当前统一架构之间的决策记录。

清单 Schema 1.1 的顶层字段不是背景说明，而是每个功能都必须读取的约束：

- `architecture`：用户指定技术基线的机器编码。
- `platform_capabilities`：公共能力到唯一所有者功能的映射。
- `platform_capability_profiles`：功能必须复用的公共能力集合。
- `external_contracts`：QwenPaw、ROSClaw、ROS/Zenoh 和厂商 SDK 的锁定门禁。
- `mandatory_done_evidence`：所有功能完成时都必须提供的部署、可观测、安全、测试和 Runbook 证据。

## 状态规则

```text
NOT_STARTED -> IN_PROGRESS -> DONE
```

- 开始实现时改为 `IN_PROGRESS`。
- M1-M6 功能进入 `IN_PROGRESS` 前，更早阶段的功能必须全部为 `DONE` 或经 ADR 批准为 `NOT_APPLICABLE`。
- 消费的外部契约必须先从 `UNPINNED` 改为 `PINNED`，且 `locked_reference` 指向仓库内非空的锁定参考。
- 只有 `verification.done_evidence` 要求的制品全部存在并通过测试后，才能改为 `DONE`。
- `mandatory_done_evidence` 与功能自己的 `verification.done_evidence` 取并集，不能被功能条目删减。
- `DONE` 功能必须增加 `evidence`，按类别列出仓库相对路径。
- `NOT_APPLICABLE` 只允许经 ADR 移除功能时使用，并填写 `not_applicable_reason`；完整平台验收仍要求所有基线功能为 `DONE`。

示例：

```yaml
implementation_status: DONE
evidence:
  contracts:
    - contracts/openapi/mission-v1.yaml
  migrations:
    - apps/cloud-control/src/main/resources/db/migration/V020__mission.sql
  backend:
    - apps/cloud-control/mission/
  frontend:
    - apps/web-console/src/features/mission/
  events:
    - contracts/asyncapi/mission-events-v1.yaml
  deployment:
    - reports/deployment/F-MISSION-001-compose.json
  observability:
    - reports/observability/F-MISSION-001.json
  security_reports:
    - reports/security/F-MISSION-001.json
  test_reports:
    - reports/tests/F-MISSION-001.json
  runbook:
    - docs/runbooks/mission-planning.md
```

路径必须真实存在并包含非空文件；空文件、空目录、`tmp/`、`.git/` 和 `apps/`、`contracts/`、`deploy/` 等过宽根目录均不能作为证据。部署、可观测、安全、测试和 HIL 证据至少有一个路径包含对应功能 ID，防止用同一个无范围报告为所有功能背书。校验器还按类别限制根目录：

| 类别 | 允许的主要位置 |
| --- | --- |
| 契约/事件 | `contracts/` |
| 迁移 | `apps/` |
| 前端 | `apps/web-console/` |
| 部署 | `deploy/`、`reports/deployment/` |
| 可观测 | `deploy/observability/`、`reports/observability/`、`apps/` |
| 安全报告 | `reports/security/` |
| 测试报告 | `reports/tests/` |
| Runbook | `docs/runbooks/` |
| HIL | `reports/hil/` |

同一个“报告存在”不能代替报告内容的人工/CI 审核；机器校验只负责最低限度的路径、非空和追踪门禁。

## 校验

日常结构和追踪校验：

```text
python scripts/validate_platform_manifest.py
```

全平台完成门禁：

```text
python scripts/validate_platform_manifest.py --require-complete
```

第二条命令只有在 29 个基线功能全部 `DONE` 且功能特定证据、全局强制证据均合格时才通过。M0 建立 Python 开发依赖时，必须把 `PyYAML` 和 `jsonschema` 固定到锁文件，并在 CI 中运行日常校验；发布流水线运行完整门禁。

## 修改规则

- 新增/删除功能、页面、用例或状态机必须同步修改实施蓝图和清单。
- 页面、用例和状态机执行双向校验：清单引用必须在蓝图存在，蓝图中的精确 ID 也必须被清单追踪；禁止使用形如 `UC-X-001-004` 的歧义范围伪装成 ID。
- `C01-C24` 必须始终全部被至少一个功能覆盖。
- 公共能力所有者、技术基线或前端主栈变化必须先有 ADR，再同步清单、Schema、校验器和规范。
- `requires_edge_safety: true` 的功能必须使用包含 `SAFETY_ENFORCEMENT` 的公共能力 profile，并包含仿真和安全测试。
- F-ADAPTER-001 是 M2 ROS2/仿真主路径；F-ADAPTER-002 是 M3 ROS1/Unitree/Custom 兼容及 HIL。不得合并后用 HIL 阻塞 M2 仿真闭环。
- Schema、校验器和清单必须在同一个变更中更新。
