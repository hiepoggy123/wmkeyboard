package com.wasimaster.wmkeyboard.core.endpoints

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceEndpointsTest {

    @Test
    fun idsAreUniqueAndDefaultsAreBareHttpsBases() {
        assertEquals(ServiceEndpoint.entries.size, ServiceEndpoint.entries.map { it.id }.toSet().size)
        assertEquals(ServiceRepo.entries.size, ServiceRepo.entries.map { it.id }.toSet().size)
        for (endpoint in ServiceEndpoint.entries) {
            assertTrue(endpoint.default, endpoint.default.startsWith("https://"))
            assertFalse(endpoint.default, endpoint.default.endsWith("/"))
            assertTrue(endpoint.default, ServiceEndpoints.isUsableBase(endpoint.default))
        }
        for (repo in ServiceRepo.entries) assertTrue(repo.id, repo.default.isComplete)
    }

    @Test
    fun onlyTheFdroidBuildReadsAnOverride() {
        val url = "https://meteo.home.example/"
        assertEquals("https://api.open-meteo.com", ServiceEndpoints.resolveBase(ServiceEndpoint.OPEN_METEO, url, fdroid = false))
        assertEquals("https://meteo.home.example", ServiceEndpoints.resolveBase(ServiceEndpoint.OPEN_METEO, url, fdroid = true))
    }

    @Test
    fun anUnusableOverrideFallsBackToTheDefault() {
        for (bad in listOf(null, "", "  ", "meteo.home.example", "ftp://x", "https://", "https:///x", "https://a b")) {
            assertEquals(bad.toString(), "https://api.open-meteo.com", ServiceEndpoints.resolveBase(ServiceEndpoint.OPEN_METEO, bad, fdroid = true))
        }
        // A local server without a certificate is a real case.
        assertEquals("http://192.168.1.5:8080", ServiceEndpoints.resolveBase(ServiceEndpoint.OPEN_METEO, " http://192.168.1.5:8080/ ", fdroid = true))
    }

    @Test
    fun repositoriesNeedAWholeLocation() {
        val mirror = RepoLocation(GitForge.FORGEJO, "codeberg.org", "me", "wmkeyboard-data")
        assertEquals(mirror, ServiceEndpoints.resolveRepo(ServiceRepo.DATA, mirror, fdroid = true))
        assertEquals(ServiceRepo.DATA.default, ServiceEndpoints.resolveRepo(ServiceRepo.DATA, mirror, fdroid = false))
        assertEquals(ServiceRepo.DATA.default, ServiceEndpoints.resolveRepo(ServiceRepo.DATA, mirror.copy(repo = ""), fdroid = true))
        assertEquals(ServiceRepo.DATA.default, ServiceEndpoints.resolveRepo(ServiceRepo.DATA, null, fdroid = true))
    }

    @Test
    fun theDefaultsAreTheAddressesUsedBeforeOverridesExisted() {
        assertEquals(
            "https://raw.githubusercontent.com/wasi-master/wmkeyboard-data/HEAD/vocab/en/x.wmvocab.json.gz",
            ServiceRepo.DATA.default.rawUrl("vocab/en/x.wmvocab.json.gz"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/wasi-master/wmkeyboard-addon-repository/HEAD/fonts/noto-color-emoji.ttf",
            ServiceRepo.ADDONS.default.rawUrl("fonts/noto-color-emoji.ttf"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/espanso/hub/main/packages/a/1.0.0/package.yml",
            ServiceRepo.ESPANSO_HUB.default.rawUrl("packages/a/1.0.0/package.yml"),
        )
        assertEquals("https://bn.wikipedia.org", ServiceEndpoints.expandLang(ServiceEndpoint.WIKIPEDIA.default, "BN"))
        assertEquals("https://en.wikipedia.org", ServiceEndpoints.expandLang(ServiceEndpoint.WIKIPEDIA.default, ""))
        assertEquals("https://wiki.home.example", ServiceEndpoints.expandLang("https://wiki.home.example", "bn"))
    }

    @Test
    fun locationsSurviveStorage() {
        val loc = RepoLocation(GitForge.GITLAB, "gitlab.example", "group/sub", "data", "stable")
        assertEquals(loc, repoLocationFromFields(loc.toFields()))
        assertNull(repoLocationFromFields(mapOf("forge" to "mercurial")))
    }
}
