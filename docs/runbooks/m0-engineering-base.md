<!--
Function: M0 Engineering Base Runbook — environment bootstrap, infra, migration, dev, test, shutdown & troubleshooting
Time: 2026-07-04
Author: AxeXie
-->

# M0 工程基线 Runbook

本 Runbook 覆盖 M0 阶段（F-ENGINEERING-001）的本地开发闭环：环境准备、依赖初始化、基础设施启动、数据库迁移、应用启动、测试、关闭与故障排查。

所有操作通过统一入口脚本完成：

```bash
./scripts/dev.sh doctor|bootstrap|infra-up|migrate|dev|sim-up|test|e2e|down
```

> 安全红线：`down` 只停止容器与开发服务器，**不会删除任何数据卷**。任何需要清除数据的重置操作必须独立、显式执行（见 [关闭与清理](#关闭与清理)）。

---

## 1. 环境准备

### 1.1 必需工具与版本

| 工具 | 要求版本 | 用途 | 校验方式 |
|------|----------|------|----------|
| git | 任意现代版本 | 版本控制 | `git --version` |
| JDK | 21.x（必须 21 主版本） | 云控制面编译运行 | `java -version` |
| Docker | 24+ | 基础设施容器 | `docker --version` |
| Docker Compose | v2 插件 | 编排 | `docker compose version` |
| Node.js | 22+（推荐 nvm） | 前端构建 | `node --version` |
| pnpm | 9+ | 前端依赖 | `pnpm --version` |
| Python | 3.12+ | Agent 运行时 | `python3 --version` |
| uv | 最新稳定 | Python 依赖管理 | `uv --version` |
| Maven | 3.9+（项目已含 `mvnw`） | Java 构建 | `./mvnw --version` |

### 1.2 一键环境自检

```bash
./scripts/dev.sh doctor
```

`doctor` 会逐项检查上述工具，并探测关键端口是否被占用：

- `5432` PostgreSQL
- `4222` NATS
- `9000` MinIO
- `8080` 云控制面
- `5173` Vite 开发服务器

- 缺失**必需**工具（git / java 21 / docker / docker compose / node / python3）时返回非零退出码。
- 缺失**可选**工具（pnpm / uv）时仅给出告警，对应的 bootstrap 步骤会被跳过。
- 端口被占用时给出告警（`[!]`），需先释放端口或调整 `.env` 再继续。

---

## 2. Bootstrap 流程（依赖初始化）

`bootstrap` 是幂等的，可重复执行。它在全新检出后完成所有依赖与配置初始化。

```bash
./scripts/dev.sh bootstrap
```

执行步骤（按顺序）：

1. **加载 `.env`**：若已存在则导出到当前环境。
2. **Maven 父 POM 校验**：执行 `./mvnw -N validate -B`（非递归，仅校验父 POM 结构）。
3. **创建 `.env`**：若不存在且 `.env.example` 存在，则复制 `.env.example -> .env`；已存在则跳过。
4. **前端依赖安装**：若 `apps/web-console/package.json` 存在且 `pnpm` 可用，执行 `pnpm install`（在工作区根目录）。
5. **Agent 依赖同步**：若 `services/agent-runtime/pyproject.toml` 存在且 `uv` 可用，执行 `uv sync`。

完成后会输出 `Bootstrap complete`。首次执行后建议检查 `.env` 中的数据库与中间件连接参数是否符合本地环境。

---

## 3. 基础设施启动与健康验证

### 3.1 启动基础设施

```bash
./scripts/dev.sh infra-up
```

等价于：

```bash
docker compose -f deploy/compose/compose.yml --profile infra up -d
```

启动 `infra` profile 下的三个服务：

| 服务 | 镜像 | 端口 | 健康检查 |
|------|------|------|----------|
| postgres | `pgvector/pgvector:pg16` | 5432 | `pg_isready -U opengeobot -d opengeobot` |
| nats | `nats:2.10.22-alpine` | 4222 / 8222 | `wget --spider http://localhost:8222/healthz` |
| minio | `minio/minio:RELEASE.2024-10-13T13-34-11Z` | 9000 / 9001 | `curl -sf http://localhost:9000/minio/health/live` |

### 3.2 健康验证

`infra-up` 会自动等待健康检查（postgres 60s / nats 30s / minio 60s）。也可手动验证：

```bash
# 容器状态（应显示 healthy）
docker compose -f deploy/compose/compose.yml ps

# PostgreSQL
docker exec -it opengeobot-postgres-1 pg_isready -U opengeobot -d opengeobot

# NATS JetStream
curl -s http://localhost:8222/healthz

# MinIO
curl -s http://localhost:9000/minio/health/live
```

MinIO 控制台：http://localhost:9001 （账号 `opengeobot` / 密码 `opengeobot_dev`）。

---

## 4. 数据库迁移流程

### 4.1 执行迁移

```bash
./scripts/dev.sh migrate
```

`migrate` 通过 Maven Wrapper 调用 Flyway，针对本地主机端口（非容器网络）执行：

- 默认连接参数：`DB_HOST=localhost`、`DB_PORT=5432`、`DB_NAME=opengeobot`、`DB_USER=opengeobot`、`DB_PASSWORD=opengeobot_dev`。
- 可通过 `.env` 或环境变量覆盖。
- 迁移脚本位于 `apps/cloud-control/bootstrap/src/main/resources/db/migration/`（如 `V1__create_domain_schemas.sql`、`V2__create_common_tables.sql`）。
- 启用 `baseline-on-migrate=true`，便于在已有库上首次接入。

### 4.2 验证迁移结果

```bash
# 查看已应用版本
docker exec -it opengeobot-postgres-1 \
  psql -U opengeobot -d opengeobot -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

成功输出应包含 `V1`、`V2` 且 `success = t`。

---

## 5. 应用启动

### 5.1 开发模式（dev）

```bash
./scripts/dev.sh dev
```

启动后台进程（PID 与日志写入 `.dev-pids/`）：

1. **云控制面**：`./mvnw -pl apps/cloud-control/bootstrap spring-boot:run`，`SPRING_PROFILES_ACTIVE=dev`，监听 http://localhost:8080。
2. **前端**：`pnpm dev`（在 `apps/web-console/`），监听 http://localhost:5173。

- 按 `Ctrl+C` 触发 `SIGINT`，脚本会优雅停止后端与前端两个进程。
- 若检测到残留 PID 文件，会先自动清理再启动。
- 日志路径：`.dev-pids/java.log`、`.dev-pids/web.log`。

健康验证：

```bash
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready
```

### 5.2 Docker 模式（full profile）

适用于不依赖本地工具链、直接用容器跑全栈的场景：

```bash
# 启动完整栈（基础设施 + 可观测性 + 云控制面 + 前端），并构建镜像
docker compose -f deploy/compose/compose.yml --profile full up -d --build

# 等待健康检查
docker compose -f deploy/compose/compose.yml ps
```

`full` profile 包含：

- `infra`：postgres / nats / minio
- `observability`：victoriametrics / vmagent / loki / vector / grafana
- `cloud`：cloud-control / web-console（依赖 postgres、nats、minio 健康）

也可通过 `./scripts/dev.sh e2e` 触发等价流程并自动等待 cloud-control / web-console 健康。

各 profile 可独立启动：

```bash
docker compose -f deploy/compose/compose.yml --profile infra up -d
docker compose -f deploy/compose/compose.yml --profile observability up -d
docker compose -f deploy/compose/compose.yml --profile cloud up -d
```

---

## 6. 测试流程

### 6.1 单元与集成测试

```bash
./scripts/dev.sh test
```

执行顺序：

1. **Java 测试**：`./mvnw test -B`。
2. **前端测试**：若 `apps/web-console` 配置了 vitest（`package.json` 含 `vitest` 或存在 `vitest.config.*`），执行 `pnpm test -- --run`；否则跳过。

### 6.2 端到端完整栈

```bash
./scripts/dev.sh e2e
```

构建并启动 `full` profile，等待 postgres / nats / minio / cloud-control / web-console 全部健康。

> 说明：`sim-up`（仿真栈）当前为占位，计划在 M2 实现。

---

## 7. 关闭与清理

### 7.1 停止服务（保留数据）

```bash
./scripts/dev.sh down
```

`down` 执行两件事：

1. `docker compose -f deploy/compose/compose.yml down` —— 停止并移除容器，**保留所有数据卷**（`postgres_data`、`nats_data`、`minio_data`、`vm_data`、`loki_data`、`grafana_data`）。
2. 停止 `dev` 命令启动的后台开发服务器（Java + 前端）。

> **重要**：`down` 不会删除任何数据。再次 `infra-up` 或 `--profile full up -d` 后，数据会恢复可用。

### 7.2 重置数据（需显式确认，破坏性）

任何数据清除都必须独立、显式执行，平台脚本默认不提供隐式清除。如确需重置：

```bash
# ⚠️ 破坏性操作：删除所有数据卷，不可恢复
docker compose -f deploy/compose/compose.yml down -v
```

执行前请二次确认。生产环境严禁使用 `-v`。

### 7.3 清理构建产物（可选）

```bash
./mvnw clean -B
rm -rf apps/web-console/node_modules
```

---

## 8. 故障排查

### 8.1 端口冲突

**现象**：`doctor` 报 `Port <PORT> (<label>) is already in use`；或容器启动失败提示端口被占用。

**排查**：

```bash
# 查看占用进程
ss -ltnp 'sport = :5432'    # 或 :4222 / :9000 / :8080 / :5173
lsof -iTCP:5432 -sTCP:LISTEN -P -n
```

**处理**：

- 停止占用进程：`kill <PID>`（必要时 `kill -9`）。
- 或在 `.env` 中调整宿主端口映射并修改 `docker-compose` 端口绑定。
- 若是本平台自身遗留进程：`./scripts/dev.sh down` 后重试。

### 8.2 Docker 权限问题

**现象**：`permission denied while trying to connect to the Docker daemon socket`。

**处理**：

```bash
# 方案 A：将当前用户加入 docker 组（需重新登录生效）
sudo usermod -aG docker "$USER"
newgrp docker

# 方案 B：临时使用 sudo（仅排查用，不建议长期）
sudo docker compose -f deploy/compose/compose.yml --profile infra up -d
```

验证：`docker ps` 无需 sudo 即可返回。

### 8.3 数据库连接失败

**现象**：`migrate` 或 `dev` 报连接超时 / 认证失败（如 `FATAL: password authentication failed`）。

**排查**：

```bash
# 1. 确认 postgres 容器健康
docker compose -f deploy/compose/compose.yml ps postgres
docker exec -it opengeobot-postgres-1 pg_isready -U opengeobot -d opengeobot

# 2. 确认端口可达
ss -ltn 'sport = :5432'

# 3. 核对 .env 中的连接参数
DB_HOST=localhost
DB_PORT=5432
DB_NAME=opengeobot
DB_USER=opengeobot
DB_PASSWORD=opengeobot_dev
```

**处理**：

- 若容器未起：`./scripts/dev.sh infra-up`。
- 若参数不匹配：修正 `.env`，默认账号为 `opengeobot` / `opengeobot_dev`。
- 若数据卷损坏：先 `down -v`（破坏性，见 7.2）后重新 `infra-up` + `migrate`。

### 8.4 构建失败

**Java 构建失败**

```bash
# 校验父 POM
./mvnw -N validate -B

# 清理后重新构建
./mvnw clean test -B
```

- 确认 `java -version` 为 21.x（不是 17 或其他）。
- 清理本地 Maven 缓存中损坏的依赖：删除 `~/.m2/repository/<对应包>` 后重试。

**前端构建失败**

```bash
cd apps/web-console
rm -rf node_modules
pnpm install
```

- 确认 Node 版本：`node --version`（应 22+，参考 `.nvmrc`）。
- 确认 pnpm：`pnpm --version`（应 9+）。

**Docker 构建失败**

```bash
# 不使用缓存重新构建
docker compose -f deploy/compose/compose.yml --profile full build --no-cache
```

- 确认磁盘空间：`docker system df`。
- 必要时清理无用镜像：`docker image prune -f`（不影响数据卷）。

### 8.5 健康检查不通过

**现象**：`wait_for_health` 报 `<service> did not become healthy within <N>s`。

**排查**：

```bash
# 查看容器日志
docker logs opengeobot-postgres-1
docker logs opengeobot-cloud-control-1

# 查看健康检查状态
docker inspect --format='{{.State.Health.Status}}' opengeobot-postgres-1
docker inspect --format='{{json .State.Health}}' opengeobot-cloud-control-1
```

- cloud-control 健康检查依赖 `http://localhost:8080/health/live`：若 Spring Boot Actuator 未就绪，延长等待或检查 `application-dev.yml` 配置。
- vmagent / vector / grafana 依赖 victoriametrics 或 loki 健康，需先确保被依赖服务健康。

---

## 9. 相关文档

- [AI 开发约束与平台公共能力规范](../AI开发约束与平台公共能力规范%20V1.0.md)
- [平台功能与数据状态统一实施蓝图](../平台功能与数据状态统一实施蓝图%20V1.0.md)
- [ADR-0001 统一技术基线与前端主栈](../adr/ADR-0001-统一技术基线与前端主栈.md)
- [C23/C24 测试计划](../test-plans/c23-c24-test-plan.md)
