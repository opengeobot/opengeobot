<!--
Function: F-PLATFORM-003 deployment summary — dictionary and i18n deployment
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-003 Deployment Summary — Dictionary & i18n

## Deployment Target
- CLOUD_JAVA: cloud-control Spring Boot application (port 8080)
- WEB: Vue 3 frontend (DictManagement, I18nManagement pages)

## Artifacts
- Java JAR: `apps/cloud-control/bootstrap/target/bootstrap-0.1.0-SNAPSHOT.jar`
- Module: `apps/cloud-control/platform-governance/` (DictController, I18nController)
- Migration: `V5__create_dict_i18n_tables.sql` (sys_dict_type, sys_dict_item, sys_i18n_resource)
- Seed: user_status (3 items), robot_status (5 items), mission_status (7 items) with bilingual labels

## Deployment Steps
1. Infra up (postgres on 5432)
2. Flyway migrate (V5 dict/i18n tables + seed data)
3. Start cloud-control JAR
4. Frontend pages served on port 5173

## Verification
- GET /api/v1/dict/types -> 200 (user_status, robot_status, mission_status PUBLISHED)
- GET /api/v1/dict/types/user_status/items -> 200 (3 items, bilingual labels)
- POST /api/v1/i18n -> 201 (zh-CN and en-US resources created)
- GET /api/v1/i18n -> 200 (both locales present)
