<!--
Function: F-FLEET-001 security checklist — Multi-robot scheduling, conflict & failover security verification
Time: 2026-07-05
Author: AxeXie
-->
# F-FLEET-001 Security Checklist — Multi-robot Scheduling, Conflict & Failover

- [x] All endpoints protected with JWT Bearer token authentication
- [x] @PreAuthorize method-level checks using stable permission codes (fleet.schedule.read, fleet.schedule.manage)
- [x] No direct /cmd_vel, motor, or vendor SDK access (Agent/LLM isolated behind Skill/Capability)
- [x] Physical actions are registered, versioned Skills/Capabilities with IAM, Mission, Policy, Fleet, Edge Safety checks
- [x] Edge safety judgment is final and authoritative; local e-stop does not depend on cloud or network
- [x] Resource locks are exclusive and versioned; concurrent schedule conflicts detected deterministically
- [x] Failover reassignment respects edge safety before cloud reassignment completes
- [x] Agent output treated as untrusted proposal; validated by schema, permission, state machine, resource, and safety
- [x] All operations traceable via trace_id (audit, fleet decision, conflict, reassignment)
- [x] No secrets in code; DB password from environment variable (DB_PASSWORD)
- [x] No hardcoded role names or display text; uses permission codes and i18n keys
- [x] Business modules do not directly read/write other domains' tables; use public application interfaces
- [x] Unpublished Flyway migrations not modified; migrations are append-only
