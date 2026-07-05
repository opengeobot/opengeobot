<!--
Function: F-MEMORY-001 security checklist — Mission memory, failure cases & improvement loop security verification
Time: 2026-07-05
Author: AxeXie
-->
# F-MEMORY-001 Security Checklist — Mission Memory, Failure Cases & Improvement Loop

- [x] All endpoints protected with JWT Bearer token authentication
- [x] @PreAuthorize method-level checks using stable permission codes (memory.memory.read, memory.failure_case.manage, memory.improvement.manage, memory.improvement.approve)
- [x] No direct /cmd_vel, motor, or vendor SDK access (Agent/LLM isolated behind Skill/Capability)
- [x] Improvement candidates require approval before release; rollback is supported
- [x] Memory evidence schema validated; failure cases linked to trace_id
- [x] Edge safety judgment is final; improvement release cannot bypass Safety Gateway
- [x] Memory & improvement state machines (SM-MEMORY-001, SM-IMPROVE-001) enforced server-side
- [x] Agent output treated as untrusted proposal; validated by schema, permission, state machine, resource, and safety
- [x] All operations traceable via trace_id (audit, memory extraction, improvement approval/release/rollback)
- [x] No secrets in code; DB password from environment variable (DB_PASSWORD)
- [x] No hardcoded role names or display text; uses permission codes and i18n keys
- [x] Business modules do not directly read/write other domains' tables; use public application interfaces
- [x] Unpublished Flyway migrations not modified; migrations are append-only
