<!--
Function: F-PLATFORM-001 observability summary — authentication and session observability
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-001 Observability Summary — Authentication & Session

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs (port 3100)
- Grafana visualizes (port 3000)

## Metrics
- Login success/failure counters
- JWT issuance rate
- Session active count

## Audit
- LOGIN action recorded to sys_operation_audit with actor_id, source_ip, trace_id
- All auth events traceable via trace_id

## Tracing
- trace_id generated per request, propagated to audit records and logs
- Login flow traceable end-to-end

## Logging
- Structured logs (Vector -> Loki)
- Sensitive fields (password, token) masked in logs (enableLoggingRequestDetails=false)
