#!/usr/bin/env python3
"""Regenerate the WMKeyboard launcher icon from its vector definition.

The mark is a Gboard-style lockup: a keyboard body with a speech-bubble pin
carrying the "WM" monogram (Amaz Obitaem Ostrov V.2 straight, outlines only -- the
font file itself is never shipped).

Everything is authored once, in a 108dp adaptive-icon canvas, and emitted as:

  drawable/ic_launcher_background.xml   linear-gradient background layer
  drawable/ic_launcher_monochrome.xml   themed-icon silhouette (Android 13+)
  mipmap-*/ic_launcher_fg.webp          foreground layer (raster: soft shadow)
  mipmap-*/ic_launcher{,_round}.webp    legacy pre-O icons
  ic_launcher-playstore.png             512px store listing icon

Requires: fontTools, Pillow, rsvg-convert, cwebp.

    python3 tools/icon/gen_icon.py [--font PATH] [--preview-only]
"""

from __future__ import annotations

import argparse
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile

from fontTools.misc.transform import Transform
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(REPO, "app", "src", "main", "res")
DEFAULT_FONT = (
    os.path.expanduser("~/Downloads/amazobitaemostrov/AmazOOSTROVv.2.ttf")
    if os.path.exists(os.path.expanduser("~/Downloads/amazobitaemostrov/AmazOOSTROVv.2.ttf"))
    else os.path.expanduser("~/Downloads/amazobitaemostrov/AmazOOSTROVItalic.ttf")
)

# ---------------------------------------------------------------- palette
BG_TOP, BG_BOTTOM = "#2F3474", "#191C42"      # background gradient
KB_TOP, KB_BOTTOM = "#5D9BFF", "#A78BFA"      # keyboard body gradient
BRAND_A, BRAND_B = "#4C8DF6", "#8B5CF6"       # monogram gradient (app seed colour)
PIN_FILL = "#FFFFFF"

# ---------------------------------------------------------------- geometry
C = 54.0                                       # canvas centre (108dp canvas)
SAFE_R = 32.5                                  # keep the lockup inside the 66dp circle

KB_W, KB_H, KB_R, KB_Y = 58.0, 36.0, 6.4, 51.0
KB_X = C - KB_W / 2
KEY_PAD, KEY_GAP, KEY_R = 4.4, 2.1, 1.2
KEY_ROWS = (5, 5)
SPACE_FRAC = 0.46

PIN_W, PIN_BODY, PIN_TIP, PIN_RX, PIN_TIPW, PIN_TOP = 41.0, 33.0, 9.5, 9.5, 4.9, 17.5
PIN_HALO = 1.6                                 # gap punched around the pin
PIN_CY = PIN_TOP + PIN_BODY / 2 - 0.6          # optical centre of the monogram
WM_W = PIN_W * 0.68

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def f(v: float) -> str:
    return f"{v:.2f}".rstrip("0").rstrip(".")


# ---------------------------------------------------------------- path builders
def rrect(x: float, y: float, w: float, h: float, r: float) -> str:
    """Rounded rectangle as SVG/VectorDrawable-safe path data (arcs only)."""
    return (f"M{f(x + r)},{f(y)} H{f(x + w - r)} A{f(r)},{f(r)} 0 0 1 {f(x + w)},{f(y + r)} "
            f"V{f(y + h - r)} A{f(r)},{f(r)} 0 0 1 {f(x + w - r)},{f(y + h)} "
            f"H{f(x + r)} A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y + h - r)} "
            f"V{f(y + r)} A{f(r)},{f(r)} 0 0 1 {f(x + r)},{f(y)} Z")


def pin_path(grow: float = 0.0) -> str:
    """Speech-bubble pin; `grow` inflates it to punch a gap in the keyboard."""
    w, body, tip = PIN_W + 2 * grow, PIN_BODY + 2 * grow, PIN_TIP + grow
    rx, tw, top = PIN_RX + grow, PIN_TIPW + grow * 1.05, PIN_TOP - grow
    l, r = C - w / 2, C + w / 2
    b, ty = top + body, top + body + tip
    return (f"M{f(l + rx)},{f(top)} H{f(r - rx)} A{f(rx)},{f(rx)} 0 0 1 {f(r)},{f(top + rx)} "
            f"V{f(b - rx)} A{f(rx)},{f(rx)} 0 0 1 {f(r - rx)},{f(b)} "
            f"H{f(C + tw)} L{f(C)},{f(ty)} L{f(C - tw)},{f(b)} "
            f"H{f(l + rx)} A{f(rx)},{f(rx)} 0 0 1 {f(l)},{f(b - rx)} "
            f"V{f(top + rx)} A{f(rx)},{f(rx)} 0 0 1 {f(l + rx)},{f(top)} Z")


def key_paths() -> list[str]:
    """Two letter rows plus a spacebar, inset in the keyboard body."""
    out = []
    ix, iy = KB_X + KEY_PAD, KB_Y + KEY_PAD
    iw, ih = KB_W - 2 * KEY_PAD, KB_H - 2 * KEY_PAD
    rows = len(KEY_ROWS) + 1
    row_h = (ih - KEY_GAP * (rows - 1)) / rows
    for ri, n in enumerate(KEY_ROWS):
        y = iy + ri * (row_h + KEY_GAP)
        kw = (iw - KEY_GAP * (n - 1)) / n
        out += [rrect(ix + ci * (kw + KEY_GAP), y, kw, row_h, KEY_R) for ci in range(n)]
    sw = iw * SPACE_FRAC
    out.append(rrect(ix + (iw - sw) / 2, iy + len(KEY_ROWS) * (row_h + KEY_GAP), sw, row_h, KEY_R))
    return out


def expand_implicit_lineto(d: str) -> str:
    """`M x,y x,y ...` -> `M x,y L x,y ...` (Android's parser copes, editors may not)."""
    def fix(m: re.Match) -> str:
        nums = re.findall(r"-?\d*\.?\d+", m.group(0))
        head = f"M{nums[0]},{nums[1]}"
        rest = "".join(f" L{nums[i]},{nums[i + 1]}" for i in range(2, len(nums) - 1, 2))
        return head + rest
    return re.sub(r"M[\s\-\d.]+", fix, d)


def monogram(font_path: str) -> tuple[str, float]:
    """WM outlines, scaled to WM_W and centred on the pin. Returns (path, height)."""
    font = TTFont(font_path)
    glyphs, cmap = font.getGlyphSet(), font.getBestCmap()
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.1f}")
    bounds = BoundsPen(glyphs)
    x = 0.0
    for ch in "WM":
        name = cmap[ord(ch)]
        glyphs[name].draw(TransformPen(pen, Transform(1, 0, 0, 1, x, 0)))
        glyphs[name].draw(TransformPen(bounds, Transform(1, 0, 0, 1, x, 0)))
        x += glyphs[name].width
    x0, y0, x1, y1 = bounds.bounds
    s = WM_W / (x1 - x0)
    h = (y1 - y0) * s
    # font units are y-up; flip and place so the monogram is centred at (C, PIN_CY)
    place = Transform(s, 0, 0, -s, C - WM_W / 2 - x0 * s, PIN_CY + h / 2 + y0 * s)
    out = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}")
    x = 0.0
    for ch in "WM":
        name = cmap[ord(ch)]
        glyphs[name].draw(TransformPen(out, place.transform((1, 0, 0, 1, x, 0))))
        x += glyphs[name].width
    return expand_implicit_lineto(out.getCommands()), h


def lockup_scale() -> float:
    """Uniform scale about the canvas centre that keeps the lockup safe-zone clean."""
    corners = [(x, y) for x in (KB_X, KB_X + KB_W) for y in (PIN_TOP, KB_Y + KB_H)]
    worst = max(math.hypot(x - C, y - C) for x, y in corners)
    return min(1.0, SAFE_R / worst)


# ---------------------------------------------------------------- svg emitters
SHADOW_FILTER = ('<filter id="sh" x="-30%" y="-30%" width="160%" height="175%">'
                 '<feDropShadow dx="0" dy="1.6" stdDeviation="1.7" flood-color="#0B0E33" '
                 'flood-opacity="0.34"/></filter>')


def gradient(gid: str, c1: str, c2: str) -> str:
    return (f'<linearGradient id="{gid}" x1="0" y1="0" x2="1" y2="1">'
            f'<stop offset="0" stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/>'
            f'</linearGradient>')


def svg(body: str, defs: str = "") -> str:
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" '
            f'viewBox="0 0 108 108"><defs>{defs}</defs>{body}</svg>')


def background_svg() -> str:
    return svg('<rect width="108" height="108" fill="url(#bg)"/>', gradient("bg", BG_TOP, BG_BOTTOM))


def foreground_svg(wm: str) -> str:
    s = lockup_scale()
    keys = "".join(f'<path d="{d}"/>' for d in key_paths())
    mask = (f'<mask id="kb"><rect width="108" height="108" fill="black"/>'
            f'<path d="{rrect(KB_X, KB_Y, KB_W, KB_H, KB_R)}" fill="white"/>'
            f'<g fill="black" fill-opacity="0.5">{keys}</g>'
            f'<path d="{pin_path(PIN_HALO)}" fill="black"/></mask>')
    inner = (f'<g filter="url(#sh)">'
             f'<rect width="108" height="108" fill="url(#kbg)" mask="url(#kb)"/>'
             f'<path d="{pin_path()}" fill="{PIN_FILL}"/></g>'
             f'<path d="{wm}" fill="url(#wmg)"/>')
    return svg(f'<g transform="translate({C},{C}) scale({s:.4f}) translate({-C},{-C})">{inner}</g>',
               SHADOW_FILTER + mask + gradient("kbg", KB_TOP, KB_BOTTOM)
               + gradient("wmg", BRAND_A, BRAND_B))


# ---------------------------------------------------------------- android emitters
def background_xml() -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:type="linear">
                <item
                    android:color="#FF{BG_TOP[1:]}"
                    android:offset="0.0" />
                <item
                    android:color="#FF{BG_BOTTOM[1:]}"
                    android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
"""


def monochrome_xml(wm: str) -> str:
    """Themed-icon layer: one even-odd path -- body filled, keys/pin-gap/WM knocked out."""
    s = lockup_scale()
    parts = [rrect(KB_X, KB_Y, KB_W, KB_H, KB_R)] + key_paths() + [pin_path(PIN_HALO), pin_path(), wm]
    data = " ".join(parts)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Themed-icon layer: keyboard + WM pin (Amaz Obitaem Ostrov Italic outlines).
         Even-odd fill knocks the keys, the pin gap and the monogram back out. -->
    <group
        android:pivotX="54"
        android:pivotY="54"
        android:scaleX="{s:.4f}"
        android:scaleY="{s:.4f}">
        <path
            android:fillColor="#FFFFFFFF"
            android:fillType="evenOdd"
            android:pathData="{data}" />
    </group>
</vector>
"""


# ---------------------------------------------------------------- raster pipeline
def need(tool: str) -> str:
    path = shutil.which(tool)
    if not path:
        sys.exit(f"missing required tool: {tool}")
    return path


def render(svg_text: str, px: int, tmp: str, name: str) -> str:
    src = os.path.join(tmp, f"{name}.svg")
    dst = os.path.join(tmp, f"{name}-{px}.png")
    with open(src, "w") as fh:
        fh.write(svg_text)
    subprocess.run(["rsvg-convert", "-w", str(px), "-h", str(px), src, "-o", dst], check=True)
    return dst


def to_webp(png: str, dst: str) -> None:
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    subprocess.run(["cwebp", "-lossless", "-exact", "-quiet", png, "-o", dst], check=True)


def legacy(compose: Image.Image, px: int, round_icon: bool) -> Image.Image:
    """Pre-O icon: centre 72dp of the adaptive canvas, masked to the legacy shape."""
    n = int(compose.width * 72 / 108)
    crop = compose.crop(((compose.width - n) // 2,) * 2 + ((compose.width + n) // 2,) * 2)
    crop = crop.resize((px * 4, px * 4), Image.LANCZOS)
    mask = Image.new("L", crop.size, 0)
    draw = ImageDraw.Draw(mask)
    if round_icon:
        draw.ellipse((0, 0, crop.width - 1, crop.height - 1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, crop.width - 1, crop.height - 1),
                               radius=int(crop.width * 0.12), fill=255)
    out = Image.new("RGBA", crop.size, (0, 0, 0, 0))
    out.paste(crop, (0, 0), mask)
    return out.resize((px, px), Image.LANCZOS)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--font", default=DEFAULT_FONT, help="Amaz Obitaem Ostrov Italic TTF")
    ap.add_argument("--preview-only", action="store_true", help="render a preview, write no assets")
    ap.add_argument("--preview", default=None, help="also write a 512px preview PNG here")
    args = ap.parse_args()

    if not os.path.exists(args.font):
        sys.exit(f"font not found: {args.font}")
    for tool in ("rsvg-convert", "cwebp"):
        need(tool)

    wm, _ = monogram(args.font)
    bg_svg, fg_svg = background_svg(), foreground_svg(wm)

    with tempfile.TemporaryDirectory() as tmp:
        store_bg = Image.open(render(bg_svg, 512, tmp, "bg")).convert("RGBA")
        store_fg = Image.open(render(fg_svg, 512, tmp, "fg")).convert("RGBA")
        store = Image.alpha_composite(store_bg, store_fg)

        if args.preview:
            store.save(args.preview)
        if args.preview_only:
            print("preview only, no assets written")
            return

        with open(os.path.join(RES, "drawable", "ic_launcher_background.xml"), "w") as fh:
            fh.write(background_xml())
        with open(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), "w") as fh:
            fh.write(monochrome_xml(wm))

        for dpi, factor in DENSITIES.items():
            px = int(round(108 * factor))
            to_webp(render(fg_svg, px, tmp, f"fg-{dpi}"),
                    os.path.join(RES, f"mipmap-{dpi}", "ic_launcher_fg.webp"))

            legacy_px = int(round(48 * factor))
            hi = Image.alpha_composite(
                Image.open(render(bg_svg, legacy_px * 4, tmp, f"bgL-{dpi}")).convert("RGBA"),
                Image.open(render(fg_svg, legacy_px * 4, tmp, f"fgL-{dpi}")).convert("RGBA"))
            for round_icon, name in ((False, "ic_launcher"), (True, "ic_launcher_round")):
                png = os.path.join(tmp, f"{name}-{dpi}.png")
                legacy(hi, legacy_px, round_icon).save(png)
                to_webp(png, os.path.join(RES, f"mipmap-{dpi}", f"{name}.webp"))

        store.save(os.path.join(REPO, "app", "src", "main", "ic_launcher-playstore.png"))

    print("icon assets regenerated")


if __name__ == "__main__":
    main()
