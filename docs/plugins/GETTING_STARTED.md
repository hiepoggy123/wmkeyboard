# Writing your first plugin

By the end of this you will have built the Cipher Tool: a keyboard panel with
two tabs that encodes and decodes text. No Lua experience assumed. If you have
written any programming language at all, you already know enough.

You need a text editor and a way to make a ZIP file. That is the whole toolchain.

## 1. Two files in a folder

Make a folder called `cipher` with two files in it.

**`plugin.json`** — who your plugin is:

```json
{
  "format": "wmkeyboard-plugin",
  "version": 1,
  "id": "com.yourname.cipher",
  "name": "Cipher Tool",
  "pluginVersion": "1.0.0",
  "author": "Your Name",
  "description": "Caesar and Vigenere ciphers.",
  "apiVersion": 1,
  "entry": "main.lua",
  "permissions": []
}
```

The `id` has to be unique, lowercase, and look like a reverse domain name — it
becomes the folder your plugin lives in on the device. `permissions` is empty
because this plugin does not need anything; most do not.

**`main.lua`** — what it does:

```lua
function render()
  return ui.label { text = "Hello from a plugin" }
end
```

## 2. Make it a .wmplugin

A `.wmplugin` is a ZIP with those two files at the top level:

```bash
cd cipher && zip ../cipher.wmplugin plugin.json main.lua
```

Nothing else to it. Put `cipher.wmplugin` on your phone, open it from a file
manager, and confirm the install. (Turn plugins on first, under **Settings →
Tools → Plugins**.) Open the **Plugins** tool on the keyboard, tap Cipher Tool,
and you should see your greeting.

Leave that file manager open — you will be replacing the ZIP and reinstalling as
you go, which takes about ten seconds each time.

## 3. How a plugin actually works

Two functions. That is the entire model.

```lua
function render()   -- describe what should be on screen right now
function on_event(e) -- react to the user doing something
```

The keyboard calls `render()`, draws what it returns, and waits. When the user
taps something, it calls `on_event(e)`, then calls `render()` again and redraws.

So you never update the screen yourself. You change a variable and describe the
result. If you have used React this will feel familiar; if you have not, the
rule is simply *`render()` says what things look like, `on_event` says what
changes*.

`render()` returns tables. The `ui.*` helpers just build them for you:

```lua
ui.button { id = "go", text = "Go" }
-- is exactly
{ type = "button", id = "go", text = "Go" }
```

## 4. A button that does something

Replace `main.lua` with:

```lua
local clicks = 0

function on_event(e)
  if e.type == "click" and e.id == "go" then
    clicks = clicks + 1
  end
end

function render()
  return ui.column {
    ui.label { text = "Clicked " .. clicks .. " times" },
    ui.button { id = "go", text = "Click me", style = "primary" },
  }
end
```

`ui.column` stacks things vertically. `..` joins strings in Lua. Every control
needs an `id`, which is what comes back to you in `e.id`.

Rebuild, reinstall, tap the button a few times.

## 5. Getting text from the user

A plugin cannot read what you are typing in your messaging app — there is no API
for it, on purpose. Instead you draw your own box, and the user types or pastes
into it:

```lua
local message = ""

function on_event(e)
  if e.type == "input_changed" and e.id == "message" then
    message = e.value
  end
end

function render()
  return ui.column {
    ui.input { id = "message", label = "Message", placeholder = "Type here" },
    ui.label { text = "You wrote: " .. message },
  }
end
```

Tap the box and the keyboard starts typing into it instead of into your app;
there is a **Paste** button beside it for text you already have. You get an
`input_changed` event with the new contents each time it changes.

## 6. The actual cipher

A Caesar cipher shifts every letter along the alphabet. In Lua:

```lua
local function caesar(text, by)
  by = by % 26
  return (text:gsub("%a", function(c)
    local base = c:match("%u") and 65 or 97
    return string.char((c:byte() - base + by) % 26 + base)
  end))
end
```

Reading that: `gsub` replaces every match of a pattern. `%a` means "any letter",
`%u` means "an uppercase letter". `base` is 65 for uppercase and 97 for
lowercase (the character codes for `A` and `a`), so the arithmetic wraps within
the right case. The outer brackets around `text:gsub(...)` throw away the second
value `gsub` returns — Lua functions can return several, and here we only want
the string.

Now wire it up:

```lua
local message = ""
local shift = "3"
local output = ""

local function caesar(text, by)
  by = by % 26
  return (text:gsub("%a", function(c)
    local base = c:match("%u") and 65 or 97
    return string.char((c:byte() - base + by) % 26 + base)
  end))
end

function on_event(e)
  if e.type == "input_changed" then
    if e.id == "message" then message = e.value end
    if e.id == "shift" then shift = e.value end
  elseif e.type == "click" then
    local by = tonumber(shift) or 0
    if e.id == "encode" then output = caesar(message, by) end
    if e.id == "decode" then output = caesar(message, -by) end
  end
end

function render()
  return ui.column {
    ui.input { id = "message", label = "Message", placeholder = "Type or paste" },
    ui.input { id = "shift", label = "Shift", placeholder = "3" },
    ui.row {
      ui.button { id = "encode", text = "Encode", style = "primary" },
      ui.button { id = "decode", text = "Decode" },
    },
    ui.output { id = "result", text = output, mono = true },
  }
end
```

`ui.row` puts things side by side. `ui.output` is a result block — and it is the
important one: it comes with an **Insert** button that puts the text into
whatever the user is writing in. That is the only way a plugin's output reaches
their text. A plugin cannot type on its own; the user taps Insert.

Rebuild, reinstall, type something, tap Encode, tap Insert.

## 7. Two tabs

The finished demo has a second cipher on its own tab:

```lua
function render()
  return ui.tabs {
    id = "cipher",
    ui.page {
      title = "Caesar",
      ui.input { id = "message", label = "Message" },
      -- ...the rest of the Caesar page
    },
    ui.page {
      title = "Vigenere",
      -- ...
    },
  }
end
```

Pages go in the array part of the table, which is why they have no `=` in front
of them. The full source, including Vigenere, is
[`plugins-src/cipher-tool/main.lua`](https://github.com/wasi-master/wmkeyboard-addon-repository/tree/main/plugins-src/cipher-tool)
in the addon repository.

## 8. Debugging

`print()` and `wm.log()` both write to your plugin's log, which you can read
under **Settings → Tools → Plugins → your plugin**. That is your only window
into a running plugin, so use it freely.

If the script fails, the panel shows the error and the plugin stays loaded — fix
it, rebuild, reinstall.

Two limits worth knowing before you hit them. Your code gets a few million
instructions and a couple of seconds per event; an accidental infinite loop is
stopped and reported rather than hanging the keyboard. And if a plugin becomes
unresponsive twice, it is switched off until you turn it back on.

## 9. Publishing

Anyone can install a `.wmplugin` file directly. To list it in an addon
repository, add an entry pointing at the file:

```json
{
  "id": "cipher-tool",
  "type": "plugin",
  "name": "Cipher Tool",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "Caesar and Vigenere ciphers.",
  "path": "plugins/cipher-tool.wmplugin",
  "sha256": "…",
  "sizeBytes": 1693,
  "license": "MIT"
}
```

The `sha256` is **required** for plugins, unlike every other addon type — the app
will not install code it cannot verify. `tools/build_index.py` in the sample
repository fills it in for you, and `tools/validate.py` checks it.

## Where to next

- [API_REFERENCE.md](API_REFERENCE.md) — every widget, event and function.
- [PERMISSIONS.md](PERMISSIONS.md) — how to ask for storage, and why that is the
  only thing to ask for.
- The **UI Kitchen Sink** demo — every widget on screen at once, with a live log
  of the events they produce.
