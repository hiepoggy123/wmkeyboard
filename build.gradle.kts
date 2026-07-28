// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Applied per-module rather than through `subprojects {}` so each project
    // configures itself — cross-project configuration is what Gradle's isolated
    // projects mode forbids, and it breaks configuration-cache reuse.
    alias(libs.plugins.detekt) apply false
}

// One entry point for every analyser, so CI and humans run the same thing:
//   ./gradlew staticAnalysis
// Type-resolution detekt (detektFullDebug) is deliberately included instead of
// the source-only `detekt` task — the rules that find real bugs (nullability,
// unreachable code, ignored return values) need a resolved classpath.
tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs every static analyser: Android Lint, detekt (with type resolution), and Kotlin compiler diagnostics."
    // :tools:dictc is intentionally absent — its whole source set is symlinked
    // app sources (see tools/dictc/build.gradle.kts), so analysing it would
    // report the same five files twice.
    dependsOn(
        ":app:lintFullDebug",
        ":app:detektFullDebug",
        // The lite flavour compiles a different set of sources (the stubs in
        // src/lite replace the ML Kit / LiteRT implementations), so it needs its
        // own pass — a bug in a stub is invisible to the full-flavour run.
        ":app:detektLiteDebug",
        ":app:detektFullDebugUnitTest",
    )
    // Per-module detekt (registered by the wmkeyboard.detekt convention
    // plugin): each module analyses its own sources against its own compile
    // classpath. :core:config is absent — it has no Kotlin sources. Lite
    // passes only where lite compiles different code (flavored modules);
    // everywhere else the two variants' sources are identical.
    val detektModules = listOf(
        ":core:common", ":core:language", ":core:input", ":core:prediction",
        ":core:emoji", ":core:theme", ":core:icons", ":core:tools",
        ":core:content", ":core:addons", ":core:voice", ":core:settings",
        ":core:feedback", ":core:plugins", ":core:intelligence",
        ":feature:tools", ":feature:addons", ":feature:ime",
    )
    dependsOn(detektModules.map { "$it:detektFullDebug" })
    dependsOn(listOf(":core:voice", ":core:intelligence", ":feature:ime").map { "$it:detektLiteDebug" })
}
