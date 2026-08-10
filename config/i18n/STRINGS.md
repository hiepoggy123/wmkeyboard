# Strings and translation

Every string a user reads lives in a string resource. This file says where it
goes, what it is called, and how it is written. Follow it when you add a screen.

## Where a string lives

Each Gradle module owns its own `res/values/strings*.xml` and its own `R` class.
The `R` classes are **non-transitive**, so reading another module's string means
naming that module:

```kotlin
import com.wasimaster.wmkeyboard.common.R as CommonR

Text(stringResource(CommonR.string.common_cancel))
```

The namespace is not the package name. `:core:settings` holds code in
`com.wasimaster.wmkeyboard.core.settings` but its `R` is
`com.wasimaster.wmkeyboard.settings.R`. The table:

| Module | key prefix | R class |
|---|---|---|
| `:app` | area name, no module prefix | `com.wasimaster.wmkeyboard.R` |
| `:core:common` | `common_` | `…wmkeyboard.common.R` |
| `:core:settings` | `core_settings_` | `…wmkeyboard.settings.R` |
| `:core:tools` | `core_tools_` | `…wmkeyboard.tools.R` |
| `:core:voice` | `core_voice_` | `…wmkeyboard.voice.R` |
| `:core:content` | `core_content_` | `…wmkeyboard.content.R` |
| `:core:emoji` | `core_emoji_` | `…wmkeyboard.emoji.R` |
| `:core:intelligence` | `core_intel_` | `…wmkeyboard.intelligence.R` |
| `:core:plugins` | `core_plugins_` | `…wmkeyboard.plugins.R` |
| `:core:prediction` | `core_pred_` | `…wmkeyboard.prediction.R` |
| `:core:icons` | `core_icons_` | `…wmkeyboard.icons.R` |
| `:core:theme` | `core_theme_` | `…wmkeyboard.theme.R` |
| `:core:addons` | `core_addons_` | `…wmkeyboard.addons.R` |
| `:core:input` | `core_input_` | `…wmkeyboard.input.R` |
| `:core:language` | `core_lang_` | `…wmkeyboard.language.R` |
| `:core:feedback` | `core_feedback_` | `…wmkeyboard.feedback.R` |
| `:feature:ime` | `ime_` | `…wmkeyboard.ime.R` |
| `:feature:tools` | `ftools_` | `…wmkeyboard.tools.feature.R` |
| `:feature:addons` | `faddons_` | `…wmkeyboard.addons.feature.R` |

The prefixes are not decoration. The resource merger silently lets the app
override a library key of the same name, and the prefixes make that collision
impossible. `:app` splits its own strings by screen area into
`strings_<area>.xml`; Android merges every file in `values/` into one table.

`:core:common` holds the words every screen repeats: `common_ok`,
`common_cancel`, `common_save`, `common_delete`, `common_retry` and the rest.
Reuse them. Adding a second "Cancel" is the mistake this layer exists to stop.

Key names are `<area>_<subject>_<role>`, where role is one of `title`,
`subtitle`, `body`, `label`, `hint`, `action`, `error`, `empty`, `progress`,
`desc` (content description) or `info`.

## What is not a string resource

Preference and DataStore keys, JSON field names, file names and extensions, MIME
types, URLs, intent actions and extras, route strings, log and `DebugLog` text,
`require`/`check`/`error` messages that never reach a screen, regex patterns,
enum `name`s, and stable ids of every kind.

Also deliberately not translated, because they are data rather than language:
language display names and English names, font family names, layout names,
built-in theme ids, emoji keywords and shortcodes, kaomoji and text art, unit
*symbols* (`km`, `kg`, `dp`, `ms`), the transliteration and apostrophe tables,
and the typing-test practice sentences. Unit *names* ("kilometre") are
translated; unit symbols are not.

The bug reports mailed to the maintainer (`core/common/.../Support.kt`,
`DebugLog.kt`, `CrashReportActivity`'s report payload) stay in English on
purpose: they are read by one person, and their field labels are what makes a
report parseable.

## Reading a string from Kotlin

| Context | Use |
|---|---|
| `@Composable` | `stringResource(R.string.key)` |
| `@Composable`, count-dependent | `pluralStringResource(R.plurals.key, count, count)` |
| Has a `Context` | `context.getString(R.string.key)` |
| `Service` / `Activity` | `getString(R.string.key)` |
| Enum or data-class field | store `@StringRes val xRes: Int`, resolve at the UI layer |
| `const val`, annotation, default argument | cannot hold a resource; store the id |

Resolve a string **where it is drawn**, not where the value is produced. That is
what makes a per-app language setting and a locale change work.

You cannot call `stringResource` inside `remember { }`, `LaunchedEffect { }`, a
click lambda, or any other non-composable lambda. Hoist it to a `val` above. A
slider's `display: (Float) -> String` is an ordinary lambda, so hoist the format
string and call `.format()` inside:

```kotlin
val dpFormat = stringResource(R.string.typing_value_dp)
SliderSetting(…, display = { dpFormat.format(it.toInt()) })
```

A number a user reads goes through a resource even when it has no words around
it. `Int.toString()` always writes Western digits; this app has users who read
Bengali, Arabic, Persian and Devanagari digits.

## Classes with no Context

A file reader or a runtime that unit tests drive with no Android around it
cannot put a message into words. It carries the resource instead, and the screen
resolves it. Each module that needs this has one such type, all the same shape:

| Module | type |
|---|---|
| `:core:content` | `ContentText` (stickers, snippets, fonts) |
| `:core:icons` | `IconText` |
| `:core:plugins` | `PluginText` (also has a `Script` case for text from the Lua VM) |
| `:core:language` | `LayoutMessage` |
| `:feature:addons` | `AddonText` |
| `:feature:ime` | `SpokenLabel` for the accessibility name of a key |

Each holds a `@StringRes` id, an optional `@PluralsRes` id with a quantity, and
up to two arguments, plus `resolve(context)`. Do not invent a seventh shape.
Copy the one in the module you are working in.

## Writing the XML

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <!-- %1$s is the language name, %2$d the number of words. -->
    <string name="languages_pack_summary">
        <xliff:g id="language" example="Bangla">%1$s</xliff:g>, %2$d words
    </string>
</resources>
```

- Escape `'` as `\'`, `"` as `\"`, `&` as `&amp;`, `<` as `&lt;`. A leading `@`
  or `?` is `\@` / `\?`. Newlines are `\n`.
- Format arguments are **always positional**: `%1$s`, `%2$d`, never bare `%s`. A
  literal percent sign is `%%`.
- Wrap an argument that must not be translated or reordered in `<xliff:g>`.
- Counts use `<plurals>` with `one` and `other`. Do not add `zero`, `two`, `few`
  or `many`: English does not use them, and a translator adds the ones their
  language needs. Lint enforces this (`PluralsCandidate`), so a number followed
  by a countable noun fails the build until it is a plural. A number followed by
  a unit symbol is not a plural. Mark it `tools:ignore="PluralsCandidate"` with
  a one-line reason.
- Mark a brand name, a code sample or a unit symbol `translatable="false"`.
- Put a comment above every string with a format argument saying what each one
  is, and above any string whose meaning is not obvious from its value. The
  translator sees the XML and nothing else.

## Writing the English

The source language follows ASD-STE100 Simplified Technical English, so that it
reads plainly and translates cleanly.

1. **No em dashes and no en dashes.** Use a full stop and a new sentence, a
   comma, a colon, or brackets. Same for a middle dot used as a separator.
2. One idea per sentence. 20 words for an instruction, 25 for a description.
3. Active voice. "The keyboard saves your themes", not "your themes are saved".
4. One word, one meaning. Use the glossary below.
5. Present tense, simple verbs. Avoid `-ing` as a noun or main verb, except in a
   fixed term ("Voice typing", "Glide typing", "Loading…").
6. Keep the articles. "The keyboard", "a word".
7. Say what happens. Avoid "should", "may", "could".
8. No noun clusters longer than three words.
9. No slang, idioms, jokes or metaphors.
10. Positive form, and "not" at most once per sentence.
11. Sentence case everywhere, titles and buttons included. Proper nouns keep
    their capitals.
12. No trailing full stop on a title, a row subtitle or a button. Dialog bodies
    and info text are full sentences and take one.
13. `…` is the single ellipsis character, not three stops.

### Glossary

Use the left column, never the alternatives.

| Use | Not |
|---|---|
| turn on / turn off | enable, disable, activate, toggle |
| press | tap, hit, click, touch |
| press and hold | long press, hold down |
| swipe | slide (drag is only for moving an object) |
| the keyboard | the IME, the input method |
| the suggestion strip | the suggestion bar, the candidate bar |
| the toolbar | the tool row |
| select | choose, pick |
| delete | remove, erase (clear is only for clearing a field) |
| the screen | the page, the view |
| a setting | an option, a preference |
| the app | the application |
| your device | your phone, your handset |
| a theme | a skin |
| an add-on | an addon |
| a plugin | a plug-in |
| voice typing | dictation |
| glide typing | swipe typing, gesture typing |
| full stop | period |
| capital letter | uppercase letter (UPPERCASE names a mode) |
| download | fetch, pull |

## Adding a translation

Add `res/values-<lang>/strings*.xml` to the module whose strings you translate.
Include only the keys you translate. Anything missing falls back to the English
in `values/`. Lint has `MissingTranslation` and `ExtraTranslation` turned off in
`config/lint/lint.xml`, so a partial translation does not fail the build.

Two things the settings search depends on, which a translation must not break:
`SettingsSearch.kt` builds its index from the same resource ids the screens draw,
and `SettingsHighlight` matches a row by resource id rather than by its words.
Both keep working in any language on their own. Neither needs a translator to do
anything.
