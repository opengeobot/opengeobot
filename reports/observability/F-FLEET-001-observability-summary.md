<!--
Function: F-FLEET-001 observability summary — Multi-robot scheduling, conflict & failover observability
Time: 2026-07-05
Author: AxeXie
-->
# F-FLEET-001 Observability Summary — Multi-robot Scheduling, Conflict & Failover

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs via Vector (port 3100)
- Grafana visualizes dashboards (port 3000)

## Metrics
- fleet.schedule_decision count and latency histograms
- fleet.conflict_detected counter by conflict type
- fleet.robot_assignment gauge (active assignments per robot)
- fleet.reassignment_completed counter and duration

## Audit
- All fleet decisions recorded to sys_operation_audit with actor_id, trace_id
- Schedule candidates, resource locks, and reassignments written to transactional outbox

## Tracing
- trace_id generated per schedule decision, propagated to audit records and logs
- Conflict detection and failover reassignment traceable end-to-end via trace_id

## Logging
- Structured logs (Vector -> Loki)
- Resource lock acquisition/release logged with lock_id and trace_id
- Sensitive fields masked in logs
