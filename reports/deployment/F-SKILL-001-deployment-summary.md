<!--
Function: F-SKILL-001 deployment summary — Capability & Skill Lifecycle deployment
Time: 2026-07-05
Author: AxeXie
-->

# F-SKILL-001 Deployment Summary — Capability & Skill Lifecycle

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Compose: `deploy/compose/compose.yml` (infra profile: postgres, nats, minio)
- Migration: `V9__create_skill_registry_tables.sql` in apps/cloud-control/bootstrap/src/main/resources/db/migration/

## Deployment Steps
1. `docker compose -f deploy/compose/compose.yml --profile infra up -d`
2. `./scripts/dev.sh migrate` (Flyway applies migrations including V9__create_skill_registry_tables.sql)
3. `DB_PASSWORD=opengeobot_dev SPRING_PROFILES_ACTIVE=dev java -jar bootstrap-0.1.0-SNAPSHOT.jar`

## Verification
- Health check: `curl http://localhost:8080/health/live` -> 200 HEALTHY
- API endpoint tested with valid JWT Bearer token -> 200 OK

## Environment
- DB_PASSWORD from environment (not hardcoded)
- SPRING_PROFILES_ACTIVE=dev
