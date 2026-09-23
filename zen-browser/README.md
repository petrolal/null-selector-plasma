# 🌐 Zen Browser Monochrome Transparency Setup

Based on [sameerasw.com/zen](https://sameerasw.com/zen), adapted for KWin/Plasma 6.

### 1. userChrome.css, userContent.css & user.js
```css
/* userChrome.css */
#browser {background-color: #40404066;}
```
```css
/* userContent.css -- transparent about:blank/about:newtab/about:home so they
   don't flash opaque on top of the transparent chrome. Bonjourr overrides
   about:newtab once installed; this covers what it doesn't touch. */
@-moz-document url(about:blank), url(about:newtab), url(about:home) {
  html, body { background-color: transparent !important; }
}
```
`user.js` sets the actual transparency prefs (`zen.widget.linux.transparency`,
`widget.transparent-windows`, `zen.theme.gradient.show-custom-colors`, the
`mod.sameerasw.*` Transparent Zen mod settings, etc.) — see §3 for why these
live here and not in `policies.json`.

*(Automated: all three symlinked into every profile listed in
`~/.config/zen/profiles.ini` by `install.sh`. Note this is `~/.config/zen/`,
not the legacy `~/.zen/` dotfolder most Firefox guides assume --
zen-browser-bin resolves its default profile via `~/.config/zen/installs.ini`.
If no profile exists yet, `install.sh` bootstraps one non-interactively via
`zen-browser --headless -CreateProfile`.)*

**Critical prerequisite:** neither CSS file loads at all without
`toolkit.legacyUserProfileCustomizations.stylesheets = true` — a hard Firefox
requirement since v69. This was missing the entire time userChrome.css was
being correctly deployed but never visibly rendering; it's now set via
`policies.json` alongside the extensions below.

### 2. KWin Better Blur DX
`kwin-effects-forceblur` was removed from the AUR; its maintained successor is
[kwin-effects-better-blur-dx](https://github.com/xarblu/kwin-effects-better-blur-dx)
(`-x11` variant on X11 sessions), which is what actually makes Zen's
transparency mod render correctly.
- Force-blurs `zen`-classed windows: strength 4, noise 5, brightness 25%, saturation 0%, contrast 105%.

*(Automated: package installed by `install.sh`'s dependency step, effect enabled and configured by `apply_kde_settings()`.)*

**One unavoidable manual step the first time:** KF6 KWin effect plugins embed
their metadata in the `.so` itself and are only discovered when KWin's own
process starts — `qdbus6 org.kde.KWin /KWin reconfigure` re-evaluates the
enabled list among effects it already knows about, but can't discover one
installed after the running KWin process started. `install.sh` detects this
(`isEffectLoaded better_blur_dx` returns false) and warns you, but the actual
fix is just **log out and back in once** (or reboot) — there's no config trick
around it.

### 3. Extensions (policies.json) & Transparency Prefs (user.js)
Extensions are force-installed via Zen's `distribution/policies.json`
(`ExtensionSettings` + `installation_mode: force_installed`, using Mozilla's
evergreen `.../downloads/latest/<slug>/latest.xpi` URLs so they never need
re-pinning). `policies.json`'s `Preferences` block also sets
`toolkit.legacyUserProfileCustomizations.stylesheets` — required for
userChrome.css/userContent.css to load at all.

**That's the only pref set via `policies.json`.** Every `zen.*` /
`mod.sameerasw.*` transparency pref used to live there too, and looked
correct — but transparency still didn't render even after a full profile
restart. Root-caused by diffing the live profile's `prefs.js` against Zen's
own compiled-in defaults (extracted from `omni.ja`'s
`defaults/preferences/firefox.js`): every one of those keys was completely
absent from `prefs.js`, meaning the policy engine silently dropped them.
Firefox's enterprise `Preferences` policy only recognizes an internal
allowlist of standard Firefox prefs — `toolkit.legacyUserProfileCustomizations
.stylesheets` is on it (it's Mozilla's own documented example), custom
`zen.*`/`mod.*` names are not, and the policy engine drops unrecognized names
without erroring.

Fix: those prefs now live in `zen-browser/user.js`, symlinked into each
profile's root next to `chrome/`. `user.js` has no allowlist — Firefox-family
browsers apply every line in it, unconditionally, on every startup:
- `browser.tabs.allow_transparent_browser`, `widget.transparent-windows`, `zen.theme.gradient.show-custom-colors`, `zen.widget.linux.transparency`, `zen.view.grey-out-inactive-windows` — general transparency (sameerasw.com/zen)
- `zen.theme.content-element-separation = 0` — removes the border around the browser window ([docs.zen-browser.app](https://docs.zen-browser.app/guides/about-config-flags), default is `8`)
- `zen.view.compact.show-sidebar-and-toolbar-on-hover = false` — disables Compact Mode's hover-to-expand sidebar ([zen-browser/desktop#3760](https://github.com/zen-browser/desktop/discussions/3760))
- `mod.sameerasw.*` — Transparent Zen mod settings (schema: `theme-store/themes/<uuid>/preferences.json`), mirroring `~/Downloads/older_rice/transparent-zen-settings.png` exactly; only `zen_no_shadow` and `zen_urlbar_zoom_anim` differ from the mod's own schema defaults

Deliberately **not** set: the video's "`browser.compactmode.show` / density = 1"
— that's a legacy Firefox toolbar-density pref unrelated to Zen's actual
Compact Mode feature, and no equivalent Zen pref for "enable compact mode"
exists in the official flag docs. Setting an unverified pref name just
creates dead config that does nothing, so it's left out rather than guessed.

*(Automated: `install.sh`'s `configure_zen_extensions()` writes `policies.json`
(requires sudo since the file lives under `/opt/zen-browser-bin/`) and
`deploy_components()` symlinks `user.js` alongside the CSS files. Re-run
`./install.sh` after any `zen-browser-bin` package update — `policies.json`
isn't a pacman backup file, so an update silently resets it; `user.js` is a
symlink so it survives package updates untouched.)*

### 4. Workspace Gradient De-Colorization
Zen assigns each newly-created Workspace a random-ish gradient accent color on
first launch (a warm cream/beige, in the case that motivated this) —
completely separate from everything above: not a pref, not `policies.json`,
not `user.js`. It's per-workspace state Zen writes into its own session store,
`<profile>/zen-sessions.jsonlz4` (Mozilla's "mozLz4" format — an 8-byte magic
+ 4-byte LE size prefix around a raw LZ4 *block*, not the frame format the
`lz4` CLI speaks, hence decompressing/recompressing via `liblz4` through
`ctypes` in Python). Traced through Zen's own `ZenGradientGenerator.mjs`:
`theme.gradientColors[].c` on each workspace is the only source of this
color — there's no about:config flag for it.

Every gradient dot with mismatched R/G/B channels gets replaced by a neutral
gray at the same average luminance (same visual weight, no hue).

*(Automated: `install.sh`'s `configure_zen_workspace_theme()`. Only runs when
Zen isn't running — it owns this file and autosaves over it, so patching it
live races the browser and gets silently reverted. Quit Zen first, then
re-run `./install.sh` if it warns and skips. Backs up the original file once,
as `zen-sessions.jsonlz4.bak-pre-monochrome`.)*

### 5. KWin Window Rule (Borderless)
A `~/.config/kwinrulesrc` entry force-removes window decorations on
`zen`-classed windows, so a titlebar/border doesn't break up the transparent
chrome. Written idempotently (won't duplicate on re-runs, won't clobber any
window rules you already have for other apps).

*(Automated: `install.sh`'s `configure_zen_kwin_rule()`, no sudo needed — it's a per-user config file.)*

### 6. Zen Mods
- **Transparent Zen** — pre-configured (see above), install it first
- Animations Plus
- Audio Indicator Enhanced
- Better Find Bar
- Floating History
- Floating Status Bar
- Load Bar

Zen mods are documented as UI-install-only — confirmed upstream
([zen-browser/desktop discussion #4097](https://github.com/zen-browser/desktop/discussions/4097));
even Zen's own local-JSON "Import Mods" feature is currently broken
([issue #8789](https://github.com/zen-browser/desktop/issues/8789)).

**However**, `configure_zen_mods_registry()` (experimental) found that an
already-installed mod's entry in `<profile>/zen-themes.json` is just static
catalog metadata plus `"enabled": true` — no user-specific data. It seeds that
file with any of the 7 required mods not already present, for every profile,
non-destructively (never touches an already-installed mod, never touches the
generated `chrome/zen-themes.css`). What's unverified: whether Zen regenerates
that CSS automatically on next launch, or only when
`about:preferences#zenMarketplace` is opened. If a mod shows as installed but
its effect isn't visible after restarting Zen, open that page once.

Fallback / manual verification:
```bash
./scripts/open_zen_mods.sh
```
opens each mod's install page in its own tab — click "Install" on each (7 clicks total).

**Configuring an already-installed mod is automatable, though** — a mod's settings
panel is just a UI over plain `about:config` prefs (schema published at
`theme-store/themes/<uuid>/preferences.json`). `zen-browser/user.js` (see §3)
sets Transparent Zen's entire preference set to match the reference config in
`~/Downloads/older_rice/transparent-zen-settings.png` (`zen.widget.linux.transparency`,
`mod.sameerasw.*`, etc.) — so by the time you click "Install" on it, it's
already configured; there's no follow-up settings step left, just the one click.
