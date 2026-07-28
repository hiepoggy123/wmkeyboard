#!/usr/bin/env python3
"""Add the CLDR/Unicode display name column to an existing catalog.tsv.

The catalog generator (generate_catalog.py) writes an optional trailing
`parent` column. This script rewrites every row into the fixed six-column
form so the name can live at a stable index:

    emoji <TAB> category <TAB> en,keywords <TAB> bn,keywords <TAB> parent <TAB> name

`parent` may be empty; `name` is the Unicode short name from emoji-test.txt
("grinning face", "flag: Bangladesh"). Rows whose emoji has no entry there
fall back to the presentation-selector-free form before giving up.

Usage:
  python3 tools/emoji/add_names.py --emoji-test /path/to/emoji-test.txt \
      [--catalog app/src/main/assets/emoji/catalog.tsv]
"""

from __future__ import annotations

import argparse
from pathlib import Path

VS16 = 0xFE0F


def cps(s: str) -> list[int]:
    return [ord(c) for c in s]


def from_cps(points: list[int]) -> str:
    return "".join(chr(c) for c in points)


def parse_names(path: Path) -> dict[str, str]:
    names: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#") or "#" not in line:
            continue
        data, comment = line.split("#", 1)
        if "fully-qualified" not in data:
            continue
        parts = comment.strip().split(" ", 2)
        if len(parts) < 3:
            continue
        emoji, name = parts[0], parts[2].strip()
        names.setdefault(emoji, name)
    return names


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--emoji-test", required=True, type=Path)
    ap.add_argument(
        "--catalog",
        type=Path,
        default=Path("app/src/main/assets/emoji/catalog.tsv"),
    )
    args = ap.parse_args()

    names = parse_names(args.emoji_test)
    # Secondary index on the VS16-free form: the catalog sometimes carries a
    # presentation selector emoji-test.txt does not, and vice versa.
    stripped = {from_cps([c for c in cps(e) if c != VS16]): n for e, n in names.items()}

    out = ["# emoji\tcategory\ten keywords\tbn keywords\tparent\tname"]
    missing = 0
    total = 0
    for line in args.catalog.read_text(encoding="utf-8").splitlines():
        # "# " opens a comment, a bare "#" does not: the keycap hash emoji
        # (#️⃣) is a data row starting with one, and this
        # loop rewrites the file — skipping it here deleted it outright.
        if not line or line.startswith("# "):
            continue
        parts = line.split("\t")
        if len(parts) < 4:
            continue
        total += 1
        emoji = parts[0]
        parent = parts[4] if len(parts) > 4 else ""
        name = names.get(emoji) or stripped.get(
            from_cps([c for c in cps(emoji) if c != VS16]), ""
        )
        if not name:
            missing += 1
            print(f"warning: no name for {emoji!r}")
        out.append(f"{emoji}\t{parts[1]}\t{parts[2]}\t{parts[3]}\t{parent}\t{name}")

    args.catalog.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"wrote {len(out) - 1} rows to {args.catalog} ({missing}/{total} unnamed)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
