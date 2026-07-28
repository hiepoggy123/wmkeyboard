---
title: "Plugin API reference"
description: "Everything a plugin can use: manifest, widgets, events, wm.*, limits."
sidebar:
  label: "API reference"
  order: 3
---

Everything a plugin can use. If it is not here, it does not exist.

- [The manifest](#the-manifest)
- [Your two functions](#your-two-functions)
- [Widgets](#widgets)
- [Events](#events)
- [`wm.*`](#wm)
- [The Lua you get](#the-lua-you-get)
- [Limits](#limits)

## The manifest

`plugin.json`, at the top level of the archive.

| Field | Required | Meaning |
|---|---|---|
| `format` | yes | Always `"wmkeyboard-plugin"`. The one tag that identifies the file. |
| `version` | — | Container format version. `1`. |
| `id` | yes | Unique, lowercase, 3–64 characters of `a-z 0-9 . _ -`, starting alphanumeric. Becomes the plugin's folder on the device. Reverse-DNS by convention. |
| `name` | yes | Shown everywhere. Trimmed to 40 characters, control and direction-override characters removed. |
| `pluginVersion` | yes | Your version, e.g. `"1.2.0"`. |
| `author` | — | Shown on the install screen. |
| `description` | — | One or two sentences. |
| `apiVersion` | — | The API level you need. `1`. A higher number than the app knows is refused. |
| `entry` | — | The script inside the archive. Defaults to `main.lua`. |
| `permissions` | — | See [PERMISSIONS.md](/plugins/permissions/). Usually `[]`. |

A manifest this build does not fully understand is **refused**, not repaired —
including any permission string it does not recognise. Half-understood code does
not get to run.

## Your two functions

```lua
function render()    --> a widget, or a list of widgets
function on_event(e) --  optional
```

`render()` is called after loading and after every event. `on_event(e)` is
called when the user does something. Change your variables in `on_event`,
describe the result in `render()`.

Both are plain globals. There is no registration step.

## Widgets

Built by `ui.*`, which are pure-Lua helpers that return tables — you can write
the tables yourself if you prefer.

### Layout

```lua
ui.column { child, child, ... }   -- stacked vertically
ui.row    { child, child, ... }   -- side by side, equal widths
ui.spacer { height = 12 }         -- vertical gap, 0-64
ui.divider()                      -- a horizontal line
```

Children go in the array part, which is why they have no `name =` in front.

### Text

```lua
ui.label { text = "…", style = "title" | "body" | "caption" }
```

`body` is the default. Labels are not interactive and have no id.

```lua
ui.output {
  id = "result",
  text = "…",
  mono = false,        -- monospace, for anything with alignment
  insertable = true,   -- show the Insert button (default true)
  copyable = true,     -- show the Copy button (default true)
}
```

**`ui.output` is how your results reach the user's text.** The keyboard draws
Insert and Copy buttons under it; tapping Insert puts the text where they are
writing. There is no function that types for them.

### Controls

```lua
ui.button { id = "go", text = "Go", style = "primary", enabled = true }
ui.toggle { id = "caps", label = "Uppercase", checked = false }
ui.input  { id = "msg", label = "Message", placeholder = "Type here" }
```

`style = "primary"` highlights a button; anything else is plain. `enabled =
false` greys one out.

**`ui.input` carries no value.** The keyboard owns what is in the box: tapping it
points the keys at it, and you are told the contents through an `input_changed`
event. Use `wm.ui.set_input(id, text)` to write to one. This is why a plugin
never sees a keystroke — only the finished contents of its own box.

### Tabs

```lua
ui.tabs {
  id = "modes",
  ui.page { title = "First", child, child },
  ui.page { title = "Second", child },
}
```

Top level only, up to 8 pages. Which page is showing is the keyboard's business;
you get a `tab_selected` event if you care.

### Progress

```lua
ui.progress()   -- an indeterminate bar
```

## Events

`on_event(e)` receives a table. Always `e.type` and `e.id`; some carry more.

| `e.type` | Extra | Fired when |
|---|---|---|
| `"click"` | — | A button was tapped. |
| `"toggle"` | `e.value` (boolean) | A toggle was flipped. |
| `"input_changed"` | `e.value` (string) | The contents of one of *your* boxes changed. |
| `"tab_selected"` | `e.index` (number, 1-based) | A tab was picked. |

`input_changed` fires per keystroke into your own input widget. That is how a
live preview works, and it is bounded to your panel — you are told what is in
your box, never what is typed anywhere else.

## `wm.*`

The complete host API.

### Always available

```lua
wm.api_version      -- 1
wm.plugin_id        -- your manifest id
wm.plugin_version   -- your manifest pluginVersion
wm.log(message)     -- a line in your log, readable in Settings
```

```lua
wm.ui.set_input(id, text)   -- write to one of your own input widgets
```

Applied after your handler returns. Capped at 8 KB.

```lua
wm.json.decode(text)   --> table, or nil + reason
wm.json.encode(value)  --> string, or nil + reason
```

A Lua table encodes as a JSON array when its keys are exactly `1..n`, and as an
object otherwise. Whole numbers stay whole. Depth is capped, which is also what
stops a self-referencing table from encoding forever.

### With the `storage` permission

```lua
wm.storage.get(key)      --> string, or nil
wm.storage.set(key, val) --> true, or nil + reason
wm.storage.remove(key)
wm.storage.keys()        --> list of strings
```

Strings only — use `wm.json` for anything structured. Local to your plugin, local
to the device, deleted when the user uninstalls you. `set` returns `nil` and a
reason when you are over quota; say something rather than losing the data
silently.

If the manifest did not declare `storage`, `wm.storage` is simply `nil`.

### Not present, and never will be

`wm.text`, `wm.clipboard`, `wm.http`, `wm.net`, `wm.files` — there is no API for
reading what the user types, reading the field, reading the clipboard, or
reaching the network. See [SECURITY.md](/plugins/security/).

## The Lua you get

Lua 5.2 via [LuaJ](https://github.com/luaj/luaj), with these libraries:

**Available:** `string`, `table`, `math`, `bit32`, the base functions (`assert`,
`error`, `getmetatable`, `setmetatable`, `ipairs`, `pairs`, `next`, `pcall`,
`xpcall`, `rawget`, `rawset`, `rawequal`, `rawlen`, `select`, `tonumber`,
`tostring`, `type`, `_G`, `_VERSION`), `print`, and a reduced `os`.

**`os`** has `os.time()`, `os.clock()` and `os.date(format, time)` — with `"*t"`
and a strftime subset (`%Y %y %m %d %H %M %S %j %p %A %a %B %b %c %x %X %%`), and
a leading `!` for UTC. Nothing else: no `getenv`, `execute`, `exit`, `remove`,
`rename` or `tmpname`.

**Absent:** `io`, `require`, `package`, `coroutine`, `debug`, `luajava`, `load`,
`loadstring`, `loadfile`, `dofile`. You cannot load code at runtime, and
precompiled Lua is refused at install.

**`collectgarbage`** exists but does nothing and returns 0.

One incompatibility worth knowing: adding to the `string` table works for
`string.mine(s)` but not for `s:mine()`. Method syntax resolves through a
process-wide table that is frozen so one plugin cannot rewrite string handling
for every other one.

## Limits

Exceeding one of these is an error you can see, not a silent truncation, except
where noted.

| | |
|---|---|
| Script size | 256 KB |
| Archive size | 1 MB, 16 entries |
| Instructions | 30M on load, 20M per event, 4M per render |
| Time | 3 s on load, 2 s per event, 0.5 s per render |
| Widgets | 256 nodes, 12 deep, 8 tabs |
| Widget text | 2 KB per node, 64 KB per tree *(truncated, and reported)* |
| `string.rep` output | 256 KB |
| Pattern subject / pattern | 256 KB / 256 bytes |
| `table.concat` output | 1 MB |
| Storage | 128 keys, 64 chars per key, 8 KB per value, 64 KB total |
| Log | 200 lines of 512 characters |
| Input box | 8 KB |

A plugin that runs out of instructions or time is stopped and the user is told.
One that becomes unresponsive twice is switched off until they turn it back on.
