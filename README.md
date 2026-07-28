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
./gradlew assembleFullDebug        # build APK (full flavor; use assembleLiteDebug for lite)
./gradlew testFullDebugUnitTest    # run unit tests in every module
```

Install the APK, then open the app and follow the setup card: enable WM Keyboard in
system settings and select it as the active input method.

## Project layout

Gradle modules, layered bottom-up (each module lists its dependencies in its
own `build.gradle.kts`; all inter-module dependencies point downward):

```
app/                  # Settings app, manifest, resources, bundled assets, most unit tests
feature/
├── ime/              # WMKeyboardService + the Compose keyboard UI (the keyboard itself)
├── addons/           # Addon install/reconcile/download (above settings)
└── tools/            # Network tool clients: AI, GIF/sticker, search, link preview
core/
├── settings/         # SettingsRepository (DataStore), KeyboardSettings model, modes, power saving
├── intelligence/     # Grammar (Harper JNI), local LLM, handwriting, spell checker service
├── feedback/         # Key sounds and haptics
├── voice/            # SpeechRecognizer + offline Whisper dictation
├── plugins/          # Lua plugin sandbox (luaj)
├── addons/           # Addon store/repo data layer
├── content/          # Clipboard, snippets, fonts, media, stickers
├── tools/            # Offline tool engines: calc, units, currency, symbols, calendars…
├── icons/            # Icon packs + SVG parser
├── theme/            # ThemeSpec, palettes, rendering
├── emoji/            # Catalog loader, semantic search, usage tracking
├── prediction/       # Trie, SuggestionEngine, UserLexicon, dictionaries, gestures
├── input/            # Composers (CJK, cluster scripts) and input pipeline
├── language/         # Scripts, layouts, transliteration
├── common/           # Utilities, direct boot, debug log, shared contracts
└── config/           # Build flags + API keys (BuildConfig for library modules)
tools/dictc/          # Host-side dictionary compiler (shares :core:prediction sources)
```

See [the architecture doc](docs/src/content/docs/development/architecture.md) for design
decisions and [TODO.md](TODO.md) for feature tracking. The full documentation site lives
in [docs/](docs/) (Astro Starlight — `npm run dev` inside `docs/` to preview).

## Contributing

The core engines (`core/`) are plain Kotlin with no Android dependencies — they are
easy to unit test and easy to extend. Dictionaries and the emoji catalog are plain
text assets designed for hand editing and community contribution.
