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
# Inspired by: agridyne/dotfiles-dt
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
DEPS_ONLY=false
SYMLINKS_ONLY=false
APPLY_LAYOUT=true
INSTALL_SDDM=true
INSTALL_PLYMOUTH=true
NO_RESTART=false
AUTO_YES=false
AUR_HELPER=""

print_help() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Automated installer and dotfiles bootstrap script for Plasma Monochrome Rice.

Options:
    -h, --help          Show this help message and exit
    -s, --symlinks-only Apply symlinks, configs, plasmoids and themes only (skip pkg manager)
    -d, --deps-only     Install dependencies only (skip applying dotfiles/layout)
    -l, --apply-layout  Evaluate Plasma 6 dual panel layout & widgets via DBus (Default: enabled)
        --no-layout     Skip evaluating panel layout and widgets
        --sddm          Install Monochrome SDDM login theme to /usr/share/sddm/themes
        --no-sddm       Skip installing SDDM login theme
        --plymouth      Install dotLock Plymouth boot splash theme
        --no-plymouth   Skip installing Plymouth boot splash theme
        --no-restart    Skip automatically restarting plasmashell at the end
    -n, --dry-run       Simulate installation without modifying any files
        --no-backup     Skip backing up existing configuration files
        --no-color      Disable colored output (honors NO_COLOR env var)
    -y, --yes           Non-interactive mode (answer yes to all confirmation prompts)
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            print_help
            exit 0
            ;;
        -s|--symlinks-only)
            SYMLINKS_ONLY=true
            SKIP_DEPS=true
            INSTALL_SDDM=false
            INSTALL_PLYMOUTH=false
            shift
            ;;
        -d|--deps-only)
            DEPS_ONLY=true
            shift
            ;;
        -l|--apply-layout)
            APPLY_LAYOUT=true
            shift
            ;;
        --no-layout|--skip-layout)
            APPLY_LAYOUT=false
            shift
            ;;
        --sddm)
            INSTALL_SDDM=true
            shift
            ;;
        --no-sddm)
            INSTALL_SDDM=false
            shift
            ;;
        --plymouth)
            INSTALL_PLYMOUTH=true
            shift
            ;;
        --no-plymouth)
            INSTALL_PLYMOUTH=false
            shift
            ;;
        --no-restart)
            NO_RESTART=true
            shift
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
        log_warn "Current desktop is not running KDE Plasma."
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

    log_step "Resolving Core Official Pacman Dependencies"
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
        "klassy"
        "zen-browser-bin"
    )

    local MISSING_AUR=()
    for apkg in "${AUR_DEPS[@]}"; do
        if ! pacman -Qi "$apkg" >/dev/null 2>&1 && ! pacman -Q "${apkg%-git}" >/dev/null 2>&1 && ! pacman -Q "${apkg%-bin}" >/dev/null 2>&1 && ! pacman -Q "${apkg}-bin" >/dev/null 2>&1; then
            MISSING_AUR+=("$apkg")
        fi
    done

    if [[ ${#MISSING_AUR[@]} -gt 0 ]]; then
        log_info "Missing AUR packages: ${MISSING_AUR[*]}"
        if [[ "$DRY_RUN" == true ]]; then
            log_info "[DRY-RUN] Would install via AUR helper: ${MISSING_AUR[*]}"
        else
            if [[ -n "$AUR_HELPER" ]]; then
                "$AUR_HELPER" -S --needed --noconfirm "${MISSING_AUR[@]}" || log_warn "Some AUR packages may need manual confirmation."
            else
                log_warn "No AUR helper found (yay/paru). Please install: ${MISSING_AUR[*]}"
            fi
        fi
    else
        log_success "All AUR enhancements & Plasma extensions are installed."
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
        "${HOME}/.config/kscreenlockerrc"
        "${HOME}/.config/panel-colorizer"
        "${HOME}/.config/Kvantum"
        "${HOME}/.config/fastfetch"
        "${HOME}/.config/starship.toml"
        "${HOME}/.zshrc"
    )

    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would backup ${#TARGETS[@]} configuration targets."
        return 0
    fi

    mkdir -p "$BACKUP_DIR"
    for target in "${TARGETS[@]}"; do
        if [[ -e "$target" && ! -L "$target" ]]; then
            local rel_path="${target#${HOME}/}"
            local dest="${BACKUP_DIR}/${rel_path}"
            mkdir -p "$(dirname "$dest")"
            cp -r "$target" "$dest"
            log_info "Backed up: $rel_path"
        fi
    done
    log_success "Backup complete."
}

# -----------------------------------------------------------------------------
# Clean Broken Symlinks
# -----------------------------------------------------------------------------
clean_broken_symlinks() {
    log_step "Cleaning Legacy / Broken Symlinks"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would check and remove broken symlinks in ~/.config and ~/.local/share"
        return 0
    fi

    find "${HOME}/.config" "${HOME}/.local/share" "${HOME}" -maxdepth 3 -xtype l 2>/dev/null | while read -r broken_link; do
        if [[ "$broken_link" != *"SingletonLock"* && "$broken_link" != *"SingletonCookie"* && "$broken_link" != *".steampath"* ]]; then
            log_info "Removing dangling symlink: $broken_link"
            rm -f "$broken_link"
        fi
    done
    log_success "Broken symlinks cleaned."
}

# -----------------------------------------------------------------------------
# Deploy Themes, Plasmoids, Presets & Wallpapers
# -----------------------------------------------------------------------------
deploy_components() {
    log_step "Deploying Rice Components, Plasmoids & Themes"

    local PLASMOIDS_DIR="${HOME}/.local/share/plasma/plasmoids"
    local COLOR_DIR="${HOME}/.local/share/color-schemes"
    local THEME_DIR="${HOME}/.local/share/plasma/desktoptheme"
    local AURORAE_DIR="${HOME}/.local/share/aurorae/themes"
    local LOOK_FEEL_DIR="${HOME}/.local/share/plasma/look-and-feel"
    local PRESETS_DIR="${HOME}/.config/panel-colorizer/presets"
    local WALLPAPER_DIR="${HOME}/.local/share/wallpapers"
    local ICONS_DIR="${HOME}/.local/share/icons"

    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would deploy plasmoids, themes, presets, and wallpapers."
        return 0
    fi

    for d in "$PLASMOIDS_DIR" "$COLOR_DIR" "$THEME_DIR" "$AURORAE_DIR" "$LOOK_FEEL_DIR" "$PRESETS_DIR" "$WALLPAPER_DIR" "$ICONS_DIR"; do
        if [[ -L "$d" ]]; then rm -f "$d"; fi
        mkdir -p "$d"
    done

    # 1. Bundled Plasmoids (Direct Symlinks to Repo)
    log_info "Symlinking Plasmoids (YoRHa HUD, CatWalk Enhanced, Thermal Monitor, ClearClock, Kurve, Control Station)..."
    if [[ -d "${SCRIPT_DIR}/plasma/.local/share/plasma/plasmoids" ]]; then
        for plasmoid in "${SCRIPT_DIR}/plasma/.local/share/plasma/plasmoids/"*; do
            if [[ -d "$plasmoid" ]]; then
                local p_name="$(basename "$plasmoid")"
                rm -rf "${PLASMOIDS_DIR:?}/${p_name}"
                ln -sfn "$plasmoid" "${PLASMOIDS_DIR}/${p_name}"
                log_success "Linked plasmoid: $p_name -> repo"
            fi
        done
    fi

    # 2. Monochrome Color Scheme & Desktop Theme (Symlinks)
    log_info "Symlinking Monochrome colors and Plasma styles..."
    if [[ -d "${SCRIPT_DIR}/plasma/.local/share/color-schemes" ]]; then
        for cs in "${SCRIPT_DIR}/plasma/.local/share/color-schemes/"*; do
            if [[ -f "$cs" ]]; then
                ln -sfn "$cs" "${COLOR_DIR}/$(basename "$cs")"
            fi
        done
    fi
    if [[ -d "${SCRIPT_DIR}/plasma/.local/share/plasma/desktoptheme" ]]; then
        for dt in "${SCRIPT_DIR}/plasma/.local/share/plasma/desktoptheme/"*; do
            if [[ -d "$dt" ]]; then
                rm -rf "${THEME_DIR:?}/$(basename "$dt")"
                ln -sfn "$dt" "${THEME_DIR}/$(basename "$dt")"
            fi
        done
    fi
    if [[ -d "${SCRIPT_DIR}/plasma/.local/share/aurorae/themes" ]]; then
        for at in "${SCRIPT_DIR}/plasma/.local/share/aurorae/themes/"*; do
            if [[ -d "$at" ]]; then
                rm -rf "${AURORAE_DIR:?}/$(basename "$at")"
                ln -sfn "$at" "${AURORAE_DIR}/$(basename "$at")"
            fi
        done
    fi

    # 3. Splash Screen (Kuro the cat)
    if [[ ! -d "${LOOK_FEEL_DIR}/a2n.kuro" ]]; then
        log_info "Downloading Kuro the cat splash screen..."
        rm -rf /tmp/kuro_splash
        git clone --depth 1 https://github.com/bouteillerAlan/kuro.git /tmp/kuro_splash 2>/dev/null && \
            cp -r /tmp/kuro_splash/a2n.kuro* "$LOOK_FEEL_DIR/" 2>/dev/null || log_warn "Could not download Kuro splash automatically."
        rm -rf /tmp/kuro_splash
    fi

    # 4. Icon Symlink Fallback (YAMIS)
    if [[ -d /usr/share/icons/yet-another-monochrome-icon-set ]]; then
        ln -sfn /usr/share/icons/yet-another-monochrome-icon-set "${ICONS_DIR}/YAMIS"
        ln -sfn /usr/share/icons/yet-another-monochrome-icon-set "${ICONS_DIR}/yet-another-monochrome-icon-set"
    fi

    # 5. Panel Colorizer Presets (Symlinked to Repo)
    log_info "Symlinking Panel Colorizer presets ('Main Setup' & 'Main Blur')..."
    if [[ -d "${SCRIPT_DIR}/plasma/.config/panel-colorizer/presets" ]]; then
        for preset in "${SCRIPT_DIR}/plasma/.config/panel-colorizer/presets/"*; do
            if [[ -d "$preset" ]]; then
                local pr_name="$(basename "$preset")"
                rm -rf "${PRESETS_DIR:?}/${pr_name}"
                ln -sfn "$preset" "${PRESETS_DIR}/${pr_name}"
            fi
        done
    fi

    # 6. Wallpapers (4K PNG frames & Live MP4 videos - Symlinked to Repo)
    if [[ -d "${SCRIPT_DIR}/assets/wallpapers" ]]; then
        for wp in "${SCRIPT_DIR}/assets/wallpapers/"*.{png,mp4}; do
            if [[ -f "$wp" ]]; then
                ln -sfn "$wp" "${WALLPAPER_DIR}/$(basename "$wp")"
            fi
        done
    fi

    # 7. Cool-Retro-Term Profile & Automatic SQLite Injection
    mkdir -p "${HOME}/.config/cool-retro-term"
    if [[ -f "${SCRIPT_DIR}/cool-retro-term/cool-retro-term-monochrome.json" ]]; then
        ln -sfn "${SCRIPT_DIR}/cool-retro-term/cool-retro-term-monochrome.json" "${HOME}/.config/cool-retro-term/cool-retro-term-monochrome.json"
        
        if command -v python3 >/dev/null 2>&1; then
            python3 -c '
import sqlite3, json, hashlib, os
try:
    db_dir = os.path.expanduser("~/.local/share/cool-retro-term/cool-retro-term/QML/OfflineStorage/Databases")
    os.makedirs(db_dir, exist_ok=True)
    db_name = "coolretroterm2"
    db_hash = hashlib.md5(db_name.encode("utf-8")).hexdigest()
    ini_path = os.path.join(db_dir, f"{db_hash}.ini")
    if not os.path.exists(ini_path):
        with open(ini_path, "w") as f:
            f.write(f"[General]\nDescription=StorageDatabase\nDriver=QSQLITE\nEstimatedSize=100000\nName={db_name}\nVersion=1.0\n")
    sqlite_path = os.path.join(db_dir, f"{db_hash}.sqlite")
    conn = sqlite3.connect(sqlite_path)
    cur = conn.cursor()
    cur.execute("CREATE TABLE IF NOT EXISTS settings (setting TEXT UNIQUE, value TEXT)")
    with open("'"${SCRIPT_DIR}"'/cool-retro-term/cool-retro-term-monochrome.json") as f:
        mono = f.read()
    cur.execute("INSERT OR REPLACE INTO settings (setting, value) VALUES (?, ?)", ("_CURRENT_PROFILE", mono))
    cur.execute("INSERT OR REPLACE INTO settings (setting, value) VALUES (?, ?)", ("_CUSTOM_PROFILES", json.dumps([{"text": "Monochrome", "obj_string": mono, "builtin": False}])))
    conn.commit()
    conn.close()
except Exception:
    pass
'
            log_success "Configured cool-retro-term default profile to Monochrome."
        fi
    fi

    # 8. Zen Browser userChrome.css Auto-Deployment (Symlinked)
    local ZEN_DIR="${HOME}/.zen"
    if [[ -d "$ZEN_DIR" ]]; then
        for profile in "$ZEN_DIR"/*; do
            if [[ -d "$profile" ]]; then
                mkdir -p "$profile/chrome"
                ln -sfn "${SCRIPT_DIR}/zen-browser/userChrome.css" "$profile/chrome/userChrome.css"
                log_success "Linked userChrome.css to Zen profile: $(basename "$profile")"
            fi
        done
    fi
}

# -----------------------------------------------------------------------------
# Apply Dotfile Symlinks (Modular GNU Stow / ln -sfn fallback)
# -----------------------------------------------------------------------------
apply_symlinks() {
    log_step "Deploying Modular Configuration Symlinks"

    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would create symlinks for plasma, starship, fastfetch, kvantum, and zsh."
        return 0
    fi

    mkdir -p "${HOME}/.config/klassy" "${HOME}/.config/fastfetch" "${HOME}/.config/zsh" "${HOME}/.config/Kvantum" "${HOME}/.config/plasma-workspace/env"

    # Core Plasma configs
    ln -sfn "${SCRIPT_DIR}/plasma/.config/kdeglobals" "${HOME}/.config/kdeglobals"
    ln -sfn "${SCRIPT_DIR}/plasma/.config/kglobalshortcutsrc" "${HOME}/.config/kglobalshortcutsrc"
    ln -sfn "${SCRIPT_DIR}/plasma/.config/kwinrc" "${HOME}/.config/kwinrc"
    # NOTE: plasmashellrc and plasma-org.kde.plasma.desktop-appletsrc are
    # deliberately NOT symlinked here. Plasmashell rewrites them constantly at
    # runtime, and a symlink would let those live writes (or apply_panel_layout's
    # hydrated activityId/$HOME) leak straight back into the tracked repo copy.
    # apply_panel_layout() deploys them as plain, one-way file copies instead;
    # harvest.sh is the only path that intentionally pulls live state back in.
    if [[ -f "${SCRIPT_DIR}/plasma/.config/kscreenlockerrc" ]]; then
        ln -sfn "${SCRIPT_DIR}/plasma/.config/kscreenlockerrc" "${HOME}/.config/kscreenlockerrc"
    fi
    if [[ -f "${SCRIPT_DIR}/plasma/.config/klassy/klassyrc" ]]; then
        ln -sfn "${SCRIPT_DIR}/plasma/.config/klassy/klassyrc" "${HOME}/.config/klassy/klassyrc"
    fi
    if [[ -f "${SCRIPT_DIR}/plasma/.config/plasma-workspace/env/rice-env.sh" ]]; then
        ln -sfn "${SCRIPT_DIR}/plasma/.config/plasma-workspace/env/rice-env.sh" "${HOME}/.config/plasma-workspace/env/rice-env.sh"
    fi
    # plasma/layout.js is kept only as historical reference (see its header
    # comment); the panel/desktop layout is deployed by apply_panel_layout().

    # Fastfetch
    if [[ -f "${SCRIPT_DIR}/fastfetch/.config/fastfetch/config.jsonc" ]]; then
        ln -sfn "${SCRIPT_DIR}/fastfetch/.config/fastfetch/config.jsonc" "${HOME}/.config/fastfetch/config.jsonc"
    fi

    # Starship
    if [[ -f "${SCRIPT_DIR}/starship/.config/starship.toml" ]]; then
        ln -sfn "${SCRIPT_DIR}/starship/.config/starship.toml" "${HOME}/.config/starship.toml"
    fi

    # CAVA Audio Visualizer
    mkdir -p "${HOME}/.config/cava"
    if [[ -f "${SCRIPT_DIR}/cava/.config/cava/config" ]]; then
        ln -sfn "${SCRIPT_DIR}/cava/.config/cava/config" "${HOME}/.config/cava/config"
    fi

    # Zsh
    if [[ -f "${SCRIPT_DIR}/zsh/.zshrc" ]]; then
        ln -sfn "${SCRIPT_DIR}/zsh/.zshrc" "${HOME}/.zshrc"
    fi
    if [[ -f "${SCRIPT_DIR}/zsh/.config/zsh/aliases.zsh" ]]; then
        ln -sfn "${SCRIPT_DIR}/zsh/.config/zsh/aliases.zsh" "${HOME}/.config/zsh/aliases.zsh"
    fi

    # Kvantum (Symlinks to Repo)
    if [[ -d "${SCRIPT_DIR}/kvantum/.config/Kvantum" ]]; then
        for kitem in "${SCRIPT_DIR}/kvantum/.config/Kvantum/"*; do
            local kname="$(basename "$kitem")"
            rm -rf "${HOME}/.config/Kvantum/${kname}"
            ln -sfn "$kitem" "${HOME}/.config/Kvantum/${kname}"
        done
    fi

    log_success "All configuration symlinks applied successfully."
}

# -----------------------------------------------------------------------------
# Apply Active KDE System Configurations
# -----------------------------------------------------------------------------
apply_kde_settings() {
    log_step "Applying KDE Plasma Look-and-Feel Settings"

    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would apply color scheme, icons, fonts, cursor, and kwin force blur."
        return 0
    fi

    # Apply Colors & Cursor
    if command -v plasma-apply-colorscheme >/dev/null 2>&1; then
        plasma-apply-colorscheme Monochrome 2>/dev/null || log_warn "Could not apply color scheme via plasma-apply-colorscheme"
    fi
    if command -v plasma-apply-cursortheme >/dev/null 2>&1; then
        plasma-apply-cursortheme Bibata-Modern-Ice 2>/dev/null || log_warn "Could not apply cursor via plasma-apply-cursortheme"
    fi

    if command -v kwriteconfig6 >/dev/null 2>&1; then
        # Color Scheme & Icons
        kwriteconfig6 --file kdeglobals --group General --key ColorScheme "Monochrome"
        kwriteconfig6 --file kdeglobals --group General --key Name "Monochrome"
        kwriteconfig6 --file kdeglobals --group Icons --key Theme "yet-another-monochrome-icon-set"
        kwriteconfig6 --file kdeglobals --group Mouse --key cursorTheme "Bibata-Modern-Ice"
        kwriteconfig6 --file ksplashrc --group KSplash --key Theme "a2n.kuro"

        # JetBrainsMono Nerd Font
        kwriteconfig6 --file kdeglobals --group General --key font "JetBrainsMono Nerd Font,10,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key fixed "JetBrainsMono Nerd Font,10,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key menuFont "JetBrainsMono Nerd Font,10,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key smallestReadableFont "JetBrainsMono Nerd Font,8,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key toolBarFont "JetBrainsMono Nerd Font,10,-1,5,50,0,0,0,0,0"
        kwriteconfig6 --file kdeglobals --group General --key windowTitleFont "JetBrainsMono Nerd Font,10,-1,5,70,0,0,0,0,0,Bold"

        # Terminal Preference (Cool-Retro-Term as Default Terminal)
        kwriteconfig6 --file kdeglobals --group General --key TerminalApplication "cool-retro-term"
        kwriteconfig6 --file kdeglobals --group General --key TerminalService "cool-retro-term.desktop"

        # Terminal Keybinding (KDE's default terminal launch shortcut: Ctrl+Alt+T)
        kwriteconfig6 --file kglobalshortcutsrc --group services --group "cool-retro-term.desktop" --key _launch "Ctrl+Alt+T,Ctrl+Alt+T,Cool Retro Term"
        kwriteconfig6 --file kglobalshortcutsrc --group kwin --key "Edit Tiles" "none,none,Toggle Tiles Editor"

        # KWin Force Blur for Zen Browser
        kwriteconfig6 --file kwinrc --group Plugins --key forceblurEnabled true
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key MatchingClasses "zen"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key BlurStrength "4"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key NoiseStrength "5"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key Brightness "25"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key Saturation "0"
        kwriteconfig6 --file kwinrc --group Effect-forceblur --key Contrast "105"

        # Lockscreen Video Wallpaper
        local video_urls_json='[{"filename":"file://'"${HOME}"'/.local/share/wallpapers/digital-gaze.mp4","enabled":true,"duration":0,"customDuration":0,"playbackRate":0.0,"alternativePlaybackRate":0.0,"loop":false,"dayNightPhase":4}]'
        kwriteconfig6 --file kscreenlockerrc --group Greeter --key WallpaperPlugin "luisbocanegra.smart.video.wallpaper.reborn"
        kwriteconfig6 --file kscreenlockerrc --group Greeter --group Wallpaper --group "luisbocanegra.smart.video.wallpaper.reborn" --group General --key VideoUrls "$video_urls_json"
        kwriteconfig6 --file kscreenlockerrc --group Greeter --group Wallpaper --group "luisbocanegra.smart.video.wallpaper.reborn" --group General --key LastVideo "file://${HOME}/.local/share/wallpapers/digital-gaze.mp4"
        kwriteconfig6 --file kscreenlockerrc --group Greeter --group Wallpaper --group "luisbocanegra.smart.video.wallpaper.reborn" --group General --key FillMode 2
        kwriteconfig6 --file kscreenlockerrc --group Greeter --group Wallpaper --group "luisbocanegra.smart.video.wallpaper.reborn" --group General --key MuteMode 5

        # Reconfigure KWin
        if command -v qdbus6 >/dev/null 2>&1; then
            qdbus6 org.kde.KWin /KWin reconfigure 2>/dev/null || true
        fi

        log_success "KDE system settings registered and active."
    fi
}

# -----------------------------------------------------------------------------
# SDDM Monochrome Login Theme Installation
# -----------------------------------------------------------------------------
install_sddm_theme() {
    if [[ "$INSTALL_SDDM" != true ]]; then
        return 0
    fi

    log_step "Installing Monochrome SDDM Login Theme"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would install SDDM theme to /usr/share/sddm/themes/monochrome"
        return 0
    fi

    if [[ -d "${SCRIPT_DIR}/assets/sddm-theme" ]]; then
        if sudo -n true 2>/dev/null || ask_confirm "Install SDDM Monochrome Theme (requires sudo)?"; then
            sudo mkdir -p /usr/share/sddm/themes/monochrome /etc/sddm.conf.d
            sudo cp -rf "${SCRIPT_DIR}/assets/sddm-theme/"* /usr/share/sddm/themes/monochrome/
            sudo kwriteconfig6 --file /etc/sddm.conf --group Theme --key Current monochrome 2>/dev/null || true
            sudo kwriteconfig6 --file /etc/sddm.conf.d/theme.conf --group Theme --key Current monochrome 2>/dev/null || true
            log_success "SDDM Monochrome theme installed and set as default."
        else
            log_warn "Skipped SDDM installation (sudo access declined)."
        fi
    else
        log_warn "SDDM theme directory not found at assets/sddm-theme"
    fi
}

# -----------------------------------------------------------------------------
# Plymouth dotLock Boot Splash Theme Installation
# -----------------------------------------------------------------------------
install_plymouth_theme() {
    if [[ "$INSTALL_PLYMOUTH" != true ]]; then
        return 0
    fi

    log_step "Installing dotLock Plymouth Boot Splash Theme"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would install dotLock Plymouth theme to /usr/share/plymouth/themes/dotLock"
        return 0
    fi

    if [[ -d "${SCRIPT_DIR}/assets/plymouth-theme/dotLock" ]]; then
        if sudo -n true 2>/dev/null || ask_confirm "Install dotLock Plymouth Boot Splash (requires sudo)?"; then
            sudo mkdir -p /usr/share/plymouth/themes/dotLock /etc/plymouth
            sudo cp -rf "${SCRIPT_DIR}/assets/plymouth-theme/dotLock/"* /usr/share/plymouth/themes/dotLock/
            
            # Set default theme via plymouth utility if present
            if command -v plymouth-set-default-theme >/dev/null 2>&1; then
                sudo plymouth-set-default-theme dotLock 2>/dev/null || true
            fi
            
            # Configure plymouthd.conf
            sudo kwriteconfig6 --file /etc/plymouth/plymouthd.conf --group Daemon --key Theme dotLock 2>/dev/null || true
            log_success "dotLock Plymouth theme installed and configured."
        else
            log_warn "Skipped Plymouth installation (sudo access declined)."
        fi
    else
        log_warn "Plymouth theme directory not found at assets/plymouth-theme/dotLock"
    fi
}

# -----------------------------------------------------------------------------
# Deploy Plasma Layout Configuration
# -----------------------------------------------------------------------------
apply_panel_layout() {
    log_step "Deploying Plasma Layout & Containment Configurations"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would stop plasmashell, deploy layout configs, and restart plasmashell."
        return 0
    fi

    local APPLETSRC_SRC="$SCRIPT_DIR/plasma/.config/plasma-org.kde.plasma.desktop-appletsrc"
    local PLASMASHELLRC_SRC="$SCRIPT_DIR/plasma/.config/plasmashellrc"

    log_info "Stopping plasmashell..."
    systemctl --user stop plasma-plasmashell 2>/dev/null || kquitapp6 plasmashell 2>/dev/null || killall plasmashell 2>/dev/null || true
    for i in $(seq 1 20); do
        pgrep -x plasmashell >/dev/null 2>&1 || break
        sleep 0.5
    done
    if pgrep -x plasmashell >/dev/null 2>&1; then
        log_warn "plasmashell did not exit cleanly after 10s; forcing termination."
        killall -9 plasmashell 2>/dev/null || true
        sleep 1
    fi

    log_info "Deploying layout configuration..."
    local APPLETSRC_DEST="$HOME/.config/plasma-org.kde.plasma.desktop-appletsrc"
    local PLASMASHELLRC_DEST="$HOME/.config/plasmashellrc"

    # Older revisions of this script (and manual stow setups) symlinked these
    # destinations back into the repo. `cp` refuses to copy a file onto a
    # symlink that resolves to itself ("are the same file") and aborts the
    # whole script under `set -e`, leaving plasmashell stopped. Force these
    # destinations to always be plain, independent files before writing.
    [[ -L "$APPLETSRC_DEST" ]] && rm -f "$APPLETSRC_DEST"
    [[ -L "$PLASMASHELLRC_DEST" ]] && rm -f "$PLASMASHELLRC_DEST"

    if [[ -f "$APPLETSRC_SRC" ]]; then
        if command -v python3 >/dev/null 2>&1; then
            local hydrated
            hydrated="$(mktemp)"
            python3 "$SCRIPT_DIR/scripts/sanitize_appletsrc.py" install \
                --appletsrc "$APPLETSRC_SRC" \
                --out "$hydrated" \
                --home "$HOME"
            cp "$hydrated" "$APPLETSRC_DEST"
            rm -f "$hydrated"
        else
            log_warn "python3 not found; deploying appletsrc without placeholder hydration."
            cp "$APPLETSRC_SRC" "$APPLETSRC_DEST"
        fi
    else
        log_warn "No tracked appletsrc found at $APPLETSRC_SRC; skipping."
    fi
    if [[ -f "$PLASMASHELLRC_SRC" ]]; then
        cp "$PLASMASHELLRC_SRC" "$PLASMASHELLRC_DEST"
    fi

    if [[ "$NO_RESTART" == true ]]; then
        log_info "Skipping plasmashell restart (--no-restart). Start it manually when ready."
        return 0
    fi

    log_info "Restarting plasmashell..."
    # plasma-plasmashell.service is Type=dbus: "systemctl start" already blocks
    # in the foreground until plasmashell registers its bus name, which is the
    # correct, race-free way to wait for it. Backgrounding this call (as a
    # previous version of this script did) put it in the terminal's process
    # group without a disown, so it could get torn down mid-startup when the
    # script exited -- plasmashell would never finish coming back up.
    if systemctl --user start plasma-plasmashell 2>/dev/null; then
        log_success "plasmashell restarted via systemd."
    else
        log_warn "systemd unit unavailable or failed; falling back to kstart."
        kstart plasmashell >/dev/null 2>&1 || true
    fi

    sleep 1
    if pgrep -x plasmashell >/dev/null 2>&1; then
        log_success "plasmashell is running."
    else
        log_warn "plasmashell does not appear to be running. Start it manually: systemctl --user start plasma-plasmashell"
    fi

    log_success "Plasma layout deployed and active."
}

# -----------------------------------------------------------------------------
# Main Entry Point
# -----------------------------------------------------------------------------
main() {
    log_step "Starting Deployment of Plasma Monochrome Rice"

    check_system

    if [[ "$SYMLINKS_ONLY" == true ]]; then
        clean_broken_symlinks
        deploy_components
        apply_symlinks
        apply_kde_settings
        if [[ "$APPLY_LAYOUT" == true ]]; then
            apply_panel_layout
        else
            log_info "Skipping panel layout deployment (--no-layout)."
        fi
        log_step "Symlinks and Configurations Applied Successfully!"
        return 0
    fi

    if [[ "$DEPS_ONLY" == true ]]; then
        install_dependencies
        log_step "Dependencies Installed Successfully!"
        return 0
    fi

    # Full Installation Workflow
    install_dependencies
    backup_configs
    clean_broken_symlinks
    deploy_components
    apply_symlinks
    apply_kde_settings
    install_sddm_theme
    install_plymouth_theme
    if [[ "$APPLY_LAYOUT" == true ]]; then
        apply_panel_layout
    else
        log_info "Skipping panel layout deployment (--no-layout)."
    fi

    log_step "Installation Completed Successfully!"
    log_info "All Rice components, SDDM, Plymouth, profiles, and dependencies are active."
}

main "$@"
