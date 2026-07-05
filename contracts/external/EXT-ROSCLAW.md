<!--
Function: EXT-ROSCLAW pinned reference — ROSClaw Edge Runtime contract
Time: 2026-07-05
Author: AxeXie
-->

# EXT-ROSCLAW — ROSClaw Edge Runtime

## Pinned Reference
- Official source: https://www.rosclaw.io/runtime
- Pin status: PINNED
- Version: M2 simulation (edge runtime simulated via cloud-control)

## Contract
- 边缘为 ROSClaw Edge Runtime + Safety Gateway + Local Skill Executor
- 边缘安全判定最终有效；本地急停不依赖云端或网络
- ROS2 主路径；ROS1 仅隔离兼容

## Required By
- F-EDGE-002 (Local execution, offline cache & reconciliation)
- F-ADAPTER-001 (ROS2 simulation & primary path adapter)
