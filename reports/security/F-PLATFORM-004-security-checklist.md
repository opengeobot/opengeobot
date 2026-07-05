<!--
Function: F-PLATFORM-004 security checklist — configuration, audit, idempotency and export security verification
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-004 Security Checklist — Configuration, Audit, Idempotency & Export

- [x] `@PreAuthorize` on all config/audit/export controller methods (platform.config.read/manage, audit.audit.read/export)
- [x] Configuration keys versioned (SM-VERSIONED-CONFIG); published vs draft separation
- [x] Audit records are append-only (sys_operation_audit), immutable once written
- [x] Audit records include trace_id linking to full request context
- [x] Idempotency records (sys_idempotency_record) prevent duplicate mutation execution
- [x] Export operations tracked (export_operation table, SM-ASYNC-OPERATION state machine)
- [x] Audit export requires audit.audit.export permission (separate from read)
- [x] No cross-domain table access; governance module owns platform_governance schema
- [x] R1_NON_MOTION risk level; no edge safety required
- [x] Sensitive config values not logged in plaintext
