plugins {
    // By id, not version-catalog alias: AGP is already on the build classpath
    // through build-logic, and Gradle refuses a versioned plugin request for
    // a plugin the classpath already carries.
    id("com.android.dynamic-feature")
}

// The on-demand home of the LiteRT-LM runtime, Play builds only: :app lists
// this module in `dynamicFeatures` when wmkb.enablePlayStore is on, and no
// other build references it at all — F-Droid and direct-download APKs embed
// the same code in :core:intelligence instead (see the playStoreChannel block
// there). The module has no sources of its own: it compiles the shared
// src/llmbridge directory, whose one class the base app reaches by
// reflection, plus the litertlm dependency whose ~20 MB per ABI of native
// code is the entire point of the split.
android {
    namespace = "com.wasimaster.wmkeyboard.llmfeature"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 24
    }

    // Must mirror the base app's dimension: each base variant needs a
    // matching variant here (fullRelease base -> fullRelease split).
    flavorDimensions += "capabilities"
    productFlavors {
        create("full") { dimension = "capabilities" }
        create("lite") { dimension = "capabilities" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint { lintConfig = rootProject.file("config/lint/lint.xml") }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

androidComponents {
    onVariants { variant ->
        // Only the full flavour has the litertlm classes the bridge imports;
        // the lite split stays empty (a few KB) and is never requested — the
        // lite app hides the On-device provider entirely.
        if (variant.flavorName == "full") {
            variant.sources.kotlin
                ?.addStaticSourceDirectory("../../core/intelligence/src/llmbridge/java")
        }
    }
}

dependencies {
    // Dynamic-feature convention: every feature depends on the base app.
    implementation(project(":app"))
    // Named directly, not inherited: a feature compiles against the base's
    // *api* surface only, and :app keeps its project modules internal. AGP's
    // feature packaging strips everything the base already carries, so this
    // puts no :core class in the split — it only makes LlmRuntime, ChatReplay,
    // LocalLlmBackend and the intelligence R visible to the bridge compile.
    implementation(project(":core:intelligence"))
    "fullImplementation"(libs.litertlm.android)
}
