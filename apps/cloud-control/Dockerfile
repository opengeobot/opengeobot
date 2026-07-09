# =============================================================================
# OpenGeoBot — Cloud Control Dockerfile (multi-stage)
# Function: Build the modular-monolith Spring Boot application into a runnable image
# Time: 2026-07-03
# Author: AxeXie
# =============================================================================

# ---- Build stage -----------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy wrapper and POMs first to maximise Docker layer caching
COPY pom.xml ./
COPY mvnw ./
COPY .mvn ./.mvn
COPY apps/cloud-control/pom.xml ./apps/cloud-control/
COPY apps/cloud-control/platform-common/pom.xml ./apps/cloud-control/platform-common/
COPY apps/cloud-control/platform-iam/pom.xml ./apps/cloud-control/platform-iam/
COPY apps/cloud-control/platform-governance/pom.xml ./apps/cloud-control/platform-governance/
COPY apps/cloud-control/platform-robot/pom.xml ./apps/cloud-control/platform-robot/
COPY apps/cloud-control/bootstrap/pom.xml ./apps/cloud-control/bootstrap/

# Pre-fetch dependencies for offline build
RUN ./mvnw -pl apps/cloud-control -am dependency:go-offline -B

# Copy source and package
COPY apps/cloud-control/ ./apps/cloud-control/
RUN ./mvnw -pl apps/cloud-control/bootstrap -am package -DskipTests -B

# ---- Run stage -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=builder /build/apps/cloud-control/bootstrap/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
    CMD wget --spider -q http://127.0.0.1:8080/health/live || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
