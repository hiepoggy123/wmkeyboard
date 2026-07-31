# harper-jni — offline grammar engine

Rust cdylib wrapping [Harper](https://github.com/automattic/harper)
(`harper-core`) behind a tiny JNI surface for the keyboard's grammar tool.
Kotlin entry point: `com.wasimaster.wmkeyboard.core.grammar.HarperNative`.

The prebuilt `libharper_jni.so` files are committed under
`core/intelligence/src/full/jniLibs/<abi>/` so the app builds without a Rust toolchain.
Rebuild them after changing this crate or bumping `harper-core`:

```sh
# One-time setup
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
brew install --cask android-ndk   # or any NDK; set ANDROID_NDK_HOME

# Build all supported ABIs (arm64-v8a, armeabi-v7a, x86_64) into core/intelligence (from this directory)
ANDROID_NDK_HOME=/opt/homebrew/share/android-ndk \
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 --platform 24 \
  -o ../../core/intelligence/src/full/jniLibs build --release
```

Host-side tests (`cargo test`) exercise the lint→JSON path without Android.

## Contract with Kotlin

- `nativeLint(text, dialect) -> String` — JSON array of
  `{start, end, original, kind, message, priority, suggestions:[{kind, text}]}`.
  `start`/`end` are **UTF-16** code-unit indices (Kotlin string indexing);
  the char→UTF-16 conversion happens on the Rust side.
- `dialect` ordinal matches `GrammarDialect` in `SettingsRepository.kt`:
  0 American, 1 British, 2 Canadian, 3 Australian. Append-only.
- Harper's `LintGroup` is not `Send`, so the linter cache is thread-local:
  `GrammarChecker` funnels every call through one dedicated thread.
