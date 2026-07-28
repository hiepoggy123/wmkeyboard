#!/usr/bin/env python3
"""Export a CLDR language's emoji annotations as an importable keyword pack.

The app bundles English and Bengali emoji keywords; every other language is
covered by importing a pack under Settings → Emoji → Emoji keywords. CLDR
already has hand-translated annotations for ~100 languages, so a pack for any
of them is a format conversion rather than a translation job.

Inputs (downloaded, not committed):
  annotations_<lang>.xml         CLDR common/annotations/<lang>.xml
  annotationsDerived_<lang>.xml  CLDR common/annotationsDerived/<lang>.xml
                                 (optional; it carries the ZWJ sequences)

Output: the pack TSV the importer reads —
  emoji <TAB> keyword,keyword,... <TAB> name

`name` is CLDR's `tts` annotation, the spoken name, which becomes the emoji's
long-press description in that language.

Usage:
  python3 tools/emoji/export_keyword_pack.py \
      --annotations /path/to/hi.xml \
      [--derived /path/to/annotationsDerived/hi.xml] \
      [--catalog app/src/main/assets/emoji/catalog.tsv] \
      --out hi.tsv
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SPLIT_RE = re.compile(r"[|:,()“”]")


def load_catalog(path: Path) -> set[str]:
    emoji = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        # "# " opens a comment; the keycap hash row starts with a bare "#".
        if not line.strip() or line.startswith("# "):
            continue
        emoji.add(line.split("\t")[0].strip())
    return emoji


def load_annotations(*paths: Path) -> dict[str, tuple[list[str], str | None]]:
    """cp -> (keywords, name), merged across files in order."""
    result: dict[str, tuple[list[str], str | None]] = {}
    for path in paths:
        if path is None or not path.exists():
            continue
        root = ET.parse(path).getroot()
        for node in root.iter("annotation"):
            cp = node.get("cp")
            text = (node.text or "").strip()
            if not cp or not text:
                continue
            words, name = result.setdefault(cp, ([], None))
            if node.get("type") == "tts":
                # The spoken name doubles as the display name and as a strong
                # keyword ("flag: Bangladesh" keeps "Bangladesh").
                name = text
                parts = [text]
            else:
                parts = [w.strip() for w in text.split("|")]
            for part in parts:
                for word in SPLIT_RE.split(part):
                    word = word.strip().lower()
                    if word and word not in words:
                        words.append(word)
            result[cp] = (words, name)
    return result


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--annotations", type=Path, required=True)
    parser.add_argument("--derived", type=Path)
    parser.add_argument(
        "--catalog", type=Path,
        default=root / "app/src/main/assets/emoji/catalog.tsv",
    )
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    catalog = load_catalog(args.catalog)
    annotations = load_annotations(args.annotations, args.derived)
    if not annotations:
        print(f"no annotations found in {args.annotations}", file=sys.stderr)
        return 1

    lines = ["# emoji\tkeywords\tname  (CLDR annotations)"]
    written = skipped = 0
    for emoji, (words, name) in sorted(annotations.items()):
        # Rows for emoji the app doesn't carry would import as dead weight;
        # the pack format tolerates them, but there is no reason to ship them.
        if emoji not in catalog:
            skipped += 1
            continue
        if not words:
            continue
        lines.append(f"{emoji}\t{','.join(words)}\t{name or ''}".rstrip("\t"))
        written += 1

    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {written} emoji to {args.out} ({skipped} not in the catalog)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
