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

if [ -d "$ROSCLAW_SOURCE_DIR" ] && [ -f "$ROSCLAW_SOURCE_DIR/pyproject.toml" ]; then
    echo "[entrypoint] Installing ROSClaw from $ROSCLAW_SOURCE_DIR ..."
    if uv pip install --system "$ROSCLAW_SOURCE_DIR"; then
        echo "[entrypoint] ROSClaw package installed via uv"
    else
        echo "[entrypoint] uv install failed, falling back to pip ..."
        pip install "$ROSCLAW_SOURCE_DIR" || {
            echo "[entrypoint] WARNING: pip install also failed; bridge will run in degraded fallback mode"
        }
    fi

    # Verify the import actually works so we get a clear log line.
    if python -c "import rosclaw; print(f'[entrypoint] ROSClaw {rosclaw.__version__} imported successfully')"; then
        echo "[entrypoint] ROSClaw runtime is available"
    else
        echo "[entrypoint] WARNING: rosclaw import failed after install; bridge will run in degraded fallback mode"
    fi
else
    echo "[entrypoint] WARNING: ROSClaw source not found at $ROSCLAW_SOURCE_DIR; bridge will run in degraded fallback mode"
fi

# Drop to the non-root bridge user for the actual runtime.
exec gosu bridge "$@"
