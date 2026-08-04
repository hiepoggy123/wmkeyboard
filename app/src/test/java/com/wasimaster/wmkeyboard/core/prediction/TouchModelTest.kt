package com.wasimaster.wmkeyboard.core.prediction

import com.wasimaster.wmkeyboard.core.transliteration.BengaliPhoneticIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchModelTest {

    /** Three keys in a row, one key-width apart. */
    private val model = KeyTouchModel(
        mapOf(
            'a' to TouchPoint(0f, 0f),
            's' to TouchPoint(1f, 0f),
            'd' to TouchPoint(2f, 0f),
        )
    )

    @Test
    fun centeredTapLikesItsOwnKeyBest() {
        val p = TouchPoint(1f, 0f)
        assertEquals('s', model.bestKey(p))
        assertTrue(model.logLikelihood(p, 's') > model.logLikelihood(p, 'a'))
        assertEquals(0.0, model.logLikelihood(p, 's'), 1e-9)
    }

    @Test
    fun likelihoodFallsMonotonicallyWithDistance() {
        val near = model.logLikelihood(TouchPoint(0.2f, 0f), 'a')
        val far = model.logLikelihood(TouchPoint(0.8f, 0f), 'a')
        assertTrue(near > far)
    }

    @Test
    fun unknownKeyIsImpossible() {
        assertTrue(model.logLikelihood(TouchPoint(0f, 0f), 'z') == Double.NEGATIVE_INFINITY)
        assertTrue(!model.knows('z'))
    }

    @Test
    fun tapOnNeighbourFlipsTheSuggestionRanking() {
        // "dats" is not a word; "data" is one adjacent slip away (s->a) and
        // "date" a far one (s->e), so without touch evidence the discrete
        // model must prefer "data". A tap dead-center on the 'e' key flips it.
        val qwertyish = KeyTouchModel(
            mapOf(
                'e' to TouchPoint(2f, 0f),
                't' to TouchPoint(4f, 0f),
                'a' to TouchPoint(0.25f, 1f),
                's' to TouchPoint(1.25f, 1f),
                'd' to TouchPoint(2.25f, 1f),
            )
        )
        val dictionary = Trie().apply {
            insert("data", 100)
            insert("date", 100)
        }
        val engine = SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), UserLexicon(null))
        engine.touchModel = qwertyish

        val without = engine.suggest("dats", previousWord = null)
        assertEquals("data", without.first())

        val tapOnE = listOf(null, null, null, TouchPoint(2f, 0f))
        val withTouch = engine.suggest("dats", previousWord = null, touch = tapOnE)
        assertEquals("date", withTouch.first())

        // Autocorrect sees the same evidence.
        assertEquals("date", engine.shouldAutocorrect("dats", touch = tapOnE))
    }

    @Test
    fun allNullFrameIsIdenticalToNoFrame() {
        val dictionary = Trie().apply {
            insert("hello", 70)
            insert("help", 60)
            insert("world", 50)
        }
        val engine = SuggestionEngine(dictionary, BengaliPhoneticIndex(emptyList()), UserLexicon(null))
        engine.touchModel = model
        for (typed in listOf("he", "helo", "wrold", "hel")) {
            val bare = engine.suggest(typed, previousWord = null)
            val nulls = engine.suggest(typed, previousWord = null, touch = List(typed.length) { null })
            assertEquals("'$typed'", bare, nulls)
            assertEquals(
                engine.shouldAutocorrect(typed),
                engine.shouldAutocorrect(typed, touch = List(typed.length) { null }),
            )
        }
    }
}
