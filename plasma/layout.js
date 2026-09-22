// ==============================================================================
// Plasma 6 Layout Script: Plasma Monochrome Rice (Cyberpunk / NieR)
// ==============================================================================

var homeDir = "/home/petrolal";
var videoFile =
  "file://" + homeDir + "/.local/share/wallpapers/synthwave-dreamwave-girl.mp4";

var videoConfigObj = [
  {
    filename: videoFile,
    enabled: true,
    duration: 0,
    customDuration: 0,
    playbackRate: 0,
    alternativePlaybackRate: 0,
    loop: false,
    dayNightPhase: 4,
  },
];

// Helper: adds an invisible spacer with fixed pixel width
function addGap(panel, width) {
  var spacer = panel.addWidget("org.kde.plasma.panelspacer");
  if (spacer) {
    spacer.currentConfigGroup = ["Configuration", "General"];
    spacer.writeConfig("expanding", false);
    spacer.writeConfig("length", width || 6);
    spacer.reloadConfig();
  }
  return spacer;
}

// -----------------------------------------------------------------------------
// 1. Clear Existing Panels & Desktop Widgets
// -----------------------------------------------------------------------------
var allPanels = panels();
for (var i = 0; i < allPanels.length; i++) {
  allPanels[i].remove();
}

var allDesktops = desktops();
for (var d = 0; d < allDesktops.length; d++) {
  var ws = allDesktops[d].widgets();
  for (var w = ws.length - 1; w >= 0; w--) {
    ws[w].remove();
  }
}

// -----------------------------------------------------------------------------
// 2. Multi-Screen Wallpaper Application (Smart Video Wallpaper)
// -----------------------------------------------------------------------------
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

  // Desktop Widgets for Screen 0
  if (d === 0) {
    // 1. Kurve Audio Visualizer (Left side)
    var kurve = desk.addWidget("luisbocanegra.audio.visualizer");
    if (kurve) {
      kurve.currentConfigGroup = ["Configuration", "General"];
      kurve.writeConfig("active", true);
      kurve.writeConfig("alignment", 0);
      kurve.writeConfig("audioBackend", "pulseaudio");
      kurve.writeConfig("barCount", 48);
      kurve.writeConfig("barGap", 3);
      kurve.writeConfig("barWidth", 2);
      kurve.writeConfig("blockHeight", 5);
      kurve.writeConfig("blockSpacing", 4);
      kurve.writeConfig("centeredBars", false);
      kurve.writeConfig("colorMode", 0);
      kurve.writeConfig("customColor", "#E0E0E0");
      kurve.writeConfig("desktopWidgetBg", 0);
      kurve.writeConfig("drawInactiveBlocks", false);
      kurve.writeConfig("drawStyle", 1);
      kurve.writeConfig("framerate", 60);
      kurve.writeConfig("hideWhenIdle", false);
      kurve.writeConfig("monstercat", true);
      kurve.writeConfig("noiseReduction", 75);
      kurve.writeConfig("orientation", 2);
      kurve.writeConfig("roundedBars", true);
      kurve.writeConfig("sensitivity", 100);
      kurve.writeConfig("smoothSpectrum", true);
      kurve.writeConfig("visualizerStyle", 0);
      kurve.writeConfig("waves", false);
      kurve.reloadConfig();
    }

    // 2. Binary Clock (Bottom Left)
    var binClock = desk.addWidget("org.kde.plasma.binaryclock");
    if (binClock) {
      binClock.currentConfigGroup = ["Configuration"];
      binClock.writeConfig("UserBackgroundHints", "ShadowBackground");

      binClock.currentConfigGroup = ["Configuration", "Appearance"];
      binClock.writeConfig("activeColor", "#FFFFFF");
      binClock.writeConfig("inactiveColor", "#3A3A3A");
      binClock.writeConfig("showSeconds", true);
      binClock.reloadConfig();
    }

    // 3. YoRHa HUD (Right side)
    var yorha = desk.addWidget("com.axzoros.yorhahud");
    if (yorha) {
      yorha.reloadConfig();
    }

    // 4. Thermal Monitor (Below YoRHa HUD)
    var thermal = desk.addWidget("org.kde.olib.thermalmonitor");
    if (thermal) {
      thermal.currentConfigGroup = ["Configuration"];
      thermal.writeConfig("UserBackgroundHints", "ShadowBackground");

      thermal.currentConfigGroup = ["Configuration", "Appearance"];
      thermal.writeConfig("fontScale", 1.4);

      thermal.currentConfigGroup = ["Configuration", "General"];
      thermal.writeConfig(
        "sensors",
        JSON.stringify([
          { name: "CPU Temperature", sensorId: "cpu/all/averageTemperature" },
          { name: "GPU Temperature", sensorId: "gpu/gpu0/temperature" },
        ]),
      );
      thermal.reloadConfig();
    }

    // Save exact widget geometries on Desktop Containment
    if (kurve && binClock && yorha && thermal) {
      var geom =
        "Applet-" +
        kurve.id +
        ":0,0,96,720,0;Applet-" +
        binClock.id +
        ":80,768,304,176,0;Applet-" +
        yorha.id +
        ":1376,320,512,304,0;Applet-" +
        thermal.id +
        ":1456,624,368,64,0;";
      desk.currentConfigGroup = [];
      desk.writeConfig("ItemGeometries-1920x1080", geom);
      desk.writeConfig("ItemGeometriesHorizontal", geom);
      desk.reloadConfig();
    }
  }
}

// -----------------------------------------------------------------------------
// 3. Primary Screen (Screen 0): Top Capsule Panel
// -----------------------------------------------------------------------------
var topPanel = new Panel();
topPanel.screen = 0;
topPanel.location = "top";
topPanel.height = 34;
topPanel.floating = true;
topPanel.opacity = "translucent";

// Top Colorizer
var topColorizer = topPanel.addWidget("luisbocanegra.panel.colorizer");
if (topColorizer) {
  topColorizer.currentConfigGroup = ["Configuration", "General"];
  topColorizer.writeConfig("hideWidget", true);
  topColorizer.writeConfig("showInPanel", false);
  topColorizer.writeConfig("editMode", false);
  topColorizer.reloadConfig();
}

// Top: System Telemetry Gauges
var netMon = topPanel.addWidget("org.kde.plasma.systemmonitor.net");
if (netMon) {
  netMon.currentConfigGroup = ["Configuration", "Appearance"];
  netMon.writeConfig("chartFace", "org.kde.ksysguard.linechart");
  netMon.writeConfig("title", "Network Speed");
  netMon.currentConfigGroup = ["Configuration", "SensorColors"];
  netMon.writeConfig("network/all/download", "114,114,116");
  netMon.writeConfig("network/all/upload", "116,116,114");
  netMon.currentConfigGroup = ["Configuration", "Sensors"];
  netMon.writeConfig(
    "highPrioritySensorIds",
    JSON.stringify(["network/all/download", "network/all/upload"]),
  );
  netMon.reloadConfig();
}
addGap(topPanel, 4);

var cpuMon = topPanel.addWidget("org.kde.plasma.systemmonitor.cpu");
if (cpuMon) {
  cpuMon.currentConfigGroup = ["Configuration", "Appearance"];
  cpuMon.writeConfig("chartFace", "org.kde.ksysguard.piechart");
  cpuMon.writeConfig("title", "Total CPU Use");
  cpuMon.currentConfigGroup = ["Configuration", "SensorColors"];
  cpuMon.writeConfig("cpu/all/usage", "114,114,116");
  cpuMon.currentConfigGroup = ["Configuration", "Sensors"];
  cpuMon.writeConfig(
    "highPrioritySensorIds",
    JSON.stringify(["cpu/all/usage"]),
  );
  cpuMon.reloadConfig();
}
addGap(topPanel, 4);

var memMon = topPanel.addWidget("org.kde.plasma.systemmonitor.memory");
if (memMon) {
  memMon.currentConfigGroup = ["Configuration", "Appearance"];
  memMon.writeConfig("chartFace", "org.kde.ksysguard.piechart");
  memMon.writeConfig("title", "Memory Usage");
  memMon.currentConfigGroup = ["Configuration", "SensorColors"];
  memMon.writeConfig("memory/physical/used", "114,114,116");
  memMon.currentConfigGroup = ["Configuration", "Sensors"];
  memMon.writeConfig(
    "highPrioritySensorIds",
    JSON.stringify(["memory/physical/used"]),
  );
  memMon.reloadConfig();
}
addGap(topPanel, 4);

var diskMon = topPanel.addWidget("org.kde.plasma.systemmonitor.diskusage");
if (diskMon) {
  diskMon.currentConfigGroup = ["Configuration", "Appearance"];
  diskMon.writeConfig("chartFace", "org.kde.ksysguard.piechart");
  diskMon.writeConfig("title", "Disk Usage");
  diskMon.currentConfigGroup = ["Configuration", "SensorColors"];
  diskMon.writeConfig("disk/all/usedPercent", "116,116,114");
  diskMon.currentConfigGroup = ["Configuration", "Sensors"];
  diskMon.writeConfig(
    "highPrioritySensorIds",
    JSON.stringify(["disk/.*/usedPercent"]),
  );
  diskMon.reloadConfig();
}

// Top Center Expanding Spacer
var topSpacer = topPanel.addWidget("org.kde.plasma.panelspacer");
if (topSpacer) {
  topSpacer.currentConfigGroup = ["Configuration", "General"];
  topSpacer.writeConfig("expanding", true);
  topSpacer.reloadConfig();
}

// Top Right: Controls & CatWalk
var catwalk = topPanel.addWidget("org.kde.plasma.catwalkEnhanced");
if (catwalk) {
  catwalk.currentConfigGroup = ["Configuration", "General"];
  catwalk.writeConfig("catScale", 0.75);
  catwalk.writeConfig("dividerThickness", 4);
  catwalk.writeConfig("linkScales", false);
  catwalk.writeConfig("type", 1);
  catwalk.writeConfig("updateRateLimit", 8000);
  catwalk.reloadConfig();
}
addGap(topPanel, 6);

var clock = topPanel.addWidget("org.kde.plasma.digitalclock");
if (clock) {
  clock.currentConfigGroup = ["Configuration", "Appearance"];
  clock.writeConfig("autoFontAndSize", false);
  clock.writeConfig("dateFormat", "shortDate");
  clock.writeConfig("fontFamily", "JetBrainsMono Nerd Font Mono");
  clock.writeConfig("fontSize", 6);
  clock.writeConfig("fontStyleName", "Regular");
  clock.writeConfig("fontWeight", 400);
  clock.writeConfig("showDate", true);
  clock.writeConfig("use24hFormat", 2);
  clock.reloadConfig();
}
addGap(topPanel, 6);

topPanel.addWidget("KdeControlStation");

// -----------------------------------------------------------------------------
// 4. Primary Screen (Screen 0): Bottom Capsule Dock
// -----------------------------------------------------------------------------
var bottomPanel = new Panel();
bottomPanel.screen = 0;
bottomPanel.location = "bottom";
bottomPanel.height = 46;
bottomPanel.floating = true;
bottomPanel.opacity = "translucent";

// Bottom Colorizer
var botColorizer = bottomPanel.addWidget("luisbocanegra.panel.colorizer");
if (botColorizer) {
  botColorizer.currentConfigGroup = ["Configuration", "General"];
  botColorizer.writeConfig("hideWidget", true);
  botColorizer.writeConfig("showInPanel", false);
  botColorizer.writeConfig("editMode", false);
  botColorizer.reloadConfig();
}

// Kickoff Application Launcher
var kickoff = bottomPanel.addWidget("org.kde.plasma.kickoff");
if (kickoff) {
  kickoff.currentConfigGroup = ["Configuration", "General"];
  kickoff.writeConfig("icon", "archlinux-logo");
  kickoff.writeConfig("favoritesPortedToKAstats", true);
  kickoff.reloadConfig();
}
addGap(bottomPanel, 6);

// Pinned Icons-Only Task Manager
var taskManager = bottomPanel.addWidget("org.kde.plasma.icontasks");
if (taskManager) {
  taskManager.currentConfigGroup = ["Configuration", "General"];
  taskManager.writeConfig(
    "launchers",
    [
      "applications:cool-retro-term.desktop",
      "preferred://browser",
      "preferred://filemanager",
      "applications:systemsettings.desktop",
      "applications:com.spotify.Client.desktop",
      "applications:discord.desktop",
    ].join(","),
  );
  taskManager.reloadConfig();
}

// Left Center Expanding Spacer
var botSpLeft = bottomPanel.addWidget("org.kde.plasma.panelspacer");
if (botSpLeft) {
  botSpLeft.currentConfigGroup = ["Configuration", "General"];
  botSpLeft.writeConfig("expanding", true);
  botSpLeft.reloadConfig();
}

// Virtual Desktop Pager
bottomPanel.addWidget("org.kde.plasma.pager");

// Right Center Expanding Spacer
var botSpRight = bottomPanel.addWidget("org.kde.plasma.panelspacer");
if (botSpRight) {
  botSpRight.currentConfigGroup = ["Configuration", "General"];
  botSpRight.writeConfig("expanding", true);
  botSpRight.reloadConfig();
}

// System Tray
var tray = bottomPanel.addWidget("org.kde.plasma.systemtray");
if (tray) {
  tray.currentConfigGroup = ["General"];
  var sysItems = [
    "org.kde.plasma.vault",
    "org.kde.kdeconnect",
    "org.kde.plasma.bluetooth",
    "org.kde.plasma.cameraindicator",
    "org.kde.plasma.clipboard",
    "org.kde.plasma.devicenotifier",
    "org.kde.plasma.manage-inputmethod",
    "org.kde.plasma.mediacontroller",
    "org.kde.plasma.notifications",
    "org.kde.plasma.keyboardindicator",
    "org.kde.plasma.weather",
    "org.kde.kscreen",
    "org.kde.plasma.battery",
    "org.kde.plasma.brightness",
    "org.kde.plasma.keyboardlayout",
    "org.kde.plasma.networkmanagement",
    "org.kde.plasma.volume",
    "org.kde.plasma.printmanager",
  ].join(",");
  tray.writeConfig("knownItems", sysItems);
  tray.writeConfig("extraItems", sysItems);
  tray.reloadConfig();
}
addGap(bottomPanel, 8);

// Trash Applet
bottomPanel.addWidget("org.kde.plasma.trash");
