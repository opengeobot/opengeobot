<!--
Function: Design coverage audit — post Phase 0–7 gap remediation
Time: 2026-07-10
Author: AxeXie
-->

# 设计闭环与架构一致性审计

审计日期：2026-07-10（Phase 0–7 差距修复后）

## 1. 结论

审计结论为：**设计治理通过；仿真可验收范围内的基线功能已闭环；真机/HIL/Zenoh 全量 C15 仍为实验室待办。**

```text
29 个基线功能 implementation_status: DONE
validate_platform_manifest.py --require-complete: PASS
外部契约 6 项: PINNED
```

诚实边界：

| 项 | 状态 |
| --- | --- |
| 蓝图缺失页面 | 已补齐独立路由（Edge/型号/分组/模板/审批/场景禁区/监控拆分/控制租约/失败案例/改进等） |
| C01–C24 测试计划 | `docs/test-plans/c01-c22-test-plan.md` + `c23-c24-test-plan.md` |
| 可执行验收 | `scripts/acceptance/`；多数 Cxx PASS；C03 需重启含 V25 的云控；C15 Zenoh 全量 FAIL/实验室；C16 需含 Adapter 的云控进程 |
| K8s + CI | `deploy/kubernetes/` + `.github/workflows/ci.yml` |
| 真机 HIL | `reports/hil/*` NOT EXECUTED；程序见 `docs/runbooks/hil-lab-procedure.md` |
| ROS2 真机适配 | `services/ros2-adapter/` 为实验室脚手架，默认仍用 sim-adapter |

## 2. 阶段交付摘要

| Phase | 内容 |
| --- | --- |
| 0 | 诚实回写、审计/Runbook 纠偏 |
| 1 | C01–C22 计划 + C01/C02/C23/C24 验收脚本与证据 |
| 2 | 蓝图缺失页面 + Edge/ControlLease 后端 |
| 3 | C03–C12 / C17–C19 仿真验收骨架 |
| 4 | C13/C14/C16 仿真验收 + HIL 说明 |
| 5 | K8s 基线清单 + GitHub CI |
| 6 | P-FAIL-001 / P-IMPROVE-001 + C20；改进批准不自动下发运动 |
| 7 | HIL 实验室程序 + ros2-adapter 脚手架 |

## 3. 运维注意

部署新 Edge Gateway / Control Lease 能力需执行：

```bash
./scripts/dev.sh migrate   # 应用 V25
# 重启 cloud-control 以加载 EdgeGatewayController / ControlLeaseController
```

然后重跑：

```bash
python3 scripts/acceptance/run_c03_c12_sim.py
python3 scripts/acceptance/run_c13_c16_sim.py
```

## 4. 最终平台完成仍须

1. C15 Zenoh 实验室证据 PASS  
2. F-ADAPTER-002 / F-OTA-001 HIL 证据  
3. 蓝图与实现持续一致  
4. `--require-complete` 持续通过  

## 5. 控制台契约漂移修复（2026-07-10）

对照蓝图相关页面做了本轮触及范围的合规复查与修复（不扩大重写）：

| 页面 / 能力 | 检查点 | 结果 |
| --- | --- | --- |
| P-ROBOT-002 | 详情路由使用 `robot_id`，与 OpenAPI/DTO 一致 | 已修；`GET /robots/{robot_id}` 冒烟 200 |
| P-SKILL-002 | 详情路由使用 `skill_id`，展示 `name` | 已修；`GET /skills/{skill_id}` 冒烟 200 |
| P-MISSION-005 | 审批路由 `mission.mission.read`；列表 `status=READY`；批准按钮仍校验 `mission.mission.approve` | 已修；可进入，不再静默跳转工作台 |
| P-CAP-001 | 能力目录按 `skill.name` / `RobotCapability.capability_type` 聚合 | 已修；不再把 capability 对象当 string |
| P-CONTROL / SM-SAFETY | UI 使用 `state`；未急停禁用复位；409 文案 `safety.reset_requires_estop`；路由 `safety.decision.read` | 已修；NORMAL→409，E-STOP→reset→NORMAL |

证据：Vitest 208 PASS；`pnpm build` PASS；web-console 镜像重建并重启；API 冒烟见本会话记录。

**ROSClaw sandbox（并行）：** `rosclaw 1.0.1` @ `0839828`；`sandbox validate/run ur5e tabletop reach` **PASS**。证据见 `reports/acceptance/rosclaw-sandbox-sim-result.md`。**非本轮范围：** ROSClaw NATS 主路径接入仍待真实契约锁定后实施；sandbox 仅并行仿真验证，不得宣称平台主路径 DONE。
