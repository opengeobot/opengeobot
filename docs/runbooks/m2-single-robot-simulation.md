<!--
Function: M2 Single Robot Simulation Runbook — robot, skill, mission, policy, safety, map, media, trace, monitor operations
Time: 2026-07-05
Author: AxeXie
-->

# M2 单机器人仿真 Runbook

本 Runbook 覆盖 M2 阶段（F-EDGE-001 ~ F-TRACE-001）的单机器人仿真闭环运维：机器人注册与状态、Skill/Capability 生命周期、MCP 工具、任务创建与执行、策略版本、安全急停、地图场景、媒体管理、链路追踪与实时监控。

## 1. 启动

```bash
# 1. 启动基础设施
./scripts/dev.sh infra-up

# 2. 执行数据库迁移（V1-V16）
./scripts/dev.sh migrate

# 3. 启动云控制面与前端
./scripts/dev.sh dev
```

健康验证：

```bash
curl http://localhost:8080/health/live    # 应返回 200 HEALTHY
curl http://localhost:5173                  # 前端可访问
```

## 2. 登录

```bash
TOKEN=$(curl -sf -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

## 3. M2 端点验证

```bash
# C03: 机器人注册
curl -sf http://localhost:8080/api/v1/robots -H "Authorization: Bearer $TOKEN"

# C04: Skill 管理
curl -sf http://localhost:8080/api/v1/skills -H "Authorization: Bearer $TOKEN"

# C05: 任务管理
curl -sf http://localhost:8080/api/v1/missions -H "Authorization: Bearer $TOKEN"

# C06: 策略管理
curl -sf http://localhost:8080/api/v1/policies -H "Authorization: Bearer $TOKEN"

# C07: 安全状态
curl -sf http://localhost:8080/api/v1/safety/state -H "Authorization: Bearer $TOKEN"

# C08: MCP 工具
curl -sf http://localhost:8080/api/v1/mcp/tools -H "Authorization: Bearer $TOKEN"

# C09: 地图管理
curl -sf http://localhost:8080/api/v1/maps -H "Authorization: Bearer $TOKEN"

# C10: 媒体
curl -sf http://localhost:8080/api/v1/media -H "Authorization: Bearer $TOKEN"

# C11: 链路追踪
curl -sf http://localhost:8080/api/v1/traces -H "Authorization: Bearer $TOKEN"

# C12: 监控概览
curl -sf http://localhost:8080/api/v1/monitor/overview -H "Authorization: Bearer $TOKEN"
```

## 4. 创建任务（闭环测试）

```bash
curl -sf -X POST http://localhost:8080/api/v1/missions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Mission",
    "description": "M2 verification mission",
    "robot_id": "rbt_01J00000000000000000000001",
    "priority": "NORMAL",
    "steps": [
      {"skill_id": "stand_up", "step_order": 1, "input_params": {"duration": 5}},
      {"skill_id": "move_forward", "step_order": 2, "input_params": {"distance": 1.0, "speed": 0.5}}
    ]
  }'
```

## 5. 安全红线

1. Agent/LLM 不得直接调用 /cmd_vel、关节、电机或厂商 SDK
2. 物理动作必须是已注册 Skill/Capability，经过 IAM、Mission、Policy、Fleet、Edge Safety
3. 边缘安全判定最终有效；本地急停不依赖云端
4. 未经仿真与安全回归的运动变更禁止连接真实机器人

## 6. 故障排查

- 数据库连接失败：检查 `docker compose ps`，确认 postgres 运行中
- 登录 401：确认数据库迁移已执行（V3 auth 表），admin 用户已种子化
- 端点 500：查看 `/tmp/opengeobot-app.log` 或应用日志
- 任务创建 500：检查 outbox_event 表的 aggregate_version 非空约束
