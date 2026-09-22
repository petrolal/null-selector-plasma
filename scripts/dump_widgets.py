#!/usr/bin/env python3
import configparser
import json
import os
import re

config_path = os.path.expanduser("~/.config/plasma-org.kde.plasma.desktop-appletsrc")
if not os.path.exists(config_path):
    print(f"Error: {config_path} not found.")
    exit(1)

config = configparser.ConfigParser(interpolation=None, strict=False)
config.read(config_path)

containments = {}
applets = {}

for section in config.sections():
    # Containment root: [Containments][X]
    m_cont = re.match(r"^Containments\]\[(\d+)$", section)
    if m_cont:
        cid = m_cont.group(1)
        containments[cid] = dict(config[section])
        continue

    # Applet root: [Containments][X][Applets][Y]
    m_app = re.match(r"^Containments\]\[(\d+)\]\[Applets\]\[(\d+)$", section)
    if m_app:
        cid, aid = m_app.group(1), m_app.group(2)
        applets.setdefault(cid, {})
        applets[cid].setdefault(
            aid, {"plugin": config[section].get("plugin", "unknown"), "config": {}}
        )
        continue

    # Applet subgroups: [Containments][X][Applets][Y][SubGroup...]
    m_sub = re.match(r"^Containments\]\[(\d+)\]\[Applets\]\[(\d+)\]\[(.*)$", section)
    if m_sub:
        cid, aid, sub = m_sub.group(1), m_sub.group(2), m_sub.group(3)
        applets.setdefault(cid, {})
        applets[cid].setdefault(aid, {"plugin": "unknown", "config": {}})
        applets[cid][aid]["config"][sub] = dict(config[section])

print("=" * 80)
print("KDE PLASMA 6 WIDGET CONFIGURATION & POSITION DUMP")
print("=" * 80)

loc_names = {"1": "TOP", "2": "RIGHT", "3": "BOTTOM", "4": "LEFT"}

for cid, cdata in containments.items():
    c_plugin = cdata.get("plugin", "unknown")

    if c_plugin == "org.kde.panel":
        loc = loc_names.get(cdata.get("location", "3"), "BOTTOM")
        order = [x for x in cdata.get("appletorder", "").split(";") if x]
        print(
            f"\n[PANEL ID: {cid}] Position: {loc} | Thickness: {cdata.get('thickness', 'default')}px"
        )
        print(f"Applet Order: {order if order else 'Natural'}")
        print("-" * 80)

    elif c_plugin == "org.kde.desktopcontainment":
        print(f"\n[DESKTOP CANVAS ID: {cid}] Screen: {cdata.get('lastscreen', '0')}")
        print("-" * 80)
    else:
        continue

    cid_applets = applets.get(cid, {})
    if not cid_applets:
        print("  (No applets registered)")
        continue

    for aid, adata in cid_applets.items():
        plugin = adata["plugin"]
        cfg = adata["config"]

        # Extract Geometry if available (mostly desktop widgets)
        geom_cfg = cfg.get("Geometry", {})
        x = geom_cfg.get("x", "auto")
        y = geom_cfg.get("y", "auto")
        w = geom_cfg.get("width", "auto")
        h = geom_cfg.get("height", "auto")

        print(f"\n  • Applet ID: {aid}")
        print(f"    Plugin  : {plugin}")
        if geom_cfg:
            print(f"    Position: X={x}, Y={y} | Size: {w}x{h}")

        # Print relevant configurations
        for sub_name, sub_data in cfg.items():
            if sub_name == "Geometry":
                continue
            cleaned_sub = sub_name.replace("][", " -> ")
            print(f"    [{cleaned_sub}]")
            for k, v in sub_data.items():
                # Truncate giant JSON strings for readability, or print them formatted
                if len(v) > 90 and (v.startswith("{") or v.startswith("[")):
                    try:
                        parsed = json.loads(v)
                        print(f"      {k}: [JSON Object with {len(parsed)} keys]")
                    except Exception:
                        print(f"      {k}: {v[:85]}...")
                else:
                    print(f"      {k} = {v}")

print("\n" + "=" * 80 + "\n")
