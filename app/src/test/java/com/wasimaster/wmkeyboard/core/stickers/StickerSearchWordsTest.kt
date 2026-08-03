package com.wasimaster.wmkeyboard.core.stickers

import org.junit.Assert.assertEquals
import org.junit.Test

class StickerSearchWordsTest {

    private fun sticker(name: String, emojis: List<String>) = CustomSticker(
        id = "s1",
        fileName = "s1.webp",
        mime = "image/webp",
        name = name,
        emojis = emojis,
    )

    @Test
    fun `joins the name and the tags into one line`() {
        assertEquals("grumpy 😂 cat", StickerSearchWords.of(sticker("grumpy", listOf("😂", "cat"))))
    }

    @Test
    fun `skips blank fields`() {
        assertEquals("cat", StickerSearchWords.of(sticker("", listOf("cat", " "))))
        assertEquals("", StickerSearchWords.of(sticker("", emptyList())))
    }

    @Test
    fun `first word is the name and the rest are tags`() {
        assertEquals("grumpy" to listOf("😂", "cat"), StickerSearchWords.split("grumpy 😂 cat"))
    }

    @Test
    fun `round trip leaves an imported sticker alone`() {
        val imported = sticker("grumpy", listOf("😂", "cat"))
        val (name, tags) = StickerSearchWords.split(StickerSearchWords.of(imported))
        assertEquals(imported.name, name)
        assertEquals(imported.emojis, tags)
    }

    @Test
    fun `collapses stray whitespace`() {
        assertEquals("cat" to listOf("dog"), StickerSearchWords.split("  cat \t\n dog  "))
    }

    @Test
    fun `a blank field clears both fields`() {
        assertEquals("" to emptyList<String>(), StickerSearchWords.split("   "))
    }

    @Test
    fun `one word leaves no tags`() {
        assertEquals("cat" to emptyList<String>(), StickerSearchWords.split("cat"))
    }
}
