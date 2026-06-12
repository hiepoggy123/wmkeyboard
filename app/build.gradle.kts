import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// API keys for the network tools (GIF/sticker via KLIPY/GIPHY, web/image search via
// Google Programmable Search, optional official Cloud Translation). Read from
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

        buildConfigField("String", "KLIPY_API_KEY", "\"${apiKey("wmkb.klipyApiKey", "WMKB_KLIPY_API_KEY")}\"")
        buildConfigField("String", "GIPHY_API_KEY", "\"${apiKey("wmkb.giphyApiKey", "WMKB_GIPHY_API_KEY")}\"")
        buildConfigField("String", "BRAVE_API_KEY", "\"${apiKey("wmkb.braveApiKey", "WMKB_BRAVE_API_KEY")}\"")
        buildConfigField("String", "GOOGLE_SEARCH_API_KEY", "\"${apiKey("wmkb.googleSearchApiKey", "WMKB_GOOGLE_SEARCH_API_KEY")}\"")
        buildConfigField("String", "GOOGLE_SEARCH_CX", "\"${apiKey("wmkb.googleSearchCx", "WMKB_GOOGLE_SEARCH_CX")}\"")
        buildConfigField("String", "GOOGLE_SEARCH_CX_IMAGES", "\"${apiKey("wmkb.googleSearchCxImages", "WMKB_GOOGLE_SEARCH_CX_IMAGES")}\"")
        buildConfigField("String", "GOOGLE_SEARCH_CX_GIFS", "\"${apiKey("wmkb.googleSearchCxGifs", "WMKB_GOOGLE_SEARCH_CX_GIFS")}\"")
        buildConfigField("String", "GOOGLE_SEARCH_CX_STICKERS", "\"${apiKey("wmkb.googleSearchCxStickers", "WMKB_GOOGLE_SEARCH_CX_STICKERS")}\"")
        buildConfigField("String", "TRANSLATE_API_KEY", "\"${apiKey("wmkb.translateApiKey", "WMKB_TRANSLATE_API_KEY")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.digital.ink)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
