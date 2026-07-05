<!--
Function: F-PLATFORM-002 observability summary — user/org/role/permission observability
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-002 Observability Summary — Users, Orgs, Roles & Permissions

## Observability Stack
- vmagent scrapes cloud-control actuator metrics (deploy/observability/vmagent.yml)
- VictoriaMetrics stores metrics (port 8428)
- Loki stores logs (port 3100)

## Audit
- CREATE_USER, CREATE_ROLE, ASSIGN_USER_ROLES, UPDATE_USER_STATUS recorded to sys_operation_audit
- Each audit record carries trace_id linking to request context
- actor_id identifies the admin performing the mutation

## Metrics
- User CRUD operation counts
- Role assignment rate
- Permission check latency

## Tracing
- trace_id propagated through controller -> service -> audit writer
- All mutations auditable end-to-end

## Logging
- Structured logs with trace_id
- Permission denials logged at WARN level
