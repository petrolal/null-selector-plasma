# Kotlin/GraalVM Migration Plan

## Goal

Replace `install.sh`, `harvest.sh`, `backup.sh`, and `scripts/*.py` with a single
Kotlin CLI, compiled to a native binary with GraalVM native-image. Same
approach already validated on the sway dotfiles (Scala + GraalVM); this repo
gets the Kotlin version, targeting **GraalVM for JDK 25** (mainline Oracle
distribution, free to use since JDK 21 — no reason to reach for Mandrel or
Liberica NIK unless a specific build problem shows up).

Also introduces a declarative dependency manifest (`deps.json`) so package
installation stops being hand-rolled bash arrays and becomes a data-driven,
checksum-verified installer — same idea as Mason, lazy.nvim, or npm's
`package.json` + lockfile.

## Scope

### Migrates to Kotlin

| Current file | New home |
|---|---|
| `install.sh` (all functions) | `mono-rice install` + subcommands |
| `harvest.sh` / `backup.sh` | `mono-rice harvest` |
| `scripts/sanitize_appletsrc.py` | `mono-rice sanitize {harvest,install,verify}` (internal, called by the above) |
| `scripts/dump_widgets.py` | `mono-rice dump-widgets` |
| Pacman/AUR dependency arrays in `install.sh` | `deps.json` + `mono-rice deps {sync,verify,list}` (new subsystem, see below) |

### Does not migrate (by design, not by limitation)

- **Config/data files** — `plasma/`, `kvantum/`, `cava/`, `fastfetch/`,
  `starship/`, `zsh/`, `zen-browser/`, `assets/`, the cool-retro-term JSON
  profile. These are data, not code.
- **`plasma/layout.js`** — KDE's own DBus `evaluateScript` JS dialect, not our
  code. Already kept only as historical reference per its header comment;
  superseded by the `sanitize_appletsrc` template approach.
- **`bootstrap.sh`** — stays a tiny POSIX shell shim. On a fresh machine
  there's no JVM or native binary yet, so it can only `git clone` the repo and
  either build the tool (needs a JDK) or fetch a prebuilt release binary from
  GitHub Releases, then exec it. This constraint is language-independent.

### Stays as external process calls (not a migration gap)

Kotlin shells out to the same system tools bash does, via `ProcessBuilder`:
`pacman`, `yay`/`paru`, `kwriteconfig6`, `qdbus6`, `systemctl`, `kquitapp6`,
`plasma-apply-colorscheme`, `plasma-apply-cursortheme`, `git`, `rsync`,
`sqlite3` (CLI, already installed on this system — avoids pulling in a JDBC
SQLite driver and its native-image resource-extraction complications for one
INSERT). Reimplementing any of these in Kotlin would mean reimplementing
pacman's dependency resolution or KDE's own config-writing semantics, which
is out of scope and not a performance win.

## Performance

This tool is I/O- and subprocess-bound, not compute-bound — it spends its
time waiting on `pacman`, `rsync`, and file copies, the same as the bash
version does. GraalVM native-image gives ~10-20ms process startup, which is
in the same ballpark as bash's own startup cost, so there is no performance
regression versus the current scripts.

## Toolchain

- **Language:** Kotlin (2.4.x, already on this machine via SDKMAN)
- **Build:** Gradle (9.7.x, already installed) with the
  `org.graalvm.buildtools.native` plugin
- **Native image:** GraalVM for JDK 25 (needs installing via SDKMAN;
  currently only Corretto 25 is installed)
- **CLI framework:** [Clikt](https://ajalt.github.io/clikt/) — pure Kotlin,
  minimal reflection, well-proven under native-image, gives subcommands/flags
  parity with `install.sh`'s existing `-s/-d/-l/--no-sddm/...` flag set for
  free.
- **JSON:** kotlinx.serialization (reflection-free, native-image friendly) for
  `deps.json` and the deps lockfile.

## Project layout (proposed)

```
tool/
  build.gradle.kts
  settings.gradle.kts
  src/main/kotlin/rice/
    Main.kt                  # Clikt CliktCommand root + subcommand wiring
    cmd/
      InstallCommand.kt       # full install.sh main() workflow
      HarvestCommand.kt       # harvest.sh
      DumpWidgetsCommand.kt   # dump_widgets.py
      DepsCommand.kt          # deps sync/verify/list
    deps/
      DependencyManifest.kt   # deps.json data classes + loader
      DependencyLockfile.kt   # deps.lock.json data classes + loader/writer
      installers/
        Installer.kt          # interface: install(dep), isInstalled(dep), currentVersion(dep)
        PacmanInstaller.kt
        AurInstaller.kt
        GitInstaller.kt
        CurlInstaller.kt      # unused today, kept for future direct-download deps
    kde/
      KWriteConfig.kt         # thin wrapper over `kwriteconfig6` process calls
      PanelLayout.kt          # apply_panel_layout() equivalent
      Sanitizer.kt            # sanitize_appletsrc.py port (harvest/install/verify)
    fs/
      Symlinks.kt             # apply_symlinks() / deploy_components() equivalent
      Backup.kt                # backup_configs()
    proc/
      Process.kt              # ProcessBuilder wrapper: run(), runOrWarn(), requireTool()
  deps.json                    # dependency manifest (see below)
  deps.lock.json                # generated; last-verified installed state
```

## New subsystem: `deps.json` dependency manifest

### Why

`install_dependencies()` today is two bash arrays (`PACMAN_DEPS`,
`AUR_DEPS`) plus one inline `git clone` for the Kuro splash theme, all with
no version pinning and no integrity check beyond whatever pacman/the AUR
helper does on its own. Moving this to a manifest gets us:

- one file the app reads to know what to install, instead of logic baked
  into `install_dependencies()`
- explicit version pinning per dependency
- checksum verification for anything fetched directly (git/curl), the same
  guarantee Mason gives you for LSP/tool binaries
- a `deps list` / `deps verify` command that can report drift without
  reinstalling anything

### Schema — `deps.json`

```json
{
  "schemaVersion": 1,
  "dependencies": [
    {
      "name": "cool-retro-term",
      "installer": "pacman",
      "version": "latest"
    },
    {
      "name": "yamis-icon-theme-git",
      "installer": "aur",
      "version": "latest"
    },
    {
      "name": "kuro-splash",
      "installer": "git",
      "source": "https://github.com/bouteillerAlan/kuro.git",
      "version": "a1b2c3d4e5f6...",
      "installPath": "~/.local/share/plasma/look-and-feel",
      "checksum": null
    }
  ]
}
```

Field notes:

- **`installer`**: `"pacman" | "aur" | "git" | "curl"`. `curl` isn't used by
  any current dependency but is kept as a schema option for future
  direct-download assets (e.g. fetching a font zip without going through
  pacman).
- **`version`**:
  - `pacman`/`aur` → `"latest"` (matches current bash behavior — pacman's own
    repo signing is the integrity guarantee) or an exact version string if a
    dependency ever needs pinning.
  - `git` → a **pinned commit SHA**, not a branch. This is the actual
    integrity guarantee for git sources — a commit hash *is* a checksum. The
    current `git clone --depth 1` (a moving branch HEAD, no pin at all) is a
    regression this manifest fixes, not something it needs to preserve.
  - `curl` → the release/artifact version string.
- **`checksum`**: only meaningful for `curl`-installed artifacts (a plain
  file to hash). `null`/omitted for `pacman`, `aur`, and `git` — pacman
  verifies its own packages, and for `git` the pinned commit SHA already is
  the checksum. This field exists so a future `curl` dependency doesn't need
  a schema change.

### `deps.lock.json` (generated, not hand-edited)

Written after a successful `deps sync`, records what's actually installed:

```json
{
  "generatedAt": "2026-09-22T10:00:00Z",
  "installed": [
    { "name": "cool-retro-term", "installer": "pacman", "resolvedVersion": "1.2.0-1" },
    { "name": "kuro-splash", "installer": "git", "resolvedVersion": "a1b2c3d4e5f6..." }
  ]
}
```

Lets `deps verify` answer "does the system match what we last confirmed
installed" without re-querying pacman/re-cloning every time, mirroring how
`package-lock.json`/`mason-registry.json` work.

### Installer interface

```kotlin
interface Installer {
    fun isInstalled(dep: Dependency): Boolean
    fun install(dep: Dependency, dryRun: Boolean)
    fun resolvedVersion(dep: Dependency): String?
}
```

- `PacmanInstaller` — `pacman -Qi <name>` to check, `sudo pacman -S --needed
  --noconfirm <name>` to install (current behavior, unchanged).
- `AurInstaller` — same shape via the detected AUR helper (`yay`/`paru`),
  preserving the current `-git`/`-bin` suffix-stripping check.
- `GitInstaller` — clone (or fetch+checkout if already cloned) the pinned
  commit SHA into a cache dir, then copy/symlink into `installPath`.
- `CurlInstaller` — download, verify `checksum`, extract/place. Not wired to
  any dependency yet.

### New CLI surface

- `mono-rice deps sync` — installs everything in `deps.json` not already
  satisfied (replaces `install_dependencies()`), writes `deps.lock.json`.
- `mono-rice deps verify` — checks installed state against the manifest/lock
  without installing; non-zero exit on drift (useful in CI or a "doctor"
  check).
- `mono-rice deps list` — prints a table: name, installer, wanted version,
  installed version, status.

## Command mapping (full CLI surface)

| Old | New |
|---|---|
| `./install.sh` | `mono-rice install` |
| `./install.sh -s` | `mono-rice install --symlinks-only` |
| `./install.sh -d` | `mono-rice deps sync` |
| `./install.sh -l` / `--no-layout` | `mono-rice install --apply-layout` / `--no-layout` (flag passthrough, unchanged) |
| `./install.sh --sddm` / `--no-sddm` | same flags on `mono-rice install` |
| `./install.sh --plymouth` / `--no-plymouth` | same flags on `mono-rice install` |
| `./install.sh -n` (dry-run) | `mono-rice install --dry-run` |
| `./harvest.sh` / `./backup.sh` | `mono-rice harvest` |
| `python3 scripts/dump_widgets.py` | `mono-rice dump-widgets` |
| `python3 scripts/sanitize_appletsrc.py {harvest,install,verify}` | `mono-rice sanitize {harvest,install,verify}` (also called internally by `install`/`harvest`) |

`bootstrap.sh` is updated to fetch/build the `mono-rice` binary and exec it
with passthrough args, replacing its current `exec ./install.sh "$@"` tail.

## Migration phases

1. **Scaffold** — Gradle project, Clikt root command, native-image build
   working end-to-end on a no-op `mono-rice --help`.
2. **`sanitize` + `dump-widgets`** — smallest, most self-contained pieces
   (pure text/regex, no process orchestration). Good correctness baseline
   since they're easy to diff against the Python originals on real config
   files.
3. **`deps` subsystem** — write `deps.json` from the current bash arrays,
   implement `PacmanInstaller`/`AurInstaller`/`GitInstaller`, `deps sync` /
   `verify` / `list`.
4. **`harvest`** — port `harvest_file`/`harvest_dir`, wire in `sanitize`.
5. **`install`** — the rest: backup, symlinks, deploy_components, KDE
   settings, SDDM/Plymouth, panel layout. Largest piece, done last since it
   depends on `deps` and `sanitize` already existing.
6. **Cutover** — `bootstrap.sh` points at the compiled binary; old
   `install.sh`/`harvest.sh`/`backup.sh`/`scripts/*.py` removed.

Each phase should be diffed against the bash/python output on this actual
machine before moving on — `--dry-run` output and `deps list` are the
easiest things to compare directly.

## Open decisions

- Whether `mono-rice` ships as a GitHub Release binary that `bootstrap.sh`
  downloads, or whether `bootstrap.sh` builds it locally with a bundled/
  installed GraalVM. Affects how `bootstrap.sh` is written in phase 6.
- Whether `deps.json`'s AUR helper detection stays runtime-only (as today)
  or becomes a manifest field (`preferredHelper`).
