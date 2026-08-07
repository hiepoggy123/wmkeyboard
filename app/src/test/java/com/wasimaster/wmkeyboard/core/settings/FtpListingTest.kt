package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.settings.sink.FtpListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `LIST` has no defined output format, which is the whole reason the parser
 * exists. These are the three shapes real servers emit.
 */
class FtpListingTest {

    /** 2025-08-07T14:02:33Z */
    private val now = 1_754_575_353_000L

    @Test
    fun `mlsd is the machine-readable one and says everything`() {
        val entries = FtpListing.parseMlsd(
            listOf(
                "type=cdir;modify=20250807140233; .",
                "type=dir;modify=20250807140233; subfolder",
                "type=file;size=4096;modify=20250807140233; wmkeyboard-auto-20250807-140233.wmconfig.json",
            ),
        )
        assertEquals(2, entries.size)
        assertTrue(entries[0].isDirectory)
        val file = entries[1]
        assertEquals("wmkeyboard-auto-20250807-140233.wmconfig.json", file.name)
        assertEquals(4096L, file.sizeBytes)
        assertEquals(now, file.modifiedAtMs)
        assertFalse(file.isDirectory)
    }

    @Test
    fun `a name with a space survives mlsd`() {
        val entries = FtpListing.parseMlsd(listOf("type=file;size=1; my backup.json"))
        assertEquals("my backup.json", entries.single().name)
    }

    @Test
    fun `unix list output parses`() {
        val entries = FtpListing.parseList(
            listOf(
                "drwxr-xr-x 2 owner group 4096 Aug  7 14:02 subfolder",
                "-rw-r--r-- 1 owner group 4096 Aug  7 14:02 wmkeyboard-auto-20250807-140233.wmconfig.json",
            ),
            now,
        )
        assertEquals(2, entries.size)
        assertTrue(entries[0].isDirectory)
        assertEquals("wmkeyboard-auto-20250807-140233.wmconfig.json", entries[1].name)
        assertEquals(4096L, entries[1].sizeBytes)
    }

    @Test
    fun `the unix format's missing year is inferred backwards, never forwards`() {
        // A recent file has a time and no year. Taking this year would put a
        // December listing read in January eleven months in the future.
        val december = FtpListing.parseList(
            listOf("-rw-r--r-- 1 o g 1 Dec 25 09:00 wmkeyboard-auto-20241225-090000.wmconfig.json"),
            // Read on 2025-01-05.
            nowMs = 1_736_035_200_000L,
        ).single()
        assertTrue(december.modifiedAtMs.toString(), december.modifiedAtMs < 1_736_035_200_000L)
    }

    @Test
    fun `an old unix entry has a year instead of a time`() {
        val entry = FtpListing.parseList(
            listOf("-rw-r--r-- 1 o g 7 Aug  7 2020 wmkeyboard-auto-20200807-140233.wmconfig.json"),
            now,
        ).single()
        assertEquals(7L, entry.sizeBytes)
        assertTrue(entry.modifiedAtMs in 1_596_000_000_000L..1_597_000_000_000L)
    }

    @Test
    fun `dos list output parses`() {
        val entries = FtpListing.parseList(
            listOf(
                "08-07-25  02:02PM       <DIR>          subfolder",
                "08-07-25  02:02PM                 4096 wmkeyboard-auto-20250807-140233.wmconfig.json",
            ),
            now,
        )
        assertEquals(2, entries.size)
        assertTrue(entries[0].isDirectory)
        assertEquals(4096L, entries[1].sizeBytes)
    }

    @Test
    fun `dot entries are never listed`() {
        assertTrue(FtpListing.parseMlsd(listOf("type=cdir; .", "type=pdir; ..")).isEmpty())
        assertTrue(
            FtpListing.parseList(
                listOf("drwxr-xr-x 2 o g 4096 Aug  7 14:02 .", "drwxr-xr-x 2 o g 4096 Aug  7 14:02 .."),
                now,
            ).isEmpty(),
        )
    }

    @Test
    fun `rubbish lines are skipped rather than fatal`() {
        assertTrue(FtpListing.parseList(listOf("", "   ", "total 8", "garbage"), now).isEmpty())
        assertTrue(FtpListing.parseMlsd(listOf("", "no-space-here")).isEmpty())
    }

    @Test
    fun `pasv gives a host and a port built from two bytes`() {
        // The port is p1*256+p2, which is the part everyone gets wrong once.
        assertEquals(
            "192.168.1.5" to 51_000,
            FtpListing.parsePasv("227 Entering Passive Mode (192,168,1,5,199,56)"),
        )
        assertEquals(
            "10.0.0.1" to 21,
            FtpListing.parsePasv("227 Passive (10,0,0,1,0,21)."),
        )
    }

    @Test
    fun `a malformed pasv reply is refused rather than guessed at`() {
        assertNull(FtpListing.parsePasv("227 Entering Passive Mode"))
        assertNull(FtpListing.parsePasv("227 (1,2,3,4,5)"))
        // A byte out of range is not an address.
        assertNull(FtpListing.parsePasv("227 (999,0,0,1,0,21)"))
    }

    @Test
    fun `epsv gives a port and no address`() {
        assertEquals(51_000, FtpListing.parseEpsv("229 Entering Extended Passive Mode (|||51000|)"))
        assertNull(FtpListing.parseEpsv("229 Entering Extended Passive Mode"))
    }

    @Test
    fun `an mlsd timestamp is read as utc`() {
        assertEquals(now, FtpListing.parseMlsdTime("20250807140233"))
        assertEquals(now, FtpListing.parseMlsdTime("20250807140233.123"))
        assertEquals(0L, FtpListing.parseMlsdTime("nonsense"))
    }
}
