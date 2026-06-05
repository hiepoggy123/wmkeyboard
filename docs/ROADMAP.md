# Roadmap

## Phase 1 — MVP (current)

Goal: a daily-drivable keyboard with the two differentiators working end to
end (Bengali phonetic typing, semantic emoji search).

- [x] Kotlin + Compose + Material 3 project, AGP 9 built-in Kotlin
- [x] IME service with Compose keyboard view
- [x] QWERTY + long-press alternates + two symbol pages
- [x] Bengali: Avro phonetic with live preview + phonetic-sibling ranking
- [x] Bengali: Probhat fixed layout
- [x] Prediction: trie completion, autocorrect, user learning, bigrams
- [x] Semantic emoji search (en + bn), recents/frequents
- [x] Clipboard history with pinning and expiry
- [x] Settings app: setup wizard, typing/appearance/languages/clipboard/privacy
- [x] Themes: dynamic color, light/dark/AMOLED, key size/radius/font sliders
- [x] Privacy: offline-only, incognito, secure fields, clear learned data
- [x] Unit tests for all core engines

## Phase 2 — Beta

Typing feel and completeness. Ordered by impact:

1. ~~**Gesture/swipe typing**~~ — ✅ shipped: SHARK²-style decoder
   (`core/gesture/GestureDecoder`), gesture overlay + trail in the Compose
   keyboard, alternates in the suggestion bar with tap-to-replace, settings
   toggle. English QWERTY only so far; Bengali layouts and mid-swipe
   preview are follow-ups.
2. **National (Jatiya) layout + Bijoy compatibility mode.**
3. **Spacebar cursor control**, multi-touch key handling, undo/redo.
4. **One-handed mode, floating keyboard, resize/split.**
5. **Bigger dictionaries** — import CLDR/AOSP wordlists (en, bn), full
   Unicode CLDR emoji annotations; asset pipeline script.
6. **Text snippets** with date/time/clipboard variables.
7. **Sticker & GIF panels** (Tenor/Giphy providers, cached, optional — network
   features stay opt-in).
8. **Toolbar customization** — reorder/enable buttons.
9. **Voice typing** passthrough, number-pad and phone layouts.
10. Instrumented UI tests (Espresso/Compose) for the IME view.

## Phase 3 — Stable / flagship

- Custom character sets (Greek, math, arrows, IPA…) as user-editable packs
- Theme engine: custom backgrounds, gradients, blur, animated themes, fonts
- Per-app context toolbar (email snippets in mail apps, symbols in IDEs)
- AI assistant (local model first, BYO-API-key cloud optional)
- Google image search / OCR / QR tools panel
- Plugin architecture + extension API
- Handwriting recognition; more languages (Hindi, Arabic, Japanese, Korean…)
- Settings: search, favorites, profiles, import/export/backup
- Accessibility audit: TalkBack traversal, high-contrast themes, dyslexia fonts

## Non-goals (for now)

- Telemetry of any kind
- Server-side prediction
- Account systems / cloud sync (may return as optional E2E-encrypted sync)
