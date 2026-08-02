package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDiffTest {

    private fun rebuildSource(result: TextDiff.Result) =
        result.spans.filter { it.op != TextDiff.Op.ADD }.joinToString("") { it.text }

    private fun rebuildResult(result: TextDiff.Result) =
        result.spans.filter { it.op != TextDiff.Op.DELETE }.joinToString("") { it.text }

    private fun ops(result: TextDiff.Result) = result.spans.map { it.op }

    // ---- the invariant everything else rests on ----

    @Test
    fun `the spans always rebuild both texts exactly`() {
        val pairs = listOf(
            "hello world" to "hello there",
            "" to "written from nothing",
            "deleted entirely" to "",
            "same" to "same",
            "the cat sat" to "The cat sat",
            "hello world" to "hello, world",
            "a b c" to "a c",
            "one\n\ntwo" to "one\ntwo",
            "line one\nline two\n" to "line one\nline three\n",
            "  leading space" to "leading space",
            "trailing space  " to "trailing space",
            "tabs\tand\tspaces" to "tabs and spaces",
            "I like 🍎 a lot" to "I like 🍏 a lot",
            "family 👨‍👩‍👧‍👦 photo" to "family 👨‍👩‍👧 photo",
            "wave 👋🏽 hello" to "wave 👋🏻 hello",
            "আমি ডাকি" to "আমি ড়াকি",
            "বাংলা লেখা ভালো" to "বাংলা লেখা খুব ভালো",
            "我喜欢猫" to "我喜欢狗",
            "これはペンです" to "これは本です",
            "สวัสดีครับ" to "สวัสดีค่ะ",
            "مرحبا بالعالم" to "مرحبا بالجميع",
            "mixed 中文 and English" to "mixed 日文 and English",
            "surrogate 𝔣𝔞𝔫𝔠𝔶 text" to "surrogate 𝔠𝔬𝔬𝔩 text",
            "a" to "b",
            "\n\n\n" to "\n",
        )
        for ((source, result) in pairs) {
            val diff = TextDiff.diff(source, result)
            assertEquals("source rebuild for [$source] -> [$result]", source, rebuildSource(diff))
            assertEquals("result rebuild for [$source] -> [$result]", result, rebuildResult(diff))
        }
    }

    @Test
    fun `no span ever begins or ends on half a surrogate pair`() {
        // Splitting one would put an unpaired code unit into the panel, and from
        // there into the text field if the user pressed Replace.
        val pairs = listOf(
            "I like 🍎🍏🍐 a lot" to "I like 🍎🍐 a lot",
            "𝔞𝔟𝔠" to "𝔞𝔵𝔠",
            "👨‍👩‍👧‍👦" to "👨‍👩‍👧",
        )
        for ((source, result) in pairs) {
            for (span in TextDiff.diff(source, result).spans) {
                assertFalse(span.text, span.text.firstOrNull()?.isLowSurrogate() == true)
                assertFalse(span.text, span.text.lastOrNull()?.isHighSurrogate() == true)
            }
        }
    }

    @Test
    fun `a Bengali nukta never becomes a span of its own`() {
        // U+09BC on its own is a combining mark: shown alone it renders as a
        // stray dot, and committed alone it corrupts the word.
        val diff = TextDiff.diff("ডাক", "ড়াক")
        for (span in diff.spans) {
            assertFalse(span.text, span.text.startsWith("়"))
        }
        assertEquals("ডাক", rebuildSource(diff))
        assertEquals("ড়াক", rebuildResult(diff))
    }

    // ---- fast paths ----

    @Test
    fun `identical texts are one kept span`() {
        val diff = TextDiff.diff("no change here", "no change here")
        assertEquals(listOf(TextDiff.Op.KEEP), ops(diff))
        assertTrue(diff.identical)
        assertEquals(0, diff.added)
        assertEquals(0, diff.deleted)
    }

    @Test
    fun `an empty side is one span`() {
        assertEquals(listOf(TextDiff.Op.ADD), ops(TextDiff.diff("", "written")))
        assertEquals(listOf(TextDiff.Op.DELETE), ops(TextDiff.diff("deleted", "")))
        assertTrue(TextDiff.diff("", "").identical)
    }

    // ---- what the trim buys ----

    @Test
    fun `one changed word in a long sentence is one delete and one add`() {
        val source = "The quick brown fox jumps over the lazy dog every single morning"
        val result = "The quick brown cat jumps over the lazy dog every single morning"
        val diff = TextDiff.diff(source, result)
        assertEquals(
            listOf(TextDiff.Op.KEEP, TextDiff.Op.DELETE, TextDiff.Op.ADD, TextDiff.Op.KEEP),
            ops(diff),
        )
        assertEquals(1, diff.added)
        assertEquals(1, diff.deleted)
    }

    @Test
    fun `a change of capital letter stays one pair of tokens`() {
        val diff = TextDiff.diff("the cat sat", "The cat sat")
        assertEquals(1, diff.added)
        assertEquals(1, diff.deleted)
        // Both halves survive, which is the whole point: the old spelling is
        // the one thing the plain result cannot show.
        assertTrue(diff.spans.any { it.op == TextDiff.Op.DELETE && it.text.trim() == "the" })
        assertTrue(diff.spans.any { it.op == TextDiff.Op.ADD && it.text.trim() == "The" })
    }

    @Test
    fun `a change of punctuation stays one pair of tokens`() {
        val diff = TextDiff.diff("hello world", "hello, world")
        assertEquals(1, diff.added)
        assertEquals(1, diff.deleted)
    }

    // ---- whitespace ----

    @Test
    fun `deleting a word takes its space with it`() {
        val diff = TextDiff.diff("a b c", "a c")
        assertEquals("a c", rebuildResult(diff))
        assertTrue(diff.spans.any { it.op == TextDiff.Op.DELETE && it.text == "b " })
        // And nothing left behind: no double space anywhere in the rebuild.
        assertFalse(rebuildResult(diff).contains("  "))
    }

    @Test
    fun `a deleted paragraph break is its own visible span`() {
        val diff = TextDiff.diff("one\n\ntwo", "one\ntwo")
        val changed = diff.spans.filter { it.op != TextDiff.Op.KEEP }
        assertTrue(changed.isNotEmpty())
        assertTrue(changed.all { it.text.isNotEmpty() && it.text.all(Char::isWhitespace) })
    }

    // ---- granularity ----

    @Test
    fun `scripts without spaces are compared character by character`() {
        assertEquals(TextDiff.Granularity.GRAPHEME, TextDiff.diff("我喜欢猫", "我喜欢狗").granularity)
        assertEquals(
            TextDiff.Granularity.GRAPHEME,
            TextDiff.diff("これはペンです", "これは本です").granularity,
        )
        assertEquals(
            TextDiff.Granularity.GRAPHEME,
            TextDiff.diff("สวัสดีครับ", "สวัสดีค่ะ").granularity,
        )
    }

    @Test
    fun `one changed character in Chinese changes one character`() {
        val diff = TextDiff.diff("我喜欢猫", "我喜欢狗")
        assertEquals(1, diff.added)
        assertEquals(1, diff.deleted)
        assertTrue(diff.spans.any { it.op == TextDiff.Op.DELETE && it.text == "猫" })
        assertTrue(diff.spans.any { it.op == TextDiff.Op.ADD && it.text == "狗" })
    }

    @Test
    fun `ordinary prose is compared word by word`() {
        assertEquals(
            TextDiff.Granularity.WORD,
            TextDiff.diff("the quick brown fox", "the quick red fox").granularity,
        )
        // A stray Chinese word in an English sentence does not flip it.
        assertEquals(
            TextDiff.Granularity.WORD,
            TextDiff.diff(
                "the quick brown fox jumps over the lazy 狗 today",
                "the quick red fox jumps over the lazy 狗 today",
            ).granularity,
        )
    }

    // ---- ordering ----

    @Test
    fun `a replacement always reads old first, then new`() {
        val diff = TextDiff.diff("alpha beta gamma", "alpha delta gamma")
        val changed = ops(diff).filter { it != TextDiff.Op.KEEP }
        assertEquals(listOf(TextDiff.Op.DELETE, TextDiff.Op.ADD), changed)
    }

    @Test
    fun `neighbouring spans of the same kind are merged`() {
        val diff = TextDiff.diff("one two three four", "one nine ten four")
        // Two changed words in a row must be one delete span and one add span,
        // not four alternating ones.
        assertEquals(
            listOf(TextDiff.Op.KEEP, TextDiff.Op.DELETE, TextDiff.Op.ADD, TextDiff.Op.KEEP),
            ops(diff),
        )
    }

    // ---- ceilings ----

    @Test
    fun `a pair of very long texts is refused rather than compared`() {
        val source = "word ".repeat(6_000)
        val result = "other ".repeat(6_000)
        val diff = TextDiff.diff(source, result)
        assertTrue(diff.tooLong)
        assertEquals(TextDiff.Granularity.NONE, diff.granularity)
        assertTrue(diff.spans.isEmpty())
    }

    @Test
    fun `too many word edits step down to whole lines`() {
        // Eighty rewritten lines in the middle of a document that is otherwise
        // untouched. Word by word that is far more edits than the search is
        // allowed to trace; line by line it is 160, which it can.
        fun document(middle: (Int) -> String) = buildString {
            for (i in 1..120) appendLine("shared opening line number $i")
            for (i in 1..80) appendLine(middle(i))
            for (i in 1..120) appendLine("shared closing line number $i")
        }
        val source = document { "the first wording of paragraph $it here" }
        val result = document { "a wholly different sentence for section $it" }
        val diff = TextDiff.diff(source, result)
        assertFalse(diff.tooLong)
        assertEquals(TextDiff.Granularity.LINE, diff.granularity)
        assertEquals(source, rebuildSource(diff))
        assertEquals(result, rebuildResult(diff))
    }

    @Test
    fun `a realistic fix-grammar run stays word by word`() {
        val source = "i recieve alot of emails every day and i dont " +
            "have enough time to reply to all of them properly"
        val result = "I receive a lot of emails every day and I don't " +
            "have enough time to reply to all of them properly"
        val diff = TextDiff.diff(source, result)
        assertEquals(TextDiff.Granularity.WORD, diff.granularity)
        assertFalse(diff.tooLong)
        assertFalse(diff.identical)
        assertEquals(source, rebuildSource(diff))
        assertEquals(result, rebuildResult(diff))
        // The tail is untouched, so it must come back as one kept span.
        assertTrue(diff.spans.last().op == TextDiff.Op.KEEP)
    }
}
