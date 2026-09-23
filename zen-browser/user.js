// Zen theme-engine + Transparent Zen mod prefs (sameerasw.com/zen reference config).
//
// These do NOT go through policies.json's "Preferences" block: empirically
// confirmed (by diffing a live profile's prefs.js against the compiled-in
// defaults extracted from Zen's own omni.ja) that Firefox's enterprise
// Preferences policy silently drops any pref name it doesn't recognize as a
// standard/allowlisted Firefox pref. Only toolkit.legacyUserProfileCustomizations
// .stylesheets (a well-known, documented policy pref) actually landed when
// this whole set was pushed via policies.json -- every zen.* and
// mod.sameerasw.* key here was silently ignored, which is why transparency
// never rendered even though policies.json looked correct.
//
// user.js has no such allowlist -- Firefox-family browsers apply every line
// in it, unconditionally, on every startup. That makes it the reliable
// delivery path for anything custom, including these.

// General transparency (sameerasw.com/zen)
user_pref("browser.tabs.allow_transparent_browser", true);
user_pref("widget.transparent-windows", true);
user_pref("zen.theme.gradient.show-custom-colors", true);
user_pref("zen.widget.linux.transparency", true);
user_pref("zen.view.grey-out-inactive-windows", false);

// Force Dark UI theme and content scheme
user_pref("layout.css.prefers-color-scheme.content", 0);
user_pref("browser.theme.toolbar-theme", 0);
user_pref("browser.theme.content-theme", 0);

// Verified against docs.zen-browser.app/guides/about-config-flags
user_pref("zen.theme.content-element-separation", 0);
user_pref("zen.view.compact.show-sidebar-and-toolbar-on-hover", false);

// Transparent Zen mod settings (schema: theme-store/themes/<uuid>/preferences.json)
// -- mirrors ~/Downloads/older_rice/transparent-zen-settings.png exactly.
user_pref("mod.sameerasw.zen_transparent_sidebar_enabled", true);
user_pref("mod.sameerasw.zen_transparent_glance_enabled", true);
user_pref("mod.sameerasw.zen_bg_color_enabled", true);
user_pref("mod.sameerasw.zen_transparency_color", "#00000000");
user_pref("mod.sameerasw_zen_light_tint", "2");
user_pref("mod.sameerasw.zen_no_shadow", true);
user_pref("mod.sameerasw.zen_bg_img_enabled", false);
user_pref("mod.sameerasw.zen_bg_img_not_fullscreen", false);
user_pref("mod.sameerasw.zen_bg_opacity", "0.8");
user_pref("mod.sameerasw.zen_bg_blur", "3px");
user_pref("mod.sameerasw_zen_empty_tab_logo", "0");
user_pref("mod.sameerasw.zen_notab_img_size", "150px");
user_pref("mod.sameerasw.zen_notab_img_opacity", "1");
user_pref("mod.sameerasw_zen_compact_sidebar_type", "0");
user_pref("mod.sameerasw.zen_compact_sidebar_width", "165px");
user_pref("mod.sameerasw.zen_tab_switch_anim", true);
user_pref("mod.sameerasw.zen_urlbar_zoom_anim", true);
user_pref("mod.sameerasw.zen_trackpad_anim", false);
user_pref("mod.sameerasw_zen_animations", "1");
