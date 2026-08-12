plugins {
    alias(libs.plugins.android.library)
    id("wmkeyboard.detekt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wasimaster.wmkeyboard.keyman"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 24
    }

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
        extraWarnings.set(true)
        freeCompilerArgs.addAll("-Xreport-all-warnings")
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false))
    }
}

// Deliberately no Compose. This module is a binary parser and a rule
// interpreter; nothing here draws. Keeping the Compose plugin off means its
// unit tests do not drag the Compose runtime in, and it is the reason the
// module sits beside :core:input rather than inside it.
dependencies {
    api(project(":core:language"))
    api(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
}
