<!--
Function: C15 weak-network / Zenoh acceptance note — lab deferred
Time: 2026-07-10
Author: AxeXie
-->

# C15 Acceptance Result

- Timestamp: 2026-07-10
- Status: **FAIL** (lab deferred — not N/A; edge offline cache exists in sim)
- Scope: Zenoh weak-network bridge + offline cache + reconnect reconciliation

## Details

- Edge offline cache + JetStream durable consumers implemented in `edge/gateway` (sim-level C15 partial).
- Zenoh ROS2DDS bridge not deployed in default Compose; see `contracts/external/EXT-ZENOH-ROS2DDS.md` and `docs/runbooks/hil-lab-procedure.md`.
- Full C15 PASS requires lab Zenoh bridge evidence.

## Partial sim evidence

- Offline cache unit tests in edge/gateway
- JetStream durable consumer recovery tests across Python services
