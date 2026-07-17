# Debug Session: qwenpaw-422-verify

## Status
- [OPEN] Runtime rebuild and verification in progress

## Scope
- Task: `fix-qwenpaw-agent-profile-422` Task 5
- Goal: rebuild `agent-runtime`, collect runtime evidence, verify checklist runtime items
- Constraint: do not modify business code unless verification is impossible and user is informed first

## Hypotheses
1. `agent-runtime` is still using an old image/container and the fix is not in effect.
2. `agent-runtime` rebuild succeeds, but startup/runtime config still causes profile update requests to fail with HTTP 422.
3. `agent-runtime` no longer returns 422, but recent logs still contain old failures from before the rebuild.
4. `agent-runtime` or a dependency still enters `stateless mode`, indicating degraded runtime behavior.
5. A dependency health issue (`cloud-control`, `safety-gateway`, or QwenPaw endpoint) indirectly causes startup instability or degraded behavior.

## Planned Evidence
- Current container states
- Targeted `docker-compose up -d --build agent-runtime` execution result
- `agent-runtime` logs from the latest startup window and recent 3 minutes
- Health endpoint responses for ports 8000, 8080, 8081
- Container restart count / state for `opengeobot-agent-runtime-1`

## Runtime Evidence Summary
- `docker-compose ... up -d --build agent-runtime` completed successfully and produced image `sha256:eeab2419b8e95a859858733d563dee21e7bff31883f9052fed1ebaf1e25d780d`.
- Rebuilt container `opengeobot-agent-runtime-1` did not stabilize; it remained in restart loop with `RestartCount=10` during verification.
- Recent `agent-runtime` logs still showed repeated `httpx.HTTPStatusError: 422 Unprocessable Entity` for `PUT /api/agents/opengeobot-controller`.
- No `stateless mode` or `degrading to stateless mode` log lines were found in the captured recent 3-minute log window.
- `GET http://127.0.0.1:8000/api/agents/opengeobot-controller` returned `200` and the agent still existed.
- Health endpoints returned `200`:
  - `GET http://127.0.0.1:8000/api/healthz`
  - `GET http://127.0.0.1:8080/health/live`
  - `GET http://127.0.0.1:8081/health`

## Code/Image Confirmation
- Local source and rebuilt image both contain the intended minimal fix shape:
  - PUT payload builder excludes `skill_names`
  - drift detection compares managed visible fields only
- Despite that, runtime still returns `422`, indicating the current minimal payload still does not match the live QwenPaw PUT contract in this environment.
