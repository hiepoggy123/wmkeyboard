# WM Keyboard documentation — content generation brief

You are filling in the WM Keyboard documentation site. The scaffold is complete:
109 pages under `docs/src/content/docs/`, each stub carrying a `Planned
coverage` outline that was audited against the codebase. Your job is to turn
every stub into a finished page, then capture every screenshot on the owner's physical device over adb.

**Read `docs/CONTENT_GUIDE.md` first.** It is the contract: page anatomy,
component usage, voice, screenshot conventions, and a dated list of
code-verified headline numbers. This brief adds process; the guide governs
content. Where they conflict, the guide wins.

Use ultracode workflows throughout: fan out research and writing per section,
and adversarially verify every factual claim against the code before a page is
considered done. Token cost is not a constraint; wrong claims are.

## Ground rules (non-negotiable)

**Never invent behaviour.** Outline bullets are hypotheses. Before writing a
   page, read the implementing code (`core/`, `feature/`, `app/`) or exercise
   the feature on the device (keep this reserved for hard cases). Every settings path, default value, limit and
   count must be verified if possible. The guide's calibration note lists verified counts
   with a date.

**A page is done when**: draft banner and `TODO(sonnet)` comment removed,
   every outline topic covered or consciously dropped, at least one
   `<PhoneFrame>` where UI is visible, `npm run check` green.

**Commit after each section** with `git add docs && git commit -- docs`
   (plus `app/src/debug/` if you have to fix the harness). The repo owner works in the same
   tree concurrently — never `git add -A`, never commit paths outside your
   scope: `docs/`, and `app/src/debug/` for the harness only.

MDX gotchas that have already broken this site once: quote YAML frontmatter
   values (colons!), backtick anything with `{braces}` or `<angle-brackets>`,
   no `<https://…>` autolinks, `{/* */}` not `<!-- -->`.

Do not restyle finished pages: the five `plugins/` pages, the
   `development/addon-repos/` section and `development/architecture.md` are
   real content — only add screenshots/cross-links where obviously missing.
## Phase plan

Run phases in order; each is one or more ultracode workflows.

### Phase 0 — the screenshot harness (already built — just verify)

The harness exists:
`app/src/debug/java/com/wasimaster/wmkeyboard/app/DocsShotActivity.kt`
(debug source set only, never ships; manifest entry in
`app/src/debug/AndroidManifest.xml`). Start it with:
adb shell am start -n com.wasimaster.wmkeyboard/.app.DocsShotActivity \
    --es mode field --es kind email --es theme dark
- Modes: `field` (default — `--es kind
  text|email|uri|password|number|phone|search|multiline`, `--es action
  send|search|go|done|next`, `--es hint …`, `--es text …` prefill),
  `chat` (neutral conversation bubbles + an input bar that accepts
  `commitContent`, so committed stickers/GIFs/images appear as a bubble —
  use it for every sticker/GIF/image-sending shot), and `blank` (silent
  surface with an invisible focus target, for floating/split/panel shots).
- `--es theme light|dark` (default dark), `--es bg white` for pure white.

Your Phase 0 is a smoke test: `./gradlew assembleFullDebug`, install on the
device, start each mode once, confirm the keyboard appears and a sticker
commit renders in chat mode. Fix anything broken before writing content.

### Phase 1 — write the guide sections

One workflow per sidebar section, in this order (trust-building first, then
feature depth):

`start/` 2. `typing/` 3. `privacy/` 4. `languages/` 5. `smart/`
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

### Phase 3 — interactive elements (mostly built — use them)

Three of the four already exist, demoed live on
`/development/component-gallery/` (source: `src/components/`):

- **`<LayoutExplorer layout={json} />`** — renders any `.wmlayout.json`
  imported straight from `app/src/main/assets/layouts/` (five `../` up from a
  section page). Hover/tap dotted keys for long-press popups; shift-layer
  toggle appears automatically. Use it on every language/layout page instead
  of screenshotting static layouts.
- **`<GestureDemo type="…" />`** — looping conceptual animations:
  `space-swipe | space-hold | long-press | glide`. Honours reduced motion.
  Extend with new types in the same file if a page needs one (keep the
  keyframe style consistent).
- **`<ThemePreview />`** — swatch grid recolouring a mock keyboard; pass a
  `themes` array for page-specific palettes.

Still to build, dependency-free (vanilla `<script>`):

**Filterable tables** — the 352-language matrix and 333-wordlist list.
Skip it rather than shipping something janky.

### Phase 4 — screenshot manifest sweep

Completeness critic pass: every page that shows UI has manifest entries; every
entry has setup steps an agent can execute. Fix gaps before capture.

### Phase 5 — capture and insert

Capture happens on the **owner's physical device over USB** — there is no
emulator. Coordinate with the owner before this phase: they plug the device
in, and they pre-download any large on-device models (Whisper, local LLM,
handwriting) you need pictured — never start multi-hundred-MB downloads on
their device without asking.

Then iterate the manifest: set up → capture → convert → move into
`src/assets/screens/` → replace the placeholder comment inside `<PhoneFrame>`
→ mark `captured`. Finish with `npm run check` and a final visual pass of ~10
pages in the browser.

## Screenshot protocol

### Manifest

`docs/screenshots/manifest.json` — the single source of truth, created in
Phase 1 and consumed in Phase 5:
[
  {
    "id": "typing/spacebar-swipe",
    "page": "src/content/docs/typing/gestures.mdx",
    "file": "src/assets/screens/typing/spacebar-swipe.webp",
    "caption": "A short spacebar swipe switches language.",
    "host": "harness-field",
    "setup": [
      "am start -n com.wasimaster.wmkeyboard/.app.DocsShotActivity --es mode field --es kind text",
      "input tap 540 700",
      "input swipe 300 2200 700 2200 150"
    ],
    "status": "todo",
    "notes": "capture mid-swipe if possible; else the post-switch state"
  }
]
`host` ∈ `harness-field | harness-chat | harness-blank | settings` (settings
screens are shot in the settings app itself — no harness needed).
`kind` ∈ `still` (default) `| anim` — see “Animated captures” below.

### Placeholder pattern (build stays green while writing)
<PhoneFrame caption="A short spacebar swipe switches language.">
  {/* shot: typing/spacebar-swipe */}
</PhoneFrame>
Empty PhoneFrame renders a designed "screenshot pending" placeholder. In
Phase 5, replace the comment with
`![alt text](@assets/screens/typing/spacebar-swipe.webp)`.

### Device + capture recipes

The capture target is the owner's own phone over USB (`adb devices` must show
exactly one device; stop and ask if it shows zero or several). Same device for
every shot — resolution consistency comes free. Keep it awake during capture:
`adb shell svc power stayon usb`, and undo with `adb shell svc power stayon
false` when finished. Demo mode below hides their notifications and clock, and
the harness is the only app you photograph — never capture their personal
apps or home screen.
./gradlew assembleFullDebug && adb install -r app/build/outputs/apk/full/debug/app-full-arm64-v8a-debug.apk
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
Interaction during setup: `input tap X Y`, `input swipe X1 Y1 X2 Y2 MS`,
`input text 'hello'`, and raw `input motionevent DOWN/MOVE/UP` sequences for
held gestures (spacebar hold-drag, key long-press popups). Gestures that need a
finger mid-flight are the hard 10% — attempt with motionevent sequencing;
if a shot truly can't be automated, set `status: "blocked"` with a note and a
manual instruction, and tell the user at the end which shots need a human hand.

Permission-dependent tools, grant via adb before the shot:
adb shell pm grant <pkg> android.permission.READ_CALENDAR   # calendar tool
adb shell pm grant <pkg> android.permission.CAMERA          # camera/scanner
adb shell pm grant <pkg> android.permission.RECORD_AUDIO    # voice
adb shell cmd notification allow_listener <pkg>/<listener>  # media controls
Media-controls album art needs something actually playing — start the
device's own music app (ask the owner to press play), or mark blocked. Whisper /
local-LLM / handwriting model shots: ask the owner which models are already
on the device; capture the *manager UI with a downloaded model* where
practical, the download-in-progress state otherwise.

Settings-app screenshots: navigate with deep links where allowlisted
(`am start -a android.intent.action.VIEW -d "wmkeyboard://settings/typing"`)
or by tap sequences; the six allowlisted routes are typing, appearance,
themes, languages, tools, search.

### Animated captures

Some pages genuinely need motion: glide-typing trails, the mid-swipe candidate
preview, spacebar hold-drag through the language picker, the Morse strip,
handwriting ink, theme editor colour changes. For those, manifest entries carry
`"kind": "anim"` and the pipeline is screen *recording*, not screencap:
adb shell settings put system show_touches 1        # visible finger dot
adb shell screenrecord --time-limit 8 --bit-rate 8M /sdcard/rec.mp4 &
# ...run the scripted input swipe/motionevent sequence while it records...
wait; adb pull /sdcard/rec.mp4 && adb shell rm /sdcard/rec.mp4
adb shell settings put system show_touches 0
# Trim to the interesting 3-6 s, downscale, loop as animated WebP:
ffmpeg -i rec.mp4 -ss 1.0 -t 4.5 -vf "fps=15,scale=540:-1"        -c:v libwebp_anim -loop 0 -q:v 70 -an out.webp
Rules of thumb:

- **Animated WebP, not GIF** — a quarter of the size at better quality, and it
  drops into the existing `![…](@assets/…)` / `<PhoneFrame>` flow like any
  image. Target ≤ 6 s, ≤ 1 MB, 15 fps, `-loop 0`. Use a `<video>` element only
  if a clip truly needs scrubbing (nothing currently does; `<PhoneFrame>`
  styles `<video>` too if it comes to that).
- **Recordings are for real behaviour** (trails, previews, ink). For
  *conceptual* gesture explanations, prefer the Phase 3 CSS/SVG animations —
  crisp, tiny, theme-aware, no device needed. Don't record what a diagram
  explains better.
- Most animations are automatable: `screenrecord` runs in the background while
  `input swipe` / `input motionevent` sequences drive the gesture. Reserve
  `status: "blocked"` for harder multi-finger or timing-critical cases not doable via adb, and
  list them for the owner at the end — with `show_touches` on, a human-driven
  recording session takes minutes.
- Keep one still per page even where an animation exists — stills are the
  fallback for reduced-motion readers and social embeds.

### Consistency rules

Dark theme default; light only when demonstrating light-specific behaviour.
Same device for every shot. Portrait unless the page is about landscape/fold.
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
