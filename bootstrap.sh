#!/usr/bin/env bash
# ==============================================================================
# sanguine-node-rice - Multi-Distro Bootstrap Entrypoint (Arch, CachyOS, NixOS)
# Repository: https://github.com/petrolal/sanguine-node-rice
# ==============================================================================

set -euo pipefail

REPO_URL="https://github.com/petrolal/sanguine-node-rice.git"
TARGET_DIR="${HOME}/sanguine-node-rice"

# 1. Ensure Repository is present
if [[ ! -d "$TARGET_DIR" ]]; then
    echo "[INFO] Cloning sanguine-node-rice into $TARGET_DIR..."
    git clone "$REPO_URL" "$TARGET_DIR"
fi

cd "$TARGET_DIR"

# 2. Distro Auto-Detection
DETECTED_DISTRO="generic"
if [[ -e /etc/NIXOS ]] || ( [[ -f /etc/os-release ]] && grep -qi "id=nixos" /etc/os-release ); then
    DETECTED_DISTRO="nixos"
elif [[ -e /etc/cachyos-release ]]; then
    DETECTED_DISTRO="cachyos"
elif [[ -e /etc/arch-release ]]; then
    DETECTED_DISTRO="arch"
fi

# 3. Handle Options / Menu
SELECTED_MODE=""
EXTRA_ARGS=()

for arg in "$@"; do
    case "$arg" in
        --arch|--cachyos)
            SELECTED_MODE="arch"
            ;;
        --nix|--nixos)
            SELECTED_MODE="nix-shell"
            ;;
        --flake|--nixos-flake)
            SELECTED_MODE="nix-flake"
            ;;
        --home-manager|--hm)
            SELECTED_MODE="nix-hm"
            ;;
        --dry-run)
            EXTRA_ARGS+=("--dry-run")
            ;;
        *)
            EXTRA_ARGS+=("$arg")
            ;;
    esac
done

# If running interactively in a TTY and no mode specified via flag
if [[ -z "$SELECTED_MODE" ]] && [[ -t 0 ]] && [[ ${#EXTRA_ARGS[@]} -eq 0 ]]; then
    echo ""
    echo "=================================================================="
    echo "       S A N G U I N E // N O D E - R I C E   B O O T S T R A P   "
    echo "=================================================================="
    echo " Detected System: ${DETECTED_DISTRO^^}"
    echo ""
    echo " Please choose installation mode:"
    echo "   [1] Auto-Install (${DETECTED_DISTRO^^} Native Engine) [Default]"
    echo "   [2] Arch Linux / CachyOS (Pacman + AUR + KDE Look-and-Feel)"
    echo "   [3] NixOS Quick Shell (Run via nix-shell -p babashka)"
    echo "   [4] NixOS System Flake Guide (configuration.nix snippet)"
    echo "   [5] NixOS Home Manager Guide (home.nix snippet)"
    echo "   [6] Dry-Run Simulation (Verify configurations without changes)"
    echo "   [q] Quit"
    echo ""
    read -rp "Select option [1-6, default=1]: " USER_CHOICE
    USER_CHOICE="${USER_CHOICE:-1}"

    case "$USER_CHOICE" in
        1)
            SELECTED_MODE="auto"
            ;;
        2)
            SELECTED_MODE="arch"
            ;;
        3)
            SELECTED_MODE="nix-shell"
            ;;
        4)
            SELECTED_MODE="nix-flake"
            ;;
        5)
            SELECTED_MODE="nix-hm"
            ;;
        6)
            SELECTED_MODE="dry-run"
            EXTRA_ARGS+=("--dry-run")
            ;;
        q|Q)
            echo "[INFO] Installation cancelled."
            exit 0
            ;;
        *)
            echo "[WARN] Unknown choice '$USER_CHOICE', proceeding with auto detection..."
            SELECTED_MODE="auto"
            ;;
    esac
fi

SELECTED_MODE="${SELECTED_MODE:-auto}"

# 4. Execute Selected Mode
case "$SELECTED_MODE" in
    nix-flake)
        echo ""
        echo "=================================================================="
        echo " NixOS Flake Declarative Configuration (System Module)"
        echo "=================================================================="
        echo "1. Add sanguine-node-rice to your inputs in /etc/nixos/flake.nix:"
        echo ""
        echo '   inputs.sanguine-node-rice.url = "github:petrolal/sanguine-node-rice";'
        echo ""
        echo "2. Import the module in your NixOS configuration:"
        echo ""
        echo '   imports = ['
        echo '     inputs.sanguine-node-rice.nixosModules.default'
        echo '   ];'
        echo ''
        echo '   programs.sanguine-node-rice = {'
        echo '     enable = true;'
        echo '     enablePlymouth = true;   # dotLock boot splash'
        echo '     enableSddm = true;       # null-sector-sddm theme'
        echo '   };'
        echo ""
        echo "3. Rebuild your system:"
        echo "   sudo nixos-rebuild switch --flake .#"
        echo ""
        exit 0
        ;;

    nix-hm)
        echo ""
        echo "=================================================================="
        echo " NixOS Home Manager Declarative Configuration (User Module)"
        echo "=================================================================="
        echo "1. Add sanguine-node-rice to your flake inputs:"
        echo ""
        echo '   inputs.sanguine-node-rice.url = "github:petrolal/sanguine-node-rice";'
        echo ""
        echo "2. Import the Home Manager module in your home.nix:"
        echo ""
        echo '   imports = ['
        echo '     inputs.sanguine-node-rice.homeManagerModules.default'
        echo '   ];'
        echo ''
        echo '   programs.sanguine-node-rice.enable = true;'
        echo ""
        echo "3. Rebuild Home Manager:"
        echo "   home-manager switch --flake .#"
        echo ""
        exit 0
        ;;

    nix-shell)
        echo "[INFO] Running sanguine-node-rice inside Nix environment..."
        if command -v nix-shell &>/dev/null; then
            exec nix-shell -p babashka --run "bb install ${EXTRA_ARGS[*]:-}"
        elif command -v nix &>/dev/null; then
            exec nix develop --command bb install "${EXTRA_ARGS[@]:-}"
        else
            echo "[ERROR] Neither nix-shell nor nix command found. Please install Nix first."
            exit 1
        fi
        ;;

    arch)
        if ! command -v bb &>/dev/null; then
            echo "[INFO] Babashka not found. Installing via pacman..."
            if command -v pacman &>/dev/null; then
                sudo pacman -S --needed --noconfirm babashka || {
                    curl -s https://raw.githubusercontent.com/babashka/babashka/master/install | bash
                }
            else
                curl -s https://raw.githubusercontent.com/babashka/babashka/master/install | bash
            fi
        fi
        echo "[INFO] Launching Arch Linux / CachyOS deployment via Babashka..."
        exec bb install "${EXTRA_ARGS[@]:-}"
        ;;

    auto|dry-run)
        if [[ "$DETECTED_DISTRO" == "nixos" ]] && ! command -v bb &>/dev/null; then
            echo "[INFO] NixOS detected without standalone 'bb'. Launching through nix-shell..."
            exec nix-shell -p babashka --run "bb install ${EXTRA_ARGS[*]:-}"
        elif ! command -v bb &>/dev/null; then
            echo "[INFO] Babashka (bb) runtime not detected. Bootstrapping..."
            if command -v pacman &>/dev/null; then
                sudo pacman -S --needed --noconfirm babashka || {
                    curl -s https://raw.githubusercontent.com/babashka/babashka/master/install | bash
                }
            else
                curl -s https://raw.githubusercontent.com/babashka/babashka/master/install | bash
            fi
        fi
        echo "[INFO] Launching sanguine-node-rice deployment via Babashka..."
        exec bb install "${EXTRA_ARGS[@]:-}"
        ;;
esac
