#!/usr/bin/env bash
# ==============================================================================
# Opens each required Zen Mod's install page in Zen Browser.
#
# Zen mods cannot be installed headlessly: they only install through the
# in-browser "Mods" store UI. Zen's own local-JSON theme import is broken
# upstream (zen-browser/desktop#8789), and the community's zen-conf tool
# confirms the same limitation, so opening each install page and clicking
# "Install" once per mod is the best available automation.
#
# Usage: ./scripts/open_zen_mods.sh
# ==============================================================================
set -euo pipefail

if ! command -v zen-browser >/dev/null 2>&1; then
    echo "[ERROR] zen-browser not found in PATH." >&2
    exit 1
fi

# name -> UUID, from https://raw.githubusercontent.com/zen-browser/theme-store/main/themes.json
declare -A MODS=(
    ["Transparent Zen"]="642854b5-88b4-4c40-b256-e035532109df"
    ["Animations Plus"]="f4866f39-cfd6-4498-ab92-54213b8279dc"
    ["Audio Indicator Enhanced"]="2317fd93-c3ed-4f37-b55a-304c1816819e"
    ["Better Find Bar"]="a6335949-4465-4b71-926c-4a52d34bc9c0"
    ["Floating History"]="253a3a74-0cc4-47b7-8b82-996a64f030d5"
    ["Floating Status Bar"]="906c6915-5677-48ff-9bfc-096a02a72379"
    ["Load Bar"]="ae7868dc-1fa1-469e-8b89-a5edf7ab1f24"
)

echo "Opening ${#MODS[@]} Zen Mod install pages. Click 'Install' on each tab."
echo "Install Transparent Zen first and enable its 'Allow transparency on linux' option."
for name in "${!MODS[@]}"; do
    uuid="${MODS[$name]}"
    echo "  -> $name"
    zen-browser "https://zen-browser.app/mods/${uuid}/" >/dev/null 2>&1 &
    sleep 0.3
done
disown -a
