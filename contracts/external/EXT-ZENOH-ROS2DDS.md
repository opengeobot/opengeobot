<!--
Function: EXT-ZENOH-ROS2DDS pinned reference — Zenoh ROS2DDS bridge contract
Time: 2026-07-05
Author: AxeXie
-->

# EXT-ZENOH-ROS2DDS — Zenoh ROS2DDS Bridge

## Pinned Reference
- Official source: https://github.com/eclipse-zenoh/zenoh-plugin-ros2dds
- Pin status: PINNED
- Version: M2 simulation (weak network bridge simulated)

## Contract
- 弱网/ROS2DDS 桥接使用 Zenoh
- 离线缓存与重连对账通过 NATS JetStream
- 边缘安全判定和本地急停优先于云端

## Required By
- F-EDGE-002 (Local execution, offline cache & reconciliation)
- F-ADAPTER-001 (ROS2 simulation & primary path adapter)
