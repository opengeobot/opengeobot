<!--
Function: M2 sim vertical-loop test plan — from infra to C03–C12 / C17–C19 acceptance skeletons
Time: 2026-07-10
Author: AxeXie
-->

# M2 Sim Vertical Loop

本文件描述仿真环境下的最小垂直闭环步骤：从基础设施启动到 C03–C12 / C17–C19 验收骨架。完整用例仍以 [c01-c22-test-plan.md](./c01-c22-test-plan.md) 为准；此处只固定可重复的 sim 入口。

## 前置

- Docker Compose v2 可用
- Python 3.12+（验收脚本）
- 仓库根目录执行命令

## 垂直闭环步骤

1. **Doctor / bootstrap（可选）**

   ```bash
   ./scripts/dev.sh doctor
   ./scripts/dev.sh bootstrap
   ```

2. **启动基础设施**

   ```bash
   ./scripts/dev.sh infra-up
   ```

   等待 PostgreSQL、NATS、MinIO 健康。

3. **迁移**

   ```bash
   ./scripts/dev.sh migrate
   ```

4. **启动云控制面与前端**

   ```bash
   ./scripts/dev.sh dev
   ```

   健康检查：

   ```bash
   curl -sf http://localhost:8080/health/live
   ```

5. **启动仿真边缘栈**

   ```bash
   ./scripts/dev.sh sim-up
   ```

   包含 sim-adapter、edge-gateway、safety-gateway、local-skill-executor（经 Safety Gateway，禁止直连 `/cmd_vel`）。

6. **登录冒烟**

   ```bash
   curl -sf -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123"}'
   ```

7. **跑验收骨架**

   ```bash
   # C01/C02 公共能力
   python3 scripts/acceptance/run_c01_c02.py

   # C03–C12 边缘/机器人/Skill/MCP/任务/策略/安全
   python3 scripts/acceptance/run_c03_c12_sim.py

   # C13/C14/C16 多机调度与适配器契约（sim）
   python3 scripts/acceptance/run_c13_c16_sim.py

   # C17–C19 地图/媒体/Trace
   python3 scripts/acceptance/run_c17_c19_sim.py

   # C23/C24 结构（dev.sh + compose + k8s/ci 制品）
   python3 scripts/acceptance/run_c23_c24_check.py
   ```

8. **结果落盘**

   每个准则写入 `reports/acceptance/C{xx}-result.md`，状态为 `PASS` / `FAIL` / `SKIP`。  
   **C09、C10、C11、C19、C24 不得标记 `NOT_APPLICABLE`。**

9. **停止（保留数据）**

   ```bash
   ./scripts/dev.sh down
   ```

## 闭环覆盖映射（骨架）

| 准则 | 主要 API（GET） | 脚本 |
|------|-----------------|------|
| C03 | `/api/v1/edge-gateways` | `run_c03_c12_sim.py` |
| C04 | `/api/v1/robots` | `run_c03_c12_sim.py` |
| C05 | `/api/v1/skills` | `run_c03_c12_sim.py` |
| C06 | `/api/v1/mcp/tools` | `run_c03_c12_sim.py` |
| C07–C09 / C12 | `/api/v1/missions` | `run_c03_c12_sim.py` |
| C08 | `/api/v1/policies` | `run_c03_c12_sim.py` |
| C10 | `/api/v1/safety/events` | `run_c03_c12_sim.py` |
| C11 | `/api/v1/safety/state` | `run_c03_c12_sim.py` |
| C13 | `/api/v1/fleet/schedule`, `/conflicts` | `run_c13_c16_sim.py` |
| C14 | `/api/v1/fleet/failovers` | `run_c13_c16_sim.py` |
| C16 | `/api/v1/adapters/compatibility/{modelId}` | `run_c13_c16_sim.py` |
| C17 | `/api/v1/maps` | `run_c17_c19_sim.py` |
| C18 | `/api/v1/media` | `run_c17_c19_sim.py` |
| C19 | `/api/v1/traces` | `run_c17_c19_sim.py` |

## 明确未覆盖

- 完整 TC-* 业务路径（审批流、急停复位、多机冲突注入）需人工/后续自动化补齐
- HIL / 真机（C16 ROS1/Unitree/Custom）见 `reports/acceptance/C16-adapter-contract-note.md` 与 `reports/hil/`
- 安全扫描（越权/注入/重放）不在本垂直环内自动执行
