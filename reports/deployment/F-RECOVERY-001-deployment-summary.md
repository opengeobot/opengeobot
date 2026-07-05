<!--
Function: F-RECOVERY-001 deployment summary — Data backup, recovery & disaster drill deployment
Time: 2026-07-05
Author: AxeXie
-->
# F-RECOVERY-001 Deployment Summary — Data Backup, Recovery & Disaster Drill

## Deployment Target
- INFRA: PostgreSQL backup automation
- KUBERNETES: disaster recovery manifests

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Backend: `apps/cloud-control/platform-robot/` (RecoveryController, BackupService, RestoreService, DrillService)
- Migration: `V21__create_recovery_tables.sql` in apps/cloud-control/bootstrap/src/main/resources/db/migration/
- Contract: `contracts/openapi/paths/recovery.yaml`

## Deployment Steps
1. `docker compose -f deploy/compose/compose.yml --profile infra up -d`
2. `./scripts/dev.sh migrate` (Flyway applies migrations including V21__create_recovery_tables.sql)
3. `DB_PASSWORD=opengeobot_dev SPRING_PROFILES_ACTIVE=dev java -jar bootstrap-0.1.0-SNAPSHOT.jar`

## Verification
- Health check: `curl http://localhost:8080/health/live` -> 200 HEALTHY
- GET /api/v1/recovery/backups -> 200 (empty list, fresh DB)
- Backup/restore state machines (SM-BACKUP-OPERATION, SM-RESTORE-OPERATION) enforced

## Environment
- DB_PASSWORD from environment (not hardcoded)
- SPRING_PROFILES_ACTIVE=dev
- Restore drills isolated from production data
