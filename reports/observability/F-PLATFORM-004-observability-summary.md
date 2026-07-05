<!--
Function: F-PLATFORM-004 observability summary — configuration, audit, idempotency and export observability
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-004 Observability Summary — Configuration, Audit, Idempotency & Export

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs (port 3100)

## Audit
- sys_operation_audit is the central audit store; all platform mutations write here
- Records include action, actor_type, actor_id, resource_type, resource_id, result, trace_id, reason_code
- Audit queryable via GET /api/v1/audits (paginated, audit.audit.read permission)

## Metrics
- Audit write rate
- Config change frequency
- Export operation duration
- Idempotency dedup hit rate

## Tracing
- trace_id on every audit record enables full-chain replay (C19 acceptance)
- Export operations carry trace_id through async state machine

## Logging
- Structured logs with trace_id
- Idempotency key collisions logged at WARN level
