package com.wasimaster.wmkeyboard.ime

import com.wasimaster.wmkeyboard.core.gesture.GesturePoint
import com.wasimaster.wmkeyboard.core.settings.OctopusFlickSensitivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The one shape test that decides whether an upward stroke off a key takes the
 * word floating over it or becomes a glide (discussion #102).
 *
 * Direction decides, not speed. The first version also asked the stroke to be
 * quick, to still be quick at the end, and to still point up across its last
 * few samples — and it failed most real flicks, because a flick ends by
 * decelerating as the finger leaves the glass and its final samples jitter.
 * Those three rules are gone and these tests are what stop them coming back.
 *
 * The remaining trade is deliberate: a glide that opens straight upward off a
 * key carrying a word is harder to start. A board with words on its keys is a
 * board whose owner asked for them.
 */
class OctopusFlickTest {

    private val keyWidth = 100f
    private val startX = 200f
    private val startY = 400f

    /** A straight stroke [degrees] off vertical, sampled evenly. */
    private fun stroke(
        degrees: Float = 0f,
        length: Float = 120f,
        durationMs: Long = 90L,
        samples: Int = 6,
        fromX: Float = startX,
        fromY: Float = startY,
    ): List<GesturePoint> {
        val radians = Math.toRadians(degrees.toDouble())
        val dx = (sin(radians) * length).toFloat()
        val dy = (-cos(radians) * length).toFloat()
        return (0..samples).map { i ->
            val f = i.toFloat() / samples
            GesturePoint(fromX + dx * f, fromY + dy * f, durationMs * i / samples)
        }
    }

    private fun flick(
        points: List<GesturePoint>,
        sensitivity: OctopusFlickSensitivity = OctopusFlickSensitivity.BALANCED,
    ) = octopusFlick(
        points = points,
        startX = startX,
        startY = startY,
        keyWidthPx = keyWidth,
        startReachPx = keyWidth * OCTOPUS_START_REACH_WIDTHS,
        minTravelPx = keyWidth * OCTOPUS_MIN_TRAVEL_WIDTHS,
        sensitivity = sensitivity,
    )

    // ---- what a pick looks like ----

    @Test
    fun `a quick straight flick up takes the word`() {
        assertTrue(flick(stroke()))
    }

    @Test
    fun `a slight lean is still a flick`() {
        assertTrue(flick(stroke(degrees = 15f)))
        assertTrue(flick(stroke(degrees = -15f)))
    }

    // ---- what a glide looks like ----

    @Test
    fun `the cone is what the sensitivity moves`() {
        val leaning = stroke(degrees = 45f)
        assertFalse("balanced keeps a 45-degree stroke for the decoder", flick(leaning))
        assertTrue(
            "relaxed takes it, which is what a board that leans on the words wants",
            flick(leaning, OctopusFlickSensitivity.RELAXED),
        )
        assertFalse(
            "and strict refuses a lean balanced would take",
            flick(stroke(degrees = 30f), OctopusFlickSensitivity.STRICT),
        )
        assertTrue(flick(stroke(degrees = 30f)))
    }

    @Test
    fun `a slow deliberate flick is still a flick`() {
        // The regression that made this unusable on a real board: speed was a
        // gate, and an ordinary unhurried flick failed it.
        assertTrue(flick(stroke(durationMs = 400L)))
    }

    @Test
    fun `a flick that decelerates into the lift is still a flick`() {
        // Every flick does this. The finger slows as it leaves the glass, so a
        // rule about the speed of the last few milliseconds rejects the gesture
        // it exists to accept.
        val rising = stroke(length = 130f, durationMs = 80L)
        val settling = rising + (1..4).map { i ->
            GesturePoint(rising.last().x + i, rising.last().y - 1f, rising.last().t + i * 20L)
        }
        assertTrue(flick(settling))
    }

    @Test
    fun `a stroke that goes up and hooks well away is a glide`() {
        // Straightness is what is left, and it is enough: a stroke that turns
        // has wandered too far from the line between its ends.
        val rising = stroke(length = 110f, durationMs = 60L)
        val hooked = rising + (1..4).map { i ->
            GesturePoint(rising.last().x + i * 40f, rising.last().y + i * 3f, rising.last().t + i * 8L)
        }
        assertFalse(flick(hooked))
    }

    @Test
    fun `a real word whose stroke opens upward stays a glide`() {
        // s to w to e on QWERTY: up and to the right, then across. This is the
        // stroke the arbitration exists to protect.
        val points = listOf(
            GesturePoint(startX, startY, 0L),
            GesturePoint(startX + 20f, startY - 60f, 40L),
            GesturePoint(startX + 45f, startY - 95f, 80L),
            GesturePoint(startX + 140f, startY - 100f, 150L),
        )
        assertFalse(flick(points))
    }

    @Test
    fun `a long sweep up the board is a path, not a flick`() {
        assertFalse(flick(stroke(length = 400f, durationMs = 150L)))
    }

    @Test
    fun `a stroke that barely leaves the key is not a flick`() {
        // And this is the clamp that matters: the glide loop's own start slop
        // grows for a moment after each keystroke, and a travel bar below it
        // would leave the two disagreeing about whether anything has begun.
        assertFalse(flick(stroke(length = 40f)))
    }

    @Test
    fun `a flick that began on another key claims nothing`() {
        assertFalse(flick(stroke(fromX = startX + 120f)))
    }

    @Test
    fun `a downward stroke is never a flick`() {
        assertFalse(flick(stroke(degrees = 180f)))
    }

    @Test
    fun `a tap is not a flick`() {
        assertFalse(flick(listOf(GesturePoint(startX, startY, 0L))))
    }

    @Test
    fun `timing is not consulted at all`() {
        val unstamped = stroke().map { GesturePoint(it.x, it.y, 0L) }
        assertTrue(flick(unstamped))
    }

    @Test
    fun `two samples are enough to have a direction`() {
        // A fast flick can be over in two reports, and those used never to
        // reach this function at all: the word was not picked and nothing was
        // typed either, so the stroke simply vanished.
        assertTrue(
            flick(listOf(GesturePoint(startX, startY, 0L), GesturePoint(startX, startY - 120f, 30L))),
        )
    }
}
