package com.wasimaster.wmkeyboard.core.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ClipboardStoreTest {

    @Test fun addAndOrder() {
        val store = ClipboardStore(null)
        store.add("first", now = 1000)
        store.add("second", now = 2000)
        assertEquals(listOf("second", "first"), store.items(now = 3000).map { it.text })
    }

    @Test fun duplicateMovesToTop() {
        val store = ClipboardStore(null)
        store.add("a", now = 1000)
        store.add("b", now = 2000)
        store.add("a", now = 3000)
        val texts = store.items(now = 4000).map { it.text }
        assertEquals(listOf("a", "b"), texts)
        assertEquals(2, texts.size)
    }

    @Test fun pinnedFirstAndSurvivesExpiry() {
        val store = ClipboardStore(null, expiryMillis = 100)
        val old = store.add("keep me", now = 0)!!
        store.setPinned(old.id, true)
        store.add("fresh", now = 500)
        val texts = store.items(now = 550).map { it.text }
        assertEquals(listOf("keep me", "fresh"), texts)
    }

    @Test fun pinnedLastReversesGroupOrderNotRecency() {
        val store = ClipboardStore(null, pinnedLast = true)
        val pin = store.add("pinned", now = 1000)!!
        store.setPinned(pin.id, true)
        store.add("older", now = 2000)
        store.add("newer", now = 3000)
        // Unpinned group leads (newest-first), pinned trails.
        assertEquals(listOf("newer", "older", "pinned"), store.items(now = 4000).map { it.text })
    }

    @Test fun pinnedLastToggleFlipsLiveStore() {
        val store = ClipboardStore(null)
        val pin = store.add("pinned", now = 1000)!!
        store.setPinned(pin.id, true)
        store.add("fresh", now = 2000)
        assertEquals(listOf("pinned", "fresh"), store.items(now = 3000).map { it.text })
        store.pinnedLast = true
        assertEquals(listOf("fresh", "pinned"), store.items(now = 3000).map { it.text })
    }

    @Test fun unpinnedExpires() {
        val store = ClipboardStore(null, expiryMillis = 100)
        store.add("ephemeral", now = 0)
        assertTrue(store.items(now = 200).isEmpty())
    }

    @Test fun searchFiltersCaseInsensitive() {
        val store = ClipboardStore(null, expiryMillis = 0)
        store.add("Hello World", now = 1000)
        store.add("other", now = 2000)
        assertEquals(1, store.search("hello").size)
    }

    @Test fun clearUnpinnedKeepsPins() {
        val store = ClipboardStore(null)
        val pin = store.add("pinned", now = 1000)!!
        store.setPinned(pin.id, true)
        store.add("gone", now = 2000)
        store.clearUnpinned()
        assertEquals(listOf("pinned"), store.items(now = 3000).map { it.text })
    }

    private fun tempImage(dir: File, name: String): File =
        File(dir, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun addImageStoresKindAndPath() {
        val dir = Files.createTempDirectory("clips").toFile()
        val store = ClipboardStore(null, imagesDir = dir)
        val file = tempImage(dir, "a.png")
        val item = store.addImage(file, "image/png", now = 1000)!!
        assertEquals(ClipKind.IMAGE, item.kind)
        assertEquals(file.absolutePath, item.imagePath)
        assertEquals("image/png", item.mimeType)
    }

    @Test fun removingImageItemDeletesFile() {
        val dir = Files.createTempDirectory("clips").toFile()
        val store = ClipboardStore(null, imagesDir = dir)
        val file = tempImage(dir, "a.png")
        val item = store.addImage(file, "image/png", now = 1000)!!
        store.remove(item.id)
        assertFalse(file.exists())
    }

    @Test fun expiredImageDeletesFile() {
        val dir = Files.createTempDirectory("clips").toFile()
        val store = ClipboardStore(null, expiryMillis = 100, imagesDir = dir)
        val file = tempImage(dir, "a.png")
        store.addImage(file, "image/png", now = 0)
        assertTrue(store.items(now = 200).isEmpty())
        assertFalse(file.exists())
    }

    @Test fun htmlClipKeepsMarkupAndDedupes() {
        val store = ClipboardStore(null)
        store.add("hello", now = 1000)
        val item = store.addHtml("hello", "<b>hello</b>", now = 2000)!!
        assertEquals(ClipKind.HTML, item.kind)
        assertEquals("<b>hello</b>", item.htmlText)
        assertEquals(1, store.items(now = 3000).size)
    }

    @Test fun latestTextSkipsImages() {
        val dir = Files.createTempDirectory("clips").toFile()
        val store = ClipboardStore(null, imagesDir = dir)
        store.add("words", now = 1000)
        store.addImage(tempImage(dir, "a.png"), "image/png", now = 2000)
        assertEquals("words", store.latestText(now = 3000))
    }

    @Test fun blankSearchReturnsEverything() {
        val dir = Files.createTempDirectory("clips").toFile()
        val store = ClipboardStore(null, expiryMillis = 0, imagesDir = dir)
        store.addImage(tempImage(dir, "a.png"), "image/png", now = 1000)
        store.add("hello", now = 2000)
        assertEquals(2, store.search("").size)
    }

    @Test fun searchOnTextSkipsImages() {
        val dir = Files.createTempDirectory("clips").toFile()
        val store = ClipboardStore(null, expiryMillis = 0, imagesDir = dir)
        store.addImage(tempImage(dir, "a.png"), "image/png", now = 1000)
        store.add("hello", now = 2000)
        assertEquals(listOf("hello"), store.search("hell").map { it.text })
    }

    /** An image clip has no text of its own; its kind and format stand in. */
    @Test fun searchFindsImagesByKindAndFormat() {
        val dir = Files.createTempDirectory("clips").toFile()
        val store = ClipboardStore(null, expiryMillis = 0, imagesDir = dir)
        store.addImage(tempImage(dir, "a.png"), "image/png", now = 1000)
        store.add("hello", now = 2000)
        assertEquals(1, store.search("image").size)
        assertEquals(1, store.search("png").size)
    }

    @Test fun searchMatchesFileNameAndSourceApp() {
        val store = ClipboardStore(null, expiryMillis = 0)
        store.addUri("content://x/1", "report.pdf", "application/pdf", isDirectory = false, now = 1000)
        store.add("plain", sourceApp = "Firefox", now = 2000)
        assertEquals(listOf("report.pdf"), store.search("report").map { it.text })
        assertEquals(listOf("plain"), store.search("firefox").map { it.text })
    }

    @Test fun legacySnapshotWithoutNewFieldsLoads() {
        val file = Files.createTempFile("history", ".json").toFile()
        file.writeText("""{"items":[{"id":1,"text":"old","timestamp":1000}]}""")
        val store = ClipboardStore(file, expiryMillis = 0)
        val items = store.items(now = 2000)
        assertEquals(1, items.size)
        assertEquals(ClipKind.TEXT, items[0].kind)
        assertNull(items[0].imagePath)
    }

    @Test fun orphanImageFilesCleanedOnLoad() {
        val dir = Files.createTempDirectory("clips").toFile()
        val orphan = tempImage(dir, "orphan.png")
        val storageFile = Files.createTempFile("history", ".json").toFile()

        val first = ClipboardStore(storageFile, expiryMillis = 0, imagesDir = dir)
        val kept = tempImage(dir, "kept.png")
        first.addImage(kept, "image/png", now = 1000)
        first.save()

        val second = ClipboardStore(storageFile, expiryMillis = 0, imagesDir = dir)
        assertNotNull(second)
        assertTrue(kept.exists())
        assertFalse(orphan.exists())
    }

    @Test fun maxItemsCapsUnpinnedHistory() {
        val store = ClipboardStore(null, expiryMillis = 0, maxItems = 3)
        repeat(6) { store.add("clip $it", now = 1000L + it) }
        val texts = store.items(now = 9000).map { it.text }
        assertEquals(listOf("clip 5", "clip 4", "clip 3"), texts)
    }

    @Test fun pinnedItemsDoNotCountAgainstTheCap() {
        val store = ClipboardStore(null, expiryMillis = 0, maxItems = 2)
        val pinned = store.add("keep me", now = 1000)!!
        store.setPinned(pinned.id, true)
        repeat(4) { store.add("clip $it", now = 2000L + it) }
        val texts = store.items(now = 9000).map { it.text }
        assertEquals(listOf("keep me", "clip 3", "clip 2"), texts)
    }

    @Test fun sensitiveClipsExpireOnTheirOwnShorterTimer() {
        // History keeps everything; the sensitive leash still has to hold.
        val store = ClipboardStore(null, expiryMillis = 0, sensitiveExpiryMillis = 500)
        store.add("secret", now = 1000, sensitive = true)
        store.add("ordinary", now = 1000)
        assertEquals(listOf("ordinary", "secret"), store.items(now = 1400).map { it.text })
        assertEquals(listOf("ordinary"), store.items(now = 1600).map { it.text })
    }

    @Test fun pinningKeepsASensitiveClip() {
        val store = ClipboardStore(null, expiryMillis = 0, sensitiveExpiryMillis = 500)
        val secret = store.add("secret", now = 1000, sensitive = true)!!
        store.setPinned(secret.id, true)
        assertEquals(listOf("secret"), store.items(now = 99_000).map { it.text })
    }

    @Test fun reCopyingOnlyEverTightensSensitivity() {
        val store = ClipboardStore(null, expiryMillis = 0)
        store.add("hunter2", now = 1000, sensitive = true)
        val again = store.add("hunter2", now = 2000, sensitive = false)
        assertTrue(again!!.sensitive)
    }

    @Test fun videoUrisBecomeVideoClips() {
        val store = ClipboardStore(null, expiryMillis = 0)
        val clip = store.addUri(
            uriString = "content://media/external/video/1",
            displayName = "holiday.mp4",
            mimeType = "video/mp4",
            isDirectory = false,
            size = 4096,
            durationMs = 64_000,
            now = 1000,
        )
        assertEquals(ClipKind.VIDEO, clip!!.kind)
        assertEquals(64_000, clip.durationMs)
        assertTrue(clip.kind.isUriBacked)
        assertFalse(clip.kind.isTextual)
    }
}
