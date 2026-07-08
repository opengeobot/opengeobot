# F-ENGINEERING-001 Security Checklist

- [x] Docker images use fixed versions (no latest)
- [x] .env file is gitignored
- [x] .env.example uses non-sensitive defaults
- [x] Health endpoints are publicly accessible (M0 design)
- [x] No authentication in M0 (M1 adds JWT auth)
- [x] Flyway migrations are versioned and immutable
- [x] No secrets in code or config
- [x] CORS configured for localhost:5173

## Verification Evidence (2026-07-08)

- **Docker 镜像版本**: `deploy/compose/compose.yml` 中所有镜像使用固定版本标签（如 postgres:16-alpine、nats:2.10-alpine），无 `latest`。
- **.gitignore**: 已确认 `.env` 在 `.gitignore` 中排除。
- **.env.example**: 仅包含端口、数据库名等非敏感默认值。
- **Flyway 迁移**: V1-V22 均为追加式，未修改已发布迁移。
- **CORS**: Spring Security 配置允许 localhost:5173（开发环境）。
- **健康端点**: `/actuator/health` 为只读状态端点，不泄露敏感信息。
