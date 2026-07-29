#!/usr/bin/env python3
"""Read the ticked `retake` boxes out of REVIEW.md and drive the reshoot.

Usage:
    python3 docs/screenshots/retake.py          # show what's ticked, print commands
    python3 docs/screenshots/retake.py --guide  # go straight into guide.py for them
    python3 docs/screenshots/retake.py --clear  # untick every box (after a reshoot)

Retaking does not touch the manifest or the page: the image path stays the same
and the page already points at it, so importing over the top is the whole job.
"""
import argparse
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REVIEW = os.path.join(HERE, "REVIEW.md")

HEADING = re.compile(r"^### `([^`]+)`\s*$")
TICKED = re.compile(r"^- \[[xX]\] retake\s*$")


def ticked_ids():
    """Ids whose `- [x] retake` box is ticked, in the order REVIEW.md lists them."""
    if not os.path.exists(REVIEW):
        sys.exit("No REVIEW.md yet. Run: python3 docs/screenshots/review.py")
    current, found = None, []
    for line in open(REVIEW):
        heading = HEADING.match(line)
        if heading:
            current = heading.group(1)
        elif TICKED.match(line) and current:
            found.append(current)
            current = None
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--guide", action="store_true",
                        help="run guide.py for the ticked shots straight away")
    parser.add_argument("--clear", action="store_true",
                        help="untick every retake box and exit")
    args = parser.parse_args()

    if args.clear:
        content = open(REVIEW).read()
        open(REVIEW, "w").write(
            re.sub(r"^- \[[xX]\] retake\s*$", "- [ ] retake", content, flags=re.M))
        print("All retake boxes cleared.")
        return

    ids = ticked_ids()
    if not ids:
        print("Nothing ticked in REVIEW.md.")
        return

    print(f"{len(ids)} shot(s) marked for retake:")
    for num, shot_id in enumerate(ids, 1):
        print(f"  {num:3}. {shot_id}")

    joined = " ".join(ids)
    if args.guide:
        subprocess.run([sys.executable, os.path.join(HERE, "guide.py"), "--ids", *ids])
        print(f"\nNow import them:\n\n  python3 docs/screenshots/import.py --ids {joined}\n")
        return

    print("\nShoot them (walks the setup for each):\n")
    print(f"  python3 docs/screenshots/retake.py --guide\n")
    print("Then import, in the same order:\n")
    print(f"  python3 docs/screenshots/import.py --ids {joined}\n")
    print("Afterwards: python3 docs/screenshots/retake.py --clear")


if __name__ == "__main__":
    main()
