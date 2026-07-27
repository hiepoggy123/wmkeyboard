# Addon repository docs

The format WM Keyboard uses to install themes, layouts, dictionaries, snippets, sticker packs,
icon packs, fonts and key sounds from a URL the user pastes in.

| File | What it is |
|---|---|
| [`REPO_FORMAT.md`](REPO_FORMAT.md) | The published spec — what a repository author writes. |
| [`wmkeyboard-repo.schema.json`](wmkeyboard-repo.schema.json) | JSON Schema (draft 2020-12) for `wmkeyboard-repo.json`. |
| [`CLIENT_DESIGN.md`](CLIENT_DESIGN.md) | How this app fetches, verifies and installs — the client side. |

## Where the canonical copies live

These three files are **mirrored** from the sample repository at
<https://github.com/wasi-master/wmkeyboard-addon-repository>, under its own `docs/addons/`.

That copy is canonical, because the schema's `$id` and the `"$schema"` key in every published
manifest already point at its `raw.githubusercontent.com` URL — repointing them would break
manifests in the wild. Edit there first, then re-copy here:

```bash
cp ~/Work/wmkeyboard-addon-repository/docs/addons/{REPO_FORMAT.md,CLIENT_DESIGN.md,wmkeyboard-repo.schema.json} docs/addons/
```

The mirror exists so the spec is readable alongside the code that implements it
(`core/addons/`), and so a change to the client and a change to the spec can land in one commit.

## Implementation

Client code lives in [`core/addons/`](../../app/src/main/java/com/wasimaster/wmkeyboard/core/addons).
The install dispatch table in `CLIENT_DESIGN.md` §5 maps each addon type to the importer that
handles it; most of those already existed for local file import.
