# WM Keyboard documentation — content generation brief

You are filling in the WM Keyboard documentation site. The scaffold is complete:
109 pages under `docs/src/content/docs/`, each stub carrying a `Planned
coverage` outline that was audited against the codebase. Your job is to turn
every stub into a finished page, then capture every screenshot on an emulator.

**Read `docs/CONTENT_GUIDE.md` first.** It is the contract: page anatomy,
component usage, voice, screenshot conventions, and a dated list of
code-verified headline numbers. This brief adds process; the guide governs
content. Where they conflict, the guide wins.

Use ultracode workflows throughout: fan out research and writing per section,
and adversarially verify every factual claim against the code before a page is
considered done. Token cost is not a constraint; wrong claims are.

## Ground rules (non-negotiable)

1. **Never invent behaviour.** Outline bullets are hypotheses. Before writing a
   page, read the implementing code (`core/`, `feature/`, `app/`) or exercise
   the feature on the emulator. Every settings path, default value, limit and
   count must be verified. The guide's calibration note lists verified counts
   with a date — re-verify any number you reuse.
2. **A page is done when**: draft banner and `TODO(sonnet)` comment removed,
   every outline topic covered or consciously dropped, at least one
   `<PhoneFrame>` where UI is visible, `npm run check` green.
3. **Commit after each section** with `git add docs && git commit -- docs`
   (plus the harness path when you create it). The repo owner works in the same
   tree concurrently — never `git add -A`, never commit paths outside your
   scope: `docs/`, and `app/src/debug/` for the harness only.
4. MDX gotchas that have already broken this site once: quote YAML frontmatter
   values (colons!), backtick anything with `{braces}` or `<angle-brackets>`,
   no `<https://…>` autolinks, `{/* */}` not `<!-- -->`.
5. Do not restyle finished pages: the five `plugins/` pages, the
   `development/addon-repos/` section and `development/architecture.md` are
   real content — only add screenshots/cross-links where obviously missing.

## Phase plan

Run phases in order; each is one or more ultracode workflows.

### Phase 0 — the screenshot harness (build this first)

Create a debug-only activity that hosts the keyboard for screenshots:
`app/src/debug/java/com/wasimaster/wmkeyboard/app/DocsShotActivity.kt`, plus an
`app/src/debug/AndroidManifest.xml` entry (`exported="true"` so adb can start
it). It never ships — debug source set only. Spec:

- **Modes** via intent extras (`adb shell am start -n
  com.wasimaster.wmkeyboard.debug/com.wasimaster.wmkeyboard.app.DocsShotActivity
  --es mode field …`):
  - `field` (default): one text field, top-third of the screen, hint and
    prefill from `--es hint` / `--es text`, input type from `--es kind`
    (`text|email|uri|password|number|phone|search|multiline`), IME action from
    `--es action` (`send|search|go|done|next`). This is how you demo field
    adaptation, suggestion states, and ordinary typing.
  - `chat`: a fake conversation — two or three neutral bubbles ("Alex", no
    avatars, lorem-adjacent but human text) above an input bar. Implement
    `onCommitContent` and render committed images/stickers/GIFs as a new
    bubble: this is the only way to screenshot sticker/GIF sending honestly.
  - `blank`: empty surface, for floating/split keyboard and panel shots.
- **Look**: Material 3 surface colours from the app theme, `--es theme
  light|dark` (default dark to match the docs), `--es bg white` override for
  the rare shot that needs pure white. No branding, no clock in the layout
  (status bar is handled by demo mode).
- Keep it under ~200 lines; Compose; no new dependencies.

Verify it builds (`./gradlew assembleFullDebug`) and commit it separately.

### Phase 1 — write the guide sections

One workflow per sidebar section, in this order (trust-building first, then
feature depth):

1. `start/` 2. `typing/` 3. `privacy/` 4. `languages/` 5. `smart/`
6. `emoji/` 7. `tools/` (biggest — split into 2–3 workflows) 8. `themes/`
9. `addons/` 10. `accessibility/`

Per-section workflow shape: parallel research agents read the implementing
code and the relevant settings screens → one writer per page (guide's page
anatomy) → adversarial fact-checkers who try to REFUTE each claim against the
code (kill or fix what fails) → build check. While writing, register every
screenshot in the manifest (below) and leave the placeholder pattern — do not
reference image files that don't exist yet, the build fails on missing assets.

### Phase 2 — the reference section

- `reference/settings/*`: mirror the real screens. Two authoritative sources:
  the NavHost in `app/src/main/java/.../MainActivity.kt` (~line 445 onward)
  and the hand-maintained search index in `SettingsSearch.kt` (route ↔ title ↔
  breadcrumb for nearly every row — your coverage checklist). Document every
  toggle with its default.
- `reference/gestures.mdx` and `shortcuts.mdx`: complete tables, sourced from
  the gesture/hardware code, cross-linked from the guide pages.
- `reference/file-formats.mdx`, `deep-links.mdx`, `troubleshooting.mdx`,
  `glossary.mdx`: consolidate from the now-finished guide pages.

### Phase 3 — interactive elements (stretch, but high value)

In priority order, as dependency-free Astro components (vanilla `<script>`):

1. **Layout explorer** — render any `.wmlayout.json` from
   `app/src/main/assets/layouts/` as an HTML keyboard; hover/tap shows
   long-press popups. Build once, reuse on every language page.
2. **Gesture demos** — looping CSS/SVG finger-path animations on a PhoneFrame.
3. **Filterable tables** — the 352-language matrix and 333-wordlist list.
4. **Theme preview** — swatch grid recolouring an HTML keyboard mockup.

Skip any of these rather than shipping something janky.

### Phase 4 — screenshot manifest sweep

Completeness critic pass: every page that shows UI has manifest entries; every
entry has setup steps an agent can execute. Fix gaps before capture.

### Phase 5 — capture and insert

Emulator up (see recipes), then iterate the manifest: set up → capture →
convert → move into `src/assets/screens/` → replace the placeholder comment
with the image line inside `<PhoneFrame>` → mark `captured`. Finish with
`npm run check` and a final visual pass of ~10 pages in the browser.

## Screenshot protocol

### Manifest

`docs/screenshots/manifest.json` — the single source of truth, created in
Phase 1 and consumed in Phase 5:

```json
[
  {
    "id": "typing/spacebar-swipe",
    "page": "src/content/docs/typing/gestures.mdx",
    "file": "src/assets/screens/typing/spacebar-swipe.webp",
    "caption": "A short spacebar swipe switches language.",
    "host": "harness-field",
    "setup": [
      "am start -n com.wasimaster.wmkeyboard.debug/com.wasimaster.wmkeyboard.app.DocsShotActivity --es mode field --es kind text",
      "input tap 540 700",
      "input swipe 300 2200 700 2200 150"
    ],
    "status": "todo",
    "notes": "capture mid-swipe if possible; else the post-switch state"
  }
]
```

`host` ∈ `harness-field | harness-chat | harness-blank | settings` (settings
screens are shot in the settings app itself — no harness needed).

### Placeholder pattern (build stays green while writing)

```mdx
<PhoneFrame caption="A short spacebar swipe switches language.">
  {/* shot: typing/spacebar-swipe */}
</PhoneFrame>
```

Empty PhoneFrame renders a designed "screenshot pending" placeholder. In
Phase 5, replace the comment with
`![alt text](@assets/screens/typing/spacebar-swipe.webp)`.

### Emulator + capture recipes

```bash
# Device: Pixel-class AVD, portrait, API 34+; boot with -no-snapshot for determinism.
./gradlew assembleFullDebug && adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk

# Clean status bar (12:00, full battery, wifi, no notifications):
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi -e level 4 -e fully true
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false

# Enable + select the IME (verify the exact id with `adb shell ime list -a`):
adb shell ime enable  <package>/<service>
adb shell ime set     <package>/<service>

# Capture → convert (target < 150 KB):
adb exec-out screencap -p > shot.png
cwebp -q 88 shot.png -o docs/src/assets/screens/<section>/<name>.webp
```

Interaction during setup: `input tap X Y`, `input swipe X1 Y1 X2 Y2 MS`,
`input text 'hello'`, and raw `input motionevent DOWN/MOVE/UP` sequences for
held gestures (spacebar hold-drag, key long-press popups). Gestures that need a
finger mid-flight are the hard 10% — attempt with motionevent sequencing;
if a shot truly can't be automated, set `status: "blocked"` with a note and a
manual instruction, and tell the user at the end which shots need a human hand.

Permission-dependent tools, grant via adb before the shot:

```bash
adb shell pm grant <pkg> android.permission.READ_CALENDAR   # calendar tool
adb shell pm grant <pkg> android.permission.CAMERA          # camera/scanner
adb shell pm grant <pkg> android.permission.RECORD_AUDIO    # voice
adb shell cmd notification allow_listener <pkg>/<listener>  # media controls
```

Media-controls album art needs something actually playing — start the
emulator's stock media player with a local file, or mark blocked. Whisper /
local-LLM / handwriting model shots may download models over the emulator's
network first; capture the *manager UI with a downloaded model* where
practical, the download-in-progress state otherwise.

Settings-app screenshots: navigate with deep links where allowlisted
(`am start -a android.intent.action.VIEW -d "wmkeyboard://settings/typing"`)
or by tap sequences; the six allowlisted routes are typing, appearance,
themes, languages, tools, search.

### Consistency rules

Dark theme default; light only when demonstrating light-specific behaviour.
Same AVD for every shot. Portrait unless the page is about landscape/fold.
English UI unless the page is about another language — then that language on
the keys is the point.

## Definition of done (whole project)

- Zero `:::caution[Draft page]` banners; zero `TODO(sonnet)` comments.
- `npm run check` green.
- Manifest: no `todo` entries; `blocked` entries listed in the final report.
- Every number in prose exists in code (spot-audit ~20 claims at the end —
  the scaffold was burned by stale counts once already).
- Final report: pages written, shots captured/blocked, claims corrected along
  the way, and anything discovered in the app that the docs still don't cover.
