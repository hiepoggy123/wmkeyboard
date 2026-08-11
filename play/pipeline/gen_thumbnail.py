"""YouTube thumbnail (1280x720) for the trailer upload — Ink & Neon language.

Run:  python3 gen_thumbnail.py
Out:  ../trailer/out/thumbnail.png (also .jpg under 2 MB for YouTube)
"""

from pathlib import Path

from PIL import Image

from wmkit import (AMBER, BODY, CYAN, INK_HI, MAGENTA, SS, VIOLET, blob,
                   canvas, chip, font, gradient_text, grain, phone, save, text,
                   vignette)

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "play" / "trailer" / "out"
W, H = 1280, 720


def main() -> Path:
    img = canvas(W, H)
    blob(img, 220, 110, 420, VIOLET, alpha=70)
    blob(img, 1130, 620, 400, CYAN, alpha=48)

    # left text block
    logo = Image.open(ROOT / "docs" / "src" / "assets" / "logo-mark.png")
    logo = logo.resize((96 * SS, 96 * SS), Image.LANCZOS)
    img.paste(logo, (84 * SS, 96 * SS), logo)

    gradient_text(img, (200, 122), "WM Keyboard",
                  font("Manrope", 78, 800), VIOLET, CYAN, glow=8)
    text(img, (88, 250), "Private. Offline. Yours.",
         font("Manrope", 58, 700))
    text(img, (88, 340), "352 languages · themes · a full toolbox",
         font("Inter", 34, 480), fill=BODY)

    y = 430
    x = 88
    for label, accent in (("Glide typing", VIOLET), ("Offline AI", CYAN),
                          ("Theme editor", MAGENTA), ("40+ tools", AMBER)):
        w, _ = chip(img, x, y, label, accent=accent,
                    fnt=font("Inter", 30, 540), pad_x=26, pad_y=14, dot=True)
        x += w + 16
        if x > 560:
            x = 88
            y += 66

    # phone on the right, keyboard half only
    shot = Image.open(ROOT / "play" / "screencaps" / "kb-english-dark.png")
    phone(img, shot, 812, 96, 380, crop_top=1000)

    vignette(img)
    grain(img)
    out_png = save(img, OUT / "thumbnail.png")

    jpg = Image.open(out_png).convert("RGB")
    jpg.save(OUT / "thumbnail.jpg", "JPEG", quality=92, optimize=True)
    return out_png


if __name__ == "__main__":
    print(main())
