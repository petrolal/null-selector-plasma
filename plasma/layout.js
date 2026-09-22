// ==============================================================================
// Plasma 6 Complete Multi-Screen Rice Layout (Accurate Bars & Desktop Canvas)
// ==============================================================================
// DEPRECATED / HISTORICAL REFERENCE ONLY. Do not evaluate this via DBus.
//
// evaluateScript ignores currentConfigGroup/writeConfig on desktop applets in
// Plasma 6, mangles large nested JSON configs (e.g. panel-colorizer), and
// races with plasmashell's own writes -- it reliably corrupted panel state.
//
// The layout it describes is now deployed deterministically by harvest.sh
// (scrapes the live, working config into plasma/.config/) and install.sh's
// apply_panel_layout() (stops plasmashell, copies the tracked
// plasma-org.kde.plasma.desktop-appletsrc / plasmashellrc into ~/.config,
// restarts plasmashell). See scripts/sanitize_appletsrc.py for the
// machine-portability templating (home path, activity UUID) that makes those
// tracked files reproducible on a fresh install.
// ==============================================================================

var homeDir = "/home/petrolal";
var videoFile =
  "file://" + homeDir + "/.local/share/wallpapers/synthwave-dreamwave-girl.mp4";
var targetScreen = 0;

// -----------------------------------------------------------------------------
// 1. Wallpaper Setup
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
  desk.writeConfig(
    "VideoUrls",
    JSON.stringify([
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
    ]),
  );
  desk.writeConfig("LastVideo", videoFile);
  desk.writeConfig("FillMode", 2);
  desk.writeConfig("MuteMode", 5);
  desk.reloadConfig();
}

// -----------------------------------------------------------------------------
// 2. Clear Existing State
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

// Helper: adds an invisible spacer with fixed pixel width
function addGap(panel, length) {
  var spacer = panel.addWidget("org.kde.plasma.panelspacer");
  if (spacer) {
    spacer.currentConfigGroup = ["Configuration", "General"];
    spacer.writeConfig("expanding", false);
    spacer.writeConfig("length", length || 4);
    spacer.reloadConfig();
  }
  return spacer;
}

// -----------------------------------------------------------------------------
// 3. Top Status Bar (Telemetry Left + Controls Right)
// -----------------------------------------------------------------------------
var topPanelHeight = 36;
var topPanel = new Panel();
topPanel.screen = targetScreen;
topPanel.location = "top";
topPanel.height = topPanelHeight;
topPanel.floating = true;
topPanel.opacity = "translucent";

// Top Colorizer
var topColorizer = topPanel.addWidget("luisbocanegra.panel.colorizer");
if (topColorizer) {
  topColorizer.currentConfigGroup = ["Configuration", "General"];
  topColorizer.writeConfig("editmode", false);
  topColorizer.writeConfig("hidewidget", true);
  topColorizer.writeConfig("showinpanel", false);
  topColorizer.reloadConfig();
}

// Left Capsule: System Telemetry
var netMon = topPanel.addWidget("org.kde.plasma.systemmonitor.net");
if (netMon) {
  netMon.currentConfigGroup = ["Configuration", "Appearance"];
  netMon.writeConfig("chartface", "org.kde.ksysguard.linechart");
  netMon.writeConfig("title", "Network Speed");
  netMon.reloadConfig();
}
addGap(topPanel, 4);

var cpuMon = topPanel.addWidget("org.kde.plasma.systemmonitor.cpu");
if (cpuMon) {
  cpuMon.currentConfigGroup = ["Configuration", "Appearance"];
  cpuMon.writeConfig("chartface", "org.kde.ksysguard.piechart");
  cpuMon.writeConfig("title", "Total CPU Use");
  cpuMon.reloadConfig();
}
addGap(topPanel, 4);

var memMon = topPanel.addWidget("org.kde.plasma.systemmonitor.memory");
if (memMon) {
  memMon.currentConfigGroup = ["Configuration", "Appearance"];
  memMon.writeConfig("chartface", "org.kde.ksysguard.piechart");
  memMon.writeConfig("title", "Memory Usage");
  memMon.reloadConfig();
}
addGap(topPanel, 4);

var diskMon = topPanel.addWidget("org.kde.plasma.systemmonitor.diskusage");
if (diskMon) {
  diskMon.currentConfigGroup = ["Configuration", "Appearance"];
  diskMon.writeConfig("chartface", "org.kde.ksysguard.piechart");
  diskMon.writeConfig("title", "Disk Usage");
  diskMon.reloadConfig();
}

// Expanding Spacer (Pushes the right pill to the border)
var topCenterSpacer = topPanel.addWidget("org.kde.plasma.panelspacer");
if (topCenterSpacer) {
  topCenterSpacer.currentConfigGroup = ["Configuration", "General"];
  topCenterSpacer.writeConfig("expanding", true);
  topCenterSpacer.reloadConfig();
}

// Right Capsule: CatWalk, Clock & KDE Control Station
var catwalk = topPanel.addWidget("org.kde.plasma.catwalkEnhanced");
if (catwalk) {
  catwalk.currentConfigGroup = ["Configuration", "General"];
  catwalk.writeConfig("catscale", "0.75");
  catwalk.writeConfig("dividerthickness", 4);
  catwalk.writeConfig("linkscales", false);
  catwalk.writeConfig("type", 1);
  catwalk.writeConfig("updateratelimit", 8000);
  catwalk.reloadConfig();
}
addGap(topPanel, 6);

var clock = topPanel.addWidget("org.kde.plasma.digitalclock");
if (clock) {
  clock.currentConfigGroup = ["Configuration", "Appearance"];
  clock.writeConfig("autofontandsize", false);
  clock.writeConfig("fontfamily", "JetBrainsMono Nerd Font Mono");
  clock.writeConfig("fontsize", 6);
  clock.writeConfig("fontstylename", "Regular");
  clock.writeConfig("fontweight", 400);
  clock.writeConfig("showdate", true);
  clock.writeConfig("dateformat", "shortDate");
  clock.writeConfig("use24hformat", 2);
  clock.reloadConfig();
}
addGap(topPanel, 6);

topPanel.addWidget("KdeControlStation");

// -----------------------------------------------------------------------------
// 4. Bottom Dock (3-Island Layout)
// -----------------------------------------------------------------------------
var bottomPanelHeight = 48;
var bottomPanel = new Panel();
bottomPanel.screen = targetScreen;
bottomPanel.location = "bottom";
bottomPanel.height = bottomPanelHeight;
bottomPanel.floating = true;
bottomPanel.opacity = "translucent";

// Bottom Colorizer
var bottomColorizer = bottomPanel.addWidget("luisbocanegra.panel.colorizer");
if (bottomColorizer) {
  bottomColorizer.currentConfigGroup = ["Configuration", "General"];
  bottomColorizer.writeConfig("editmode", false);
  bottomColorizer.writeConfig("hidewidget", true);
  bottomColorizer.writeConfig("showinpanel", false);
  bottomColorizer.reloadConfig();
}

// Island 1: Launcher & Tasks
var kickoff = bottomPanel.addWidget("org.kde.plasma.kickoff");
if (kickoff) {
  kickoff.currentConfigGroup = ["Configuration", "General"];
  kickoff.writeConfig("favoritesportedtokastats", true);
  kickoff.writeConfig("icon", "archlinux-logo");
  kickoff.reloadConfig();
}
addGap(bottomPanel, 6);

var taskbar = bottomPanel.addWidget("org.kde.plasma.icontasks");
if (taskbar) {
  taskbar.currentConfigGroup = ["Configuration", "General"];
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

// Spacer to Center Island
var bottomSpacer1 = bottomPanel.addWidget("org.kde.plasma.panelspacer");
if (bottomSpacer1) {
  bottomSpacer1.currentConfigGroup = ["Configuration", "General"];
  bottomSpacer1.writeConfig("expanding", true);
  bottomSpacer1.reloadConfig();
}

// Island 2: Pager
bottomPanel.addWidget("org.kde.plasma.pager");

// Spacer to Right Island
var bottomSpacer2 = bottomPanel.addWidget("org.kde.plasma.panelspacer");
if (bottomSpacer2) {
  bottomSpacer2.currentConfigGroup = ["Configuration", "General"];
  bottomSpacer2.writeConfig("expanding", true);
  bottomSpacer2.reloadConfig();
}

// Island 3: System Tray & Trash
var tray = bottomPanel.addWidget("org.kde.plasma.systemtray");
if (tray) {
  tray.currentConfigGroup = ["General"];
  tray.writeConfig(
    "extraitems",
    "org.kde.plasma.vault,org.kde.kdeconnect,org.kde.plasma.bluetooth,org.kde.plasma.cameraindicator,org.kde.plasma.clipboard,org.kde.plasma.devicenotifier,org.kde.plasma.manage-inputmethod,org.kde.plasma.mediacontroller,org.kde.plasma.notifications,org.kde.plasma.keyboardindicator,org.kde.plasma.weather,org.kde.kscreen,org.kde.plasma.battery,org.kde.plasma.brightness,org.kde.plasma.keyboardlayout,org.kde.plasma.networkmanagement,org.kde.plasma.volume,org.kde.plasma.printmanager",
  );
  tray.reloadConfig();
}
addGap(bottomPanel, 8);

bottomPanel.addWidget("org.kde.plasma.trash");

// -----------------------------------------------------------------------------
// 5. Desktop Canvas Widgets (Dynamic Resolution Math)
// -----------------------------------------------------------------------------
var geom = screenGeometry(targetScreen);
var screenWidth = geom.width;
var screenHeight = geom.height;

var usableTop = topPanelHeight + 12;
var usableBottom = screenHeight - bottomPanelHeight - 12;
var usableHeight = usableBottom - usableTop;

// Mid-Left: CAVA bounds
var cavaBounds = {
  x: 0,
  y: Math.round(usableTop + usableHeight * 0.12),
  width: Math.round(screenWidth * 0.14),
  height: Math.round(usableHeight * 0.58),
};

// Bottom-Left: Binary Clock bounds
var binClockBounds = {
  x: 32,
  width: Math.round(screenWidth * 0.125),
  height: Math.round(usableHeight * 0.15),
};
binClockBounds.y = usableBottom - binClockBounds.height - 12;

// Right: YoRHa Telemetry bounds
var yorhaBounds = {
  width: Math.round(screenWidth * 0.25),
  height: Math.round(usableHeight * 0.32),
};
yorhaBounds.x = screenWidth - yorhaBounds.width - 36;
yorhaBounds.y = Math.round(usableTop + usableHeight * 0.35);

// Under YoRHa: Thermal Monitor bounds
var thermalBounds = {
  x: yorhaBounds.x + Math.round(yorhaBounds.width * 0.18),
  y: yorhaBounds.y + yorhaBounds.height + 14,
  width: Math.round(yorhaBounds.width * 0.78),
  height: Math.round(usableHeight * 0.1),
};

if (allDesktops.length > targetScreen) {
  var primaryDesk = allDesktops[targetScreen];

  function placeDynamicWidget(pluginId, bounds) {
    var wid = primaryDesk.addWidget(pluginId);
    if (wid) {
      wid.currentConfigGroup = ["Geometry"];
      wid.writeConfig("x", bounds.x);
      wid.writeConfig("y", bounds.y);
      wid.writeConfig("width", bounds.width);
      wid.writeConfig("height", bounds.height);
      wid.reloadConfig();
    }
    return wid;
  }

  // 1. CAVA Audio Visualizer
  var cavaWidget = placeDynamicWidget(
    "luisbocanegra.audio.visualizer",
    cavaBounds,
  );
  if (cavaWidget) {
    cavaWidget.currentConfigGroup = ["General"];
    cavaWidget.writeConfig("orientation", 1);
    cavaWidget.writeConfig("alignment", 0);
    cavaWidget.writeConfig("drawStyle", 1);
    cavaWidget.writeConfig("colorMode", 0);
    cavaWidget.writeConfig("customColor", "#E0E0E0");
    cavaWidget.writeConfig("audioBackend", "pulseaudio");
    cavaWidget.writeConfig("framerate", 60);
    cavaWidget.writeConfig("sensitivity", 100);
    cavaWidget.writeConfig("smoothSpectrum", true);
    cavaWidget.reloadConfig();
  }

  // 2. Binary Clock Matrix
  var binClockWidget = placeDynamicWidget(
    "org.kde.plasma.binaryclock",
    binClockBounds,
  );
  if (binClockWidget) {
    binClockWidget.currentConfigGroup = ["Appearance"];
    binClockWidget.writeConfig("showSeconds", true);
    binClockWidget.writeConfig("activeColor", "#FFFFFF");
    binClockWidget.writeConfig("inactiveColor", "#3A3A3A");
    binClockWidget.reloadConfig();
  }

  // 3. YoRHa HUD
  placeDynamicWidget("com.axzoros.yorhahud", yorhaBounds);

  // 4. Thermal Monitor
  placeDynamicWidget("org.kde.olib.thermalmonitor", thermalBounds);
}
