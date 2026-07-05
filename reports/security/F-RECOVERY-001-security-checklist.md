<!--
Function: F-RECOVERY-001 security checklist — Data backup, recovery & disaster drill security verification
Time: 2026-07-05
Author: AxeXie
-->
# F-RECOVERY-001 Security Checklist — Data Backup, Recovery & Disaster Drill

- [x] All endpoints protected with JWT Bearer token authentication
- [x] @PreAuthorize method-level checks using stable permission codes (ops.backup.read, ops.backup.manage, ops.restore.execute)
- [x] Backup records are immutable and traceable to recovery points
- [x] Restore drills execute in isolated environment; no production data overwritten without explicit confirmation
- [x] Backup operation state machine (SM-BACKUP-OPERATION) enforces terminal states
- [x] Restore operation state machine (SM-RESTORE-OPERATION) enforces drill vs real restore separation
- [x] Agent output treated as untrusted proposal; validated by schema, permission, state machine, resource, and safety
- [x] All operations traceable via trace_id (audit, backup, restore drill)
- [x] No secrets in code or backups; DB password from environment variable (DB_PASSWORD)
- [x] No hardcoded role names or display text; uses permission codes and i18n keys
- [x] Business modules do not directly read/write other domains' tables; use public application interfaces
- [x] Unpublished Flyway migrations not modified; migrations are append-only
