# Function: ROS2 Jazzy adapter scaffold (Phase 7 lab) — replaces sim skills with rclpy
# Time: 2026-07-10
# Author: AxeXie

# OpenGeoBot ROS2 Adapter (lab)

This package is the **laboratory** replacement path for `services/sim-adapter/`.
It is intentionally a scaffold until HIL prerequisites in
`docs/runbooks/hil-lab-procedure.md` are met.

## Contract

- Same NATS subjects and Skill/Capability interface as sim-adapter.
- No direct `/cmd_vel` from Agent/LLM.
- All motion goes through registered skills after Safety Gateway ALLOW.

## Status

- Implementation: scaffold / not wired into Compose `full` profile by default.
- HIL: NOT EXECUTED in CI.

## Next steps

1. Add `rclpy` skill implementations behind the existing Skill protocol.
2. Pin base image to ROS 2 Jazzy (no `latest`).
3. Run safety regression in sim, then staged HIL.
