---
title: "WM Keyboard plugins"
description: "Plugins are small sandboxed Lua scripts that add tools to the keyboard."
sidebar:
  label: "Overview"
  order: 1
---

A plugin is a small Lua script that adds a tool to the keyboard. It draws a
panel — some text, a few buttons, maybe a text box — and does whatever you write
it to do.

```lua
function render()
  return ui.column {
    ui.label { text = "Hello!", style = "title" },
    ui.button { id = "wave", text = "Wave back" },
  }
end

function on_event(e)
  if e.id == "wave" then wm.log("👋") end
end
```

That is a complete plugin, minus its `plugin.json`.

## Start here

- **[Getting started](/plugins/getting-started/)** — build a working cipher tool from an
  empty folder. Assumes you have never written Lua.
- **[API reference](/plugins/api-reference/)** — the manifest, every widget, every
  event, every `wm.*` function, and every limit.
- **[Permissions](/plugins/permissions/)** — the one thing a plugin can ask for, and the
  many it cannot.
- **[Security](/plugins/security/)** — what the sandbox does, what it deliberately does
  not offer, and where its limits actually are.

## What a plugin can do

Compute things, and draw its own panel. That is genuinely the whole list.

## What a plugin cannot do

- **See what you type.** There is no key event API. A plugin cannot observe,
  change or delay a single keystroke going to your app.
- **Read the text you are writing in.** No API returns the contents of the field.
- **Read your clipboard.** No API.
- **Use the internet.** No HTTP, no sockets, no URLs.
- **Reach Android.** No files, no reflection, no other apps, no device
  information, no permissions of its own.
- **Run in the background.** A plugin only runs while its panel is open.

These are not settings, and there is no version of a plugin that gets around
them — the sandbox has no function for any of it. See [SECURITY.md](/plugins/security/)
for how that is enforced and what the residual risks honestly are.

Text still gets into a plugin: you type into its box, or tap **Paste** on it.
Results come back out when you tap **Insert** under a result. Both directions
are a deliberate tap, on a control the keyboard drew.

## Trying the examples

The four demo plugins in the
[addon repository](https://github.com/wasi-master/wmkeyboard-addon-repository)
are meant to be read:

| Plugin | Shows |
|---|---|
| **Cipher Tool** | Tabs, inputs, buttons, insertable output. The getting-started build. |
| **UI Kitchen Sink** | Every widget and every event, with a live event log. |
| **Todo List** | `wm.storage` — the one capability a plugin can ask for. |
| **Text Tools** | The paste-in, transform, insert-out flow. |

Turn plugins on under **Settings → Tools → Plugins** (they are off until you ask
for them), then install from the Addons screen or open a `.wmplugin` file.
