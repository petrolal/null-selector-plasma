#!/usr/bin/env bash
# ==============================================================================
#  _                               _        _     
# | |__   __ _ _ ____   _____  ___| |_  ___| |__  
# | '_ \ / _` | '__\ \ / / _ \/ __| __|/ __| '_ \ 
# | | | | (_| | |   \ V /  __/\__ \ |_ \__ \ | | |
# |_| |_|\__,_|_|    \_/ \___||___/\__|___/_| |_|
#
# Dotfile Sync / Harvesting Script
# Scrapes active configurations from $HOME into the repository.
# Author: petrolal
# License: MIT
# ==============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Color Codes
# -----------------------------------------------------------------------------
BOLD='\033[1m'
NC='\033[0m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
PURPLE='\033[0;35m'

disable_colors() {
    BOLD=''
    NC=''
    CYAN=''
    GREEN=''
    YELLOW=''
    RED=''
    BLUE=''
    PURPLE=''
}

if [[ -n "${NO_COLOR:-}" || ! -t 1 ]]; then
    disable_colors
fi

log_info()    { printf "${CYAN}[INFO]${NC} %b\n" "$*"; }
log_success() { printf "${GREEN}[OK]${NC} %b\n" "$*"; }
log_warn()    { printf "${YELLOW}[WARN]${NC} %b\n" "$*"; }
log_error()   { printf "${RED}[ERROR]${NC} %b\n" "$*"; }
log_step()    { printf "\n${BOLD}${PURPLE}==> %b${NC}\n" "$*"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

AUTO_YES=false
for arg in "$@"; do
    case "$arg" in
        -y|--yes) AUTO_YES=true ;;
        --no-color) disable_colors ;;
        -h|--help)
            cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Scrapes active configurations from \$HOME into this repository.

Options:
    -y, --yes    Skip confirmation prompt
    --no-color   Disable color output
    -h, --help   Show this help message
EOF
            exit 0
            ;;
    esac
done

if [[ "$AUTO_YES" == false ]]; then
    printf "${YELLOW}${BOLD}[ATTENTION]${NC} This script will scrape active configs from %s into the repository.\n" "$HOME"
    printf "Any unstaged modifications in the repository will be overwritten.\n"
    printf "${BOLD}Proceed with harvesting? [y/N]: ${NC}"
    read -r response
    case "$response" in
        [yY][eE][sS]|[yY]) ;;
        *)
            log_warn "Harvest cancelled."
            exit 0
            ;;
    esac
fi

# Helper function to copy if file exists and resolve symlinks or copy content cleanly
harvest_file() {
    local src="$1"
    local dest="$2"

    if [[ -f "$src" ]]; then
        mkdir -p "$(dirname "$dest")"
        # If dest is already a symlink pointing to src (due to stow), reading or copying dereferences safely
        cp -L "$src" "$dest"
        log_success "Harvested: $src -> $dest"
    else
        log_warn "Source file not found (skipping): $src"
    fi
}

harvest_dir() {
    local src_dir="$1"
    local dest_dir="$2"

    if [[ -d "$src_dir" ]]; then
        mkdir -p "$dest_dir"
        # Copy directory contents dereferencing symlinks, excluding temporary or lock files
        rsync -aL --exclude="*.lock" \
                  --exclude="*.tmp" \
                  --exclude="*.swp" \
                  --exclude="*.log" \
                  --exclude="*history*" \
                  --exclude="*cache*" \
                  "$src_dir/" "$dest_dir/"
        log_success "Harvested directory: $src_dir -> $dest_dir"
    else
        log_warn "Source directory not found (skipping): $src_dir"
    fi
}

log_step "Harvesting KDE Plasma 6 Configurations"
harvest_file "$HOME/.config/kdeglobals"                              "plasma/.config/kdeglobals"
harvest_file "$HOME/.config/kwinrc"                                 "plasma/.config/kwinrc"
harvest_file "$HOME/.config/plasmashellrc"                          "plasma/.config/plasmashellrc"
harvest_file "$HOME/.config/plasma-org.kde.plasma.desktop-appletsrc" "plasma/.config/plasma-org.kde.plasma.desktop-appletsrc"
harvest_file "$HOME/.config/kglobalshortcutsrc"                     "plasma/.config/kglobalshortcutsrc"
harvest_file "$HOME/.config/klassy/klassyrc"                        "plasma/.config/klassy/klassyrc"

log_step "Harvesting Theme & Engine Configurations"
harvest_file "$HOME/.config/Kvantum/kvantum.kvconfig"                "kvantum/.config/Kvantum/kvantum.kvconfig"
if [[ -d "$HOME/.config/Kvantum/PetrolalDark" ]]; then
    harvest_dir "$HOME/.config/Kvantum/PetrolalDark"                "kvantum/.config/Kvantum/PetrolalDark"
fi

log_step "Harvesting Terminal & Shell Configurations"
harvest_file "$HOME/.config/kitty/kitty.conf"                       "kitty/.config/kitty/kitty.conf"
harvest_file "$HOME/.config/kitty/current-theme.conf"               "kitty/.config/kitty/current-theme.conf"
harvest_file "$HOME/.config/ghostty/config"                         "ghostty/.config/ghostty/config"
harvest_file "$HOME/.zshrc"                                         "zsh/.zshrc"
harvest_file "$HOME/.config/zsh/aliases.zsh"                        "zsh/.config/zsh/aliases.zsh"
harvest_file "$HOME/.config/fastfetch/config.jsonc"                 "fastfetch/.config/fastfetch/config.jsonc"
harvest_file "$HOME/.config/starship.toml"                         "starship/.config/starship.toml"

# -----------------------------------------------------------------------------
# Sanitization & Security Pass
# -----------------------------------------------------------------------------
log_step "Sanitizing Harvested Configurations"

# Remove transient history/token keys if any crept in
find "$SCRIPT_DIR" -type f \( -name "*.bak" -o -name "*kwallet*" -o -name "*.tmp" \) -delete

# Clean potential personal screen mappings or machine UUIDs from desktop-appletsrc
if [[ -f "$SCRIPT_DIR/plasma/.config/plasma-org.kde.plasma.desktop-appletsrc" ]]; then
    sed -i '/screenMapping=/d' "$SCRIPT_DIR/plasma/.config/plasma-org.kde.plasma.desktop-appletsrc" || true
    sed -i '/itemsOnDisabledScreens=/d' "$SCRIPT_DIR/plasma/.config/plasma-org.kde.plasma.desktop-appletsrc" || true
fi

log_success "Configs sanitized."

# -----------------------------------------------------------------------------
# Git Status Summary
# -----------------------------------------------------------------------------
log_step "Repository Status"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    printf "${BOLD}Changed files:${NC}\n"
    git status -s
    printf "\n"
    printf "To review changes in detail, run: ${CYAN}git diff${NC}\n"
    printf "To commit these updates, run:    ${CYAN}git add -A && git commit -m 'chore: harvest updated rice configs'${NC}\n"
else
    log_info "Not a git repository or git metadata not initialized."
fi
