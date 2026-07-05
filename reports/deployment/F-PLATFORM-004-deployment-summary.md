<!--
Function: F-PLATFORM-004 deployment summary — configuration, audit, idempotency and export deployment
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-004 Deployment Summary — Configuration, Audit, Idempotency & Export

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)
- WEB: Vue 3 frontend (ConfigManagement, AuditLog pages)

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Module: `apps/cloud-control/platform-governance/` (ConfigController, AuditController, ExportController)
- Module: `apps/cloud-control/platform-common/` (idempotency, error model, trace context)
- Migration: `V6__create_config_export_tables.sql` (sys_config, sys_operation_audit, sys_idempotency_record, export_operation)

## Deployment Steps
1. Infra up (postgres on 5432)
2. Flyway migrate (V6 config/audit/idempotency/export tables)
3. Start cloud-control JAR
4. Frontend pages served on port 5173

## Verification
- GET /api/v1/configs -> 200 (paginated)
- GET /api/v1/audits -> 200 (paginated, trace_id present)
- Audit records cover LOGIN, CREATE_USER, CREATE_ROLE, ASSIGN_USER_ROLES, UPDATE_USER_STATUS
- POST /api/v1/exports registered (export_operation table)
