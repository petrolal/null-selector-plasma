#!/usr/bin/env bash
# ==============================================================================
# sanguine-node-rice - One-Liner Bootstrap Entrypoint (Clojure / Babashka)
# Repository: https://github.com/petrolal/sanguine-node-rice
# ==============================================================================

set -euo pipefail

REPO_URL="https://github.com/petrolal/sanguine-node-rice.git"
TARGET_DIR="${HOME}/sanguine-node-rice"

if [[ ! -d "$TARGET_DIR" ]]; then
    echo "[INFO] Cloning sanguine-node-rice into $TARGET_DIR..."
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

echo "[INFO] Launching sanguine-node-rice deployment via Babashka..."
exec bb install "$@"
