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

**`prebuild`** — two `sed` commands, both for the source scanner. It is a text
search over the build files, run before the build and fatal on a match, and it
does not read build logic, so it flags strings this build never resolves:

* `org.gradle.toolchains.foojay-resolver-convention` in `settings.gradle.kts`,
  which would fetch a JDK over the network. Their tooling deletes the
  `gradle-daemon-jvm.properties` the plugin maintains, and auto-provisioning is
  switched off on their builders regardless, so the plugin can do nothing there
  but fail the scan. **Consequence worth keeping in mind: with foojay gone, any
  `jvmToolchain(n)` in the tree is fatal unless `n` is a JDK their image already
  carries.** `:tools:dictc` learned this the hard way in 0.5.4; it sets a
  jvmTarget instead now, which needs no particular JDK installed.
* `libs.play.app.update`, `libs.play.feature.delivery` and
  `libs.play.services.auth` in `app/build.gradle.kts`. All three sit inside
  `if (playStoreChannel)` / `if (gmsChannel)` blocks, which are false for this
  recipe, so they are never on the compile classpath. The blocks are left empty
  by the `sed`, which is valid Kotlin.

Deleting the lines rather than listing the files in `scanignore` is deliberate:
`scanignore` asks a packager to take the build file on trust, and this way the
scanner reads a tree that genuinely does not mention them. `prebuild` runs before
the scanner (`prepare_source` precedes `scan_source` in fdroidserver's
`build.py`), with the working directory set to `subdir`, which is why the first
command reaches up with `../`.

The 0.5.4 entry carried two more, both since fixed in the source: one deleted the
`.takeIf` line their signing strip orphaned, and one deleted `jvmToolchain(17)`
from `:tools:dictc`. Neither is needed from 0.5.5 on. They are in this file's git
history if a build of an older tag ever has to be reproduced.

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

fdroiddata's CI runs four jobs against a changed metadata file, and all four can
be run locally. Two things have to be right first.

**Use fdroidserver from git master, and pin `ruamel.yaml` to 0.18.6.** Both
matter, and each cost a failed pipeline to find. Their CI installs Debian's
`fdroidserver` package first and then overlays master's source on `PATH`, so the
*code* is master but the *YAML library* is Debian trixie's. Homebrew's 2.4.5
wrote a `\` line continuation into the `AntiFeatures` string that master does
not; and ruamel 0.19 wraps at a different column than 0.18, which rewrites every
wrapped line. Either one is enough to fail the `rewritemeta` job, whose test is a
byte-for-byte diff. With master plus 0.18.6 the local output matches CI's
artifact exactly.

**`fdroid lint` needs fdroiddata's own `config/` directory** — the valid category
and anti-feature name lists live there, and without them lint rejects both. A
plain `git clone` of fdroiddata fails on some networks; the archive does not.

```sh
python3 -m venv /tmp/fdsvenv
/tmp/fdsvenv/bin/pip install git+https://gitlab.com/fdroid/fdroidserver.git check-jsonschema
/tmp/fdsvenv/bin/pip install 'ruamel.yaml==0.18.6'   # pin AFTER, see below
mkdir -p /tmp/fd && cd /tmp/fd
curl -sS --http1.1 -o config.tar.gz \
  'https://gitlab.com/fdroid/fdroiddata/-/archive/master/fdroiddata-master.tar.gz?path=config'
tar xzf config.tar.gz && mv fdroiddata-master-config/config .
curl -sS --http1.1 -o metadata.json \
  'https://gitlab.com/fdroid/fdroiddata/-/raw/master/schemas/metadata.json'
mkdir -p metadata && cp ~/Work/WMKeyboard/fdroid/com.wasimaster.wmkeyboard.yml metadata/
export PATH=/tmp/fdsvenv/bin:$PATH
fdroid lint com.wasimaster.wmkeyboard        # must exit 0 and print nothing
fdroid rewritemeta com.wasimaster.wmkeyboard # must leave the file unchanged
fdroid checkupdates --auto com.wasimaster.wmkeyboard  # must leave the file unchanged
check-jsonschema --schemafile metadata.json metadata/com.wasimaster.wmkeyboard.yml
```

The fifth job, `fdroid build`, runs the real build in an image provisioned like
the production buildserver. There is no local stand-in for it, but building the
tag in a clean worktree with no `local.properties` and the `scandelete`
directories removed is the same thing in miniature — see the F-Droid steps in
`docs/development/releasing`.

When `rewritemeta` does disagree with you, its job uploads what it wanted under
`tmp/` in the job artifacts, reachable at
`.../-/jobs/<job id>/artifacts/raw/tmp/com.wasimaster.wmkeyboard.yml`. Diffing
against that is faster than guessing.

## Why there is no `AntiFeatures` block

`NonFreeNet` was declared until 0.5.6, for the tools that reached proprietary
services. It is gone because every one of those tools now has a free,
self-hostable option the user can point at, which is the condition F-Droid's own
rule waives the anti-feature on: translate takes a LibreTranslate instance,
search a SearXNG one, photos and GIFs any MediaWiki, and the AI tool has always
taken an Ollama URL. Backups already offered WebDAV beside Dropbox.

Two things still reach a proprietary service with no free instance to swap in,
both inside optional tools that work without them: **crypto exchange rates**
(Coinbase, CoinGecko, currency-api — fiat rates have Frankfurter, which is free
software, but crypto has no equivalent) and the **Keyman layout catalogue**
(`api.keyman.com` — the software is open source, the catalogue is theirs). Say
so plainly if a packager asks rather than letting them find it; if they judge
either one disqualifying, the field goes back.

## A note on the wrapped strings

`rewritemeta` wraps any value too long for its line width, and the wrap leaves a
trailing space on each continued line it produces. That is its output, so it
cannot be cleaned up without failing the job, and it is harmless: YAML strips a
trailing space before a folded line break, so the string parses back with single
spaces and no newlines. Keep any long value free of real line breaks — an
earlier `AntiFeatures` description was written as a `|-` block and every wrap
point became a literal `\n` in the middle of a sentence.
