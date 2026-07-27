# Launcher icon

The app mark is a keyboard body with a speech-bubble pin carrying the **WM**
monogram, drawn on a deep-indigo gradient. Everything is defined once, in
`gen_icon.py`, on the 108dp adaptive-icon canvas; the whole lockup is scaled to
stay inside the 66dp safe circle, so no launcher mask (circle, squircle, rounded
square, teardrop) clips it.

## Regenerate

```bash
python3 tools/icon/gen_icon.py
```

Needs `fontTools`, `Pillow`, `rsvg-convert` (librsvg) and `cwebp` (webp), plus
the monogram font. Pass `--font PATH` if it is not at
`~/Downloads/amazobitaemostrov/AmazOOSTROVItalic.ttf`, and `--preview-only
--preview out.png` to look before writing anything.

## What it writes

| Output | Purpose |
| --- | --- |
| `drawable/ic_launcher_background.xml` | adaptive background — linear gradient vector |
| `drawable/ic_launcher_monochrome.xml` | Android 13+ themed icon — one even-odd path |
| `mipmap-*/ic_launcher_fg.webp` | adaptive foreground (raster: the drop shadow needs a blur) |
| `mipmap-*/ic_launcher{,_round}.webp` | legacy pre-O icons, centre-72dp crop |
| `app/src/main/ic_launcher-playstore.png` | 512px store icon, full bleed |

## Addon-repository variant

`gen_addon_icon.py` renders the same lockup with a puzzle piece in the pin
instead of the monogram — the mark for an addon repository. It imports
`gen_icon.py`, so palette, geometry and safe-zone scale stay in step.

```bash
python3 tools/icon/gen_addon_icon.py --out ../wmkeyboard-addon-repository/icon.png
```

`--size` defaults to 256px (what `repo.icon` in the repo format wants); it needs
no font.

## Notes

- The monogram is **Amaz Obitaem Ostrov Italic**, converted to outlines. The
  font file is never committed or shipped — only the resulting path data.
- Palette follows the app seed colour (`#4C8DF6` → `#8B5CF6`); the keyboard body
  uses a lighter tint of the same ramp so it stays readable on the dark ground.
- To restyle, edit the palette/geometry constants at the top of `gen_icon.py`
  and re-run; the raster, vector and themed layers all derive from them.
