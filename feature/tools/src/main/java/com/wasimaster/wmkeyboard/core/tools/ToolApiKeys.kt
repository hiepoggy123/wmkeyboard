package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.config.BuildConfig
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.hasSearchKey

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

    /**
     * Sources the sticker tool can offer. The user's own packs need no key,
     * so this is never empty — the sticker panel can't reach a "needs a key"
     * state the way the GIF panel can.
     */
    fun stickerSources(settings: KeyboardSettings): List<GifSource> =
        gifSources(settings) + GifSource.LOCAL

    fun brave(settings: KeyboardSettings): String =
        settings.braveApiKey.ifBlank { BuildConfig.BRAVE_API_KEY }

    /**
     * Whether the web/image search tools have a usable Brave key. Delegates to
     * `hasSearchKey`, which is where the toolbar and the settings screens ask
     * the same question — one answer, so a tool can never be on the bar while
     * its panel says the key is missing.
     */
    fun hasSearchProvider(settings: KeyboardSettings): Boolean = hasSearchKey(settings)

    fun translate(settings: KeyboardSettings): String =
        settings.translateApiKey.ifBlank { BuildConfig.TRANSLATE_API_KEY }

    fun unsplash(settings: KeyboardSettings): String =
        settings.photoBackground.unsplashApiKey.ifBlank { BuildConfig.UNSPLASH_API_KEY }

    fun pexels(settings: KeyboardSettings): String =
        settings.photoBackground.pexelsApiKey.ifBlank { BuildConfig.PEXELS_API_KEY }

    /** Which background-photo providers can actually serve requests. */
    fun photoSources(settings: KeyboardSettings): List<PhotoSource> = buildList {
        if (unsplash(settings).isNotBlank()) add(PhotoSource.UNSPLASH)
        if (pexels(settings).isNotBlank()) add(PhotoSource.PEXELS)
    }

    /** Resolves each provider's key for the photo client's dispatch. */
    fun photoKeys(settings: KeyboardSettings): PhotoSearchClient.Keys =
        PhotoSearchClient.Keys { source ->
            when (source) {
                PhotoSource.UNSPLASH -> unsplash(settings)
                PhotoSource.PEXELS -> pexels(settings)
            }
        }

    /** For the settings screens: whether the build ships its own key. */
    val builtInKlipy: Boolean get() = BuildConfig.KLIPY_API_KEY.isNotBlank()
    val builtInGiphy: Boolean get() = BuildConfig.GIPHY_API_KEY.isNotBlank()
    val builtInBrave: Boolean get() = BuildConfig.BRAVE_API_KEY.isNotBlank()
    val builtInTranslate: Boolean get() = BuildConfig.TRANSLATE_API_KEY.isNotBlank()
    val builtInUnsplash: Boolean get() = BuildConfig.UNSPLASH_API_KEY.isNotBlank()
    val builtInPexels: Boolean get() = BuildConfig.PEXELS_API_KEY.isNotBlank()
}
