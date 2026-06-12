package com.wasimaster.wmkeyboard.core.tools

import com.wasimaster.wmkeyboard.BuildConfig
import com.wasimaster.wmkeyboard.core.settings.KeyboardSettings
import com.wasimaster.wmkeyboard.core.settings.WebSearchProvider

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

    /**
     * Which GIF/sticker providers can actually serve requests: KLIPY and
     * GIPHY need their keys, Google needs the Programmable Search key, an
     * engine id resolving for this mode ([stickers] picks which dedicated
     * cx applies) and the tool's own opt-in (its 100-requests/day quota is
     * shared with the web/image search tools).
     */
    fun gifSources(settings: KeyboardSettings, stickers: Boolean): List<GifSource> = buildList {
        if (klipy(settings).isNotBlank()) add(GifSource.KLIPY)
        if (giphy(settings).isNotBlank()) add(GifSource.GIPHY)
        val cx = if (stickers) googleSearchCxStickers(settings) else googleSearchCxGifs(settings)
        if (settings.gifUseGoogle && googleSearch(settings).isNotBlank() && cx.isNotBlank()) {
            add(GifSource.GOOGLE)
        }
    }

    fun brave(settings: KeyboardSettings): String =
        settings.braveApiKey.ifBlank { BuildConfig.BRAVE_API_KEY }

    fun googleSearch(settings: KeyboardSettings): String =
        settings.googleSearchApiKey.ifBlank { BuildConfig.GOOGLE_SEARCH_API_KEY }

    private fun hasBrave(settings: KeyboardSettings) = brave(settings).isNotBlank()

    private fun hasGoogleSearch(settings: KeyboardSettings) =
        googleSearch(settings).isNotBlank() && googleSearchCx(settings).isNotBlank()

    /**
     * Which backend the web/image search tools should actually use: the
     * preferred provider when its key is set, otherwise the other one if
     * it has a key, otherwise null ("needs an API key" panel).
     */
    fun activeSearchProvider(settings: KeyboardSettings): WebSearchProvider? {
        val braveReady = hasBrave(settings)
        val googleReady = hasGoogleSearch(settings)
        return when (settings.searchProvider) {
            WebSearchProvider.BRAVE -> if (braveReady) WebSearchProvider.BRAVE
                else WebSearchProvider.GOOGLE.takeIf { googleReady }
            WebSearchProvider.GOOGLE -> if (googleReady) WebSearchProvider.GOOGLE
                else WebSearchProvider.BRAVE.takeIf { braveReady }
        }
    }

    /** Engine id for web search — also the fallback for every other tool. */
    fun googleSearchCx(settings: KeyboardSettings): String =
        settings.googleSearchCx.ifBlank { BuildConfig.GOOGLE_SEARCH_CX }

    /**
     * Per-tool engine ids. Each resolves user setting → built-in → the
     * general web-search cx, so a single engine still serves everything
     * when no dedicated ones are configured — but a dedicated engine (say
     * a 50-GIF-site one in the GIFs slot) is only ever used by its own
     * tool, never by web/image/sticker search.
     */
    fun googleSearchCxImages(settings: KeyboardSettings): String =
        settings.googleSearchCxImages.ifBlank { BuildConfig.GOOGLE_SEARCH_CX_IMAGES }
            .ifBlank { googleSearchCx(settings) }

    fun googleSearchCxGifs(settings: KeyboardSettings): String =
        settings.googleSearchCxGifs.ifBlank { BuildConfig.GOOGLE_SEARCH_CX_GIFS }
            .ifBlank { googleSearchCx(settings) }

    fun googleSearchCxStickers(settings: KeyboardSettings): String =
        settings.googleSearchCxStickers.ifBlank { BuildConfig.GOOGLE_SEARCH_CX_STICKERS }
            .ifBlank { googleSearchCx(settings) }

    fun translate(settings: KeyboardSettings): String =
        settings.translateApiKey.ifBlank { BuildConfig.TRANSLATE_API_KEY }

    /** For the settings screens: whether the build ships its own key. */
    val builtInKlipy: Boolean get() = BuildConfig.KLIPY_API_KEY.isNotBlank()
    val builtInGiphy: Boolean get() = BuildConfig.GIPHY_API_KEY.isNotBlank()
    val builtInBrave: Boolean get() = BuildConfig.BRAVE_API_KEY.isNotBlank()
    val builtInGoogleSearch: Boolean
        get() = BuildConfig.GOOGLE_SEARCH_API_KEY.isNotBlank() && BuildConfig.GOOGLE_SEARCH_CX.isNotBlank()
    val builtInGoogleCxImages: Boolean get() = BuildConfig.GOOGLE_SEARCH_CX_IMAGES.isNotBlank()
    val builtInGoogleCxGifs: Boolean get() = BuildConfig.GOOGLE_SEARCH_CX_GIFS.isNotBlank()
    val builtInGoogleCxStickers: Boolean get() = BuildConfig.GOOGLE_SEARCH_CX_STICKERS.isNotBlank()
    val builtInTranslate: Boolean get() = BuildConfig.TRANSLATE_API_KEY.isNotBlank()
}
