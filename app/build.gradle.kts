import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
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

        // ABI target filtering is handled by splits.abi below.

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

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = apiKey("RELEASE_STORE_FILE", "RELEASE_STORE_FILE")
            if (storeFilePath.isNotEmpty()) {
                val file = rootProject.file(storeFilePath)
                if (file.exists()) {
                    storeFile = file
                    storePassword = apiKey("RELEASE_STORE_PASSWORD", "RELEASE_STORE_PASSWORD")
                    keyAlias = apiKey("RELEASE_KEY_ALIAS", "RELEASE_KEY_ALIAS")
                    keyPassword = apiKey("RELEASE_KEY_PASSWORD", "RELEASE_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            val releaseSigningConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseSigningConfig.storeFile?.exists() == true) {
                releaseSigningConfig
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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
    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols += "**/*.so"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Android Lint. Severities live in config/lint/lint.xml — this block only
    // says *what* to analyse and *how loudly* to report it.
    lint {
        lintConfig = rootProject.file("config/lint/lint.xml")

        // Turn on every check Lint ships, including the large set that is
        // disabled by default (interoperability, correctness edge cases,
        // API-surface checks). lint.xml then silences the ones that do not
        // apply to an IME, each with a written reason.
        checkAllWarnings = true
        checkDependencies = true
        // Test sources ship bugs too, and the unit tests here encode the
        // dictionary/transliteration contracts.
        checkTestSources = true
        ignoreTestFixturesSources = false
        checkGeneratedSources = false

        // Real problems stop the build; style-grade warnings do not, so the
        // signal stays readable. Escalation to `error` is per-issue in lint.xml.
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true

        // Print the full explanation and the offending lines, not just the
        // one-line summary — a finding nobody understands is a finding nobody
        // fixes.
        explainIssues = true
        noLines = false
        absolutePaths = false

        // AGP 9 always writes HTML/XML/SARIF/text reports to
        // build/reports/lint-results-<variant>.*; the report toggles and output
        // paths were removed from the DSL.
    }

    // The Harper native libraries live in :core:intelligence under src/full/jniLibs,
    // so lite builds exclude them by construction — no source-set surgery needed here.
}

// Compiles dictionaries-src/*.txt into assets/dictionaries/*.wmdict via the
// :tools:dictc host tool, which shares the app's own trie/codec sources so the
// binary format cannot drift between writer and reader. Wired into every
// variant's assets through the Variant API below.
val dictc: Configuration by configurations.creating

abstract class CompileDictionariesTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    /** Assets root chosen by AGP; wordlists land in `dictionaries/` inside it. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: org.gradle.process.ExecOperations

    @TaskAction
    fun run() {
        execOperations.javaexec {
            classpath = toolClasspath
            mainClass.set("MainKt")
            args(
                sourceDir.get().asFile.absolutePath,
                outputDir.get().asFile.resolve("dictionaries").absolutePath,
            )
        }
    }
}

val compileBundledDictionaries =
    tasks.register<CompileDictionariesTask>("compileBundledDictionaries") {
        sourceDir.set(layout.projectDirectory.dir("dictionaries-src"))
        toolClasspath.from(dictc)
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            compileBundledDictionaries,
            CompileDictionariesTask::outputDir,
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)

        // The compiler is the cheapest static analyser available: it already
        // knows the types. `extraWarnings` turns on the diagnostics that are
        // off by default — unused expression results, redundant casts and
        // labels, useless elvis branches, unnecessary type arguments.
        extraWarnings.set(true)

        freeCompilerArgs.addAll(
            // Without this the compiler stops reporting warnings for a file as
            // soon as that file has an error — which hides warnings exactly when
            // the code is being changed most.
            "-Xreport-all-warnings",
        )

        // Gated rather than unconditional: CI can enforce a zero-warning build
        // (`./gradlew assemble -PwarningsAsErrors=true`) without making every
        // local edit fail on a warning mid-refactor.
        allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false))
    }
}

// ---------------------------------------------------------------------------
// detekt: rule-based static analysis over the Kotlin sources.
//
// The `detekt<Variant>` tasks (detektFullDebug, ...) run *with type
// resolution* — they get the variant's compile classpath, which is what the
// high-value rules need: nullability, unreachable code, ignored return values
// and exhaustive-`when` checks are all impossible without resolved types.
// The plain `detekt` task is source-only and much weaker; it stays available
// for a fast pass but is not what `staticAnalysis` runs.
// ---------------------------------------------------------------------------
// Library-module *test* sources, analysed by this module's unit-test detekt
// task below. Main sources are NOT swept from here — each module's own
// wmkeyboard.detekt plugin analyses them against that module's classpath.
// (Analysing module sources with the app's classpath makes detekt see those
// classes as both source and binary, which fabricates UnreachableCode
// findings.) Test sources are safe to sweep: they are never packaged into a
// jar on this classpath.
val moduleTestSrc = listOf(
    "../core/language", "../core/tools", "../core/emoji",
    "../core/intelligence", "../feature/tools", "../feature/ime",
).map { "$it/src/test/java" } + listOf("../core/intelligence/src/testFull/java")

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    // `allRules` would also enable rules detekt itself marks as unstable or
    // opinionated; the ruleset in detekt.yml is curated explicitly instead.
    allRules = false
    parallel = true
    ignoreFailures = false
    // Report paths relative to the repo root so findings are clickable from CI
    // logs regardless of where the checkout lives.
    basePath = rootProject.projectDir.absolutePath
    source.setFrom(
        files(
            "src/main/java",
            "src/full/java",
            "src/lite/java",
            "src/test/java",
            "src/testFull/java",
            "src/androidTest/java",
        ),
    )
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "11"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        md.required.set(false)
        txt.required.set(false)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = "11"
}

// detekt 1.23's Android integration is written against the AGP 7/8 variant API
// and creates no `detekt<Variant>` tasks under AGP 9, which would leave the
// project with source-only analysis — losing every rule that needs resolved
// types. So the type-resolution tasks are registered here by hand: same Detekt
// task type, classpath taken from the variant's own Kotlin compilation.
//
//   ./gradlew detektFullDebug            # main + flavour sources
//   ./gradlew detektFullDebugUnitTest    # unit tests, with main on the classpath
fun registerTypeResolvedDetekt(
    taskName: String,
    description: String,
    sourceDirs: List<String>,
    compileTaskName: String,
    extraClasspath: List<String> = emptyList(),
) = tasks.register<Detekt>(taskName) {
    this.description = description
    group = "verification"

    val compileTask =
        tasks.named(compileTaskName, org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class)

    setSource(files(sourceDirs.map { layout.projectDirectory.dir(it) }))
    include("**/*.kt")
    exclude("**/build/**", "**/resources/**")

    // `libraries` is the variant's resolved compile classpath; android.jar has
    // to be added separately because it is on the bootclasspath, not there.
    classpath.setFrom(
        compileTask.map { it.libraries },
        androidComponents.sdkComponents.bootClasspath,
        files(extraClasspath.map { layout.buildDirectory.dir(it) }),
    )
    if (extraClasspath.isNotEmpty()) {
        dependsOn(compileTask)
    }

    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    ignoreFailures = false
    basePath = rootProject.projectDir.absolutePath
    jvmTarget = "11"

    reports {
        html.outputLocation.set(layout.buildDirectory.file("reports/detekt/$taskName.html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/detekt/$taskName.xml"))
        sarif.outputLocation.set(layout.buildDirectory.file("reports/detekt/$taskName.sarif"))
    }
}

registerTypeResolvedDetekt(
    taskName = "detektFullDebug",
    description = "Runs detekt with type resolution over the fullDebug variant's sources.",
    sourceDirs = listOf("src/main/java", "src/full/java"),
    compileTaskName = "compileFullDebugKotlin",
)

registerTypeResolvedDetekt(
    taskName = "detektLiteDebug",
    description = "Runs detekt with type resolution over the liteDebug variant's sources.",
    sourceDirs = listOf("src/main/java", "src/lite/java"),
    compileTaskName = "compileLiteDebugKotlin",
)

registerTypeResolvedDetekt(
    taskName = "detektFullDebugUnitTest",
    description = "Runs detekt with type resolution over the unit tests, including the library modules'.",
    sourceDirs = listOf("src/test/java", "src/testFull/java") + moduleTestSrc,
    compileTaskName = "compileFullDebugUnitTestKotlin",
    extraClasspath = listOf("tmp/kotlin-classes/fullDebug"),
)

registerTypeResolvedDetekt(
    taskName = "detektFullDebugAndroidTest",
    description = "Runs detekt with type resolution over the instrumentation tests.",
    sourceDirs = listOf("src/androidTest/java"),
    compileTaskName = "compileFullDebugAndroidTestKotlin",
    extraClasspath = listOf("tmp/kotlin-classes/fullDebug"),
)

// `./gradlew check` should mean "everything the analysers know how to check".
tasks.named("check") {
    dependsOn("detektFullDebug", "detektLiteDebug", "detektFullDebugUnitTest")
}

dependencies {
    dictc(project(":tools:dictc"))

    // Static-analysis rule packs. These are analyser plugins, not app code —
    // nothing here reaches the APK.
    detektPlugins(libs.detekt.formatting)
    // Compose correctness rules (unremembered state, unstable parameters,
    // modifier misuse, composables that read state too often). Compose bugs are
    // recomposition bugs, and none of them are visible to the type checker.
    lintChecks(libs.compose.lint.checks)

    // Feature modules. Their build files declare project dependencies with
    // `api`, so :feature:ime alone would reach every :core:* transitively —
    // the explicit list is for the unit tests in src/test, which compile
    // against classes from every layer.
    implementation(project(":core:config"))
    implementation(project(":core:common"))
    implementation(project(":core:language"))
    implementation(project(":core:input"))
    implementation(project(":core:prediction"))
    implementation(project(":core:emoji"))
    implementation(project(":core:theme"))
    implementation(project(":core:icons"))
    implementation(project(":core:tools"))
    implementation(project(":core:content"))
    implementation(project(":core:addons"))
    implementation(project(":core:voice"))
    implementation(project(":core:settings"))
    implementation(project(":core:feedback"))
    implementation(project(":core:plugins"))
    implementation(project(":core:intelligence"))
    implementation(project(":feature:tools"))
    implementation(project(":feature:addons"))
    implementation(project(":feature:ime"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    // The language-settings screens edit DataStore Preferences directly.
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The plugin tests drive the Lua sandbox through luaj types directly.
    testImplementation(libs.luaj.jse)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // The empty activity ComposeTestRule launches into. Debug-only by design:
    // it adds a manifest entry, so it must never reach a release build.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
