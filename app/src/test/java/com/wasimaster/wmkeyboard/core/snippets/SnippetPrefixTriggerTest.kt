package com.wasimaster.wmkeyboard.core.snippets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Triggers that lead with punctuation, extra spellings of a trigger, and
 * carrying the trigger's capitals into what it expands to.
 *
 * The prefix path exists because the keyboard's composing buffer never holds
 * punctuation, so `:shrug` cannot go through the plain whole-word lookup. That
 * is the property most of these guard.
 */
class SnippetPrefixTriggerTest {

    private fun snip(
        trigger: String? = null,
        text: String = "expanded",
        id: Long = 1,
        aliases: List<String> = emptyList(),
        confirm: Boolean = false,
        propagateCase: Boolean = false,
        style: UppercaseStyle = UppercaseStyle.CAPITALIZE,
    ) = Snippet(
        id = id,
        label = "s$id",
        text = text,
        trigger = trigger,
        aliases = aliases,
        confirm = confirm,
        propagateCase = propagateCase,
        uppercaseStyle = style,
    )

    // ---- splitting ----

    @Test
    fun `a prefix trigger splits into its punctuation and its word`() {
        assertEquals(SnippetMatcher.Prefixed(":", "shrug"), SnippetMatcher.splitPrefix(":shrug"))
        assertEquals(SnippetMatcher.Prefixed(";", "ty"), SnippetMatcher.splitPrefix(";ty"))
        assertEquals(SnippetMatcher.Prefixed("//", "date"), SnippetMatcher.splitPrefix("//date"))
    }

    @Test
    fun `a plain word is not a prefix trigger`() {
        assertNull(SnippetMatcher.splitPrefix("omw"))
        assertNull(SnippetMatcher.splitPrefix("brb2"))
        assertNull(SnippetMatcher.splitPrefix("it's"))
    }

    @Test
    fun `a trigger with no word part is refused`() {
        // Nothing would ever be looked up, so it must not be stored as if it
        // worked. The Espanso importer reports these.
        assertNull(SnippetMatcher.splitPrefix("->"))
        assertNull(SnippetMatcher.splitPrefix("!!"))
    }

    @Test
    fun `punctuation anywhere but the front is refused`() {
        // The buffer would break the word apart before any lookup saw it.
        assertNull(SnippetMatcher.splitPrefix(":a-b"))
        assertNull(SnippetMatcher.splitPrefix(":x:"))
        assertNull(SnippetMatcher.splitPrefix("a.b"))
    }

    @Test
    fun `an over-long prefix is refused`() {
        assertNotNull(SnippetMatcher.splitPrefix(":".repeat(SnippetMatcher.MAX_PREFIX) + "x"))
        assertNull(SnippetMatcher.splitPrefix(":".repeat(SnippetMatcher.MAX_PREFIX + 1) + "x"))
    }

    // ---- the index ----

    @Test
    fun `a prefix trigger is not in the plain lookup`() {
        val index = SnippetIndex.of(listOf(snip(trigger = ":shrug")))
        assertNull(index.matchTrigger("shrug"))
        assertNull(index.matchTrigger(":shrug"))
        assertTrue(index.hasPrefixTriggers)
    }

    @Test
    fun `a prefix trigger matches when the punctuation is in front of the word`() {
        val index = SnippetIndex.of(listOf(snip(trigger = ":shrug", text = "shrugged")))
        val hit = index.matchPrefix("shrug", "hello :")
        assertNotNull(hit)
        assertEquals(":", hit!!.prefix)
        assertEquals("shrugged", hit.snippet.text)
    }

    @Test
    fun `a prefix trigger does not match without its punctuation`() {
        val index = SnippetIndex.of(listOf(snip(trigger = ":shrug")))
        assertNull(index.matchPrefix("shrug", "hello "))
        assertNull(index.matchPrefix("shrug", ""))
    }

    @Test
    fun `the longest prefix wins`() {
        val index = SnippetIndex.of(
            listOf(
                snip(trigger = ":x", text = "one", id = 1),
                snip(trigger = "::x", text = "two", id = 2),
            ),
        )
        assertEquals("two", index.matchPrefix("x", "a ::")!!.snippet.text)
        assertEquals("one", index.matchPrefix("x", "a :")!!.snippet.text)
    }

    @Test
    fun `matching is case-insensitive on the word part`() {
        val index = SnippetIndex.of(listOf(snip(trigger = ":Shrug")))
        assertNotNull(index.matchPrefix("SHRUG", "x :"))
        assertNotNull(index.matchPrefix("shrug", "x :"))
    }

    @Test
    fun `the free gate answers without reading anything`() {
        val index = SnippetIndex.of(listOf(snip(trigger = ":shrug")))
        assertTrue(index.prefixCandidates("shrug").isNotEmpty())
        assertTrue(index.prefixCandidates("something-else").isEmpty())
        // And a list with no prefix trigger costs nothing at all.
        val plain = SnippetIndex.of(listOf(snip(trigger = "omw")))
        assertFalse(plain.hasPrefixTriggers)
        assertTrue(plain.prefixCandidates("omw").isEmpty())
    }

    @Test
    fun `an asking trigger never answers for an expanding one`() {
        val index = SnippetIndex.of(listOf(snip(trigger = ":ask", confirm = true)))
        assertNull(index.matchPrefix("ask", "x :", confirm = false))
        assertNotNull(index.matchPrefix("ask", "x :", confirm = true))
        assertTrue(index.hasConfirmPrefixTriggers)
    }

    // ---- aliases ----

    @Test
    fun `every alias fires the same snippet`() {
        val index = SnippetIndex.of(
            listOf(snip(trigger = "hi", aliases = listOf("hello", ":hey"), text = "Hello there")),
        )
        assertEquals("Hello there", index.matchTrigger("hi")!!.text)
        assertEquals("Hello there", index.matchTrigger("hello")!!.text)
        // Including one that leads with punctuation.
        assertEquals("Hello there", index.matchPrefix("hey", "x :")!!.snippet.text)
    }

    @Test
    fun `spellings lists the trigger and its aliases`() {
        assertEquals(
            listOf("hi", "hello"),
            snip(trigger = "hi", aliases = listOf("hello")).spellings(),
        )
        assertEquals(emptyList<String>(), snip(trigger = null).spellings())
        assertEquals(listOf("hi"), snip(trigger = "hi", aliases = listOf(" ", "")).spellings())
    }

    @Test
    fun `a snippet with a trigger never reaches the pattern side`() {
        // The rule that already held for one trigger has to hold for aliases.
        val both = Snippet(
            id = 1,
            label = "s",
            text = "t",
            trigger = ":x",
            triggerPattern = "^anything$",
        )
        val index = SnippetIndex.of(listOf(both))
        assertFalse(index.hasPatterns)
        assertNotNull(index.matchPrefix("x", "a :"))
    }

    // ---- capitals ----

    @Test
    fun `capitals are carried only when the snippet asks`() {
        val off = snip(propagateCase = false)
        assertEquals(TriggerCasing.NONE, SnippetStore.casingFor(off, "OMW"))

        val on = snip(propagateCase = true)
        assertEquals(TriggerCasing.UPPER, SnippetStore.casingFor(on, "OMW"))
        assertEquals(TriggerCasing.CAPITALIZE, SnippetStore.casingFor(on, "Omw"))
        assertEquals(TriggerCasing.NONE, SnippetStore.casingFor(on, "omw"))
    }

    @Test
    fun `the style only decides what a leading capital means`() {
        val words = snip(propagateCase = true, style = UppercaseStyle.CAPITALIZE_WORDS)
        assertEquals(TriggerCasing.CAPITALIZE_WORDS, SnippetStore.casingFor(words, "Omw"))
        // An all-caps trigger always shouts back, whatever the style says.
        assertEquals(TriggerCasing.UPPER, SnippetStore.casingFor(words, "OMW"))
    }

    @Test
    fun `a punctuation prefix is ignored when reading the capitals`() {
        val on = snip(propagateCase = true)
        assertEquals(TriggerCasing.CAPITALIZE, SnippetStore.casingFor(on, ":Omw"))
        assertEquals(TriggerCasing.UPPER, SnippetStore.casingFor(on, ":OMW"))
    }

    @Test
    fun `a single letter is not shouting`() {
        val on = snip(propagateCase = true)
        // "A" is a capital, not capitals: it takes the style, not the shout.
        assertEquals(TriggerCasing.CAPITALIZE, SnippetStore.casingFor(on, "A"))
    }

    @Test
    fun `the casings do what they say`() {
        assertEquals("on my way", TriggerCasing.NONE.apply("on my way"))
        assertEquals("ON MY WAY", TriggerCasing.UPPER.apply("on my way"))
        assertEquals("On my way", TriggerCasing.CAPITALIZE.apply("on my way"))
        assertEquals("On My Way", TriggerCasing.CAPITALIZE_WORDS.apply("on my way"))
    }

    @Test
    fun `capitalizing leaves the rest of the text exactly as written`() {
        // A snippet that deliberately holds an acronym keeps it.
        assertEquals("Send the PDF", TriggerCasing.CAPITALIZE.apply("send the PDF"))
        assertEquals("Send The PDF", TriggerCasing.CAPITALIZE_WORDS.apply("send the PDF"))
    }

    @Test
    fun `capitalizing text that starts with punctuation finds the first letter`() {
        assertEquals("\"Hello\"", TriggerCasing.CAPITALIZE.apply("\"hello\""))
        assertEquals("  Hi there", TriggerCasing.CAPITALIZE.apply("  hi there"))
    }

    @Test
    fun `a case mapping that changes length does not lose a character`() {
        // "ß" uppercases to two characters, so an index into the builder is not
        // an index into the source.
        assertEquals("SStraße", TriggerCasing.CAPITALIZE.apply("ßtraße"))
    }
}
