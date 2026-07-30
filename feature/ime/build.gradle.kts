plugins {
    alias(libs.plugins.android.library)
    id("wmkeyboard.detekt")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wasimaster.wmkeyboard.ime"
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
    buildFeatures { compose = true }
    lint { lintConfig = rootProject.file("config/lint/lint.xml") }
}

// Compose compiler skippability/stability report, on demand:
//   ./gradlew :feature:ime:assembleFullDebug -PcomposeMetrics=true
// then read build/compose/reports/*-composables.txt. Inert without the flag, so
// ordinary builds neither slow down nor write the reports.
if (providers.gradleProperty("composeMetrics").isPresent) {
    composeCompiler {
        metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        extraWarnings.set(true)
        freeCompilerArgs.addAll("-Xreport-all-warnings")
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false))
    }
}

dependencies {
    api(project(":core:config"))
    api(project(":core:common"))
    api(project(":core:language"))
    api(project(":core:input"))
    api(project(":core:prediction"))
    api(project(":core:emoji"))
    api(project(":core:theme"))
    api(project(":core:icons"))
    api(project(":core:content"))
    api(project(":core:tools"))
    api(project(":core:settings"))
    api(project(":core:addons"))
    api(project(":core:voice"))
    api(project(":core:feedback"))
    api(project(":core:plugins"))
    api(project(":core:intelligence"))
    api(project(":feature:tools"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    "fullImplementation"(libs.mlkit.text.recognition)
    "fullImplementation"(libs.mlkit.barcode.scanning)
    "fullImplementation"(libs.mlkit.document.scanner)

    testImplementation(libs.junit)
}
