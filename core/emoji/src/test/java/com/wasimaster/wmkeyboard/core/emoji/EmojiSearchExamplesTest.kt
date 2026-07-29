package com.wasimaster.wmkeyboard.core.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Picking settings-copy examples out of the user's own languages. */
class EmojiSearchExamplesTest {

    private val money = EmojiSearchExamples.money
    private val birthday = EmojiSearchExamples.birthday

    @Test
    fun `the user's own languages come first, in their own order`() {
        val picked = EmojiSearchExamples.pick(money, listOf("ja", "fr"), limit = 2)
        assertEquals(listOf("お金", "argent"), picked)
    }

    @Test
    fun `English is skipped, since the copy names it separately`() {
        val picked = EmojiSearchExamples.pick(money, listOf("en", "de"), limit = 1)
        assertEquals(listOf("Geld"), picked)
    }

    @Test
    fun `a language with no entry is passed over rather than blanking a slot`() {
        val picked = EmojiSearchExamples.pick(money, listOf("tlh", "ru"), limit = 1)
        assertEquals(listOf("деньги"), picked)
    }

    @Test
    fun `the list is topped up when the user's languages don't fill it`() {
        val picked = EmojiSearchExamples.pick(money, listOf("ko"), limit = 3)
        assertEquals(3, picked.size)
        assertEquals("돈", picked.first())
    }

    @Test
    fun `no language is offered twice`() {
        val picked = EmojiSearchExamples.pick(money, listOf("bn", "bn", "hi"), limit = 3)
        assertEquals(picked.size, picked.distinct().size)
    }

    @Test
    fun `a user with no listed language still gets examples`() {
        val picked = EmojiSearchExamples.pick(money, emptyList(), limit = 3)
        assertEquals(listOf("টাকা", "पैसा", "مال"), picked)
    }

    @Test
    fun `one always returns something`() {
        assertEquals("誕生日", EmojiSearchExamples.one(birthday, listOf("ja")))
        assertEquals("জন্মদিন", EmojiSearchExamples.one(birthday, emptyList()))
        assertEquals("জন্মদিন", EmojiSearchExamples.one(birthday, listOf("en")))
    }

    @Test
    fun `both example sets cover the same languages`() {
        assertEquals(
            money.map { it.languageId }.toSet(),
            birthday.map { it.languageId }.toSet(),
        )
    }

    @Test
    fun `no example is English or blank, and no language repeats`() {
        for (set in listOf(money, birthday)) {
            val ids = set.map { it.languageId }
            assertEquals(ids.size, ids.distinct().size)
            assertFalse("en" in ids)
            assertTrue(set.all { it.word.isNotBlank() })
        }
    }
}
