# Tasks

## Phase 1: ROS2 真实环境与适配器实现

- [x] Task 1: 实现 ROS2 rclpy 技能模块
  - [x] SubTask 1.1: 创建 `services/ros2-adapter/src/opengeobot_ros2/skills/` 目录，复用 sim-adapter 的 `Skill` Protocol、`SkillContext`、`SkillResult` 类型定义
  - [x] SubTask 1.2: 实现 `Ros2SkillBase` 基类，封装 rclpy Node 初始化、publisher 创建和异步 spin 逻辑
  - [x] SubTask 1.3: 实现 `MoveForwardSkill`（发布 Twist 到 `/turtle1/cmd_vel`）、`StopSkill`（发布零 Twist）、`StandUpSkill`（发布 teleport_absolute 服务调用）、`EmergencyStopSkill`（发布零 Twist 并记录）、`CaptureImageSkill`（订阅 image topic 或返回模拟数据）
  - [x] SubTask 1.4: 实现 `default_skills()` 注册表，返回与 sim-adapter 相同的 skill_id 集合

- [x] Task 2: 实现 ROS2 适配器主入口和配置
  - [x] SubTask 2.1: 实现 `services/ros2-adapter/src/opengeobot_ros2/config.py`，包含 `Ros2Config`（NATS_URL、ROBOT_ID、DDS_DOMAIN_ID、LOG_LEVEL 等）
  - [x] SubTask 2.2: 实现 `services/ros2-adapter/src/opengeobot_ros2/adapter.py`，`Ros2Adapter` 类通过 NATS 订阅 `opengeobot.dev.edge.ros2.skill.execute.{robot_id}`，执行 rclpy 技能并回复 `SkillExecutionResponse`
  - [x] SubTask 2.3: 实现 `services/ros2-adapter/src/opengeobot_ros2/main.py` 入口，初始化 rclpy、NATS 连接和信号处理
  - [x] SubTask 2.4: 更新 `services/ros2-adapter/pyproject.toml`，添加 rclpy 可选依赖和 nats-py、pydantic、loguru 必需依赖
  - [x] SubTask 2.5: 更新 `services/ros2-adapter/README.md`，移除骨架标注

- [x] Task 3: 构建 ROS2 Dockerfile 和 Compose 集成
  - [x] SubTask 3.1: 创建 `services/ros2-adapter/Dockerfile`，基于 `ros:jazzy-ros-core`，安装 rclpy 依赖和项目包
  - [x] SubTask 3.2: 在 `deploy/compose/compose.yml` 新增 `ros2` profile，包含 ros2-turtlesim 节点（`ros:jazzy` 运行 turtlesim）和 ros2-adapter 服务
  - [x] SubTask 3.3: 配置 DDS 域隔离（`ROS_DOMAIN_ID`），确保 turtlesim 和 ros2-adapter 在同一 DDS 域
  - [x] SubTask 3.4: 配置 DISPLAY 和 X11 转发支持 turtlesim GUI（或使用 turtlesim 的无头模式替代）

## Phase 2: ROS1 真实环境与适配器增强

- [x] Task 4: 实现 ROS1 rospy 原生适配器
  - [x] SubTask 4.1: 创建 `services/ros1-adapter/src/opengeobot_ros1/ros1_native_adapter.py`，实现 `Ros1NativeAdapter`，通过 rospy 连接 ROS Master 并发布到真实 ROS1 话题
  - [x] SubTask 4.2: 实现 `Ros1NativeAdapter.translate()` 返回话题消息结构，同时通过 rospy Publisher 发布到真实话题（`/turtle1/cmd_vel` 等）
  - [x] SubTask 4.3: 更新 `_select_protocol_handler()` 支持 `ROS1_NATIVE` 协议类型，选择 `Ros1NativeAdapter`
  - [x] SubTask 4.4: 更新 `Ros1Config` 添加 `ros_master_uri` 实际使用逻辑和 rospy 节点初始化

- [x] Task 5: 构建 ROS1 Dockerfile 和 Compose 集成
  - [x] SubTask 5.1: 重建 `services/ros1-adapter/Dockerfile`，基于 `ros:noetic-ros-core`，安装 rospy 依赖和项目包
  - [x] SubTask 5.2: 在 `deploy/compose/compose.yml` 新增 `ros1-sim` profile，包含 ros1-roscore（`ros:noetic-ros-core` 运行 roscore）、ros1-turtlesim（`ros:noetic-ros-core` 运行 turtlesim）和 ros1-adapter 服务
  - [x] SubTask 5.3: 配置 `ROS_MASTER_URI` 环境变量，确保 ros1-adapter 连接到正确 ROS Master
  - [x] SubTask 5.4: 配置 DISPLAY 和 X11 转发支持 turtlesim GUI（或使用替代方案）

## Phase 3: Zenoh 弱网桥接配置

- [x] Task 6: 配置 Zenoh ROS2DDS 桥接
  - [x] SubTask 6.1: 创建 `deploy/compose/zenoh/zenoh-bridge-ros2dds.json5` 配置文件，定义 DDS 域桥接规则
  - [x] SubTask 6.2: 在 `deploy/compose/compose.yml` 新增 `zenoh` profile，包含 zenoh-bridge-ros2dds 容器（基于 `zenoh-bridge-ros2dds` 镜像）
  - [x] SubTask 6.3: 配置 Zenoh 桥接连接到 ROS2 DDS 域，支持跨网络 ROS2 话题传输
  - [x] SubTask 6.4: 添加弱网模拟配置说明（通过 `tc netem` 或 Docker 网络配置注入延迟/丢包）

## Phase 4: 集成测试与验证

- [x] Task 7: ROS2 适配器单元测试
  - [x] SubTask 7.1: 创建 `services/ros2-adapter/tests/test_config.py`，测试 Ros2Config 环境变量加载
  - [x] SubTask 7.2: 创建 `services/ros2-adapter/tests/test_skills.py`，测试各 rclpy 技能的消息构建逻辑（不依赖真实 ROS2 环境）
  - [x] SubTask 7.3: 创建 `services/ros2-adapter/tests/test_adapter.py`，测试 NATS 请求处理和技能分发（mock rclpy）

- [x] Task 8: ROS1 原生适配器单元测试
  - [x] SubTask 8.1: 创建/更新 `services/ros1-adapter/tests/test_ros1_native_adapter.py`，测试 Ros1NativeAdapter 话题消息翻译（mock rospy）
  - [x] SubTask 8.2: 验证 ROS1_NATIVE 协议类型选择逻辑

- [x] Task 9: ROS2 端到端集成测试
  - [x] SubTask 9.1: 创建 `services/ros2-adapter/tests/test_ros2_e2e.py`，启动真实 ROS2 节点和 NATS，通过 NATS 发送技能请求验证 ROS2 话题消息（需要 ROS2 环境，标记为 integration test）
  - [x] SubTask 9.2: 验证 trace_id 从 NATS 请求到 ROS2 话题响应的端到端贯通
  - [x] SubTask 9.3: 验证 emergency_stop 技能发布停止命令后 SafetyStateMachine 状态一致

- [x] Task 10: ROS1 端到端集成测试
  - [x] SubTask 10.1: 创建 `services/ros1-adapter/tests/test_ros1_e2e.py`，启动真实 ROS Master 和 NATS，通过 NATS 发送翻译请求验证 ROS1 话题消息（需要 ROS1 环境，标记为 integration test）
  - [x] SubTask 10.2: 验证 trace_id 端到端贯通

## Phase 5: 契约更新与验收报告

- [x] Task 11: 更新外部契约验证状态
  - [x] SubTask 11.1: 更新 `contracts/external/EXT-ROS2-JAZZY.md`，记录真实 ROS2 Jazzy 环境验证证据
  - [x] SubTask 11.2: 更新 `contracts/external/EXT-ROS1-BRIDGE.md`，记录真实 ROS1 Noetic 环境验证证据
  - [x] SubTask 11.3: 更新 `contracts/external/EXT-ZENOH-ROS2DDS.md`，记录 Zenoh 桥接部署证据

- [x] Task 12: 编写验收报告
  - [x] SubTask 12.1: 创建/更新 `reports/acceptance/C16-result.md`，记录多适配器（ROS2 真实 + ROS1 真实 + sim）契约一致性验证结果
  - [x] SubTask 12.2: 创建/更新 `reports/acceptance/C15-result.md`，记录 Zenoh 弱网桥接部署和基础验证结果
  - [x] SubTask 12.3: 运行 `python3 scripts/validate_platform_manifest.py` 验证设计追踪（PASS: features=29; DONE=29）

## Phase 6: 构建验证与回归

- [x] Task 13: 全量构建与测试验证
  - [x] SubTask 13.1: 验证 ROS2 Docker 镜像构建成功（Dockerfile 基于 ros:jazzy-ros-core）
  - [x] SubTask 13.2: 验证 ROS1 Docker 镜像构建成功（Dockerfile 基于 ros:noetic-ros-core）
  - [x] SubTask 13.3: 运行 ROS2/ROS1 适配器单元测试通过（ROS2: 98 pass + 3 skip; ROS1: 120 pass + 3 skip）
  - [x] SubTask 13.4: 验证 Compose `ros2`、`ros1-sim`、`zenoh` profile 配置正确
  - [x] SubTask 13.5: 验证现有 sim profile 测试不受影响（回归: 63 pass, 0 fail）

# Task Dependencies
- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 2]
- [Task 5] depends on [Task 4]
- [Task 7] depends on [Task 2]
- [Task 8] depends on [Task 4]
- [Task 9] depends on [Task 3]
- [Task 10] depends on [Task 5]
- [Task 11] depends on [Task 9, Task 10]
- [Task 12] depends on [Task 11]
- [Task 13] depends on [Task 3, Task 5, Task 6]
- [Task 4] 和 [Task 6] 可与 [Task 1-3] 并行
