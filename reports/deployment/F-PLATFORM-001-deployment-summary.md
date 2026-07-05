<!--
Function: F-PLATFORM-001 deployment summary — authentication and session deployment
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-001 Deployment Summary — Authentication & Session

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)
- WEB: Vue 3 frontend served on port 5173

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Compose: `deploy/compose/compose.yml` (cloud profile)
- Migration: `V3__create_iam_auth_tables.sql` (sys_user, sys_refresh_token, sys_session)

## Deployment Steps
1. `docker compose -f deploy/compose/compose.yml --profile infra up -d` (postgres, nats, minio)
2. `./scripts/dev.sh migrate` (Flyway applies V1-V6 including V3 auth tables)
3. `DB_PASSWORD=opengeobot_dev SPRING_PROFILES_ACTIVE=dev java -jar bootstrap-0.1.0-SNAPSHOT.jar`
4. Frontend: `pnpm dev` or docker web-console on port 5173

## Verification
- Health check: `curl http://localhost:8080/health/live` -> 200 HEALTHY
- Login: `POST /api/v1/auth/login` returns JWT + refresh token
- Profile: `GET /api/v1/profile` with Bearer token -> 200

## Environment
- DB_PASSWORD from environment (not hardcoded)
- SPRING_PROFILES_ACTIVE=dev
- JWT secret configured in application-dev.yml
