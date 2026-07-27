#!/usr/bin/env python3
"""Render the addon-repository icon: the WMKeyboard mark with a puzzle piece in the pin.

Shares its geometry, palette and lockup scale with `gen_icon.py`, so the two
marks stay in step -- only the badge inside the speech-bubble pin differs.

    python3 tools/icon/gen_addon_icon.py --out ../wmkeyboard-addon-repository/icon.png
"""

from __future__ import annotations

import argparse
import math
import os
import subprocess
import tempfile

import gen_icon as base
from gen_icon import C, f


# ---------------------------------------------------------------- puzzle piece
PIECE_W = 20.0          # body side, knob excluded
PIECE_R = 2.2           # body corner radius
KNOB_R = 4.6            # knob / socket radius
KNOB_OFF = 0.55         # knob centre offset past the edge, in knob radii -> narrow neck
TILT = -10.0            # degrees, for a bit of life


def circle(cx: float, cy: float, r: float) -> str:
    return (f"M{f(cx - r)},{f(cy)} A{f(r)},{f(r)} 0 0 1 {f(cx + r)},{f(cy)} "
            f"A{f(r)},{f(r)} 0 0 1 {f(cx - r)},{f(cy)} Z")


def piece_paths() -> tuple[str, str]:
    """(filled piece, socket to punch back out) centred on the pin, y-down.

    Knob bulges past the right edge, socket bites into the left one, both sitting
    far enough outside/inside their edge to leave a proper puzzle neck. The socket
    is painted in the pin colour rather than subtracted -- the pin behind it is flat
    white, so the result is identical and the path data stays simple.
    """
    half, d = PIECE_W / 2, KNOB_R * KNOB_OFF
    cx, cy = C, base.PIN_CY
    x, y = cx - half, cy - half
    body = base.rrect(x, y, PIECE_W, PIECE_W, PIECE_R)
    knob = circle(x + PIECE_W + d, cy, KNOB_R)
    socket = circle(x + d, cy, KNOB_R)
    return body + " " + knob, socket


def foreground_svg() -> str:
    s = base.lockup_scale()
    piece, socket = piece_paths()
    keys = "".join(f'<path d="{d}"/>' for d in base.key_paths())
    mask = (f'<mask id="kb"><rect width="108" height="108" fill="black"/>'
            f'<path d="{base.rrect(base.KB_X, base.KB_Y, base.KB_W, base.KB_H, base.KB_R)}" '
            f'fill="white"/>'
            f'<g fill="black" fill-opacity="0.5">{keys}</g>'
            f'<path d="{base.pin_path(base.PIN_HALO)}" fill="black"/></mask>')
    badge = (f'<g transform="rotate({TILT},{C},{base.PIN_CY})">'
             f'<path d="{piece}" fill="url(#wmg)"/>'
             f'<path d="{socket}" fill="{base.PIN_FILL}"/></g>')
    inner = (f'<g filter="url(#sh)">'
             f'<rect width="108" height="108" fill="url(#kbg)" mask="url(#kb)"/>'
             f'<path d="{base.pin_path()}" fill="{base.PIN_FILL}"/></g>{badge}')
    defs = (base.SHADOW_FILTER + mask + base.gradient("kbg", base.KB_TOP, base.KB_BOTTOM)
            + base.gradient("wmg", base.BRAND_A, base.BRAND_B))
    return base.svg(
        f'<g transform="translate({C},{C}) scale({s:.4f}) translate({-C},{-C})">{inner}</g>', defs)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, help="destination PNG")
    ap.add_argument("--size", type=int, default=256)
    args = ap.parse_args()

    base.need("rsvg-convert")
    from PIL import Image

    with tempfile.TemporaryDirectory() as tmp:
        bg = Image.open(base.render(base.background_svg(), args.size, tmp, "bg")).convert("RGBA")
        fg = Image.open(base.render(foreground_svg(), args.size, tmp, "fg")).convert("RGBA")
        out = os.path.abspath(args.out)
        Image.alpha_composite(bg, fg).save(out)
    print(f"wrote {out} ({args.size}px)")


if __name__ == "__main__":
    main()
