<!--
Function: F-PLATFORM-002 deployment summary — user/org/role/permission deployment
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-002 Deployment Summary — Users, Orgs, Roles & Permissions

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)
- WEB: Vue 3 frontend (UserManagement, OrgManagement, RoleManagement, PermissionView pages)

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Module: `apps/cloud-control/platform-iam/` (UserController, RoleController, OrgController, PermissionController)
- Migration: `V4__create_iam_rbac_tables.sql` (sys_org, sys_user_org, sys_role, sys_permission, sys_user_role, sys_role_permission)
- Seed: admin user, platform admin role, 15 permission codes

## Deployment Steps
1. Infra up (postgres on 5432)
2. Flyway migrate (V4 RBAC tables + seed data)
3. Start cloud-control JAR
4. Frontend pages served on port 5173

## Verification
- GET /api/v1/users -> 200 (paginated)
- POST /api/v1/users -> 201 (user_id returned)
- POST /api/v1/roles -> 201 (role_id returned)
- PUT /api/v1/users/{id}/roles -> 200
- PATCH /api/v1/users/{id}/status -> 200
- Audit records written for all mutations
