#!/usr/bin/env python3
"""Guided shooting: walks a batch shot by shot, running the adb setup for you.

Usage:
    python3 docs/screenshots/guide.py <batch>          # walk the batch
    python3 docs/screenshots/guide.py <batch> --from 12  # resume at shot 12
    python3 docs/screenshots/guide.py --ids a/b c/d    # just these

For each shot it prints what to frame, runs any adb commands in that shot's setup,
then waits. You take the screenshot (Power + Volume-Down) and press Enter.
Type `s` to skip a shot, `q` to stop early.

Nothing is imported while you shoot. At the end it hands you the exact import
command for what you actually captured, so a skip mid-batch can't misalign things.
"""
import argparse
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MANIFEST = os.path.join(HERE, "manifest.json")

sys.path.insert(0, HERE)
from shootlist import bucket  # noqa: E402

ADB_LINE = re.compile(r"^\s*(adb\s|am start)")

BOLD, DIM, GREEN, YELLOW, RESET = "\033[1m", "\033[2m", "\033[32m", "\033[33m", "\033[0m"


def runnable(step):
    """An adb command we can run as-is, normalised to start with `adb shell`.

    `wmkeyboard://settings/<route>` links only work as an explicit intent: the
    manifest registers a browsable filter for addons/repo/addon and nothing else,
    so an implicit VIEW intent fails to resolve. Name MainActivity directly.
    """
    if not ADB_LINE.match(step):
        return None
    cmd = step.strip()
    if cmd.startswith("am start"):
        cmd = "adb shell " + cmd
    if "wmkeyboard://settings/" in cmd and "-n " not in cmd:
        cmd = cmd.replace(
            "am start",
            "am start -n com.wasimaster.wmkeyboard/.app.MainActivity",
            1,
        )
    return cmd


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("batch", nargs="?")
    parser.add_argument("--ids", nargs="+")
    parser.add_argument("--from", dest="start", type=int, default=1,
                        help="1-based shot number to resume at")
    args = parser.parse_args()

    manifest = json.load(open(MANIFEST))
    by_id = {e["id"]: e for e in manifest}

    if args.ids:
        queue = [by_id[i] for i in args.ids]
    elif args.batch:
        pending = [e for e in manifest if e["status"] != "done"]
        queue = [e for e in pending if bucket(e)[0] == args.batch]
        if not queue:
            sys.exit(f"No pending shots in batch '{args.batch}'.")
    else:
        sys.exit("Give a batch name or --ids. See docs/screenshots/SHOOTLIST.md.")

    queue = queue[args.start - 1:]
    captured = []

    print(f"\n{BOLD}{len(queue)} shots{RESET}. Enter = shot taken · s = skip · q = stop\n")

    for num, entry in enumerate(queue, args.start):
        print(f"{BOLD}[{num}] {entry['id']}{RESET}")
        print(f"    {entry['caption']}")

        manual = []
        for step in entry.get("setup", []):
            cmd = runnable(step)
            if cmd:
                subprocess.run(cmd, shell=True, capture_output=True)
                print(f"    {DIM}ran: {cmd[:110]}{RESET}")
            else:
                manual.append(step)
        for step in manual:
            print(f"    {YELLOW}do:{RESET} {step}")
        if entry.get("kind") == "anim":
            print(f"    {YELLOW}note:{RESET} animated — record a clip, don't screenshot")

        try:
            answer = input("    > ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            print("\nstopped")
            break
        if answer == "q":
            break
        if answer == "s":
            print(f"    {DIM}skipped{RESET}\n")
            continue
        captured.append(entry["id"])
        print(f"    {GREEN}captured{RESET}\n")

    if not captured:
        print("Nothing captured.")
        return

    print(f"{BOLD}{len(captured)} captured.{RESET} Import them with:\n")
    print("  python3 docs/screenshots/import.py --ids \\")
    for index, shot_id in enumerate(captured):
        tail = " \\" if index < len(captured) - 1 else ""
        print(f"    {shot_id}{tail}")
    print("\nThen: CHECK_LINKS=1 npm --prefix docs run check")


if __name__ == "__main__":
    main()
