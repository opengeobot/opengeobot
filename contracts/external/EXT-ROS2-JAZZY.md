<!--
Function: EXT-ROS2-JAZZY pinned reference — ROS2 Jazzy contract
Time: 2026-07-05
Author: AxeXie
-->

# EXT-ROS2-JAZZY — ROS2 Jazzy

## Pinned Reference
- Official source: https://docs.ros.org/en/jazzy/
- Pin status: PINNED
- Version: Jazzy (M2 simulation adapter, sim-adapter Python service)

## Contract
- ROS2 为运动控制主路径
- 仿真适配器位于 services/sim-adapter/，通过 NATS 与云端通信
- 不直接调用厂商 SDK；所有运动指令通过 Skill/Capability 注册

## Required By
- F-EDGE-002 (Local execution, offline cache & reconciliation)
- F-ADAPTER-001 (ROS2 simulation & primary path adapter)
