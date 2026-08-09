package com.wasimaster.wmkeyboard.core.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SoundPackFileTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(dir: File? = File(temp.root, "soundpacks")) =
        SoundPackStore(dir?.apply { mkdirs() })

    /** A minimal but real 16-bit mono PCM WAV, so the header sniff passes. */
    private fun wav(samples: Int = 64): ByteArray {
        val data = ByteArray(samples * 2)
        val out = ByteArrayOutputStream()
        fun le32(v: Int) = out.write(byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
        ))
        fun le16(v: Int) = out.write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()))
        out.write("RIFF".toByteArray()); le32(36 + data.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1)
        le32(44100); le32(88200); le16(2); le16(16)
        out.write("data".toByteArray()); le32(data.size); out.write(data)
        return out.toByteArray()
    }

    private fun pack(manifest: String, files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(SoundPackFile.MANIFEST))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            for ((name, bytes) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun threeVariantPack(
        roles: String = "{}",
        press: String = """["sounds/1.wav","sounds/2.wav","sounds/3.wav"]""",
    ) = pack(
        """
        {"format":"wmkeyboard-sound-pack","version":1,"id":"test","name":"Test Pack",
         "author":"Nobody","packVersion":"1.2.3","gain":1.0,
         "press":$press,"release":[],"roles":$roles}
        """.trimIndent(),
        mapOf(
            "sounds/1.wav" to wav(),
            "sounds/2.wav" to wav(),
            "sounds/3.wav" to wav(),
            "sounds/space.wav" to wav(),
        ),
    )

    private fun import(s: SoundPackStore, bytes: ByteArray) =
        SoundPackFile.import(ByteArrayInputStream(bytes), s)

    private fun fixtureBytes(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("addons/$name")) {
            "missing test fixture addons/$name"
        }.use { it.readBytes() }

    // ---- the real thing ------------------------------------------------

    @Test
    fun `a pack built by the monkeytype repository's tooling imports`() {
        // The fixture is packs/click.wmsoundpack from
        // wasi-master/wmkeyboard-monkeytype-sounds, unmodified. The publishing
        // tooling and this importer are two halves of one format, written on
        // opposite sides of a repository boundary; without this test the only
        // thing checking they agree is a user's phone.
        val s = store()
        val result = import(s, fixtureBytes("click.wmsoundpack"))
        assertTrue("$result", result is SoundPackImportResult.Imported)
        val pack = (result as SoundPackImportResult.Imported).pack

        assertEquals("Click", pack.name)
        assertEquals("Monkeytype contributors", pack.author)
        assertEquals(3, pack.variantCount)
        // Monkeytype has no per-key sounds to import.
        assertTrue(pack.roles.isEmpty())

        val manifest = checkNotNull(s.manifestFor(pack.id))
        // Every role resolves to something playable, by falling back.
        for (role in KeySoundRole.entries) {
            val names = manifest.pressFor(role)
            assertEquals("$role", 3, names.size)
            for (name in names) assertNotNull("$role/$name", s.sampleFile(pack.id, name))
        }
        // A synthesized interface click is one event; there is no key-up.
        assertFalse(manifest.hasRelease())
        assertEquals(false, pack.hasRelease)
    }

    @Test
    fun `a pack cut into key-down and key-up imports both halves`() {
        // packs/nk-creams.wmsoundpack from the same repository — one of the
        // twelve sets whose recordings hold the switch coming back up, cut in
        // two at import time by its tooling. The other fixture only proves the
        // press half of the format still agrees across the boundary.
        val s = store()
        val result = import(s, fixtureBytes("nk-creams.wmsoundpack"))
        assertTrue("$result", result is SoundPackImportResult.Imported)
        val pack = (result as SoundPackImportResult.Imported).pack

        assertEquals(6, pack.variantCount)
        assertEquals(true, pack.hasRelease)
        // Six of each, stored as twelve distinct samples: no key-up is the
        // same file as a key-down.
        assertEquals(12, pack.sampleCount)

        val manifest = checkNotNull(s.manifestFor(pack.id))
        assertTrue(manifest.hasRelease())
        for (role in KeySoundRole.entries) {
            // The pack fills no roles, so every role falls back to both of the
            // pack's own lists rather than to the press one twice.
            val down = manifest.samplesFor(role, KeySoundPhase.PRESS)
            val up = manifest.samplesFor(role, KeySoundPhase.RELEASE)
            assertEquals("$role", 6, down.size)
            assertEquals("$role", 6, up.size)
            assertTrue("$role", down.none { it in up })
            for (name in down + up) assertNotNull("$role/$name", s.sampleFile(pack.id, name))
        }
    }

    @Test
    fun `a role that names only a press keeps the pack's key-up`() {
        val s = store()
        val result = import(
            s,
            pack(
                """
                {"format":"wmkeyboard-sound-pack","version":1,"id":"split","name":"Split",
                 "press":["sounds/1.wav"],"release":["sounds/1-up.wav"],
                 "roles":{"space":{"press":["sounds/space.wav"]}}}
                """.trimIndent(),
                mapOf(
                    "sounds/1.wav" to wav(),
                    "sounds/1-up.wav" to wav(32),
                    "sounds/space.wav" to wav(),
                ),
            ),
        )
        val pack = (result as SoundPackImportResult.Imported).pack
        val manifest = checkNotNull(s.manifestFor(pack.id))
        // The spacebar brought its own way down and nothing else, so it keeps
        // the board's key-up. Fallback is per field, not per role.
        assertEquals(
            manifest.pressFor(KeySoundRole.SPACE),
            manifest.samplesFor(KeySoundRole.SPACE, KeySoundPhase.PRESS),
        )
        assertNotEquals(
            manifest.pressFor(KeySoundRole.DEFAULT),
            manifest.pressFor(KeySoundRole.SPACE),
        )
        assertEquals(
            manifest.releaseFor(KeySoundRole.DEFAULT),
            manifest.releaseFor(KeySoundRole.SPACE),
        )
        assertEquals(true, pack.hasRelease)
    }

    @Test
    fun `a record written before hasRelease existed is backfilled from its manifest`() {
        val dir = File(temp.root, "soundpacks")
        val first = store(dir)
        val pack = (
            import(first, fixtureBytes("nk-creams.wmsoundpack"))
                as SoundPackImportResult.Imported
            ).pack
        assertEquals(true, pack.hasRelease)

        // Rewrite packs.json the way a build that predates the field wrote it:
        // the record is there, the field is not. Edited as text rather than
        // through the store, because the store has no way to express "absent".
        val index = File(dir, "packs.json")
        val aged = index.readText().replace(""""hasRelease":true,""", "")
        assertTrue("the field should have been in the file", aged != index.readText())
        index.writeText(aged)

        // A fresh store over the same directory answers it from the pack's own
        // manifest rather than reporting a pack with key-up samples as one
        // without — those packs were installed with their release lists intact
        // and have been playing them all along.
        assertEquals(true, SoundPackStore(dir).pack(pack.id)?.hasRelease)
        // And the answer is written back, so it is read once and not again.
        assertTrue(index.readText().contains(""""hasRelease":true"""))
    }

    // ---- accepting -----------------------------------------------------

    @Test
    fun `a three-variant pack imports with all three on disk`() {
        val s = store()
        val result = import(s, threeVariantPack())
        assertTrue("$result", result is SoundPackImportResult.Imported)
        val pack = (result as SoundPackImportResult.Imported).pack
        assertEquals("Test Pack", pack.name)
        assertEquals(3, pack.variantCount)
        // The fourth file is in the archive but nothing references it, so it
        // is not stored: a pack ships what it plays.
        assertEquals(3, pack.sampleCount)

        val manifest = checkNotNull(s.manifestFor(pack.id))
        assertEquals(3, manifest.press.size)
        for (name in manifest.press) assertNotNull(s.sampleFile(pack.id, name))
    }

    @Test
    fun `a role falls back to the default set per field`() {
        val s = store()
        val result = import(
            s,
            threeVariantPack(roles = """{"space":{"press":["sounds/space.wav"],"gain":0.5}}"""),
        )
        val pack = (result as SoundPackImportResult.Imported).pack
        assertEquals(listOf("space"), pack.roles)

        val manifest = checkNotNull(s.manifestFor(pack.id))
        assertEquals(1, manifest.pressFor(KeySoundRole.SPACE).size)
        // Enter filled nothing, so it plays the pack's own three.
        assertEquals(3, manifest.pressFor(KeySoundRole.ENTER).size)
        assertEquals(0.5f, manifest.gainFor(KeySoundRole.SPACE), 0.001f)
        assertEquals(1f, manifest.gainFor(KeySoundRole.ENTER), 0.001f)
    }

    @Test
    fun `gain is clamped into what SoundPool can actually do`() {
        val s = store()
        val result = import(
            s,
            pack(
                """
                {"format":"wmkeyboard-sound-pack","version":1,"id":"loud","name":"Loud",
                 "gain":4.0,"press":["sounds/1.wav"],"roles":{"space":{"gain":-1.0}}}
                """.trimIndent(),
                mapOf("sounds/1.wav" to wav()),
            ),
        )
        val pack = (result as SoundPackImportResult.Imported).pack
        val manifest = checkNotNull(s.manifestFor(pack.id))
        // SoundPool.play takes 0..1 and cannot amplify, so a pack asking for
        // 4.0 gets 1.0 rather than a field that silently does nothing.
        assertEquals(1f, manifest.gainFor(KeySoundRole.DEFAULT), 0.001f)
        assertEquals(0f, manifest.gainFor(KeySoundRole.SPACE), 0.001f)
    }

    @Test
    fun `a missing variant is dropped and the rest of the pack survives`() {
        val s = store()
        val result = import(
            s,
            threeVariantPack(press = """["sounds/1.wav","sounds/gone.wav","sounds/3.wav"]"""),
        )
        val pack = (result as SoundPackImportResult.Imported).pack
        // Losing one of three recordings is not worth refusing the other two.
        assertEquals(2, pack.variantCount)
    }

    // ---- refusing ------------------------------------------------------

    @Test
    fun `an archive with no manifest is not a sound pack`() {
        val s = store()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("sounds/1.wav"))
            zip.write(wav())
            zip.closeEntry()
        }
        assertEquals(SoundPackImportResult.NotASoundPack, import(s, out.toByteArray()))
    }

    @Test
    fun `a manifest for another format is refused`() {
        val s = store()
        val bytes = pack("""{"format":"mechvibes","press":["sounds/1.wav"]}""", mapOf("sounds/1.wav" to wav()))
        assertEquals(SoundPackImportResult.NotASoundPack, import(s, bytes))
    }

    @Test
    fun `a newer format version is refused rather than guessed at`() {
        val s = store()
        val bytes = pack(
            """{"format":"wmkeyboard-sound-pack","version":2,"id":"x","name":"X","press":["sounds/1.wav"]}""",
            mapOf("sounds/1.wav" to wav()),
        )
        val result = import(s, bytes)
        assertTrue("$result", result is SoundPackImportResult.Rejected)
        assertEquals("2", (result as SoundPackImportResult.Rejected).messageArg)
    }

    @Test
    fun `a pack whose samples are all unplayable is refused`() {
        val s = store()
        val bytes = pack(
            """{"format":"wmkeyboard-sound-pack","version":1,"id":"x","name":"X","press":["sounds/1.wav"]}""",
            mapOf("sounds/1.wav" to "<!DOCTYPE html><html>nope</html>".toByteArray()),
        )
        val result = import(s, bytes)
        assertTrue("$result", result is SoundPackImportResult.Rejected)
    }

    // ---- entry names are never paths -----------------------------------

    @Test
    fun `a traversing entry name cannot write outside the store`() {
        val s = store()
        val escape = File(temp.root, "escaped.wav")
        val bytes = pack(
            """
            {"format":"wmkeyboard-sound-pack","version":1,"id":"evil","name":"Evil",
             "press":["../../escaped.wav"]}
            """.trimIndent(),
            mapOf("../../escaped.wav" to wav()),
        )
        val result = import(s, bytes)

        // The entry resolves — it is a map key, not a path — so the pack
        // installs, but the byte array lands in a file this importer named.
        assertTrue("$result", result is SoundPackImportResult.Imported)
        assertTrue("the importer wrote outside its own directory", !escape.exists())
        val pack = (result as SoundPackImportResult.Imported).pack
        assertEquals(listOf("s000.snd"), checkNotNull(s.manifestFor(pack.id)).press)
    }

    @Test
    fun `a sample name the store did not generate resolves to nothing`() {
        val s = store()
        val pack = (import(s, threeVariantPack()) as SoundPackImportResult.Imported).pack
        // The assertion that makes joining an on-disk manifest string onto a
        // directory safe at play time.
        assertNull(s.sampleFile(pack.id, "../../../etc/passwd"))
        assertNull(s.sampleFile(pack.id, "pack.json"))
        assertNotNull(s.sampleFile(pack.id, "s000.snd"))
    }

    // ---- store ---------------------------------------------------------

    @Test
    fun `deleting a pack takes its directory with it`() {
        val s = store()
        val pack = (import(s, threeVariantPack()) as SoundPackImportResult.Imported).pack
        val dir = checkNotNull(s.existingDirFor(pack.id))
        s.delete(pack.id)
        assertTrue("the pack directory outlived the record", !dir.exists())
        assertNull(s.pack(pack.id))
    }

    @Test
    fun `packs round-trip through disk`() {
        val dir = File(temp.root, "soundpacks")
        val first = store(dir)
        val pack = (import(first, threeVariantPack()) as SoundPackImportResult.Imported).pack

        val second = SoundPackStore(dir)
        assertEquals(1, second.packs().size)
        assertEquals("Test Pack", second.pack(pack.id)?.name)
        assertEquals(pack.id, second.resolve("Test Pack"))
    }

    @Test
    fun `with no directory the store reads as empty and writes nothing`() {
        val s = store(dir = null)
        assertTrue(s.packs().isEmpty())
        assertEquals(SoundPackImportResult.Failed, import(s, threeVariantPack()))
        assertTrue("nothing should have been written", temp.root.listFiles().orEmpty().isEmpty())
    }
}
