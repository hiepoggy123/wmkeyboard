package com.wasimaster.wmkeyboard.core.snippets.espanso

/**
 * Translation between Espanso's date format and this app's.
 *
 * Espanso's `date` extension takes a chrono strftime string (`%d/%m/%Y`); a
 * snippet here takes a `java.text.SimpleDateFormat` pattern (`dd/MM/yyyy`).
 * Neither is a superset of the other, so both directions drop what they cannot
 * say and the caller reports it.
 *
 * The detail that bites is quoting. In a `SimpleDateFormat` pattern every
 * unescaped letter is a field, so the literal text between two specifiers has
 * to be wrapped in single quotes on the way in: `%H hours` becomes
 * `HH ' hours'`, not `HH  hours`, which would read the h, o, u, r and s as five
 * fields. Going the other way the quotes come off again.
 */
object EspansoDate {

    /**
     * strftime specifier to `SimpleDateFormat` pattern.
     *
     * `%s` is deliberately absent: epoch seconds are not a date pattern at all,
     * and [toPattern] turns them into the app's own `{timestamp}` token.
     */
    private val TO_PATTERN = mapOf(
        'Y' to "yyyy", 'y' to "yy", 'C' to "yy",
        'm' to "MM", 'B' to "MMMM", 'b' to "MMM", 'h' to "MMM",
        'd' to "dd", 'e' to "d", 'j' to "DDD",
        'A' to "EEEE", 'a' to "EEE", 'u' to "u", 'w' to "E",
        'H' to "HH", 'k' to "H", 'I' to "hh", 'l' to "h",
        'M' to "mm", 'S' to "ss", 'f' to "SSSSSSSSS", 'L' to "SSS",
        'p' to "a", 'P' to "a",
        'Z' to "zzz", 'z' to "Z", 'V' to "ww", 'U' to "ww", 'W' to "ww",
        // Compound specifiers, spelled out rather than recursed into.
        'F' to "yyyy-MM-dd", 'T' to "HH:mm:ss", 'X' to "HH:mm:ss",
        'D' to "MM/dd/yy", 'x' to "MM/dd/yy", 'R' to "HH:mm", 'r' to "hh:mm:ss a",
        'c' to "EEE MMM d HH:mm:ss yyyy",
    )

    /** The reverse of [TO_PATTERN], longest pattern first so `yyyy` beats `yy`. */
    private val TO_STRFTIME: List<Pair<String, String>> = listOf(
        "yyyy" to "%Y", "yy" to "%y",
        "MMMM" to "%B", "MMM" to "%b", "MM" to "%m", "M" to "%m",
        "dd" to "%d", "d" to "%e", "DDD" to "%j",
        "EEEE" to "%A", "EEE" to "%a", "EE" to "%a", "E" to "%a",
        "HH" to "%H", "H" to "%k", "hh" to "%I", "h" to "%l",
        "mm" to "%M", "m" to "%M", "ss" to "%S", "s" to "%S",
        "SSS" to "%L", "a" to "%p",
        "zzzz" to "%Z", "zzz" to "%Z", "zz" to "%Z", "z" to "%Z",
        "XXX" to "%z", "XX" to "%z", "X" to "%z", "ZZZZ" to "%z", "Z" to "%z",
        "ww" to "%V", "w" to "%V", "u" to "%u",
    )

    /** What a conversion could not carry across, for the caller's note list. */
    class Result(val value: String, val dropped: List<String>)

    /**
     * A strftime string as a `SimpleDateFormat` pattern.
     *
     * `%%` is a literal percent. An unknown specifier is dropped and named in
     * [Result.dropped]; the rest of the pattern still converts, because a date
     * missing its week number is far better than a snippet that refuses to
     * import.
     */
    fun toPattern(strftime: String): Result {
        val out = StringBuilder(strftime.length * 2)
        val literal = StringBuilder()
        val dropped = ArrayList<String>()

        fun flushLiteral() {
            if (literal.isEmpty()) return
            out.append(quote(literal.toString()))
            literal.setLength(0)
        }

        var i = 0
        while (i < strftime.length) {
            val c = strftime[i]
            if (c != '%') {
                literal.append(c)
                i++
                continue
            }
            // chrono allows padding modifiers between the % and the letter.
            var j = i + 1
            while (j < strftime.length && strftime[j] in "-_0^#") j++
            val spec = strftime.getOrNull(j)
            if (spec == null) {
                literal.append('%')
                i++
                continue
            }
            if (spec == '%') {
                literal.append('%')
                i = j + 1
                continue
            }
            val mapped = TO_PATTERN[spec]
            if (mapped == null) {
                dropped.add("%$spec")
            } else {
                flushLiteral()
                out.append(mapped)
            }
            i = j + 1
        }
        flushLiteral()
        return Result(out.toString(), dropped)
    }

    /**
     * A `SimpleDateFormat` pattern as a strftime string.
     *
     * Quoted runs come back out as literal text, `''` is a literal apostrophe,
     * and a field with no strftime equivalent is dropped and named.
     */
    fun toStrftime(pattern: String): Result {
        val out = StringBuilder(pattern.length)
        val dropped = ArrayList<String>()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                // Outside a quoted run, `''` is a literal apostrophe on its own.
                if (pattern.getOrNull(i + 1) == '\'') {
                    out.append('\'')
                    i += 2
                    continue
                }
                // Inside one it is also a literal apostrophe rather than the
                // end of the run, so the scan cannot stop at the first quote it
                // meets: "yyyy' o''clock'" is one run reading " o'clock".
                val literal = StringBuilder()
                var j = i + 1
                var open = true
                while (open && j < pattern.length) {
                    val inner = pattern[j]
                    when {
                        inner != '\'' -> {
                            literal.append(inner)
                            j++
                        }
                        pattern.getOrNull(j + 1) == '\'' -> {
                            literal.append('\'')
                            j += 2
                        }
                        else -> {
                            j++
                            open = false
                        }
                    }
                }
                out.append(escapePercent(literal.toString()))
                i = j
                continue
            }
            if (!c.isLetter()) {
                out.append(if (c == '%') "%%" else c.toString())
                i++
                continue
            }
            // A run of the same letter is one field, and the count matters:
            // "MM" and "MMMM" are different things.
            var end = i
            while (end < pattern.length && pattern[end] == c) end++
            val run = pattern.substring(i, end)
            val mapped = TO_STRFTIME.firstOrNull { it.first == run }
                ?: TO_STRFTIME.firstOrNull { it.first.isNotEmpty() && it.first[0] == c }
            if (mapped == null) dropped.add(run) else out.append(mapped.second)
            i = end
        }
        return Result(out.toString(), dropped)
    }

    /**
     * [text] as a literal run inside a `SimpleDateFormat` pattern.
     *
     * Only letters and apostrophes need the treatment; wrapping punctuation and
     * spaces as well would turn `dd/MM` into an unreadable thicket of quotes.
     */
    private fun quote(text: String): String {
        if (text.none { it.isLetter() || it == '\'' }) return text
        return "'" + text.replace("'", "''") + "'"
    }

    private fun escapePercent(text: String): String = text.replace("%", "%%")
}
