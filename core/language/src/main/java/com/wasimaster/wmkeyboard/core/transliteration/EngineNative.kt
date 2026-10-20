package com.wasimaster.wmkeyboard.core.transliteration

import java.io.File

/**
 * JNI surface of the text engines: libwm_engine_jni, built from
 * `native/wm-engine-jni` over the `native/wm-engine` crate, which is the one
 * implementation of the phonetic engines the Android app and WMK Desktop
 * share. The Kotlin objects in this package ([AvroPhonetic], [Khipro],
 * [BijoyAnsi] and the rest) are facades over these calls and keep the
 * signatures the rest of the app always had.
 *
 * On a device the library is in the base APK, so `System.loadLibrary` finds
 * it. In a JVM unit test it is the host build `native/wm-engine-jni/build-host.sh`
 * makes, found by walking up from the test's working directory to
 * `settings.gradle.kts`; the Gradle test tasks of the modules that touch the
 * engine run that script first. `-Dwmkeyboard.engine.lib=<path>` or
 * `WM_ENGINE_LIB` name a library outright.
 *
 * Strings cross as they are; enums cross as ordinals in declaration order,
 * which the Rust side mirrors, so neither enum may be reordered on its own.
 * A panic inside an engine comes back as a [RuntimeException].
 */
internal object EngineNative {
    const val LIBRARY = "wm_engine_jni"

    init {
        load()
    }

    @Volatile
    var isLoaded = false
        private set

    private fun load() {
        val explicit = System.getProperty("wmkeyboard.engine.lib") ?: System.getenv("WM_ENGINE_LIB")
        if (!explicit.isNullOrEmpty()) {
            try {
                System.load(explicit)
                isLoaded = true
                return
            } catch (e: Throwable) {
                System.err.println("EngineNative: failed to load explicit library: $e")
            }
        }
        // ART reports itself as Dalvik; Robolectric runs on a desktop JVM.
        if (System.getProperty("java.vm.name") == "Dalvik") {
            try {
                System.loadLibrary(LIBRARY)
                isLoaded = true
                return
            } catch (_: UnsatisfiedLinkError) {
                // Robolectric desktop environment: fall through to hostLibrary
            }
        }
        val host = hostLibrary() ?: return
        try {
            System.load(host.absolutePath)
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("EngineNative: failed to load host library at ${host.absolutePath}: $e")
        }
    }

    /** The host build under the repo's `native/wm-engine-jni/target`, release first. */
    private fun hostLibrary(): File? {
        val names = listOf("libwm_engine_jni.dylib", "libwm_engine_jni.so", "wm_engine_jni.dll")
        val explicitDir = System.getProperty("wmkeyboard.engine.lib.dir") ?: System.getenv("WM_ENGINE_LIB_DIR")
        if (!explicitDir.isNullOrEmpty()) {
            val dir = File(explicitDir)
            for (name in names) {
                val file = File(dir, name)
                if (file.isFile) return file
            }
        }

        val candidates = mutableListOf<File>()
        try {
            EngineNative::class.java.protectionDomain?.codeSource?.location?.toURI()?.let {
                val f = File(it)
                candidates.add(if (f.isDirectory) f else f.parentFile)
            }
        } catch (_: Throwable) {}

        candidates.add(File("").absoluteFile)
        System.getProperty("user.dir")?.let { candidates.add(File(it).absoluteFile) }
        System.getProperty("java.class.path")?.split(File.pathSeparator)?.forEach { cp ->
            if (cp.isNotEmpty()) {
                val f = File(cp).absoluteFile
                candidates.add(if (f.isDirectory) f else f.parentFile)
            }
        }

        for (start in candidates) {
            var dir: File? = start
            while (dir != null) {
                if (File(dir, "settings.gradle.kts").isFile) {
                    for (profile in listOf("release", "debug")) {
                        for (name in names) {
                            val file = File(dir, "native/wm-engine-jni/target/$profile/$name")
                            if (file.isFile) return file
                        }
                    }
                    break
                }
                dir = dir.parentFile
            }
        }
        return null
    }

    // Phonetic engines.
    external fun avro(input: String): String
    external fun assamese(input: String): String
    external fun assameseToBengali(text: String): String
    external fun hindi(input: String): String
    external fun hindiVariants(input: String): Array<String>
    external fun urdu(input: String): String
    external fun urduVariants(input: String): Array<String>
    external fun arabic(input: String): String
    external fun arabicVariants(input: String): Array<String>
    external fun persian(input: String): String
    external fun persianVariants(input: String): Array<String>
    external fun indic(languageId: String, input: String): String
    external fun indicVariants(languageId: String, input: String): Array<String>
    external fun indicRomanPrep(languageId: String, input: String): String
    external fun indicDevanagariPrep(languageId: String, dev: String): String
    external fun indicRerankFlatList(languageId: String): Boolean
    external fun indicToDevanagari(script: Int, text: String): String
    external fun indicFromDevanagari(script: Int, dev: String): String

    // Khipro.
    external fun khipro(roman: String, variant: Int): String
    external fun khiproWarm(variant: Int)
    external fun khiproSpellings(word: String): Array<String>
    external fun khiproRomanizerWarm()

    // Bijoy ANSI.
    external fun bijoy(text: String, version: Int, before: String): String
    external fun bijoyNeedsConversion(text: String): Boolean

    // Bengali graphemes. Lengths are UTF-16 units; `previous` is a code point or -1.
    external fun clusterDeleteLength(text: String): Int
    external fun vowelFormAfter(previous: Int): Int
    external fun vowelKeyText(kar: Int, form: Int): String?

    // Romanizers.
    external fun bengaliRomanize(text: String): String
    external fun bengaliRomanizeWord(word: String): String
    external fun bengaliNormalize(text: String): String
    external fun devanagariRomanize(text: String): String
    external fun devanagariRomanizeWord(word: String): String
    external fun devanagariNormalize(text: String): String
    external fun urduRomanize(text: String): String
    external fun urduRomanizeWord(word: String): String
    external fun urduNormalize(text: String): String
}
