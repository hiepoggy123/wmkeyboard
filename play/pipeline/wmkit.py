"""Ink & Neon design kit for WM Keyboard Play Store graphics.

Everything renders at SS× supersample and downsamples with Lanczos at save
time, so edges, glows and thin callout lines stay crisp.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).parent
FONTS = ROOT / "fonts"
SS = 2  # supersample factor

# ---------------------------------------------------------------- palette --
INK = (10, 13, 20)          # canvas base
INK_HI = (16, 21, 32)       # slightly lifted panel
VIOLET = (124, 92, 255)
CYAN = (34, 211, 238)
MAGENTA = (255, 78, 205)
LIME = (163, 230, 53)
AMBER = (251, 191, 36)
WHITE = (255, 255, 255)
BODY = (168, 179, 199)      # secondary text
CHIP_TEXT = (220, 227, 240)

_font_cache: dict[tuple, ImageFont.FreeTypeFont] = {}


def font(name: str, size: int, weight: int = 400) -> ImageFont.FreeTypeFont:
    """Variable-font loader with weight axis pinning. Sizes are in FINAL px;
    the SS multiplier is applied here so call sites think in design units."""
    key = (name, size, weight)
    if key in _font_cache:
        return _font_cache[key]
    f = ImageFont.truetype(str(FONTS / f"{name}.ttf"), size * SS,
                           layout_engine=ImageFont.Layout.RAQM)
    try:
        axes = f.get_variation_axes()
        vals = []
        for ax in axes:
            n = ax["name"]
            n = n.decode() if isinstance(n, bytes) else str(n)
            if "Weight" in n:
                vals.append(max(ax["minimum"], min(ax["maximum"], weight)))
            elif "Optical" in n:
                vals.append(ax["maximum"])  # display cuts
            else:
                vals.append(ax["default"])
        f.set_variation_by_axes(vals)
    except Exception:
        pass
    _font_cache[key] = f
    return f


def measure(draw: ImageDraw.ImageDraw, text: str, fnt) -> tuple[int, int]:
    box = draw.textbbox((0, 0), text, font=fnt)
    return box[2] - box[0], box[3] - box[1]


# ---------------------------------------------------------------- canvas ---
def canvas(w: int, h: int) -> Image.Image:
    return Image.new("RGB", (w * SS, h * SS), INK)


def blob(img: Image.Image, cx: int, cy: int, r: int, color, alpha: int = 90,
         blur: int = 220) -> None:
    """Soft radial glow blob, composited additively-ish via alpha."""
    cx, cy, r, blur = cx * SS, cy * SS, r * SS, blur * SS
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    img.paste(Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB"))


def vignette(img: Image.Image, strength: int = 110) -> None:
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse([-w * 0.25, -h * 0.18, w * 1.25, h * 1.12], fill=strength)
    mask = mask.filter(ImageFilter.GaussianBlur(min(w, h) // 6))
    dark = Image.new("RGB", (w, h), (0, 0, 0))
    img.paste(Image.composite(img, dark, mask.point(lambda p: 255 - (strength - p))))


def grain(img: Image.Image, opacity: int = 6) -> None:
    import random
    random.seed(7)
    w, h = img.size
    noise = Image.effect_noise((w // 2, h // 2), 22).resize((w, h))
    img.paste(Image.blend(img, Image.merge("RGB", (noise, noise, noise)), opacity / 255))


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def h_gradient(size, c1, c2) -> Image.Image:
    w, h = size
    g = Image.new("RGB", (256, 1))
    for x in range(256):
        g.putpixel((x, 0), lerp(c1, c2, x / 255))
    return g.resize((w, h))


def gradient_text(img: Image.Image, xy, text: str, fnt, c1, c2,
                  anchor: str = "la", glow: int = 0) -> tuple[int, int, int, int]:
    """Draw text filled with a horizontal gradient; optional soft glow."""
    d = ImageDraw.Draw(img)
    box = d.textbbox((xy[0] * SS, xy[1] * SS), text, font=fnt, anchor=anchor)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).text((xy[0] * SS, xy[1] * SS), text, font=fnt,
                              fill=255, anchor=anchor)
    grad = h_gradient(img.size, c1, c2)
    if glow:
        glow_layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        glow_layer.paste(grad.convert("RGBA"), (0, 0),
                         mask.filter(ImageFilter.GaussianBlur(glow * SS)))
        img.paste(Image.alpha_composite(img.convert("RGBA"), glow_layer).convert("RGB"))
    img.paste(grad, (0, 0), mask)
    return tuple(v // SS for v in box)


def text(img: Image.Image, xy, s: str, fnt, fill=WHITE, anchor: str = "la"):
    d = ImageDraw.Draw(img)
    d.text((xy[0] * SS, xy[1] * SS), s, font=fnt, fill=fill, anchor=anchor)
    box = d.textbbox((xy[0] * SS, xy[1] * SS), s, font=fnt, anchor=anchor)
    return tuple(v // SS for v in box)


# ----------------------------------------------------------------- shapes --
def rrect(img: Image.Image, box, radius: int, fill=None, outline=None,
          width: int = 1) -> None:
    d = ImageDraw.Draw(img, "RGBA")
    d.rounded_rectangle([v * SS for v in box], radius * SS, fill=fill,
                        outline=outline, width=width * SS)


def shadow(img: Image.Image, box, radius: int, blur: int, color=(0, 0, 0),
           alpha: int = 160, offset=(0, 30)) -> None:
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    b = [(box[0] + offset[0]) * SS, (box[1] + offset[1]) * SS,
         (box[2] + offset[0]) * SS, (box[3] + offset[1]) * SS]
    d.rounded_rectangle(b, radius * SS, fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(blur * SS))
    img.paste(Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB"))


# ------------------------------------------------------------------ phone --
def phone(img: Image.Image, shot: Image.Image, x: int, y: int, w: int,
          glow_colors=(VIOLET, CYAN), crop_top: int = 0) -> tuple[int, int, int, int]:
    """Composite a device screenshot in a slim dark bezel at (x, y) top-left,
    scaled to width w (design units). Returns the outer box (design units).
    crop_top removes that many source pixels from the top (e.g. status bar)."""
    if crop_top:
        shot = shot.crop((0, crop_top, shot.width, shot.height))
    ratio = shot.height / shot.width
    sw, sh = w * SS, int(w * ratio) * SS
    shot = shot.resize((sw, sh), Image.LANCZOS)

    bezel = 7
    r_screen = 46
    ob = (x - bezel, y - bezel, x + w + bezel, y + w * ratio + bezel)

    # colored under-glow, then drop shadow
    glow_layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow_layer)
    mid = ((ob[1] + ob[3]) / 2)
    gd.rounded_rectangle([ob[0] * SS, ob[1] * SS, ob[2] * SS, mid * SS],
                         (r_screen + bezel) * SS, fill=glow_colors[0] + (70,))
    gd.rounded_rectangle([ob[0] * SS, mid * SS, ob[2] * SS, ob[3] * SS],
                         (r_screen + bezel) * SS, fill=glow_colors[1] + (70,))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(70 * SS))
    img.paste(Image.alpha_composite(img.convert("RGBA"), glow_layer).convert("RGB"))
    shadow(img, ob, r_screen + bezel, 40, alpha=180, offset=(0, 26))

    rrect(img, ob, r_screen + bezel, fill=(21, 26, 35, 255),
          outline=(255, 255, 255, 26), width=1)

    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw, sh], r_screen * SS, fill=255)
    img.paste(shot, (x * SS, y * SS), mask)
    return ob


# ------------------------------------------------------------------- chip --
def chip(img: Image.Image, x: int, y: int, s: str, accent=None,
         fnt=None, pad_x: int = 22, pad_y: int = 12, dot: bool = False) -> tuple[int, int]:
    """Pill chip at (x, y) top-left. Returns (w, h) in design units."""
    fnt = fnt or font("Inter", 27, 520)
    d = ImageDraw.Draw(img, "RGBA")
    tw, th = measure(d, s, fnt)
    tw, th = tw // SS, th // SS
    dot_w = 16 if dot else 0
    w, h = tw + pad_x * 2 + dot_w, th + pad_y * 2
    fill = (255, 255, 255, 13)
    outline = accent + (110,) if accent else (255, 255, 255, 26)
    d.rounded_rectangle([x * SS, y * SS, (x + w) * SS, (y + h) * SS],
                        (h // 2) * SS, fill=fill, outline=outline, width=SS)
    tx = x + pad_x + dot_w
    if dot:
        cy = y + h / 2
        d.ellipse([(x + pad_x - 2) * SS, (cy - 4) * SS,
                   (x + pad_x + 6) * SS, (cy + 4) * SS], fill=accent + (255,))
    d.text(((tx) * SS, (y + h / 2) * SS), s, font=fnt,
           fill=accent if accent else CHIP_TEXT, anchor="lm")
    return w, h


def save(img: Image.Image, path: str | Path, final_size=None) -> Path:
    w, h = img.size
    out = img.resize((w // SS, h // SS), Image.LANCZOS)
    if final_size:
        out = out.resize(final_size, Image.LANCZOS)
    path = Path(path)
    out.convert("RGB").save(path, "PNG")
    return path
