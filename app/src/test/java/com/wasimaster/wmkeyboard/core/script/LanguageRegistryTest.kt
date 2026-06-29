package com.wasimaster.wmkeyboard.core.script

import com.wasimaster.wmkeyboard.core.layout.BuiltInLayouts
import com.wasimaster.wmkeyboard.core.layout.LEGACY_MODE_LANG
import com.wasimaster.wmkeyboard.core.layout.LEGACY_MODE_LAYOUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pins the registry that replaced the `InputMode`/`KeyboardLanguage` enums: every
 * language resolves to a real script, its locale parses, and the migration maps
 * cover every name the old enum could have stored — the two seams an existing
 * install passes through on upgrade.
 */
class LanguageRegistryTest {

    /** The nine former `InputMode` values. If one is missing from a map, an upgrade loses it. */
    private val legacyModeNames = listOf(
        "ENGLISH", "AZERTY", "DVORAK", "AVRO", "PROBHAT", "JATIYA", "FRENCH", "GERMAN", "SPANISH",
    )

    @Test
    fun `every language uses a registered script`() {
        for (lang in LanguageRegistry.all) {
            assertTrue(
                "language ${lang.id} names script ${lang.script} with no ScriptDef",
                ScriptRegistry.isRegistered(lang.script),
            )
        }
    }

    @Test
    fun `every language locale tag parses to a language`() {
        for (lang in LanguageRegistry.all) {
            val locale = Locale.forLanguageTag(lang.localeTag)
            assertTrue(
                "language ${lang.id} has an unparseable locale tag '${lang.localeTag}'",
                locale.language.isNotEmpty(),
            )
        }
    }

    @Test
    fun `every language layout id is a real built-in`() {
        val builtInIds = BuiltInLayouts.all.mapTo(HashSet()) { it.id }
        for (lang in LanguageRegistry.all) {
            for (layoutId in lang.layoutIds) {
                assertTrue(
                    "language ${lang.id} lists unknown layout '$layoutId'",
                    layoutId in builtInIds,
                )
            }
        }
    }

    @Test
    fun `an unknown id resolves to the generic language, never null`() {
        assertSame(LanguageRegistry.GENERIC, LanguageRegistry.byId("zz-Xxxx"))
        assertSame(LanguageRegistry.GENERIC, LanguageRegistry.byId(""))
    }

    @Test
    fun `byLocale matches on the primary subtag`() {
        assertEquals("fr", LanguageRegistry.byLocale("fr-FR")?.id)
        assertEquals("en", LanguageRegistry.byLocale("en")?.id)
        assertEquals("bn", LanguageRegistry.byLocale("bn_BD")?.id)
        assertEquals(null, LanguageRegistry.byLocale("zz"))
    }

    @Test
    fun `languageOf maps every built-in layout to its language`() {
        assertEquals("en", LanguageRegistry.languageOf(BuiltInLayouts.QWERTY_ID).id)
        assertEquals("en", LanguageRegistry.languageOf(BuiltInLayouts.AZERTY_ID).id)
        assertEquals("bn", LanguageRegistry.languageOf(BuiltInLayouts.AVRO_ID).id)
        assertEquals("de", LanguageRegistry.languageOf(BuiltInLayouts.GERMAN_ID).id)
    }

    @Test
    fun `legacy language map covers every old mode name and lands on a real language`() {
        assertEquals(legacyModeNames.toSet(), LEGACY_MODE_LANG.keys)
        for ((mode, langId) in LEGACY_MODE_LANG) {
            assertNotNull("$mode maps to nothing", langId)
            assertFalse(
                "$mode maps to the generic fallback instead of a real language",
                LanguageRegistry.byId(langId) === LanguageRegistry.GENERIC,
            )
        }
    }

    @Test
    fun `legacy layout map covers every old mode name and lands on a real built-in`() {
        assertEquals(legacyModeNames.toSet(), LEGACY_MODE_LAYOUT.keys)
        val builtInIds = BuiltInLayouts.all.mapTo(HashSet()) { it.id }
        for ((mode, layoutId) in LEGACY_MODE_LAYOUT) {
            assertTrue("$mode maps to unknown layout '$layoutId'", layoutId in builtInIds)
        }
    }

    /** The two maps must agree: a mode's layout belongs to the mode's language. */
    @Test
    fun `the two legacy maps are consistent`() {
        for (mode in legacyModeNames) {
            val langId = LEGACY_MODE_LANG.getValue(mode)
            val layoutId = LEGACY_MODE_LAYOUT.getValue(mode)
            assertTrue(
                "$mode: layout '$layoutId' is not in language '$langId'",
                layoutId in LanguageRegistry.byId(langId).layoutIds,
            )
        }
    }
}
