#!/usr/bin/env python3
"""
Template engine for plasma-org.kde.plasma.desktop-appletsrc and plasmashellrc.

Replaces the unreliable evaluateScript/layout.js DBus approach: this script
turns a harvested (live) config into a machine-portable template, and turns
that template back into a live config on install, so the repo never carries
one machine's hardcoded home directory or activity UUID.

Subcommands:
    harvest   Live config -> repo template (placeholders + stale-panel prune)
    install   Repo template -> live config (placeholders hydrated)
    verify    Check that all required rice widgets are present in a config
"""
import argparse
import re
import subprocess
import sys
import uuid

HOME_PLACEHOLDER = "{{HOME}}"
ACTIVITY_PLACEHOLDER = "{{ACTIVITY_ID}}"

UUID_RE = r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

# Plugins that must be present for the documented rice layout to be intact.
REQUIRED_PLUGINS = [
    "luisbocanegra.panel.colorizer",
    "org.kde.plasma.systemmonitor.net",
    "org.kde.plasma.systemmonitor.cpu",
    "org.kde.plasma.systemmonitor.memory",
    "org.kde.plasma.systemmonitor.diskusage",
    "org.kde.plasma.panelspacer",
    "org.kde.plasma.catwalkEnhanced",
    "org.kde.plasma.digitalclock",
    "KdeControlStation",
    "org.kde.plasma.kickoff",
    "org.kde.plasma.icontasks",
    "org.kde.plasma.pager",
    "org.kde.plasma.systemtray",
    "org.kde.plasma.trash",
    "luisbocanegra.audio.visualizer",
    "org.kde.plasma.binaryclock",
    "com.axzoros.yorhahud",
    "org.kde.olib.thermalmonitor",
]


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def query_current_activity():
    """Best-effort lookup of the live default activity UUID via DBus."""
    try:
        out = subprocess.run(
            ["qdbus6", "org.kde.ActivityManager", "/ActivityManager/Activities", "CurrentActivity"],
            capture_output=True, text=True, timeout=5,
        )
        activity_id = out.stdout.strip()
        if out.returncode == 0 and re.fullmatch(UUID_RE, activity_id):
            return activity_id
    except (OSError, subprocess.SubprocessError):
        pass
    return ""


def panel_containment_ids(appletsrc_text):
    """IDs of [Containments][N] blocks whose plugin= is org.kde.panel."""
    ids = set()
    blocks = re.split(r"(?m)^\[Containments\]\[(\d+)\]$", appletsrc_text)
    # re.split with a capturing group yields: [pre, id1, body1, id2, body2, ...]
    for i in range(1, len(blocks), 2):
        cid, body = blocks[i], blocks[i + 1]
        # Stop the body at the next top-level bracketed section.
        body = body.split("\n[", 1)[0]
        if re.search(r"(?m)^plugin=org\.kde\.panel$", body):
            ids.add(cid)
    return ids


def cmd_harvest(args):
    home = args.home.rstrip("/")
    appletsrc = read(args.appletsrc)

    home_hits = appletsrc.count(home)
    appletsrc = appletsrc.replace(home, HOME_PLACEHOLDER)

    activity_hits = len(re.findall(r"activityId=" + UUID_RE, appletsrc))
    appletsrc = re.sub(r"(activityId=)" + UUID_RE, r"\1" + ACTIVITY_PLACEHOLDER, appletsrc)

    write(args.appletsrc, appletsrc)
    print(f"[sanitize] templated {home_hits} home-path occurrence(s), "
          f"{activity_hits} activityId occurrence(s) in {args.appletsrc}")

    if args.plasmashellrc:
        live_panel_ids = panel_containment_ids(appletsrc)
        pruned = _prune_stale_panels(args.plasmashellrc, live_panel_ids)
        print(f"[sanitize] pruned {pruned} stale [PlasmaViews][Panel N] "
              f"block(s) not matching live panel IDs {sorted(live_panel_ids)}")


def _prune_stale_panels(plasmashellrc_path, live_panel_ids):
    text = read(plasmashellrc_path)
    lines = text.split("\n")

    out = []
    pruned = 0
    skipping = False
    updates_re = re.compile(r"^\[Updates\]$")
    panel_re = re.compile(r"^\[PlasmaViews\]\[Panel (\d+)\](\[.*\])?$")

    for line in lines:
        m = panel_re.match(line)
        if m:
            skipping = m.group(1) not in live_panel_ids
            if skipping:
                pruned += 1
            if skipping:
                continue
            out.append(line)
            continue
        if updates_re.match(line):
            skipping = True
            continue
        if skipping and line.startswith("["):
            # A new, unrelated section starts: stop skipping.
            skipping = False
        if skipping:
            continue
        out.append(line)

    # Collapse runs of blank lines left behind by removed blocks.
    collapsed = re.sub(r"\n{3,}", "\n\n", "\n".join(out))
    write(plasmashellrc_path, collapsed.strip("\n") + "\n")
    return pruned


def cmd_install(args):
    text = read(args.appletsrc)

    home = args.home.rstrip("/")
    text = text.replace(HOME_PLACEHOLDER, home)

    activity_id = args.activity_id or query_current_activity()
    text = text.replace(ACTIVITY_PLACEHOLDER, activity_id)

    remaining = text.count(HOME_PLACEHOLDER) + text.count(ACTIVITY_PLACEHOLDER)
    if remaining:
        print(f"[sanitize] WARNING: {remaining} placeholder(s) left unresolved", file=sys.stderr)

    write(args.out, text)
    print(f"[sanitize] hydrated -> {args.out} "
          f"(home={home}, activityId={activity_id or '<empty>'})")


def cmd_verify(args):
    text = read(args.appletsrc)
    missing = []
    for plugin in REQUIRED_PLUGINS:
        pattern = r"(?m)^plugin=" + re.escape(plugin) + r"$"
        count = len(re.findall(pattern, text))
        status = "OK " if count else "MISSING"
        print(f"  [{status}] {plugin}" + (f" (x{count})" if count else ""))
        if not count:
            missing.append(plugin)

    if missing:
        print(f"[verify] {len(missing)}/{len(REQUIRED_PLUGINS)} required widget(s) missing.")
        return 1
    print(f"[verify] all {len(REQUIRED_PLUGINS)} required widgets present.")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    p_harvest = sub.add_parser("harvest", help="Live config -> repo template")
    p_harvest.add_argument("--appletsrc", required=True)
    p_harvest.add_argument("--plasmashellrc")
    p_harvest.add_argument("--home", default=__import__("os").path.expanduser("~"))
    p_harvest.set_defaults(func=cmd_harvest)

    p_install = sub.add_parser("install", help="Repo template -> live config")
    p_install.add_argument("--appletsrc", required=True)
    p_install.add_argument("--out", required=True)
    p_install.add_argument("--home", default=__import__("os").path.expanduser("~"))
    p_install.add_argument("--activity-id", default="")
    p_install.set_defaults(func=cmd_install)

    p_verify = sub.add_parser("verify", help="Check required widgets are present")
    p_verify.add_argument("--appletsrc", required=True)
    p_verify.set_defaults(func=cmd_verify)

    args = parser.parse_args()
    sys.exit(args.func(args) or 0)


if __name__ == "__main__":
    main()
