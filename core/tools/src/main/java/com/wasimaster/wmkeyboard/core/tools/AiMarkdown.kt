package com.wasimaster.wmkeyboard.core.tools

/**
 * Detects and removes markdown syntax from AI output. Models answer in
 * markdown whether or not the prompt asks for it, and a text field almost
 * never renders it — so a result with markers in it gets a "plain text"
 * toggle on the panel, on by default.
 *
 * Detection is deliberately conservative: only markers that are unambiguous
 * in ordinary prose count, so a lone asterisk or an underscore inside a
 * file_name never trips the toggle into existence.
 */
object AiMarkdown {

    private val HEADING = Regex("""^ {0,3}#{1,6} +\S""", RegexOption.MULTILINE)
    private val BULLET = Regex("""^ {0,3}[-*+] +\S""", RegexOption.MULTILINE)
    private val QUOTE = Regex("""^ {0,3}> +""", RegexOption.MULTILINE)
    private val FENCE = Regex("""^ {0,3}(```|~~~)""", RegexOption.MULTILINE)
    // Bold/italic require non-space just inside the markers, so "2 * 3 * 4"
    // and "a_b_c" stay plain text.
    private val BOLD = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""", RegexOption.DOT_MATCHES_ALL)
    private val ITALIC = Regex("""(?<![\w*_])([*_])(?=\S)([^*_\n]+?)(?<=\S)\1(?![\w*_])""")
    private val CODE = Regex("""`([^`\n]+)`""")
    private val LINK = Regex("""\[([^\]\n]*)]\((\S*?)\)""")

    private val HEADING_PREFIX = Regex("""^ {0,3}#{1,6} +""")
    private val HEADING_SUFFIX = Regex(""" +#+ *$""")
    private val QUOTE_PREFIX = Regex("""^ {0,3}(> ?)+""")
    private val BULLET_PREFIX = Regex("""^( *)[-*+] +""")
    private val FENCE_LINE = Regex("""^ {0,3}(```|~~~).*$""")
    private val RULE_LINE = Regex("""^ {0,3}([-*_])( *\1){2,} *$""")

    /** True when [text] contains syntax worth stripping. */
    fun hasMarkdown(text: String): Boolean =
        HEADING.containsMatchIn(text) ||
            BULLET.containsMatchIn(text) ||
            QUOTE.containsMatchIn(text) ||
            FENCE.containsMatchIn(text) ||
            BOLD.containsMatchIn(text) ||
            ITALIC.containsMatchIn(text) ||
            CODE.containsMatchIn(text) ||
            LINK.containsMatchIn(text)

    /**
     * Markdown markers removed, content kept. Fenced code keeps its lines but
     * loses the fences, bullets become "• ", and a link becomes its text with
     * the URL in parentheses (dropping the URL entirely would lose the only
     * part that can't be retyped).
     */
    fun strip(text: String): String {
        val out = StringBuilder()
        var inFence = false
        for (line in text.lines()) {
            if (FENCE_LINE.matches(line)) {
                inFence = !inFence
                continue
            }
            if (inFence) {
                out.appendLine(line)
                continue
            }
            if (RULE_LINE.matches(line)) continue
            // A trailing "###" is only a marker on a line that opened with one.
            val heading = HEADING_PREFIX.containsMatchIn(line)
            var l = line.replace(HEADING_PREFIX, "")
            if (heading) l = l.replace(HEADING_SUFFIX, "")
            l = l.replace(QUOTE_PREFIX, "")
            l = BULLET_PREFIX.replace(l) { m -> m.groupValues[1] + "• " }
            out.appendLine(stripInline(l))
        }
        return out.toString().trim()
    }

    private fun stripInline(line: String): String {
        var l = LINK.replace(line) { m ->
            val (label, url) = m.destructured
            when {
                label.isBlank() -> url
                url.isBlank() || url == label -> label
                else -> "$label ($url)"
            }
        }
        l = BOLD.replace(l) { it.groupValues[2] }
        l = ITALIC.replace(l) { it.groupValues[2] }
        l = CODE.replace(l) { it.groupValues[1] }
        return l
    }
}
