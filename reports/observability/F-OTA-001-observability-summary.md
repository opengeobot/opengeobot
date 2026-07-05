<!--
Function: F-OTA-001 observability summary — OTA release, batch deploy & rollback observability
Time: 2026-07-05
Author: AxeXie
-->
# F-OTA-001 Observability Summary — OTA Release, Batch Deploy & Rollback

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs via Vector (port 3100)
- Grafana visualizes dashboards (port 3000)

## Metrics
- ota.release_created counter by version
- ota.deployment_progressed gauge (items completed / total per campaign)
- ota.deployment_rolled_back counter and duration
- ota.artifact_download count and latency histograms

## Audit
- All OTA operations recorded to sys_operation_audit with actor_id, trace_id
- Release, deployment, and rollback events written to transactional outbox

## Tracing
- trace_id generated per OTA operation, propagated to audit records and logs
- Deployment progression and rollback traceable end-to-end via trace_id

## Logging
- Structured logs (Vector -> Loki)
- Deployment items logged with campaign_id, robot_id, and trace_id
- Sensitive fields masked in logs
