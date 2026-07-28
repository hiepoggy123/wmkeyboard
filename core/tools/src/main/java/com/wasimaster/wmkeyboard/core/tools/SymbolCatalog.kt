package com.wasimaster.wmkeyboard.core.tools

/**
 * Curated special-character catalog for the symbols tool: the characters
 * people otherwise hunt down on the web (fractions, real math operators,
 * Greek letters, arrows, typographic marks…), grouped for a chip-per-category
 * picker. Everything is a plain Unicode string, committed like any key press.
 */
object SymbolCatalog {

    data class SymbolCategory(val name: String, val symbols: List<String>)

    val categories: List<SymbolCategory> = listOf(
        SymbolCategory(
            "Fractions",
            listOf(
                "½", "⅓", "⅔", "¼", "¾", "⅕", "⅖", "⅗", "⅘", "⅙", "⅚",
                "⅐", "⅛", "⅜", "⅝", "⅞", "⅑", "⅒", "⁄",
            ),
        ),
        SymbolCategory(
            "Math",
            listOf(
                "±", "∓", "×", "÷", "≠", "≈", "≡", "≤", "≥", "≪", "≫",
                "∞", "√", "∛", "∜", "∝", "∴", "∵", "∈", "∉", "⊂", "⊃",
                "⊆", "⊇", "∪", "∩", "∅", "∀", "∃", "∄", "¬", "∧", "∨",
                "⊕", "⊗", "∑", "∏", "∫", "∬", "∮", "∂", "∇", "Δ", "∠",
                "∟", "⊥", "∥", "∦", "≅", "≃", "≜", "≐", "⌀", "%", "‰",
                "‱", "°", "′", "″", "‴", "ℵ", "ℏ", "℮",
            ),
        ),
        SymbolCategory(
            "Greek",
            listOf(
                "α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ",
                "μ", "ν", "ξ", "ο", "π", "ρ", "σ", "ς", "τ", "υ", "φ",
                "χ", "ψ", "ω",
                "Α", "Β", "Γ", "Δ", "Ε", "Ζ", "Η", "Θ", "Ι", "Κ", "Λ",
                "Μ", "Ν", "Ξ", "Ο", "Π", "Ρ", "Σ", "Τ", "Υ", "Φ", "Χ",
                "Ψ", "Ω",
            ),
        ),
        SymbolCategory(
            "Arrows",
            listOf(
                "←", "→", "↑", "↓", "↔", "↕", "↖", "↗", "↘", "↙",
                "⇐", "⇒", "⇑", "⇓", "⇔", "⇕", "↩", "↪", "↰", "↱",
                "↺", "↻", "⟲", "⟳", "↶", "↷", "⇄", "⇅", "⇆", "⇋", "⇌",
                "➔", "➜", "➤", "⟵", "⟶", "⟷", "↦", "⇥", "⇤",
            ),
        ),
        SymbolCategory(
            "Currency",
            listOf(
                "$", "¢", "€", "£", "¥", "₹", "₽", "₩", "₺", "₴", "₦",
                "₱", "฿", "₫", "₪", "₨", "৳", "₡", "₭", "₮", "₲", "₵",
                "₸", "₼", "₿", "¤",
            ),
        ),
        SymbolCategory(
            "Superscript",
            listOf(
                "⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹",
                "⁺", "⁻", "⁼", "⁽", "⁾", "ⁿ", "ⁱ",
                "₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉",
                "₊", "₋", "₌", "₍", "₎", "ₐ", "ₑ", "ₓ",
            ),
        ),
        SymbolCategory(
            "Punctuation",
            listOf(
                "–", "—", "―", "…", "•", "·", "‣", "§", "¶", "†", "‡",
                "‘", "’", "“", "”", "‚", "„", "‹", "›", "«", "»",
                "¡", "¿", "‽", "⁂", "№", "℗", "©", "®", "™", "℠",
                " ", "​", "‍",
            ),
        ),
        SymbolCategory(
            "Shapes & stars",
            listOf(
                "★", "☆", "✦", "✧", "✪", "✯", "✶", "✳", "✴", "❋",
                "■", "□", "▪", "▫", "▲", "△", "▼", "▽", "◆", "◇",
                "●", "○", "◎", "◉", "◌", "◯", "▶", "◀", "▷", "◁",
                "♠", "♣", "♥", "♦", "♤", "♧", "♡", "♢",
                "✓", "✔", "✕", "✗", "✘", "☑", "☒", "☐",
            ),
        ),
        SymbolCategory(
            "Misc",
            listOf(
                "♩", "♪", "♫", "♬", "♭", "♮", "♯",
                "☀", "☁", "☂", "☃", "☄", "☾", "☽", "☼",
                "☎", "✆", "✉", "✎", "✏", "✂", "⌛", "⌚", "⌘", "⌥",
                "⎋", "⏎", "⌫", "⇧", "␣", "♻", "⚠", "⚡", "☮", "☯",
                "⚕", "⚖", "⚛", "♾", "☢", "☣", "⚓", "⚔", "⚙", "⚗",
                "♔", "♕", "♖", "♗", "♘", "♙", "♚", "♛", "♜", "♝", "♞", "♟",
            ),
        ),
    )

    /** Display label for symbols that render invisibly in a picker cell. */
    fun label(symbol: String): String = when (symbol) {
        " " -> "NBSP"
        "​" -> "ZWSP"
        "‍" -> "ZWJ"
        else -> symbol
    }
}
