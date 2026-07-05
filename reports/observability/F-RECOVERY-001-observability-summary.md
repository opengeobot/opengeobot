<!--
Function: F-RECOVERY-001 observability summary — Data backup, recovery & disaster drill observability
Time: 2026-07-05
Author: AxeXie
-->
# F-RECOVERY-001 Observability Summary — Data Backup, Recovery & Disaster Drill

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs via Vector (port 3100)
- Grafana visualizes dashboards (port 3000)

## Metrics
- backup.completed / backup.failed counters by backup type
- backup.duration histograms
- restore_drill.completed counter and duration
- recovery.point_age gauge (time since last successful backup)

## Audit
- All backup and restore operations recorded to sys_operation_audit with actor_id, trace_id
- Backup and restore drill events written to transactional outbox

## Tracing
- trace_id generated per backup/restore operation, propagated to audit records and logs
- Disaster drill execution traceable end-to-end via trace_id

## Logging
- Structured logs (Vector -> Loki)
- Backup records logged with backup_id, size, and trace_id
- Restore drills logged with drill_id and trace_id
- Sensitive fields masked in logs
