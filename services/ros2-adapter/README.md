# Function: ROS2 Jazzy adapter - real rclpy skill execution
# Time: 2026-07-15
# Author: AxeXie

# OpenGeoBot ROS2 Adapter

This package is the **real ROS2 Jazzy adapter** for OpenGeoBot. It connects to
a ROS2 Jazzy environment via `rclpy` and executes registered skills over NATS
JetStream.

## Contract

- Subscribes to `opengeobot.dev.edge.ros2.skill.execute.{robot_id}` (durable JetStream consumer).
- Same `Skill` / `SkillContext` / `SkillResult` interface as `services/sim-adapter/`.
- Same `SkillExecutionRequest` / `SkillExecutionResponse` models as sim-adapter.
- No direct `/cmd_vel` from Agent/LLM.
- All motion goes through registered skills after Safety Gateway ALLOW.
- The adapter does **not** maintain a local safety latch; safety is delegated to the SafetyStateMachine.

## Skills

| Skill ID        | ROS2 Action                                           |
|-----------------|-------------------------------------------------------|
| `stand_up`      | Calls `/turtle1/teleport_absolute` service            |
| `stop`          | Publishes zero `Twist` to `/turtle1/cmd_vel`         |
| `move_forward`  | Publishes `Twist` (linear.x > 0) to `/turtle1/cmd_vel`|
| `capture_image` | Returns simulated image data (no camera in turtlesim)|
| `emergency_stop`| Publishes zero `Twist` to `/turtle1/cmd_vel`        |

## Configuration

All configuration is driven by environment variables:

| Variable                  | Default                                | Description                          |
|---------------------------|----------------------------------------|--------------------------------------|
| `ROBOT_ID`                | `rbt_01J00000000000000000000001`       | Robot identifier                     |
| `NATS_URL`                | `nats://localhost:4222`                | NATS server URL                      |
| `NATS_MAX_RECONNECT`      | `-1` (unlimited)                       | Max NATS reconnect attempts          |
| `ROS2_NATS_RECONNECT_WAIT`| `2.0`                                  | NATS reconnect wait (seconds)         |
| `NATS_CONNECT_TIMEOUT`    | `5.0`                                  | NATS connect timeout (seconds)       |
| `ROS_DOMAIN_ID`           | `42`                                   | ROS_DOMAIN_ID for DDS isolation      |
| `LOG_LEVEL`               | `INFO`                                 | Loguru log level                     |
| `ROS2_JETSTREAM_STREAM`   | `ROS2_ADAPTER_STREAM`                  | JetStream stream name                |

## Running

```bash
python -m opengeobot_ros2.main
```

On non-ROS hosts (unit tests, CI) `rclpy` is imported gracefully and skills
fall back to simulation mode, returning success without touching a real robot.

## Docker

```bash
docker build -t opengeobot-ros2-adapter .
docker run --rm \
  -e ROBOT_ID=rbt_01J00000000000000000000001 \
  -e NATS_URL=nats://nats:4222 \
  -e ROS_DOMAIN_ID=42 \
  opengeobot-ros2-adapter
```

Base image is pinned to `ros:jazzy-ros-core` (no `latest`).
