# 🪐 Architecture & Future Phases Roadmap

## 1. Project Overview & Philosophy

`null-sector-plasma` is an automated, reproducible, and declarative KDE Plasma 6 desktop environment ("rice") for Arch Linux and CachyOS on Wayland, styled with a high-contrast cyberpunk and NieR: Automata aesthetic.

The codebase is driven by a pure functional **Clojure / Babashka** automation engine (`mono-rice`), replacing legacy shell and Python scripts.

---

## 2. Implementation State (Completed Phases 1–8)

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
│   ├── fs.clj                            # GNU Stow symlink engine, directory creation, backups
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
│       ├── install.clj                   # mono-rice install workflow
│       ├── harvest.clj                   # mono-rice harvest workflow
│       ├── backup.clj                    # mono-rice backup workflow
│       ├── verify.clj                    # mono-rice verify workflow
│       ├── theme.clj                     # mono-rice theme workflow
│       └── watch.clj                     # mono-rice watch drift sentinel
│
├── test/mono_rice/
│   ├── sanitizer_test.clj                # Unit tests for template replacement & panel pruning
│   ├── manifest_test.clj                 # Unit tests for rice.edn structure
│   ├── inspector_test.clj                # Unit tests for INI parsing
│   ├── theme_test.clj                    # Unit tests for theme switching and resolution
│   ├── scaling_test.clj                  # Unit tests for resolution scaling engine
│   └── watch_test.clj                    # Unit tests for configuration drift sentinel
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
bb install --dry-run      # Preview full installation without changes
bb install                # Execute full installation
bb harvest                # Scrape active configurations from $HOME into repository
bb backup                 # Create timestamped configuration snapshot
bb verify                 # Check system dependencies, layout widgets, and symlinks
bb theme list             # List available theme profiles
bb theme set <name>       # Switch active theme profile on the fly
bb watch --once           # Check live configuration drift
bb watch                  # Run continuous configuration drift sentinel
bb dump-widgets           # Print containment and plasmoid tree
bb open-zen-mods          # Open Zen Mod install URLs in browser
```

### Running Tests & Linting
```bash
bb test                   # Run full Clojure test suite across all modules
bb check                  # Run dry-run verification, backup checks, theme and drift validation
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
   * Whenever adding features or modifying sanitizer/inspector/theme/scaling logic, add matching tests in `test/mono_rice/` and run `bb test`.
