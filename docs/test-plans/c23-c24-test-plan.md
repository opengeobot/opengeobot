<!--
Function: C23 (Unified Dev Script) & C24 (Docker Compose Deployment) Acceptance Test Plan
Time: 2026-07-04
Author: AxeXie
-->

# C23 / C24 验收测试计划

本计划覆盖 M0 工程基线（F-ENGINEERING-001）的两项验收标准：

- **C23**：统一开发脚本（`./scripts/dev.sh`）端到端可用。
- **C24**：Docker Compose 部署（多 profile）可用，含健康检查与数据持久化。

## 测试环境

- 操作系统：Linux（bash）
- 仓库根目录：`/home/axe/workspace/opengeobot/opengeobot`
- 所有命令在仓库根目录执行，除非另注。
- 默认账号：PostgreSQL/MinIO `opengeobot` / `opengeobot_dev`；Grafana `admin` / `admin`。

## 通用前置条件

1. 已克隆仓库并位于根目录。
2. Docker 24+ 与 Docker Compose v2 已安装且当前用户有权限访问 Docker daemon。
3. 端口 5432 / 4222 / 9000 / 9001 / 8080 / 5173 / 3000 / 8428 / 3100 未被占用（或已在 `.env` 中改写映射）。

---

# C23 — 统一开发脚本验证

目标：验证 `./scripts/dev.sh` 的 7 个核心子命令（doctor / bootstrap / infra-up / migrate / dev / test / down）行为符合预期，且 `down` 不删除数据。

## TC-C23-01 doctor（环境自检）

| 项 | 内容 |
|----|------|
| 前置条件 | 所有必需工具（git / JDK 21 / docker / docker compose / node / python3）已安装；pnpm、uv 可选。 |
| 步骤 | 1. 执行 `./scripts/dev.sh doctor`。 |
| 预期结果 | 逐项输出工具与版本；JDK 显示 `21.x`；端口 5432/4222/9000/8080/5173 逐项检查并标记空闲或占用；全部必需项就绪时输出 `Environment looks ready` 且退出码为 0；缺可选工具时仅告警。 |

## TC-C23-02 bootstrap（依赖初始化）

| 项 | 内容 |
|----|------|
| 前置条件 | 全新检出（`.env` 与 `node_modules` 不存在）；docker daemon 可用。 |
| 步骤 | 1. 执行 `./scripts/dev.sh bootstrap`。 |
| 预期结果 | `./mvnw -N validate -B` 成功；`.env.example` 复制为 `.env`（已存在则跳过）；若 pnpm 可用则 `pnpm install` 成功；若 uv 可用则 `services/agent-runtime` 下 `uv sync` 成功；输出 `Bootstrap complete`。重复执行仍成功（幂等）。 |

## TC-C23-03 infra-up（基础设施启动）

| 项 | 内容 |
|----|------|
| 前置条件 | `doctor` 通过；docker daemon 可用；端口未被占用。 |
| 步骤 | 1. 执行 `./scripts/dev.sh infra-up`。<br>2. 执行 `docker compose -f deploy/compose/compose.yml ps`。 |
| 预期结果 | postgres / nats / minio 容器启动；脚本自动等待健康检查（postgres 60s、nats 30s、minio 60s）并输出 `<service> is healthy`；`ps` 显示三者状态为 `healthy`。 |

## TC-C23-04 migrate（数据库迁移）

| 项 | 内容 |
|----|------|
| 前置条件 | `infra-up` 已完成且 postgres 健康。 |
| 步骤 | 1. 执行 `./scripts/dev.sh migrate`。<br>2. 执行 `docker exec -it opengeobot-postgres-1 psql -U opengeobot -d opengeobot -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"`。 |
| 预期结果 | Flyway 通过 `./mvnw -pl apps/cloud-control/bootstrap flyway:migrate` 执行成功，输出 `Flyway migrations applied`；`flyway_schema_history` 包含 `V1`（create_domain_schemas）与 `V2`（create_common_tables），`success = t`。重复执行幂等（无新迁移）。 |

## TC-C23-05 dev（应用开发模式启动）

| 项 | 内容 |
|----|------|
| 前置条件 | `migrate` 已完成；端口 8080 / 5173 空闲。 |
| 步骤 | 1. 后台执行 `./scripts/dev.sh dev`。<br>2. 等待约 30–60s 后执行 `curl -sf http://localhost:8080/health/live`。<br>3. 执行 `curl -sI http://localhost:5173`。<br>4. 发送 `Ctrl+C`（SIGINT）。 |
| 预期结果 | 脚本输出后端地址 `http://localhost:8080` 与前端地址 `http://localhost:5173`；`.dev-pids/java.pid`、`.dev-pids/web.pid` 存在；`/health/live` 返回 200；前端 5173 可访问；Ctrl+C 后两个进程均被停止且 PID 文件被清理。 |

## TC-C23-06 test（测试执行）

| 项 | 内容 |
|----|------|
| 前置条件 | 代码与测试源码存在；`bootstrap` 已完成。 |
| 步骤 | 1. 执行 `./scripts/dev.sh test`。 |
| 预期结果 | `./mvnw test -B` 执行并通过；若 web-console 配置了 vitest 则 `pnpm test -- --run` 执行并通过；未配置 vitest 时输出 `skipping frontend tests` 并仍判定成功；输出 `Java tests passed`。 |

## TC-C23-07 down（停止并保留数据）

| 项 | 内容 |
|----|------|
| 前置条件 | `infra-up` / `dev` 产生的容器或进程正在运行。 |
| 步骤 | 1. 执行 `./scripts/dev.sh down`。<br>2. 执行 `docker volume ls \| grep opengeobot`。<br>3. 执行 `./scripts/dev.sh infra-up` 后查询数据是否仍在。 |
| 预期结果 | docker compose 容器被移除；输出 `Docker Compose stack stopped (volumes retained)`；dev 后台进程被停止；数据卷 `opengeobot_postgres_data`（及 nats/minio/vm/loki/grafana）仍存在；重新 `infra-up` 后原有数据可访问（验证持久化）。 |

---

# C24 — Docker Compose 部署验证

目标：验证 `deploy/compose/compose.yml` 的 profile（infra / observability / full）、健康检查与数据持久化行为符合预期。

## TC-C24-01 infra profile

| 项 | 内容 |
|----|------|
| 前置条件 | docker daemon 可用；端口 5432 / 4222 / 8222 / 9000 / 9001 空闲。 |
| 步骤 | 1. 执行 `docker compose -f deploy/compose/compose.yml --profile infra up -d`。<br>2. 等待约 60s 后执行 `docker compose -f deploy/compose/compose.yml ps`。 |
| 预期结果 | 仅 postgres / nats / minio 三容器启动（observability 与 cloud 不启动）；三者状态均为 `healthy`；postgres 镜像为 `pgvector/pgvector:pg16`；nats 启用 JetStream（`-js`）。 |

## TC-C24-02 observability profile

| 项 | 内容 |
|----|------|
| 前置条件 | docker daemon 可用；端口 8428 / 8429 / 3100 / 3000 空闲。 |
| 步骤 | 1. 执行 `docker compose -f deploy/compose/compose.yml --profile observability up -d`。<br>2. 等待约 60s 后执行 `docker compose -f deploy/compose/compose.yml ps`。<br>3. `curl -sf http://localhost:8428/health`。<br>4. `curl -sf http://localhost:3100/ready`。 |
| 预期结果 | victoriametrics / vmagent / loki / vector / grafana 容器启动；vmagent 依赖 victoriametrics healthy、vector 依赖 loki healthy、grafana 依赖二者 healthy；victoriametrics `/health` 返回 200；loki `/ready` 返回 200；Grafana 可访问 http://localhost:3000（admin/admin）。 |

## TC-C24-03 full profile

| 项 | 内容 |
|----|------|
| 前置条件 | docker daemon 可用；所有相关端口空闲；Java/前端 Dockerfile 可构建。 |
| 步骤 | 1. 执行 `docker compose -f deploy/compose/compose.yml --profile full up -d --build`。<br>2. 等待约 120s 后执行 `docker compose -f deploy/compose/compose.yml ps`。<br>3. `curl -sf http://localhost:8080/health/live`。 |
| 预期结果 | infra + observability + cloud 全部容器启动；cloud-control 依赖 postgres/nats/minio healthy，web-console 依赖 cloud-control healthy；所有容器状态为 `healthy`；`/health/live` 返回 200；前端 http://localhost:5173 可访问。等价命令 `./scripts/dev.sh e2e` 应得到同样结果。 |

## TC-C24-04 健康检查

| 项 | 内容 |
|----|------|
| 前置条件 | `--profile full` 栈已启动且健康。 |
| 步骤 | 1. `docker compose -f deploy/compose/compose.yml ps`（查看 STATUS 列）。<br>2. 云控制面健康端点：`curl -sf http://localhost:8080/health/live`、`curl -sf http://localhost:8080/health/ready`。<br>3. 基础设施健康：`docker exec opengeobot-postgres-1 pg_isready -U opengeobot -d opengeobot`、`curl -sf http://localhost:8222/healthz`、`curl -sf http://localhost:9000/minio/health/live`。 |
| 预期结果 | 所有容器 STATUS 列显示 `(healthy)`；云控制面 liveness/ready 均返回 200；postgres/nats/minio 健康探测均成功。 |

## TC-C24-05 数据持久化

| 项 | 内容 |
|----|------|
| 前置条件 | `--profile infra` 已启动且 postgres 健康；已执行 `migrate`。 |
| 步骤 | 1. 记录迁移基线：`docker exec opengeobot-postgres-1 psql -U opengeobot -d opengeobot -c "SELECT count(*) FROM flyway_schema_history;"`。<br>2. 执行 `docker compose -f deploy/compose/compose.yml --profile infra down`（不带 `-v`）。<br>3. 执行 `docker compose -f deploy/compose/compose.yml --profile infra up -d`。<br>4. 等待 postgres healthy 后再次查询 `flyway_schema_history` 行数。<br>5. 验证卷存在：`docker volume ls \| grep opengeobot_postgres_data`。 |
| 预期结果 | 步骤 2 后容器移除但卷保留；步骤 4 后行数与步骤 1 一致（数据未丢失）；卷 `opengeobot_postgres_data` 在整个流程中始终存在，证明 `down` 不删除数据。 |

---

## 验收判定

- C23 通过：TC-C23-01 至 TC-C23-07 全部预期结果达成。
- C24 通过：TC-C24-01 至 TC-C24-05 全部预期结果达成。
- 任何一项失败：记录失败现象、复现步骤、相关日志（`docker logs`、`.dev-pids/*.log`），修复后重测。

## 关联文档

- [M0 工程基线 Runbook](../runbooks/m0-engineering-base.md)
- [AI 开发约束与平台公共能力规范](../AI开发约束与平台公共能力规范%20V1.0.md)
- [平台功能与数据状态统一实施蓝图](../平台功能与数据状态统一实施蓝图%20V1.0.md)
