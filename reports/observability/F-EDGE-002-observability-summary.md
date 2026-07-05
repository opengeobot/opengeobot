<!--
Function: F-EDGE-002 observability summary — Local Execution, Offline Cache & Reconnection Reconciliation observability
Time: 2026-07-05
Author: AxeXie
-->

# F-EDGE-002 Observability Summary — Local Execution, Offline Cache & Reconnection Reconciliation

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs via Vector (port 3100)
- Grafana visualizes dashboards (port 3000)

## Metrics
- API request count and latency histograms
- Database connection pool utilization
- Error rate counters

## Audit
- All write operations recorded to sys_operation_audit with actor_id, trace_id
- Events written to transactional outbox (platform_governance.outbox_event)

## Tracing
- trace_id generated per request, propagated to audit records and logs
- Mission and safety operations traceable end-to-end via trace_id

## Logging
- Structured logs (Vector -> Loki)
- Sensitive fields masked in logs
