# F-DEPLOY-001 Observability Summary

- 功能 ID: F-DEPLOY-001
- 阶段: M0
- 日期: 2026-07-04
- 作者: AxeXie

## 监控组件

| 组件 | 配置文件 | 用途 | 端口 |
|------|----------|------|------|
| vmagent | `deploy/observability/vmagent.yml` | 指标采集 (scrape cloud-control /actuator/metrics) | 8429 |
| VictoriaMetrics | Compose 内置 | 指标存储与查询 | 8428 |
| Vector | `deploy/observability/vector.yaml` | 日志采集与转换 (JSON 结构化日志) | — |
| Grafana | `deploy/observability/grafana-datasources.yaml` | 数据源配置 (VictoriaMetrics + Loki) | 3000 |
| Loki | `deploy/observability/loki-config.yaml` | 日志存储 | 3100 |

## 指标

- 服务健康状态 (`up`)
- 依赖连通性 (PostgreSQL, NATS, MinIO)
- Spring Boot Actuator metrics: HTTP 请求计数、延迟直方图、DB 连接池利用率

## 日志

- 结构化 JSON 日志，由 Vector 采集至 Loki
- 关键审计事件进入 PostgreSQL `sys_operation_audit` 表（M1+ 实现）

## 追踪 (Tracing)

- trace_id 由公共模块生成并贯穿请求链路
- 审计记录、日志和事件均携带 trace_id

## 告警

- M0 阶段告警规则为占位，M5 阶段由 F-ALARM-001 / F-OPS-001 补充
- Grafana Alerting 为告警后端
