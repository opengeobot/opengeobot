<!--
Function: F-MCP-001 security checklist — MCP Tool Registry, Grayscale & Invocation security verification
Time: 2026-07-05
Author: AxeXie
-->

# F-MCP-001 Security Checklist — MCP Tool Registry, Grayscale & Invocation

- [x] All endpoints protected with JWT Bearer token authentication
- [x] @PreAuthorize method-level checks using stable permission codes (not role names)
- [x] No direct /cmd_vel, motor, or vendor SDK access (Agent/LLM isolated behind Skill/Capability)
- [x] Physical actions are registered, versioned Skills/Capabilities with IAM, Mission, Policy, Fleet, Edge Safety checks
- [x] Edge safety judgment is final and authoritative; local e-stop does not depend on cloud or network
- [x] Agent output treated as untrusted proposal; validated by schema, permission, state machine, resource, and safety
- [x] All operations traceable via trace_id (audit, mission, safety, tool invocation, human intervention)
- [x] No secrets in code; DB password from environment variable (DB_PASSWORD)
- [x] CORS configured for dev (localhost:5173)
- [x] No hardcoded role names or display text; uses permission codes and i18n keys
- [x] Business modules do not directly read/write other domains' tables; use public application interfaces
- [x] Unpublished Flyway migrations not modified; migrations are append-only
