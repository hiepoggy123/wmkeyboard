#!/usr/bin/env python3
"""Generate the bundled emoji catalog and variant tables from Unicode data.

Inputs (downloaded, not committed):
  emoji-test.txt            https://unicode.org/Public/emoji/latest/emoji-test.txt
  annotations_en.xml        CLDR common/annotations/en.xml
  annotations_bn.xml        CLDR common/annotations/bn.xml
  annotationsDerived_en.xml CLDR common/annotationsDerived/en.xml
  annotationsDerived_bn.xml CLDR common/annotationsDerived/bn.xml

Outputs (written into app/src/main/assets/emoji/):
  catalog.tsv   emoji <TAB> category <TAB> en,keywords <TAB> bn,keywords [<TAB> parent]
                One row per fully-qualified, tone-free emoji. `parent` marks
                gender/role variants that the palette grid collapses under
                their base emoji (they stay searchable).
  variants.tsv  base <TAB> tones <TAB> sequence
                Exact RGI skin-toned sequences keyed by their tone-free base.
                `tones` is a comma list of Fitzpatrick indices 1..5 in the
                order the modifiers appear (1 entry = uniform/single tone,
                2 entries = per-person tones, e.g. the handshake).

Usage:
  python3 tools/emoji/generate_catalog.py --data-dir /path/to/downloads \
      [--old-catalog app/src/main/assets/emoji/catalog.tsv]

Keywords are CLDR annotations (en + bn) merged with any hand-curated
keywords found in the previous catalog for the same emoji.
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

TONE_CPS = {0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF}
FEMALE, MALE, ZWJ, VS16 = 0x2640, 0x2642, 0x200D, 0xFE0F
MAN, WOMAN, ADULT = 0x1F468, 0x1F469, 0x1F9D1

# Unicode group -> app category. "sky & weather" moves to `nature` to match
# the app's historical placement of the sun/rainbow/etc.
GROUP_MAP = {
    "Smileys & Emotion": "smileys",
    "People & Body": "people",
    "Food & Drink": "food",
    "Travel & Places": "travel",
    "Activities": "activities",
    "Objects": "objects",
    "Symbols": "symbols",
    "Flags": "flags",
}

# Bases whose gendered/role variants collapse into them in the palette grid.
# name-prefix groups: "kiss: woman, man" -> kiss, "family: man, boy" -> family.
NAME_PREFIX_PARENTS = {
    "kiss": "💏",
    "couple with heart": "💑",
    "family": "👪",
}

# Mixed-tone multi-person sequences decompose (tones stripped) to a ZWJ shape
# that is not itself the RGI neutral emoji; map those shapes to the palette
# emoji whose variant popup should own them.
ZWJ = 0x200D
ORPHAN_BASE_MAP = {
    (0x1FAF1, ZWJ, 0x1FAF2): "🤝",                       # handshake
    (0x1F469, ZWJ, 0x1F91D, ZWJ, 0x1F468): "👫",         # woman+man holding hands
    (0x1F468, ZWJ, 0x1F91D, ZWJ, 0x1F468): "👬",         # men holding hands
    (0x1F469, ZWJ, 0x1F91D, ZWJ, 0x1F469): "👭",         # women holding hands
    (0x1F9D1, ZWJ, 0x1FAEF, ZWJ, 0x1F9D1): "🤼",         # people wrestling (E17)
    (0x1F468, ZWJ, 0x1FAEF, ZWJ, 0x1F468): "🤼‍♂️",  # men wrestling
    (0x1F469, ZWJ, 0x1FAEF, ZWJ, 0x1F469): "🤼‍♀️",  # women wrestling
    (0x1F9D1, ZWJ, 0x1F430, ZWJ, 0x1F9D1): "👯",         # people w/ bunny ears (E17)
    (0x1F468, ZWJ, 0x1F430, ZWJ, 0x1F468): "👯‍♂️",  # men w/ bunny ears
    (0x1F469, ZWJ, 0x1F430, ZWJ, 0x1F469): "👯‍♀️",  # women w/ bunny ears
    (0x1F9D1, ZWJ, 0x2764, 0xFE0F, ZWJ, 0x1F48B, ZWJ, 0x1F9D1): "💏",  # kiss
    (0x1F9D1, ZWJ, 0x2764, 0xFE0F, ZWJ, 0x1F9D1): "💑",  # couple with heart
}


def cps(s: str) -> list[int]:
    return [ord(c) for c in s]


def from_cps(points: list[int]) -> str:
    return "".join(chr(c) for c in points)


def strip_tones(points: list[int]) -> tuple[list[int], list[int]]:
    """Remove tone modifiers; returns (stripped, tone indices 1..5 in order)."""
    out, tones = [], []
    for cp in points:
        if cp in TONE_CPS:
            tones.append(cp - 0x1F3FA)
        else:
            out.append(cp)
    return out, tones


def parse_emoji_test(path: Path):
    """Yield (emoji, name, group, subgroup) for fully-qualified sequences."""
    group = subgroup = ""
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("# group:"):
            group = line.split(":", 1)[1].strip()
        elif line.startswith("# subgroup:"):
            subgroup = line.split(":", 1)[1].strip()
        elif line and not line.startswith("#"):
            body, _, comment = line.partition("#")
            fields = body.split(";")
            if len(fields) < 2 or fields[1].strip() != "fully-qualified":
                continue
            points = [int(h, 16) for h in fields[0].split()]
            # comment: " 😀 E1.0 grinning face"
            m = re.match(r"\s*\S+\s+E[\d.]+\s+(.*)", comment)
            name = m.group(1).strip() if m else ""
            yield from_cps(points), name, group, subgroup


def load_annotations(*paths: Path) -> dict[str, list[str]]:
    """cp -> keyword list, from CLDR annotation XML files (later files add)."""
    result: dict[str, list[str]] = {}
    for path in paths:
        if not path.exists():
            continue
        root = ET.parse(path).getroot()
        for node in root.iter("annotation"):
            cp = node.get("cp")
            text = (node.text or "").strip()
            if not cp or not text:
                continue
            words = result.setdefault(cp, [])
            if node.get("type") == "tts":
                # The name itself is a strong keyword ("flag: Bangladesh"
                # -> "flag Bangladesh" split below keeps "Bangladesh").
                parts = [text]
            else:
                parts = [w.strip() for w in text.split("|")]
            for part in parts:
                for w in re.split(r"[|:,()“”]", part):
                    w = w.strip().lower()
                    if w and w not in words:
                        words.append(w)
    return result


def load_old_catalog(path: Path | None) -> dict[str, tuple[list[str], list[str]]]:
    """emoji -> (en keywords, bn keywords) from the previous curated TSV."""
    if path is None or not path.exists():
        return {}
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        en = [w.strip().lower() for w in parts[2].split(",") if w.strip()]
        bn = [w.strip() for w in parts[3].split(",") if w.strip()] if len(parts) > 3 else []
        out[parts[0].strip()] = (en, bn)
    return out


BN_RANGE = re.compile(r"[ঀ-৿]")


def is_bengali(word: str) -> bool:
    return bool(BN_RANGE.search(word))


def category_for(group: str, subgroup: str) -> str | None:
    if group == "Component":
        return None
    if group == "Animals & Nature":
        return "animals" if subgroup.startswith("animal") else "nature"
    if group == "Travel & Places" and subgroup == "sky & weather":
        return "nature"
    return GROUP_MAP.get(group)


def find_parent(emoji: str, name: str, all_tone_free: set[str]) -> str | None:
    """Return the base emoji this entry should collapse under, if any."""
    points = cps(emoji)
    # Rule 1: drop a ZWJ+gender-sign segment anywhere (🏃‍♀️ -> 🏃,
    # 🏃‍♀️‍➡️ -> 🏃‍➡️) when the remainder is itself an emoji.
    for i, cp in enumerate(points):
        if cp in (FEMALE, MALE) and i > 0 and points[i - 1] == ZWJ:
            end = i + 2 if i + 1 < len(points) and points[i + 1] == VS16 else i + 1
            base = from_cps(points[: i - 1] + points[end:])
            if base in all_tone_free:
                return base
    # Rule 2: MAN/WOMAN-led ZWJ sequence with a PERSON-led sibling
    # (👨‍⚕️ / 👩‍⚕️ -> 🧑‍⚕️, 👨‍🦰 -> 🧑‍🦰).
    if len(points) > 1 and points[0] in (MAN, WOMAN) and ZWJ in points:
        base = from_cps([ADULT] + points[1:])
        if base in all_tone_free:
            return base
    # Rule 3: named combination groups (kiss/couple/family renderings).
    prefix = name.split(":", 1)[0].strip().lower()
    parent = NAME_PREFIX_PARENTS.get(prefix)
    if parent and ":" in name and parent in all_tone_free and emoji != parent:
        return parent
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", required=True, type=Path)
    ap.add_argument("--old-catalog", type=Path, default=None)
    repo = Path(__file__).resolve().parents[2]
    ap.add_argument(
        "--out-dir", type=Path, default=repo / "app/src/main/assets/emoji"
    )
    args = ap.parse_args()

    rows = list(parse_emoji_test(args.data_dir / "emoji-test.txt"))
    en = load_annotations(
        args.data_dir / "annotations_en.xml", args.data_dir / "annotationsDerived_en.xml"
    )
    bn = load_annotations(
        args.data_dir / "annotations_bn.xml", args.data_dir / "annotationsDerived_bn.xml"
    )
    old = load_old_catalog(args.old_catalog)

    tone_free: list[tuple[str, str, str, str]] = []  # emoji, name, group, subgroup
    variants: dict[str, list[tuple[str, str]]] = {}  # base -> [(tones, sequence)]
    seen = set()
    for emoji, name, group, subgroup in rows:
        points = cps(emoji)
        stripped, tones = strip_tones(points)
        if tones:
            base = from_cps(stripped)
            # Uniform multi-tone renders collapse to one index; per-person
            # tones (handshake, couples) keep each index in order.
            key = ",".join(str(t) for t in (tones if len(set(tones)) > 1 else tones[:1]))
            variants.setdefault(base, []).append((key, emoji))
        else:
            if emoji not in seen:
                seen.add(emoji)
                tone_free.append((emoji, name, group, subgroup))

    all_tone_free = {e for e, _, _, _ in tone_free}
    # Index of VS16-free form -> canonical emoji, for bases whose toned form
    # drops the presentation selector (🏋️‍♀️ toned has no FE0F after 🏋).
    no_vs16 = {from_cps([c for c in cps(e) if c != VS16]): e for e in all_tone_free}
    remap = {}
    for base in list(variants):
        if base in all_tone_free:
            continue
        stripped_key = from_cps([c for c in cps(base) if c != VS16])
        mapped = ORPHAN_BASE_MAP.get(tuple(cps(base))) or no_vs16.get(stripped_key)
        if mapped:
            remap[base] = mapped
        else:
            print(f"warning: orphan variant base {base!r} ({[hex(c) for c in cps(base)]})",
                  file=sys.stderr)
    for src, dst in remap.items():
        variants.setdefault(dst, []).extend(variants.pop(src))

    catalog_lines = ["# emoji\tcategory\ten keywords\tbn keywords\tparent"]
    kept = 0
    for emoji, name, group, subgroup in tone_free:
        category = category_for(group, subgroup)
        if category is None:
            continue
        en_words = list(en.get(emoji, []))
        bn_words = list(bn.get(emoji, []))
        # Fall back to the VS16-free key CLDR sometimes uses.
        if not en_words:
            en_words = list(en.get(from_cps([c for c in cps(emoji) if c != VS16]), []))
        if not bn_words:
            bn_words = list(bn.get(from_cps([c for c in cps(emoji) if c != VS16]), []))
        for w in name.lower().replace(":", " ").replace(",", " ").split():
            if w not in en_words:
                en_words.append(w)
        old_en, old_bn = old.get(emoji, ([], []))
        for w in old_en:
            if not is_bengali(w) and w not in en_words:
                en_words.append(w)
        for w in old_bn + [x for x in old_en if is_bengali(x)]:
            if w not in bn_words:
                bn_words.append(w)
        parent = find_parent(emoji, name, all_tone_free) or ""
        en_col = ",".join(en_words)
        bn_col = ",".join(bn_words)
        line = f"{emoji}\t{category}\t{en_col}\t{bn_col}"
        if parent:
            line += f"\t{parent}"
        catalog_lines.append(line)
        kept += 1

    variant_lines = ["# base\ttones\tsequence"]
    n_var = 0
    for base, entries in variants.items():
        for key, seq in entries:
            variant_lines.append(f"{base}\t{key}\t{seq}")
            n_var += 1

    args.out_dir.mkdir(parents=True, exist_ok=True)
    (args.out_dir / "catalog.tsv").write_text("\n".join(catalog_lines) + "\n", encoding="utf-8")
    (args.out_dir / "variants.tsv").write_text("\n".join(variant_lines) + "\n", encoding="utf-8")
    print(f"catalog: {kept} emojis; variants: {n_var} toned sequences "
          f"({sum(1 for b in variants for k, _ in variants[b] if ',' in k)} dual-tone)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
