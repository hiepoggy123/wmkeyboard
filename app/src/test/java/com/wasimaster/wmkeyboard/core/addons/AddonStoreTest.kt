package com.wasimaster.wmkeyboard.core.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AddonStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(dir: File? = File(temp.root, "addons")) = AddonStore(dir?.apply { mkdirs() })

    private val githubManifest =
        "https://raw.githubusercontent.com/user/repo/HEAD/wmkeyboard-repo.json"

    // ---- repositories --------------------------------------------------

    @Test
    fun `adding a repository stores its resolved manifest URL`() {
        val added = store().addRepo("https://github.com/user/repo")
        assertNotNull(added)
        assertEquals(githubManifest, added!!.manifestUrl)
        // The pasted form is kept too, because that is what the user recognises.
        assertEquals("https://github.com/user/repo", added.url)
    }

    @Test
    fun `an unresolvable URL is not added`() {
        val s = store()
        assertNull(s.addRepo("http://example.com/wmkeyboard-repo.json"))
        assertNull(s.addRepo(""))
        assertTrue(s.repos().isEmpty())
    }

    @Test
    fun `adding the same repository twice is idempotent`() {
        val s = store()
        // Two spellings of the same place — pasting either, or following a deep
        // link to a repository already added, must not produce a duplicate.
        s.addRepo("https://github.com/user/repo")
        s.addRepo(githubManifest)
        assertEquals(1, s.repos().size)
    }

    @Test
    fun `repositories survive a reload`() {
        val dir = File(temp.root, "addons")
        store(dir).addRepo("https://github.com/user/repo")
        assertEquals(1, AddonStore(dir).repos().size)
    }

    @Test
    fun `removing a repository leaves its installs alone`() {
        // An installed theme is still the user's theme; removing the source
        // only stops the update check.
        val s = store()
        val ref = s.addRepo("https://github.com/user/repo")!!
        s.markInstalled("repo/theme", InstalledAddon(version = "1.0.0", type = AddonType.Theme))
        s.removeRepo(ref.manifestUrl)
        assertTrue(s.repos().isEmpty())
        assertEquals(1, s.installed().size)
    }

    @Test
    fun `the repository cap is enforced`() {
        val s = store()
        repeat(AddonStore.MAX_REPOS + 5) { s.addRepo("https://example.com/r$it") }
        assertEquals(AddonStore.MAX_REPOS, s.repos().size)
    }

    @Test
    fun `a cached manifest is stored against its repository`() {
        val s = store()
        val ref = s.addRepo("https://github.com/user/repo")!!
        s.cacheManifest(ref.manifestUrl, """{"format":"wmkeyboard-repo"}""")
        assertEquals("""{"format":"wmkeyboard-repo"}""", s.repo(ref.manifestUrl)?.cachedManifest)
    }

    // ---- seeding -------------------------------------------------------

    @Test
    fun `seeding adds the sample repository once`() {
        val dir = File(temp.root, "addons")
        val s = store(dir)
        s.seedIfNeeded()
        s.seedIfNeeded()
        assertEquals(1, s.repos().size)
        assertTrue(s.repos().single().seeded)
    }

    @Test
    fun `removing the seeded repository sticks`() {
        // Helpfully re-adding it on the next launch reads as the app ignoring
        // the user, which is why the marker is a file rather than a check for
        // the repository's presence.
        val dir = File(temp.root, "addons")
        val first = store(dir)
        first.seedIfNeeded()
        first.removeRepo(first.repos().single().manifestUrl)

        val second = AddonStore(dir)
        second.seedIfNeeded()
        assertTrue("the seed came back after being removed", second.repos().isEmpty())
    }

    // ---- installs ------------------------------------------------------

    @Test
    fun `installs round-trip through disk`() {
        val dir = File(temp.root, "addons")
        val record = InstalledAddon(
            version = "1.2.0",
            type = AddonType.IconPack,
            localRef = "pack_1",
            name = "Lucide",
            repoName = "Sample",
        )
        store(dir).markInstalled("sample/lucide", record)

        val reloaded = AddonStore(dir).installed("sample/lucide")
        assertEquals(record, reloaded)
    }

    @Test
    fun `uninstalling forgets the record`() {
        val s = store()
        s.markInstalled("a/b", InstalledAddon(version = "1.0.0", type = AddonType.Theme))
        s.markUninstalled("a/b")
        assertNull(s.installed("a/b"))
    }

    @Test
    fun `reconcile drops records whose local target is gone`() {
        // The user deleted the theme from the Themes screen. Without this the
        // addon would keep claiming to be installed and offer an Update for
        // something that no longer exists.
        val s = store()
        s.markInstalled("a/kept", InstalledAddon(version = "1.0.0", localRef = "kept"))
        s.markInstalled("a/gone", InstalledAddon(version = "1.0.0", localRef = "gone"))
        s.reconcileInstalled { it.localRef == "kept" }
        assertEquals(setOf("a/kept"), s.installed().keys)
    }

    // ---- direct boot ---------------------------------------------------

    @Test
    fun `with no directory the store reads as empty and writes nothing`() {
        // Before the first unlock there is no filesDir to point at. Reading as
        // empty is right; throwing, or pretending to persist, is not.
        val s = store(dir = null)
        assertTrue(s.repos().isEmpty())
        assertNull(s.addRepo("https://github.com/user/repo")?.let { s.repo(it.manifestUrl) })
        s.markInstalled("a/b", InstalledAddon(version = "1.0.0"))
        s.seedIfNeeded()
        assertTrue("nothing should have been written", temp.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `attaching a directory after unlock picks up what is on disk`() {
        val dir = File(temp.root, "addons").apply { mkdirs() }
        store(dir).addRepo("https://github.com/user/repo")

        val locked = AddonStore(null)
        assertTrue(locked.repos().isEmpty())
        locked.attach(dir)
        assertEquals(1, locked.repos().size)
    }

    @Test
    fun `an unreadable manifest is survived rather than thrown`() {
        val dir = File(temp.root, "addons").apply { mkdirs() }
        File(dir, "repos.json").writeText("{ this is not json")
        File(dir, "installed.json").writeText("also not json")
        val s = AddonStore(dir)
        assertTrue(s.repos().isEmpty())
        assertTrue(s.installed().isEmpty())
        // And the store is still usable afterwards.
        assertNotNull(s.addRepo("https://github.com/user/repo"))
    }

    @Test
    fun `the revision flow advances on every change`() {
        val s = store()
        val before = s.revision.value
        s.addRepo("https://github.com/user/repo")
        assertFalse(s.revision.value == before)
    }
}
