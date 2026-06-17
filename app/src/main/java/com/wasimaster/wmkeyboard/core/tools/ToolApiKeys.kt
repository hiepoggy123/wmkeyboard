package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings

/**
 * Resolves the effective API key for each network tool: a key the user
 * pasted into the tool's settings wins; otherwise the key baked into the
 * build (from local.properties / environment variables — see
 * app/build.gradle.kts) applies; blank means the tool shows its
 * "needs an API key" state (translate instead falls back to the free
 * endpoint, which needs none).
 */
object ToolApiKeys {

    fun klipy(settings: KeyboardSettings): String =
        settings.klipyApiKey.ifBlank { BuildConfig.KLIPY_API_KEY }

    fun giphy(settings: KeyboardSettings): String =
        settings.giphyApiKey.ifBlank { BuildConfig.GIPHY_API_KEY }

    /** Which GIF/sticker providers can actually serve requests. */
    fun gifSources(settings: KeyboardSettings): List<GifSource> = buildList {
        if (klipy(settings).isNotBlank()) add(GifSource.KLIPY)
        if (giphy(settings).isNotBlank()) add(GifSource.GIPHY)
    }

    fun brave(settings: KeyboardSettings): String =
        settings.braveApiKey.ifBlank { BuildConfig.BRAVE_API_KEY }

    /** Whether the web/image search tools have a usable Brave key. */
    fun hasSearchProvider(settings: KeyboardSettings): Boolean =
        brave(settings).isNotBlank()

    fun translate(settings: KeyboardSettings): String =
        settings.translateApiKey.ifBlank { BuildConfig.TRANSLATE_API_KEY }

    /** For the settings screens: whether the build ships its own key. */
    val builtInKlipy: Boolean get() = BuildConfig.KLIPY_API_KEY.isNotBlank()
    val builtInGiphy: Boolean get() = BuildConfig.GIPHY_API_KEY.isNotBlank()
    val builtInBrave: Boolean get() = BuildConfig.BRAVE_API_KEY.isNotBlank()
    val builtInTranslate: Boolean get() = BuildConfig.TRANSLATE_API_KEY.isNotBlank()
}
