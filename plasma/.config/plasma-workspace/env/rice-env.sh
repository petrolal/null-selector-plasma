#!/bin/sh
# ==============================================================================
# Plasma Session Environment Setup
# Automatically sourced by KDE Plasma on session launch
# ==============================================================================

# Force Qt applications to use Kvantum engine for native blur & styling
export QT_STYLE_OVERRIDE="kvantum"
export QT_QPA_PLATFORMTHEME="kde"

# Wayland & Hardware Acceleration defaults
export MOZ_ENABLE_WAYLAND=1
export ELECTRON_OZONE_PLATFORM_HINT="auto"
export GDK_BACKEND="wayland,x11,*"
