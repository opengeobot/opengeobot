<!--
Function: EXT-UNITREE-SDK pinned reference — Unitree SDK contract
Time: 2026-07-05
Author: AxeXie
-->

# EXT-UNITREE-SDK — Unitree Robotics SDK (unitree_sdk2)

## Pinned Reference
- Official source: https://github.com/unitreerobotics/unitree_sdk2
- Pin status: PINNED
- Version: unitree_sdk2 (Unitree Go2 compatibility, isolated edge adapter)

## Contract
- Unitree SDK 仅在边缘适配层隔离使用；云端与 Agent/LLM 不得直接调用厂商 SDK
- 所有 Unitree 运动指令必须通过已注册 Skill/Capability，经 IAM、Mission、Policy、Fleet、Edge Safety
- 适配器将 Unitree SDK 能力映射为 capability-port，不暴露原始 SDK 接口
- 边缘安全判定最终有效；Unitree 适配不得绕过 Safety Gateway

## Required By
- F-ADAPTER-002 (ROS1 Unitree & custom protocol compatibility adapter)
