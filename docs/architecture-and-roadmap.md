# 🪐 Architecture & Complete Implementation Roadmap

## 1. Project Overview & Philosophy

`null-sector-plasma` is an automated, reproducible, and declarative KDE Plasma 6 desktop environment ("rice") for Arch Linux and CachyOS on Wayland, styled with a high-contrast cyberpunk and NieR: Automata aesthetic.

The codebase is driven by a pure functional **Clojure / Babashka** automation engine (`mono-rice`), replacing legacy shell and Python scripts.

---

## 2. Implementation State (Completed Phases 1–20)

* **Phase 1: Foundation Setup**
  * Task configuration: [`bb.edn`](file:///home/petrolal/null-sector-plasma/bb.edn)
  * Declarative specification: [`rice.edn`](file:///home/petrolal/null-sector-plasma/rice.edn)
  * Core modules: [`mono_rice.proc`](file:///home/petrolal/null-sector-plasma/src/mono_rice/proc.clj), [`mono_rice.fs`](file:///home/petrolal/null-sector-plasma/src/mono_rice/fs.clj), [`mono_rice.kde`](file:///home/petrolal/null-sector-plasma/src/mono_rice/kde.clj)

* **Phase 2: Core Domain Logic**
  * Pacman / AUR / Git package resolver: [`mono_rice.deps`](file:///home/petrolal/null-sector-plasma/src/mono_rice/deps.clj)
  * Zen Browser glass translucency & policies: [`mono_rice.zen.*`](file:///home/petrolal/null-sector-plasma/src/mono_rice/zen)
  * Functional layout templating & sanitation: [`mono_rice.layout.sanitizer`](file:///home/petrolal/null-sector-plasma/src/mono_rice/layout/sanitizer.clj)

* **Phase 3: CLI Subcommand Architecture**
  * Modular command handlers under [`src/mono_rice/cmd/`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd):
    * `install.clj` — Full non-destructive deployment pipeline
    * `harvest.clj` — Scrapes active `$HOME` configs back into repo with placeholder sanitization
    * `backup.clj` — Creates timestamped rollback snapshots in `~/.config_backup_mono_<timestamp>`
    * `verify.clj` — Health check of pacman/AUR packages, widget presence, and symlinks

* **Phase 4: Standalone Binary, Testing & CI**
  * Standalone executable: [`mono-rice`](file:///home/petrolal/null-sector-plasma/mono-rice) (`chmod +x`, sub-40ms startup)
  * Automated test suite under [`test/mono_rice/`](file:///home/petrolal/null-sector-plasma/test/mono_rice) (`bb test`)
  * GitHub Actions CI pipeline: [`.github/workflows/ci.yml`](file:///home/petrolal/null-sector-plasma/.github/workflows/ci.yml)
  * Compatibility shims for legacy scripts (`install.sh`, `harvest.sh`, `backup.sh`) forwarding to `bb`

* **Phase 5: Packaging & Distribution**
  * Arch Linux / CachyOS package manifest: [`PKGBUILD`](file:///home/petrolal/null-sector-plasma/PKGBUILD)
  * Automated release workflow: [`.github/workflows/release.yml`](file:///home/petrolal/null-sector-plasma/.github/workflows/release.yml)

* **Phase 6: Multi-Profile & Dynamic Theming Engine**
  * Dynamic palette registry in [`rice.edn`](file:///home/petrolal/null-sector-plasma/rice.edn) (`:monochrome-dark`, `:monochrome-light`, `:amber-crt`, `:cyberpunk-red`)
  * Theme switching logic: [`mono_rice.theme`](file:///home/petrolal/null-sector-plasma/src/mono_rice/theme.clj) and [`mono_rice.cmd.theme`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/theme.clj)
  * CLI command: `mono-rice theme [list | set <name>]` / `bb theme [list | set <name>]`

* **Phase 7: Multi-Monitor & Resolution Scaling Engine**
  * Display resolution auto-detection and geometry scaling engine: [`mono_rice.layout.scaling`](file:///home/petrolal/null-sector-plasma/src/mono_rice/layout/scaling.clj)

* **Phase 8: Configuration Drift Sentinel**
  * Live drift detection & monitoring loop: [`mono_rice.cmd.watch`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/watch.clj)
  * CLI command: `mono-rice watch [--once | --interval <sec>]` / `bb watch`

* **Phase 9: Rollback & Snapshot Restore Engine**
  * Snapshot discovery and restoration: [`mono_rice.cmd.rollback`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/rollback.clj) and [`mono_rice.fs`](file:///home/petrolal/null-sector-plasma/src/mono_rice/fs.clj)
  * CLI command: `mono-rice rollback [list | restore <name>]` / `bb rollback`

* **Phase 10: Unified Visual Diff Engine**
  * Line-by-line visual difference inspector: [`mono_rice.cmd.diff`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/diff.clj)
  * CLI command: `mono-rice diff [file]` / `bb diff`

* **Phase 11: Standalone Rice Bundle Export & Import**
  * Portable `.tar.gz` bundle archiver and extractor: [`mono_rice.cmd.bundle`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/bundle.clj)
  * CLI command: `mono-rice bundle [export | import <archive>]` / `bb bundle`

* **Phase 12: Declarative Plasmoid Package Manager**
  * Plasma 6 applet package inspector and manager: [`mono_rice.cmd.plasmoid`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/plasmoid.clj)
  * CLI command: `mono-rice plasmoid [list | install <path> | remove <id>]` / `bb plasmoid`

* **Phase 13: Interactive Terminal Dashboard & TUI**
  * ANSI cybernetic terminal menu & dashboard: [`mono_rice.cmd.tui`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/tui.clj)
  * CLI command: `mono-rice tui` / `bb tui`

* **Phase 14: Systemd Sentinel Service & Desktop Notifications**
  * User systemd unit automation & desktop notifications: [`mono_rice.cmd.daemon`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/daemon.clj)
  * CLI command: `mono-rice daemon [start | install-service | uninstall-service]` / `bb daemon`

* **Phase 15: Wallpaper & Lockscreen Video Sync Engine**
  * Simultaneous synchronization for desktop & lockscreen 4K video wallpapers: [`mono_rice.cmd.wallpaper`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/wallpaper.clj)
  * CLI command: `mono-rice wallpaper [list | set <name> | random]` / `bb wallpaper`

* **Phase 16: Hardware Telemetry & Audio Equalizer Profiler**
  * Dynamic CAVA & Kurve spectrum equalizer profile switcher: [`mono_rice.cmd.audio`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/audio.clj)
  * CLI command: `mono-rice audio [list | preset <name>]` / `bb audio`

* **Phase 17: Rice Doctor & Auto-Repair Engine**
  * Deep system diagnostics & one-click automatic remediation: [`mono_rice.cmd.doctor`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/doctor.clj)
  * CLI command: `mono-rice doctor [--fix]` / `bb doctor [--fix]`

* **Phase 18: KWin Window Rules & Tiling Manager**
  * Declarative translucency rules & blur effect shader tuning: [`mono_rice.cmd.kwin`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/kwin.clj)
  * CLI command: `mono-rice kwin [rules | apply-rules | set-blur <val>]` / `bb kwin`

* **Phase 19: Terminal, Fastfetch & Shell Aesthetic Synchronizer**
  * Fastfetch ASCII emblem switcher (NieR, Cyberpunk, Null-Sector, Arch): [`mono_rice.cmd.fetch`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/fetch.clj)
  * CLI command: `mono-rice fetch [list | preview <preset> | set <preset>]` / `bb fetch`

* **Phase 20: Multi-Machine Hardware Profiler & Laptop Adaptations**
  * Form factor detection & hardware telemetry tuning: [`mono_rice.cmd.profile`](file:///home/petrolal/null-sector-plasma/src/mono_rice/cmd/profile.clj)
  * CLI command: `mono-rice profile [detect | apply]` / `bb profile`

---

## 3. Codebase Source Tree Map

```text
null-sector-plasma/
├── bb.edn                                # Babashka task manifest, test runner & nREPL task
├── rice.edn                              # Master EDN rice manifest with multi-theme profiles
├── mono-rice                             # Direct executable wrapper (#!/usr/bin/env bb)
├── PKGBUILD                              # Arch Linux / AUR package specification
├── bootstrap.sh                          # Non-interactive shell bootstrapper
├── install.sh                            # Compatibility forwarder -> bb install
├── harvest.sh                            # Compatibility forwarder -> bb harvest
├── backup.sh                             # Compatibility forwarder -> bb backup
│
├── .github/workflows/
│   ├── ci.yml                            # GitHub Actions CI (bb test & bb check)
│   └── release.yml                       # GitHub Actions Release pipeline
│
├── src/mono_rice/
│   ├── core.clj                          # CLI option parsing (babashka.cli) & command router
│   ├── proc.clj                          # Shell execution helper (sh!), sudo wrapping, logging
│   ├── fs.clj                            # GNU Stow symlink engine, directory creation, backups & rollbacks
│   ├── deps.clj                          # Pacman / AUR (yay/paru) / Git dependency management
│   ├── kde.clj                           # kwriteconfig6, shortcuts, themes, KWin force-blur
│   ├── theme.clj                         # Dynamic theme engine & multi-profile switcher
│   │
│   ├── layout/
│   │   ├── sanitizer.clj                 # Plasma desktop-appletsrc template & pruning engine
│   │   ├── inspector.clj                 # Live containment & plasmoid tree introspection
│   │   └── scaling.clj                   # Resolution detection & panel geometry auto-scaling
│   │
│   ├── zen/
│   │   ├── profile.clj                   # Profile discovery, CSS symlinks, KWin window rules
│   │   ├── policies.clj                  # Enterprise policies.json force-install extension setup
│   │   └── mods.clj                      # Mod registry seeding & mozLz4 gradient neutralizer
│   │
│   └── cmd/
│       ├── tui.clj                       # Interactive Cyberpunk terminal dashboard
│       ├── doctor.clj                    # Deep diagnostic health checks & auto-repair engine
│       ├── install.clj                   # mono-rice install workflow
│       ├── harvest.clj                   # mono-rice harvest workflow
│       ├── backup.clj                    # mono-rice backup workflow
│       ├── rollback.clj                  # mono-rice rollback workflow
│       ├── diff.clj                      # mono-rice visual diff workflow
│       ├── verify.clj                    # mono-rice verify workflow
│       ├── theme.clj                     # mono-rice theme workflow
│       ├── kwin.clj                      # mono-rice KWin window rules & blur tuning
│       ├── fetch.clj                     # mono-rice Fastfetch ASCII aesthetic manager
│       ├── profile.clj                   # mono-rice hardware profiler & form factor tuner
│       ├── wallpaper.clj                 # mono-rice wallpaper & lockscreen sync
│       ├── audio.clj                     # mono-rice audio equalizer presets
│       ├── watch.clj                     # mono-rice watch drift sentinel
│       ├── daemon.clj                    # mono-rice systemd service & notifications
│       ├── bundle.clj                    # mono-rice bundle packaging workflow
│       └── plasmoid.clj                  # mono-rice plasmoid package manager
│
├── test/mono_rice/
│   ├── sanitizer_test.clj                # Unit tests for template replacement & panel pruning
│   ├── manifest_test.clj                 # Unit tests for rice.edn structure
│   ├── inspector_test.clj                # Unit tests for INI parsing
│   ├── theme_test.clj                    # Unit tests for theme switching and resolution
│   ├── scaling_test.clj                  # Unit tests for resolution scaling engine
│   ├── watch_test.clj                    # Unit tests for configuration drift sentinel
│   ├── rollback_test.clj                 # Unit tests for snapshot rollback & restoration
│   ├── diff_test.clj                     # Unit tests for template diffing
│   ├── bundle_test.clj                   # Unit tests for archive export/import
│   ├── plasmoid_test.clj                 # Unit tests for plasmoid package manager
│   ├── tui_test.clj                      # Unit tests for TUI rendering
│   ├── daemon_test.clj                   # Unit tests for systemd service generator
│   ├── wallpaper_test.clj                # Unit tests for wallpaper manager
│   ├── audio_test.clj                    # Unit tests for CAVA equalizer presets
│   ├── doctor_test.clj                   # Unit tests for diagnostic health & repair
│   ├── kwin_test.clj                     # Unit tests for KWin window rules & blur
│   ├── fetch_test.clj                    # Unit tests for Fastfetch ASCII presets
│   └── profile_test.clj                  # Unit tests for hardware profiling
│
├── plasma/                               # Modular tracked KDE configurations
├── kvantum/                              # Kvantum translucent theme engine
├── fastfetch/                            # Fastfetch configuration
├── starship/                             # Starship prompt configuration
├── zsh/                                  # Zsh shell configuration
└── assets/                               # Wallpapers, SDDM & Plymouth themes
```

---

## 4. Developer & Agent Workflow

### Running Commands
```bash
bb tui                    # Launch interactive Cyberpunk terminal dashboard
bb doctor                 # Run system health diagnostics
bb doctor --fix           # Auto-repair broken symlinks, stale cache & missing links
bb install --dry-run      # Preview full installation without changes
bb install                # Execute full installation
bb harvest                # Scrape active configurations from $HOME into repository
bb backup                 # Create timestamped configuration snapshot
bb rollback list          # List available rollback snapshots
bb rollback restore <id>  # Restore a snapshot to $HOME
bb diff                   # Visual line-by-line diff of repo templates vs $HOME
bb verify                 # Check system dependencies, layout widgets, and symlinks
bb theme list             # List available theme profiles
bb theme set <name>       # Switch active theme profile on the fly
bb kwin rules             # List declarative KWin window rules
bb kwin apply-rules       # Apply window transparency and borderless rules
bb kwin set-blur <val>    # Adjust KWin background blur strength (1-10)
bb fetch list             # List Fastfetch ASCII presets
bb fetch set <preset>     # Apply Fastfetch ASCII logo (NieR, Cyberpunk, Null-Sector, Arch)
bb profile detect         # Detect hardware environment (CPU/GPU/Form-factor)
bb profile apply          # Apply hardware-optimized widget settings
bb wallpaper set <name>   # Switch desktop and 4K video lockscreen wallpaper
bb audio preset <name>    # Switch audio equalizer preset
bb watch --once           # Check live configuration drift
bb daemon install-service # Install background systemd drift sentinel
bb bundle export          # Export portable rice bundle (.tar.gz)
bb plasmoid list          # List installed user plasmoids
bb dump-widgets           # Print containment and plasmoid tree
bb open-zen-mods          # Open Zen Mod install URLs in browser
```

### Running Tests & Linting
```bash
bb test                   # Run full Clojure test suite across all 18 modules
bb check                  # Run comprehensive validation routines
bb repl                   # Start nREPL server on port 1667
```

---

## 5. Guidelines for Future AI Agents & Contributors

1. **Maintain Pure Clojure / EDN Architecture:**
   * Keep configuration data in `rice.edn` as EDN data structures rather than hardcoded script strings.
   * Put command workflows in `src/mono_rice/cmd/` and reusable domain logic in dedicated namespaces (`mono_rice.*`).

2. **Idempotency & Non-Destructive Safety:**
   * Every file mutation MUST support `--dry-run` (`-n`).
   * Never overwrite user files without first triggering a snapshot via `mono-rice.fs/backup-configs!`.

3. **Keep Tests Green:**
   * Whenever adding features or modifying modules, add matching tests in `test/mono_rice/` and run `bb test`.
