# F-DEPLOY-001 Deployment Summary

- 功能 ID: F-DEPLOY-001
- 阶段: M0
- 日期: 2026-07-04
- 作者: AxeXie

## Compose 部署

- 主编排文件: `deploy/compose/compose.yml`
- 项目名: `opengeobot`
- 网络: `opengeobot-net` (bridge driver)
- 数据卷: `postgres_data`, `nats_data`, `minio_data`, `vm_data`, `loki_data`, `grafana_data`

## Compose Profiles 与服务

| Profile | 服务 | 镜像 (固定版本) | 健康检查 |
|---------|------|-----------------|----------|
| infra | postgres | `pgvector/pgvector:pg16` | pg_isready (10s/5s/5) |
| infra | nats | `nats:2.10.22-alpine` | wget healthz (10s/5s/5) |
| infra | minio | `minio/minio:RELEASE.2024-10-13T13-34-11Z` | curl health/live (10s/5s/5) |
| observability | victoriametrics | `victoriametrics/victoria-metrics:v1.108.1` | wget /health (10s/5s/5) |
| observability | vmagent | `victoriametrics/vmagent:v1.108.1` | none (metric scraper) |
| observability | loki | `grafana/loki:3.2.1` | wget /ready (10s/5s/5) |
| observability | vector | `timberio/vector:0.41.1-debian` | none (log shipper) |
| observability | grafana | `grafana/grafana:11.3.0` | none |
| cloud | cloud-control | 本地构建 (Maven) | Spring Boot actuator |
| cloud | web-console | 本地构建 (Vite) | HTTP / |
| sim | sim-adapter | Python 本地构建 | HTTP /health |
| sim | edge-gateway | Python 本地构建 | HTTP /health |
| sim | safety-gateway | Python 本地构建 | HTTP /health |
| sim | local-skill-executor | Python 本地构建 | HTTP /health |

## 启动顺序

1. `infra` profile 先启动（PostgreSQL、NATS、MinIO），等待健康检查通过
2. `observability` profile 可并行启动
3. `cloud` profile 依赖 infra 就绪后启动
4. `sim` profile 依赖 infra 的 NATS 服务

## 统一开发命令

- `./scripts/dev.sh doctor|bootstrap|infra-up|migrate|dev|sim-up|test|e2e|down`
- `pwsh ./scripts/dev.ps1 doctor|bootstrap|infra-up|migrate|dev|sim-up|test|e2e|down`
- `down` 命令仅停止容器，保留数据卷

## Kubernetes

- 清单目录: `deploy/kubernetes/`（M0 阶段为占位，M5 阶段补充）

## 验收

- C23: 统一开发命令可用 (`scripts/dev.sh` / `scripts/dev.ps1`)
- C24: Compose 启动后健康检查通过
