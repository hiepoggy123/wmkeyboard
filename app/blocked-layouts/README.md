# Layouts that are written but cannot ship yet

These are complete, verified `.wmlayout.json` grids that are **not** under
`app/src/main/assets/layouts/`, so they are not packaged and not registered. Each
one is blocked on engine work, not on layout work — the grid is finished and the
file is ready to move across the moment its blocker is lifted.

They live here rather than in the assets folder because `AssetLayoutsTest` walks
that folder and rightly refuses a layout that names an unknown language, and
because shipping a grid that composes nothing is worse than shipping no grid.
`app/dictionaries-src/` is the existing precedent for source data that lives in
the repo without being packaged.

## `ko_sebeolsik_390`, `ko_sebeolsik_final`

The two three-set (세벌식) Korean layouts. Both correctly emit **conjoining** jamo
from the U+1100 block, which is what a positional layout has to do: a three-set
keyboard distinguishes initial ᄀ from final ᆨ, and they are different code
points.

`HangulComposer` only understands **compatibility** jamo (U+3131), the block the
shipped two-beolsik layout uses. Fed conjoining jamo it composes nothing, so both
layouts would type strings of isolated jamo instead of syllables.

**To unblock:** teach `core/input/.../HangulComposer.kt` to accept the U+1100
block, mapping choseong/jungseong/jongseong by position rather than by lookup,
and add a choseong-doubling rule so ᄁ ᄄ ᄈ ᄊ ᄍ (까 따 빠 싸 짜) are reachable.
Widening `ScriptId.HANGUL`'s `unicodeRange` to cover U+1100..U+11FF goes with it.
Then move both files into `assets/layouts/`, add their id constants, and append
them to the `ko` language's `layoutIds`.

## `hoc_warang_citi`

Ho, written in its own Warang Citi script (U+118A0..U+118FF).

That block is outside the BMP, so every letter is a surrogate pair. `keySpelling`
in `feature/ime/.../KeyboardState.kt` indexes a key label per `Char`, and a lone
high surrogate is not a letter, so every key would be dropped from the glide grid
and from `letterAlphabet` — typing would work, glide typing and prediction would
silently not. The script also has no `ScriptId`, and device fonts rarely carry it.

**To unblock:** make `keySpelling` code-point aware rather than `Char`-aware
(and `KeyCenter`/`GlideKeyMap` with it), add `ScriptId.WARANG_CITI`, and decide
whether to ride a bundled font the way the Pixelborno work did. Then register
`hoc` as a language and move the file across.
