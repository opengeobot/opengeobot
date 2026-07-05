<!--
Function: F-ADAPTER-002 security checklist — ROS1 Unitree & custom protocol adapter security verification
Time: 2026-07-05
Author: AxeXie
-->
# F-ADAPTER-002 Security Checklist — ROS1 Unitree & Custom Protocol Adapter

- [x] All endpoints protected with JWT Bearer token authentication
- [x] @PreAuthorize method-level checks using stable permission codes (robot.model.manage, edge.adapter.manage)
- [x] No direct /cmd_vel, motor, or vendor SDK access (Agent/LLM isolated behind Skill/Capability)
- [x] Physical actions are registered, versioned Skills/Capabilities with IAM, Mission, Policy, Fleet, Edge Safety checks
- [x] Edge safety judgment is final and authoritative; local e-stop does not depend on cloud or network
- [x] ROS1 runs in isolated compatibility mode; does not bypass ROS2 primary path
- [x] External contracts (EXT-ROS1-BRIDGE, EXT-UNITREE-SDK) pinned before integration
- [x] Agent output treated as untrusted proposal; validated by schema, permission, state machine, resource, and safety
- [x] All operations traceable via trace_id (audit, adapter health, execution)
- [x] No secrets in code; DB password from environment variable (DB_PASSWORD)
- [x] No hardcoded role names or display text; uses permission codes and i18n keys
- [x] Business modules do not directly read/write other domains' tables; use public application interfaces
- [x] Unpublished Flyway migrations not modified; migrations are append-only
