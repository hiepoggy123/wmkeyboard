package com.wasimaster.wmkeyboard.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ranking and seeding from the device's own locale and region signals. */
class LanguageSuggestionsTest {

    private fun signals(locales: List<String> = emptyList(), regions: List<String> = emptyList()) =
        DeviceLanguageSignals(systemLocales = locales, regionCodes = regions)

    // ---- the region table itself ----

    @Test
    fun `every region language is a real registry entry with a layout`() {
        for ((region, ids) in REGION_LANGUAGES) {
            for (id in ids) {
                val language = LanguageRegistry.byId(id)
                assertEquals("$region lists unknown language id '$id'", id, language.id)
                assertTrue(
                    "$region lists '$id', which has no layout to enable",
                    language.layoutIds.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `no region lists the same language twice`() {
        for ((region, ids) in REGION_LANGUAGES) {
            assertEquals("$region repeats a language", ids.size, ids.distinct().size)
        }
    }

    @Test
    fun `region codes are uppercase ISO-3166 alpha-2`() {
        for (region in REGION_LANGUAGES.keys) {
            assertEquals("'$region' is not alpha-2", 2, region.length)
            assertEquals("'$region' is not uppercase", region.uppercase(), region)
        }
    }

    @Test
    fun `English is never listed in a region, it arrives by other routes`() {
        for ((region, ids) in REGION_LANGUAGES) {
            assertFalse("$region lists English", "en" in ids)
        }
    }

    // ---- ranking ----

    @Test
    fun `system languages come first, in the phone's own order`() {
        val out = LanguageSuggestions.suggest(signals(locales = listOf("fr-FR", "de-DE")))
        assertEquals(listOf("fr", "de"), out.map { it.language.id })
        assertTrue(out.all { it.reason == SuggestionReason.SYSTEM_LANGUAGE })
    }

    @Test
    fun `region languages follow the system ones and carry their region`() {
        val out = LanguageSuggestions.suggest(signals(locales = listOf("en-US"), regions = listOf("BD")))
        assertEquals("en", out.first().language.id)
        val bengali = out.first { it.language.id == "bn" }
        assertEquals(SuggestionReason.REGION, bengali.reason)
        assertEquals("BD", bengali.regionCode)
    }

    @Test
    fun `a language already enabled is never suggested`() {
        val out = LanguageSuggestions.suggest(
            signals(locales = listOf("bn-BD"), regions = listOf("BD")),
            exclude = setOf("bn"),
        )
        assertFalse(out.any { it.language.id == "bn" })
    }

    @Test
    fun `a language reached by two signals appears once, under the stronger one`() {
        val out = LanguageSuggestions.suggest(signals(locales = listOf("bn"), regions = listOf("BD")))
        assertEquals(1, out.count { it.language.id == "bn" })
        assertEquals(SuggestionReason.SYSTEM_LANGUAGE, out.first { it.language.id == "bn" }.reason)
    }

    @Test
    fun `lowercase region codes still match`() {
        val out = LanguageSuggestions.suggest(signals(regions = listOf("jp")))
        assertTrue(out.any { it.language.id == "ja" })
    }

    @Test
    fun `unknown locales and regions are skipped, not surfaced as Unknown`() {
        val out = LanguageSuggestions.suggest(signals(locales = listOf("zz"), regions = listOf("ZZ")))
        assertFalse(out.any { it.language.id == LanguageRegistry.GENERIC.id })
    }

    @Test
    fun `a device that says nothing useful still gets English`() {
        val out = LanguageSuggestions.suggest(signals())
        assertEquals(listOf("en"), out.map { it.language.id })
        assertEquals(SuggestionReason.FALLBACK, out.single().reason)
    }

    @Test
    fun `the fallback is skipped when English is already enabled`() {
        assertTrue(LanguageSuggestions.suggest(signals(), exclude = setOf("en")).isEmpty())
    }

    @Test
    fun `only the first region that offers anything is read`() {
        // A phone set to English (United Kingdom) sitting in Bangladesh: the
        // SIM says BD, the locale says GB. Welsh and Irish are not the answer.
        val out = LanguageSuggestions.suggest(
            signals(locales = listOf("en-GB"), regions = listOf("BD", "GB")),
        )
        assertTrue(out.any { it.language.id == "bn" })
        assertFalse(out.any { it.language.id == "cy" })
        assertFalse(out.any { it.language.id == "ga" })
    }

    @Test
    fun `a region with nothing to offer falls through to the next`() {
        // ZZ is in no table, so the next region still gets its turn.
        val out = LanguageSuggestions.suggest(signals(regions = listOf("ZZ", "JP")))
        assertTrue(out.any { it.language.id == "ja" })
    }

    @Test
    fun `a romanized variant is offered beside its own language`() {
        val out = LanguageSuggestions.suggest(signals(locales = listOf("bn-BD")))
        assertEquals(listOf("bn", "bn_rom"), out.take(2).map { it.language.id })
        assertEquals(SuggestionReason.SYSTEM_LANGUAGE, out[1].reason)
    }

    @Test
    fun `a romanized variant follows its language from the region table too`() {
        val out = LanguageSuggestions.suggest(signals(regions = listOf("BD")))
        val ids = out.map { it.language.id }
        assertEquals(ids.indexOf("bn") + 1, ids.indexOf("bn_rom"))
        assertEquals("BD", out.first { it.language.id == "bn_rom" }.regionCode)
    }

    @Test
    fun `an already-enabled romanized variant is not offered again`() {
        val out = LanguageSuggestions.suggest(
            signals(locales = listOf("bn-BD")),
            exclude = setOf("bn_rom"),
        )
        assertFalse(out.any { it.language.id == "bn_rom" })
    }

    @Test
    fun `a language with no romanized variant is offered alone`() {
        val out = LanguageSuggestions.suggest(signals(locales = listOf("fr-FR")))
        assertEquals(listOf("fr"), out.map { it.language.id })
    }

    @Test
    fun `the limit is honoured`() {
        val out = LanguageSuggestions.suggest(signals(regions = listOf("IN")), limit = 3)
        assertEquals(3, out.size)
    }

    // ---- seeding ----

    @Test
    fun `seeding starts a phone in its own languages, plus English`() {
        val ids = LanguageSuggestions.seedLayoutIds(signals(locales = listOf("bn-BD")))
        val languages = ids.map { LanguageRegistry.languageOf(it).id }
        assertEquals(listOf("bn", "en"), languages)
    }

    @Test
    fun `an English phone seeds English alone, not English twice`() {
        val ids = LanguageSuggestions.seedLayoutIds(signals(locales = listOf("en-GB")))
        assertEquals(listOf("en"), ids.map { LanguageRegistry.languageOf(it).id })
    }

    @Test
    fun `seeding ignores the region, which is a reason to offer and not to enable`() {
        val ids = LanguageSuggestions.seedLayoutIds(signals(regions = listOf("BD")))
        assertEquals(listOf("en"), ids.map { LanguageRegistry.languageOf(it).id })
    }

    @Test
    fun `seeding never hands over a switch cycle longer than the limit`() {
        val ids = LanguageSuggestions.seedLayoutIds(
            signals(locales = listOf("hi", "bn", "ta", "te", "mr", "gu")),
        )
        assertTrue("seeded ${ids.size} layouts", ids.size <= 4)
    }

    @Test
    fun `seeding always produces something enableable`() {
        assertTrue(LanguageSuggestions.seedLayoutIds(signals()).isNotEmpty())
        assertTrue(LanguageSuggestions.seedLayoutIds(signals(locales = listOf("zz"))).isNotEmpty())
    }
}
