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

    fun tenor(settings: KeyboardSettings): String =
        settings.tenorApiKey.ifBlank { BuildConfig.TENOR_API_KEY }

    fun googleSearch(settings: KeyboardSettings): String =
        settings.googleSearchApiKey.ifBlank { BuildConfig.GOOGLE_SEARCH_API_KEY }

    fun googleSearchCx(settings: KeyboardSettings): String =
        settings.googleSearchCx.ifBlank { BuildConfig.GOOGLE_SEARCH_CX }

    fun translate(settings: KeyboardSettings): String =
        settings.translateApiKey.ifBlank { BuildConfig.TRANSLATE_API_KEY }

    /** For the settings screens: whether the build ships its own key. */
    val builtInTenor: Boolean get() = BuildConfig.TENOR_API_KEY.isNotBlank()
    val builtInGoogleSearch: Boolean
        get() = BuildConfig.GOOGLE_SEARCH_API_KEY.isNotBlank() && BuildConfig.GOOGLE_SEARCH_CX.isNotBlank()
    val builtInTranslate: Boolean get() = BuildConfig.TRANSLATE_API_KEY.isNotBlank()
}
