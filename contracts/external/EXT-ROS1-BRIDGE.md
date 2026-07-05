<!--
Function: EXT-ROS1-BRIDGE pinned reference — ROS1 bridge contract
Time: 2026-07-05
Author: AxeXie
-->

# EXT-ROS1-BRIDGE — ROS1 Bridge (ros1_bridge)

## Pinned Reference
- Official source: https://github.com/ros2/ros1_bridge
- Pin status: PINNED
- Version: ros1_bridge (ROS2 Jazzy compatibility bridge, isolated compatibility path)

## Contract
- ROS1 仅作为隔离兼容路径；不影响 ROS2 主路径
- ROS1 桥接通过 ros1_bridge 在隔离环境中运行，不直接暴露 /cmd_vel 或厂商 SDK
- 所有运动指令通过已注册 Skill/Capability 经 IAM、Mission、Policy、Fleet、Edge Safety
- 边缘安全判定最终有效；ROS1 适配不得绕过 Safety Gateway

## Required By
- F-ADAPTER-002 (ROS1 Unitree & custom protocol compatibility adapter)
