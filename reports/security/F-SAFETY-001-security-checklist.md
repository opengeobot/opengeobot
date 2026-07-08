<!--
Function: F-SAFETY-001 security checklist — Action Safety, Emergency Stop & Reset security verification
Time: 2026-07-05
Author: AxeXie
-->

# F-SAFETY-001 Security Checklist — Action Safety, Emergency Stop & Reset

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

## Verification Evidence (2026-07-08)

以下安全声明已通过代码审查验证：

- **@PreAuthorize**: SafetyController 包含 4 处 @PreAuthorize 注解（safety.decision.read、safety.emergency_stop.execute、safety.emergency_stop.reset）。
- **Safety Gateway 急停锁存**: `edge/safety-gateway/src/opengeobot_safety_gateway/safety_state.py` 第 113 行实现 `trigger_emergency_stop()`，支持从任意状态触发急停并锁存；`handler.py` 第 138 行 `handle_emergency_stop()` 订阅 NATS 急停指令。
- **本地急停不依赖网络**: `safety_state.py` 第 76 行文档说明急停和复位无需云端或网络连接。
- **Agent 隔离**: `services/agent-runtime/src/opengeobot_agent/provider.py` 第 126-127 行明确不调用 `/cmd_vel`、电机或厂商 SDK。
- **输入验证**: Controller 使用 @Valid 注解校验请求体（53 处 @Valid/@Validated 跨 24 个文件）。
- **审计追踪**: trace_id 贯穿安全决策、急停事件和审计记录。
