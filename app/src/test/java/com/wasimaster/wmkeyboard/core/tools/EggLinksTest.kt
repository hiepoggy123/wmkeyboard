package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The QR scanner's rickroll detector: every common shape of the link matches,
 * and everything that merely resembles it does not.
 */
class EggLinksTest {

    @Test
    fun `matches the canonical watch url`() {
        assertTrue(EggLinks.isRickroll("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `matches the short shapes`() {
        assertTrue(EggLinks.isRickroll("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(EggLinks.isRickroll("youtu.be/dQw4w9WgXcQ"))
        assertTrue(EggLinks.isRickroll("https://youtu.be/dQw4w9WgXcQ?t=42"))
    }

    @Test
    fun `matches embed shorts and live paths`() {
        assertTrue(EggLinks.isRickroll("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertTrue(EggLinks.isRickroll("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertTrue(EggLinks.isRickroll("https://m.youtube.com/live/dQw4w9WgXcQ"))
        assertTrue(EggLinks.isRickroll("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun `matches when v sits among other query parameters`() {
        assertTrue(EggLinks.isRickroll("https://www.youtube.com/watch?list=PL123&v=dQw4w9WgXcQ&t=1"))
    }

    @Test
    fun `host comparison ignores case`() {
        assertTrue(EggLinks.isRickroll("HTTPS://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `the video id is case sensitive`() {
        assertFalse(EggLinks.isRickroll("https://www.youtube.com/watch?v=dqw4w9wgxcq"))
    }

    @Test
    fun `other videos and hosts do not match`() {
        assertFalse(EggLinks.isRickroll("https://www.youtube.com/watch?v=9bZkp7q19f0"))
        assertFalse(EggLinks.isRickroll("https://vimeo.com/dQw4w9WgXcQ"))
        assertFalse(EggLinks.isRickroll("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(EggLinks.isRickroll("not a url at all"))
    }

    @Test
    fun `an id that continues past eleven characters is a different video`() {
        assertFalse(EggLinks.isRickroll("https://www.youtube.com/watch?v=dQw4w9WgXcQQ"))
        assertFalse(EggLinks.isRickroll("https://youtu.be/dQw4w9WgXcQQ"))
    }
}
