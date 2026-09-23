#!/usr/bin/env bash
# ==============================================================================
#  ____  _                               __  __                   ____  _          
# |  _ \| | __ _ ___ _ __ ___   __ _    |  \/  | ___  _ __   ___ |  _ \(_) ___ ___ 
# | |_) | |/ _` / __| '_ ` _ \ / _` |   | |\/| |/ _ \| '_ \ / _ \| |_) | |/ __/ _ \
# |  __/| | (_| \__ \ | | | | | (_| |---| |  | | (_) | | | | (_) |  _ <| | (_|  __/
# |_|   |_|\__,_|___/_| |_| |_|\__,_|___|_|  |_|\___/|_| |_|\___/|_| \_\_|\___\___|
#
# Automated Non-Destructive Installer & Deployment Script for KDE Plasma 6
# Rice: null-sector-plasma (Cyberpunk / NieR Monochrome Aesthetic)
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
INSTALL_ZEN_EXTENSIONS=true
OPEN_ZEN_MODS=false
NO_RESTART=false
AUTO_YES=false
AUR_HELPER=""

print_help() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Automated installer and dotfiles bootstrap script for null-sector-plasma.

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
        --no-zen-extensions  Skip force-installing Bonjourr/Dark Reader/Zen Internet
        --open-zen-mods      Open each required Zen Mod's install page for one-click setup
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
            INSTALL_ZEN_EXTENSIONS=false
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
        --zen-extensions)
            INSTALL_ZEN_EXTENSIONS=true
            shift
            ;;
        --no-zen-extensions)
            INSTALL_ZEN_EXTENSIONS=false
            shift
            ;;
        --open-zen-mods)
            OPEN_ZEN_MODS=true
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
        "dolphin"
        "discord"
        "kservice"
        "emacs"
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

    # kwin-effects-forceblur was removed from the AUR upstream; better-blur-dx
    # is its maintained successor (also required for Zen Browser transparency
    # per https://sameerasw.com/zen). Ships separate Wayland/X11 packages.
    local BLUR_PKG="kwin-effects-better-blur-dx"
    if [[ "${XDG_SESSION_TYPE:-}" == "x11" ]]; then
        BLUR_PKG="kwin-effects-better-blur-dx-x11"
    fi

    local AUR_DEPS=(
        "yamis-icon-theme-git"
        "bibata-cursor-git"
        "plasma6-applets-panel-colorizer"
        "plasma6-applets-kurve-git"
        "plasma6-applets-kde-control-station"
        "plasma6-wallpapers-smart-video-wallpaper-reborn"
        "$BLUR_PKG"
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
                # better-blur-dx declares a hard package conflict with the old
                # forceblur-git; --noconfirm does NOT auto-answer pacman's
                # conflict-removal prompt (only its "proceed?" prompts), so it
                # silently defaults to "N" and the whole transaction aborts.
                # Remove the superseded package explicitly first.
                if [[ " ${MISSING_AUR[*]} " == *" $BLUR_PKG "* ]] && pacman -Qq kwin-effects-forceblur-git >/dev/null 2>&1; then
                    log_info "Removing superseded kwin-effects-forceblur-git (conflicts with $BLUR_PKG)..."
                    sudo pacman -R --noconfirm kwin-effects-forceblur-git || log_warn "Could not remove kwin-effects-forceblur-git; $BLUR_PKG install may fail."
                fi
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
    settings = json.dumps({
        "effectsFrameSkip": 3,
        "windowScaling": 1,
        "showTerminalSize": True,
        "fontScaling": 0.8,
        "showMenubar": False,
        "bloomQuality": 0.5,
        "burnInQuality": 0.5,
        "useCustomCommand": False,
        "customCommand": ""
    })
    cur.execute("INSERT OR REPLACE INTO settings (setting, value) VALUES (?, ?)", ("_CURRENT_SETTINGS", settings))
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

    # 8. Zen Browser userChrome.css / userContent.css Auto-Deployment (Symlinked)
    #
    # zen-browser-bin actually resolves its profile under the XDG path
    # ~/.config/zen/ (confirmed via ~/.config/zen/installs.ini's pinned
    # Default=<profile>), NOT the legacy ~/.zen/ dotfolder that Firefox
    # documentation and most guides assume. Targeting ~/.zen/ silently
    # symlinked userChrome.css into a profile the browser never reads --
    # that's the actual reason transparency never appeared.
    local ZEN_DIR="${HOME}/.config/zen"

    # Firefox-family browsers only create their profile on first launch, so on
    # a fresh install there's nothing here yet to symlink userChrome.css into.
    # `-CreateProfile` bootstraps one non-interactively without opening a
    # window, but it only ever creates the LEAF directory it's given -- it
    # silently no-ops (exit 0, nothing written) if the parent doesn't already
    # exist, which is why this needs the `mkdir -p "$ZEN_DIR"` first.
    if [[ ! -d "$ZEN_DIR" ]] && command -v zen-browser >/dev/null 2>&1; then
        log_info "No Zen Browser profile found; bootstrapping one non-interactively..."
        mkdir -p "$ZEN_DIR"
        timeout --signal=KILL 15 zen-browser --headless -CreateProfile "default ${ZEN_DIR}/default" >/dev/null 2>&1
        if [[ -d "${ZEN_DIR}/default" ]]; then
            log_success "Created Zen profile: ${ZEN_DIR}/default"
        else
            log_warn "Could not auto-create a Zen profile; launch Zen Browser once yourself, then re-run this script."
        fi
    fi

    # ~/.config/zen/ also contains installs.ini, profiles.ini and a "Profile
    # Groups" directory that are NOT browsable profiles -- read profiles.ini's
    # Path= entries instead of blindly globbing every directory in there.
    if [[ -f "${ZEN_DIR}/profiles.ini" ]]; then
        while IFS='=' read -r _ rel_path; do
            [[ -n "$rel_path" && -d "${ZEN_DIR}/${rel_path}" ]] || continue
            mkdir -p "${ZEN_DIR}/${rel_path}/chrome"
            ln -sfn "${SCRIPT_DIR}/zen-browser/userChrome.css" "${ZEN_DIR}/${rel_path}/chrome/userChrome.css"
            ln -sfn "${SCRIPT_DIR}/zen-browser/userContent.css" "${ZEN_DIR}/${rel_path}/chrome/userContent.css"
            # user.js lives at the profile root (not chrome/) -- it's how the
            # zen.*/mod.sameerasw.* transparency prefs actually get applied;
            # see the NOTE in configure_zen_extensions() for why policies.json
            # can't carry those.
            ln -sfn "${SCRIPT_DIR}/zen-browser/user.js" "${ZEN_DIR}/${rel_path}/user.js"
            log_success "Linked userChrome.css + userContent.css + user.js to Zen profile: ${rel_path}"
        done < <(grep "^Path=" "${ZEN_DIR}/profiles.ini")
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

        # Terminal Keybinding (Ctrl+Alt+T and Meta+Return / Super+Enter)
        kwriteconfig6 --file kglobalshortcutsrc --group services --group "org.kde.konsole.desktop" --key _launch "none,none,Konsole"
        kwriteconfig6 --file kglobalshortcutsrc --group services --group "cool-retro-term.desktop" --key _launch $'Ctrl+Alt+T\tMeta+Return,Ctrl+Alt+T\tMeta+Return,Cool Retro Term'
        kwriteconfig6 --file kglobalshortcutsrc --group kwin --key "Edit Tiles" "none,none,Toggle Tiles Editor"
        systemctl --user restart plasma-kglobalaccel 2>/dev/null || true

        # Default Browser (Zen), Default File Manager (Dolphin), & Default Editor (Emacs)
        if command -v xdg-settings >/dev/null 2>&1; then
            xdg-settings set default-web-browser zen.desktop 2>/dev/null || log_warn "Could not set Zen as default browser via xdg-settings."
        fi
        kwriteconfig6 --file mimeapps.list --group "Default Applications" --key inode/directory "org.kde.dolphin.desktop"
        kwriteconfig6 --file mimeapps.list --group "Default Applications" --key text/plain "emacs.desktop"
        if command -v xdg-mime >/dev/null 2>&1; then
            xdg-mime default emacs.desktop text/plain 2>/dev/null || true
        fi

        # KWin Force Blur for Zen Browser. better-blur-dx is forceblur's
        # maintained successor (forceblur was removed from the AUR upstream)
        # and what makes Zen's transparency mod actually render correctly, but
        # it's a from-source AUR build that can fail (e.g. its package conflict
        # with forceblur-git isn't --noconfirm-safe -- see install_dependencies).
        # Never disable forceblur in favor of a plugin that isn't actually
        # installed: that leaves zero working blur effect.
        if pacman -Qq kwin-effects-better-blur-dx >/dev/null 2>&1 || pacman -Qq kwin-effects-better-blur-dx-x11 >/dev/null 2>&1; then
            # Plugin service name is "better_blur_dx" (see CMakeLists.txt
            # upstream); its config group is "Effect-better-blur-dx" (see
            # src/blur.kcfg).
            kwriteconfig6 --file kwinrc --group Plugins --key forceblurEnabled false
            kwriteconfig6 --file kwinrc --group Plugins --key better_blur_dxEnabled true
            kwriteconfig6 --file kwinrc --group Effect-better-blur-dx --key WindowClasses "zen"
            kwriteconfig6 --file kwinrc --group Effect-better-blur-dx --key BlurMatching true
            kwriteconfig6 --file kwinrc --group Effect-better-blur-dx --key BlurNonMatching false
            # Strength/noise/brightness/saturation/contrast carried over
            # unchanged from the old forceblur tuning (same effect lineage,
            # same 0-200/percent semantics); adjust via System Settings >
            # Desktop Effects if your reference screenshots specify different
            # numbers.
            kwriteconfig6 --file kwinrc --group Effect-better-blur-dx --key BlurStrength "4"
            kwriteconfig6 --file kwinrc --group Effect-better-blur-dx --key NoiseStrength "5"
            kwriteconfig6 --file kwinrc --group Effect-better-blur-dx --key Brightness "25"
            kwriteconfig6 --file kwinrc --group Effect-better-blur-dx --key Saturation "0"
            kwriteconfig6 --file kwinrc --group Effect-better-blur-dx --key Contrast "105"
        elif pacman -Qq kwin-effects-forceblur-git >/dev/null 2>&1 || pacman -Qq kwin-effects-forceblur >/dev/null 2>&1; then
            log_warn "kwin-effects-better-blur-dx not installed; keeping forceblur active as a fallback."
            kwriteconfig6 --file kwinrc --group Plugins --key forceblurEnabled true
            kwriteconfig6 --file kwinrc --group Effect-forceblur --key MatchingClasses "zen"
            kwriteconfig6 --file kwinrc --group Effect-forceblur --key BlurStrength "4"
            kwriteconfig6 --file kwinrc --group Effect-forceblur --key NoiseStrength "5"
            kwriteconfig6 --file kwinrc --group Effect-forceblur --key Brightness "25"
            kwriteconfig6 --file kwinrc --group Effect-forceblur --key Saturation "0"
            kwriteconfig6 --file kwinrc --group Effect-forceblur --key Contrast "105"
        else
            log_warn "Neither kwin-effects-better-blur-dx nor kwin-effects-forceblur is installed; skipping Zen blur config."
        fi

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

            # KF6 effect plugins embed their metadata in the .so itself (no
            # .desktop file for ksycoca to index) and KWin only scans its
            # plugin directories for new ones at its own startup -- a
            # `reconfigure` call re-evaluates the enabled list among effects
            # it already knows about, but can't discover one installed after
            # the current KWin process started. If this rice just installed
            # better-blur-dx for the first time, config is correct but the
            # effect genuinely cannot be loaded without a logout/login (or
            # reboot) to restart kwin_wayland itself.
            if pacman -Qq kwin-effects-better-blur-dx >/dev/null 2>&1 || pacman -Qq kwin-effects-better-blur-dx-x11 >/dev/null 2>&1; then
                if [[ "$(qdbus6 org.kde.KWin /Effects isEffectLoaded better_blur_dx 2>/dev/null)" != "true" ]]; then
                    log_warn "better_blur_dx is enabled but not yet loaded by KWin (new plugins are only discovered at KWin's own startup)."
                    log_warn "Log out and back in (or reboot) once for Zen's blur to actually appear -- this is unavoidable, not a config bug."
                fi
            fi
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
# Zen Browser Extension Auto-Install (Bonjourr, Dark Reader, Zen Internet)
# -----------------------------------------------------------------------------
# Zen's built-in "Mods" (Transparent Zen, Animations Plus, etc.) can only be
# installed by clicking through the in-browser store UI -- confirmed upstream
# limitation (zen-browser/desktop discussion #4097; even Zen's own local-JSON
# theme import is broken, see issue #8789). There is no reliable file-based
# path to automate that part; see scripts/open_zen_mods.sh for the closest
# available shortcut (opens each mod's install page for a one-click install).
#
# Regular WebExtensions ARE scriptable: Firefox-based browsers read
# <install-dir>/distribution/policies.json and can force-install/force-set
# prefs from it with zero UI interaction. That's what this function does.
configure_zen_extensions() {
    if [[ "$INSTALL_ZEN_EXTENSIONS" != true ]]; then
        return 0
    fi

    log_step "Force-Installing Zen Browser Extensions (Bonjourr, Dark Reader, Zen Internet)"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would merge ExtensionSettings/Preferences into Zen's policies.json."
        return 0
    fi

    if ! command -v zen-browser >/dev/null 2>&1; then
        log_warn "Zen Browser not found (zen-browser-bin not installed?); skipping."
        return 0
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        log_warn "python3 not found; skipping."
        return 0
    fi

    local POLICIES=""
    for candidate in \
        /opt/zen-browser-bin/distribution/policies.json \
        /opt/zen-browser/distribution/policies.json \
        /usr/lib/zen-browser/distribution/policies.json
    do
        if [[ -f "$candidate" ]]; then
            POLICIES="$candidate"
            break
        fi
    done
    if [[ -z "$POLICIES" ]]; then
        log_warn "Could not locate Zen's distribution/policies.json; skipping."
        return 0
    fi

    if ! sudo -n true 2>/dev/null && ! ask_confirm "Force-install Bonjourr, Dark Reader & Zen Internet system-wide (requires sudo, edits $POLICIES)?"; then
        log_warn "Skipped Zen extension auto-install (sudo access declined)."
        return 0
    fi

    # NOTE: zen-browser-bin does not mark this file as a pacman backup, so a
    # future package update can silently overwrite it. Re-running install.sh
    # after an update re-applies this merge.
    local merged
    merged="$(mktemp)"
    python3 - "$POLICIES" "$merged" <<'PYEOF'
import json, sys

src, dst = sys.argv[1], sys.argv[2]
try:
    with open(src) as f:
        data = json.load(f)
except (FileNotFoundError, json.JSONDecodeError):
    data = {}

policies = data.setdefault("policies", {})
ext = policies.setdefault("ExtensionSettings", {})

# GUIDs from Mozilla's AMO API (addons.mozilla.org/api/v5/addons/addon/<slug>/).
# The "latest.xpi" URL is Mozilla's evergreen redirect: it always resolves to
# the current version, so this never needs to be re-pinned by hand.
ext["addon@darkreader.org"] = {
    "installation_mode": "force_installed",
    "install_url": "https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi",
}
ext["{91aa3897-2634-4a8a-9092-279db23a7689}"] = {
    "installation_mode": "force_installed",
    "install_url": "https://addons.mozilla.org/firefox/downloads/latest/zen-internet/latest.xpi",
}
ext["{4f391a9e-8717-4ba6-a5b1-488a34931fcb}"] = {
    "installation_mode": "force_installed",
    "install_url": "https://addons.mozilla.org/firefox/downloads/latest/bonjourr-startpage/latest.xpi",
}

# REQUIRED for userChrome.css/userContent.css to load at all -- without this,
# Firefox-family browsers silently ignore both files entirely (a hard
# requirement since Firefox 69). This was missing the whole time userChrome.css
# was being correctly deployed but never visibly rendering.
#
# NOTE: this is the ONLY custom preference set via policies.json's
# "Preferences" block. Every zen.*/mod.sameerasw.* transparency pref used to
# live here too, but empirically (diffing a live profile's prefs.js against
# Zen's own compiled-in defaults) none of them ever actually applied --
# Firefox's enterprise Preferences policy silently drops names it doesn't
# recognize as standard/allowlisted Firefox prefs, and only this one is.
# Those prefs now live in zen-browser/user.js instead, which has no such
# allowlist and is symlinked into every profile alongside userChrome.css.
prefs = policies.setdefault("Preferences", {})
prefs["toolkit.legacyUserProfileCustomizations.stylesheets"] = {"Value": True, "Status": "user"}

with open(dst, "w") as f:
    json.dump(data, f, indent=2)
PYEOF

    sudo cp "$merged" "$POLICIES"
    rm -f "$merged"
    log_success "policies.json updated: Bonjourr, Dark Reader & Zen Internet will force-install on next Zen launch."
    log_info "Zen Mods can't be installed headlessly -- run ./scripts/open_zen_mods.sh once Zen is open, then click Install on each tab."
}

# -----------------------------------------------------------------------------
# Zen Browser KWin Window Rule (Borderless for Transparency)
# -----------------------------------------------------------------------------
# Force-removes window decorations on zen-classed windows so the transparent
# chrome isn't broken up by a KWin titlebar/border. This is a plain per-user
# ~/.config/kwinrulesrc entry (no sudo needed), written idempotently: it
# no-ops if a rule with this Description already exists, and appends rather
# than overwriting if the user already has other, unrelated window rules.
configure_zen_kwin_rule() {
    log_step "Configuring KWin Window Rule for Zen Browser"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would add a borderless kwinrulesrc entry for zen-classed windows."
        return 0
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        log_warn "python3 not found; skipping KWin window rule."
        return 0
    fi

    python3 - "${HOME}/.config/kwinrulesrc" <<'PYEOF'
import configparser
import sys

path = sys.argv[1]
DESC = "Zen Browser - Borderless for Transparency"

config = configparser.ConfigParser(strict=False)
config.optionxform = str
config.read(path)

for section in config.sections():
    if section != "General" and config.get(section, "Description", fallback="") == DESC:
        print(f"[kwinrule] '{DESC}' already present (rule {section}); skipping.")
        sys.exit(0)

existing = sorted(int(s) for s in config.sections() if s != "General" and s.isdigit())
next_idx = (existing[-1] + 1) if existing else 1

config[str(next_idx)] = {
    "Description": DESC,
    "wmclass": "zen",
    "wmclassmatch": "1",       # 1 = Exact Match
    "wmclasscomplete": "false",
    "types": "1",              # 1 = NormalWindow
    "noborder": "true",
    "noborderrule": "2",       # 2 = Force
}

all_indices = existing + [next_idx]
if "General" not in config:
    config["General"] = {}
config["General"]["count"] = str(len(all_indices))
config["General"]["rules"] = ",".join(str(i) for i in all_indices)

with open(path, "w") as f:
    config.write(f, space_around_delimiters=False)
print(f"[kwinrule] added rule {next_idx}: {DESC}")
PYEOF

    if command -v qdbus6 >/dev/null 2>&1; then
        qdbus6 org.kde.KWin /KWin reconfigure 2>/dev/null || true
    fi
    log_success "KWin window rule configured."
}

# -----------------------------------------------------------------------------
# Zen Mods Registry Seeding (Experimental)
# -----------------------------------------------------------------------------
# Zen mods are documented as UI-install-only (zen-browser/desktop discussion
# #4097, and the official "Import Mods" JSON feature is confirmed broken --
# issue #8789). But inspecting a real installed mod on this rice's own machine
# showed the profile's own registry (<profile>/zen-themes.json) is just plain,
# static catalog metadata plus "enabled": true -- no user-specific data, no
# checksum, nothing that looks hand-wavy. This function seeds that registry
# for the 6 mods not yet installed, additively (never touches an already-
# registered mod, e.g. Transparent Zen, and never touches the generated
# chrome/zen-themes.css -- that's left for Zen's own marketplace code to
# regenerate).
#
# What's UNVERIFIED: whether Zen actually regenerates zen-themes.css from this
# file automatically on next launch, or only when the marketplace preferences
# page (about:preferences#zenMarketplace) is opened. If mods show as installed
# but their CSS doesn't apply after restarting Zen, opening that page once
# should force the regeneration. Treat this as a real time-saver (skips 6 of 7
# manual installs) rather than a guaranteed full replacement for the click.
configure_zen_mods_registry() {
    log_step "Seeding Zen Mods Registry (experimental)"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would seed zen-themes.json with the required mods for each profile."
        return 0
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        log_warn "python3 not found; skipping mod registry seeding."
        return 0
    fi

    local ZEN_DIR="${HOME}/.config/zen"
    [[ -f "${ZEN_DIR}/profiles.ini" ]] || return 0

    local catalog
    catalog="$(mktemp)"
    if ! curl -fsSL "https://raw.githubusercontent.com/zen-browser/theme-store/main/themes.json" -o "$catalog"; then
        log_warn "Could not fetch the Zen mods catalog; skipping registry seeding."
        rm -f "$catalog"
        return 0
    fi

    while IFS='=' read -r _ rel_path; do
        [[ -n "$rel_path" && -d "${ZEN_DIR}/${rel_path}" ]] || continue
        python3 - "$catalog" "${ZEN_DIR}/${rel_path}/zen-themes.json" "$rel_path" <<'PYEOF'
import json, sys

catalog_path, registry_path, profile_name = sys.argv[1], sys.argv[2], sys.argv[3]

REQUIRED = {
    "642854b5-88b4-4c40-b256-e035532109df": "Transparent Zen",
    "f4866f39-cfd6-4498-ab92-54213b8279dc": "Animations Plus",
    "2317fd93-c3ed-4f37-b55a-304c1816819e": "Audio Indicator Enhanced",
    "a6335949-4465-4b71-926c-4a52d34bc9c0": "Better Find Bar",
    "253a3a74-0cc4-47b7-8b82-996a64f030d5": "Floating History",
    "906c6915-5677-48ff-9bfc-096a02a72379": "Floating Status Bar",
    "ae7868dc-1fa1-469e-8b89-a5edf7ab1f24": "Load Bar",
}

with open(catalog_path) as f:
    catalog = json.load(f)

try:
    with open(registry_path) as f:
        registry = json.load(f)
except (FileNotFoundError, json.JSONDecodeError):
    registry = {}

added = []
for uuid, name in REQUIRED.items():
    if uuid in registry:
        continue
    entry = catalog.get(uuid)
    if not entry:
        continue
    entry = dict(entry)
    entry["enabled"] = True
    registry[uuid] = entry
    added.append(name)

if added:
    with open(registry_path, "w") as f:
        json.dump(registry, f, indent=2)
    print(f"[zen-mods] {profile_name}: registered {', '.join(added)}")
else:
    print(f"[zen-mods] {profile_name}: all required mods already registered")
PYEOF
    done < <(grep "^Path=" "${ZEN_DIR}/profiles.ini")

    rm -f "$catalog"
    log_success "Zen mods registry seeded. Restart Zen, then check about:preferences#zenMarketplace if CSS doesn't apply."
}

# -----------------------------------------------------------------------------
# Zen Browser Workspace Gradient De-Colorization
# -----------------------------------------------------------------------------
# Zen assigns each newly-created Workspace a random-ish gradient accent color
# on first launch (e.g. a warm cream/beige) -- this is separate from
# everything else in this file: it's not a pref, not policies.json, not
# user.js, it's per-workspace state Zen writes into its own session store
# (<profile>/zen-sessions.jsonlz4, Mozilla's "mozLz4" format: an 8-byte magic
# + 4-byte LE size prefix around a raw LZ4 block -- decompressed/recompressed
# here via liblz4 through ctypes since there's no python-lz4 package and the
# `lz4` CLI only speaks the frame format, not this block format). There is no
# UI pref or about:config flag for it; confirmed by tracing Zen's own
# ZenGradientGenerator.mjs, the workspace's theme.gradientColors dots are the
# only source of this color.
#
# Every dot with mismatched R/G/B gets replaced by a neutral gray at the same
# average luminance (keeps the same visual weight/opacity, drops the hue).
# Only runs when Zen isn't running -- it owns this file and autosaves over it,
# so patching it live would race and likely get silently reverted.
configure_zen_workspace_theme() {
    log_step "De-Colorizing Zen Workspace Gradients"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] Would neutralize any non-gray Zen workspace gradient colors."
        return 0
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        log_warn "python3 not found; skipping workspace gradient de-colorization."
        return 0
    fi
    if pgrep -f 'zen-bin$' >/dev/null 2>&1; then
        log_warn "Zen Browser is running; skipping workspace gradient de-colorization (it owns zen-sessions.jsonlz4 and would overwrite this). Quit Zen and re-run to apply."
        return 0
    fi

    local ZEN_DIR="${HOME}/.config/zen"
    [[ -f "${ZEN_DIR}/profiles.ini" ]] || return 0

    while IFS='=' read -r _ rel_path; do
        [[ -n "$rel_path" && -f "${ZEN_DIR}/${rel_path}/zen-sessions.jsonlz4" ]] || continue
        python3 - "${ZEN_DIR}/${rel_path}/zen-sessions.jsonlz4" "$rel_path" <<'PYEOF'
import ctypes, json, struct, sys

path, profile_name = sys.argv[1], sys.argv[2]
MAGIC = b"mozLz40\x00"

with open(path, "rb") as f:
    raw = f.read()
if raw[:8] != MAGIC:
    print(f"[zen-workspace-theme] {profile_name}: not a mozLz4 file, skipping")
    sys.exit(0)

size = struct.unpack("<I", raw[8:12])[0]
lz4 = ctypes.CDLL("liblz4.so.1")
out = ctypes.create_string_buffer(size)
ret = lz4.LZ4_decompress_safe(raw[12:], out, len(raw) - 12, size)
if ret != size:
    print(f"[zen-workspace-theme] {profile_name}: decompression failed, skipping")
    sys.exit(0)

data = json.loads(out.raw[:ret])

patched = []
for space in data.get("spaces", []):
    for dot in space.get("theme", {}).get("gradientColors", []):
        c = dot.get("c")
        lightness = dot.get("lightness")
        if c == [25, 25, 25] and lightness == "15":
            continue
        dot["c"] = [25, 25, 25]
        dot["lightness"] = "15"
        patched.append((space.get("name"), c, [25, 25, 25]))

if not patched:
    print(f"[zen-workspace-theme] {profile_name}: all workspace gradients already dark monochrome")
    sys.exit(0)

import shutil
backup = path + ".bak-pre-monochrome"
if not __import__("os").path.exists(backup):
    shutil.copy2(path, backup)

body = json.dumps(data, separators=(",", ":")).encode("utf-8")
bound = lz4.LZ4_compressBound(len(body))
buf = ctypes.create_string_buffer(bound)
lz4.LZ4_compress_default.restype = ctypes.c_int
comp_size = lz4.LZ4_compress_default(body, buf, len(body), bound)
with open(path, "wb") as f:
    f.write(MAGIC + struct.pack("<I", len(body)) + buf.raw[:comp_size])

for name, old, gray in patched:
    print(f"[zen-workspace-theme] {profile_name}: workspace {name!r} gradient {old} -> [{gray}, {gray}, {gray}]")
PYEOF
    done < <(grep "^Path=" "${ZEN_DIR}/profiles.ini")

    log_success "Zen workspace gradients de-colorized where needed."
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

    log_info "Rebuilding KDE service cache..."
    if command -v kbuildsycoca6 >/dev/null 2>&1; then
        kbuildsycoca6 --noincremental >/dev/null 2>&1 || true
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
    log_step "Starting Deployment of null-sector-plasma"

    check_system

    if [[ "$SYMLINKS_ONLY" == true ]]; then
        clean_broken_symlinks
        deploy_components
        apply_symlinks
        apply_kde_settings
        configure_zen_extensions
        configure_zen_kwin_rule
        configure_zen_mods_registry
        configure_zen_workspace_theme
        if [[ "$APPLY_LAYOUT" == true ]]; then
            apply_panel_layout
        else
            log_info "Skipping panel layout deployment (--no-layout)."
        fi
        if [[ "$OPEN_ZEN_MODS" == true ]]; then
            "${SCRIPT_DIR}/scripts/open_zen_mods.sh" || log_warn "Could not open Zen Mod install pages."
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
    configure_zen_extensions
    configure_zen_kwin_rule
    configure_zen_mods_registry
    configure_zen_workspace_theme
    if [[ "$APPLY_LAYOUT" == true ]]; then
        apply_panel_layout
    else
        log_info "Skipping panel layout deployment (--no-layout)."
    fi
    if [[ "$OPEN_ZEN_MODS" == true ]]; then
        "${SCRIPT_DIR}/scripts/open_zen_mods.sh" || log_warn "Could not open Zen Mod install pages."
    fi

    log_step "Installation Completed Successfully!"
    log_info "All Rice components, SDDM, Plymouth, profiles, and dependencies are active."
}

main "$@"
