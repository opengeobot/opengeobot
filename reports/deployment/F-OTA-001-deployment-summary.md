<!--
Function: F-OTA-001 deployment summary — OTA release, batch deploy & rollback deployment
Time: 2026-07-05
Author: AxeXie
-->
# F-OTA-001 Deployment Summary — OTA Release, Batch Deploy & Rollback

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)
- EDGE: ROSClaw Edge Runtime (OTA agent)
- KUBERNETES: deployment manifests

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Backend: `apps/cloud-control/platform-robot/` (OtaController, OtaService)
- Migration: `V20__create_ota_tables.sql` in apps/cloud-control/bootstrap/src/main/resources/db/migration/
- Contract: `contracts/openapi/paths/ota.yaml`

## Deployment Steps
1. `docker compose -f deploy/compose/compose.yml --profile infra up -d`
2. `./scripts/dev.sh migrate` (Flyway applies migrations including V20__create_ota_tables.sql)
3. `DB_PASSWORD=opengeobot_dev SPRING_PROFILES_ACTIVE=dev java -jar bootstrap-0.1.0-SNAPSHOT.jar`

## Verification
- Health check: `curl http://localhost:8080/health/live` -> 200 HEALTHY
- GET /api/v1/ota/packages -> 200 (empty list, fresh DB)
- GET /api/v1/ota/campaigns -> 200 (empty list, fresh DB)
- OTA state machines (SM-OTA-001, SM-OTA-002) enforced for release/deployment lifecycle

## Environment
- DB_PASSWORD from environment (not hardcoded)
- SPRING_PROFILES_ACTIVE=dev
