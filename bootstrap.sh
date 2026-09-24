#!/usr/bin/env bash
# ==============================================================================
# null-sector-plasma - One-Liner Bootstrap Entrypoint (Clojure / Babashka)
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

# Ensure Babashka (bb) runtime is available
if ! command -v bb &>/dev/null; then
    echo "[INFO] Babashka (bb) runtime not detected. Installing via pacman/installer..."
    if command -v pacman &>/dev/null; then
        sudo pacman -S --needed --noconfirm babashka || {
            curl -s https://raw.githubusercontent.com/babashka/babashka/master/install | bash
        }
    else
        curl -s https://raw.githubusercontent.com/babashka/babashka/master/install | bash
    fi
fi

echo "[INFO] Launching null-sector-plasma deployment via Babashka..."
exec bb install "$@"
