<div align="center">

# 🖤 plasma-mono-rice

### Automated • Reproducible • Declarative KDE Plasma 6 Monochrome Rice
*(Inspired by [agridyne/dotfiles-dt](https://github.com/agridyne/dotfiles-dt) & NieR: Automata / Cyberpunk Aesthetics)*

[![Arch Linux](https://img.shields.io/badge/Arch_Linux-1793D1?logo=arch-linux&logoColor=white&style=for-the-badge)](https://archlinux.org/)
[![CachyOS](https://img.shields.io/badge/CachyOS-00A389?logo=linux&logoColor=white&style=for-the-badge)](https://cachyos.org/)
[![KDE Plasma 6](https://img.shields.io/badge/KDE_Plasma_6-1D99F3?logo=kde&logoColor=white&style=for-the-badge)](https://kde.org/plasma-desktop/)
[![Wayland](https://img.shields.io/badge/Wayland-Native-brightgreen?logo=wayland&logoColor=white&style=for-the-badge)](https://wayland.freedesktop.org/)
[![GNU Stow](https://img.shields.io/badge/Dotfiles-GNU_Stow-informational?logo=gnu&logoColor=white&style=for-the-badge)](https://www.gnu.org/software/stow/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

<p align="center">
  A high-contrast <b>monochrome cyberpunk desktop environment</b> for <b>KDE Plasma 6 on Wayland</b>.<br />
  Engineered with <b>Panel Colorizer</b> floating capsule islands, <b>YoRHa HUD</b> telemetry, <b>Kurve</b> audio visualizer, <b>CatWalk</b> CPU monitor, <b>YAMIS</b> monochrome icons, <b>cool-retro-term</b>, and <b>Zen Browser</b> translucent glass.
</p>

[Quick Start](#-quick-start) • [Design Specifications](#-design-specifications) • [Architecture](#-directory-tree--stow-architecture) • [Post-Install Guides](#-component-configuration-guides) • [Troubleshooting](#-troubleshooting)

</div>

---

## 📸 Visual Showcase

<div align="center">

### Desktop Overview
![Desktop Showcase](assets/screenshots/desktop.png)

| 🎛️ Panel Colorizer Presets | 🔊 Kurve CAVA Audio Visualizer |
| :---: | :---: |
| ![Panel Colorizer](assets/screenshots/panel-colorizer-1.png) | ![Kurve](assets/screenshots/kurve-settings-1.png) |

| 🪟 KWin Wayland Force Blur | 🌐 Zen Browser Translucency |
| :---: | :---: |
| ![KWin Force Blur](assets/screenshots/kwin-effects-forceblur-1.png) | ![Zen Browser](assets/screenshots/transparent-zen-settings.png) |

</div>

---

## 💎 Design Specifications

| Component | Technology | Configuration Details |
| :--- | :--- | :--- |
| **Base System** | Arch Linux / CachyOS | KDE Plasma 6.7+, Wayland native, Linux Zen kernel |
| **Live Wallpaper** | [Smart Video Wallpaper Reborn](https://github.com/adhec/smart-video-wallpaper-reborn) | [DesktopHut Digital Gaze](https://www.desktophut.com/digital-gaze-8642) 4K video (fallback static PNG included) |
| **Theme & Colors** | [Monochrome KDE](https://github.com/pwyde/monochrome-kde) | Minimal high-contrast black & white palette |
| **Icon Theme** | [YAMIS](https://github.com/dirn/yet-another-monochrome-icon-set) | Adaptive monochrome vector icons across panel and desktop |
| **Cursor Theme** | `Bibata-Modern-Ice` | Crisp white minimalist cursor |
| **System Font** | `JetBrainsMono Nerd Font` | System-wide 10pt monospace font with icons |
| **Panels** | [Panel Colorizer](https://github.com/luisbocanegra/plasma-panel-colorizer) | Bundled `Main Setup` & `Main Blur` presets with floating capsules |
| **Telemetry HUD** | [YoRHa HUD](https://github.com/AxZoRos/YoRHa-HUD) | *NieR: Automata* Bunker link telemetry widget on desktop |
| **Thermal Monitor** | [Thermal Monitor](https://github.com/olib14/thermal-monitor) | Direct hardware temperature telemetry (CPU/GPU) |
| **Audio Visualizer** | [Kurve](https://github.com/luisbocanegra/kurve) | Left-side CAVA desktop spectrum equalizer |
| **CPU Cat** | [CatWalk Enhanced](https://github.com/BLADR-ONE/CatWalk-Enhanced-Plasmoid) | Animated running cat scaled to processor load |
| **Terminal** | [Cool-Retro-Term](https://github.com/Swordfish90/cool-retro-term) | Bundled amber/white monochrome CRT profile with scanlines |
| **Browser** | [Zen Browser](https://zen-browser.app/) | Glass translucency via `userChrome.css` and KWin force blur |

---

## 📂 Directory Tree & Stow Architecture

This repository strictly follows the **GNU Stow** modular architecture. Every component links cleanly into `$HOME` without manual copying:

```text
plasma-mono-rice/
├── install.sh                       # Non-destructive automated deployment & dependency installer
├── harvest.sh                       # Dotfile sync script to scrape live configs back into repo
├── backup.sh                        # CLI alias wrapper for harvest.sh
├── .gitignore                       # Rules preventing secret / cache pollution
├── LICENSE                          # MIT License
├── README.md                        # Documentation and architecture guide
│
├── assets/
│   ├── wallpapers/                  # 4K static wallpaper & video URL guides
│   │   ├── digital-gaze.png         # 3840x2160 crisp 8-bit static frame
│   │   └── wallpapers.md            # Direct 4K MP4 live video wallpaper links
│   └── screenshots/                 # Showcase screenshots & config references
│       ├── desktop.png
│       ├── panel-colorizer-1.png
│       ├── kurve-settings-1.png
│       ├── kwin-effects-forceblur-1.png
│       └── transparent-zen-settings.png
│
├── scripts/
│   ├── sanitize_appletsrc.py        # Harvest/install template engine + widget verifier
│   └── dump_widgets.py              # Debug: pretty-print live containment/applet config
│
├── plasma/                          # KDE Plasma 6 desktop & KWin configuration
│   ├── .config/
│   │   ├── kdeglobals               # Monochrome color scheme, YAMIS icons, JetBrains font
│   │   ├── kwinrc                   # KWin window manager & forceblur effects
│   │   ├── plasmashellrc            # Floating top bar and bottom dock geometry
│   │   ├── plasma-org.kde.plasma.desktop-appletsrc  # Panel/desktop widget layout (templated)
│   │   ├── panel-colorizer/
│   │   │   └── presets/
│   │   │       ├── Main Setup/      # Active floating capsule preset
│   │   │       └── Main Blur/       # Maximized / fullscreen window blur preset
│   │   └── plasma-workspace/env/
│   │       └── rice-env.sh          # Session environment overrides
│   ├── .local/share/
│   │   ├── color-schemes/           # Monochrome color palette
│   │   ├── plasma/desktoptheme/     # Monochrome Plasma desktop theme
│   │   ├── aurorae/themes/          # Monochrome window decorations
│   │   └── plasma/plasmoids/        # Pre-packaged native Plasma 6 widgets
│   │       ├── com.axzoros.yorhahud/          # YoRHa HUD Telemetry
│   │       ├── org.kde.plasma.catwalkEnhanced/# CatWalk Animated CPU Cat
│   │       ├── org.kde.olib.thermalmonitor/   # Hardware Temperature Monitor
│   │       └── org.kde.plasma.clearclock/     # Minimalist ClearClock
│   └── layout.js                    # DEPRECATED historical reference only, not deployed
│
├── cool-retro-term/                 # CRT terminal emulator profile
│   ├── cool-retro-term-monochrome.json
│   └── README.md
│
├── zen-browser/                     # Zen Browser styling
│   ├── userChrome.css
│   └── README.md
│
├── kvantum/                         # Kvantum translucent theme engine
│   └── .config/Kvantum/
│       ├── kvantum.kvconfig
│       ├── Monochrome/
│       └── MonochromeBlur/
│
├── fastfetch/                       # Fastfetch system info
│   └── .config/fastfetch/
│
├── starship/                        # Minimal prompt
│   └── .config/starship.toml
│
└── zsh/                             # Zsh shell configuration
    ├── .zshrc
    └── .zshenv
```

---

## ⚡ Quick Start

### 1. Clone the Repository
```bash
git clone git@github.com:petrolal/plasma-mono-rice.git
cd plasma-mono-rice
```

### 2. Run the Automated Installer
The installer creates a timestamped safety backup in `~/.config_backup_mono_<timestamp>`, installs all missing dependencies via `pacman` and `yay`, deploys the plasmoids, applies the Monochrome theme, and stows dotfiles:

```bash
# Preview what the script will do without making changes:
./install.sh --dry-run

# Run the complete deployment:
./install.sh

# Or apply the declarative dual-panel layout automatically:
./install.sh --apply-layout
```

---

## 🛠️ Component Configuration Guides

### 📺 Cool-Retro-Term (Phosphor CRT Terminal)
1. Launch `cool-retro-term`.
2. Right-click inside the terminal window and select **Settings**.
3. Navigate to the **Profiles** tab and click **Import Profile**.
4. Select `cool-retro-term/cool-retro-term-monochrome.json`.
5. Select the **Monochrome** profile and click **Load**.

### 🌐 Zen Browser Transparency & Blur
1. Ensure `kwin-effects-forceblur-git` is installed.
2. In **System Settings** -> **Window Management** -> **Desktop Effects** -> **Blur / Force Blur**:
   - Add `zen` to the forced window classes.
   - Blur strength: `4`, Noise strength: `5`, Brightness: `25%`, Saturation: `0%`, Contrast: `105%`.
3. In Zen Browser, install the **Transparent Zen** mod and configure as shown in `assets/screenshots/transparent-zen-settings.png`.
4. The file `zen-browser/userChrome.css` is automatically copied to your Zen profiles by `install.sh`.

### 🔊 Kurve Audio Visualizer
1. Add the **Kurve** widget to your left desktop screen.
2. Open **Kurve Settings** -> **Visualizer**:
   - Style: `Blocks`, Orientation: `Left`
   - Bar width: `4`, Bar gap: `5`, Block height: `5`, Block gap: `4`
   - Background: `Transparent` (in General tab)
3. Ensure `cava` is installed (`sudo pacman -S --needed cava`).

---

## 🧩 Panel & Desktop Layout Architecture

The dual-panel + desktop HUD layout is **not** applied via `qdbus6 ... evaluateScript`.
In Plasma 6 that DBus scripting API ignores `writeConfig` on desktop applets,
mangles large nested JSON configs (e.g. Panel Colorizer), and races with
plasmashell's own writes to disk — reliably corrupting panel state.

Instead, the layout is scraped and replayed as plain config files:

- **`harvest.sh`** copies your live, working
  `~/.config/plasma-org.kde.plasma.desktop-appletsrc` and `~/.config/plasmashellrc`
  into `plasma/.config/`, then runs `scripts/sanitize_appletsrc.py harvest` to
  template out your `$HOME` path and default activity UUID (so the file is
  reproducible on any machine) and prune stale `[PlasmaViews][Panel N]` blocks
  left behind by prior panel recreations. It finishes with a `verify` pass
  confirming every widget in the [Layout Specification](#-design-specifications)
  is still present.
- **`install.sh`**'s `apply_panel_layout()` stops plasmashell, hydrates the
  tracked appletsrc template back into a real config (via
  `scripts/sanitize_appletsrc.py install`, injecting the current machine's
  `$HOME` and live default activity UUID), copies both files into
  `~/.config/`, and restarts plasmashell — waiting for it to actually exit and
  come back up rather than a fixed `sleep`.

`plasma/layout.js` is kept only as a historical reference of the layout's
intent; it is not linked or evaluated by either script.

## 🔄 Maintaining & Syncing Dotfiles

When you customize your settings in KDE System Settings, you can scrape your changes back into this repository:

```bash
# Sync active system configurations into the repo
./harvest.sh
```

---

## 📜 License
Released under the [MIT License](LICENSE).
Monochrome theme by [pwyde](https://github.com/pwyde/monochrome-kde), YAMIS by [dirn](https://github.com/dirn/yet-another-monochrome-icon-set), YoRHa HUD by [AxZoRos](https://github.com/AxZoRos/YoRHa-HUD), CatWalk Enhanced by [BLADR-ONE](https://github.com/BLADR-ONE/CatWalk-Enhanced-Plasmoid).
Original rice inspiration from [agridyne/dotfiles-dt](https://github.com/agridyne/dotfiles-dt).
