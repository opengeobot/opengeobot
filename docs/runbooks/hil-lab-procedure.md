<!--
Function: HIL / real ROS2 / ROSClaw / Zenoh laboratory procedure (Phase 7)
Time: 2026-07-10
Author: AxeXie
-->

# Phase 7 — 真机与弱网实验室程序

本 Runbook 定义真实 ROS2、ROSClaw、Zenoh 与 HIL 的受控接入步骤。**未完成仿真与安全回归前禁止连接真实机器人。**

## 1. 前置条件

1. M2 仿真垂直闭环（C03–C12、C17–C19）PASS。
2. Safety Gateway 急停锁存本地可用（C10/C11）。
3. 外部契约 PINNED：
   - `contracts/external/EXT-ROS2-JAZZY.md`
   - `contracts/external/EXT-ROSCLAW.md`
   - `contracts/external/EXT-ZENOH-ROS2DDS.md`
   - `contracts/external/EXT-ROS1-BRIDGE.md`
   - `contracts/external/EXT-UNITREE-SDK.md`
4. 实验室急停硬件可用；云端 ALLOW 不能覆盖边缘 BLOCK。

## 2. ROS2 主路径替换（F-ADAPTER-001）

当前 `services/sim-adapter/` 为仿真实现。真机步骤：

1. 在 Ubuntu 24.04 + ROS 2 Jazzy 环境构建 `services/ros2-adapter/`（见该目录 README）。
2. 保持同一 Skill/Capability 契约与 NATS subject；仅替换 skill 实现为 `rclpy`。
3. Agent/LLM **禁止**直接发布 `/cmd_vel` 或调用厂商 SDK。
4. 先在仿真回归，再切真机。

## 3. ROSClaw Edge Runtime（F-EDGE-002）

1. 按 `EXT-ROSCLAW.md` 锁定版本部署边缘运行时。
2. 平台 `edge/gateway` + `edge/safety-gateway` 仍为安全最终权威。
3. Experimental/Research 能力默认关闭。

## 4. Zenoh 弱网（C15）

1. 部署 `zenoh-plugin-ros2dds` 桥（契约见 EXT-ZENOH-ROS2DDS）。
2. 验证断网缓存、重连对账、乱序/重复命令幂等。
3. 证据写入 `reports/acceptance/C15-result.md`。

## 5. HIL 范围

| 功能 | 报告 | 状态 |
|------|------|------|
| F-ADAPTER-002 | `reports/hil/F-ADAPTER-002-hil-summary.md` | 实验室待执行 |
| F-OTA-001 | `reports/hil/F-OTA-001-hil-summary.md` | 实验室待执行 |

HIL 不得在无硬件 CI 中伪造 PASS。

## 6. 安全红线

1. 物理动作必须是已注册 Skill，经 IAM → Mission → Policy → Fleet → Edge Safety。
2. 本地急停不依赖云端。
3. Memory 改进建议批准后不得自动下发运动变更。
