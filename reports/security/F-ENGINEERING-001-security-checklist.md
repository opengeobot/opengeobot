# F-ENGINEERING-001 Security Checklist

- [x] Docker images use fixed versions (no latest)
- [x] .env file is gitignored
- [x] .env.example uses non-sensitive defaults
- [x] Health endpoints are publicly accessible (M0 design)
- [x] No authentication in M0 (M1 adds JWT auth)
- [x] Flyway migrations are versioned and immutable
- [x] No secrets in code or config
- [x] CORS configured for localhost:5173
