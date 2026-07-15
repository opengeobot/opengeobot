# 真实 ROS1/ROS2 环境构建与边缘侧功能验证 Spec

## Why

OpenGeoBot 平台边缘组件（edge/gateway、safety-gateway、local-skill-executor）已在仿真路径（sim-adapter）下完整实现并通过 425 项测试，但 ROS2 适配器仅有骨架（`__init__.py`），ROS1 适配器仅为协议翻译层（不连接真实 ROS Master），且无真实 ROS1/ROS2 运行时环境。外部契约（EXT-ROS2-JAZZY、EXT-ROS1-BRIDGE、EXT-ZENOH-ROS2DDS、EXT-UNITREE-SDK）均已 PINNED，满足 HIL 前置条件。需构建真实 ROS1 和 ROS2 Docker 环境并实现 rclpy/rospy 驱动的适配器，以完成 F-ADAPTER-001 真机主路径和 F-ADAPTER-002 受控 HIL 验证。

## What Changes

### ROS2 真实环境与适配器（F-ADAPTER-001 主路径）
- 基于 `ros:jazzy-ros-core` 构建 ROS2 Jazzy Docker 环境，运行 turtlesim 作为仿真机器人节点
- 实现 `services/ros2-adapter/` 的 rclpy 技能实现，复用 sim-adapter 的 `Skill` Protocol 接口和 NATS subject
- 技能通过 rclpy 发布到标准 ROS2 话题（如 `/turtle1/cmd_vel`、`/turtle1/teleport_absolute`），但 Agent/LLM 不直接发布，仅通过已注册 Skill 执行
- 新增 `ros2` Compose profile，包含 ros2-adapter 和 ROS2 turtlesim 节点
- 保持 `safety_latched` 由 SafetyStateMachine 统一管理，ros2-adapter 不维护独立安全状态

### ROS1 真实环境与适配器（F-ADAPTER-002 隔离兼容）
- 基于 `ros:noetic-ros-core` 构建 ROS1 Noetic Docker 环境（隔离容器，不污染 ROS2 主环境）
- 在 ROS1 适配器中新增 rospy 驱动的真实话题发布能力，作为 `ProtocolAdapter` 的新实现 `Ros1NativeAdapter`
- rospy 节点连接 ROS Master，将翻译后的命令发布到真实 ROS1 话题（`/turtle1/cmd_vel` 等）
- 新增 `ros1-sim` Compose profile，包含 roscore、turtlesim 和 ros1-adapter（连接真实 ROS Master）

### Zenoh 弱网桥接（C15 验证基础）
- 部署 `zenoh-bridge-ros2dds` 容器，配置 ROS2 DDS 域桥接
- 新增 Zenoh 配置文件，支持弱网注入测试（延迟、丢包模拟）
- 在 Compose 中新增 `zenoh` profile，可选择性启用弱网桥接

### Docker Compose 集成
- 新增 `ros2` profile：ros2-turtlesim + ros2-adapter（连接真实 ROS2 DDS）
- 新增 `ros1-sim` profile：ros1-roscore + ros1-turtlesim + ros1-adapter（连接真实 ROS Master）
- 新增 `zenoh` profile：zenoh-bridge-ros2dds（可选弱网桥接）
- 更新 `full` profile 包含新组件

### 集成测试与验证
- 新增 ROS2 端到端集成测试：通过 NATS 发送技能请求 -> safety-gateway 审批 -> ros2-adapter 执行 -> 验证 ROS2 话题收到正确消息
- 新增 ROS1 端到端集成测试：通过 NATS 发送翻译请求 -> ros1-adapter 发布到真实 ROS1 话题 -> 验证话题消息
- 新增 Zenoh 弱网测试基础：验证 zenoh-bridge-ros2dds 启动和 DDS 域桥接
- 验证 trace_id 贯通全链路

## Impact

- Affected specs: F-ADAPTER-001, F-ADAPTER-002, F-EDGE-002, F-SAFETY-001
- Affected code:
  - `services/ros2-adapter/` - 从骨架实现为完整的 rclpy 适配器
  - `services/ros1-adapter/src/opengeobot_ros1/` - 新增 rospy 驱动的 Ros1NativeAdapter
  - `services/ros1-adapter/Dockerfile` - 基于 ROS Noetic 镜像重建
  - `deploy/compose/compose.yml` - 新增 ros2、ros1-sim、zenoh profile
  - `deploy/compose/` - 新增 ROS2、ROS1、Zenoh 相关配置文件
  - `contracts/external/EXT-ROS2-JAZZY.md` - 更新实际验证状态
  - `contracts/external/EXT-ROS1-BRIDGE.md` - 更新实际验证状态
  - `contracts/external/EXT-ZENOH-ROS2DDS.md` - 更新实际验证状态
  - `reports/acceptance/C16-result.md` - 多适配器验证报告
  - `reports/acceptance/C15-result.md` - 弱网验证报告

## ADDED Requirements

### Requirement: ROS2 真实环境适配器
系统 SHALL 提供基于 ROS2 Jazzy + rclpy 的真实 ROS2 适配器，实现与 sim-adapter 相同的 `Skill` Protocol 接口和 NATS subject，通过 rclpy 发布到标准 ROS2 话题。Agent/LLM SHALL NOT 直接发布 `/cmd_vel` 或调用厂商 SDK。

#### Scenario: ROS2 技能执行
- **WHEN** local-skill-executor 将技能请求路由到 ros2 适配器
- **THEN** ros2-adapter 通过 rclpy 发布对应 ROS2 话题消息（如 `/turtle1/cmd_vel`），并返回执行结果

#### Scenario: 同一 Skill 契约
- **WHEN** 使用 ros2 适配器替换 sim 适配器
- **THEN** NATS subject、SkillExecutionRequest/Response 模型和技能 ID 保持一致，无需修改 edge-gateway 或 local-skill-executor

#### Scenario: 安全状态委托
- **WHEN** emergency_stop 技能执行
- **THEN** ros2-adapter 发布停止命令到 ROS2 话题，但安全锁存状态由 SafetyStateMachine 统一管理，适配器不维护独立 `_safety_latched`

### Requirement: ROS1 真实环境适配器
系统 SHALL 提供基于 ROS Noetic + rospy 的 ROS1 原生适配器，连接真实 ROS Master 并发布到 ROS1 话题。ROS1 适配器 SHALL 在隔离容器中运行，不污染 ROS2 主开发环境。

#### Scenario: ROS1 话题发布
- **WHEN** ros1-adapter 收到翻译请求且 PROTOCOL_TYPE=ROS1_NATIVE
- **THEN** 适配器通过 rospy 连接 ROS Master，发布到对应 ROS1 话题（如 `/turtle1/cmd_vel`）

#### Scenario: ROS1 隔离运行
- **WHEN** ROS1 容器启动
- **THEN** ROS1 环境完全隔离在独立容器中，ROS2 主路径容器不受影响

### Requirement: Zenoh ROS2DDS 桥接
系统 SHALL 提供 zenoh-bridge-ros2dds 配置，支持 ROS2 DDS 域桥接和弱网测试。Zenoh 桥接 SHALL 作为可选组件，不影响主路径功能。

#### Scenario: Zenoh 桥接启动
- **WHEN** 启用 zenoh profile
- **THEN** zenoh-bridge-ros2dds 容器启动并桥接 ROS2 DDS 域，ROS2 话题消息可通过 Zenoh 传输

### Requirement: Docker Compose 多 ROS 环境编排
系统 SHALL 提供 `ros2`、`ros1-sim` 和 `zenoh` 三个 Compose profile，支持独立或组合启动真实 ROS 环境。

#### Scenario: ROS2 环境启动
- **WHEN** 执行 `docker compose --profile ros2 up`
- **THEN** ROS2 turtlesim 节点和 ros2-adapter 启动，ros2-adapter 连接到同一 DDS 域

#### Scenario: ROS1 环境启动
- **WHEN** 执行 `docker compose --profile ros1-sim up`
- **THEN** ROS1 roscore、turtlesim 和 ros1-adapter 启动，ros1-adapter 连接到 ROS Master

### Requirement: 真实 ROS 环境集成测试
系统 SHALL 提供使用真实 ROS2/ROS1 环境的集成测试，验证从 NATS 请求到 ROS 话题消息的端到端链路，并验证 trace_id 贯通。

#### Scenario: ROS2 端到端验证
- **WHEN** 测试通过 NATS 发送 move_forward 技能请求
- **THEN** ROS2 turtlesim 话题 `/turtle1/cmd_vel` 收到正确的 Twist 消息，响应包含 trace_id

#### Scenario: ROS1 端到端验证
- **WHEN** 测试通过 NATS 发送翻译请求到 ros1-adapter
- **THEN** ROS1 话题收到正确的消息，响应包含 trace_id

## MODIFIED Requirements

### Requirement: ROS2 适配器状态
`services/ros2-adapter/` SHALL 从骨架升级为完整实现，包含 rclpy 技能实现、Dockerfile 和 Compose 集成，不再标注为 "Phase 7 laboratory integration scaffold"。

### Requirement: ROS1 适配器协议处理
ROS1 适配器 SHALL 在 `PROTOCOL_TYPE=ROS1_NATIVE` 时使用 rospy 连接真实 ROS Master 并发布话题消息，而非回退到 CustomAdapter 的 JSON 翻译。

### Requirement: 外部契约验证状态
EXT-ROS2-JAZZY、EXT-ROS1-BRIDGE、EXT-ZENOH-ROS2DDS 的契约 SHALL 更新为包含真实环境验证证据，不再标注为 "M2 simulation"。

## REMOVED Requirements

### Requirement: ROS2 适配器骨架状态
**Reason**: 从骨架升级为完整实现，骨架标注不再适用
**Migration**: 实现 rclpy 技能后更新 README 和 pyproject.toml
