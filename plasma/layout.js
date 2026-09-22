// ==============================================================================
// Plasma 6 Multi-Screen Layout Script: Plasma Monochrome Rice (Cyberpunk / NieR)
// ==============================================================================
// Evaluated via:
// qdbus6 org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "$(< layout.js)"
// ==============================================================================

var homeDir = "/home/petrolal";
var videoFile = "file://" + homeDir + "/.local/share/wallpapers/synthwave-dreamwave-girl.mp4";
var imageFile = "file://" + homeDir + "/.local/share/wallpapers/synthwave-dreamwave-girl.png";

var videoConfigObj = [
    {
        "filename": videoFile,
        "enabled": true,
        "duration": 0,
        "customDuration": 0,
        "playbackRate": 0.0,
        "alternativePlaybackRate": 0.0,
        "loop": false,
        "dayNightPhase": 4
    }
];

// -----------------------------------------------------------------------------
// 1. Multi-Screen Wallpaper Application (Smart Video Wallpaper)
// -----------------------------------------------------------------------------
var allDesktops = desktops();
for (var d = 0; d < allDesktops.length; d++) {
    var desk = allDesktops[d];
    desk.wallpaperPlugin = "luisbocanegra.smart.video.wallpaper.reborn";
    desk.currentConfigGroup = ["Wallpaper", "luisbocanegra.smart.video.wallpaper.reborn", "General"];
    desk.writeConfig("VideoUrls", JSON.stringify(videoConfigObj));
    desk.writeConfig("LastVideo", videoFile);
    desk.writeConfig("FillMode", 2);
    desk.writeConfig("MuteMode", 5);
    desk.reloadConfig();
}

// -----------------------------------------------------------------------------
// 2. Clear Existing Panels & Desktop Widgets (Prevent Duplication)
// -----------------------------------------------------------------------------
var allPanels = panels();
for (var i = 0; i < allPanels.length; i++) {
    allPanels[i].remove();
}

for (var d = 0; d < allDesktops.length; d++) {
    var ws = allDesktops[d].widgets();
    for (var w = ws.length - 1; w >= 0; w--) {
        ws[w].remove();
    }
}

// -----------------------------------------------------------------------------
// 3. Primary Screen (Screen 0): Top Status Bar & Bottom Application Dock
// -----------------------------------------------------------------------------

// --- Top Panel (Status & System Monitors) ---
var topPanel = new Panel();
topPanel.screen = 0;
topPanel.location = "top";
topPanel.height = 34;
topPanel.floating = true;
topPanel.opacity = "translucent";

// Left: System Monitors
var cpuMon = topPanel.addWidget("org.kde.plasma.systemmonitor.cpu");
var memMon = topPanel.addWidget("org.kde.plasma.systemmonitor.memory");
var netMon = topPanel.addWidget("org.kde.plasma.systemmonitor.net");

// Expanding Spacer
topPanel.addWidget("org.kde.plasma.panelspacer");

// Right: CatWalk, Control Station, Clock, Panel Colorizer
topPanel.addWidget("org.kde.plasma.catwalkEnhanced");
topPanel.addWidget("KdeControlStation");

var clock = topPanel.addWidget("org.kde.plasma.digitalclock");
if (clock) {
    clock.currentConfigGroup = ["Appearance"];
    clock.writeConfig("showDate", true);
    clock.writeConfig("dateFormat", "shortDate");
    clock.writeConfig("use24hFormat", 2);
    clock.writeConfig("fontWeight", 500);
    clock.reloadConfig();
}

// Panel Colorizer (Loads "Main Setup" / "Main Blur" island presets)
topPanel.addWidget("luisbocanegra.panel.colorizer");

// --- Bottom Panel (Launcher, Dock & Tray) ---
var bottomPanel = new Panel();
bottomPanel.screen = 0;
bottomPanel.location = "bottom";
bottomPanel.height = 48;
bottomPanel.floating = true;
bottomPanel.opacity = "translucent";

// Application Launcher
var kickoff = bottomPanel.addWidget("org.kde.plasma.kickoff");
if (kickoff) {
    kickoff.currentConfigGroup = ["General"];
    kickoff.writeConfig("icon", "archlinux-logo");
    kickoff.reloadConfig();
}

// Task Manager (Monochrome Icons)
var taskbar = bottomPanel.addWidget("org.kde.plasma.icontasks");
if (taskbar) {
    taskbar.currentConfigGroup = ["General"];
    taskbar.writeConfig("launchers", [
        "applications:cool-retro-term.desktop",
        "preferred://browser",
        "preferred://filemanager",
        "applications:systemsettings.desktop",
        "applications:com.spotify.Client.desktop",
        "applications:discord.desktop"
    ]);
    taskbar.reloadConfig();
}

// Expanding Spacer
bottomPanel.addWidget("org.kde.plasma.panelspacer");

// Pager & Media
bottomPanel.addWidget("org.kde.plasma.pager");
bottomPanel.addWidget("org.kde.plasma.mediacontroller");

// System Tray
bottomPanel.addWidget("org.kde.plasma.systemtray");

// Trash Widget
bottomPanel.addWidget("org.kde.plasma.trash");

// Panel Colorizer for Bottom Panel
bottomPanel.addWidget("luisbocanegra.panel.colorizer");

// -----------------------------------------------------------------------------
// 4. Desktop Telemetry & Widgets (Screen 0)
// -----------------------------------------------------------------------------
if (allDesktops.length > 0) {
    var primaryDesk = allDesktops[0];

    // Left Desktop: ClearClock / Digital Clock
    primaryDesk.addWidget("org.kde.plasma.clearclock");

    // Left Desktop: Kurve Audio Visualizer (CAVA)
    primaryDesk.addWidget("luisbocanegra.audio.visualizer");

    // Right Desktop: YoRHa HUD (NieR: Automata Telemetry)
    primaryDesk.addWidget("com.axzoros.yorhahud");

    // Right Desktop: Thermal Monitor
    primaryDesk.addWidget("org.kde.olib.thermalmonitor");
}
