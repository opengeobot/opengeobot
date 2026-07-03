# 平台实施清单

本目录把平台设计转换为 AI 可核查的实施清单。

## 文件

- `platform-feature-manifest.yaml`：每个功能的阶段、页面、后端用例、表、状态机、权限、契约、事件、观测和验收映射。
- `platform-feature-manifest.schema.json`：清单 JSON Schema。
- `design-coverage-audit.md`：用户目标、PDF 章节、技术栈和状态一致性的覆盖审计。
- `../平台功能与数据状态统一实施蓝图 V1.0.md`：映射项的完整业务说明。

## 状态规则

```text
NOT_STARTED -> IN_PROGRESS -> DONE
```

- 开始实现时改为 `IN_PROGRESS`。
- 只有 `verification.done_evidence` 要求的制品全部存在并通过测试后，才能改为 `DONE`。
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
  test_reports:
    - reports/tests/F-MISSION-001.json
  runbook:
    - docs/runbooks/mission-planning.md
```

路径必须真实存在；不得创建空文件充当证据。

## 校验

日常结构和追踪校验：

```text
python scripts/validate_platform_manifest.py
```

全平台完成门禁：

```text
python scripts/validate_platform_manifest.py --require-complete
```

第二条命令只有在 28 个基线功能全部 `DONE` 且证据路径完整时才通过。M0 建立 Python 开发依赖时，必须把 `PyYAML` 和 `jsonschema` 固定到锁文件，并在 CI 中运行日常校验；发布流水线运行完整门禁。

## 修改规则

- 新增/删除功能、页面、用例或状态机必须同步修改实施蓝图和清单。
- `C01-C24` 必须始终全部被至少一个功能覆盖。
- 清单中所有页面 ID、用例 ID、状态机 ID 和功能 ID 必须能在实施蓝图中找到。
- Schema、校验器和清单必须在同一个变更中更新。
