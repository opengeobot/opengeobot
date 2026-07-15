# Checklist

## Phase 1: ROS2 真实环境与适配器实现

- [x] `services/ros2-adapter/src/opengeobot_ros2/skills/` 目录已创建，包含 base.py（Skill Protocol 复用）
- [x] `Ros2SkillBase` 基类正确封装 rclpy Node 初始化、publisher 创建和异步 spin
- [x] 5 个 rclpy 技能实现（MoveForward、Stop、StandUp、EmergencyStop、CaptureImage）消息构建正确
- [x] `default_skills()` 注册表返回与 sim-adapter 相同的 skill_id 集合
- [x] `Ros2Config` 正确从环境变量加载配置（NATS_URL、ROBOT_ID、DDS_DOMAIN_ID 等）
- [x] `Ros2Adapter` 通过 NATS 订阅 `opengeobot.dev.edge.ros2.skill.execute.{robot_id}` 并执行 rclpy 技能
- [x] `main.py` 入口正确初始化 rclpy、NATS 连接和信号处理
- [x] `pyproject.toml` 包含 rclpy 可选依赖和 nats-py、pydantic、loguru 必需依赖
- [x] `README.md` 已移除骨架标注，更新为实现状态
- [x] `Dockerfile` 基于 `ros:jazzy-ros-core`，正确安装 rclpy 依赖和项目包
- [x] Compose `ros2` profile 包含 ros2-turtlesim 和 ros2-adapter 服务
- [x] DDS 域隔离配置正确（`ROS_DOMAIN_ID=42` 一致）

## Phase 2: ROS1 真实环境与适配器增强

- [x] `Ros1NativeAdapter` 通过 rospy 连接真实 ROS Master 并发布到 ROS1 话题
- [x] `Ros1NativeAdapter.translate()` 返回正确的话题消息结构
- [x] `_select_protocol_handler()` 支持 `ROS1_NATIVE` 协议类型
- [x] `Ros1Config` 的 `ros_master_uri` 在 ROS1_NATIVE 模式下被实际使用
- [x] `Dockerfile` 基于 `ros:noetic-ros-core`，正确安装 rospy 依赖和项目包
- [x] Compose `ros1-sim` profile 包含 ros1-roscore、ros1-turtlesim 和 ros1-adapter-native 服务
- [x] `ROS_MASTER_URI` 环境变量配置正确（http://ros1-roscore:11311）
- [x] ROS1 容器与 ROS2 容器完全隔离（不同基础镜像、不同 DDS 域）

## Phase 3: Zenoh 弱网桥接配置

- [x] `deploy/compose/zenoh/zenoh-bridge-ros2dds.json5` 配置文件正确定义 DDS 域桥接规则
- [x] Compose `zenoh` profile 包含 zenoh-bridge-ros2dds 容器（eclipse/zenoh-bridge-ros2dds:1.1.1）
- [x] Zenoh 桥接正确连接到 ROS2 DDS 域（ROS_DOMAIN_ID=42）
- [x] 弱网模拟配置说明已提供（deploy/compose/zenoh/README.md，含 tc netem 用法）

## Phase 4: 集成测试与验证

- [x] `test_config.py` 测试 Ros2Config 环境变量加载
- [x] `test_skills.py` 测试各 rclpy 技能的消息构建逻辑（不依赖真实 ROS2）
- [x] `test_adapter.py` 测试 NATS 请求处理和技能分发（mock rclpy）
- [x] `test_ros1_native_adapter.py` 测试 Ros1NativeAdapter 话题消息翻译（mock rospy，26 tests）
- [x] ROS1_NATIVE 协议类型选择逻辑有测试覆盖
- [x] ROS2 端到端集成测试验证 NATS 请求到 ROS2 话题的完整链路（test_ros2_e2e.py）
- [x] ROS1 端到端集成测试验证 NATS 请求到 ROS1 话题的完整链路（test_ros1_e2e.py）
- [x] trace_id 从 NATS 请求到 ROS 话题响应端到端贯通
- [x] emergency_stop 技能发布停止命令后 SafetyStateMachine 状态一致（适配器不维护独立 _safety_latched）

## Phase 5: 契约更新与验收报告

- [x] `EXT-ROS2-JAZZY.md` 包含真实 ROS2 Jazzy 环境验证证据
- [x] `EXT-ROS1-BRIDGE.md` 包含真实 ROS1 Noetic 环境验证证据
- [x] `EXT-ZENOH-ROS2DDS.md` 包含 Zenoh 桥接部署证据
- [x] `C16-result.md` 记录多适配器契约一致性验证结果
- [x] `C15-result.md` 记录 Zenoh 弱网桥接部署和基础验证结果
- [x] `python3 scripts/validate_platform_manifest.py` 通过（PASS: features=29; DONE=29）

## Phase 6: 构建验证与回归

- [x] ROS2 Docker 镜像构建成功（Dockerfile 基于 ros:jazzy-ros-core）
- [x] ROS1 Docker 镜像构建成功（Dockerfile 基于 ros:noetic-ros-core）
- [x] ROS2/ROS1 适配器单元测试通过（ROS2: 98 pass + 3 skip; ROS1: 120 pass + 3 skip）
- [x] Compose `ros2` profile 配置正确（ros2-turtlesim + ros2-adapter）
- [x] Compose `ros1-sim` profile 配置正确（ros1-roscore + ros1-turtlesim + ros1-adapter-native）
- [x] Compose `zenoh` profile 配置正确（zenoh-bridge-ros2dds:1.1.1）
- [x] 现有 sim profile 测试不受影响（回归: 63 pass, 0 fail）
- [x] 无安全红线违反（Agent 不直接调 /cmd_vel、急停锁存本地优先、Agent 输出为不可信提案）
- [x] 无 `latest` 镜像标签、无浮动依赖、无真实 Secret
