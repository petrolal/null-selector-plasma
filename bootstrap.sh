#!/usr/bin/env bash
# ==============================================================================
# Plasma Monochrome Rice - One-Liner Bootstrap Entrypoint
# Repository: https://github.com/petrolal/plasma-mono-rice
# ==============================================================================

set -euo pipefail

REPO_URL="https://github.com/petrolal/plasma-mono-rice.git"
TARGET_DIR="${HOME}/plasma-mono-rice"

if [[ ! -d "$TARGET_DIR" ]]; then
    echo "[INFO] Cloning plasma-mono-rice into $TARGET_DIR..."
    git clone "$REPO_URL" "$TARGET_DIR"
fi

cd "$TARGET_DIR"
chmod +x install.sh
exec ./install.sh "$@"
