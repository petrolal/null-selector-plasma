#!/usr/bin/env bash
# ==============================================================================
#  ____  _                               __  __                   ____  _          
# |  _ \| | __ _ ___ _ __ ___   __ _    |  \/  | ___  _ __   ___ |  _ \(_) ___ ___ 
# | |_) | |/ _` / __| '_ ` _ \ / _` |   | |\/| |/ _ \| '_ \ / _ \| |_) | |/ __/ _ \
# |  __/| | (_| \__ \ | | | | | (_| |---| |  | | (_) | | | | (_) |  _ <| | (_|  __/
# |_|   |_|\__,_|___/_| |_| |_|\__,_|___|_|  |_|\___/|_| |_|\___/|_| \_\_|\___\___|
#
# Automated Non-Destructive Installer & Deployment Script for KDE Plasma 6
# Rice: plasma-mono-rice (Cyberpunk / NieR Monochrome Aesthetic)
# Author: petrolal
# License: MIT
# ==============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Color Codes & Logging
# -----------------------------------------------------------------------------
BOLD='\033[1m'
NC='\033[0m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
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

# -----------------------------------------------------------------------------
# Script Variables & Arguments
# -----------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="${HOME}/.config_backup_mono_${TIMESTAMP}"

DRY_RUN=false
SKIP_BACKUP=false
SKIP_DEPS=false
APPLY_LAYOUT=false
AUTO_YES=false

print_help() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Automated non-destructive deployment script for Plasma Monochrome Rice.

Options:
    -h, --help          Show this help message and exit
    -n, --dry-run       Simulate the installation without writing any files
    --no-backup         Skip backing up existing configuration files
    --skip-deps         Skip checking and installing dependencies
    --apply-layout      Automatically apply the Plasma 6 dual panel layout via DBus
    --no-color          Disable colored output (honors NO_COLOR env var)
    -y, --yes           Non-interactive mode; answer yes to all confirmation prompts
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            print_help
            exit 0
            ;;
        -n|--dry-run)
            DRY_RUN=true
            shift
            ;;
        --no-color)
            disable_colors
            shift
            ;;
        --no-backup)
            SKIP_BACKUP=true
            shift
            ;;
        --skip-deps)
            SKIP_DEPS=true
            shift
            ;;
        --apply-layout)
            APPLY_LAYOUT=true
            shift
            ;;
        -y|--yes)
            AUTO_YES=true
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            print_help
            exit 1
            ;;
    esac
done

ask_confirm() {
    local prompt="$1"
    if [[ "$AUTO_YES" == true ]]; then
        return 0
    fi
    if [[ ! -t 0 ]]; then
        return 0
    fi
    read -rp "$(printf "${YELLOW}%s [y/N]: ${NC}" "$prompt")" response
    case "$response" in
        [yY][eE][sS]|[yY]) return 0 ;;
        *) return 1 ;;
    esac
}

# -----------------------------------------------------------------------------
# System Verification
# -----------------------------------------------------------------------------
check_system() {
    log_step "Verifying Host System Compatibility"

    if [[ ! -f /etc/arch-release && ! -f /etc/cachyos-release ]]; then
        log_warn "This installer is tailored for Arch Linux and CachyOS."
        if ! ask_confirm "Proceed anyway?"; then
            log_error "Installation aborted by user."
            exit 1
        fi
    else
        log_success "Arch Linux / CachyOS base system detected."
    fi

    if [[ "${XDG_CURRENT_DESKTOP:-}" != *"KDE"* && "${DESKTOP_SESSION:-}" != *"plasma"* ]]; then
        log_warn "Current desktop does not appear to be KDE Plasma."
    else
        log_success "KDE Plasma session active."
    fi
}

detect_aur_helper() {
    if command -v yay >/dev/null 2>&1; then
        AUR_HELPER="yay"
    elif command -v paru >/dev/null 2>&1; then
        AUR_HELPER="paru"
    else
        AUR_HELPER=""
    fi
}

# -----------------------------------------------------------------------------
# Dependency Installation
# -----------------------------------------------------------------------------
install_dependencies() {
    if [[ "$SKIP_DEPS" == true ]]; then
        log_info "Skipping dependency installation as requested."
        return 0
    fi

    log_step "Resolving Core Dependencies"
    detect_aur_helper

    local PACMAN_DEPS=(
        "base-devel"
        "git"
        "cmake"
        "extra-cmake-modules"
        "stow"
        "kvantum"
        "ttf-jetbrains-mono-nerd"
        "cool-retro-term"
        "cava"
        "ffmpeg"
        "starship"
        "fastfetch"
        "zsh"
    )

    local MISSING_PACMAN=()
    for pkg in "${PACMAN_DEPS[@]}"; do
        if ! pacman -Qi "$pkg" >/dev/null 2>&1; then
            MISSING_PACMAN+=("$pkg")
        fi
    done

    if [[ ${#MISSING_PACMAN[@]} -gt 0 ]]; then
        log_info "Installing missing pacman packages: ${MISSING_PACMAN[*]}"
        if [[ "$DRY_RUN" == true ]]; then
            log_info "[DRY-RUN] Would run: sudo pacman -S --needed --noconfirm ${MISSING_PACMAN[*]}"
        else
            sudo pacman -S --needed --noconfirm "${MISSING_PACMAN[@]}"
        fi
    else
        log_success "All official pacman packages installed."
    fi

    log_step "Resolving AUR Enhancements & Plasma 6 Extensions"
    local AUR_DEPS=(
        "yamis-icon-theme-git"
        "bibata-cursor-git"
        "plasma6-applets-panel-colorizer"
        "plasma6-applets-kurve-git"
        "plasma6-applets-kde-control-station"
        "plasma6-wallpapers-smart-video-wallpaper-reborn"
        "kwin-effects-forceblur-git"
    )

    local MISSING_AUR=()
    for apkg in "${AUR_DEPS[@]}"; do
        if ! pacman -Qi "$apkg" >/dev/null 2>&1; then
            MISSING_AUR+=("$apkg")
        fi
    done

    if [[ ${#MISSING_AUR[@]} -gt 0 ]]; then
        log_info "Missing AUR packages: ${MISSING_AUR[*]}"
        if [[ "$DRY_RUN" == true ]]; then
            log_info "[DRY-RUN] Would install via AUR helper: ${MISSING_AUR[*]}"
        else
            if [[ -n "$AUR_HELPER" ]]; then
                "$AUR_HELPER" -S --needed --noconfirm "${MISSING_AUR[@]}" || log_warn "Some AUR packages failed to install automatically."
            else
                log_warn "No AUR helper found. Please install: ${MISSING_AUR[*]}"
            fi
        fi
    else
        log_success "All recommended AUR enhancements are installed."
    fi
}

# -----------------------------------------------------------------------------
# Configuration Backup
# -----------------------------------------------------------------------------
backup_configs() {
    if [[ "$SKIP_BACKUP" == true ]]; then
        log_info "Skipping backup as requested."
        return 0
    fi

    log_step "Backing Up Existing Configurations to: $BACKUP_DIR"
    local TARGETS=(
        "${HOME}/.config/kdeglobals"
        "${HOME}/.config/kwinrc"
        "${HOME}/.config/plasmashellrc"
        "${HOME}/.config/plasma-org.kde.plasma.desktop-appletsrc"
        "${HOME}/.config/panel-colorizer"
        "${HOME}/.config/Kvantum"
        "${HOME}/.config/fastfetch"
        "${HOME}/.config/starship.toml"
    )

    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would backup ${#TARGETS[@]} configuration targets."
        return 0
    fi

    mkdir -p "$BACKUP_DIR"
    for target in "${TARGETS[@]}"; do
        if [[ -e "$target" ]]; then
            local rel_path="${target#${HOME}/}"
            local dest="${BACKUP_DIR}/${rel_path}"
            mkdir -p "$(dirname "$dest")"
            cp -rL "$target" "$dest"
            log_info "Backed up: $rel_path"
        fi
    done
    log_success "Backup complete."
}

# -----------------------------------------------------------------------------
# Deploy Themes, Plasmoids, Presets & Wallpapers
# -----------------------------------------------------------------------------
deploy_components() {
    log_step "Deploying Monochrome Components & Plasmoids"

    local PLASMOIDS_DIR="${HOME}/.local/share/plasma/plasmoids"
    local COLOR_DIR="${HOME}/.local/share/color-schemes"
    local THEME_DIR="${HOME}/.local/share/plasma/desktoptheme"
    local AURORAE_DIR="${HOME}/.local/share/aurorae/themes"
    local PRESETS_DIR="${HOME}/.config/panel-colorizer/presets"
    local WALLPAPER_DIR="${HOME}/.local/share/wallpapers"

    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would deploy plasmoids, themes, presets, and wallpapers."
        return 0
    fi

    mkdir -p "$PLASMOIDS_DIR" "$COLOR_DIR" "$THEME_DIR" "$AURORAE_DIR" "$PRESETS_DIR" "$WALLPAPER_DIR"

    # 1. Bundled Plasmoids
    log_info "Installing bundled plasmoids (YoRHa HUD, CatWalk Enhanced, Thermal Monitor, ClearClock)..."
    for plasmoid in "${SCRIPT_DIR}/plasma/.local/share/plasma/plasmoids/"*; do
        if [[ -d "$plasmoid" ]]; then
            local p_name="$(basename "$plasmoid")"
            rm -rf "${PLASMOIDS_DIR:?}/${p_name}"
            cp -r "$plasmoid" "${PLASMOIDS_DIR}/"
            log_success "Installed plasmoid: $p_name"
        fi
    done

    # 2. Monochrome Color Scheme & Desktop Theme
    log_info "Installing Monochrome colors and Plasma style..."
    cp "${SCRIPT_DIR}/plasma/.local/share/color-schemes/"* "$COLOR_DIR/" 2>/dev/null || true
    cp -r "${SCRIPT_DIR}/plasma/.local/share/plasma/desktoptheme/"* "$THEME_DIR/" 2>/dev/null || true
    cp -r "${SCRIPT_DIR}/plasma/.local/share/aurorae/themes/"* "$AURORAE_DIR/" 2>/dev/null || true

    # 3. Panel Colorizer Presets
    log_info "Installing Panel Colorizer presets ('Main Setup' & 'Main Blur')..."
    cp -r "${SCRIPT_DIR}/plasma/.config/panel-colorizer/presets/"* "$PRESETS_DIR/"

    # 4. Wallpaper
    log_info "Installing static wallpaper frame to ${WALLPAPER_DIR}/digital-gaze.png..."
    cp "${SCRIPT_DIR}/assets/wallpapers/digital-gaze.png" "${WALLPAPER_DIR}/"

    # 5. Zen Browser userChrome.css helper
    local ZEN_DIR="${HOME}/.zen"
    if [[ -d "$ZEN_DIR" ]]; then
        for profile in "$ZEN_DIR"/*; do
            if [[ -d "$profile" && -f "$profile/prefs.js" ]]; then
                mkdir -p "$profile/chrome"
                cp "${SCRIPT_DIR}/zen-browser/userChrome.css" "$profile/chrome/"
                log_success "Deployed userChrome.css to Zen profile: $(basename "$profile")"
            fi
        done
    fi
}

# -----------------------------------------------------------------------------
# Dotfiles Stowing & Configuration Linking
# -----------------------------------------------------------------------------
stow_configurations() {
    log_step "Deploying Configuration Packages via GNU Stow"

    local PACKAGES=(
        "fastfetch"
        "starship"
        "kvantum"
        "zsh"
    )

    if command -v stow >/dev/null 2>&1; then
        for pkg in "${PACKAGES[@]}"; do
            if [[ -d "${SCRIPT_DIR}/${pkg}" ]]; then
                log_info "Stowing package: $pkg"
                if [[ "$DRY_RUN" == true ]]; then
                    log_info "[DRY-RUN] Would run: stow -v -R -t \"$HOME\" \"$pkg\""
                else
                    stow -R -t "$HOME" -d "$SCRIPT_DIR" "$pkg" || log_warn "Stow had non-fatal warnings for $pkg"
                fi
            fi
        done
    else
        log_warn "GNU Stow not available; falling back to manual copy."
        for pkg in "${PACKAGES[@]}"; do
            if [[ -d "${SCRIPT_DIR}/${pkg}" ]]; then
                cp -r "${SCRIPT_DIR}/${pkg}/." "$HOME/"
            fi
        done
    fi
}

# -----------------------------------------------------------------------------
# Apply Active KDE System Configurations
# -----------------------------------------------------------------------------
apply_kde_settings() {
    log_step "Applying KDE Plasma Look-and-Feel Settings"

    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would invoke kwriteconfig6 for colors, icons, fonts, and kwin."
        return 0
    fi

    if command -v kwriteconfig6 >/dev/null 2>&1; then
        # Color Scheme & Icons
        kwriteconfig6 --file kdeglobals --group General --key ColorScheme "Monochrome"
        kwriteconfig6 --file kdeglobals --group Icons --key Theme "YAMIS"
        kwriteconfig6 --file kdeglobals --group Mouse --key cursorTheme "Bibata-Modern-Ice"

        # JetBrainsMono Nerd Font
        kwriteconfig6 --file kdeglobals --group General --key font "JetBrainsMono Nerd Font,10,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key fixed "JetBrainsMono Nerd Font,10,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key menuFont "JetBrainsMono Nerd Font,10,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key smallestReadableFont "JetBrainsMono Nerd Font,8,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key toolBarFont "JetBrainsMono Nerd Font,10,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key windowTitleFont "JetBrainsMono Nerd Font,10,-1,5,70,0,0,0,0,0,Bold"

        # KWin Force Blur for Zen Browser
        kwriteconfig6 --file kwinrc --group Plugins --key forceblurEnabled true
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key MatchingClasses "zen"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key BlurStrength "4"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key NoiseStrength "5"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key Brightness "25"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key Saturation "0"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key Contrast "105"

        # Terminal Preference
        kwriteconfig6 --file kdeglobals --group General --key TerminalApplication "cool-retro-term"
        kwriteconfig6 --file kdeglobals --group General --key TerminalService "cool-retro-term.desktop"

        log_success "KDE system settings registered."
    fi
}

# -----------------------------------------------------------------------------
# Apply Dual Panel Layout (Optional / Flagged)
# -----------------------------------------------------------------------------
apply_panel_layout() {
    if [[ "$APPLY_LAYOUT" != true ]]; then
        log_info "Skipping automatic panel reset. Use --apply-layout to reset and configure panels."
        return 0
    fi

    log_step "Applying Plasma 6 Dual Panel Layout via DBus"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would evaluate layout.js via qdbus6."
        return 0
    fi

    if command -v qdbus6 >/dev/null 2>&1; then
        log_info "Evaluating layout.js..."
        qdbus6 org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "$(< "${SCRIPT_DIR}/plasma/layout.js")" || log_warn "Panel layout script returned a non-zero exit code."
        log_success "Panel layout evaluated."
    else
        log_warn "qdbus6 command not found; could not evaluate layout.js automatically."
    fi
}

# -----------------------------------------------------------------------------
# Main Entry Point
# -----------------------------------------------------------------------------
main() {
    log_info "Starting deployment of plasma-mono-rice..."

    check_system
    install_dependencies
    backup_configs
    deploy_components
    stow_configurations
    apply_kde_settings
    apply_panel_layout

    log_step "Installation Completed Successfully!"
    log_info "Next Steps:"
    log_info " 1. Open cool-retro-term -> Settings -> Profiles -> Import: ${SCRIPT_DIR}/cool-retro-term/cool-retro-term-monochrome.json"
    log_info " 2. (Optional) For video wallpaper: Download 4K video using assets/wallpapers/wallpapers.md"
    log_info " 3. Restart Plasma or log out & back in: kquitapp6 plasmashell && kstart plasmashell"
}

main "$@"
