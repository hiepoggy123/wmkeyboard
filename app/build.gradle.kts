import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// API keys for the network tools (GIF/sticker via KLIPY/GIPHY, web/image search via
// Brave Search, optional official Cloud Translation). Read from
// local.properties (never committed) or, failing that, environment variables —
// all optional: without a key the affected tool shows a "needs API key" panel
// and users can paste their own key in the tool's settings.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun apiKey(propertyName: String, envName: String): String =
    localProperties.getProperty(propertyName)?.trim()
        ?: System.getenv(envName)?.trim()
        ?: ""

android {
    namespace = "com.wasimaster.wmkeyboard"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.wasimaster.wmkeyboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Personal-build keyboard: every target device is arm64. Dropping the
        // other ABIs removes ~90 MB of native libs (ML Kit + Harper copies).
        ndk {
            abiFilters += "arm64-v8a"
        }

        buildConfigField("String", "KLIPY_API_KEY", "\"${apiKey("wmkb.klipyApiKey", "WMKB_KLIPY_API_KEY")}\"")
        buildConfigField("String", "GIPHY_API_KEY", "\"${apiKey("wmkb.giphyApiKey", "WMKB_GIPHY_API_KEY")}\"")
        buildConfigField("String", "BRAVE_API_KEY", "\"${apiKey("wmkb.braveApiKey", "WMKB_BRAVE_API_KEY")}\"")
        buildConfigField("String", "TRANSLATE_API_KEY", "\"${apiKey("wmkb.translateApiKey", "WMKB_TRANSLATE_API_KEY")}\"")
    }

    // Build flavors for storage-constrained devices.
    // - full: all features (handwriting, OCR, QR scan, doc scan, grammar checker).
    // - lite: removes ~100 MB of ML Kit + Harper native libraries for low-storage devices.
    flavorDimensions += "capabilities"
    productFlavors {
        create("full") {
            dimension = "capabilities"
            buildConfigField("Boolean", "ENABLE_ML_KIT_HANDWRITING", "true")
            buildConfigField("Boolean", "ENABLE_ML_KIT_SCANNERS", "true")
            buildConfigField("Boolean", "ENABLE_GRAMMAR", "true")
            buildConfigField("Boolean", "ENABLE_LOCAL_LLM", "true")
            buildConfigField("Boolean", "ENABLE_WHISPER", "true")
        }
        create("lite") {
            dimension = "capabilities"
            buildConfigField("Boolean", "ENABLE_ML_KIT_HANDWRITING", "false")
            buildConfigField("Boolean", "ENABLE_ML_KIT_SCANNERS", "false")
            buildConfigField("Boolean", "ENABLE_GRAMMAR", "false")
            buildConfigField("Boolean", "ENABLE_LOCAL_LLM", "false")
            buildConfigField("Boolean", "ENABLE_WHISPER", "false")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Lite builds exclude native libraries (Harper grammar, ~20 MB per ABI).
        getByName("lite") {
            jniLibs.setSrcDirs(emptySet<String>())
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // ML Kit features (full flavor only)
    "fullImplementation"(libs.mlkit.digital.ink)
    "fullImplementation"(libs.mlkit.text.recognition)
    "fullImplementation"(libs.mlkit.barcode.scanning)
    "fullImplementation"(libs.mlkit.document.scanner)

    // On-device LLM runtime (full flavor only)
    "fullImplementation"(libs.litertlm.android)

    // On-device Whisper speech-to-text runtime (full flavor only). The classic
    // LiteRT/TF-Lite Interpreter API (org.tensorflow.lite.*) — .tflite Whisper
    // graphs run the full decode internally via named signatures.
    "fullImplementation"(libs.litert)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
