<!--
Function: F-ALARM-001 observability summary — Alarm lifecycle & notification observability
Time: 2026-07-05
Author: AxeXie
-->
# F-ALARM-001 Observability Summary — Alarm Lifecycle & Notification

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs via Vector (port 3100)
- Grafana visualizes dashboards and alert rules (port 3000)

## Metrics
- alarm.opened / alarm.acknowledged / alarm.resolved counters by severity
- alarm.suppressed counter and active suppression gauge
- alarm.rule_evaluation count and latency histograms

## Audit
- All alarm state transitions recorded to sys_operation_audit with actor_id, trace_id
- Alarm events (open, acknowledge, resolve, suppress) written to transactional outbox

## Tracing
- trace_id generated per alarm operation, propagated to audit records and logs
- Alarm lifecycle traceable end-to-end via trace_id

## Logging
- Structured logs (Vector -> Loki)
- Alarm rule evaluations logged with rule_id and trace_id
- Sensitive fields masked in logs
