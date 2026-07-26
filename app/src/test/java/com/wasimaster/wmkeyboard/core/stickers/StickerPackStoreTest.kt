package com.wasimaster.wmkeyboard.core.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StickerPackStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): StickerPackStore = StickerPackStore(temp.root)

    private fun webp(marker: Byte = 1) = ProcessedSticker(
        bytes = byteArrayOf(marker, 2, 3),
        mime = "image/webp",
        animated = false,
        aspectRatio = 1f,
    )

    @Test
    fun `adds a sticker and writes its file`() {
        val store = store()
        val pack = store.createPack("Cats")!!
        val added = store.addSticker(pack.id, webp(), name = "grumpy")
        assertTrue(added is StickerAddResult.Added)
        val sticker = (added as StickerAddResult.Added).sticker
        assertEquals("${sticker.id}.webp", sticker.fileName)
        assertTrue(store.fileFor(pack.id, sticker)!!.isFile)
        assertEquals(1, store.packs().single().stickers.size)
    }

    @Test
    fun `survives a reload`() {
        val first = store()
        val pack = first.createPack("Cats")!!
        first.addSticker(pack.id, webp())

        val second = store()
        assertEquals("Cats", second.packs().single().name)
        assertEquals(1, second.totalStickers())
    }

    @Test
    fun `drops manifest entries whose file vanished`() {
        val first = store()
        val pack = first.createPack("Cats")!!
        val sticker = (first.addSticker(pack.id, webp()) as StickerAddResult.Added).sticker
        assertTrue(first.fileFor(pack.id, sticker)!!.delete())

        assertEquals(0, store().totalStickers())
    }

    @Test
    fun `deletes files nothing references`() {
        val first = store()
        val pack = first.createPack("Cats")!!
        val orphan = File(first.packDir(pack.id), "stray.webp").apply { writeBytes(byteArrayOf(9)) }
        val strayPack = File(temp.root, "pack_nobody").apply { mkdirs() }

        store()
        assertFalse(orphan.exists())
        assertFalse(strayPack.exists())
    }

    @Test
    fun `leaves an import staging directory alone`() {
        val store = store()
        val staging = store.stagingDir()!!
        File(staging, "e1.bin").writeBytes(byteArrayOf(1))

        store.reload()
        assertTrue(staging.isDirectory)
    }

    @Test
    fun `refuses a sticker past the per-pack cap`() {
        val store = store()
        val pack = store.createPack("Cats")!!
        repeat(StickerPackStore.MAX_STICKERS_PER_PACK) { store.addSticker(pack.id, webp()) }
        assertEquals(StickerAddResult.PackFull, store.addSticker(pack.id, webp()))
    }

    @Test
    fun `refuses a pack past the cap`() {
        val store = store()
        repeat(StickerPackStore.MAX_PACKS) { store.createPack("Pack $it") }
        assertNull(store.createPack("One more"))
    }

    @Test
    fun `disambiguates a duplicate pack name`() {
        val store = store()
        store.createPack("Cats")
        assertEquals("Cats (2)", store.createPack("Cats")!!.name)
        assertEquals("Cats (3)", store.createPack("Cats")!!.name)
    }

    @Test
    fun `deleting a pack removes its files`() {
        val store = store()
        val pack = store.createPack("Cats")!!
        store.addSticker(pack.id, webp())
        val dir = store.packDir(pack.id)!!

        store.deletePack(pack.id)
        assertFalse(dir.exists())
        assertTrue(store.isEmpty())
    }

    @Test
    fun `moving a sticker carries its file across`() {
        val store = store()
        val from = store.createPack("Cats")!!
        val to = store.createPack("Dogs")!!
        val sticker = (store.addSticker(from.id, webp()) as StickerAddResult.Added).sticker

        assertTrue(store.moveSticker(from.id, sticker.id, to.id))
        assertFalse(File(store.packDir(from.id), sticker.fileName).exists())
        assertTrue(File(store.packDir(to.id), sticker.fileName).isFile)
        assertEquals(0, store.pack(from.id)!!.stickers.size)
        assertEquals(1, store.pack(to.id)!!.stickers.size)
    }

    @Test
    fun `reordering moves a sticker within its pack`() {
        val store = store()
        val pack = store.createPack("Cats")!!
        val first = (store.addSticker(pack.id, webp(1)) as StickerAddResult.Added).sticker
        store.addSticker(pack.id, webp(2))

        store.reorderSticker(pack.id, first.id, 1)
        assertEquals(first.id, store.pack(pack.id)!!.stickers[1].id)
        // Clamped at the end rather than wrapping.
        store.reorderSticker(pack.id, first.id, 5)
        assertEquals(first.id, store.pack(pack.id)!!.stickers[1].id)
    }

    @Test
    fun `search matches name, tags and pack`() {
        val store = store()
        val cats = store.createPack("Cats")!!
        val dogs = store.createPack("Dogs")!!
        store.addSticker(cats.id, webp(1), name = "grumpy")
        store.addSticker(dogs.id, webp(2), name = "happy", emojis = listOf("🐶"))

        assertEquals(2, store.searchAsGifItems("").size)
        assertEquals(1, store.searchAsGifItems("grump").size)
        assertEquals(1, store.searchAsGifItems("🐶").size)
        // Pack name matches every sticker inside it.
        assertEquals(1, store.searchAsGifItems("dogs").size)
        assertEquals(0, store.searchAsGifItems("nothing").size)
        assertEquals(1, store.searchAsGifItems("", packId = cats.id).size)
    }

    @Test
    fun `item ids round-trip back to their sticker`() {
        val store = store()
        val pack = store.createPack("Cats")!!
        val sticker = (store.addSticker(pack.id, webp()) as StickerAddResult.Added).sticker

        val item = store.searchAsGifItems("").single()
        assertTrue(item.id.startsWith(StickerPackStore.ITEM_PREFIX))
        val found = store.findByItemId(item.id)
        assertNotNull(found)
        assertEquals(pack.id, found!!.first.id)
        assertEquals(sticker.id, found.second.id)
        assertNull(store.findByItemId("giphy_123"))
    }

    @Test
    fun `a corrupt manifest loses no images`() {
        val store = store()
        val pack = store.createPack("Cats")!!
        store.addSticker(pack.id, webp())
        val dir = store.packDir(pack.id)!!
        File(temp.root, "packs.json").writeText("{ not json")

        assertTrue(store().isEmpty())
        // The manifest is unreadable, so the directory is left for a repair,
        // not swept as an orphan.
        assertTrue(dir.isDirectory)
        assertEquals(1, dir.listFiles()!!.size)
    }
}
