#!/usr/bin/env python3
"""Import a batch of device screenshots into the docs.

Usage:
    python3 docs/screenshots/import.py <batch>            # import a whole batch
    python3 docs/screenshots/import.py <batch> --skip 3   # you skipped shot #3
    python3 docs/screenshots/import.py <batch> --count 5  # you only shot the first 5
    python3 docs/screenshots/import.py --ids a/b c/d      # explicit ids, in shot order
    python3 docs/screenshots/import.py <batch> --dry-run  # show the mapping, change nothing

For each shot it converts the PNG to WebP at quality 88, writes it to the asset
path the manifest specifies, replaces the `{/* shot: id */}` placeholder in the
page with a real image tag, and flips the manifest entry to done.

Screenshots are read newest-last from /sdcard/DCIM/Screenshots and matched to the
pending ids in order, so take them in the order SHOOTLIST.md lists.
"""
import argparse
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.dirname(HERE)
REPO = os.path.dirname(DOCS)
MANIFEST = os.path.join(HERE, "manifest.json")
STAGING = os.path.join(HERE, ".incoming")
DEVICE_DIR = "/sdcard/DCIM/Screenshots"

sys.path.insert(0, HERE)
from shootlist import bucket  # noqa: E402  (same directory)


def sh(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout


def pull_recent(count):
    """Pull the `count` newest device screenshots, oldest first.

    Samsung writes screenshots as .jpg with spaces in the name
    (`Screenshot_20260729_061342_WM Keyboard.jpg`), so this lists bare names one
    per line instead of globbing: a `*.jpg` glob gets expanded by the local
    shell before adb ever sees it, and splitting on whitespace mangles the names.
    """
    listing = sh(f"adb shell ls -t '{DEVICE_DIR}'").splitlines()
    images = [n.strip() for n in listing
              if n.strip().lower().endswith((".png", ".jpg", ".jpeg"))]
    if not images:
        sys.exit(f"No screenshots found in {DEVICE_DIR} on the device.")
    newest_first = images[:count]
    if len(newest_first) < count:
        sys.exit(
            f"Asked for {count} screenshots but only {len(newest_first)} are on the "
            "device. Take the missing ones, or pass --count / --skip."
        )
    os.makedirs(STAGING, exist_ok=True)
    local = []
    for index, name in enumerate(reversed(newest_first)):  # oldest first
        dest = os.path.join(STAGING, f"{index:03d}{os.path.splitext(name)[1].lower()}")
        pull = subprocess.run(["adb", "pull", f"{DEVICE_DIR}/{name}", dest],
                              capture_output=True, text=True)
        if not os.path.exists(dest) or os.path.getsize(dest) == 0:
            sys.exit(f"Failed to pull {name}: {pull.stderr.strip() or 'empty file'}")
        local.append(dest)
    return local


def embed(entry):
    """Swap the placeholder comment in the page for a real image tag.

    Returns "placed", "already" (a retake — the page points at this image
    already, so overwriting the file is the whole job), or "missing".
    """
    page = os.path.join(REPO, entry["page"])
    placeholder = "{/* shot: %s */}" % entry["id"]
    with open(page) as handle:
        content = handle.read()
    rel = entry["file"]
    rel = rel[len("src/assets/"):] if rel.startswith("src/assets/") else rel
    if placeholder not in content:
        return "already" if f"](@assets/{rel})" in content else "missing"
    alt = entry["caption"].replace('"', "'")
    with open(page, "w") as handle:
        handle.write(content.replace(placeholder, f"![{alt}](@assets/{rel})"))
    return "placed"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("batch", nargs="?", help="batch slug from SHOOTLIST.md")
    parser.add_argument("--ids", nargs="+", help="explicit manifest ids, in shot order")
    parser.add_argument("--skip", type=int, action="append", default=[],
                        help="1-based shot number you did NOT take (repeatable)")
    parser.add_argument("--count", type=int, help="only the first N shots of the batch")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    manifest = json.load(open(MANIFEST))
    by_id = {e["id"]: e for e in manifest}

    if args.ids:
        targets = [by_id[i] for i in args.ids]
    elif args.batch:
        pending = [e for e in manifest if e["status"] != "done"]
        targets = [e for e in pending if bucket(e)[0] == args.batch]
        if not targets:
            sys.exit(f"No pending shots in batch '{args.batch}'.")
        skipped = set(args.skip)
        targets = [e for n, e in enumerate(targets, 1) if n not in skipped]
        if args.count:
            targets = targets[: args.count]
    else:
        sys.exit("Give a batch name or --ids. See docs/screenshots/SHOOTLIST.md.")

    print(f"{len(targets)} shots to import:")
    for num, entry in enumerate(targets, 1):
        print(f"  {num:3}. {entry['id']}")

    if args.dry_run:
        print("\n(dry run — nothing changed)")
        return

    shots = pull_recent(len(targets))

    for entry, png in zip(targets, shots):
        dest = os.path.join(DOCS, entry["file"])
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        convert = subprocess.run(["cwebp", "-q", "88", png, "-o", dest],
                                 capture_output=True, text=True)
        if convert.returncode != 0:
            sys.exit(f"cwebp failed on {png} -> {entry['id']}:\n{convert.stderr.strip()}")
        size = os.path.getsize(dest) // 1024
        placed = embed(entry)
        entry["status"] = "done"
        flag = {"placed": "", "already": "  (retake — page already points here)",
                "missing": "  (no placeholder found — check the page)"}[placed]
        print(f"  {entry['id']} -> {entry['file']} ({size} KB){flag}")

    json.dump(manifest, open(MANIFEST, "w"), indent=2)
    remaining = sum(1 for e in manifest if e["status"] != "done")
    print(f"\nDone. {remaining} shots still to take.")
    print("Next: CHECK_LINKS=1 npm --prefix docs run check")


if __name__ == "__main__":
    main()
