// ==============================================================================
// Dynamic Geometry & Space Calculator for Plasma 6
// ==============================================================================

var targetScreen = 0;

// 1. Fetch Dynamic Screen Geometry
var geom = screenGeometry(targetScreen);
var screenWidth = geom.width;
var screenHeight = geom.height;

print("\n--- SCREEN RESOLUTION: " + screenWidth + "x" + screenHeight + " ---");

// 2. Define Layout Ratios based on Rice Reference
var topPanelHeight = 36;
var bottomPanelHeight = 46;

// Dynamic Safe Area (Usable area between top and bottom panels)
var usableTop = topPanelHeight + 12; // 12px margin
var usableBottom = screenHeight - bottomPanelHeight - 12;
var usableHeight = usableBottom - usableTop;

print(
  "Usable Desktop Canvas: Height = " +
    usableHeight +
    "px (Y: " +
    usableTop +
    " to " +
    usableBottom +
    ")",
);

// -----------------------------------------------------------------------------
// Dynamic Widget Positioning & Gap Calculations
// -----------------------------------------------------------------------------

// --- WIDGET A: CAVA Visualizer (Mid-Left, 50% usable height) ---
var cava = {
  x: 0,
  y: Math.round(usableTop + usableHeight * 0.15),
  width: Math.round(screenWidth * 0.14), // 14% of screen width (~268px on 1080p)
  height: Math.round(usableHeight * 0.55), // 55% of usable height (~540px on 1080p)
};
cava.right = cava.x + cava.width;
cava.bottom = cava.y + cava.height;

// --- WIDGET B: Binary Clock (Bottom-Left, directly under CAVA) ---
var desiredLeftMargin = 32;
var binClock = {
  x: desiredLeftMargin,
  width: Math.round(screenWidth * 0.125), // 12.5% of screen width (~240px)
  height: Math.round(usableHeight * 0.14), // 14% of usable height (~140px)
};
// Pin Y dynamically above the bottom panel
binClock.y = usableBottom - binClock.height - 16;
binClock.right = binClock.x + binClock.width;
binClock.bottom = binClock.y + binClock.height;

// Calculate vertical gap between CAVA and Binary Clock:
var verticalLeftGap = binClock.y - cava.bottom;

// --- WIDGET C: YoRHa HUD (Right Side) ---
var desiredRightMargin = 40;
var yorhaWidth = Math.round(screenWidth * 0.25); // 25% of screen width (~480px)
var yorhaHeight = Math.round(usableHeight * 0.32); // 32% of usable height (~320px)

var yorha = {
  x: screenWidth - yorhaWidth - desiredRightMargin,
  y: Math.round(usableTop + usableHeight * 0.35),
  width: yorhaWidth,
  height: yorhaHeight,
};
yorha.bottom = yorha.y + yorha.height;

// --- WIDGET D: Thermal Monitor (Anchored dynamically under YoRHa) ---
var dynamicGap = 16; // Fixed 16px distance
var thermal = {
  x: yorha.x + Math.round(yorha.width * 0.2), // Indented slightly to the right
  y: yorha.bottom + dynamicGap, // Dynamically derived from YoRHa bottom
  width: Math.round(yorha.width * 0.75),
  height: Math.round(usableHeight * 0.1),
};

// -----------------------------------------------------------------------------
// Output Computed Spaces & Offsets
// -----------------------------------------------------------------------------
print("\n--- COMPUTED DESKTOP WIDGET COORDINATES ---");
print(
  "CAVA Visualizer : x=" +
    cava.x +
    ", y=" +
    cava.y +
    ", w=" +
    cava.width +
    ", h=" +
    cava.height,
);
print(
  "Binary Clock    : x=" +
    binClock.x +
    ", y=" +
    binClock.y +
    ", w=" +
    binClock.width +
    ", h=" +
    binClock.height,
);
print(
  "-> Dynamic Vertical Gap between CAVA & Clock: " + verticalLeftGap + "px",
);

print(
  "YoRHa HUD       : x=" +
    yorha.x +
    ", y=" +
    yorha.y +
    ", w=" +
    yorha.width +
    ", h=" +
    yorha.height,
);
print(
  "Thermal Monitor : x=" +
    thermal.x +
    ", y=" +
    thermal.y +
    ", w=" +
    thermal.width +
    ", h=" +
    thermal.height,
);
print("-> Vertical Gap between YoRHa & Thermal: " + dynamicGap + "px");

// -----------------------------------------------------------------------------
// Apply to Screen 0 Desktop
// -----------------------------------------------------------------------------
var primaryDesk = desktops()[targetScreen];
if (primaryDesk) {
  // Clear existing widgets
  var ws = primaryDesk.widgets();
  for (var w = ws.length - 1; w >= 0; w--) {
    ws[w].remove();
  }

  // Helper to apply dynamic bounds
  function injectWidget(pluginId, bounds) {
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

  injectWidget("luisbocanegra.audio.visualizer", cava);
  injectWidget("org.kde.plasma.binaryclock", binClock);
  injectWidget("com.axzoros.yorhahud", yorha);
  injectWidget("org.kde.olib.thermalmonitor", thermal);
}
