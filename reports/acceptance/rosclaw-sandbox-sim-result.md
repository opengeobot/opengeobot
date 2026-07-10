<!--
Function: Acceptance evidence — ROSClaw sandbox parallel sim
Time: 2026-07-10
Author: AxeXie
-->

# ROSClaw Sandbox Sim Result

| Field | Value |
| --- | --- |
| Date | 2026-07-10 |
| Verdict | **PASS** (sandbox only) |
| Package | rosclaw 1.0.1 |
| Commit | `0839828d89f989cf2a71d7d75ca60527bdbea84b` |
| Platform NATS main path | **NOT wired** — parallel verification only |

## Commands

```text
rosclaw firstboot --yes --profile offline --no-telemetry --enable-sandbox --robot sim_ur5e
rosclaw doctor
rosclaw sandbox validate ur5e
rosclaw sandbox run --robot ur5e --world tabletop --task reach
```

Note: INSTALL.md mentions `sim_ur5e`; e-URDF-Zoo robot id present in this commit is `ur5e`. Validation of `sim_ur5e` failed with “Robot directory not found”; `ur5e` passed.

## Results

| Step | Result | Notes |
| --- | --- | --- |
| firstboot offline | PASS (READY_WITH_WARNINGS) | ROS 2 not installed (expected for sandbox-only) |
| doctor | PASS with warnings | pytest / RealSense / ROS2 optional gaps |
| sandbox validate ur5e | PASS | |
| sandbox run ur5e/tabletop/reach | PASS | status=success, steps=100, duration=5.00s, final_error=0.020m |

## Log excerpts

```text
[ROSClaw] ✅ Sandbox validation passed for ur5e

Sandbox Episode Result
Episode ID: sb_ur5e_reach_1783676238
Status:     success
World:      tabletop
Backend:    mujoco
Steps:      100
Duration:   5.00s
Final Error:0.020m
```

Supporting logs (host): `/tmp/rosclaw-firstboot.log`, `/tmp/rosclaw-doctor-final.log`, `/tmp/rosclaw-validate-ur5e.log`, `/tmp/rosclaw-sandbox-run.log`

## Boundaries (do not over-claim)

- Does **not** mean OpenGeoBot edge NATS ↔ ROSClaw adapter is DONE.
- Platform Safety Gateway remains authoritative for OpenGeoBot physical actions.
- Agent must not command `/cmd_vel` or vendor SDKs directly.
