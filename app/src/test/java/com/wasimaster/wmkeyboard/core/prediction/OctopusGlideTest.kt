package com.wasimaster.wmkeyboard.core.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mid-stroke the octopus (discussion #102) hangs each alternate off the key
 * where it leaves the decoder's leader — steer to the p and you get "help"
 * instead of "hello".
 *
 * This is the same assignment the idle board uses with the leader standing in
 * for the buffer, and it is exact rather than approximate: a candidate shares
 * the stroke drawn so far exactly as far as it shares the leader's spelling.
 */
class OctopusGlideTest {

    private fun alternates(leader: String, vararg words: String) = assignOctopus(
        typed = leader,
        candidates = words.mapIndexed { place, word ->
            OctopusCandidate(word, -place.toDouble(), OctopusKind.COMPLETION)
        },
        keys = null,
        keyOf = { it },
        limit = 8,
        scoreSpread = Double.POSITIVE_INFINITY,
    )

    @Test
    fun `each alternate hangs off the key that reaches it`() {
        val floated = alternates("hello", "help", "held")
        assertEquals(
            mapOf('p'.code to "help", 'd'.code to "held"),
            floated.associate { it.keyCodePoint to it.word },
        )
    }

    @Test
    fun `the drawn part of each alternate is what the stroke already covers`() {
        val help = alternates("hello", "help").single()
        assertEquals(3, help.typedChars)
        assertEquals("hel", help.word.take(help.typedChars))
    }

    @Test
    fun `an alternate that is a prefix of the leader has nowhere to go`() {
        // You cannot reach "hell" by carrying on from "hello"; lifting early is
        // how you get it, and the picker is what offers it.
        assertTrue(alternates("hello", "hell").isEmpty())
    }

    @Test
    fun `an alternate diverging at the first letter hangs off its first letter`() {
        assertEquals('j'.code, alternates("hello", "jello").single().keyCodePoint)
    }

    @Test
    fun `two alternates wanting one key leave the better one there`() {
        val floated = alternates("hello", "help", "helping")
        assertEquals(listOf("help"), floated.map { it.word })
    }
}
