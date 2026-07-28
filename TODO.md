# TODO

Working checklist. Roadmap-level planning lives in [docs/ROADMAP.md](docs/ROADMAP.md).

## Done

- [x] Build conversion: Kotlin 2.2 + Compose + Material 3 on AGP 9.3
- [x] AvroPhonetic engine + 10 tests
- [x] BengaliPhoneticIndex (asi→আছি ranking) + engine integration
- [x] Trie / SuggestionEngine / UserLexicon + 8 tests
- [x] Seed dictionaries (en ~370 words, bn ~130 words)
- [x] Emoji catalog (~500 entries, en+bn keywords) + semantic search + 13 tests
- [x] EmojiUsage recents/frequents
- [x] ClipboardStore + 6 tests
- [x] SettingsRepository (DataStore) covering all current options
- [x] WMKeyboardService + Compose keyboard (QWERTY, Probhat, 2 symbol pages)
- [x] Long-press popups, key repeat, key preview, haptics
- [x] Emoji panel with in-keyboard search; clipboard panel
- [x] Settings app with setup wizard + 5 sections
- [x] README / ARCHITECTURE / ROADMAP docs
- [x] Swipe typing: SHARK²-style GestureDecoder in core + 10 tests, gesture
      overlay + trail on the Compose keyboard, alternates-in-suggestion-bar
      with tap-to-replace, settings toggle (English QWERTY only for now)
- [x] Mid-swipe live candidate preview in the suggestion bar
- [x] Nav-bar inset padding (edge-to-edge IME windows) + Samsung-style key
      look (contrast, rounded corners, uppercase Latin labels)
- [x] Shift double-tap → caps lock (350 ms window); distinct shift icons
- [x] Enter-key action icon from imeOptions (search/send/go/next/prev/done)
- [x] Spacebar-swipe cursor movement (toggleable)
- [x] Bengali conjunct-aware backspace option + 13 tests
- [x] Text snippets with {date}/{time}/{datetime}/{clip} variables: panel,
      settings CRUD screen, 6 tests
- [x] Emoji skin-tone variants via long-press (Emoji_Modifier_Base ranges,
      ZWJ-safe) + 8 tests
- [x] One-handed mode (left/right, side rail on keyboard, appearance setting)
- [x] Emoji overhaul: full Unicode 17.0 catalog (1914 base emojis, CLDR en+bn
      keywords, generator in tools/emoji/), data-driven RGI variant index with
      per-person dual skin tones (🤝-style two-slot picker), gender variants
      collapsed under base with long-press popup, favourites pinned in
      recents/most-used, emoji prediction in the suggestion strip, dedicated
      emoji row (off/button/own-row × most-used/recents/favourites), settings
      for history tab + prediction + row; 40+ new tests

## Next up (Beta)

- [x] Swipe typing follow-ups: Bengali layouts, bigram-aware ranking,
      trail fade animation
- [x] Floating / resizable / split keyboard
- [x] Dictionary import pipeline (CLDR/AOSP wordlists — emoji annotations done)
- [x] GIF/sticker panels (opt-in network)
- [x] Toolbar reordering
- [x] Compose UI tests for the keyboard view
- [x] Undo/redo; multi-touch key handling

## Known gaps / polish

- [x] Avro: khanda-ta (ৎ) via backquote and word-final hasant collapse,
      backquote as cluster-breaker, "Z" as an unambiguous য-ফলা
- [ ] Seed dictionaries are small; autocorrect confidence is conservative
- [x] Settings search box
- [x] App icon
