pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// Convention plugins (per-module type-resolved detekt).
includeBuild("build-logic")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WM Keyboard"
include(":app")

// Library modules, layered bottom-up. :core:config carries the build flags and
// API keys every library reads; :core:common the shared utilities; the rest are
// one feature area each. :feature:* sit above :core:settings (they read the
// settings repository), :feature:ime is the keyboard runtime itself.
include(":core:config")
include(":core:common")
include(":core:language")
include(":core:input")
include(":core:prediction")
include(":core:emoji")
include(":core:theme")
include(":core:icons")
include(":core:tools")
include(":core:content")
include(":core:addons")
include(":core:voice")
include(":core:settings")
include(":core:feedback")
include(":core:plugins")
include(":core:intelligence")
include(":feature:tools")
include(":feature:addons")
include(":feature:ime")
// Host-side dictionary compiler: turns dictionaries-src/*.txt into the .wmdict
// binary assets at build time, sharing the app's own trie/codec sources so the
// written format can never drift from the reader.
include(":tools:dictc")
