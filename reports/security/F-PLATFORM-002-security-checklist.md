<!--
Function: F-PLATFORM-002 security checklist — user/org/role/permission security verification
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-002 Security Checklist — Users, Orgs, Roles & Permissions

- [x] Permission codes are stable string constants (platform.user.read, platform.role.manage, etc.), not role names
- [x] `@PreAuthorize` on all user/role/org/permission controller methods
- [x] Role assignment is a full replace operation (PUT replaces entire role set), audited
- [x] User status transitions (ACTIVE/DISABLED/LOCKED) recorded with reason_code in audit
- [x] Built-in roles cannot be deleted (built_in flag)
- [x] All mutations write audit records (CREATE_USER, CREATE_ROLE, ASSIGN_USER_ROLES, UPDATE_USER_STATUS)
- [x] trace_id propagated to every audit record
- [x] Password never returned in user DTO responses
- [x] Pagination enforced on list endpoints (page/pageSize)
- [x] No cross-domain table access; IAM module owns platform_iam schema only
- [x] R1_NON_MOTION risk level; no edge safety required
- [x] No hardcoded role names in authorization checks (permission-code based)
