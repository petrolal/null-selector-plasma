// ==============================================================================
// Plasma 6 Multi-Screen Layout Script: Plasma Monochrome Rice (Cyberpunk / NieR)
// ==============================================================================
// Evaluated via:
// qdbus6 org.kde.plasmashell /PlasmaShell org.kde.PlasmaShell.evaluateScript "$(< layout.js)"
// ==============================================================================

var homeDir = "/home/petrolal";
var videoFile =
  "file://" + homeDir + "/.local/share/wallpapers/synthwave-dreamwave-girl.mp4";
var imageFile =
  "file://" + homeDir + "/.local/share/wallpapers/synthwave-dreamwave-girl.png";

var videoConfigObj = [
  {
    filename: videoFile,
    enabled: true,
    duration: 0,
    customDuration: 0,
    playbackRate: 0.0,
    alternativePlaybackRate: 0.0,
    loop: false,
    dayNightPhase: 4,
  },
];

// Helper: adds an invisible spacer with fixed pixel width (no black separator bar)
function addInvisibleGap(panel, width) {
  var gap = panel.addWidget("org.kde.plasma.panelspacer");
  if (gap) {
    gap.currentConfigGroup = ["General"];
    gap.writeConfig("expanding", false);
    gap.writeConfig("length", width || 12);
    gap.reloadConfig();
  }
  return gap;
}

// -----------------------------------------------------------------------------
// 1. Multi-Screen Wallpaper Application (Smart Video Wallpaper)
// -----------------------------------------------------------------------------
var allDesktops = desktops();
for (var d = 0; d < allDesktops.length; d++) {
  var desk = allDesktops[d];
  desk.wallpaperPlugin = "luisbocanegra.smart.video.wallpaper.reborn";
  desk.currentConfigGroup = [
    "Wallpaper",
    "luisbocanegra.smart.video.wallpaper.reborn",
    "General",
  ];
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
// 3. Primary Screen (Screen 0): Top Bar (Upper Left Info + Upper Right Pill)
// -----------------------------------------------------------------------------
var topPanel = new Panel();
topPanel.screen = 0;
topPanel.location = "top";
topPanel.height = 36;
topPanel.floating = true;
topPanel.opacity = "translucent";

// Top Colorizer (Must be loaded first for styling rules)
var topColorizer = topPanel.addWidget("luisbocanegra.panel.colorizer");
if (topColorizer) {
  topColorizer.currentConfigGroup = ["Configuration", "General"];
  topColorizer.writeConfig("hideWidget", true);
  topColorizer.writeConfig("showInPanel", false);
  topColorizer.writeConfig("editMode", false);
  topColorizer.reloadConfig();
}

// --- UPPER LEFT: System Telemetry / Info ---
topPanel.addWidget("org.kde.plasma.systemmonitor.net");
addInvisibleGap(topPanel, 6);

topPanel.addWidget("org.kde.plasma.systemmonitor.cpu");
addInvisibleGap(topPanel, 6);

topPanel.addWidget("org.kde.plasma.systemmonitor.memory");
addInvisibleGap(topPanel, 6);

topPanel.addWidget("org.kde.plasma.systemmonitor.diskusage");

// --- CENTER: Expanding Spacer (Pushes Upper Right Pill) ---
topPanel.addWidget("org.kde.plasma.panelspacer");

// --- UPPER RIGHT: Controls & Clock Pill ---
topPanel.addWidget("org.kde.plasma.catwalkEnhanced");
addInvisibleGap(topPanel, 6);

topPanel.addWidget("KdeControlStation");
addInvisibleGap(topPanel, 6);

var clock = topPanel.addWidget("org.kde.plasma.digitalclock");
if (clock) {
  clock.currentConfigGroup = ["Appearance"];
  clock.writeConfig("showDate", true);
  clock.writeConfig("dateFormat", "shortDate");
  clock.writeConfig("use24hFormat", 2);
  clock.writeConfig("fontWeight", 500);
  clock.reloadConfig();
}

// -----------------------------------------------------------------------------
// 4. Primary Screen (Screen 0): Bottom Dock (3-Island Layout)
// -----------------------------------------------------------------------------
var bottomPanel = new Panel();
bottomPanel.screen = 0;
bottomPanel.location = "bottom";
bottomPanel.height = 46;
bottomPanel.floating = true;
bottomPanel.opacity = "translucent";

// Bottom Colorizer
var bottomColorizer = bottomPanel.addWidget("luisbocanegra.panel.colorizer");
if (bottomColorizer) {
  bottomColorizer.currentConfigGroup = ["Configuration", "General"];
  bottomColorizer.writeConfig("hideWidget", true);
  bottomColorizer.writeConfig("showInPanel", false);
  bottomColorizer.writeConfig("editMode", false);
  bottomColorizer.reloadConfig();
}

// --- ISLAND 1 (LEFT): Kickoff Launcher & Task Manager ---
var kickoff = bottomPanel.addWidget("org.kde.plasma.kickoff");
if (kickoff) {
  kickoff.currentConfigGroup = ["General"];
  kickoff.writeConfig("icon", "archlinux-logo");
  kickoff.reloadConfig();
}

addInvisibleGap(bottomPanel, 6);

var taskbar = bottomPanel.addWidget("org.kde.plasma.icontasks");
if (taskbar) {
  taskbar.currentConfigGroup = ["General"];
  taskbar.writeConfig("launchers", [
    "applications:cool-retro-term.desktop",
    "preferred://browser",
    "preferred://filemanager",
    "applications:systemsettings.desktop",
    "applications:com.spotify.Client.desktop",
    "applications:discord.desktop",
  ]);
  taskbar.reloadConfig();
}

// --- PUSH TO CENTER ---
bottomPanel.addWidget("org.kde.plasma.panelspacer");

// --- ISLAND 2 (CENTER): Pager ---
bottomPanel.addWidget("org.kde.plasma.pager");

// --- PUSH TO RIGHT ---
bottomPanel.addWidget("org.kde.plasma.panelspacer");

// --- ISLAND 3 (RIGHT): System Tray & Trash ---
bottomPanel.addWidget("org.kde.plasma.systemtray");
addInvisibleGap(bottomPanel, 8);
bottomPanel.addWidget("org.kde.plasma.trash");

// -----------------------------------------------------------------------------
// 5. Desktop Telemetry & Widgets (Screen 0 HUD)
// -----------------------------------------------------------------------------
if (allDesktops.length > 0) {
  var primaryDesk = allDesktops[0];

  // Mid-Left: CAVA Audio Visualizer (Kurve / Audio Visualizer)
  var cavaWidget = primaryDesk.addWidget("luisbocanegra.audio.visualizer");
  if (cavaWidget) {
    cavaWidget.currentConfigGroup = ["General"];

    // Geometry & Layout
    cavaWidget.writeConfig("orientation", 1); // 1 = Vertical (bars expand horizontally)
    cavaWidget.writeConfig("alignment", 0); // Align against the left border
    cavaWidget.writeConfig("barWidth", 2); // Dot width
    cavaWidget.writeConfig("barSpacing", 3); // Spacing between dot columns
    cavaWidget.writeConfig("barSegments", 32); // Dotted/matrix segmentation
    cavaWidget.writeConfig("segmentSpacing", 2); // Vertical gap between dots

    // Style & Colors (Monochrome White Cyberpunk)
    cavaWidget.writeConfig("drawStyle", 1); // 1 = Dotted / Segmented matrix
    cavaWidget.writeConfig("colorMode", 0); // 0 = Single solid color
    cavaWidget.writeConfig("customColor", "#E0E0E0"); // Crisp off-white / light grey
    cavaWidget.writeConfig("opacity", 0.85);

    // Audio Input & Responsiveness
    cavaWidget.writeConfig("audioBackend", "pipewire"); // Or "pulseaudio"
    cavaWidget.writeConfig("framerate", 60);
    cavaWidget.writeConfig("sensitivity", 100);
    cavaWidget.writeConfig("smoothSpectrum", true);

    cavaWidget.reloadConfig();
  }

  // Bottom-Left: Binary Clock Matrix (Dotted Indicator Grid)
  var binClock = primaryDesk.addWidget("org.kde.plasma.binaryclock");
  if (binClock) {
    binClock.currentConfigGroup = ["Appearance"];
    binClock.writeConfig("showSeconds", true);
    binClock.writeConfig("activeColor", "#FFFFFF");
    binClock.writeConfig("inactiveColor", "#3A3A3A");
    binClock.reloadConfig();
  }

  // Bottom-Left / Center: ClearClock & HUD Elements
  primaryDesk.addWidget("org.kde.plasma.clearclock");

  // Right Side: YoRHa Telemetry & Thermal HUD
  primaryDesk.addWidget("com.axzoros.yorhahud");
  primaryDesk.addWidget("org.kde.olib.thermalmonitor");
}
