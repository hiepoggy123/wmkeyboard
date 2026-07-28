# WM Keyboard docs — content guide

Read this before writing or editing any page. It is the contract between the
scaffold and the content passes that fill it in.

## The one-paragraph brief

This site documents **every feature, every setting, every nuance** of WM
Keyboard. The reader is a curious user first, a power user second, a developer
only in the last two sidebar sections. Write warm, precise, screenshot-heavy
pages. Privacy claims must be specific ("this tool sends X to Y when you press
Z"), never vague ("we care about your privacy").

## Ground rules

1. **Never invent behaviour.** Every claim must be verified against the code
   (`../app`, `../core`, `../feature`) or on a device (reserved for hard cases).
   Stub outlines are *checklists written from memory* — treat each bullet as a
   hypothesis to verify from code, not a fact to restate. If a bullet turns out
   wrong, fix the page, not the truth.
2. **One page = one job.** If a section outgrows its page, split it and
   cross-link rather than nesting H4s.
3. **Kill the draft banner** (`:::caution[Draft page]` block) and the
   `TODO(sonnet)` comment when a page is done. A page with the banner is
   unfinished by definition.
4. **Keep frontmatter `description`** — it feeds search, SEO and `<LinkCard>`s.
   Rewrite it if the page's focus shifts or you find that it is factually incorrect.
5. **Link generously.** Guide pages link to their Reference → Settings screen
   and vice versa. Use absolute paths with trailing slash: `/typing/gestures/`.
6. **American-neutral, second person, present tense.** "Long-press the key" not
   "The key can be long-pressed by the user."

## Page anatomy (target shape)

```mdx
---
title: Spacebar gestures            # sentence case, no "WM Keyboard" prefix
description: One crisp sentence — shown in search results and link cards.
sidebar:
  order: 2                          # keep the scaffold's ordering unless told
---

import PhoneFrame from '@components/PhoneFrame.astro';
import SettingsPath from '@components/SettingsPath.astro';
import KeyCap from '@components/KeyCap.astro';

Lead paragraph: what this feature is and why you'd care. Two sentences max
before the first visual.

<PhoneFrame caption="Short swipe switches language; long swipe moves the cursor.">
  ![Spacebar gesture in action](@assets/screens/typing/spacebar-swipe.webp)
</PhoneFrame>

## Using it            ← H2s are task-shaped, not noun-shaped

## Options

<SettingsPath path="Typing / Gestures / Spacebar swipes" />
…each setting: what it does, its default, when to change it…

## Details & edge cases  ← the "every nuance" section; small print welcome
```

## Components

All in `src/components/`, importable via the `@components/*` alias
(pages must be `.mdx` to use them):

| Component | Use for | Example |
|---|---|---|
| `<KeyCap>` | Any key name inline | `<KeyCap>Shift</KeyCap>`, `<KeyCap>?123</KeyCap>` |
| `<SettingsPath path="…" />` | Where a setting lives; put one at the top of every "Options" section | `<SettingsPath path="Typing / Autocorrect" />` |
| `<Flavor edition="full" />` | Feature gated to an edition; place next to the H1 lead or section heading | also `lite`, `both` |
| `<Since v="1.4" />` | Version a feature landed (start using once versions are documented) | |
| `<PhoneFrame caption="…">` | Every screenshot. Empty `<PhoneFrame />` renders a "screenshot pending" placeholder — acceptable in drafts, not in finished pages | |
| `<LayoutExplorer layout={json} />` | Interactive keyboard rendered from a real `.wmlayout.json` (import it from `app/src/main/assets/layouts/`, five `../` up). Prefer over static layout screenshots | see gallery |
| `<GestureDemo type="…" />` | Conceptual gesture loop: `space-swipe`, `space-hold`, `long-press`, `glide`. Reduced-motion safe | see gallery |
| `<ThemePreview />` | Swatch grid recolouring a mock keyboard; optional `themes` array | see gallery |

Live demos of everything: `/development/component-gallery/`.

Starlight built-ins (`@astrojs/starlight/components`) to lean on:

- `<Tabs>/<TabItem>` — Full vs Lite behaviour, Android version differences.
- `<Steps>` — any setup flow ≥ 3 steps.
- `<Card>/<CardGrid>/<LinkCard>` — section landing pages and "next steps" footers.
- `<Badge>` — inline "New"/"Full" markers in tables.
- Asides: `:::note`, `:::tip`, `:::caution`, `:::danger` — danger is reserved
  for data-loss and privacy warnings.

## Screenshots

- Live under `src/assets/screens/<section>/kebab-name.webp`, referenced with
  the `@assets` alias. Anything in `src/assets` is optimised at build time.
- Capture at device resolution, **portrait, status bar clean** (100% battery,
  no notifications — use demo mode: `adb shell settings put global sysui_demo_allowed 1`).
- Prefer the default theme in **dark mode** for consistency; use light only
  when demonstrating light-specific behaviour.
- `adb exec-out screencap -p > name.png`, then convert:
  `cwebp -q 88 name.png -o name.webp` (target < 150 KB each).
- Always wrap in `<PhoneFrame>`; always write meaningful alt text.
- Zoom on click is automatic (starlight-image-zoom) — don't hand-roll lightboxes.
- **Motion**: where behaviour only reads in motion (glide trails, hold-drags,
  live previews), use a looping **animated WebP** captured via
  `adb shell screenrecord` + ffmpeg (`-c:v libwebp_anim -loop 0`, ≤ 6 s,
  ≤ 1 MB, 15 fps) — it drops into `<PhoneFrame>` like any image. GIFs are
  banned (4× the bytes). Keep a still alongside every animation for
  reduced-motion readers. Conceptual gesture *explanations* should be CSS/SVG
  animations instead of recordings. Full pipeline in `SONNET_PROMPT.md`.

## Interactive elements — the taste rules

Interactivity must explain something a static image can't. Good candidates,
roughly in order of value:

1. **Gesture demos**: a looping CSS/SVG animation of a finger path on a key
   (build as a small Astro component with a `<PhoneFrame>` base; no JS
   framework needed).
2. **Layout explorer**: render a keyboard layout as HTML from its data file so
   readers can hover keys to see long-press popups. Worth building once,
   reusable for all 393 layouts + notation layouts.
3. **Theme preview**: swatch grid that live-recolours an HTML keyboard mockup.
4. **Searchable tables**: the 333-wordlist list and 352-language matrix
   should be filterable (a `<script>` in the MDX is fine at this scale).
5. Mermaid/diagram embeds for the developer section (addon install pipeline,
   IME lifecycle).

Keep them dependency-free (vanilla `<script>` in Astro components). If a
widget needs a framework, question it first.

## Voice & style calibration

- Explain jargon on first use, then use it freely (glossary backs you up).
- Numbers are features: "332 languages", "27 Whisper models" — be exact,
  and verify the number in code before writing it. Headline counts were
  code-verified on 2026-07-28: 352 registered languages, 393 layouts
  (18 built-in + 375 asset), 333 wordlists (332 languages), 125 emoji keyword
  packs, 27 Whisper models, 8 local LLMs, 60 toolbar tools, 11 addon types,
  8 alternative calendars, 22 fancy-text layouts, 18 snippet variables,
  Emoji 16.0 catalog (1,913 base emoji). Re-verify before reuse — several
  of these drift with every feature release.
- The privacy section is the trust anchor: every network claim there needs a
  file/class reference in a code comment or PR description, even though the
  prose itself stays reference-free.
- Screenshots > words. If a paragraph describes UI for more than two
  sentences, it should be a captioned screenshot instead.

## Build & check

```sh
npm run dev     # live preview at localhost:4321
npm run build   # what CI runs
npm run check   # build + validate all internal links (CHECK_LINKS=1)
```

`npm run check` must pass before a content PR merges — it catches dead links
from renamed pages.

## Current state / handoff notes

- All stub pages carry `:::caution[Draft page]` + a `Planned coverage` outline.
  Outlines were audited against the codebase (toolbar tool registry, the
  settings nav graph, layout assets) — still verify details, but the page *set*
  is believed complete.
- `reference/settings/` mirrors the app's real navigation graph (the NavHost in
  `app/.../MainActivity.kt`): one page per screen, small sub-screens folded
  into their parent's page. Per-tool settings screens (one per toolbar tool)
  are documented on each tool's guide page, not as separate reference pages.
- Legacy docs were moved in with history intact and have been restyled to
  Starlight MDX (components, asides, FileTree): all five plugin pages and the
  addon-repos section — these are **real content already**, only screenshots
  pending. `development/architecture.md` and `client-design.md` intentionally
  stay plain Markdown.
- The addon repo JSON schema is served at `/schemas/wmkeyboard-repo.schema.json`
  (source: `public/schemas/`).
- `site` in `astro.config.mjs` and both GitHub URLs are placeholders — fix
  before deploying.
- Sidebar order is controlled by `sidebar.order` frontmatter per page;
  top-level grouping lives in `astro.config.mjs`.
