package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the plugin sandbox against the one shrinker failure no other test can see.
 *
 * LuaJ is compiled at Java 1.4 source level, where `Foo.class` is not a class
 * constant in the bytecode but a `class$("org.luaj...")` helper handing a *string*
 * to `Class.forName`. A class reached only that way is invisible to R8: nothing in
 * the dex points at it, so it is stripped as dead code, and the first plugin to run
 * dies with `NoClassDefFoundError` — in release builds only. Debug builds, unit
 * tests and the demo-plugin suite all pass, because none of them shrink anything.
 *
 * So this test does the shrinker's reading for it: whatever library classes
 * [PluginSandbox] installs, every LuaJ class those libraries name as a string has
 * to be named back in `proguard-rules.pro`. Adding a library to
 * [PluginSandbox.create] that resolves helpers by name fails here instead of on a
 * user's phone.
 */
class PluginShrinkerRulesTest {

    @Test
    fun `every luaj class reached by name survives shrinking`() {
        val named = luajClassesReachedByName()
        // If this trips, the scan below stopped finding what it is meant to find —
        // the guard is worthless silently passing on an empty set.
        assertTrue(
            "found no class-by-name references at all; the constant-pool scan has stopped working",
            named.isNotEmpty(),
        )

        val rules = proguardRules()
        val unkept = named.filterNot { rules.contains("-keep class $it") }
        assertTrue(
            "R8 strips these, and LuaJ then resolves them with Class.forName and fails: $unkept",
            unkept.isEmpty(),
        )
    }

    /**
     * The LuaJ classes named as strings by the libraries [PluginSandbox] installs.
     *
     * One level deep on purpose. The hazard is `LibFunction.bind`, which every
     * library calls from its own `call` method on classes it names there, and
     * [PluginSandbox] constructs each of those libraries directly. Walking further
     * would drag in libraries the sandbox deliberately never installs and start
     * demanding keep rules for `IoLib` and `OsLib`, whose absence is the point.
     */
    private fun luajClassesReachedByName(): List<String> {
        val sandbox = constantPoolOf(SANDBOX_CLASS) ?: error("cannot read PluginSandbox's own class file")
        return LUAJ_TYPE.findAll(sandbox)
            .map { it.value }
            .distinct()
            .mapNotNull { constantPoolOf(it) }
            .flatMap { LUAJ_NAME.findAll(it).map(MatchResult::value) }
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * A class file as text. ISO-8859-1 maps every byte to one character, so the
     * ASCII constant pool survives and the rest is harmless noise.
     */
    private fun constantPoolOf(internalName: String): String? =
        javaClass.classLoader?.getResourceAsStream("$internalName.class")
            ?.use { String(it.readBytes(), Charsets.ISO_8859_1) }

    private fun proguardRules(): String {
        // Unit tests run from the module directory, but not every runner agrees.
        val file = listOf(File("proguard-rules.pro"), File("app/proguard-rules.pro"))
            .firstOrNull { it.isFile }
            ?: error("cannot find proguard-rules.pro from ${File("").absolutePath}")
        return file.readText()
    }

    private companion object {
        const val SANDBOX_CLASS = "com/wasimaster/wmkeyboard/core/plugins/PluginSandbox"

        /** A type reference in a constant pool: slashes. */
        val LUAJ_TYPE = Regex("""org/luaj/vm2/[A-Za-z0-9_/${'$'}]+""")

        /** A class *name* handed to Class.forName: dots. */
        val LUAJ_NAME = Regex("""org\.luaj\.vm2\.[A-Za-z0-9_.${'$'}]+""")
    }
}
