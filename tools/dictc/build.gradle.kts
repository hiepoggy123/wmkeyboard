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
            srcDir(rootProject.file("core/prediction/src/main/java/com/wasimaster/wmkeyboard/core/prediction"))
            include(
                "Main.kt",
                "Trie.kt",
                "TrieWalker.kt",
                "TrieCompleter.kt",
                "PackedTrie.kt",
                "PackedTrieCodec.kt",
                "RankFloorCache.kt",
                "DictionaryLoader.kt",
            )
        }
    }
}

// Target 17 without asking for a *toolchain* of 17. A toolchain is a request for
// a specific JDK to be installed, and when it is not there Gradle either
// downloads one or fails. F-Droid's builder does neither: auto-provisioning is
// off on their machines, and its own image carries no 17, so `jvmToolchain(17)`
// failed the build outright on `:tools:dictc:compileJava`. Setting the target
// instead compiles with whatever JDK is running Gradle and emits 17 bytecode,
// which is all this module needs — it is a build-time tool, run by
// `:app:compileBundledDictionaries` on that same JVM and never shipped.
// Both halves are set because Kotlin 2 fails the build on a Java/Kotlin target
// mismatch rather than warning about it.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
