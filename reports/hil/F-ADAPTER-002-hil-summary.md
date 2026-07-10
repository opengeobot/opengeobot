<!--
Function: F-ADAPTER-002 HIL summary — ROS1 Unitree & custom protocol adapter hardware-in-the-loop
Time: 2026-07-10
Author: AxeXie
-->
# F-ADAPTER-002 HIL Summary — ROS1 Unitree & Custom Protocol Adapter

## HIL Scope
- Feature: F-ADAPTER-002 (ROS1 Unitree & custom protocol compatibility adapter)
- Risk level: R3_HIGH_RISK, requires_edge_safety: true

## HIL Status
- NOT EXECUTED in CI environment
- Simulation parity verified (adapter compatibility schema, capability-port, vendor-adapter-envelope)
- Real-device HIL deferred to hardware lab per `docs/runbooks/hil-lab-procedure.md`
- Lab scaffold: `services/ros2-adapter/` (ROS2 path) and `services/ros1-adapter/` (compat path)

## HIL Prerequisites
1. External contracts pinned: EXT-ROS1-BRIDGE, EXT-UNITREE-SDK
2. Safety Gateway regression passed in simulation
3. ROS1 bridge running in isolated compatibility mode (no impact on ROS2 primary path)
4. Edge Safety Gateway final authority verified; local e-stop latched and network-independent

## HIL Test Plan (Deferred)
- Unitree Go2 stand-up / sit-down via registered Skill (not direct SDK)
- ROS1 topic bridge health and command translation
- Adapter failover and reconnection recovery
- Emergency stop propagation through adapter layer

## HIL Evidence
- No real-device evidence captured in this cycle
- Simulation evidence: reports/tests/F-ADAPTER-002-test-summary.html
- Acceptance note: reports/acceptance/C16-adapter-contract-note.md
