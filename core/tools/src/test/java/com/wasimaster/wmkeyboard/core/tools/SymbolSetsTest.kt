package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The symbol set's press-and-hold popups (issue #83): how they are stored, how
 * a set stored before they existed reads, and what a write keeps of them.
 */
class SymbolSetsTest {

    private val set = SymbolSet(
        id = "custom_1",
        name = "Mine",
        chars = listOf("@", "->", "\t"),
        popups = mapOf("@" to listOf("@gmail.com", "@outlook.com"), "->" to listOf("=>", "→")),
    )

    @Test
    fun `popups survive a round trip`() {
        val decoded = SymbolSetCodec.decodeList(SymbolSetCodec.encodeList(listOf(set)))
        assertEquals(listOf(set), decoded)
    }

    @Test
    fun `a set stored without popups reads with none`() {
        val legacy = """[{"id":"custom_1","name":"Mine","chars":["@","->"]}]"""
        val decoded = SymbolSetCodec.decodeList(legacy).single()
        assertEquals(listOf("@", "->"), decoded.chars)
        assertTrue(decoded.popups.isEmpty())
        assertTrue(decoded.popupFor("@").isEmpty())
    }

    @Test
    fun `popupFor reads the entry's own popup and nothing for the rest`() {
        assertEquals(listOf("@gmail.com", "@outlook.com"), set.popupFor("@"))
        assertEquals(emptyList<String>(), set.popupFor("\t"))
        assertEquals(emptyList<String>(), set.popupFor("missing"))
    }

    @Test
    fun `a popup for an entry the set no longer has is dropped`() {
        val clean = sanitizeSymbolPopups(listOf("@"), set.popups)
        assertEquals(setOf("@"), clean.keys)
    }

    @Test
    fun `the entry itself, blanks and repeats leave the popup`() {
        val clean = sanitizeSymbolPopups(
            listOf("a"),
            mapOf("a" to listOf("a", "", "á", "à", "á")),
        )
        assertEquals(mapOf("a" to listOf("á", "à")), clean)
    }

    @Test
    fun `a popup with nothing left in it is dropped`() {
        val clean = sanitizeSymbolPopups(listOf("a"), mapOf("a" to listOf("a", "")))
        assertTrue(clean.isEmpty())
    }

    @Test
    fun `the order the user typed is the order kept`() {
        val clean = sanitizeSymbolPopups(listOf("x", "y"), mapOf("y" to listOf("2", "1"), "x" to listOf("b", "a")))
        assertEquals(listOf("y", "x"), clean.keys.toList())
        assertEquals(listOf("2", "1"), clean["y"])
    }

    @Test
    fun `built-in sets carry no popups`() {
        for (builtIn in BuiltInSymbolSets.sets) assertTrue(builtIn.id, builtIn.popups.isEmpty())
    }
}
