#!/usr/bin/env python3
"""Pad the phone slides out to a Play-legal aspect ratio.

The slides are composed on a 1080x2400 canvas because that is the shape of the
device they were captured on. Play will not take that: a store screenshot's
"maximum dimension can't be more than twice the minimum dimension", and
2400 is 2.22x 1080, so the upload is refused before anyone looks at the art.
The same rule is why the downscaled 810x1800 copies under fastlane/ were no
better - the ratio travels with the scale.

So this widens the canvas instead of cropping the art: 1080x2400 becomes
1200x2400, exactly 2:1, by replicating the leftmost and rightmost column of
pixels 60px outwards. The backgrounds are vertical gradients, so an edge
column is the correct colour for every row and the seam is invisible; nothing
is scaled, moved or clipped. 1200 on the short side also clears the 1080px
floor that large-format Play recommendations ask for, which 810 did not.

Run from the repo root:  python3 play/pipeline/pad_phone_shots.py
Reads play/store-listing/??-*.png, writes both that file and the fastlane
upload copy in place. Idempotent: an image that is already 2:1 is skipped.
"""

from __future__ import annotations

import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE_DIR = ROOT / "play" / "store-listing"
UPLOAD_DIR = (
    ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "phoneScreenshots"
)

TARGET_W, TARGET_H = 1200, 2400


def pad(img: Image.Image) -> Image.Image:
    """[img] centred on a TARGET_W canvas, the gap filled by its edge columns."""
    out = Image.new("RGB", (TARGET_W, TARGET_H))
    left = (TARGET_W - img.width) // 2
    # Stretch one column, not a solid fill: the backgrounds are vertical
    # gradients, so the edge column already holds the right colour per row.
    out.paste(img.crop((0, 0, 1, img.height)).resize((left, img.height)), (0, 0))
    out.paste(
        img.crop((img.width - 1, 0, img.width, img.height)).resize(
            (TARGET_W - left - img.width, img.height),
        ),
        (left + img.width, 0),
    )
    out.paste(img, (left, 0))
    return out


def main() -> int:
    slides = sorted(p for p in SOURCE_DIR.glob("*.png") if p.name[:2].isdigit())
    if not slides:
        print(f"No numbered slides in {SOURCE_DIR}", file=sys.stderr)
        return 1

    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    for index, path in enumerate(slides, start=1):
        with Image.open(path) as opened:
            img = opened.convert("RGB")
            if (img.width, img.height) == (TARGET_W, TARGET_H):
                print(f"  = {path.name} already {TARGET_W}x{TARGET_H}")
                padded = img.copy()
            elif img.height != TARGET_H or img.width > TARGET_W:
                print(f"  ! {path.name} is {img.width}x{img.height}, skipped")
                continue
            else:
                padded = pad(img)
                padded.save(path)
                print(f"  ✔ {path.name} -> {TARGET_W}x{TARGET_H}")
        # Play takes the upload copies from fastlane/, so they have to be the
        # padded ones too, and at full size: the 810px-wide downscales were
        # under the 1080px floor as well as the wrong shape.
        padded.save(UPLOAD_DIR / f"{index}.png")
    print(f"  → {len(slides)} screenshots in {UPLOAD_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
