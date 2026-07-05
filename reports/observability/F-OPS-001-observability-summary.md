<!--
Function: F-OPS-001 observability summary — Metrics, logs, health & ops situational awareness observability
Time: 2026-07-05
Author: AxeXie
-->
# F-OPS-001 Observability Summary — Metrics, Logs, Health & Ops Situational Awareness

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs via Vector (port 3100)
- Grafana visualizes dashboards (port 3000)

## Metrics
- service.health gauge per component (postgresql, nats, minio)
- ops.dashboard_request count and latency histograms
- telemetry_rollup aggregation counters
- health_snapshot staleness gauge

## Audit
- All ops dashboard and health accesses recorded to sys_operation_audit with actor_id, trace_id
- Service health changes written to transactional outbox

## Tracing
- trace_id generated per ops request, propagated to audit records and logs
- Health snapshot and runbook link access traceable via trace_id

## Logging
- Structured logs (Vector -> Loki) following loki:log-fields contract
- Component health checks logged with component name and latency_ms
- Sensitive fields masked in logs
