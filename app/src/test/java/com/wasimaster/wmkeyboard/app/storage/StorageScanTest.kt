package com.wasimaster.wmkeyboard.app.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The measuring half of the Storage screen.
 *
 * The screen's whole claim is that its total is the same number the system's
 * App info page shows, so the arithmetic underneath it — block rounding, the
 * residual, what counts and what does not — is worth pinning down. None of it
 * needs Android: it is file walking and subtraction.
 */
class StorageScanTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val block = 4096L

    @Test
    fun `a file costs whole blocks, not its length`() {
        val root = temp.newFolder("tiny")
        File(root, "one.txt").writeText("x")
        File(root, "two.txt").writeText("y")
        // Two files at a block each, plus a block for the directory itself.
        assertEquals(block * 3, diskUsage(root, block))
    }

    @Test
    fun `a file just over a block boundary costs two`() {
        val root = temp.newFolder("edge")
        File(root, "big.bin").writeBytes(ByteArray(block.toInt() + 1))
        assertEquals(block * 3, diskUsage(root, block))
    }

    @Test
    fun `an empty file costs nothing beyond its directory`() {
        val root = temp.newFolder("empty")
        File(root, "nothing.bin").createNewFile()
        assertEquals(block, diskUsage(root, block))
    }

    @Test
    fun `a missing path measures zero`() {
        assertEquals(0L, diskUsage(File(temp.root, "not-here"), block))
    }

    @Test
    fun `nested directories each cost a block`() {
        val root = temp.newFolder("nested")
        val inner = File(root, "a/b").apply { mkdirs() }
        File(inner, "leaf.txt").writeText("z")
        // root + a + b + one file.
        assertEquals(block * 4, diskUsage(root, block))
    }

    /**
     * `dataDir/lib` is a link into `/data/app`, and the bytes behind it are
     * already counted as the app's code. Following it would report them twice.
     */
    @Test
    fun `a symlink out of the tree is not counted`() {
        val outside = temp.newFolder("outside")
        File(outside, "payload.bin").writeBytes(ByteArray(block.toInt() * 4))
        val root = temp.newFolder("linked")
        Files.createSymbolicLink(File(root, "lib").toPath(), outside.toPath())
        assertEquals(block, diskUsage(root, block))
    }

    @Test
    fun `a symlink is not walked into`() {
        val outside = temp.newFolder("target")
        File(outside, "deep").mkdirs()
        File(outside, "deep/leaf.bin").writeBytes(ByteArray(block.toInt()))
        val root = temp.newFolder("walker")
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        assertTrue(filesUnder(root).isEmpty())
    }

    @Test
    fun `filesUnder flattens the whole tree`() {
        val root = temp.newFolder("flat")
        File(root, "a").mkdirs()
        File(root, "a/one.txt").writeText("1")
        File(root, "a/two.txt").writeText("2")
        File(root, "three.txt").writeText("3")
        assertEquals(
            listOf("one.txt", "three.txt", "two.txt"),
            filesUnder(root).map { it.name }.sorted(),
        )
    }

    // ---- the registry ----

    @Test
    fun `no two categories claim the same directory`() {
        val roots = roots()
        val seen = HashMap<String, String>()
        for (category in StorageCategories.all) {
            for (path in category.paths(roots)) {
                val other = seen.put(path.absolutePath, category.id)
                assertEquals("${path.absolutePath} is claimed twice", null, other)
            }
        }
    }

    @Test
    fun `every category has a distinct id`() {
        val ids = StorageCategories.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `the residual rows absorb what no category names`() {
        val roots = roots()
        val named = StorageCategories.all.filterNot { it.synthetic }
        val cache = named.first { it.group == StorageGroup.CACHE }
        val data = named.first { it.group == StorageGroup.PERSONAL }
        val report = StorageReport(
            appBytes = 100,
            dataBytes = 1000,
            cacheBytes = 400,
            sizes = mapOf(
                cache.id to CategorySize(250, 0),
                data.id to CategorySize(300, 0),
            ),
        ).withResiduals(StorageCategories.all)

        assertEquals(150L, report.bytesOf(StorageCategories.OTHER_CACHE))
        // userDataBytes is 1000 - 400 = 600, of which one category claims 300.
        assertEquals(300L, report.bytesOf(StorageCategories.OTHER_DATA))
        assertEquals(100L, report.bytesOf(StorageCategories.APP_PACKAGE))
        assertEquals(roots.blockSize, block)
    }

    /**
     * Our walk rounds up to whole blocks and the system may not, so a category
     * can overshoot the total it is a share of. A negative row would be worse
     * than a slightly optimistic zero.
     */
    @Test
    fun `a residual never goes negative`() {
        val cache = StorageCategories.all.first { !it.synthetic && it.group == StorageGroup.CACHE }
        val report = StorageReport(
            dataBytes = 100,
            cacheBytes = 100,
            sizes = mapOf(cache.id to CategorySize(999_999, 0)),
        ).withResiduals(StorageCategories.all)

        assertEquals(0L, report.bytesOf(StorageCategories.OTHER_CACHE))
        assertEquals(0L, report.bytesOf(StorageCategories.OTHER_DATA))
    }

    @Test
    fun `the totals follow the system's own arithmetic`() {
        val report = StorageReport(appBytes = 30, dataBytes = 100, cacheBytes = 40)
        // What the App info page calls Total, and what it calls User data.
        assertEquals(130L, report.totalBytes)
        assertEquals(60L, report.userDataBytes)
    }

    private fun roots(): StorageRoots {
        val base = temp.newFolder()
        fun dir(name: String) = File(base, name).apply { mkdirs() }
        return StorageRoots(
            files = dir("files"),
            cache = dir("cache"),
            codeCache = dir("code_cache"),
            deFiles = dir("de/files"),
            deCache = dir("de/cache"),
            prefs = dir("shared_prefs"),
            dePrefs = dir("de/shared_prefs"),
            dataDir = base,
            deDataDir = dir("de"),
            blockSize = block,
            unlocked = true,
        )
    }
}
