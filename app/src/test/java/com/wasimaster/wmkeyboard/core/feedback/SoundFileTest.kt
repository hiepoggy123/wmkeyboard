package com.wasimaster.wmkeyboard.core.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class SoundFileTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(dir: File? = File(temp.root, "keysounds")) =
        SoundStore(dir?.apply { mkdirs() })

    private fun fixtureBytes(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("addons/$name")) {
            "missing test fixture addons/$name"
        }.use { it.readBytes() }

    private fun import(store: SoundStore, bytes: ByteArray, name: String = "Test Sound") =
        SoundFile.import(ByteArrayInputStream(bytes), store, name)

    // ---- accepting -----------------------------------------------------

    @Test
    fun `the sample repository's mp3 imports`() {
        val s = store()
        val result = import(s, fixtureBytes("blip.mp3"), name = "Blip")
        assertTrue("$result", result is SoundImportResult.Imported)
        val sound = (result as SoundImportResult.Imported).sound
        assertEquals("Blip", sound.name)
        assertNotNull(s.existingFileFor(sound.id))
    }

    @Test
    fun `an mp3 with an ID3 tag is accepted`() {
        val bytes = "ID3".toByteArray(Charsets.ISO_8859_1) + ByteArray(512)
        assertTrue(import(store(), bytes) is SoundImportResult.Imported)
    }

    @Test
    fun `a bare mp3 frame sync is accepted`() {
        // No metadata, straight into the first frame header.
        val bytes = byteArrayOf(0xFF.toByte(), 0xFB.toByte()) + ByteArray(512)
        assertTrue(import(store(), bytes) is SoundImportResult.Imported)
    }

    @Test
    fun `ogg and wav are accepted even though the spec says mp3`() {
        // SoundPool plays them, and a user importing their own file has no
        // reason to convert first.
        val ogg = "OggS".toByteArray(Charsets.ISO_8859_1) + ByteArray(512)
        assertTrue(import(store(File(temp.root, "s1")), ogg) is SoundImportResult.Imported)

        val wav = "RIFF".toByteArray(Charsets.ISO_8859_1) + ByteArray(4) +
            "WAVE".toByteArray(Charsets.ISO_8859_1) + ByteArray(512)
        assertTrue(import(store(File(temp.root, "s2")), wav) is SoundImportResult.Imported)
    }

    @Test
    fun `the stored file name comes from the id, never the source`() {
        val s = store()
        val sound = (import(s, fixtureBytes("blip.mp3"), name = "../../evil")
            as SoundImportResult.Imported).sound
        assertEquals(SoundStore.fileNameFor(sound.id), sound.fileName)
        assertTrue(s.existingFileFor(sound.id)!!.canonicalPath.startsWith(temp.root.canonicalPath))
    }

    // ---- refusing ------------------------------------------------------

    @Test
    fun `an m4a is refused with a message naming the fix`() {
        val bytes = ByteArray(4) + "ftypM4A ".toByteArray(Charsets.ISO_8859_1) + ByteArray(64)
        val result = import(store(), bytes)
        assertTrue("$result", result is SoundImportResult.NotASound)
        assertTrue((result as SoundImportResult.NotASound).message.contains("MP3"))
    }

    @Test
    fun `a zip and an HTML page are refused as what they are`() {
        assertTrue(
            (import(store(), "PK".toByteArray(Charsets.ISO_8859_1) + ByteArray(64))
                as SoundImportResult.NotASound).message.contains("zip"),
        )
        assertTrue(
            (import(store(File(temp.root, "s2")), "<html>".toByteArray())
                as SoundImportResult.NotASound).message.contains("HTML"),
        )
    }

    @Test
    fun `an empty file is refused`() {
        assertTrue(import(store(), ByteArray(0)) is SoundImportResult.NotASound)
    }

    @Test
    fun `a file over the size cap is refused`() {
        val s = store()
        val huge = "ID3".toByteArray(Charsets.ISO_8859_1) +
            ByteArray((SoundStore.MAX_BYTES + 1024).toInt())
        assertTrue(import(s, huge) is SoundImportResult.Failed)
        assertTrue(s.sounds().isEmpty())
    }

    @Test
    fun `the sound cap is enforced`() {
        val s = store()
        val bytes = fixtureBytes("blip.mp3")
        repeat(SoundStore.MAX_SOUNDS) { import(s, bytes, name = "S$it") }
        assertTrue(import(s, bytes, name = "one too many") is SoundImportResult.TooManySounds)
        assertEquals(SoundStore.MAX_SOUNDS, s.sounds().size)
    }

    // ---- store ---------------------------------------------------------

    @Test
    fun `sounds survive a reload`() {
        val dir = File(temp.root, "keysounds")
        val id = (import(store(dir), fixtureBytes("blip.mp3")) as SoundImportResult.Imported).sound.id
        assertNotNull(SoundStore(dir).sound(id))
    }

    @Test
    fun `reconcile drops an entry whose file was deleted by hand`() {
        val dir = File(temp.root, "keysounds")
        val id = (import(store(dir), fixtureBytes("blip.mp3")) as SoundImportResult.Imported).sound.id
        File(dir, SoundStore.fileNameFor(id)).delete()
        assertTrue(SoundStore(dir).sounds().isEmpty())
    }

    @Test
    fun `deleting a sound removes its file`() {
        val s = store()
        val sound = (import(s, fixtureBytes("blip.mp3")) as SoundImportResult.Imported).sound
        val file = s.existingFileFor(sound.id)!!
        s.delete(sound.id)
        assertTrue(s.sounds().isEmpty())
        assertTrue(file.exists().not())
    }

    @Test
    fun `with no directory the store reads as empty and imports fail cleanly`() {
        val s = store(dir = null)
        assertTrue(s.sounds().isEmpty())
        assertNull(s.fileFor("anything"))
        assertTrue(import(s, fixtureBytes("blip.mp3")) is SoundImportResult.Failed)
    }

    @Test
    fun `installed sounds live in filesDir, not the cache the built-ins use`() {
        // The synthesised built-ins are derived data and meant to be evictable;
        // an installed sound is the user's and must not vanish with the cache.
        assertEquals("keysounds", SoundStore.DIR_NAME)
    }
}
