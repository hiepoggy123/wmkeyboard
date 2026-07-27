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
PIECE_W = 21.5          # body side, knobs excluded
PIECE_R = 4.3           # body corner radius, 0.20 of the side (chunky, Material-ish)
KNOB_R = 3.65           # knob / socket radius, 0.17 of the side
KNOB_OFF = 0.41         # knob centre offset past its edge, in knob radii -> ~0.3W neck
TILT = 0.0              # degrees


def circle(cx: float, cy: float, r: float) -> str:
    return (f"M{f(cx - r)},{f(cy)} A{f(r)},{f(r)} 0 0 1 {f(cx + r)},{f(cy)} "
            f"A{f(r)},{f(r)} 0 0 1 {f(cx - r)},{f(cy)} Z")


def piece_paths() -> tuple[str, str]:
    """(filled piece, sockets to punch back out) centred on the pin, y-down.

    The classic four-sided piece: knobs bulge past the top and right edges, sockets
    bite into the left and bottom ones. Each circle centre sits a fraction of its
    radius past its edge, which is what leaves a proper narrow neck. Sockets are
    painted in the pin colour rather than subtracted -- the pin behind them is flat
    white, so the result is identical and the path data stays simple.
    """
    half, d = PIECE_W / 2, KNOB_R * KNOB_OFF
    bulge = KNOB_R + d                       # how far a knob reaches past its edge
    # centre the union's bounding box (body + top/right knobs) on the pin
    cx, cy = C - bulge / 2, base.PIN_CY + bulge / 2
    x, y = cx - half, cy - half
    body = base.rrect(x, y, PIECE_W, PIECE_W, PIECE_R)
    knobs = " ".join((circle(cx, y - d, KNOB_R),                  # top
                      circle(x + PIECE_W + d, cy, KNOB_R)))       # right
    sockets = " ".join((circle(x + d, cy, KNOB_R),                # left
                        circle(cx, y + PIECE_W - d, KNOB_R)))     # bottom
    return body + " " + knobs, sockets


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
