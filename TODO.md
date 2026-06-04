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

## Next up (Beta)

- [ ] Swipe typing (path decoding over trie)
- [ ] National (Jatiya) layout; Bijoy compatibility
- [ ] Spacebar swipe cursor movement
- [ ] One-handed / floating / resizable keyboard
- [ ] Dictionary import pipeline (CLDR/AOSP wordlists, full emoji annotations)
- [ ] Snippets with variables (date, time, clipboard)
- [ ] GIF/sticker panels (opt-in network)
- [ ] Toolbar reordering
- [ ] Compose UI tests for the keyboard view
- [ ] Enter-key action label (Search/Send/Go) from imeOptions
- [ ] Shift double-tap → caps lock timing window
- [ ] Bengali conjunct-aware backspace (delete jukto-borno as one unit option)

## Known gaps / polish

- [ ] Avro: khanda-ta (ৎ), explicit hasant edge cases, য-ফলা shortcuts
- [ ] Seed dictionaries are small; autocorrect confidence is conservative
- [ ] Emoji skin-tone variants not yet exposed in the panel
- [ ] Clipboard images (text only today)
- [ ] Settings search box
- [ ] App icon
