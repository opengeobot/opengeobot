<!--
Function: Acceptance result C01-result.md
Time: 2026-07-10T08:36:02Z
Author: AxeXie
-->

# C01 Acceptance Result

- **Status**: `PASS`
- **Timestamp**: `2026-07-10T08:36:02Z`
- **Base URL**: `http://localhost:8080`

## Details

- login as admin -> OK
- GET /api/v1/users -> 200 (items=3)
- GET /api/v1/orgs -> 200 (items=1)
- GET /api/v1/roles -> 200 (items=3)
- GET /api/v1/permissions -> 200 (items=72)
- GET /api/v1/audits -> 200 (items=147)
- POST /api/v1/users -> 201 created user_id=usr_01KX5JPR8WBHKV9B9CP242JX9Z
- PATCH /api/v1/users/{id}/status DISABLED -> 200 (cleanup)
