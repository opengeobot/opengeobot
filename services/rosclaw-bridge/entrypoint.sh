#!/bin/bash
# Function: ROSClaw Bridge runtime entrypoint
# Time: 2026-07-16
# Author: AxeXie
#
# Installs the ROSClaw package from the source directory mounted at
# /opt/rosclaw-source (read-only) before starting the bridge. The install
# targets the container's system site-packages, so the read-only mount is
# sufficient. If the source is not mounted or the install fails, the bridge
# starts in degraded fallback mode (handled gracefully by bridge.py).
#
# Runs as root to perform the install, then drops to the non-root bridge user
# via gosu for the actual runtime.
set -e

ROSCLAW_SOURCE_DIR="/opt/rosclaw-source"
ROSCLAW_SOURCE_PATH="${ROSCLAW_SOURCE_DIR}/src"
READY_FILE="${ROSCLAW_BRIDGE_READY_FILE:-/tmp/opengeobot-rosclaw-bridge.ready}"
ROSCLAW_REQUIRE_RUNTIME="${ROSCLAW_REQUIRE_RUNTIME:-true}"

rm -f "$READY_FILE"

verify_rosclaw_import() {
    python -c "from rosclaw.skill_manager.registry import SkillRegistry; from rosclaw.runtime.plugin import get_runtime_plugin; import rosclaw.runtime.handlers.navigation as _n; print('[entrypoint] ROSClaw runtime modules imported successfully')"
}

if [ -d "$ROSCLAW_SOURCE_PATH" ]; then
    export PYTHONPATH="${ROSCLAW_SOURCE_PATH}:${PYTHONPATH:-}"
fi

if verify_rosclaw_import; then
    echo "[entrypoint] ROSClaw runtime is available"
else
    echo "[entrypoint] WARNING: ROSClaw runtime modules are unavailable"
    if [ "$ROSCLAW_REQUIRE_RUNTIME" = "true" ]; then
        exit 1
    fi
fi

# Drop to the non-root bridge user for the actual runtime.
exec gosu bridge "$@"
