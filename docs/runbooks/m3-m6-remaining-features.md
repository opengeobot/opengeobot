<!--
Function: M3-M6 Remaining Features Runbook — adapter, fleet, alarm, ops, ota, recovery, memory operations
Time: 2026-07-05
Author: AxeXie
-->

# M3-M6 Remaining Features Runbook

本 Runbook 覆盖 M3-M6 阶段剩余功能（F-ADAPTER-002、F-FLEET-001、F-ALARM-001、F-OPS-001、F-OTA-001、F-RECOVERY-001、F-MEMORY-001）的运维：ROS1/Unitree 兼容适配、多机器人调度、告警生命周期、运维态势、OTA 发布、数据恢复与任务记忆闭环。

## 1. 启动

```bash
# 1. 启动基础设施
./scripts/dev.sh infra-up

# 2. 执行数据库迁移（V1-V22）
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

## 3. M3-M6 端点验证

```bash
# F-ALARM-001: 告警
curl -sf http://localhost:8080/api/v1/alarms -H "Authorization: Bearer $TOKEN"
curl -sf http://localhost:8080/api/v1/alarms/rules -H "Authorization: Bearer $TOKEN"

# F-OPS-001: 运维态势
curl -sf http://localhost:8080/api/v1/ops/dashboard -H "Authorization: Bearer $TOKEN"
curl -sf http://localhost:8080/api/v1/ops/health -H "Authorization: Bearer $TOKEN"

# F-OTA-001: OTA 发布
curl -sf http://localhost:8080/api/v1/ota/packages -H "Authorization: Bearer $TOKEN"
curl -sf http://localhost:8080/api/v1/ota/campaigns -H "Authorization: Bearer $TOKEN"

# F-RECOVERY-001: 数据恢复
curl -sf http://localhost:8080/api/v1/recovery/backups -H "Authorization: Bearer $TOKEN"

# F-MEMORY-001: 任务记忆
curl -sf http://localhost:8080/api/v1/memory/cases -H "Authorization: Bearer $TOKEN"
curl -sf http://localhost:8080/api/v1/memory/suggestions -H "Authorization: Bearer $TOKEN"
```

## 4. 已知限制

- F-FLEET-001: `/api/v1/fleet/schedule` 与 `/api/v1/fleet/conflicts` 当前返回 404。Fleet 领域实体（FleetSchedule、schedule_candidate、resource_lock、robot_assignment、fleet_conflict）与迁移 V17 已就绪，但 FleetController REST 控制器尚未暴露到 `/api/v1/fleet` 路径。
- F-ADAPTER-002 与 F-OTA-001 的 HIL 真机测试未在 CI 执行，仅完成仿真与契约验证。

## 5. 安全红线

1. Agent/LLM 不得直接调用 /cmd_vel、关节、电机或厂商 SDK
2. 物理动作必须是已注册 Skill/Capability，经过 IAM、Mission、Policy、Fleet、Edge Safety
3. 边缘安全判定最终有效；本地急停不依赖云端
4. OTA 发布与恢复操作必须经审批；回滚必须自动触发
5. 未经仿真与安全回归的运动变更禁止连接真实机器人

## 6. 故障排查

- 数据库连接失败：检查 `docker compose ps`，确认 postgres 运行中
- 登录 401：确认数据库迁移已执行（V3 auth 表），admin 用户已种子化
- 端点 404：确认对应 Controller 已注册且路径匹配
- 端点 500：查看 `/tmp/opengeobot-app.log` 或应用日志
- Fleet 404：FleetController 尚未暴露 REST 路径；领域层与迁移已就绪
