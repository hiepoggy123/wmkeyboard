package com.wasimaster.wmkeyboard.ime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A plugin's own text box is one of several places that take the keys away from
 * the user's field. Every one of them has to be handled the same way in a
 * scattered set of conditions — the delete keys, the forward-delete guards, the
 * numeric-pad override, and the decision to draw the key rows at all.
 *
 * Those lists are hand-maintained, and a plugin box added to some but not others
 * is exactly what shipped: the panel collapsed to make room for keys that were
 * never drawn, so a focused box had nothing to type into it, and a backspace
 * swipe word-deleted the app's own text behind the panel.
 *
 * There is no way to assert this from state alone — the conditions live inside
 * private methods and composables that need an Android runtime. So this reads
 * the sources and holds the lists together: wherever the clipboard search is
 * named as owning the keys, the plugin box must be named too.
 */
class PluginKeyOwnershipTest {

    private fun source(path: String): String {
        val file = File(path)
        assertTrue("source not found at ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    private val serviceSource: String by lazy {
        source("src/main/java/com/wasimaster/wmkeyboard/ime/WMKeyboardService.kt")
    }

    private val screenSource: String by lazy {
        source("src/main/java/com/wasimaster/wmkeyboard/ime/ui/KeyboardScreen.kt")
    }

    /**
     * The `if (...)` heads that list several states at once and name the
     * clipboard search among them. Single-clause guards are left alone: those
     * are the dispatch branches that route a keystroke to one particular
     * buffer, and a plugin box has its own branch right beside them.
     *
     * Parentheses are balanced by hand rather than by regex, or a match runs
     * off the end of the condition and swallows the body.
     */
    private fun ownerConditions(text: String): List<String> = buildList {
        var from = 0
        while (true) {
            val start = text.indexOf("if (", from)
            if (start < 0) break
            var depth = 0
            var i = start + 3
            while (i < text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) break
                }
                i++
            }
            if (i >= text.length) break
            val head = text.substring(start, i + 1)
            if (head.contains("clipboardSearchActive") && head.contains("||")) add(head)
            from = i + 1
        }
    }

    @Test
    fun `the service treats a plugin box as owning the keys wherever the clipboard search does`() {
        val conditions = ownerConditions(serviceSource)
        assertTrue("no keystroke-owner conditions found in the service", conditions.size >= 3)
        val missing = conditions.filterNot { it.contains("pluginTypingActive") }
        assertEquals(
            "service conditions that hand the keys to the clipboard search but not to a plugin box",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `the numeric pad steps aside for a plugin box`() {
        val body = screenSource
            .substringAfter("private fun numericPadActive(")
            .substringBefore("\n\n")
        assertTrue("numericPadActive not found", body.contains("clipboardSearchActive"))
        assertTrue(
            "a numeric field would give a plugin box a digits-only pad",
            body.contains("pluginTypingActive"),
        )
    }

    @Test
    fun `the key rows are drawn while a plugin box has the keys`() {
        assertTrue(
            "no KeyRows are drawn for pluginTypingActive, so a focused plugin box " +
                "would have nothing on screen to type into it",
            Regex("""if \(state\.pluginTypingActive\) \{\s*KeyRows\(""")
                .containsMatchIn(screenSource),
        )
    }
}
