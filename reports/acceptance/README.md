<!--
Function: Acceptance evidence directory — how to run C01–C24 acceptance skeletons
Time: 2026-07-10
Author: AxeXie
-->

# Acceptance 证据目录

本目录存放平台闭环验收（C01–C24）结果文件，命名约定：`Cxx-result.md`。

状态取值：`PASS` / `FAIL` / `NOT_APPLICABLE`。  
**C09、C10、C11、C19、C24 不得标记 `NOT_APPLICABLE`。**

## 测试计划

| 范围 | 文档 |
|------|------|
| C01–C22 | [docs/test-plans/c01-c22-test-plan.md](../../docs/test-plans/c01-c22-test-plan.md) |
| C23–C24 | [docs/test-plans/c23-c24-test-plan.md](../../docs/test-plans/c23-c24-test-plan.md) |
| M2 sim 垂直环 | [docs/test-plans/m2-sim-vertical-loop.md](../../docs/test-plans/m2-sim-vertical-loop.md) |

## 可运行骨架

前置：云控制面可达时再跑 HTTP 类验收（例如 `./scripts/dev.sh infra-up && ./scripts/dev.sh migrate && ./scripts/dev.sh dev`；sim 闭环另加 `sim-up`）。

```bash
# C01 + C02（登录后检查用户/组织/角色/权限/审计与字典/i18n）
python3 scripts/acceptance/run_c01_c02.py

# C03–C12（edge/robots/skills/mcp/missions/policies/safety）
python3 scripts/acceptance/run_c03_c12_sim.py

# C13/C14/C16（fleet schedule/conflicts/failovers + adapter compatibility）
python3 scripts/acceptance/run_c13_c16_sim.py

# C17–C19（maps/media/traces）
python3 scripts/acceptance/run_c17_c19_sim.py

# C23 + C24 结构检查（dev.sh + compose.yml + deploy/kubernetes/ + .github/workflows/ci.yml）
python3 scripts/acceptance/run_c23_c24_check.py
```

可选环境变量（C01/C02）：

| 变量 | 默认 |
|------|------|
| `OPENGEOBOT_BASE_URL` | `http://localhost:8080` |
| `OPENGEOBOT_USERNAME` | `admin` |
| `OPENGEOBOT_PASSWORD` | `admin123` |
| `OPENGEOBOT_HTTP_TIMEOUT` | `15` |

API 不可达时脚本写入 `FAIL` 结果并以退出码 1 结束（不会抛出未处理异常）。

## 与 `./scripts/dev.sh test` 的关系

`dev.sh test` 仍只跑单元/组件测试。验收骨架为可选补充，不阻断现有测试流；可在 `cmd_test` 附近注释中找到入口提示。
