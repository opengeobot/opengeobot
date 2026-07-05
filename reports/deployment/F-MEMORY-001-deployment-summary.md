<!--
Function: F-MEMORY-001 deployment summary — Mission memory, failure cases & improvement loop deployment
Time: 2026-07-05
Author: AxeXie
-->
# F-MEMORY-001 Deployment Summary — Mission Memory, Failure Cases & Improvement Loop

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)
- AGENT_PYTHON: QwenPaw via AgentRuntimeProvider

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Backend: `apps/cloud-control/platform-robot/` (MemoryController, MemoryService)
- Migration: `V22__create_memory_tables.sql` in apps/cloud-control/bootstrap/src/main/resources/db/migration/
- Contract: `contracts/openapi/paths/memory.yaml`

## Deployment Steps
1. `docker compose -f deploy/compose/compose.yml --profile infra up -d`
2. `./scripts/dev.sh migrate` (Flyway applies migrations including V22__create_memory_tables.sql)
3. `DB_PASSWORD=opengeobot_dev SPRING_PROFILES_ACTIVE=dev java -jar bootstrap-0.1.0-SNAPSHOT.jar`

## Verification
- Health check: `curl http://localhost:8080/health/live` -> 200 HEALTHY
- GET /api/v1/memory/cases -> 200 (empty list, fresh DB)
- GET /api/v1/memory/suggestions -> 200 (empty list, fresh DB)
- Memory/improvement state machines (SM-MEMORY-001, SM-IMPROVE-001) enforced

## Environment
- DB_PASSWORD from environment (not hardcoded)
- SPRING_PROFILES_ACTIVE=dev
- pgvector used for memory_embedding
