<!--
Function: F-PLATFORM-001 security checklist — authentication and session security verification
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-001 Security Checklist — Authentication & Session

- [x] Passwords hashed with BCrypt (cost factor configured), never returned in responses
- [x] JWT signed with HS512 secret key, expiry enforced (expires_in=1800)
- [x] Refresh tokens stored with `rt_` prefix, revocable via session endpoint
- [x] Login endpoint requires no prior authentication; all other endpoints require valid Bearer token
- [x] Permission claims embedded in JWT (platform.profile.read/manage, platform.user.read/manage, etc.)
- [x] `@PreAuthorize` method-level checks on every controller endpoint (permission codes, not role names)
- [x] Login action recorded in audit log with actor_id, source_ip, trace_id
- [x] No secrets in code; DB password loaded from environment variable (DB_PASSWORD)
- [x] CORS configured for localhost:5173 (dev profile)
- [x] Session/refresh token rotation supported
- [x] Disabled users cannot authenticate (status check on login)
- [x] No direct /cmd_vel, motor, or vendor SDK access (R1_NON_MOTION, no edge safety required)
