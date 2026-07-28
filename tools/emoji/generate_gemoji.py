#!/usr/bin/env python3
"""Generate the bundled emoji shortcode and trigger tables from gemoji.

Shortcodes are the `:tada:` / `:+1:` / `:shrug:` names people already type in
GitHub Markdown, Discord and Slack. The canonical list is GitHub's own gemoji
database; Discord and Slack use the emoji-toolkit short names, which overlap
with gemoji almost entirely — the handful of spellings they differ on are
listed in EXTRA_ALIASES below.

Triggers are the other half of the same data, read as prediction rather than
lookup: which *typed word* should offer which emoji. gemoji's single-word
aliases and its hand-picked `tags` are exactly that, and they cover far more
ground than a hand-written table can.

Inputs (downloaded, not committed):
  emoji.json    https://raw.githubusercontent.com/github/gemoji/master/db/emoji.json
                (github/gemoji, MIT)

Outputs (written into app/src/main/assets/emoji/):
  shortcodes.tsv   shortcode <TAB> emoji
                   One row per alias, sorted. Only emoji that exist in
                   catalog.tsv are emitted — a shortcode for an emoji the
                   palette doesn't carry would be a dead search result.
  triggers.tsv     word <TAB> emoji,emoji,...
                   Word → emoji predictions, best first. Multi-word aliases
                   are dropped (nobody types "heart_eyes" as a word) and so
                   are names under MIN_TRIGGER_LEN, which is what keeps the
                   two-letter country codes (:it:, :de:, :us:) from firing on
                   ordinary words.

Usage:
  python3 tools/emoji/generate_gemoji.py --gemoji /path/to/emoji.json \
      [--catalog app/src/main/assets/emoji/catalog.tsv] \
      [--out app/src/main/assets/emoji/shortcodes.tsv] \
      [--triggers app/src/main/assets/emoji/triggers.tsv]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

# Spellings Discord and Slack use that gemoji does not carry. Kept small and
# hand-checked: each is a name someone genuinely types expecting a hit, not a
# mechanical variation. A pair here that gemoji already maps to the *same*
# emoji is dropped silently; one that disagrees with gemoji is a hard error,
# since it would mean two chat clients disagree and the table has to pick.
EXTRA_ALIASES = [
    # Discord's shorter face names.
    ("slight_smile", "🙂"),
    ("slight_frown", "🙁"),
    ("upside_down", "🙃"),
    ("rolling_eyes", "🙄"),
    ("zipper_mouth", "🤐"),
    ("money_mouth", "🤑"),
    ("hugging", "🤗"),
    ("nerd", "🤓"),
    ("thinking", "🤔"),
    ("face_vomiting", "🤮"),
    ("face_with_monocle", "🧐"),
    ("face_with_raised_eyebrow", "🤨"),
    ("face_with_hand_over_mouth", "🤭"),
    ("face_with_symbols_over_mouth", "🤬"),
    ("smiling_face_with_3_hearts", "🥰"),
    ("grinning_face_with_star_eyes", "🤩"),
    ("person_shrugging", "🤷"),
    ("person_facepalming", "🤦"),
    ("face_palm", "🤦"),
    ("thumbup", "👍"),
    ("thumbdown", "👎"),
    # Slack.
    ("simple_smile", "🙂"),
    ("white_frowning_face", "☹️"),
    # Long-standing informal spellings.
    ("laughing_crying", "😂"),
    ("crossed_fingers", "🤞"),
]

SHORTCODE_RE = re.compile(r"^[a-z0-9_+-]+$")

# Trigger words shorter than this are dropped. Two-letter aliases are almost
# all flag codes (:it:, :de:, :us:) and enclosed letters (:a:, :x:, :m:) —
# valid shortcodes, but firing them at anyone who types "it" or "us" is the
# wrong-emoji-is-worse-than-no-emoji case the suggester exists to avoid.
MIN_TRIGGER_LEN = 3

# Most emoji a single word may offer. The strip shows four at the very most,
# and a longer tail only makes the table bigger.
MAX_TRIGGER_FANOUT = 6

# Words from an emoji's description that carry no meaning on their own. Typing
# "with" should not offer anything, and every description is full of them.
DESCRIPTION_STOPWORDS = frozenset(
    """a an and at for from in of on or the to with without over under
    other type light medium dark tone skin""".split()
)


def load_catalog(path: Path) -> dict[str, str]:
    """emoji -> the base it is a gender/role variant of, or "" when it is one."""
    emoji: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        # Only "# " opens a comment — the keycap hash emoji starts a data row
        # with a bare "#".
        if not line.strip() or line.startswith("# "):
            continue
        parts = line.split("\t")
        emoji[parts[0].strip()] = parts[4].strip() if len(parts) > 4 else ""
    return emoji


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gemoji", type=Path, required=True)
    parser.add_argument(
        "--catalog", type=Path,
        default=root / "app/src/main/assets/emoji/catalog.tsv",
    )
    parser.add_argument(
        "--out", type=Path,
        default=root / "app/src/main/assets/emoji/shortcodes.tsv",
    )
    parser.add_argument(
        "--triggers", type=Path,
        default=root / "app/src/main/assets/emoji/triggers.tsv",
    )
    args = parser.parse_args()

    catalog = load_catalog(args.catalog)
    if not catalog:
        print(f"empty catalog: {args.catalog}", file=sys.stderr)
        return 1

    table: dict[str, str] = {}
    triggers: dict[str, list[str]] = {}
    skipped_emoji: set[str] = set()

    def add_trigger(word: str, emoji: str) -> None:
        word = word.strip().lower()
        if len(word) < MIN_TRIGGER_LEN or "_" in word or not SHORTCODE_RE.match(word):
            return
        # Offer the base rather than the gendered form: gemoji tags "chef" on
        # both 👨‍🍳 and 👩‍🍳, which would take two of the four slots the strip
        # has for one concept. The palette collapses them the same way, and the
        # dedup below turns the pair into a single 🧑‍🍳.
        emoji = catalog.get(emoji) or emoji
        offered = triggers.setdefault(word, [])
        if emoji not in offered and len(offered) < MAX_TRIGGER_FANOUT:
            offered.append(emoji)

    for entry in json.loads(args.gemoji.read_text(encoding="utf-8")):
        emoji = entry["emoji"]
        if emoji not in catalog:
            skipped_emoji.add(emoji)
            continue
        for alias in entry.get("aliases", ()):
            alias = alias.strip().lower()
            if not SHORTCODE_RE.match(alias):
                print(f"skipping odd shortcode {alias!r}", file=sys.stderr)
                continue
            if alias in table and table[alias] != emoji:
                print(
                    f"conflicting shortcode :{alias}: -> {table[alias]} / {emoji}",
                    file=sys.stderr,
                )
                return 1
            table[alias] = emoji
            add_trigger(alias, emoji)
        # `tags` are gemoji's own "what would you type to mean this" list, so
        # they read as predictions even where the alias doesn't ("hooray" and
        # "party" both reaching 🎉).
        for tag in entry.get("tags", ()):
            add_trigger(tag, emoji)
        # The description is the emoji's name ("face with tears of joy"), which
        # is the same string CLDR gives and the catalog already stores — so it
        # is mined for its *words* rather than kept whole. Variants are skipped
        # for the reason EmojiSuggester skips them: the gendered and role forms
        # of one emoji would take every slot "man" or "woman" has to offer.
        if not catalog.get(emoji):
            for word in re.split(r"[^a-z0-9]+", entry.get("description", "").lower()):
                if word not in DESCRIPTION_STOPWORDS:
                    add_trigger(word, emoji)

    for alias, emoji in EXTRA_ALIASES:
        if emoji not in catalog:
            print(f"extra alias :{alias}: -> {emoji} not in catalog", file=sys.stderr)
            return 1
        existing = table.get(alias)
        if existing is not None and existing != emoji:
            print(
                f"extra alias :{alias}: -> {emoji} disagrees with gemoji ({existing})",
                file=sys.stderr,
            )
            return 1
        table[alias] = emoji
        add_trigger(alias, emoji)

    lines = ["# shortcode\temoji  (github/gemoji + Discord/Slack spellings)"]
    lines += [f"{alias}\t{emoji}" for alias, emoji in sorted(table.items())]
    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")

    trigger_lines = ["# word\temoji,emoji,...  (github/gemoji aliases + tags)"]
    trigger_lines += [
        f"{word}\t{','.join(offered)}" for word, offered in sorted(triggers.items())
    ]
    args.triggers.write_text("\n".join(trigger_lines) + "\n", encoding="utf-8")

    print(f"wrote {len(table)} shortcodes to {args.out}")
    print(f"wrote {len(triggers)} trigger words to {args.triggers}")
    if skipped_emoji:
        print(f"{len(skipped_emoji)} gemoji emoji not in catalog: {sorted(skipped_emoji)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
