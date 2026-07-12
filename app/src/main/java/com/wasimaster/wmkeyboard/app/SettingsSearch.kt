package com.wasimaster.wmkeyboard.app

import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.settings.ToolbarTool
import com.wasimaster.wmkeyboard.core.settings.isSupportedTool

/**
 * One searchable setting: what it is called, where it lives, and the route
 * that opens the screen holding it.
 *
 * The index below is hand-maintained. It is generated from the row titles in
 * the settings screens, so a renamed or new setting has to be mirrored here —
 * [SettingsSearchIndexTest] fails when a title in the index no longer exists
 * in any screen, which is the guard against silent drift.
 */
internal data class SettingsSearchEntry(
    val title: String,
    val subtitle: String,
    /** Breadcrumb shown under the result, e.g. "Tools › Camera". */
    val screen: String,
    val route: String,
)

/**
 * Builds an entry. [tool] is a shorthand for the tool-detail screens, whose
 * breadcrumb and route both derive from the tool itself, so a renamed tool
 * never desynchronises from its entries.
 */
private fun entry(
    title: String,
    subtitle: String,
    screen: String = "",
    route: String = "",
    tool: ToolbarTool? = null,
): SettingsSearchEntry = if (tool != null) {
    SettingsSearchEntry(title, subtitle, "Tools › ${toolTitle(tool)}", "tool/${tool.name}")
} else {
    SettingsSearchEntry(title, subtitle, screen, route)
}

/** Every individual setting row, in screen order. */
private val SettingRows: List<SettingsSearchEntry> = listOfNotNull(
    entry("Autocorrect", "Fix typos automatically when you press space", "Typing", "typing"),
    entry("Autocorrect confidence", "How sure a correction must be before it is applied", "Typing", "typing"),
    entry("Undo autocorrect with backspace", "Backspace right after a correction restores what you typed", "Typing", "typing"),
    entry("Skip all-caps words", "Autocorrect leaves words typed in capitals alone", "Typing", "typing"),
    entry("Fix missing apostrophes", "arent → aren't, im → I'm, dont → don't", "Typing", "typing"),
    entry("Auto-capitalize", "Capitalize the first letter of sentences", "Typing", "typing"),
    entry("Double-space period", "Double-tapping space inserts “. ”", "Typing", "typing"),
    entry("Double-space tab", "Double-tapping space inserts a tab", "Typing", "typing"),
    entry("Wrap selection with brackets", "Typing ( [ { < \" ' or ` around selected text wraps it", "Typing", "typing"),
    entry("Shift re-cases selection", "Shift with text selected cycles lowercase, Title, UPPERCASE", "Typing", "typing"),
    entry("Suggestions", "Show word predictions above the keyboard", "Typing", "typing"),
    entry("Suggestions bar always visible", "Keep the suggestion strip up even before you type", "Typing", "typing"),
    entry("Best suggestion in the middle", "Show the top candidate in the center slot", "Typing", "typing"),
    entry("Suggest contact names", "Complete names from your contacts as you type", "Typing", "typing"),
    entry("Suggest contact emails", "Complete a contact's email as you type the start of it", "Typing", "typing"),
    entry("Contact emails in email fields too", "Show contact emails even where the app hides suggestions", "Typing", "typing"),
    entry("Suggest app names", "Complete the names of installed apps as you type", "Typing", "typing"),
    entry("Inline emoji search", "Type \":\" then a word to find emoji — :smi → 😄", "Typing", "typing"),
    entry("Password manager suggestions", "Show saved logins from your autofill service in the strip", "Typing", "typing"),
    entry("Personal dictionary", "Words the keyboard has learned — review, remove, add your own", "Typing", "typing"),
    entry("Custom dictionaries", "Import your own word lists, per language", "Typing", "typing"),
    entry("Suggestion blacklist", "Words to never suggest or autocorrect to", "Typing", "typing"),
    entry("Smart chips", "Answer sums, conversions and tool keywords in the strip", "Typing", "typing"),
    entry("Calculate as you type", "\"12*4\" offers 48", "Typing", "typing"),
    entry("Convert currencies", "", "Typing", "typing"),
    entry("Convert units", "\"1 ft\" offers the same length in metres", "Typing", "typing"),
    entry("Tool keywords", "Typing \"wiki\" offers to open Wikipedia", "Typing", "typing"),
    entry("Gesture typing", "Swipe across letters to type a word", "Typing", "typing"),
    // Full builds only — the handwriting recognizer is an ML Kit feature.
    if (BuildConfig.ENABLE_ML_KIT_HANDWRITING) {
        entry("Handwrite with swipes", "Draw letters over the keys instead of gliding", "Typing", "typing")
    } else {
        null
    },
    entry("Glide across spacebar", "Swipe over space to keep gliding the next word", "Typing", "typing"),
    entry("Swipe start distance", "How far to move before a glide begins", "Typing", "typing"),
    entry("Trail width", "Thickness of the glide trail", "Typing", "typing"),
    entry("Trail length", "How long the trail lingers behind your finger", "Typing", "typing"),
    entry("Trail opacity", "How solid the glide trail looks", "Typing", "typing"),
    entry("Arrows on spacebar", "Hint that a swipe switches language", "Typing", "typing"),
    entry("Spacebar text", "Blank = current language name. %s inserts it, e.g. \"— %s —\".", "Typing", "typing"),
    entry("Swipe to delete words", "Drag sideways on backspace to delete whole words", "Typing", "typing"),
    entry("Volume cursor control", "Volume up and down move the text cursor", "Typing", "typing"),
    entry("Release while audio plays", "Keep volume control when something is playing", "Typing", "typing"),
    entry("Key press haptics", "Vibrate on every key press", "Key press", "keypress"),
    entry("Haptic strength", "Vibration length per key press", "Key press", "keypress"),
    entry("Haptic intensity", "Vibration amplitude per key press", "Key press", "keypress"),
    entry("Long-press haptics", "Vibrate when a long press registers", "Key press", "keypress"),
    entry("Long-press release haptics", "Vibrate on release after a long press", "Key press", "keypress"),
    entry("Vibrate on space", "Buzz when you press the space bar", "Key press", "keypress"),
    entry("Vibrate on delete swipe", "Buzz on each word a backspace swipe removes", "Key press", "keypress"),
    entry("Vibrate on key repeat", "Buzz on every auto-repeat while a key is held", "Key press", "keypress"),
    entry("Mute haptics in Do Not Disturb", "Stop keyboard vibration while Do Not Disturb is on", "Key press", "keypress"),
    entry("Key popup", "Show a character bubble above the pressed key", "Key press", "keypress"),
    entry("Minimum popup duration", "How long the bubble stays up even on a fast tap", "Key press", "keypress"),
    entry("Popup on key", "Grow the bubble upward from the pressed key itself", "Key press", "keypress"),
    entry("Popup font size", "Scale of the key preview bubble and long-press alternates", "Key press", "keypress"),
    entry("Popup height", "Height of the key preview bubble", "Key press", "keypress"),
    entry("Long-press delay", "Hold time before alternate characters appear", "Key press", "keypress"),
    entry("Key repeat interval", "Speed of repeated delete while held", "Key press", "keypress"),
    entry("Long-press hints", "Show each key's long-press character in its corner", "Key press", "keypress"),
    entry("Ctrl shortcuts as raw key events", "For terminals; off means Ctrl+A/C/V/X use the clipboard", "Key press", "keypress"),
    entry("Hold A to select all", "Long-pressing A selects all text", "Key press", "keypress"),
    entry("Hold C to copy", "Copies the selection, or everything if nothing is selected", "Key press", "keypress"),
    entry("Hold X to cut", "Cuts the selection, or everything if nothing is selected", "Key press", "keypress"),
    entry("Hold V to paste", "Long-pressing V pastes the clipboard", "Key press", "keypress"),
    entry("Keyboard themes", "Light/dark/AMOLED, colors, background images, import/export", "Appearance", "appearance"),
    entry("Auto theme", "Pick a light and a dark theme that switch with the system setting", "Appearance › Themes", "themes"),
    entry("Landscape background image", "A separate theme background for landscape, editable per theme", "Appearance › Themes", "themes"),
    entry("Keyboard font", "Google Fonts, or import your own font file", "Appearance", "appearance"),
    entry("Key corner radius", "Roundness of the key corners", "Appearance", "appearance"),
    entry("Key label font size", "Scale of the labels printed on the keys", "Appearance", "appearance"),
    entry("Show the toolbar", "The strip above the keys that carries suggestions and tools", "Appearance", "appearance"),
    entry("Swipe down to hide", "A downward flick on the toolbar dismisses the keyboard", "Appearance", "appearance"),
    entry("Only toolbar with hardware keyboard", "When a physical keyboard is attached, show just the toolbar", "Appearance", "appearance"),
    entry("Reverse order for RTL languages", "Mirror the tool order when typing a right-to-left script", "Appearance", "appearance"),
    entry("Spread tools across the bar", "Toolbar tools split the available width evenly", "Appearance", "appearance"),
    entry("Toolbar height", "Height of the top toolbar / suggestion strip", "Appearance", "appearance"),
    entry("Scroll the toolbar", "Swipe the tools sideways instead of shrinking them to fit", "Appearance", "appearance"),
    entry("Tool labels", "Show each tool's name under its icon on the toolbar", "Appearance", "appearance"),
    entry("Label text size", "Font size of the toolbar tool labels", "Appearance", "appearance"),
    entry("Reset pinned tools", "Restore the default toolbar tools", "Appearance", "appearance"),
    entry("Tool circle radius", "Roundness of the circle behind each toolbar tool", "Appearance", "appearance"),
    entry("Toolbox grid size", "Tools per row in the toolbox grid", "Appearance", "appearance"),
    entry("Number row", "Show a dedicated digit row above the letters", "Layout & size", "layout"),
    entry("Number row height", "Height of the digit row, independent of the letter keys", "Layout & size", "layout"),
    entry("Key height", "Height of each key row — sets the overall input height", "Layout & size", "layout"),
    entry("Key spacing", "Gap between the keys", "Layout & size", "layout"),
    entry("Keyboard scale", "Shrink or grow the whole keyboard per screen (folded/unfolded)", "Layout & size", "layout"),
    entry("Bottom padding", "Extra space below the keys, above the navigation bar", "Layout & size", "layout"),
    entry("Keyboard width", "Shrink the keyboard horizontally", "Layout & size", "layout"),
    entry("Keyboard position", "Where the narrowed keyboard sits: hugging the left edge, centered, or hugging the right edge.", "Layout & size", "layout"),
    entry("Following portrait", "", "Layout & size", "layout"),
    entry("Font size", "×%.2f", "Layout & size", "layout"),
    entry("Follow portrait again", "", "Layout & size", "layout"),
    entry("One-handed mode", "Shrink the keyboard toward one edge", "Layout & size", "layout"),
    entry("Split keyboard", "Divide the keys into left and right halves", "Layout & size", "layout"),
    entry("Split gap", "Width of the gap between the halves", "Layout & size", "layout"),
    entry("Floating keyboard", "Detach the keyboard into a movable panel", "Layout & size", "layout"),
    entry("Floating keyboard width", "Also adjustable by dragging the panel's corner grip", "Layout & size", "layout"),
    entry("Comma key opens emoji", "Replace the comma key with an emoji key", "Layout & size", "layout"),
    entry("Emoji key instead of 🌐", "Replace the language key with an emoji key", "Layout & size", "layout"),
    entry("Import a layout", "Open a .wmlayout.json file someone shared", "Key layouts", "keymaps"),
    entry("Conjunct-aware backspace", "Delete a whole যুক্তবর্ণ (like ক্ষ or স্ত্রী) as one unit", "Languages", "languages"),
    entry("Emoji button in toolbar", "One-tap emoji access from the top bar", "Emoji", "emoji"),
    entry("Full-screen emoji panel", "Hide the toolbar and move the category tabs up next to a back button", "Emoji", "emoji"),
    entry("Emoji suggestions", "Offer emojis while typing — birthday suggests 🎂 🎉 🥳", "Emoji", "emoji"),
    entry("Emoji suggestion tap", "What happens to the word you typed", "Emoji", "emoji"),
    entry("History tab", "What the first emoji-panel tab shows", "Emoji", "emoji"),
    entry("Clear recents button", "A button on the Recent tab to wipe the recents list", "Emoji", "emoji"),
    entry("Emoji descriptions", "Name the emoji at the top of its long-press popup", "Emoji", "emoji"),
    entry("Emoji row", "A dedicated row of your emojis, like Gboard", "Emoji", "emoji"),
    entry("Emoji row content", "Favourites always come first", "Emoji", "emoji"),
    entry("Emoji font", "How emojis look on the keyboard itself", "Emoji", "emoji"),
    entry("Default skin tone", "Skin tone for toned emoji in suggestions and search", "Emoji", "emoji"),
    entry("Override with last used", "Prefer the tone last picked over the default", "Emoji", "emoji"),
    entry("Return to keyboard after emoji", "Close the panel after inserting one emoji", "Emoji", "emoji"),
    entry("Hide emoji this phone can't display", "Skip emoji that show as a blank box", "Emoji", "emoji"),
    entry("Include API keys", "Translate, GIF, search and AI keys", "Backup & restore", "backup"),
    entry("Emoji button in toolbar", "Keep the emoji button visible next to suggestions", tool = ToolbarTool.EMOJI),
    entry("All emoji settings", "Suggestions, history tab, emoji row, skin tones & favourites", tool = ToolbarTool.EMOJI),
    entry("Clipboard history", "Save copied text for quick paste", tool = ToolbarTool.CLIPBOARD),
    entry("Suggest recent copy", "Show the last copied text as a chip on the suggestion strip", tool = ToolbarTool.CLIPBOARD),
    entry("Toast on copy", "Show a brief \"Copied\" pop-up when you copy or cut text", tool = ToolbarTool.CLIPBOARD),
    entry("Clipboard expiry", "Remove unpinned items after this long", tool = ToolbarTool.CLIPBOARD),
    entry("Bottom control row", "abc, space and backspace row at the bottom of the clipboard panel", tool = ToolbarTool.CLIPBOARD),
    entry("Pinned entries last", "List pinned clips at the end of the panel instead of the top", tool = ToolbarTool.CLIPBOARD),
    entry("Link previews", "Fetch the page title of copied links and show it in the panel. This contacts the linked site.", tool = ToolbarTool.CLIPBOARD),
    entry("Split gap", "Width of the gap between the halves", tool = ToolbarTool.SPLIT),
    entry("All layout & size settings", "Keyboard height, width, alignment and split", tool = ToolbarTool.SPLIT),
    entry("Floating keyboard width", "Also adjustable by dragging the panel's corner grip", tool = ToolbarTool.FLOATING),
    entry("All layout & size settings", "Keyboard height, width, alignment and floating mode", tool = ToolbarTool.FLOATING),
    entry("Auto-off with keyboard", "Turn the torch off when the keyboard is dismissed", tool = ToolbarTool.FLASHLIGHT),
    entry("Degree readout", "Show the numeric heading under the compass rose", tool = ToolbarTool.COMPASS),
    entry("Show qibla", "Mark the direction of the Kaaba on the compass", tool = ToolbarTool.COMPASS),
    entry("Angle readout", "Show pitch and roll in degrees under the bubble", tool = ToolbarTool.LEVEL),
    entry("Redo sends Ctrl+Y", "Instead of the default Ctrl+Shift+Z", tool = ToolbarTool.UNDO),
    entry("Redo sends Ctrl+Y", "Instead of the default Ctrl+Shift+Z", tool = ToolbarTool.REDO),
    entry("Southern hemisphere", "Mirror the moon the way it appears south of the equator", tool = ToolbarTool.MOON_PHASE),
    entry("Fahrenheit", "Show temperatures in °F instead of °C", tool = ToolbarTool.WEATHER),
    entry("Bengali calendar", "বঙ্গাব্দ (revised Bangladeshi calendar) alongside dates", tool = ToolbarTool.CALENDAR),
    entry("Hijri calendar", "Islamic (tabular) calendar alongside dates", tool = ToolbarTool.CALENDAR),
    entry("Hijri day adjustment", "Shift the computed Hijri date to match local moon sighting", tool = ToolbarTool.CALENDAR),
    entry("Start with the selfie camera", "Open the tool on the front camera instead of the back one", tool = ToolbarTool.CAMERA),
    entry("Mirror selfies", "Save front-camera photos the way the preview shows them", tool = ToolbarTool.CAMERA),
    entry("Save to gallery", "Also keep captures in Pictures/WM Keyboard", tool = ToolbarTool.CAMERA),
    entry("Shutter sound", "Play the camera click when a photo is taken", tool = ToolbarTool.CAMERA),
    entry("Haptics", "Vibrate on the shutter, controls and timer countdown", tool = ToolbarTool.CAMERA),
    entry("Look up the word at the cursor", "Opening the tool searches the selected or current word", tool = ToolbarTool.DICTIONARY),
    entry("Key repeat interval", "Pause between repeats while holding an arrow or backspace — lower is faster", tool = ToolbarTool.TEXT_EDIT),
    entry("Phone-style layout", "1 2 3 on the top row, like a dialer. Off puts 7 8 9 on top, like a calculator.", tool = ToolbarTool.NUMPAD),
    entry("Pause learning", "No words or emoji habits are learned from typing", tool = ToolbarTool.INCOGNITO),
    entry("Pause clipboard capture", "Copies don't join the clipboard tool's history", tool = ToolbarTool.INCOGNITO),
    entry("Follow private browsing", "Switch on by itself in incognito tabs and private fields", tool = ToolbarTool.INCOGNITO),
    // The tool screen's own "Autocorrect" toggle is not listed: ToolRows
    // already indexes the tool under that title and route. A row named after
    // the tool holding it is always a duplicate result, never a second one.
    entry("All typing settings", "Suggestions, autocorrect, capitalization and more", tool = ToolbarTool.AUTOCORRECT),
    entry("All key press settings", "Haptic style and strength, key preview, long-press", tool = ToolbarTool.SOUND_HAPTICS),
    entry("Stylus only", "Only an S Pen or other stylus draws; finger touches are ignored", tool = ToolbarTool.HANDWRITING),
    entry("Auto space", "Insert a space between consecutively written words", tool = ToolbarTool.HANDWRITING),
    entry("Recognition pause", "How long after the last stroke before the word is recognized", tool = ToolbarTool.HANDWRITING),
    entry("All theme settings", "Create, edit, import and export keyboard themes", tool = ToolbarTool.THEMES),
    entry("All layout & size settings", "Keyboard height, width, alignment and one-handed mode", tool = ToolbarTool.ONE_HANDED),
    entry("Cloud Translation API key (optional)", "Without a key, translation uses Google's free public endpoint", tool = ToolbarTool.TRANSLATE),
    entry("Full-screen picker", "Hide the toolbar and move search up next to a back button", tool = ToolbarTool.GIF),
    entry("Full-screen picker", "Hide the toolbar and move search up next to a back button", tool = ToolbarTool.STICKER),
    entry("Klipy API key", "Free from partner.klipy.com (Tenor's API was retired mid-2026)", tool = ToolbarTool.GIF),
    entry("Klipy API key", "Free from partner.klipy.com (Tenor's API was retired mid-2026)", tool = ToolbarTool.STICKER),
    entry("GIPHY API key", "Free from developers.giphy.com", tool = ToolbarTool.GIF),
    entry("GIPHY API key", "Free from developers.giphy.com", tool = ToolbarTool.STICKER),
    entry("Send stickers as", "What the sticker tool hands the chat app", tool = ToolbarTool.GIF),
    entry("Send stickers as", "What the sticker tool hands the chat app", tool = ToolbarTool.STICKER),
    entry("Send GIFs as", "Images by default — most chat apps animate them", tool = ToolbarTool.GIF),
    entry("Send GIFs as", "Images by default — most chat apps animate them", tool = ToolbarTool.STICKER),
    entry("Brave API key", "From api-dashboard.search.brave.com — monthly free credit", tool = ToolbarTool.WEB_SEARCH),
    entry("Brave API key", "From api-dashboard.search.brave.com — monthly free credit", tool = ToolbarTool.IMAGE_SEARCH),
    entry("SafeSearch", "Filter explicit results", tool = ToolbarTool.WEB_SEARCH),
    entry("SafeSearch", "Filter explicit results", tool = ToolbarTool.IMAGE_SEARCH),
    entry("Results per search", "Each search uses one API request either way", tool = ToolbarTool.WEB_SEARCH),
    entry("Results per search", "Each search uses one API request either way", tool = ToolbarTool.IMAGE_SEARCH),
    entry("Start with everything selected", "Deselect words to trim the capture. Off starts empty and words are picked one by one.", tool = ToolbarTool.OCR),
    entry("Insert automatically", "Type the code's text the moment one is spotted, no confirm tap", tool = ToolbarTool.QR_SCAN),
    entry("Vibrate on detection", "A short buzz when a code is spotted", tool = ToolbarTool.QR_SCAN),
    entry("Save to gallery", "Also keep scanned pages in Pictures/WM Keyboard", tool = ToolbarTool.DOC_SCAN),
    entry("Compact bar", "Dictate over the keys instead of a full panel", tool = ToolbarTool.VOICE),
    entry("Keep listening", "Start the next sentence automatically after each one commits", tool = ToolbarTool.VOICE),
    entry("Spoken punctuation", "Saying \"comma\", \"question mark\" or \"দাঁড়ি\" types the mark", tool = ToolbarTool.VOICE),
    entry("English dialect", "Spelling and style conventions to lint against", tool = ToolbarTool.GRAMMAR),
    entry("Re-check delay", "Pause after typing stops before issues refresh — lower feels snappier, higher churns less", tool = ToolbarTool.GRAMMAR),
    entry("Use Harper everywhere", "Set WM Keyboard as Android's spell checker", tool = ToolbarTool.GRAMMAR),
    entry("Wikipedia language", "Subdomain code: en, bn, de, fr, es …", tool = ToolbarTool.WIKIPEDIA),
    entry("Markdown links", "Insert links as [Title](url) instead of the bare URL", tool = ToolbarTool.WIKIPEDIA),
    entry("Calculate as you type", "Offer the result on the strip when you type a sum", tool = ToolbarTool.CALCULATOR),
    entry("Degrees", "Trig functions use degrees; off = radians", tool = ToolbarTool.CALCULATOR),
    entry("Result precision", "Maximum decimal places (also used by the unit converter)", tool = ToolbarTool.CALCULATOR),
    entry("Convert as you type", "Offer the conversion on the strip when you type a measurement", tool = ToolbarTool.UNIT_CONVERT),
    entry("Convert as you type", "", tool = ToolbarTool.CURRENCY),
    entry("Decimal places", "Rounding of the converted amount", tool = ToolbarTool.CURRENCY),
    entry("Refresh rates every", "How long fetched exchange rates stay fresh", tool = ToolbarTool.CURRENCY),
    entry("Image size", "Side length of the inserted PNG", tool = ToolbarTool.QR_GEN),
    entry("Send as", "QR codes go out as images by default", tool = ToolbarTool.QR_GEN),
    entry("Save to gallery", "Also keep generated codes in Pictures/WM Keyboard", tool = ToolbarTool.QR_GEN),
    entry("Length", "", tool = ToolbarTool.PASSWORD_GEN),
    entry("Uppercase letters", "A–Z", tool = ToolbarTool.PASSWORD_GEN),
    entry("Digits", "0–9", tool = ToolbarTool.PASSWORD_GEN),
    entry("Symbols", "", tool = ToolbarTool.PASSWORD_GEN),
    entry("Exclude look-alikes", "Skip Il1O0o5S8B and similar", tool = ToolbarTool.PASSWORD_GEN),
    entry("Words", "", tool = ToolbarTool.PASSWORD_GEN),
    entry("Separator", "Between words, e.g. - or . (blank = none)", tool = ToolbarTool.PASSWORD_GEN),
    entry("Capitalize words", "correct-Horse → Correct-Horse", tool = ToolbarTool.PASSWORD_GEN),
    entry("Include a digit", "Appended to a random word", tool = ToolbarTool.PASSWORD_GEN),
    entry("Edit keyboard modes", "Per-app and per-field setups, and what each one changes", tool = ToolbarTool.MODES),
    entry("Seconds", "", tool = ToolbarTool.TYPING_TEST),
    entry("Words", "", tool = ToolbarTool.TYPING_TEST),
    entry("Punctuation", "Capitals, commas and full stops in the prompt", tool = ToolbarTool.TYPING_TEST),
    entry("Numbers", "Mixes numerals into the word list", tool = ToolbarTool.TYPING_TEST),
    entry("Clear records", "Deletes every best score and the run history", tool = ToolbarTool.TYPING_TEST),
    entry("Anthropic API key", "From console.anthropic.com → API keys", tool = ToolbarTool.AI),
    entry("Model", "", tool = ToolbarTool.AI),
    entry("OpenAI API key", "From platform.openai.com → API keys", tool = ToolbarTool.AI),
    entry("Gemini API key", "Free tier from aistudio.google.com", tool = ToolbarTool.AI),
    entry("Server address", "e.g. http://192.168.0.10:11434 (your computer's LAN IP)", tool = ToolbarTool.AI),
    entry("Max response length", "Upper bound in tokens (≈ ¾ of a word each). Reasoning models automatically get 4× this, since their thinking is spent from the same budget.", tool = ToolbarTool.AI),
    entry("Translate action's target language", "e.g. English, Bengali, Japanese", tool = ToolbarTool.AI),
    entry("Show model reasoning", "Stream reasoning models' <think> passages instead of a progress bar", tool = ToolbarTool.AI),
    entry("Model picker on the panel", "Switch between configured providers and downloaded models right on the keyboard", tool = ToolbarTool.AI),
    entry("Translate into", "Source language is auto-detected; also changeable from the panel", tool = ToolbarTool.TRANSLATE),
    entry("Learn from typing", "Personalize suggestions on-device. Nothing ever leaves your phone.", "Privacy", "privacy"),
    entry("Add words to the system dictionary", "Share learned words with Android's personal dictionary", "Privacy", "privacy"),
    entry("Incognito mode", "Pause learning and clipboard capture", "Privacy", "privacy"),
    entry("Follow private browsing", "Turn incognito on by itself in private tabs and fields", "Privacy", "privacy"),
    entry("Symbol row", "A row of special characters and snippets above the keys", "Rows & bars", "rows"),
    entry("Drags edit the active mode", "Hold-and-drag on the keyboard saves into the mode that is on", "Keyboard modes", "modes"),
    entry("Name", "Shown in the Modes tool", "Edit mode", "modes"),
    entry("Emoji row", "While this mode is active", "Edit mode", "modes"),
    entry("Symbol row", "Inherit", "Edit mode", "modes"),
    entry("Custom pinned tools", "Change the toolbar's pinned tools while active", "Edit mode", "modes"),
    entry("Pinned tools behaviour", "Added after the tools you pinned yourself", "Edit mode", "modes"),
    entry("Custom toolbox order", "Float this mode's tools to the front of the toolbox panel", "Edit mode", "modes"),
    entry("Custom symbol sets", "The symbol row offers only this mode's sets while active", "Edit mode", "modes"),
    entry("Colour vision", "Adjust the palette for colour blindness", "Accessibility", "accessibility"),
    entry("High contrast keys", "Maximum-contrast labels on a plain black or white board", "Accessibility", "accessibility"),
    entry("Key outlines", "Draw a border around every key", "Accessibility", "accessibility"),
    entry("Bold key labels", "Heavier letters on the keys", "Accessibility", "accessibility"),
    entry("Readable font", "", "Accessibility", "accessibility"),
    entry("Text size", "Size of the labels on the keys", "Accessibility", "accessibility"),
    entry("Keyboard font", "Browse all fonts, or import your own", "Accessibility", "accessibility"),
    entry("Reduce motion", "Remove non-essential animation", "Accessibility", "accessibility"),
    entry("TalkBack support", "How keys are announced", "Accessibility", "accessibility"),
    entry("Ignore repeated presses", "Off — every press registers", "Accessibility", "accessibility"),
    entry("Long-press delay", "How long to hold a key before its alternates open", "Accessibility", "accessibility"),
    entry("Key size & spacing", "Taller keys and wider gaps are easier to hit accurately", "Accessibility", "accessibility"),
    entry("Haptics & sound", "Confirm each key press by feel or by ear", "Accessibility", "accessibility"),
    entry("Version", "", "About", "about"),
    entry("Licence", "MIT — © 2026 Wasi Master", "About", "about"),
    entry("Source code", "https://", "About", "about"),
    entry("Open-source licences", "Libraries and data bundled in this build", "About", "about"),
    entry("Dictionaries", "The English and Bengali word lists, bigrams and the loanword map are hand-curated for this project and covered by its licence", "About", "about"),
    entry("Colorful tool icons", "Tint each tool its own accent colour in Settings and the toolbox", "Tools", "tools"),
)

/**
 * The top-level destinations. These are searchable in their own right so that
 * "themes" finds the themes screen and not only the rows inside it.
 */
private val SectionRows: List<SettingsSearchEntry> = listOf(
    entry("Typing", "Autocorrect, suggestions, gestures", "Settings", "typing"),
    entry("Key press", "Haptics, key popup, long-press shortcuts", "Settings", "keypress"),
    entry("Languages", "English, বাংলা (Avro phonetic, প্রভাত, জাতীয়)", "Settings", "languages"),
    entry("Appearance", "Themes, fonts, toolbar style", "Settings", "appearance"),
    entry("Layout & size", "Key size, number row, one-handed, split & floating", "Settings", "layout"),
    entry("Key layouts", "Design your own key grid, Ctrl/Alt keys, import & export", "Settings", "keymaps"),
    entry("Rows & bars", "Symbol row, emoji row, row order & symbol sets", "Settings", "rows"),
    entry("Keyboard modes", "Per-app setups: email, browser, coding, passwords", "Settings", "modes"),
    entry("Emoji", "Suggestions, emoji row, emoji style, favourites", "Settings", "emoji"),
    entry("Tools", "Flashlight, compass, snippets, calendar & more", "Settings", "tools"),
    entry("Accessibility", "Contrast, colour vision, TalkBack, reduced motion", "Settings", "accessibility"),
    entry("Privacy", "On-device learning, incognito", "Settings", "privacy"),
    entry("Backup & restore", "Export your settings to a file, or restore them", "Settings", "backup"),
    entry("About", "Version, licence, open-source notices", "Settings", "about"),
    entry("Keyboard themes", "Light/dark/AMOLED, colours, background images", "Appearance", "themes"),
    entry("Keyboard font", "Google Fonts, or import your own font file", "Appearance", "fonts"),
    entry("Personal dictionary", "Words the keyboard has learned", "Typing", "dictionary"),
    entry("Custom dictionaries", "Import your own word lists, per language", "Typing", "customdictionaries"),
    entry("Suggestion blacklist", "Words to never suggest or autocorrect to", "Typing", "blacklist"),
    entry("Open-source licences", "Libraries and data bundled in this build", "About", "licenses"),
)

/**
 * The tools themselves, derived from the enum rather than listed, so a new
 * tool is searchable the moment it has a title — no index edit needed.
 */
private val ToolRows: List<SettingsSearchEntry>
    get() = ToolbarTool.entries.filter(::isSupportedTool).map { tool ->
        SettingsSearchEntry(toolTitle(tool), toolDescription(tool), "Tools", "tool/${tool.name}")
    }

/**
 * The whole searchable surface. Tool-detail rows for tools this build cannot
 * provide (the lite flavor drops the ML Kit ones) are filtered out, because
 * their screens are unreachable.
 */
internal val SettingsSearchIndex: List<SettingsSearchEntry> by lazy {
    val unsupported = ToolbarTool.entries.filterNot(::isSupportedTool)
        .map { "tool/${it.name}" }.toSet()
    (SectionRows + ToolRows + SettingRows).filterNot { it.route in unsupported }
}

/**
 * Scores [entry] against an already-lowercased [token]. Higher is better;
 * zero means the token is absent, which disqualifies the whole entry — every
 * token must land somewhere, so "emoji row" does not match every emoji row.
 */
private fun score(entry: SettingsSearchEntry, token: String): Int {
    val title = entry.title.lowercase()
    return when {
        title == token -> 100
        title.startsWith(token) -> 80
        title.split(' ', '-', '&', '(').any { it.startsWith(token) } -> 60
        title.contains(token) -> 40
        entry.screen.lowercase().contains(token) -> 25
        entry.subtitle.lowercase().split(' ', '-', ',', '.').any { it.startsWith(token) } -> 15
        entry.subtitle.lowercase().contains(token) -> 8
        else -> 0
    }
}

/**
 * Ranked matches for [query]. Ties break on title length so the plainest
 * setting with a matching name floats above the wordier ones.
 */
internal fun searchSettings(
    query: String,
    index: List<SettingsSearchEntry> = SettingsSearchIndex,
): List<SettingsSearchEntry> {
    val tokens = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    return index
        .map { candidate -> candidate to tokens.map { score(candidate, it) } }
        // Every token has to land somewhere: "emoji row" must not match a row
        // that only knows about emoji, nor every row on the emoji screen.
        .filter { (_, scores) -> scores.all { it > 0 } }
        .map { (candidate, scores) -> candidate to scores.sum() }
        .sortedWith(
            compareByDescending<Pair<SettingsSearchEntry, Int>> { it.second }
                .thenBy { it.first.title.length },
        )
        .map { it.first }
}
