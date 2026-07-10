# F-ENGINEERING-001 Deployment Summary

## Engineering Scaffold Deployment

- **Build system**: Maven 3.9.9 with Maven Wrapper
- **Java version**: 21 (Eclipse Temurin)
- **Spring Boot**: 3.3.5
- **Docker Compose**: v2 with profiles (infra, observability, cloud, sim, ros1, full)
- **Dev scripts**: scripts/dev.sh (Bash), scripts/dev.ps1 (PowerShell)
- **Health checks**: /health/live, /health/ready, /health/info
- **CI gates**: Format, lint, type-check, unit test, integration test

## K8s + CI evidence update (2026-07-10)

- **Kubernetes baseline**: `deploy/kubernetes/` manifests exist (namespace, infra StatefulSets/Deployments, cloud-control, web-console, ConfigMap, Secret example). See `deploy/kubernetes/README.md` for apply order.
- **GitHub Actions**: `.github/workflows/ci.yml` runs on push/PR — `validate-manifest` and `frontend-test` (Node 22 + pnpm@9.12.3). Java jobs should use `actions/setup-java@v4` with Temurin 21 when enabled.
- **Acceptance structure**: `scripts/acceptance/run_c23_c24_check.py` now requires compose + `deploy/kubernetes/` + CI workflow for C24 structural PASS.
- Manifest `implementation_status` for F-ENGINEERING-001 remains **DONE** with Compose + CI + acceptance scripts as evidence (K8s covered under F-DEPLOY-001).
