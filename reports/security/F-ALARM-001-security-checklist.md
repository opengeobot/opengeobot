<!--
Function: F-ALARM-001 security checklist — Alarm lifecycle & notification security verification
Time: 2026-07-05
Author: AxeXie
-->
# F-ALARM-001 Security Checklist — Alarm Lifecycle & Notification

- [x] All endpoints protected with JWT Bearer token authentication
- [x] @PreAuthorize method-level checks using stable permission codes (ops.alarm.read, ops.alarm.manage, ops.alarm.suppress)
- [x] Alarm rules seeded with stable metric/condition/threshold (no hardcoded success responses)
- [x] Alarm state machine (SM-ALARM-001) transitions enforced server-side
- [x] Suppression is audited and time-bounded; cannot silently disable alarms permanently
- [x] Notification channels integrate with Grafana Alerting / platform alarm service
- [x] Agent output treated as untrusted proposal; validated by schema, permission, state machine, resource, and safety
- [x] All operations traceable via trace_id (audit, alarm open/acknowledge/resolve/suppress)
- [x] No secrets in code; DB password from environment variable (DB_PASSWORD)
- [x] No hardcoded role names or display text; uses permission codes and i18n keys
- [x] Business modules do not directly read/write other domains' tables; use public application interfaces
- [x] Unpublished Flyway migrations not modified; migrations are append-only
