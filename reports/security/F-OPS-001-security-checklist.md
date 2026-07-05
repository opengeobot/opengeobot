<!--
Function: F-OPS-001 security checklist — Metrics, logs, health & ops situational awareness security verification
Time: 2026-07-05
Author: AxeXie
-->
# F-OPS-001 Security Checklist — Metrics, Logs, Health & Ops Situational Awareness

- [x] All endpoints protected with JWT Bearer token authentication
- [x] @PreAuthorize method-level checks using stable permission codes (ops.health.read, dashboard.view)
- [x] Read-only risk level (R0_READ_ONLY); no motion or state mutation exposed
- [x] Health snapshot aggregates component status deterministically (postgresql, nats, minio)
- [x] Metrics/log fields follow loki:log-fields and metrics:platform contracts
- [x] No secrets in logs; sensitive fields masked via Vector pipeline
- [x] Agent output treated as untrusted proposal; validated by schema, permission, state machine, resource, and safety
- [x] All operations traceable via trace_id (audit, health snapshot)
- [x] No secrets in code; DB password from environment variable (DB_PASSWORD)
- [x] No hardcoded role names or display text; uses permission codes and i18n keys
- [x] Business modules do not directly read/write other domains' tables; use public application interfaces
- [x] Unpublished Flyway migrations not modified; migrations are append-only
