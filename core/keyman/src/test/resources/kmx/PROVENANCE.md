# Test fixtures: compiled Keyman keyboards

Binary `.kmx` files, extracted from the official `.kmp` packages published at
`downloads.keyman.com`. They exist so `KmxParserTest` can prove the parser
against real compiler output rather than against bytes this repository wrote
itself, which would only prove the parser agrees with its own assumptions.

These are test resources. They are not on any source set that reaches the APK.

Each keyboard is MIT licensed, as every keyboard in the `release/` tree of
keymanapp/keyboards is; the accompanying `<id>.LICENSE.md` is that keyboard's
own licence file, copied verbatim with its copyright holder.

Upstream: https://github.com/keymanapp/keyboards
Commit:   ec743357fb22a74db1c7d780f3f63fa2715e5836
Fetched:  2026-08-12

| file | why it is here |
|---|---|
| `basic_kbdus.kmx` | The simplest possible keyboard: US English, no deadkeys. |
| `khmer_angkor.kmx` | Heavy context matching and reordering; the shape the engine exists for. |
| `lao_2008_basic.kmx` | Non-Latin with a small rule set. |
| `sil_euro_latin.kmx` | Deadkey-heavy, and the widest language list in the corpus. |
| `sil_ipa.kmx` | Large store set and long context rules. |
