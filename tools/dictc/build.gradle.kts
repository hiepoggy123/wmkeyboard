plugins {
    // Version-less: the Kotlin plugin is already on the build classpath via the
    // app module, and Gradle rejects re-declaring a version for it here.
    kotlin("jvm")
}

// The compiler shares the app's own trie + codec sources (they are pure JVM —
// no Android imports) instead of reimplementing the writer: one implementation,
// so the emitted bytes can never drift from what MappedTrie/PackedTrieCodec read.
sourceSets {
    main {
        kotlin {
            srcDir(rootProject.file("app/src/main/java/com/wasimaster/wmkeyboard/core/prediction"))
            include(
                "Main.kt",
                "Trie.kt",
                "PackedTrie.kt",
                "PackedTrieCodec.kt",
                "DictionaryLoader.kt",
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}
