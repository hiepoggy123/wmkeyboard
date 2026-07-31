package com.wasimaster.wmkeyboard.core.settings

/**
 * The settings as they apply during direct boot — before the user has unlocked
 * the device for the first time since it booted.
 *
 * Everything switched off here is switched off because the thing behind it is
 * *unreadable*, not because it would be rude to show: the fonts, theme images
 * and word lists live in credential-encrypted storage, the contact and app
 * lists behind providers that are themselves locked. Turning them off in the
 * settings object rather than at each use site means every downstream reader —
 * the toolbar, the toolbox, the hardware-keyboard shortcuts, the tool tap
 * handler, the renderer — agrees on what exists without any of them having to
 * know about direct boot.
 *
 * This is a view, never persisted: it is applied on the way out of the
 * repository, and the moment the user unlocks, the unrestricted settings emit
 * again and everything comes back.
 */
fun KeyboardSettings.restrictedToDirectBoot(): KeyboardSettings {
    val tools = enabledTools.filter(::isDirectBootSafeTool)
    return copy(
        enabledTools = tools,
        toolbarTools = toolbarTools.filter(::isDirectBootSafeTool),
        toolboxOrder = toolboxOrder.filter(::isDirectBootSafeTool),
        // A custom theme's background image is an absolute path under filesDir.
        // Drop the path and the theme still renders — as its own colors, with
        // no image behind them — instead of the loader failing per frame.
        customThemes = customThemes.map {
            it.copy(backgroundImage = null, backgroundImageLandscape = null)
        },
        // Imported font files are in filesDir; the Google Fonts families come
        // from a GMS content provider that is itself locked. Both fall back to
        // the system face rather than to no glyphs at all.
        keyFontId = "default",
        scriptFontIds = emptyMap(),
        emojiFont = EmojiFontChoice.SYSTEM,
        // An installed key sound is a file under filesDir too. Unlike the fonts
        // there is no per-glyph fallback to hide it — the style would simply
        // make no sound — so it drops back to the system click.
        keySoundStyle = if (keySoundStyle == KeySoundStyle.CUSTOM) {
            KeySoundStyle.CLICK
        } else {
            keySoundStyle
        },
        // Contacts, contact e-mails and the installed-app list all come from
        // providers that need the user's credential.
        contactSuggestions = false,
        contactEmailSuggestions = false,
        appNameSuggestions = false,
        // Android's shared personal dictionary is credential-encrypted too, and
        // nothing typed on a lock screen should be learned anywhere regardless.
        addWordsToSystemDictionary = false,
        // Clipboard history is not loaded while locked, so the paste chip would
        // only ever offer something copied inside this same locked session; the
        // screenshot watcher reads MediaStore, which is locked with everything
        // else.
        clipboard = clipboard.copy(
            // Nothing copied on a lock screen is recorded: the store has no
            // file to write to, and the images would want one under filesDir.
            history = false,
            suggestRecent = false,
            userScreenshots = false,
        ),
        // Handwriting recognizes through an ML Kit model downloaded into
        // filesDir. Without it the swipe would only ever reach the "download
        // the model" prompt, so the gesture goes back to typing words.
        letterSwipeAction = if (letterSwipeAction == LetterSwipeAction.HANDWRITE) {
            LetterSwipeAction.TYPE_WORDS
        } else {
            letterSwipeAction
        },
        // Offline dictation loads its weights from filesDir; the OS recognizer
        // does not, so dictation started from a key still works.
        whisper = whisper.copy(engine = "system"),
    )
}
