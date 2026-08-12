package com.wasimaster.wmkeyboard.core.keyman

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the rules out of a package, including out of one built to misbehave.
 *
 * The archives here are made in memory rather than vendored, because the point
 * is what happens with entry names and sizes no real package would ever carry.
 */
class KeymanPackageTest {

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun rules(archive: ByteArray, id: String): ByteArray? =
        KeymanPackage.rulesFrom(ByteArrayInputStream(archive), id)

    @Test
    fun `the keyboard's rules are found among the other files`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val archive = zip(
            "kmp.json" to "{}".toByteArray(),
            "welcome.htm" to "<html/>".toByteArray(),
            "khmer_angkor.kmx" to payload,
            "KbdKhmr.ttf" to ByteArray(64),
        )
        assertArrayEquals(payload, rules(archive, "khmer_angkor"))
    }

    @Test
    fun `another keyboard's rules in the same package are not taken`() {
        val archive = zip(
            "other.kmx" to byteArrayOf(9),
            "wanted.kmx" to byteArrayOf(7),
        )
        assertArrayEquals(byteArrayOf(7), rules(archive, "wanted"))
        assertNull(rules(archive, "absent"))
    }

    /**
     * The guarantee that matters. An entry named to climb out of the extraction
     * directory is matched on its base name, and the bytes come back as a return
     * value, so there is no path for it to escape from.
     */
    @Test
    fun `a traversing entry name is treated as a name, not a path`() {
        val payload = byteArrayOf(5, 5, 5)
        for (name in listOf(
            "../../../databases/x.kmx",
            "/etc/x.kmx",
            "a/b/../../x.kmx",
            "nested/dir/x.kmx",
        )) {
            val got = rules(zip(name to payload), "x")
            assertArrayEquals("entry '$name' was not read as x.kmx", payload, got)
        }
    }

    /** Reading stops at the cap rather than at whatever the header claims. */
    @Test
    fun `an oversized entry is refused`() {
        val huge = ByteArray((KeymanLimits.MAX_KMX_BYTES + 1024).toInt())
        assertNull(rules(zip("big.kmx" to huge), "big"))
    }

    @Test
    fun `an entry at the cap is still read`() {
        val atCap = ByteArray(KeymanLimits.MAX_KMX_BYTES.toInt()) { 3 }
        val got = rules(zip("ok.kmx" to atCap), "ok")
        assertEquals(atCap.size, got?.size)
    }

    /** An archive of many tiny entries must not be walked forever. */
    @Test
    fun `an archive with too many entries is refused`() {
        val many = Array(600) { "filler$it.txt" to ByteArray(1) }
        assertNull(rules(zip(*many, "late.kmx" to byteArrayOf(1)), "late"))
    }

    @Test
    fun `unreadable input is null rather than an exception`() {
        assertNull(rules(byteArrayOf(1, 2, 3), "x"))
        assertNull(rules(ByteArray(0), "x"))
        assertNull(rules("not a zip at all".toByteArray(), "x"))
    }

    /** Matching ignores case, because packages are built on Windows. */
    @Test
    fun `the extension is matched case-insensitively`() {
        assertArrayEquals(byteArrayOf(2), rules(zip("Thing.KMX" to byteArrayOf(2)), "thing"))
    }

    /** The real packages parse through this path end to end. */
    @Test
    fun `a real package yields rules that parse`() {
        val archive = zip(
            "kmp.json" to "{}".toByteArray(),
            "khmer_angkor.kmx" to fixtureBytes("khmer_angkor.kmx"),
        )
        val extracted = rules(archive, "khmer_angkor")
        assertTrue("nothing extracted", extracted != null)
        assertTrue(
            "extracted rules did not parse",
            KmxParser.parse(extracted!!) is KeymanResult.Success,
        )
    }

    private fun fixtureBytes(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("kmx/$name")) {
            "missing fixture kmx/$name"
        }.use { it.readBytes() }

    private fun jsFixture(id: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("js/$id.js")) {
            "missing fixture js/$id.js"
        }.use { it.readBytes() }

    /**
     * A package shaped like the real thing, built from the real parts.
     *
     * Assembled here rather than vendored because a genuine `.kmp` is mostly
     * fonts, and none of what this reads depends on them being present.
     */
    private fun packageZip(id: String, manifest: String = manifestFor(id)): ByteArray = zip(
        "kmp.json" to manifest.toByteArray(),
        "kmp.inf" to "[Package]".toByteArray(),
        "$id.js" to jsFixture(id),
        "$id.kmx" to fixtureBytes("$id.kmx"),
        "welcome.htm" to "<html/>".toByteArray(),
        "font.ttf" to ByteArray(512),
    )

    private fun manifestFor(id: String): String = """
        {"system":{"fileVersion":"7.0"},
         "keyboards":[{"id":"$id","name":"Test $id","version":"1.0",
           "languages":[{"id":"km","name":"Khmer"},{"id":"en","name":"English"}]}]}
    """.trimIndent()

    @Test
    fun `a package gives up its keyboard, grid and rules`() {
        val contents = checkNotNull(KeymanPackage.read(ByteArrayInputStream(packageZip("khmer_angkor"))))
        assertEquals("khmer_angkor", contents.keyboardId)
        assertEquals("Test khmer_angkor", contents.name)
        assertEquals(listOf("km", "en"), contents.languages)
        assertTrue("no grid came out", contents.isUsable)
        assertTrue("no rules came out", contents.rules != null)
        assertTrue(
            "the rules did not parse",
            KmxParser.parse(contents.rules!!) is KeymanResult.Success,
        )
    }

    /** The grid out of a package must convert the same as any other. */
    @Test
    fun `the grid from a package converts`() {
        val contents = KeymanPackage.read(ByteArrayInputStream(packageZip("lao_2008_basic")))!!
        val doc = KeymanTouchLayoutReader.parse(contents.touchLayoutJson!!)
        assertTrue(doc is KeymanResult.Success)
        val converted = TouchLayoutConverter.convert(
            (doc as KeymanResult.Success).value,
            contents.keyboardId,
            contents.name,
        )
        assertTrue("package grid did not convert", converted is KeymanResult.Success)
    }

    /** Without a manifest it is some other ZIP, not a keyboard package. */
    @Test
    fun `an archive with no manifest is not a package`() {
        val archive = zip("thing.js" to jsFixture("basic_kbdus"))
        assertNull(KeymanPackage.read(ByteArrayInputStream(archive)))
    }

    @Test
    fun `a malformed manifest is refused rather than guessed at`() {
        for (bad in listOf("{}", "not json", """{"keyboards":[]}""", """{"keyboards":[{}]}""")) {
            assertNull(
                "accepted manifest: $bad",
                KeymanPackage.read(ByteArrayInputStream(packageZip("basic_kbdus", bad))),
            )
        }
    }

    /** A package with no compiled keyboard has no grid, and says so. */
    @Test
    fun `a package with no compiled keyboard is unusable, not null`() {
        val archive = zip(
            "kmp.json" to manifestFor("x").toByteArray(),
            "x.kmx" to fixtureBytes("basic_kbdus.kmx"),
        )
        val contents = checkNotNull(KeymanPackage.read(ByteArrayInputStream(archive)))
        assertEquals("x", contents.keyboardId)
        assertTrue("should have no grid", !contents.isUsable)
        assertTrue("rules should still be there", contents.rules != null)
    }

    @Test
    fun `reading a package never throws on rubbish`() {
        for (bytes in listOf(ByteArray(0), byteArrayOf(1, 2, 3), "PK".toByteArray())) {
            assertNull(KeymanPackage.read(ByteArrayInputStream(bytes)))
        }
    }
}
