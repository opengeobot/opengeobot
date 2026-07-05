# F-DEPLOY-001 Deployment Summary

- 功能 ID: F-DEPLOY-001
- 阶段: M0
- 日期: 2026-07-04
- 作者: AxeXie

## Compose 部署

- 主编排文件: `deploy/compose/compose.yml`
- 服务: PostgreSQL、cloud-control bootstrap
- 健康检查: `docker compose` 启动后 bootstrap 服务达到 healthy 状态
- Profile: `deploy`

## Kubernetes

- 清单目录: `deploy/kubernetes/`（M0 阶段为占位，M5 阶段补充）

## 验收

- C23: 统一开发命令可用 (`scripts/dev.sh` / `scripts/dev.ps1`)
- C24: Compose 启动后健康检查通过
