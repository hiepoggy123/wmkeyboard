# Architecture

## Overview

WM Keyboard is a single-module app with strict package-level layering. The
`core/` packages are plain Kotlin (no Android framework types except Context in
`settings/`), which keeps the interesting logic unit-testable on the JVM and
leaves the door open to extracting Gradle modules later without rewrites.

```
┌─────────────────────────────────────────────┐
│  ime/ WMKeyboardService (InputMethodService)│
│   • owns KeyboardUiState (StateFlow)        │
│   • InputConnection plumbing                │
│   • wires engines → UI callbacks            │
├──────────────┬──────────────────────────────┤
│ ime/ui/      │ app/ (settings activity)     │
│ Compose view │ Compose M3 + navigation      │
├──────────────┴──────────────────────────────┤
│ core/  (plain Kotlin, JVM-testable)         │
│  prediction/ transliteration/ emoji/        │
│  clipboard/  settings/                      │
└─────────────────────────────────────────────┘
```

## Key decisions

### Compose inside the IME window

`InputMethodService` predates architecture components, so its window has no
ViewTree owners. `KeyboardViewLifecycleOwner` implements `LifecycleOwner`,
`ViewModelStoreOwner` and `SavedStateRegistryOwner`, is attached to the IME
decor view, and is driven from the service lifecycle callbacks
(`onStartInputView` → resume, `onFinishInputView` → pause). This is the same
approach used by production Compose keyboards (e.g. FlorisBoard).

### Unidirectional data flow

The service owns a single `MutableStateFlow<KeyboardUiState>`. The Compose tree
collects it and renders; every interaction calls back into the service
(`onKey`, `onSuggestion`, `onEmoji`, …) which mutates state via `copy()`. No
state lives in the view layer beyond per-key press animation.

### Composing region as the source of truth

English and Avro modes type into an `InputConnection` composing region. In Avro
mode the composing preview is the live transliteration, so the user watches
বাংলা appear as they type romanized text; on commit (space/suggestion tap) the
top phonetic-index candidate wins. Probhat mode commits characters directly —
fixed layouts don't compose.

### Bengali phonetics: two engines, one job

- `AvroPhonetic` is a deterministic greedy longest-match transliterator (rules
  → glyphs). It answers "what did the user literally type?"
- `BengaliPhoneticIndex` answers "which real words sound like this?" by folding
  both roman input and dictionary words into a lenient canonical key
  (স/শ/ষ/ছ/চ → s, aspiration dropped, inherent vowels dropped). This is what
  turns `asi` into আছি while আসি stays one tap away.

Corrections that need context (আসি vs আছি) are deliberately a ranking problem,
not a transliteration problem.

### Prediction

`Trie` (frequency-weighted) serves prefix completions; `UserLexicon` overlays
the user's learned words (heavily boosted) and bigrams for next-word
prediction; `SuggestionEngine` merges, ranks and case-matches, and generates
Norvig-style edit-distance-1 corrections when the typed word is unknown.
Learning is skipped for secure fields and in incognito mode, and everything is
JSON on private storage with one-tap clearing.

### Emoji search

The catalog is a TSV asset (`emoji/catalog.tsv`) with English and Bengali
keywords merged into a single token index — multilingual search falls out for
free. Query scoring: exact token (100) > curated synonym expansion (60) >
prefix (40) > Damerau-Levenshtein distance-1 (30), summed across query tokens.
The search field lives inside the keyboard: while it is active, letter keys
feed the query instead of the app.

### Persistence

- **Settings** — Preferences DataStore, exposed as a `Flow<KeyboardSettings>`
  the service collects, so changes apply live without restarting the IME.
- **Learning data / emoji usage / clipboard** — kotlinx-serialization JSON
  files under `filesDir`. Room was deliberately avoided for the MVP: the data
  sets are small, append-mostly, and the KSP/AGP compatibility surface during
  the AGP 9 transition wasn't worth it. Revisit if any store outgrows JSON.

### Direct boot

The IME service is `directBootAware`, because the keyboard is what the user
types their PIN on — a keyboard that cannot run before the first unlock is one
the platform silently replaces on the lock screen.

In that window there is no credential-encrypted storage at all: no `filesDir`,
no settings DataStore, no learned words. The keyboard runs on what
device-protected storage can hold, which deliberately excludes anything of the
user's:

- **Settings** — `LockedSettings` keeps a mirror of the DataStore in
  device-protected storage, rewritten on every change while unlocked and read
  (never as a source of truth) while locked. `SettingsBackup.SECRET_KEYS` — the
  API keys and tokens — is filtered out on the way in, since that storage is
  not covered by the user's credential.
- **Personal stores** — the learned lexicon, clipboard, snippets, emoji history
  and sticker packs are constructed with a null file, which every one of them
  already treats as "memory only, never persisted". A locked session learns
  nothing and writes nothing.
- **Dictionaries** — the bundled `.wmdict` lists are inflated into
  device-protected storage (they come out of the APK, so nothing is exposed by
  it) and serve both states from one copy. Downloaded and imported lists stay
  behind the credential, so prediction while locked knows only the words that
  shipped with the app.
- **Everything else** — `KeyboardSettings.restrictedToDirectBoot()` switches
  off, in one place, every feature whose data is unreadable: custom fonts and
  theme images, contact and app-name suggestions, offline dictation, and the
  tools that fail the `isDirectBootSafeTool` test. Downstream code — toolbar,
  toolbox, shortcuts, renderer — needs no direct-boot awareness of its own.

`ACTION_USER_UNLOCKED` arrives while the keyboard is often still on screen, so
the service re-attaches the real stores, rebuilds the suggestion engine around
them and flips the repository back to the DataStore in place, rather than
waiting for the process to be restarted.

### Power saving

Power saving reuses direct boot's shape rather than inventing one: a pure
function, `KeyboardSettings.underPowerSaving()`, returns the settings *as they
apply* while it is on. The service combines the DataStore flow with
`core/power/PowerSaver`'s device state and applies the view on the way out, so
the renderer, the suggestion engine and the tool handlers all see one
already-reduced settings object and none of them knows power saving exists.
Nothing is persisted, which is what makes it reversible: the user's own
settings are never rewritten, only hidden, so ending power saving restores them
exactly.

`PowerSaver` deliberately does *not* subscribe to `ACTION_BATTERY_CHANGED` — it
fires per percentage point, and waking the process that often to decide whether
to save power defeats the feature. It subscribes to the coarse broadcasts
(battery low/okay, charger in/out, system battery-saver toggle) and reads the
exact level from the sticky battery intent when the keyboard comes on screen,
which is the only moment the answer can matter.

### Screen readers and the touch stream

While an explore-by-touch service (TalkBack) runs, the accessibility
framework's input filter consumes touches before any window sees them, so
every gesture the keyboard owns — the spacebar cursor slide, the backspace
word swipe, glide typing, handwriting — is dead, and no app-side workaround
reaches the events. `ScreenReaderMode` therefore offers four behaviours: `OFF`,
`LABELS` (spoken names, direct typing), `EXPLORE` (hand the keys to TalkBack's
own hover-and-activate) and `PASSTHROUGH`.

`PASSTHROUGH` uses the one supported escape hatch,
`AccessibilityService.setTouchExplorationPassthroughRegion` (API 30) — which
only an accessibility service may call. Hence `core/accessibility/`:

- `TouchPassthroughService` is an accessibility service that exists solely to
  publish that region. It subscribes to no events, cannot retrieve window
  content, and adds `FLAG_REQUEST_TOUCH_EXPLORATION_MODE` only while some
  *other* enabled service already explores by touch — requesting it
  unconditionally would switch explore-by-touch on for a user who never asked
  for a screen reader.
- `KeyboardPassthrough` is the in-process channel between the two (the IME and
  the service share the app's process). `KeyRows` publishes the **key grid**
  in display coordinates — never the whole window, so the suggestion strip,
  the toolbar and every panel stay explorable — and the IME clears it when the
  input view goes away.
- Inside the carve-out TalkBack no longer speaks, so `KeyButton` announces the
  key itself on press. Keys already commit on release, so a key can be heard
  before it types.

Without the service granted the mode degrades to `EXPLORE`, so picking it and
never granting it can never leave a screen-reader user with keys that neither
announce nor explore.

### Performance

- Dictionaries and the emoji catalog load on `Dispatchers.Default` after
  `onCreate`; the keyboard renders immediately and suggestions attach when
  ready (~10k trie inserts, tens of ms).
- Suggestion computation runs off the main thread with job cancellation on
  each keystroke.
- The layout model is immutable data; recomposition is limited to the pressed
  key and the suggestion bar.

## Extension points

- **Layouts** — add a `KeyboardLayout` in `ime/layout/` and a case in
  `currentLayout()`. Keys are data; no drawing code.
- **Languages** — drop a `dictionaries/<lang>.txt` asset (word + frequency per
  line) and register an `InputMode`.
- **Emoji** — append lines to `emoji/catalog.tsv`; add synonym rows to
  `EmojiSearch.SYNONYMS` for concept queries.
- **Transliteration schemes** — implement alongside `AvroPhonetic`; the
  suggestion engine only needs a `transliterate()` and an optional index.
