package com.wasimaster.wmkeyboard.core.plugins

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.JarURLConnection

/**
 * Guards the one decision the plugin editor's language service changed about R8.
 *
 * `proguard-rules.pro` used to count the luaj AST parser among the corners R8
 * strips. It ships now, because the editor parses Lua to check it. That is safe
 * only while two things stay true, and this test holds both:
 *
 *  * Nothing in the plugin module reaches the corners whose absence *is* the
 *    security property: luajava's Java interop, the luajc bytecode backend, the
 *    JSR-223 script engine, and the libraries the sandbox deliberately never
 *    installs. The parser itself reaches none of them either.
 *  * The parser is plain `new` and `switch`. It has none of the Java 1.4
 *    `class$("...")` / `Class.forName` lookups that made `Bit32Lib` need a keep
 *    rule, so R8 keeps exactly what the service references and nothing breaks
 *    in release only.
 *
 * Reads class files as ISO-8859-1 text, the same trick as [PluginShrinkerRulesTest].
 */
class LuaLanguageShrinkerTest {

    @Test
    fun `no plugin class reaches a stripped luaj corner`() {
        val classes = classesUnder(PLUGINS_PACKAGE, anchor = SANDBOX_CLASS)
        // The scan must be looking at real code, or it passes on nothing.
        assertTrue("found no plugin classes at all", classes.size > 10)
        assertTrue(
            "no plugin class names luaj; the scan has stopped reading class files",
            classes.any { constantPoolOf(it)?.contains("org/luaj/vm2/") == true },
        )

        val offenders = classes.associateWith { forbiddenIn(constantPoolOf(it).orEmpty()) }.filterValues { it.isNotEmpty() }
        assertTrue("these plugin classes reach a corner R8 must keep stripping: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the parser reaches nothing outside itself but three value types`() {
        val parser = parserClasses()
        assertTrue("found no parser or ast classes; the scan has stopped working", parser.size > 40)

        val strays = parser.associateWith { name ->
            LUAJ_TYPE.findAll(constantPoolOf(name).orEmpty())
                .map { it.value.substringBefore('$').trimEnd(';') }
                .filterNot { it.startsWith(AST) || it.startsWith(PARSER) || it in PARSER_MAY_REACH }
                .toSet()
        }.filterValues { it.isNotEmpty() }
        assertTrue("the parser now reaches more of luaj than the proguard comment promises: $strays", strays.isEmpty())
    }

    @Test
    fun `the parser needs no keep rule`() {
        // Positive control: the class that taught us this hazard must still show it.
        val bit32 = constantPoolOf("org/luaj/vm2/lib/Bit32Lib").orEmpty()
        assertTrue("Bit32Lib no longer shows class-by-name; the scan has stopped working", "forName" in bit32)

        val reflective = parserClasses().filter { name ->
            val pool = constantPoolOf(name).orEmpty()
            "class$" in pool || "forName" in pool
        }
        assertTrue("these parser classes look classes up by name, so R8 would strip their targets: $reflective", reflective.isEmpty())
    }

    @Test
    fun `proguard still silences the corners it strips`() {
        val rules = listOf(File("proguard-rules.pro"), File("app/proguard-rules.pro"))
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("cannot find proguard-rules.pro from ${File("").absolutePath}")
        for (rule in listOf("-dontwarn org.luaj.vm2.luajc.**", "-dontwarn org.luaj.vm2.script.**", "-keeppackagenames org.luaj.**")) {
            assertTrue("proguard-rules.pro lost `$rule`", rule in rules)
        }
    }

    private fun parserClasses(): List<String> =
        classesUnder(AST.removeSuffix("/"), anchor = "${AST}Chunk") +
            classesUnder(PARSER.removeSuffix("/"), anchor = "${PARSER}LuaParser")

    private fun forbiddenIn(pool: String): List<String> = FORBIDDEN.filter { it in pool }

    /** Every class file under [packagePath], found through the location that holds [anchor]. */
    private fun classesUnder(packagePath: String, anchor: String): List<String> {
        val url = javaClass.classLoader?.getResource("$anchor.class") ?: error("cannot find $anchor on the test classpath")
        return when (url.protocol) {
            "jar" -> {
                val connection = url.openConnection() as JarURLConnection
                // Not the shared cached JarFile: closing that one would pull it out
                // from under whoever else in this JVM is reading it.
                connection.useCaches = false
                connection.jarFile.use { jar ->
                    jar.entries().asSequence()
                        .map { it.name }
                        .filter { it.startsWith("$packagePath/") && it.endsWith(".class") }
                        .map { it.removeSuffix(".class") }
                        .toList()
                }
            }

            "file" -> {
                val root = File(File(url.toURI()).path.removeSuffix("$anchor.class"))
                File(root, packagePath).walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".class") }
                    .map { it.relativeTo(root).path.replace(File.separatorChar, '/').removeSuffix(".class") }
                    .toList()
            }

            else -> error("unexpected classpath location ${url.protocol}: $url")
        }
    }

    private fun constantPoolOf(internalName: String): String? =
        javaClass.classLoader?.getResourceAsStream("$internalName.class")
            ?.use { String(it.readBytes(), Charsets.ISO_8859_1) }

    private companion object {
        const val PLUGINS_PACKAGE = "com/wasimaster/wmkeyboard/core/plugins"
        const val SANDBOX_CLASS = "$PLUGINS_PACKAGE/PluginSandbox"
        const val AST = "org/luaj/vm2/ast/"
        const val PARSER = "org/luaj/vm2/parser/"

        /** The only luaj types outside its own packages the parser may name. */
        val PARSER_MAY_REACH = setOf("org/luaj/vm2/LuaValue", "org/luaj/vm2/LuaString", "org/luaj/vm2/LuaBoolean")

        /** Descriptors whose presence would ship what the sandbox's outer wall depends on stripping. */
        val FORBIDDEN = listOf(
            "org/luaj/vm2/luajc/",
            "org/luaj/vm2/script/",
            "org/luaj/vm2/lib/jse/LuajavaLib",
            "org/luaj/vm2/lib/jse/JseIoLib",
            "org/luaj/vm2/lib/jse/JseOsLib",
            "org/luaj/vm2/lib/PackageLib",
            "org/luaj/vm2/lib/IoLib",
            "org/luaj/vm2/lib/OsLib",
            "org/luaj/vm2/lib/CoroutineLib",
        )

        val LUAJ_TYPE = Regex("""org/luaj/vm2/[A-Za-z0-9_/${'$'}]+""")
    }
}
