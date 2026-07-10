<!--
Function: F-OTA-001 HIL summary — OTA campaign hardware-in-the-loop
Time: 2026-07-10
Author: AxeXie
-->
# F-OTA-001 HIL Summary — OTA Deployment

## HIL Scope
- Feature: F-OTA-001 (OTA packages and staged campaigns)
- Requires edge delivery and rollback verification on real devices

## HIL Status
- NOT EXECUTED in CI environment
- Simulation / API contract verification completed
- Real-device HIL deferred to hardware lab per `docs/runbooks/hil-lab-procedure.md`

## HIL Prerequisites
1. Safety regression PASS in simulation
2. Staged rollout policy and approval path verified
3. Automatic rollback path verified in sim
4. Target device enrolled with active edge gateway certificate

## HIL Evidence
- No real-device evidence in this cycle
- Lab procedure: docs/runbooks/hil-lab-procedure.md
