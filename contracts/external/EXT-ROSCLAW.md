<!--
Function: EXT-ROSCLAW pinned reference — ROSClaw Edge Runtime contract
Time: 2026-07-10
Author: AxeXie
-->

# EXT-ROSCLAW — ROSClaw Edge Runtime

## Pinned Reference

- Official source: https://www.rosclaw.io/runtime
- Installer: https://www.rosclaw.io/get
- Upstream repo: https://github.com/ros-claw/rosclaw
- Pin status: PINNED
- Verified install (this lab):
  - Package: `rosclaw==1.0.1`
  - Git commit: `0839828d89f989cf2a71d7d75ca60527bdbea84b`
  - Install path: `~/.rosclaw/lib/rosclaw` (venv: `~/.rosclaw/venv`)
  - Platform NATS adapter wiring: **not** claimed DONE (sandbox parallel only)

## Contract

- 边缘为 ROSClaw Edge Runtime + Safety Gateway + Local Skill Executor
- 边缘安全判定最终有效；本地急停不依赖云端或网络
- ROS2 主路径；ROS1 仅隔离兼容
- OpenGeoBot Agent **不得**直连 `/cmd_vel` / 关节 / 厂商 SDK；物理动作须经已注册 Skill + Safety Gateway

## Sandbox verification (parallel, not main path)

Commands used (official CLI; zoo robot id is `ur5e`, not `sim_ur5e`):

```bash
rosclaw firstboot --yes --profile offline --no-telemetry --enable-sandbox
rosclaw doctor
rosclaw sandbox validate ur5e
rosclaw sandbox run --robot ur5e --world tabletop --task reach
```

Evidence: `reports/acceptance/rosclaw-sandbox-sim-result.md`

## Required By

- F-EDGE-002 (Local execution, offline cache & reconciliation)
- F-ADAPTER-001 (ROS2 simulation & primary path adapter)
