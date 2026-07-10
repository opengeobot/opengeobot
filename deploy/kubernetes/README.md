<!--
Function: Kubernetes baseline manifests README — apply order and scope notes
Time: 2026-07-10
Author: AxeXie
-->

# OpenGeoBot Kubernetes Baseline Manifests

These manifests are a **baseline** for lab/dev clusters. They mirror fixed image tags from `deploy/compose/compose.yml` and are not a production HA topology.

## Image tags (pinned — no `latest`)

| Component | Image |
|-----------|-------|
| PostgreSQL | `pgvector/pgvector:pg16` |
| NATS | `nats:2.10.22-alpine` |
| MinIO | `minio/minio:RELEASE.2024-10-13T13-34-11Z` |
| cloud-control | `opengeobot/cloud-control:0.1.0` (build from `apps/cloud-control/Dockerfile`) |
| web-console | `opengeobot/web-console:0.1.0` (build from `apps/web-console/Dockerfile`) |

## Apply order

```bash
# 1. Namespace
kubectl apply -f deploy/kubernetes/namespace.yaml

# 2. Config + secrets (copy example first; never commit real secret.yaml)
cp deploy/kubernetes/secret.yaml.example deploy/kubernetes/secret.yaml
# edit secret.yaml with local-only values
kubectl apply -f deploy/kubernetes/configmap.yaml
kubectl apply -f deploy/kubernetes/secret.yaml

# 3. Infrastructure
kubectl apply -f deploy/kubernetes/postgres.yaml
kubectl apply -f deploy/kubernetes/nats.yaml
kubectl apply -f deploy/kubernetes/minio.yaml

# 4. Application (images must already be built/loaded into the cluster)
kubectl apply -f deploy/kubernetes/cloud-control.yaml
kubectl apply -f deploy/kubernetes/web-console.yaml
```

## Notes

- Baseline only: single replica, emptyDir for MinIO in this draft, no Ingress/TLS, no observability stack.
- Build app images before step 4, e.g. `docker build -t opengeobot/cloud-control:0.1.0 -f apps/cloud-control/Dockerfile .`
- Prefer Sealed Secrets / External Secrets for real environments; do not commit `secret.yaml`.
- Compose remains the primary local path via `./scripts/dev.sh`; K8s is the deploy target evidence for F-DEPLOY-001.
