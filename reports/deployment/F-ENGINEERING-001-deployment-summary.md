# F-ENGINEERING-001 Deployment Summary

## Engineering Scaffold Deployment

- **Build system**: Maven 3.9.9 with Maven Wrapper
- **Java version**: 21 (Eclipse Temurin)
- **Spring Boot**: 3.3.5
- **Docker Compose**: v2 with profiles (infra, observability, cloud, full)
- **Dev scripts**: scripts/dev.sh (Bash), scripts/dev.ps1 (PowerShell)
- **Health checks**: /health/live, /health/ready, /health/info
- **CI gates**: Format, lint, type-check, unit test, integration test
