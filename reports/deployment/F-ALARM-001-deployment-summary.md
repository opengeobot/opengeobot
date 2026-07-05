<!--
Function: F-ALARM-001 deployment summary — Alarm lifecycle & notification deployment
Time: 2026-07-05
Author: AxeXie
-->
# F-ALARM-001 Deployment Summary — Alarm Lifecycle & Notification

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Backend: `apps/cloud-control/platform-robot/` (AlarmController, AlarmService)
- Migration: `V18__create_alarm_tables.sql` in apps/cloud-control/bootstrap/src/main/resources/db/migration/
- Contract: `contracts/openapi/paths/alarm.yaml`

## Deployment Steps
1. `docker compose -f deploy/compose/compose.yml --profile infra up -d`
2. `./scripts/dev.sh migrate` (Flyway applies migrations including V18__create_alarm_tables.sql)
3. `DB_PASSWORD=opengeobot_dev SPRING_PROFILES_ACTIVE=dev java -jar bootstrap-0.1.0-SNAPSHOT.jar`

## Verification
- Health check: `curl http://localhost:8080/health/live` -> 200 HEALTHY
- GET /api/v1/alarms -> 200 (empty list, fresh DB)
- GET /api/v1/alarms/rules -> 200 (seeded rules: robot_offline, mission_failure_rate)
- Alarm rules seeded with stable metric/condition/threshold

## Environment
- DB_PASSWORD from environment (not hardcoded)
- SPRING_PROFILES_ACTIVE=dev
