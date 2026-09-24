#!/usr/bin/env bash
# ==============================================================================
# harvest.sh (DEPRECATED -> mono-rice / Babashka Engine)
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v bb &>/dev/null; then
    exec bb harvest "$@"
elif [[ -x "$SCRIPT_DIR/mono-rice" ]]; then
    exec "$SCRIPT_DIR/mono-rice" harvest "$@"
else
    echo "[INFO] Babashka not found in PATH. Bootstrapping..."
    exec "$SCRIPT_DIR/bootstrap.sh" harvest "$@"
fi
