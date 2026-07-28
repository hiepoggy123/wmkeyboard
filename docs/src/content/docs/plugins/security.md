---
title: "The plugin sandbox"
description: "What the Lua sandbox can do, how that is enforced, and where the limits are."
sidebar:
  label: "Security"
  order: 5
---

What plugins can do, how that is enforced, and where the limits honestly are.

Written for three audiences: users deciding whether to trust the feature,
developers who want to know what they are working inside, and reviewers checking
what third-party code on a keyboard is allowed to reach.

## The invariants

Each of these is a property of what was built, not a policy that could be
configured differently. Each has a test.

1. **Plugins cannot read what you type.** There is no key-event API. The IME's
   typing, prediction and autocorrect paths have no plugin hook at all — a
   plugin cannot observe, alter or delay a single keystroke on its way to your
   app.
2. **Plugins cannot read the text field.** No `InputConnection` read path is
   exposed; no function returns the contents of what you are writing in.
3. **Plugins cannot read the clipboard.** No API.
4. **Plugins cannot reach the network.** No HTTP client, no sockets, no URL
   handling. The sandbox has zero egress.
5. **Plugins cannot execute downloaded code.** `load`, `loadstring`, `dofile`
   and `loadfile` are removed and no bytecode undumper is installed, so a
   plugin cannot turn a string into runnable Lua. Precompiled Lua is refused at
   install.
6. **Plugins cannot reach Android.** No `luajava`, no `require`, no reflection,
   no class loading, no `io`, no `os` beyond the clock, no file paths, no
   Intents, no `Context`.
7. **Plugins cannot see other apps or the device.** No package enumeration, no
   identifiers, no location, contacts, camera, microphone, SMS or
   accessibility.
8. **Plugins cannot run in the background.** Execution exists only while the
   plugin's panel is open. No timers, no callbacks that outlive it.
9. **Plugins cannot change the app.** They render inside their own panel below a
   host-drawn title bar, and cannot alter keyboard behaviour, request
   permissions, or affect any other feature.

Anything a plugin holds, you gave it on purpose — and it has nowhere to send it.

**Data safety:** the app collects and shares nothing new because of this feature.

## How it is enforced

### The interpreter

Lua 5.2 via [LuaJ](https://github.com/luaj/luaj) 3.0.1, a pure-JVM interpreter
with no native code. Each plugin session gets a fresh environment holding only
`BaseLib`, `Bit32Lib`, `TableLib`, `StringLib`, `JseMathLib` and the compiler.

The omissions matter more than the inclusions:

- **`PackageLib` is never installed.** Its `require` resolves module names
  through `Class.forName` and instantiates the result — a straight line from a
  script to arbitrary JVM classes. This is the single most important library not
  to install.
- **`CoroutineLib` is never installed.** LuaJ implements coroutines as real,
  non-daemon Java threads and reclaims them only when they yield, so a script
  spawning non-yielding coroutines would leak OS threads that outlive the
  keyboard.
- **`IoLib` and `OsLib` are never installed.** `os` is replaced by a
  pure-Kotlin table that can tell the time and nothing else.
- **`LuajavaLib` is never installed** — and, because nothing references it, R8
  strips it from the shipped APK entirely, along with the JVM-coercion helpers,
  the bytecode backend and the JSR-223 engine. Verified against the release
  build's mapping file: the reflective interop surface is not in the binary.

The `debug` library *is* loaded, because the interpreter's per-instruction hook
lives on it, and then removed from the environment so no script can reach it.

### Stopping runaway scripts

Two mechanisms, because one is not enough.

The interpreter calls a hook before every bytecode instruction. That hook spends
an instruction budget and checks a wall-clock deadline, and throws when either
runs out. The abort extends `java.lang.Error` rather than `Exception`
specifically because LuaJ's `pcall` catches `LuaError` and `Exception` only — so
a script cannot swallow its own termination in a `pcall` loop. There is a test
that proves exactly that.

What the hook cannot catch is a thread inside LuaJ's own Java code, where no
bytecode boundary is crossed — a pathological `string.gsub` is the realistic
case. A watchdog covers that: it notices a call past its deadline with the hook
plainly not firing, and *abandons* the thread.

Abandoning is not killing, and it is worth being precise. `Thread.stop` is gone
from the platform and LuaJ never polls for interrupts, so a thread spinning in
Java keeps spinning. What abandonment does is sever it — the session is revoked
so every host call it might still make throws instead of acting, its executor is
shut down, its priority is dropped to minimum, and the runtime forgets it. It
burns CPU as a background daemon until the process ends, and can affect nothing
else. A plugin that does this twice is switched off until the user re-enables
it.

### Memory

There is no per-thread heap limit on Android, so this is layered rather than
absolute: the script is capped at 256 KB, the instruction budget bounds the
allocation *rate*, `string.rep` and the pattern functions and `table.concat` are
wrapped with output caps, every string crossing the API boundary is capped, the
widget tree is capped, storage is quota'd, and only one plugin runs at a time.

Honest residual: repeated string doubling (`s = s .. s`) reaches gigabytes in
about twenty instructions, and concatenation is not something the hook can price.
The allocating thread gets an `OutOfMemoryError`, which is caught at the session
boundary and drops the environment; worst case the OS kills the keyboard process
and it restarts clean. That is a crash, not a leak — no data crosses any
boundary — and the strike system disables a plugin that does it repeatedly.

### Cross-plugin isolation

LuaJ's string metatable is a **process-global static**, populated from whichever
environment is built first. Left alone, one plugin writing to
`getmetatable("").__index` would rewrite string methods for every other plugin in
the process and for every session afterwards. It is replaced with a frozen table
that refuses writes and hides itself behind `__metatable`, so `getmetatable("")`
returns an opaque marker and no reference escapes.

Everything else is per-session: a fresh environment per plugin per panel opening,
discarded when the panel closes.

### The key-routing gate

The one mechanism that could break invariant 1 if it were wrong.

A plugin's text box is not a real text field — there are no `TextField`s anywhere
in the keyboard UI, since they fight the `InputConnection`. Tapping one instead
makes the keyboard route keystrokes into a host-owned buffer, and hand the
plugin the resulting *contents*. While that routing is on, what the user types
goes to a script rather than to their app, so:

- it requires both the Plugins panel to be open *and* a widget to be focused;
- it is gated at all five keystroke entry points (characters, backspace, space,
  enter, gesture typing), each returning before the `InputConnection` is
  touched;
- it is switched off when the panel closes, when the keyboard hides, when the
  focused field changes, and when the plugin stops drawing that widget;
- the runtime is torn down at the same moment, so afterwards there is no plugin
  left in the process to receive anything.

### Distribution

Plugins arrive as `.wmplugin` archives from an addon repository or a local file.
Archive entry names are never used as filesystem paths, the plugin id is proven
to be a single safe lowercase path segment before it becomes a directory, and
archives are capped in size and entry count.

For plugins alone, a **SHA-256 is mandatory** — for every other addon type a
missing checksum only means "unverified", but the app will not install code it
cannot verify.

The whole subsystem is **off until the user turns it on**, and while it is off
nothing installs and nothing runs. Before any install, the user is shown what the
plugin says it is and what it would be allowed to do; only the manifest is read
to build that screen, never the script.

## What this does not protect against

Stated plainly, because a security document that only lists strengths is not
useful.

- **A bug in LuaJ itself.** The interpreter runs in the app's process, so an
  interpreter escape would be an app compromise. Mitigations: no `luajava`, no
  reflection surface, no class loading, minimal host objects (plain functions
  over immutable strings and numbers), and a pinned version whose relevant
  internals were audited. The runtime is kept behind an interface so it could be
  moved into an `isolatedProcess` later; that is not done today.
- **A plugin that wastes CPU.** Contained, not prevented — see abandonment
  above. A determined script can burn one background thread until the process
  ends.
- **A plugin that lies about what it does.** Nothing stops a plugin called
  "Calculator" from being a poem generator. It still cannot read your text or
  reach the network, so the worst case is a waste of your time.
- **Anything you paste into it.** If you paste a password into a plugin's box,
  that plugin has your password. It cannot send it anywhere, but it is worth
  saying out loud.

## Reporting

Found something wrong with any of this? Please open an issue on the
[WM Keyboard repository](https://github.com/wasi-master/WMKeyboard) — a way for a
plugin to reach text, the clipboard or the network is a security bug, not a
feature request, and will be treated as one.
