# WM Keyboard

A modern, privacy-first Android keyboard with first-class Bengali support.

Built with Kotlin, Jetpack Compose (including the keyboard view itself), Material 3,
and a fully offline core: dictionaries, transliteration, prediction and emoji search
are all bundled — nothing leaves the device.

## Highlights

- **Avro-style Bengali phonetic typing** — type `ami valo achi`, get **আমি ভালো আছি**.
  A lenient phonetic index means loose spellings (`asi`, `achi`, `achhi`) all resolve
  to the right dictionary word.
- **Probhat layout** — the fixed Bengali layout, with aspirates on shift.
- **Semantic emoji search** — `happy`, `party`, `fire`, `বিড়াল`, `হাসি` all find the
  right emojis via synonyms, concept expansion, prefix and typo-tolerant matching.
  Searchable without leaving the keyboard.
- **Prediction engine** — trie-based completion, Norvig-style autocorrect, on-device
  learning of your words and bigrams for next-word prediction.
- **Clipboard manager** — history, pinning, expiry, dedup, search.
- **Material You** — dynamic color, light/dark/AMOLED themes, adjustable key height,
  corner radius, font scale, haptics, popups, number row.
- **Privacy** — offline-first, no telemetry, incognito mode, secure-field detection,
  one-tap clearing of learned data.

## Building

Requires JDK 17+ (a JDK 21 toolchain is auto-provisioned via Foojay) and the Android
SDK (compileSdk 36).

```sh
./gradlew assembleDebug        # build APK
./gradlew testDebugUnitTest    # run unit tests
```

Install the APK, then open the app and follow the setup card: enable WM Keyboard in
system settings and select it as the active input method.

## Project layout

```
app/src/main/java/com/wasimaster/wmkeyboard/
├── app/            # Settings app (Compose M3, navigation, DataStore)
├── core/
│   ├── clipboard/  # ClipboardStore: history, pinning, expiry
│   ├── emoji/      # Catalog loader, semantic search, usage tracking
│   ├── prediction/ # Trie, SuggestionEngine, UserLexicon, dictionary loader
│   ├── settings/   # SettingsRepository (DataStore) + models
│   └── transliteration/  # AvroPhonetic engine, BengaliPhoneticIndex
└── ime/            # InputMethodService, UI state, layouts, Compose keyboard
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design decisions,
[docs/ROADMAP.md](docs/ROADMAP.md) for the MVP → Beta → Stable plan, and
[TODO.md](TODO.md) for feature tracking.

## Contributing

The core engines (`core/`) are plain Kotlin with no Android dependencies — they are
easy to unit test and easy to extend. Dictionaries and the emoji catalog are plain
text assets designed for hand editing and community contribution.
