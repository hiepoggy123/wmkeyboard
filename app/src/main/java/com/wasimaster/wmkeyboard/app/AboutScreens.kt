package com.wasimaster.wmkeyboard.app

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wasimaster.wmkeyboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Attribution and licence notices.
 *
 * Every third-party component that ships inside the APK is listed here with
 * its licence, and the full licence texts are bundled as assets under
 * `assets/licenses/` — Apache-2.0 §4(a) and the MIT/OFL/Unicode notices all
 * require the text to travel with the binary, not merely a link to it.
 * Online services the tools call are listed separately: no code of theirs is
 * distributed, but their terms still ask to be credited.
 */

internal const val SOURCE_URL = "https://github.com/wasi-master/WMKeyboard"
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
private const val FDROID_URL = "https://f-droid.org/packages/${BuildConfig.APPLICATION_ID}/"

private const val SHARE_BLURB = "Try out an awesome keyboard app called WM Keyboard!"

/** Opens the system share sheet with [SHARE_BLURB] and [url]. */
private fun shareLink(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$SHARE_BLURB\n$url")
    }
    context.startActivity(Intent.createChooser(intent, "Share WM Keyboard").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

/** One row in the licences list. [licenseAsset] is a file in `assets/licenses/`. */
private data class Attribution(
    val name: String,
    val used: String,
    val copyright: String,
    val license: String,
    val licenseAsset: String?,
    val url: String,
)

/**
 * Components whose code or data is bundled into the APK. Flavour-gated
 * entries are absent from lite builds, where their libraries are not linked.
 */
private val bundledAttributions: List<Attribution> = buildList {
    add(
        Attribution(
            "AndroidX & Jetpack Compose",
            "UI toolkit, navigation, DataStore, CameraX, emoji2",
            "Copyright The Android Open Source Project",
            "Apache-2.0", "apache-2.0.txt",
            "https://developer.android.com/jetpack/androidx",
        ),
    )
    add(
        Attribution(
            "Kotlin, coroutines & serialization",
            "Language runtime, concurrency, settings serialisation",
            "Copyright JetBrains s.r.o. and Kotlin contributors",
            "Apache-2.0", "apache-2.0.txt",
            "https://github.com/JetBrains/kotlin",
        ),
    )
    add(
        Attribution(
            "Coil",
            "Image loading for the GIF, sticker and search panels",
            "Copyright Coil Contributors",
            "Apache-2.0", "apache-2.0.txt",
            "https://github.com/coil-kt/coil",
        ),
    )
    add(
        Attribution(
            "OkHttp & Okio",
            "HTTP client behind Coil and the network tools",
            "Copyright Square, Inc.",
            "Apache-2.0", "apache-2.0.txt",
            "https://github.com/square/okhttp",
        ),
    )
    add(
        Attribution(
            "ZXing",
            "QR code generation",
            "Copyright ZXing authors",
            "Apache-2.0", "apache-2.0.txt",
            "https://github.com/zxing/zxing",
        ),
    )
    add(
        Attribution(
            "LuaJ",
            "Lua interpreter that runs plugins inside their sandbox",
            "Copyright (c) 2007 LuaJ. All rights reserved.",
            "MIT", "mit-luaj.txt",
            "https://github.com/luaj/luaj",
        ),
    )
    if (BuildConfig.ENABLE_GRAMMAR) {
        add(
            Attribution(
                "Harper",
                "Offline grammar and style checking (native library)",
                "Copyright Automattic, Inc. and Harper contributors",
                "Apache-2.0", "apache-2.0.txt",
                "https://github.com/automattic/harper",
            ),
        )
        add(
            Attribution(
                "Rust crates used by Harper",
                "Transitive dependencies linked into the grammar library",
                "Copyright the respective crate authors",
                "MIT / Apache-2.0", "mit.txt",
                "https://crates.io",
            ),
        )
    }
    if (BuildConfig.ENABLE_ML_KIT_HANDWRITING || BuildConfig.ENABLE_ML_KIT_SCANNERS) {
        add(
            Attribution(
                "Google ML Kit",
                "Handwriting recognition, text and barcode scanning, document scanner",
                "Copyright Google LLC",
                "Google APIs Terms of Service", null,
                "https://developers.google.com/ml-kit/terms",
            ),
        )
    }
    if (BuildConfig.ENABLE_LOCAL_LLM) {
        add(
            Attribution(
                "LiteRT-LM",
                "On-device language model runtime",
                "Copyright Google LLC",
                "Apache-2.0", "apache-2.0.txt",
                "https://github.com/google-ai-edge/LiteRT-LM",
            ),
        )
    }
    if (BuildConfig.ENABLE_WHISPER) {
        add(
            Attribution(
                "LiteRT",
                "On-device runtime for offline Whisper speech-to-text",
                "Copyright Google LLC",
                "Apache-2.0", "apache-2.0.txt",
                "https://github.com/google-ai-edge/LiteRT",
            ),
        )
        add(
            Attribution(
                "OpenAI Whisper",
                "Speech recognition model (TFLite conversions run on-device)",
                "Copyright OpenAI",
                "MIT", "mit.txt",
                "https://github.com/openai/whisper",
            ),
        )
        add(
            Attribution(
                "whisper_android",
                "Reference for the mel-spectrogram and tokenizer port",
                "Copyright Vilas Nandeshwar and contributors",
                "MIT", "mit.txt",
                "https://github.com/vilassn/whisper_android",
            ),
        )
    }
    add(
        Attribution(
            "Unicode CLDR & emoji data",
            "Emoji catalogue, names, keywords and skin-tone sequences",
            "Copyright Unicode, Inc.",
            "Unicode License v3", "unicode-3.0.txt",
            "https://www.unicode.org/license.txt",
        ),
    )
    add(
        Attribution(
            "CC-CEDICT",
            "Chinese Pinyin conversion dictionary (downloadable pack)",
            "Copyright MDBG and CC-CEDICT contributors",
            "CC BY-SA 4.0", "cc-by-sa-4.0.txt",
            "https://cc-cedict.org/",
        ),
    )
    add(
        Attribution(
            "OpenCC",
            "Simplified↔Traditional character map and Taiwan/Hong Kong vocabulary",
            "Copyright Carbo Kuo and OpenCC contributors",
            "Apache-2.0", "apache-2.0.txt",
            "https://github.com/BYVoid/OpenCC",
        ),
    )
    add(
        Attribution(
            "mozc & SudachiDict",
            "Japanese kana→kanji conversion dictionary (downloadable pack)",
            "Copyright Google Inc. and Works Applications Co., Ltd.",
            "BSD-3-Clause / Apache-2.0", "bsd-3-clause.txt",
            "https://github.com/google/mozc",
        ),
    )
    add(
        Attribution(
            "Editor colour palettes",
            "Built-in themes: Dracula, Nord, Solarized, Catppuccin, Tokyo Night",
            "Copyright the respective theme authors",
            "MIT", "mit-color-themes.txt",
            "https://github.com/dracula/dracula-theme",
        ),
    )
    add(
        Attribution(
            "Google Fonts",
            "Keyboard typefaces, downloaded on demand by the system",
            "Copyright the respective font authors",
            "SIL Open Font License 1.1", "ofl-1.1.txt",
            "https://fonts.google.com/attribution",
        ),
    )
}

/**
 * Services the tools call over the network. Nothing of theirs is bundled, so
 * these are attribution and terms links rather than licence texts.
 */
private val serviceAttributions: List<Attribution> = listOf(
    Attribution(
        "Brave Search", "Web and image search results", "",
        "Brave Search API terms", null,
        "https://brave.com/search/api/",
    ),
    Attribution(
        "KLIPY & GIPHY", "GIFs and stickers", "",
        "Provider API terms", null,
        "https://developers.giphy.com/",
    ),
    Attribution(
        "Wikipedia", "Article search and summaries", "",
        "CC BY-SA — article text keeps its own licence", null,
        "https://en.wikipedia.org/wiki/Wikipedia:Copyrights",
    ),
    Attribution(
        "Google Translate", "Translation tool", "",
        "Google Cloud terms", null,
        "https://cloud.google.com/terms",
    ),
    Attribution(
        "Open-Meteo", "Weather and geocoding", "",
        "CC BY 4.0", null,
        "https://open-meteo.com/en/license",
    ),
    Attribution(
        "Frankfurter & ExchangeRate-API", "Currency conversion rates", "",
        "Provider terms", null,
        "https://www.frankfurter.app/",
    ),
    Attribution(
        "Free Dictionary API", "Word definitions", "",
        "Provider terms", null,
        "https://dictionaryapi.dev/",
    ),
    Attribution(
        "Hugging Face", "Local model downloads", "",
        "Per-model licence, accepted on the model's page", null,
        "https://huggingface.co/terms-of-service",
    ),
    Attribution(
        "Anthropic, OpenAI & Google AI", "Optional bring-your-own-key AI tool", "",
        "Provider terms, under your own account", null,
        "https://www.anthropic.com/legal/consumer-terms",
    ),
)

@Composable
internal fun AboutSettings(
    onOpenLicenses: () -> Unit,
    onOpenLicenseText: (String) -> Unit,
    onOpenDebugLog: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val flavor = BuildConfig.FLAVOR.replaceFirstChar { it.uppercase() }

    SettingsGroup("Share") {
        item {
            NavRow("Share via Play Store", PLAY_STORE_URL.removePrefix("https://")) {
                shareLink(context, PLAY_STORE_URL)
            }
        }
        item {
            NavRow("Share via F-Droid", FDROID_URL.removePrefix("https://")) {
                shareLink(context, FDROID_URL)
            }
        }
        item {
            NavRow("Share via GitHub", SOURCE_URL.removePrefix("https://")) {
                shareLink(context, SOURCE_URL)
            }
        }
    }

    SettingsGroup("App") {
        item {
            NavRow(
                "Version",
                "$flavor build (${BuildConfig.BUILD_TYPE}) · code ${BuildConfig.VERSION_CODE}",
                value = BuildConfig.VERSION_NAME,
            ) {}
        }
        item {
            NavRow("Licence", "MIT — © 2026 Wasi Master") {
                onOpenLicenseText("mit-wmkeyboard.txt")
            }
        }
        item {
            NavRow("Source code", SOURCE_URL.removePrefix("https://")) {
                uriHandler.openUri(SOURCE_URL)
            }
        }
        item {
            NavRow(
                "Diagnostics",
                "What the keyboard recorded about itself — read it, or send it with a bug report",
                onClick = onOpenDebugLog,
            )
        }
    }
    CaptionText(
        "WM Keyboard is free software: you may use, modify and redistribute it, " +
            "provided the copyright notice and licence text travel with it.",
    )

    SettingsGroup("Third party") {
        item {
            NavRow("Open-source licences", "Libraries and data bundled in this build") {
                onOpenLicenses()
            }
        }
    }

    SettingsGroup("Word lists") {
        item {
            NavRow(
                "Dictionaries",
                "The English and Bengali word lists, bigrams and the loanword map are " +
                    "hand-curated for this project and covered by its licence",
            ) {}
        }
    }
}

@Composable
internal fun LicensesScreen(onOpenLicenseText: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current

    CaptionText(
        "Components bundled in this build. Tap a row for its full licence text; " +
            "rows without a bundled text open the provider's terms.",
    )
    SettingsGroup("Bundled in the app") {
        bundledAttributions.forEach { entry ->
            item {
                NavRow(entry.name, "${entry.used}\n${entry.copyright} · ${entry.license}") {
                    if (entry.licenseAsset != null) onOpenLicenseText(entry.licenseAsset)
                    else uriHandler.openUri(entry.url)
                }
            }
        }
    }
    SettingsGroup("Online services") {
        serviceAttributions.forEach { entry ->
            item {
                NavRow(entry.name, "${entry.used} · ${entry.license}") {
                    uriHandler.openUri(entry.url)
                }
            }
        }
    }
    CaptionText(
        "Online services are contacted only by the tool that uses them, and only " +
            "when you open it. None of their code ships inside the app.",
    )
}

/** Renders one bundled licence file verbatim. */
@Composable
internal fun LicenseTextScreen(assetName: String) {
    val context = LocalContext.current
    var text by remember(assetName) { mutableStateOf<String?>(null) }
    LaunchedEffect(assetName) {
        text = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("licenses/$assetName").use { it.readBytes().decodeToString() }
            }.getOrElse { "Licence text unavailable." }
        }
    }
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        // Licence texts are hard-wrapped at 80 columns; scroll sideways rather
        // than reflow, so the original layout stays intact.
        Text(
            text.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )
    }
}
