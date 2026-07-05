<!--
Function: F-FLEET-001 deployment summary — Multi-robot scheduling, conflict & failover deployment
Time: 2026-07-05
Author: AxeXie
-->
# F-FLEET-001 Deployment Summary — Multi-robot Scheduling, Conflict & Failover

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Backend: `apps/cloud-control/platform-robot/` (fleet domain: FleetSchedule, schedule_candidate, resource_lock, robot_assignment, fleet_conflict)
- Migration: `V17__create_fleet_tables.sql` in apps/cloud-control/bootstrap/src/main/resources/db/migration/
- Contract: `contracts/openapi/paths/fleet.yaml`

## Deployment Steps
1. `docker compose -f deploy/compose/compose.yml --profile infra up -d`
2. `./scripts/dev.sh migrate` (Flyway applies migrations including V17__create_fleet_tables.sql)
3. `DB_PASSWORD=opengeobot_dev SPRING_PROFILES_ACTIVE=dev java -jar bootstrap-0.1.0-SNAPSHOT.jar`

## Verification
- Health check: `curl http://localhost:8080/health/live` -> 200 HEALTHY
- Fleet domain entities and repository present; schedule_candidate and resource_lock tables created
- Database at version v22

## Environment
- DB_PASSWORD from environment (not hardcoded)
- SPRING_PROFILES_ACTIVE=dev
