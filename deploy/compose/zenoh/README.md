# Zenoh ROS2DDS Bridge Configuration

## Purpose

Zenoh ROS2DDS bridge for weak-network testing (acceptance criterion **C15**). The bridge
transports ROS2 DDS topic traffic across networks where direct DDS multicast is
unreachable, enabling simulated weak-network scenarios for the OpenGeoBot platform.

- **Pinned contract**: `contracts/external/EXT-ZENOH-ROS2DDS.md` (PINNED)
- **Upstream**: https://github.com/eclipse-zenoh/zenoh-plugin-ros2dds
- **Image**: `eclipse/zenoh-bridge-ros2dds:1.1.1` (pinned, no `latest`)
- **Required by**: F-EDGE-002 (offline cache & reconciliation), F-ADAPTER-001 (ROS2 adapter)

## Files

| File | Description |
|---|---|
| `zenoh-bridge-ros2dds.json5` | JSON5 configuration for the `zenoh-bridge-ros2dds` plugin |

The configuration uses `ROS_DOMAIN_ID=42` to match the ROS2 adapter and turtlesim
nodes. Only turtlesim topics (`/turtle1/cmd_vel`, `/turtle1/pose`,
`/turtle1/color_sensor`) are bridged to keep the setup minimal.

## Starting the Bridge

The Zenoh bridge runs as an optional Docker Compose profile:

```bash
docker compose -f deploy/compose/compose.yml --profile zenoh up
```

To combine with the ROS2 simulation stack:

```bash
docker compose -f deploy/compose/compose.yml --profile infra --profile sim --profile zenoh up
```

The bridge listens on `tcp/0.0.0.0:7447` and bridges DDS domain 42 traffic.

## Injecting Network Impairments

Use `tc netem` on the host (or inside a network namespace) to simulate latency,
packet loss, and jitter between Zenoh endpoints.

### Latency

```bash
# Add 200ms one-way latency on the docker bridge interface
sudo tc qdisc add dev docker0 root netem delay 200ms

# Remove the rule
sudo tc qdisc del dev docker0 root netem
```

### Packet loss

```bash
# 5% packet loss
sudo tc qdisc add dev docker0 root netem loss 5%

# Remove
sudo tc qdisc del dev docker0 root netem
```

### Combined latency + jitter + loss

```bash
sudo tc qdisc add dev docker0 root netem delay 200ms 50ms 25% loss 5%
```

### Per-container network shaping

For more targeted testing, apply `tc` rules inside a specific container's network
namespace:

```bash
PID=$(docker inspect -f '{{.State.Pid}}' <container_name>)
sudo nsenter -t "$PID" -n tc qdisc add dev eth0 root netem delay 200ms loss 5%
```

## Relationship to the Platform

```
                     ┌─────────────────────┐
   Cloud (NATS) ────►│  Edge Gateway       │
                     │  Safety Gateway     │
                     │  Local Skill Exec   │
                     └────────┬────────────┘
                              │ NATS (skill.execute)
                     ┌────────▼────────────┐
                     │  ROS2 Adapter       │  ROS_DOMAIN_ID=42
                     │  (rclpy skills)     │
                     └────────┬────────────┘
                              │ DDS (domain 42)
                     ┌────────▼────────────┐
                     │  Zenoh Bridge       │  ← optional (zenoh profile)
                     │  ros2dds:1.1.1      │
                     └────────┬────────────┘
                              │ Zenoh protocol (tcp:7447)
                              │ [tc netem: latency / loss]
                     ┌────────▼────────────┐
                     │  Remote ROS2 Node   │  (turtlesim, real robot)
                     └─────────────────────┘
```

- **Zenoh** bridges ROS2 DDS traffic across networks where DDS multicast cannot
  reach. It is transparent to the ROS2 adapter — the adapter publishes/subscribes
  to DDS topics as usual.
- **Offline cache and reconciliation** are handled by NATS JetStream
  (`F-EDGE-002`). When the Zenoh link is down, skill commands are persisted
  via JetStream and reconciled on reconnection. Zenoh itself does not persist
  commands.
- **The Zenoh bridge is optional** — it does not affect the main simulation path.
  Without the `zenoh` profile, the ROS2 adapter and turtlesim communicate
  directly via DDS within the same Docker network.

## Safety Note

> **The Zenoh bridge does NOT bypass the Safety Gateway.**

All motion commands still flow through the platform's safety pipeline:
1. Agent/LLM produces a **proposal** (not a direct command).
2. The proposal passes through Schema validation, IAM, Mission, Policy, Fleet,
   and Edge Safety checks.
3. Only **ALLOW** decisions reach the ROS2 adapter as registered Skill executions.
4. The ROS2 adapter publishes to `/cmd_vel` only via registered, versioned Skills.

Zenoh is a **transport layer** — it carries DDS messages but does not modify,
intercept, or override safety decisions. The edge Safety Gateway and local
emergency stop remain authoritative and take priority over cloud, Zenoh, and
administrative commands.
