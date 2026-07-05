<!--
Function: F-ADAPTER-002 deployment summary — ROS1 Unitree & custom protocol adapter deployment
Time: 2026-07-05
Author: AxeXie
-->
# F-ADAPTER-002 Deployment Summary — ROS1 Unitree & Custom Protocol Adapter

## Deployment Target
- EDGE: ROSClaw Edge Runtime + adapter modules (adapter-ros1, adapter-unitree, adapter-custom)
- WEB: cloud-control Spring Boot application (port 8080)

## Artifacts
- Edge adapters: `apps/cloud-control/platform-robot/` (adapter compatibility domain)
- Migration: `V8__create_robot_model_group_tables.sql` (adapter_compatibility table)
- Contract: `contracts/openapi/paths/adapter.yaml`
- External pinned contracts: `contracts/external/EXT-ROS1-BRIDGE.md`, `contracts/external/EXT-UNITREE-SDK.md`

## Deployment Steps
1. `./scripts/dev.sh infra-up` (postgres, nats, minio)
2. `./scripts/dev.sh migrate` (Flyway applies migrations including V8)
3. `DB_PASSWORD=opengeobot_dev SPRING_PROFILES_ACTIVE=dev java -jar bootstrap-0.1.0-SNAPSHOT.jar`
4. Edge adapter deployed via ROSClaw Edge Runtime in isolated compatibility mode

## Verification
- Health check: `curl http://localhost:8080/health/live` -> 200 HEALTHY
- Adapter compatibility schema validated against capability-port contract
- ROS1 bridge runs isolated; does not affect ROS2 primary path

## Environment
- DB_PASSWORD from environment (not hardcoded)
- SPRING_PROFILES_ACTIVE=dev
- ROS1 compatibility isolated via bridge; Zenoh for constrained network
