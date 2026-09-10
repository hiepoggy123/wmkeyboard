package com.wasimaster.wmkeyboard.core.endpoints

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitForgeTest {

    private fun loc(forge: GitForge, host: String = forge.defaultHost, owner: String = "o", repo: String = "r", ref: String = "") =
        RepoLocation(forge, host, owner, repo, ref)

    @Test
    fun githubIsTheAddressTheCatalogsUseToday() {
        val data = RepoLocation(GitForge.GITHUB, "github.com", "wasi-master", "wmkeyboard-data")
        assertEquals(
            "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/data/en/en_50k.txt.gz",
            data.rawUrl("data/en/en_50k.txt.gz"),
        )
    }

    @Test
    fun githubEnterpriseKeepsRawFilesOnTheInstance() {
        assertEquals("https://git.corp.example/o/r/raw/HEAD/x.json", loc(GitForge.GITHUB, host = "git.corp.example").rawUrl("x.json"))
    }

    @Test
    fun forgejoSpellsTheRefKind() {
        assertEquals("https://codeberg.org/o/r/raw/branch/main/a/b.txt", loc(GitForge.FORGEJO).rawUrl("/a/b.txt"))
        val sha = "0123456789abcdef0123456789abcdef01234567"
        assertEquals("https://codeberg.org/o/r/raw/commit/$sha/a", loc(GitForge.FORGEJO, ref = sha).rawUrl("a"))
    }

    @Test
    fun gitlabTakesAGroupPathAsTheOwner() {
        assertEquals("https://gitlab.com/group/sub/r/-/raw/HEAD/f", loc(GitForge.GITLAB, owner = "group/sub").rawUrl("f"))
    }

    @Test
    fun sourcehutOwnersCarryTheirTilde() {
        assertEquals("https://git.sr.ht/~o/r/blob/main/f", loc(GitForge.SOURCEHUT).rawUrl("f"))
        assertEquals("https://git.sr.ht/~o/r/blob/main/f", loc(GitForge.SOURCEHUT, owner = "~o").rawUrl("f"))
    }

    @Test
    fun bitbucketAndAPlainFolder() {
        assertEquals("https://bitbucket.org/o/r/raw/main/f", loc(GitForge.BITBUCKET).rawUrl("f"))
        val folder = RepoLocation(GitForge.FOLDER, "https://mirror.example.org/wm/")
        assertEquals("https://mirror.example.org/wm/data/x.gz", folder.rawUrl("data/x.gz"))
        assertTrue(folder.isComplete)
    }

    @Test
    fun aHostTypedWithItsSchemeStillWorks() {
        assertEquals("https://codeberg.org/o/r/raw/branch/dev/f", loc(GitForge.FORGEJO, host = "https://codeberg.org/", ref = "dev").rawUrl("f"))
    }

    @Test
    fun anIncompleteLocationSaysSo() {
        assertFalse(loc(GitForge.FORGEJO, owner = "").isComplete)
        assertFalse(loc(GitForge.FORGEJO, repo = " ").isComplete)
        assertFalse(loc(GitForge.GITLAB, host = "gitlab.com/group").isComplete)
        assertFalse(RepoLocation(GitForge.FOLDER, "http://mirror.example.org/").isComplete)
        assertTrue(loc(GitForge.GITHUB).isComplete)
    }

    @Test
    fun listingsExistWhereTheForgeHasOne() {
        assertEquals("https://api.github.com/repos/espanso/hub/contents/packages/x", loc(GitForge.GITHUB, owner = "espanso", repo = "hub").directoryListingUrl("packages/x"))
        assertEquals("https://api.github.com/repos/o/r/contents/p?ref=main", loc(GitForge.GITHUB, ref = "main").directoryListingUrl("p"))
        assertEquals("https://codeberg.org/api/v1/repos/o/r/contents/p?ref=main", loc(GitForge.FORGEJO).directoryListingUrl("/p/"))
        assertEquals(
            "https://gitlab.com/api/v4/projects/group%2Fsub%2Fr/repository/tree?path=packages%2Fx&per_page=100",
            loc(GitForge.GITLAB, owner = "group/sub").directoryListingUrl("packages/x"),
        )
        assertNull(loc(GitForge.SOURCEHUT).directoryListingUrl("p"))
        assertNull(loc(GitForge.BITBUCKET).directoryListingUrl("p"))
        assertTrue(RepoLocation.isDirectoryType("dir"))
        assertTrue(RepoLocation.isDirectoryType("tree"))
        assertFalse(RepoLocation.isDirectoryType("file"))
    }

    @Test
    fun pageAddressesReadOnEveryForge() {
        fun read(url: String) = RepoLocation.fromPageUrl(url)
        assertEquals(RepoLocation(GitForge.GITHUB, "github.com", "o", "r", "dev") to "sub/dir", read("https://github.com/o/r/tree/dev/sub/dir"))
        assertEquals(RepoLocation(GitForge.GITHUB, "github.com", "o", "r") to "", read("github.com/o/r.git"))
        assertEquals(RepoLocation(GitForge.FORGEJO, "codeberg.org", "o", "r", "main") to "", read("https://codeberg.org/o/r/src/branch/main"))
        assertEquals(RepoLocation(GitForge.FORGEJO, "git.home.example", "o", "r", "main") to "x", read("https://git.home.example/o/r/src/branch/main/x/"))
        assertEquals(RepoLocation(GitForge.FORGEJO, "codeberg.org", "o", "r") to "", read("https://codeberg.org/o/r"))
        assertEquals(RepoLocation(GitForge.GITLAB, "gitlab.example", "group/sub", "r", "HEAD") to "", read("https://gitlab.example/group/sub/r/-/tree/HEAD"))
        assertEquals(RepoLocation(GitForge.SOURCEHUT, "git.sr.ht", "o", "r", "master") to "d", read("https://git.sr.ht/~o/r/tree/master/d"))
        assertEquals(RepoLocation(GitForge.BITBUCKET, "bitbucket.org", "o", "r", "main") to "", read("https://bitbucket.org/o/r/src/main/"))
        // An unknown host with no path shape to go on is not guessed at.
        assertNull(read("https://git.home.example/o/r"))
        assertNull(read("http://github.com/o/r"))
        assertNull(read("https://github.com/o"))
    }
}
