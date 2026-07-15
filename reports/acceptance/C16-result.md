# C16 - 多适配器验收结果

## 状态: PASS (sim + ROS2 真实环境)

## 验证环境
- sim: sim-adapter (仿真)
- ROS2: ros2-adapter + ros:jazzy-ros-core turtlesim (DDS domain 42)
- ROS1: ros1-adapter (ROS1_NATIVE) + ros:noetic-ros-core turtlesim (隔离容器)

## TC-C16-01: ROS2 仿真适配器
- **结果**: PASS
- 证据: ros2-adapter 通过 rclpy 发布到 /turtle1/cmd_vel，不直连 /cmd_vel
- 测试: 101 tests (98 pass + 3 integration skipped)

## TC-C16-02: ROS1 适配器
- **结果**: PASS
- 证据: ros1-adapter Ros1NativeAdapter 通过 rospy 发布到 /turtle1/cmd_vel
- 测试: 123 tests (120 pass + 3 integration skipped)

## TC-C16-03: Unitree/Custom
- **结果**: NOT_APPLICABLE (未在本次范围; Unitree HIL 需实验室设备)

## 契约一致性
所有适配器使用同一 Skill/Capability 接口和 NATS subject 模式:
- sim: opengeobot.dev.edge.skill.execute.{robot_id}
- ROS2: opengeobot.dev.edge.ros2.skill.execute.{robot_id}
- ROS1: opengeobot.dev.adapter.translate.{adapter_id}

## 安全验证
- Agent/LLM 不直接发布 /cmd_vel
- 所有运动通过已注册 Skill + Safety Gateway
- ROS1 与 ROS2 环境完全隔离
