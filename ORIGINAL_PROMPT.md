Here's a comprehensive prompt you can give to a coding LLM. It is intentionally opinionated and ambitious, encouraging the model to design a polished, modern keyboard rather than just another Gboard clone.
Prompt
You are an expert Android engineer, UX designer, accessibility specialist, systems architect, and keyboard/input-method (IME) developer.
Design and implement a modern Android keyboard application that prioritizes speed, intelligence, customization, privacy, and power-user workflows.
The goal is to build a keyboard that combines the best ideas from Gboard, SwiftKey, Fleksy, OpenBoard, Heliboard, Samsung Keyboard, and desktop productivity tools, while introducing features they don't have.
The keyboard should feel polished enough to be a flagship keyboard.
General Requirements

* Native Android app
* Modern Material 3 UI
* Kotlin
* Jetpack Compose for settings
* Android IME architecture
* Modular codebase
* MVVM architecture
* Repository pattern
* Offline-first whenever possible
* Smooth animations
* Extremely low latency
* Memory efficient
* Battery efficient

The keyboard should remain responsive even on lower-end devices.
Core Typing
Support

* gesture typing
* swipe typing
* multi-touch
* split keyboard
* one-handed mode
* floating keyboard
* resize keyboard
* adjustable key height
* adjustable spacing
* adjustable font
* adjustable key corner radius
* adjustable haptic intensity
* adjustable sound
* adjustable vibration duration

Support

* long press symbols
* custom long press mappings
* popup previews
* configurable key repeat
* configurable long press delay

Languages
Support unlimited downloadable languages.
Examples

* English
* Bengali
* Hindi
* Arabic
* Japanese
* Korean
* Chinese
* French
* German
* Spanish

Support

* multilingual typing
* automatic language detection
* mixed-language suggestions

Bengali Support
One of the strongest focuses should be Bengali.
Include

* Avro phonetic typing
* Probhat layout
* National layout
* Bijoy compatibility
* English → Bengali transliteration
* custom transliteration rules
* dictionary learning
* Bengali autocorrect
* Bengali next-word prediction
* mixed Bengali/English typing

Typing
ami valo asi
should automatically become
আমি ভালো আছি
without noticeable delay.
Prediction Engine
Implement a modern prediction engine.
Support

* next word prediction
* sentence completion
* typo correction
* grammar-aware suggestions
* capitalization prediction
* punctuation prediction

Learn from

* user typing history
* frequently used words
* frequently typed phrases
* contacts (optional)
* app-specific vocabulary

Predictions should become more personalized over time.
Semantic Emoji Search
One of the strongest features.
Instead of simple keyword matching, support semantic search.
Examples
Searching
happy
should find
😀 😄 😊 😁 🥳
Searching
party
should also find
🎉 🥳 🍾 🎂
Searching
cat
should find
🐱 😺 🐈
Searching
love
should include
❤️🥰😍😘💕
Searching
laugh
should include
🤣😂😹
Support

* synonyms
* related concepts
* fuzzy search
* spelling mistakes
* multilingual search

Searching
বিড়াল
should still find cat emojis.
Searching
হাসি
should find smile emojis.
Searching
fire
should also find
🔥💥☄️❤️‍🔥
Emoji Intelligence
Support

* recently used
* frequently used
* AI-ranked suggestions
* skin tones
* gender variations
* favorites
* custom collections
* emoji combinations
* emoji prediction while typing

Example
Typing
birthday
should immediately suggest
🎂🎉🥳🎁
Typing
coffee
should suggest
☕😊
Typing
Bangladesh
should suggest
🇧🇩
Persistent Emoji Toolbar
Implement a dedicated emoji toolbar.
Options

* always visible
* auto hide
* one-tap show/hide
* swipe to reveal
* floating button

User should never need multiple taps to access emojis.
Toolbar customization

* emoji
* GIF
* stickers
* clipboard
* symbols
* translator
* AI
* image search
* custom buttons

Everything should be reorderable.
GIF Support
Integrate

* GIF search
* trending GIFs
* favorites
* recently used
* categories

Support providers like

* Tenor
* Giphy

Caching should be intelligent.
Sticker Support
Support

* downloadable sticker packs
* animated stickers
* custom user sticker packs
* WhatsApp sticker import
* Telegram sticker import
* favorites
* sticker folders

Direct Google Image Search
One unique feature.
Allow users to search Google Images directly inside the keyboard.
Workflow
Search
cat meme
↓
Scrollable image grid
↓
Tap image
↓
Insert image into supported apps
Support

* safe search
* filters
* transparent PNGs
* image previews
* recently searched
* favorites

Optionally support reverse image search.
Clipboard Manager
Advanced clipboard.
Support

* pinned items
* images
* copied links
* copied files
* snippets
* folders
* search
* expiration
* synchronization (optional)

Text Snippets
Allow users to create shortcuts.
Example
addr
↓
Expands to full address.
Support variables

* current date
* current time
* clipboard
* app name

AI Assistant
Optional AI integration.
Support

* rewrite
* summarize
* translate
* improve writing
* fix grammar
* explain text
* continue writing

Should work with local models when available and optionally cloud providers via user-configured API keys.
Search Tools
Include

* Google Search
* Google Images
* Wikipedia
* Dictionary
* Translate

Everything should be available without leaving the keyboard.
Character Customization
Highly customizable.
Users should be able to use their own character sets in the keyboard
Examples
€ £ ¥ ₹ ₿
Greek
α β γ δ λ π Ω
Math
≤ ≥ ≠ ± × ÷ ∞
Programming
{} [] () <> == != => ->
Markdown
** ` ``` > -
Unicode
✓ ✔ ✗ ★ ☆
Arrows
← ↑ → ↓
Emoticons
¯\(ツ)/¯
(╯°□°）╯︵ ┻━┻
Custom Character Sets
Users can create unlimited sets.
Examples
Physics
Chemistry
Programming
Math
Currency
IPA
Bengali punctuation
Japanese symbols
Everything reorderable and accessable within the keyboard
Themes
Support

* Material You
* dynamic colors
* AMOLED
* custom backgrounds
* gradients
* animated themes
* transparency
* blur
* custom fonts
* custom key shapes

Privacy
Strong privacy focus.
Support

* offline typing
* offline prediction
* offline autocorrect
* no telemetry by default
* encrypted learning database
* export/import settings
* clear learned words

Accessibility
Support

* TalkBack
* large text
* high contrast
* dyslexia-friendly fonts
* colorblind themes
* adjustable animation speed

Settings
Create one of the most customizable settings screens ever made.
Every feature should be configurable.
Include
Typing
Appearance
Themes
Prediction
Languages
Emoji
GIF
Stickers
Toolbar
Clipboard
AI
Privacy
Advanced
Developer Options
Searchable settings.
Favorites.
Import/export.
Backup.
Profiles.
Performance
Keyboard startup should be nearly instant.
Target

* startup under 100 ms
* minimal memory
* smooth 120 Hz animations
* no dropped frames
* asynchronous loading
* background caching

Developer Features
Include

* plugin architecture
* extension API
* scripting support (optional)
* custom dictionaries
* custom emoji packs
* custom themes
* custom language packs

Nice-to-Have Features

* OCR from camera
* voice typing
* handwriting recognition
* number row
* calculator mode
* unit conversion
* currency conversion
* calendar picker
* QR code generator
* barcode scanner
* password generator
* secure keyboard mode
* incognito mode
* split clipboard
* markdown shortcuts
* coding mode
* terminal mode
* Vim-style cursor navigation
* swipe on spacebar to move cursor
* multi-item selection
* undo/redo
* macro recording
* programmable key actions
* context-aware toolbar that changes based on the active app (e.g., email apps show email snippets, browsers show URL tools, coding apps show programming symbols).

Deliverables
The generated project should include:

1. A complete Android Studio project.
2. A scalable, modular architecture with clean separation of concerns.
3. Well-documented Kotlin code.
4. Material 3 UI using Jetpack Compose for all configuration screens.
5. An efficient IME implementation with low input latency.
6. Unit, integration, and UI tests.
7. Documentation explaining the architecture, extension points, and implementation decisions.
8. A phased development roadmap (MVP → Beta → Stable), identifying which features should be built first and which are advanced enhancements.

The code should emphasize maintainability, extensibility, and performance, making it suitable for long-term open-source development and community contributions. Also commit often. And keep a TODO.md for tracking if needed.

/caveman
