<!--
Function: F-OTA-001 security checklist — OTA release, batch deploy & rollback security verification
Time: 2026-07-05
Author: AxeXie
-->
# F-OTA-001 Security Checklist — OTA Release, Batch Deploy & Rollback

- [x] All endpoints protected with JWT Bearer token authentication
- [x] @PreAuthorize method-level checks using stable permission codes (ops.ota.read, ops.ota.manage, ops.ota.approve)
- [x] No direct /cmd_vel, motor, or vendor SDK access (Agent/LLM isolated behind Skill/Capability)
- [x] OTA releases are versioned and approved before deployment
- [x] Batch deployment respects canary/percentage rollout; rollback is automatic on failure
- [x] Edge safety judgment is final and authoritative; OTA cannot bypass Safety Gateway
- [x] Artifact integrity validated against schema:ota-artifact before deployment
- [x] Agent output treated as untrusted proposal; validated by schema, permission, state machine, resource, and safety
- [x] All operations traceable via trace_id (audit, release, deployment, rollback)
- [x] No secrets in code; DB password from environment variable (DB_PASSWORD)
- [x] No hardcoded role names or display text; uses permission codes and i18n keys
- [x] Business modules do not directly read/write other domains' tables; use public application interfaces
- [x] Unpublished Flyway migrations not modified; migrations are append-only
