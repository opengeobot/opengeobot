#!/usr/bin/env bash
# =============================================================================
# OpenGeoBot — Unified Development Script
# Function: M0 engineering scaffold — unified dev entry point
# Time: 2026-07-03
# Author: AxeXie
#
# Usage:
#   ./scripts/dev.sh doctor|bootstrap|infra-up|migrate|dev|sim-up|test|e2e|down
# =============================================================================
set -euo pipefail

# -----------------------------------------------------------------------------
# Resolve project root (this file lives in <root>/scripts/)
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

COMPOSE_FILE="${ROOT_DIR}/deploy/compose/compose.yml"
ENV_FILE="${ROOT_DIR}/.env"
ENV_EXAMPLE="${ROOT_DIR}/.env.example"
WEB_CONSOLE_DIR="${ROOT_DIR}/apps/web-console"
AGENT_RUNTIME_DIR="${ROOT_DIR}/services/agent-runtime"
PID_DIR="${ROOT_DIR}/.dev-pids"

# Track background process PIDs for the `dev` command
DEV_PIDS=()

# -----------------------------------------------------------------------------
# Color helpers (disable when not a TTY)
# -----------------------------------------------------------------------------
if [ -t 1 ]; then
    GREEN='\033[0;32m'
    RED='\033[0;31m'
    YELLOW='\033[0;33m'
    CYAN='\033[0;36m'
    NC='\033[0m'
else
    GREEN=''
    RED=''
    YELLOW=''
    CYAN=''
    NC=''
fi

ok()   { printf "${GREEN}[✓]${NC} %s\n" "$*"; }
fail() { printf "${RED}[✗]${NC} %s\n" "$*"; }
info() { printf "${CYAN}[i]${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}[!]${NC} %s\n" "$*"; }

# -----------------------------------------------------------------------------
# Load .env if present (exported into the environment)
# Strips inline comments and handles values with spaces/special chars
# -----------------------------------------------------------------------------
load_env() {
    if [ -f "${ENV_FILE}" ]; then
        set -a
        while IFS= read -r line || [ -n "$line" ]; do
            # Skip empty lines and full-line comments
            line="${line%%#*}"
            [ -z "${line// }" ] && continue
            # Extract KEY=VALUE (value may contain spaces)
            key="${line%%=*}"
            val="${line#*=}"
            # Trim leading/trailing whitespace from key and val
            key="$(echo "$key" | xargs)"
            val="$(echo "$val" | xargs)"
            [ -z "$key" ] && continue
            eval "export ${key}=\"${val}\""
        done < "${ENV_FILE}"
        set +a
    fi
}

# -----------------------------------------------------------------------------
# Check whether a TCP port is listening (no connection is fully established)
# -----------------------------------------------------------------------------
port_in_use() {
    local port="$1"
    if command -v ss >/dev/null 2>&1; then
        ss -ltn "sport = :${port}" 2>/dev/null | grep -q ":${port}"
    elif command -v lsof >/dev/null 2>&1; then
        lsof -iTCP:"${port}" -sTCP:LISTEN -P -n >/dev/null 2>&1
    else
        # Fallback: try to connect via bash /dev/tcp
        (echo > "/dev/tcp/127.0.0.1/${port}") >/dev/null 2>&1
    fi
}

check_port() {
    local port="$1"
    local label="$2"
    if port_in_use "${port}"; then
        warn "Port ${port} (${label}) is already in use"
    else
        ok "Port ${port} (${label}) is free"
    fi
}

# -----------------------------------------------------------------------------
# Wait for a service healthcheck to become healthy via docker compose
# -----------------------------------------------------------------------------
wait_for_health() {
    local service="$1"
    local max_wait="${2:-120}"
    local elapsed=0
    info "Waiting for ${service} to become healthy (max ${max_wait}s)..."
    while [ "${elapsed}" -lt "${max_wait}" ]; do
        local status
        status="$(docker inspect --format='{{.State.Health.Status}}' \
            "opengeobot-${service}-1" 2>/dev/null || \
            docker inspect --format='{{.State.Health.Status}}' \
            "opengeobot_${service}_1" 2>/dev/null || \
            echo "not-found")"
        if [ "${status}" = "healthy" ]; then
            ok "${service} is healthy"
            return 0
        fi
        printf "."
        sleep 2
        elapsed=$((elapsed + 2))
    done
    echo
    fail "${service} did not become healthy within ${max_wait}s"
    return 1
}

# =============================================================================
# Subcommands
# =============================================================================

cmd_doctor() {
    info "Running environment diagnostics..."
    echo
    local all_good=0

    # --- Tools ---
    printf "${CYAN}== Tools ==${NC}\n"

    if command -v git >/dev/null 2>&1; then
        ok "git $(git --version | awk '{print $3}')"
    else
        fail "git not found"
        all_good=1
    fi

    if command -v java >/dev/null 2>&1; then
        local java_version
        java_version="$(java -version 2>&1 | head -n1 | awk -F '"' '{print $2}')"
        if [[ "${java_version}" == 21.* ]]; then
            ok "java ${java_version}"
        else
            fail "java ${java_version} found, but version 21.x is required"
            all_good=1
        fi
    else
        fail "java not found (require JDK 21)"
        all_good=1
    fi

    if command -v docker >/dev/null 2>&1; then
        ok "docker $(docker --version | awk '{print $3}' | tr -d ',')"
    else
        fail "docker not found"
        all_good=1
    fi

    if docker compose version >/dev/null 2>&1; then
        ok "docker compose $(docker compose version --short 2>/dev/null || echo 'available')"
    else
        fail "docker compose plugin not found"
        all_good=1
    fi

    if command -v node >/dev/null 2>&1; then
        ok "node $(node --version)"
    else
        fail "node not found (require >=22)"
        all_good=1
    fi

    if command -v pnpm >/dev/null 2>&1; then
        ok "pnpm $(pnpm --version)"
    else
        warn "pnpm not found (frontend bootstrap will be skipped)"
    fi

    if command -v python3 >/dev/null 2>&1; then
        ok "python3 $(python3 --version | awk '{print $2}')"
    else
        fail "python3 not found (require 3.12)"
        all_good=1
    fi

    if command -v uv >/dev/null 2>&1; then
        ok "uv $(uv --version 2>/dev/null | awk '{print $2}' || echo 'available')"
    else
        warn "uv not found (agent-runtime bootstrap will be skipped)"
    fi

    echo
    printf "${CYAN}== Ports ==${NC}\n"
    check_port 5432 "PostgreSQL"
    check_port 4222 "NATS"
    check_port 9000 "MinIO"
    check_port 8080 "Cloud Control"
    check_port 5173 "Vite Dev Server"

    echo
    if [ "${all_good}" -eq 0 ]; then
        ok "Environment looks ready"
    else
        warn "Some checks failed — review the output above"
    fi
    return "${all_good}"
}

cmd_bootstrap() {
    info "Bootstrapping project dependencies..."
    load_env

    # 1. Maven validate (non-recursive to validate parent only)
    info "Validating Maven parent POM..."
    if [ -f "${ROOT_DIR}/mvnw" ]; then
        "${ROOT_DIR}/mvnw" -N validate -B
        ok "Maven parent POM validated"
    else
        warn "mvnw not found, skipping Maven validation"
    fi

    # 2. Copy .env.example -> .env if missing (idempotent)
    if [ ! -f "${ENV_FILE}" ]; then
        if [ -f "${ENV_EXAMPLE}" ]; then
            cp "${ENV_EXAMPLE}" "${ENV_FILE}"
            ok "Copied .env.example -> .env (adjust values as needed)"
        else
            warn ".env.example not found, skipping .env creation"
        fi
    else
        ok ".env already exists"
    fi

    # 3. Frontend dependencies (if package.json exists)
    if [ -f "${WEB_CONSOLE_DIR}/package.json" ]; then
        if command -v pnpm >/dev/null 2>&1; then
            info "Installing frontend dependencies (pnpm install)..."
            (cd "${ROOT_DIR}" && pnpm install)
            ok "Frontend dependencies installed"
        else
            warn "pnpm not found, skipping frontend bootstrap"
        fi
    else
        info "apps/web-console/package.json not found yet, skipping frontend bootstrap"
    fi

    # 4. Agent runtime dependencies (if pyproject.toml exists)
    if [ -f "${AGENT_RUNTIME_DIR}/pyproject.toml" ]; then
        if command -v uv >/dev/null 2>&1; then
            info "Syncing agent-runtime dependencies (uv sync)..."
            (cd "${AGENT_RUNTIME_DIR}" && uv sync)
            ok "Agent-runtime dependencies synced"
        else
            warn "uv not found, skipping agent-runtime bootstrap"
        fi
    else
        info "services/agent-runtime/pyproject.toml not found, skipping agent bootstrap"
    fi

    ok "Bootstrap complete"
}

cmd_infra_up() {
    info "Starting infrastructure profile..."
    load_env
    docker compose -f "${COMPOSE_FILE}" --profile infra up -d
    ok "Infrastructure containers started"

    info "Waiting for healthchecks..."
    wait_for_health postgres 60 || true
    wait_for_health nats 30 || true
    wait_for_health minio 60 || true
    ok "Infrastructure is ready"
}

cmd_migrate() {
    info "Running Flyway migrations..."
    load_env

    # Use localhost PostgreSQL (the dev workflow runs migrations against the host port)
    export DB_HOST="${DB_HOST:-localhost}"
    export DB_PORT="${DB_PORT:-5432}"
    export DB_NAME="${DB_NAME:-opengeobot}"
    export DB_USER="${DB_USER:-opengeobot}"
    export DB_PASSWORD="${DB_PASSWORD:-opengeobot_dev}"

    # Step 1: Install dependent modules to local Maven repo
    info "Installing cloud-control modules..."
    "${ROOT_DIR}/mvnw" install -DskipTests -pl apps/cloud-control/bootstrap -am -B -q 2>&1 | tail -5

    # Step 2: Run Flyway migrate on bootstrap module only
    "${ROOT_DIR}/mvnw" -pl apps/cloud-control/bootstrap flyway:migrate -B \
        -Dflyway.url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
        -Dflyway.user="${DB_USER}" \
        -Dflyway.password="${DB_PASSWORD}" \
        -Dflyway.locations="classpath:db/migration" \
        -Dflyway.baselineOnMigrate=true

    ok "Flyway migrations applied"
}

cmd_dev() {
    info "Starting development servers..."
    load_env
    mkdir -p "${PID_DIR}"

    # Ensure no stale PIDs
    if [ -f "${PID_DIR}/java.pid" ] || [ -f "${PID_DIR}/web.pid" ]; then
        warn "Stale PID files found, cleaning up..."
        cmd_down_dev
    fi

    # Cleanup function (also triggered by trap)
    cleanup() {
        info "Stopping development servers..."
        cmd_down_dev
        exit 0
    }
    trap cleanup SIGINT SIGTERM

    # 1. Start Java backend
    info "Starting cloud-control (Spring Boot)..."
    SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}" \
    DB_HOST="${DB_HOST:-localhost}" \
    DB_PORT="${DB_PORT:-5432}" \
    DB_NAME="${DB_NAME:-opengeobot}" \
    DB_USER="${DB_USER:-opengeobot}" \
    DB_PASSWORD="${DB_PASSWORD:-opengeobot_dev}" \
    NATS_URL="${NATS_URL:-nats://localhost:4222}" \
    MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}" \
        "${ROOT_DIR}/mvnw" -pl apps/cloud-control/bootstrap spring-boot:run -B \
        > "${PID_DIR}/java.log" 2>&1 &
    echo $! > "${PID_DIR}/java.pid"
    DEV_PIDS+=("$(cat "${PID_DIR}/java.pid")")

    # 2. Start frontend (if it exists)
    local web_started=0
    if [ -f "${WEB_CONSOLE_DIR}/package.json" ] && command -v pnpm >/dev/null 2>&1; then
        info "Starting web-console (Vite)..."
        (cd "${WEB_CONSOLE_DIR}" && pnpm dev) > "${PID_DIR}/web.log" 2>&1 &
        echo $! > "${PID_DIR}/web.pid"
        DEV_PIDS+=("$(cat "${PID_DIR}/web.pid")")
        web_started=1
    else
        warn "web-console not ready or pnpm missing, skipping frontend"
    fi

    echo
    ok "Development servers starting"
    printf "${CYAN}  Backend  : http://localhost:8080${NC}\n"
    if [ "${web_started}" -eq 1 ]; then
        printf "${CYAN}  Frontend : http://localhost:5173${NC}\n"
    fi
    printf "${YELLOW}  Press Ctrl+C to stop both servers${NC}\n"
    echo

    # Keep the script alive until interrupted
    wait
}

# Internal: stop dev servers started by `cmd_dev`
cmd_down_dev() {
    if [ -f "${PID_DIR}/web.pid" ]; then
        local web_pid
        web_pid="$(cat "${PID_DIR}/web.pid" 2>/dev/null || true)"
        if [ -n "${web_pid}" ] && kill -0 "${web_pid}" >/dev/null 2>&1; then
            kill "${web_pid}" 2>/dev/null || true
            ok "Stopped web-console (pid ${web_pid})"
        fi
        rm -f "${PID_DIR}/web.pid"
    fi
    if [ -f "${PID_DIR}/java.pid" ]; then
        local java_pid
        java_pid="$(cat "${PID_DIR}/java.pid" 2>/dev/null || true)"
        if [ -n "${java_pid}" ] && kill -0 "${java_pid}" >/dev/null 2>&1; then
            # Send SIGTERM first; the Maven spring-boot:run process tree handles shutdown
            kill "${java_pid}" 2>/dev/null || true
            sleep 2
            kill -9 "${java_pid}" 2>/dev/null || true
            ok "Stopped cloud-control (pid ${java_pid})"
        fi
        rm -f "${PID_DIR}/java.pid"
    fi
}

cmd_sim_up() {
    info "Simulation stack not yet implemented (M2)"
}

cmd_test() {
    info "Running Java tests..."
    load_env
    "${ROOT_DIR}/mvnw" test -B
    ok "Java tests passed"

    if [ -f "${WEB_CONSOLE_DIR}/package.json" ] && command -v pnpm >/dev/null 2>&1; then
        if grep -q '"vitest"' "${WEB_CONSOLE_DIR}/package.json" 2>/dev/null \
           || [ -f "${WEB_CONSOLE_DIR}/vitest.config.ts" ] \
           || [ -f "${WEB_CONSOLE_DIR}/vitest.config.js" ]; then
            info "Running frontend tests (vitest)..."
            (cd "${WEB_CONSOLE_DIR}" && pnpm test -- --run)
            ok "Frontend tests passed"
        else
            info "Vitest not configured in web-console, skipping frontend tests"
        fi
    fi
}

cmd_e2e() {
    info "Starting full stack (infra + observability + cloud)..."
    load_env
    docker compose -f "${COMPOSE_FILE}" --profile full up -d --build
    ok "Full stack containers started"

    info "Waiting for healthchecks..."
    wait_for_health postgres 60 || true
    wait_for_health nats 30 || true
    wait_for_health minio 60 || true
    wait_for_health cloud-control 120 || true
    wait_for_health web-console 60 || true
    ok "Full stack is ready"
}

cmd_down() {
    info "Stopping Docker Compose stack (data preserved)..."
    if [ -f "${COMPOSE_FILE}" ]; then
        docker compose -f "${COMPOSE_FILE}" down
        ok "Docker Compose stack stopped (volumes retained)"
    else
        warn "Compose file not found, skipping docker down"
    fi

    # Also stop any background dev servers
    cmd_down_dev
}

# =============================================================================
# Dispatch
# =============================================================================
usage() {
    cat <<'EOF'
OpenGeoBot Unified Development Script

Usage:
  ./scripts/dev.sh <command>

Commands:
  doctor      Check toolchain and ports
  bootstrap   Install/validate all project dependencies (idempotent)
  infra-up    Start infrastructure containers (Postgres, NATS, MinIO)
  migrate     Run Flyway migrations against local PostgreSQL
  dev         Start backend + frontend dev servers (Ctrl+C to stop)
  sim-up      Start simulation stack (not yet implemented)
  test        Run Java (and frontend, if configured) tests
  e2e         Build and start the full stack (infra + observability + cloud)
  down        Stop Docker Compose stack and dev servers (keeps volumes)
EOF
}

main() {
    local cmd="${1:-}"
    case "${cmd}" in
        doctor)    cmd_doctor ;;
        bootstrap) cmd_bootstrap ;;
        infra-up)  cmd_infra_up ;;
        migrate)   cmd_migrate ;;
        dev)       cmd_dev ;;
        sim-up)    cmd_sim_up ;;
        test)      cmd_test ;;
        e2e)       cmd_e2e ;;
        down)      cmd_down ;;
        -h|--help|"") usage; exit 0 ;;
        *)
            fail "Unknown command: ${cmd}"
            usage
            exit 1
            ;;
    esac
}

main "$@"
