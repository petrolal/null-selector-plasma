#!/usr/bin/env bash
# ==============================================================================
# null-sector-plasma - One-Liner Bootstrap Entrypoint
# Repository: https://github.com/petrolal/null-sector-plasma
# ==============================================================================

set -euo pipefail

REPO_URL="https://github.com/petrolal/null-sector-plasma.git"
TARGET_DIR="${HOME}/null-sector-plasma"

if [[ ! -d "$TARGET_DIR" ]]; then
    echo "[INFO] Cloning null-sector-plasma into $TARGET_DIR..."
    git clone "$REPO_URL" "$TARGET_DIR"
fi

cd "$TARGET_DIR"
chmod +x install.sh
exec ./install.sh "$@"
