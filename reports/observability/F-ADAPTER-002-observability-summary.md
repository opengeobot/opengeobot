<!--
Function: F-ADAPTER-002 observability summary — ROS1 Unitree & custom protocol adapter observability
Time: 2026-07-05
Author: AxeXie
-->
# F-ADAPTER-002 Observability Summary — ROS1 Unitree & Custom Protocol Adapter

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs via Vector (port 3100)
- Grafana visualizes dashboards (port 3000)

## Metrics
- adapter.health_changed counter and gauge per adapter type (ros1, unitree, custom)
- adapter.compatibility_check count and latency histograms
- execution.started / execution.failed counters per adapter

## Audit
- All adapter health changes recorded to sys_operation_audit with actor_id, trace_id
- Adapter compatibility changes written to transactional outbox

## Tracing
- trace_id generated per adapter operation, propagated to audit records and logs
- Execution and health events traceable end-to-end via trace_id

## Logging
- Structured logs (Vector -> Loki)
- ROS1 bridge and Unitree SDK interactions logged with adapter_id and trace_id
- Sensitive fields masked in logs
