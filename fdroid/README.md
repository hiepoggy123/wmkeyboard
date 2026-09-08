# F-Droid build recipe

`com.wasimaster.wmkeyboard.yml` is the recipe F-Droid builds the app from. It is
**not** read by F-Droid from this repository: it is copied to
`metadata/com.wasimaster.wmkeyboard.yml` inside a fork of
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) and submitted as a merge
request. The copy lives here so the recipe is versioned next to the code that
has to keep satisfying it. The walk-through is in
[docs/development/releasing](../docs/src/content/docs/development/releasing.mdx).

The file itself carries no comments, and it cannot. fdroiddata's CI runs
`fdroid rewritemeta` on every changed metadata file and fails the pipeline if
the result differs by a byte; `rewritemeta` strips comments and reorders fields.
So the recipe is kept in exactly the form `rewritemeta` emits, and everything
that would have been a comment is written down here instead.

## Why each field is what it is

**`gradle: [lite]`** — the lite flavour. Every proprietary dependency in the
tree (ML Kit, LiteRT, Play in-app updates, `play-services-auth`) is declared
`fullImplementation` or sits behind the `enablePlayStore`/`enableGms` flags, so
this variant has no Google artifact on its compile classpath at all.

**`gradleprops`** — the first two default to `false` when `local.properties` is
absent, which it is in a clean checkout; they are stated anyway so the recipe
does not depend on that. `wmkb.enableFdroid=true` is not a default and has to be
set here. It is what tells the built app it is an F-Droid install, which decides
the channel line on bug reports and diagnostics, and which suppresses the "get it
on F-Droid" row in About that would otherwise point an F-Droid user at their own
install.

**`scandelete`** — prebuilt native libraries belonging to variants this build
does not produce. The Harper grammar engine lives in the full source set (its
Rust sources are under `native/harper-jni`), and the LLM module is only assembled
for the Play channel. Deleting them keeps F-Droid's scanner quiet and proves
neither reaches the built APK.

**One `Builds:` entry** — F-Droid's buildserver builds every entry it has not
seen. Shipping the back catalogue in a first submission would spend their build
time on versions nobody can install any more. Entries for 0.3.0 through 0.5.3 are
in this file's git history.

**`UpdateCheckMode: HTTP`, not `Tags`** — Tags mode scans the Gradle build file
for a `versionCode`/`versionName` literal, and `:app` has neither: both come from
`gradle.properties` through `providers.gradleProperty()`. HTTP mode reads that
file directly instead. The third field of `UpdateCheckData` is `.`, which
fdroidserver reads as "the same URL again" for the versionName lookup.

**`AutoUpdateMode: Version v%v`** — releases are tagged `v<versionName>`, and
every release uses the same build configuration, so their bot can add the next
`Builds:` entry by copying this one. Verify a release still satisfies the recipe
before tagging (`fdroid checkupdates` is what the bot runs).

**No `Name`, `Summary`, `Description`, icon or screenshots** — F-Droid reads
those from `fastlane/metadata/android/en-US/` in this repo, so they only have to
be right in one place. `AutoName` is written by `fdroid checkupdates`; leaving it
out makes fdroiddata's CI add it and fail on the resulting diff.

## Checking a change before submitting it

fdroiddata's CI runs three jobs against a changed metadata file. All three can be
run locally, but `fdroid lint` needs fdroiddata's own `config/` directory (its
category and anti-feature name lists), so fetch that first — a plain `git clone`
of fdroiddata fails on some networks, the archive does not:

```sh
mkdir -p /tmp/fd && cd /tmp/fd
curl -sS --http1.1 -o config.tar.gz \
  'https://gitlab.com/fdroid/fdroiddata/-/archive/master/fdroiddata-master.tar.gz?path=config'
tar xzf config.tar.gz && mv fdroiddata-master-config/config .
mkdir -p metadata && cp ~/Work/WMKeyboard/fdroid/com.wasimaster.wmkeyboard.yml metadata/
fdroid lint com.wasimaster.wmkeyboard        # must exit 0
fdroid rewritemeta com.wasimaster.wmkeyboard # must leave the file unchanged
fdroid checkupdates --auto com.wasimaster.wmkeyboard  # must leave the file unchanged
```

`fdroid lint` reports one `trailing spaces` warning on the `UpdateCheckData:`
line. That whitespace is written by `rewritemeta` itself, which wraps any value
too long for its line width, so it cannot be removed without failing the
`rewritemeta` job. The warning does not affect lint's exit status.
