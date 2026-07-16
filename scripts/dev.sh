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
    info "Starting simulation stack..."
    load_env

    # Start infrastructure (NATS) and simulation services together.
    # docker compose up -d is idempotent: already-running containers are not restarted.
    docker compose -f "${COMPOSE_FILE}" --profile infra --profile sim up -d --build
    ok "Simulation stack containers started"

    info "Waiting for healthchecks..."
    wait_for_health nats 30 || true
    wait_for_health qwenpaw 90 || true
    wait_for_health agent-runtime 60 || true
    wait_for_health sim-adapter 60 || true
    wait_for_health rosclaw-bridge 60 || true
    wait_for_health edge-gateway 60 || true
    wait_for_health safety-gateway 60 || true
    wait_for_health local-skill-executor 60 || true
    ok "Simulation stack is ready"

    echo
    printf "${CYAN}  Simulation Stack Services:${NC}\n"
    printf "${CYAN}  QwenPaw LLM       : http://localhost:8000/v1/models${NC}\n"
    printf "${CYAN}  Safety Gateway    : http://localhost:8081/health${NC}\n"
    printf "${CYAN}  NATS Monitoring   : http://localhost:8222${NC}\n"
    printf "${CYAN}  MinIO Console     : http://localhost:9001${NC}\n"
    printf "${YELLOW}  Use 'down' to stop (data preserved)${NC}\n"
    echo
}

cmd_test() {
    # Optional platform acceptance skeletons (do not replace unit/component tests):
    #   python3 scripts/acceptance/run_c01_c02.py
    #   python3 scripts/acceptance/run_c23_c24_check.py
    # Plans: docs/test-plans/c01-c22-test-plan.md, docs/test-plans/c23-c24-test-plan.md
    # Evidence: reports/acceptance/
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

    # --- Python component tests ---
    info "Running Python tests..."
    local py_components=(
        "edge/gateway"
        "edge/safety-gateway"
        "edge/local-skill-executor"
        "services/sim-adapter"
        "services/rosclaw-bridge"
        "services/ros1-adapter"
        "services/agent-runtime"
        "services/mcp-tool-gateway"
    )
    local py_passed=()
    local py_failed=()
    local py_skipped=()

    for comp in "${py_components[@]}"; do
        local comp_dir="${ROOT_DIR}/${comp}"
        if [ ! -d "${comp_dir}" ]; then
            warn "Directory not found, skipping: ${comp}"
            py_skipped+=("${comp}")
            continue
        fi
        if [ ! -f "${comp_dir}/pyproject.toml" ]; then
            info "No pyproject.toml, skipping: ${comp}"
            py_skipped+=("${comp}")
            continue
        fi

        info "Testing Python component: ${comp}"
        if command -v uv >/dev/null 2>&1; then
            if (cd "${comp_dir}" && uv run pytest -q 2>&1); then
                ok "Python tests passed: ${comp}"
                py_passed+=("${comp}")
            else
                fail "Python tests failed: ${comp}"
                py_failed+=("${comp}")
            fi
        elif command -v python3 >/dev/null 2>&1; then
            if (cd "${comp_dir}" && python3 -m pytest -q 2>&1); then
                ok "Python tests passed: ${comp}"
                py_passed+=("${comp}")
            else
                fail "Python tests failed: ${comp}"
                py_failed+=("${comp}")
            fi
        else
            warn "Neither uv nor python3 found, skipping: ${comp}"
            py_skipped+=("${comp}")
        fi
    done

    echo
    printf "${CYAN}== Python Test Summary ==${NC}\n"
    printf "  Passed:  %d\n" "${#py_passed[@]}"
    printf "  Failed:  %d\n" "${#py_failed[@]}"
    printf "  Skipped: %d\n" "${#py_skipped[@]}"
    if [ "${#py_passed[@]}" -gt 0 ]; then
        printf "  Passed components: %s\n" "${py_passed[*]}"
    fi
    if [ "${#py_failed[@]}" -gt 0 ]; then
        printf "  Failed components: %s\n" "${py_failed[*]}"
    fi
    if [ "${#py_skipped[@]}" -gt 0 ]; then
        printf "  Skipped components: %s\n" "${py_skipped[*]}"
    fi
    echo

    if [ "${#py_failed[@]}" -gt 0 ]; then
        warn "Some Python test components failed — see output above"
    else
        ok "All Python tests passed"
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
    wait_for_health qwenpaw 90 || true
    wait_for_health agent-runtime 60 || true
    wait_for_health cloud-control 120 || true
    wait_for_health web-console 60 || true
    wait_for_health edge-gateway 60 || true
    wait_for_health safety-gateway 60 || true
    wait_for_health local-skill-executor 60 || true
    ok "Full stack is ready"

    info "Running E2E business loop test..."
    if python3 "${ROOT_DIR}/tests/e2e/test_business_loop.py"; then
        ok "E2E business loop test PASSED"
    else
        warn "E2E business loop test FAILED (see output above)"
        warn "Check that cloud-control is running and migrated: ./scripts/dev.sh migrate"
    fi
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
  sim-up      Start simulation stack (infra + qwenpaw + agent-runtime + edge + safety + executor)
  test        Run Java, frontend (if configured), and Python tests
  e2e         Build, start full stack, and run E2E business loop test
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
