<!--
Function: F-MEMORY-001 observability summary — Mission memory, failure cases & improvement loop observability
Time: 2026-07-05
Author: AxeXie
-->
# F-MEMORY-001 Observability Summary — Mission Memory, Failure Cases & Improvement Loop

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs via Vector (port 3100)
- Grafana visualizes dashboards (port 3000)

## Metrics
- memory.extracted counter by source type
- failure_case.created counter by severity
- improvement.created / improvement.approved / improvement.released / improvement.rolled_back counters
- memory.embedding_count gauge

## Audit
- All memory and improvement operations recorded to sys_operation_audit with actor_id, trace_id
- Memory extraction, failure case, and improvement events written to transactional outbox

## Tracing
- trace_id generated per memory operation, propagated to audit records and logs
- Improvement approval/release/rollback traceable end-to-end via trace_id
- Failure cases linked to originating trace_id for fact replay

## Logging
- Structured logs (Vector -> Loki)
- Memory extraction logged with case_id and trace_id
- Sensitive fields masked in logs
