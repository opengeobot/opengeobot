# F-DEPLOY-001 Observability Summary

- 功能 ID: F-DEPLOY-001
- 阶段: M0
- 日期: 2026-07-04
- 作者: AxeXie

## 监控组件

| 组件 | 配置文件 | 用途 |
|------|----------|------|
| vmagent | `deploy/observability/vmagent.yml` | 指标采集 |
| Vector | `deploy/observability/vector.yaml` | 日志采集与转换 |
| Grafana | `deploy/observability/grafana-datasources.yaml` | 数据源配置 |
| Loki | `deploy/observability/loki-config.yaml` | 日志存储 |

## 指标

- 服务健康状态 (`up`)
- 依赖连通性 (PostgreSQL)

## 日志

- 结构化 JSON 日志，由 Vector 采集至 Loki
- 关键审计事件进入 PostgreSQL（M1+ 实现）

## 告警

- M0 阶段告警规则为占位，M5 阶段由 F-ALARM-001 / F-OPS-001 补充
