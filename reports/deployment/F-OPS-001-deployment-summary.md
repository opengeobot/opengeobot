<!--
Function: F-OPS-001 deployment summary — Metrics, logs, health & ops situational awareness deployment
Time: 2026-07-05
Author: AxeXie
-->
# F-OPS-001 Deployment Summary — Metrics, Logs, Health & Ops Situational Awareness

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)
- OBSERVABILITY: vmagent + VictoriaMetrics + Grafana; Vector + Loki

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Backend: `apps/cloud-control/platform-robot/` (OpsController, OpsService)
- Migration: `V19__create_ops_tables.sql` in apps/cloud-control/bootstrap/src/main/resources/db/migration/
- Contract: `contracts/openapi/paths/ops.yaml`
- Observability: `deploy/observability/vmagent.yml`

## Deployment Steps
1. `docker compose -f deploy/compose/compose.yml --profile infra up -d`
2. `./scripts/dev.sh migrate` (Flyway applies migrations including V19__create_ops_tables.sql)
3. `DB_PASSWORD=opengeobot_dev SPRING_PROFILES_ACTIVE=dev java -jar bootstrap-0.1.0-SNAPSHOT.jar`

## Verification
- Health check: `curl http://localhost:8080/health/live` -> 200 HEALTHY
- GET /api/v1/ops/dashboard -> 200 (system_health: postgresql/nats/minio HEALTHY; robot/mission/alarm stats)
- GET /api/v1/ops/health -> 200 (component health: postgresql, nats, minio all HEALTHY with latency_ms)

## Environment
- DB_PASSWORD from environment (not hardcoded)
- SPRING_PROFILES_ACTIVE=dev
