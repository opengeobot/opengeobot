<!--
Function: C16 adapter contract note — sim path covered; HIL deferred to lab
Time: 2026-07-10
Author: AxeXie
-->

# C16 Adapter Contract Note

## Scope covered in sim

- Seed robot model: `mdl_01J00000000000000000000001`
- Acceptance skeleton: `GET /api/v1/adapters/compatibility/{robotModelId}` via `scripts/acceptance/run_c13_c16_sim.py`
- Result file: `reports/acceptance/C16-result.md`
- Sim / ROS2 main path remains the only path asserted in CI/local sim vertical loop
- Capability contract must not bypass Safety Gateway or call `/cmd_vel` / vendor SDK directly

## HIL deferred to lab

The following adapter families are **not** executed in this repository's automated sim acceptance:

| Adapter family | Status | Evidence |
|----------------|--------|----------|
| ROS2 / simulation | Sim skeleton only | `C16-result.md` |
| ROS1 | HIL deferred to lab | `reports/hil/F-ADAPTER-002-hil-summary.md` |
| Unitree | HIL deferred to lab | lab runbook when scheduled |
| Custom protocol | HIL deferred to lab | lab runbook when scheduled |

Do **not** mark `F-ADAPTER-002` / C16 as DONE based solely on the sim compatibility GET. Lab HIL must produce separate evidence before DONE.
