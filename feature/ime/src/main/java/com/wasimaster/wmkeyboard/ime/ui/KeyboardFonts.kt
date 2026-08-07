package com.wasimaster.wmkeyboard.ime.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as DownloadableFont
import com.wasimaster.wmkeyboard.ime.R
import com.wasimaster.wmkeyboard.core.fonts.FontStore
import com.wasimaster.wmkeyboard.core.script.ScriptId
import com.wasimaster.wmkeyboard.core.settings.EmojiFontChoice
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Resolves the user's font choices into Compose [FontFamily]s.
 *
 * Google Fonts families are fetched through the Google Play Services font
 * provider (the system-level downloadable-fonts mechanism, cached
 * on-device after the first use); if the provider is missing or offline
 * the async font simply falls back to the platform default. Custom fonts
 * are files the user imported, copied into the app's private storage.
 *
 * Missing glyphs — a Latin display font on Bengali keys, for example —
 * fall back to the system font per-glyph, so exotic choices never leave
 * keys blank.
 */
object KeyboardFonts {

    const val DEFAULT_ID = "default"
    const val CUSTOM_ID = "custom"
    const val CUSTOM_BENGALI_ID = "custom_bn"
    private const val GOOGLE_PREFIX = "google:"

    /**
     * Curated Google Fonts choices for the English font: workhorse sans
     * faces, a few serifs, monos and display faces.
     */
    val googleFonts: List<String> = listOf(
        "Roboto", "Inter", "Open Sans", "Lato", "Montserrat", "Poppins",
        "Nunito", "Rubik", "Ubuntu", "Source Sans 3",
        "Merriweather", "Lora", "Playfair Display", "Roboto Slab",
        "Roboto Mono", "JetBrains Mono",
        "Comfortaa", "Pacifico", "Caveat", "Orbitron",
    )

    /**
     * Bengali-script Google Fonts families, used while a Bengali input
     * mode is active. All of these also carry Latin glyphs, so mixed
     * strings (suggestion strip in Avro mode) stay in one face.
     */
    val bengaliGoogleFonts: List<String> = listOf(
        "Hind Siliguri", "Noto Serif Bengali",
        "Anek Bangla", "Baloo Da 2", "Tiro Bangla", "Atma", "Mina", "Galada",
    )

    /**
     * The imported-font file id for a script's picker, for the scripts whose
     * picker offers an import. Bengali's id predates per-script fonts and is
     * kept verbatim so an already-imported file keeps working.
     */
    private val customScriptFontIds: Map<ScriptId, String> = mapOf(
        ScriptId.BENGALI to CUSTOM_BENGALI_ID,
    )

    /** Where a script's imported font file lives, or null if it allows none. */
    fun customScriptFontFile(context: Context, script: ScriptId): File? =
        customScriptFontIds[script]?.let { customFontFile(context, it) }

    /** The imported-font id a script's picker writes, or null if it allows none. */
    fun customScriptFontId(script: ScriptId): String? = customScriptFontIds[script]

    /**
     * The default (automatic) Noto face each non-Latin script draws with, so a
     * script always gets a correct glyph even when the user's key font is a
     * Latin-only display face. Latin, Cyrillic and Greek are absent — they ride
     * the user's [keyFontId] choice (Noto Sans, the system default, covers all
     * three). Scripts here may additionally expose a handful of pickable
     * alternatives via [scriptFontChoices]; when the user has not chosen one this
     * face is used. A name the Google Fonts provider does not recognise simply
     * falls back to the system face, which carries the glyph anyway.
     */
    private val scriptGoogleFonts: Map<ScriptId, String> = mapOf(
        ScriptId.BENGALI to "Noto Sans Bengali",
        ScriptId.ARMENIAN to "Noto Sans Armenian",
        ScriptId.GEORGIAN to "Noto Sans Georgian",
        ScriptId.ARABIC to "Noto Naskh Arabic",
        ScriptId.HEBREW to "Noto Sans Hebrew",
        ScriptId.THAANA to "Noto Sans Thaana",
        ScriptId.DEVANAGARI to "Noto Sans Devanagari",
        ScriptId.GURMUKHI to "Noto Sans Gurmukhi",
        ScriptId.GUJARATI to "Noto Sans Gujarati",
        ScriptId.ORIYA to "Noto Sans Oriya",
        ScriptId.TAMIL to "Noto Sans Tamil",
        ScriptId.TELUGU to "Noto Sans Telugu",
        ScriptId.KANNADA to "Noto Sans Kannada",
        ScriptId.MALAYALAM to "Noto Sans Malayalam",
        ScriptId.SINHALA to "Noto Sans Sinhala",
        ScriptId.THAI to "Noto Sans Thai",
        ScriptId.LAO to "Noto Sans Lao",
        ScriptId.KHMER to "Noto Sans Khmer",
        ScriptId.MYANMAR to "Noto Sans Myanmar",
        ScriptId.ETHIOPIC to "Noto Sans Ethiopic",
        ScriptId.HANGUL to "Noto Sans KR",
        ScriptId.JAPANESE to "Noto Sans JP",
        ScriptId.HAN to "Noto Sans SC",
        // Notations rather than languages, but the same problem: device fonts
        // rarely carry the SMP Musical Symbols block, and braille dot-cells
        // deserve a face that has all 256 patterns. Missing glyphs still fall
        // back to the system font per-glyph, so a GMS-less device degrades to
        // whatever coverage it has rather than blank keys.
        ScriptId.MUSIC to "Noto Music",
        ScriptId.BRAILLE to "Noto Sans Symbols 2",
    )

    /**
     * A curated set of pickable font alternatives for one non-Latin script, shown
     * in settings only while a language using that script is enabled. [fonts] are
     * Google Fonts *alternatives* to the script's automatic [scriptGoogleFonts]
     * face — the picker lists that automatic face first, so these never repeat it.
     * All carry the script's glyphs; anything missing falls back to the system
     * font per-glyph. Scripts absent from this list (e.g. Thaana, which has no
     * second good face) still render with their automatic Noto face — they simply
     * get no picker.
     */
    data class ScriptFontChoices(
        val script: ScriptId,
        @StringRes val labelRes: Int,
        val sample: String,
        val fonts: List<String>,
    )

    val scriptFontChoices: List<ScriptFontChoices> = listOf(
        // Bengali leads the list because it is the one script here that also
        // takes an imported file, so its picker is the longest.
        ScriptFontChoices(
            ScriptId.BENGALI, R.string.ime_font_script_bengali_label, "আমি ভালো আছি · কখগঘঙ চছজঝঞ",
            bengaliGoogleFonts,
        ),
        ScriptFontChoices(
            ScriptId.ARABIC, R.string.ime_font_script_arabic_label, "السلام عليكم · ابجد",
            listOf("Noto Sans Arabic", "Noto Kufi Arabic", "Amiri", "Cairo", "Tajawal", "Markazi Text", "Reem Kufi"),
        ),
        ScriptFontChoices(
            ScriptId.HEBREW, R.string.ime_font_script_hebrew_label, "שלום · אבגד הוז",
            listOf("Noto Serif Hebrew", "Rubik", "Heebo", "Assistant", "Frank Ruhl Libre", "David Libre", "Secular One"),
        ),
        ScriptFontChoices(
            ScriptId.ARMENIAN, R.string.ime_font_script_armenian_label, "Բարեւ · Աբգդ",
            listOf("Noto Serif Armenian"),
        ),
        ScriptFontChoices(
            ScriptId.GEORGIAN, R.string.ime_font_script_georgian_label, "გამარჯობა · აბგდ",
            listOf("Noto Serif Georgian"),
        ),
        ScriptFontChoices(
            ScriptId.DEVANAGARI, R.string.ime_font_script_devanagari_label, "नमस्ते · कखगघ",
            listOf("Noto Serif Devanagari", "Hind", "Mukta", "Baloo 2", "Tiro Devanagari Hindi", "Rozha One", "Kalam"),
        ),
        ScriptFontChoices(
            ScriptId.GURMUKHI, R.string.ime_font_script_gurmukhi_label, "ਸਤ ਸ੍ਰੀ ਅਕਾਲ · ਕਖਗ",
            listOf("Noto Serif Gurmukhi", "Mukta Mahee", "Baloo Paaji 2"),
        ),
        ScriptFontChoices(
            ScriptId.GUJARATI, R.string.ime_font_script_gujarati_label, "નમસ્તે · કખગઘ",
            listOf("Noto Serif Gujarati", "Mukta Vaani", "Hind Vadodara", "Baloo Bhai 2", "Shrikhand"),
        ),
        ScriptFontChoices(
            ScriptId.ORIYA, R.string.ime_font_script_odia_label, "ନମସ୍କାର · କଖଗ",
            listOf("Noto Serif Oriya", "Baloo Bhaina 2"),
        ),
        ScriptFontChoices(
            ScriptId.TAMIL, R.string.ime_font_script_tamil_label, "வணக்கம் · கஙச",
            listOf("Noto Serif Tamil", "Hind Madurai", "Mukta Malar", "Baloo Thambi 2", "Catamaran", "Pavanam"),
        ),
        ScriptFontChoices(
            ScriptId.TELUGU, R.string.ime_font_script_telugu_label, "నమస్కారం · కఖగ",
            listOf("Noto Serif Telugu", "Hind Guntur", "Mallanna", "Mandali", "Ramabhadra", "Baloo Tammudu 2", "Suranna"),
        ),
        ScriptFontChoices(
            ScriptId.KANNADA, R.string.ime_font_script_kannada_label, "ನಮಸ್ಕಾರ · ಕಖಗ",
            listOf("Noto Serif Kannada", "Baloo Tamma 2", "Benne", "Akaya Kanadaka"),
        ),
        ScriptFontChoices(
            ScriptId.MALAYALAM, R.string.ime_font_script_malayalam_label, "നമസ്കാരം · കഖഗ",
            listOf("Noto Serif Malayalam", "Manjari", "Baloo Chettan 2", "Gayathri", "Anek Malayalam", "Chilanka"),
        ),
        ScriptFontChoices(
            ScriptId.SINHALA, R.string.ime_font_script_sinhala_label, "ආයුබෝවන් · කඛග",
            listOf("Noto Serif Sinhala", "Abhaya Libre", "Yaldevi", "Gemunu Libre"),
        ),
        ScriptFontChoices(
            ScriptId.THAI, R.string.ime_font_script_thai_label, "สวัสดี · กขคง",
            listOf("Noto Serif Thai", "Sarabun", "Kanit", "Prompt", "Mitr", "Pridi", "Bai Jamjuree", "K2D", "Mali"),
        ),
        ScriptFontChoices(
            ScriptId.LAO, R.string.ime_font_script_lao_label, "ສະບາຍດີ · ກຂຄ",
            listOf("Noto Serif Lao", "Noto Sans Lao Looped"),
        ),
        ScriptFontChoices(
            ScriptId.KHMER, R.string.ime_font_script_khmer_label, "សួស្តី · កខគ",
            listOf("Noto Serif Khmer", "Battambang", "Hanuman", "Suwannaphum", "Koulen", "Moul", "Content", "Kantumruy Pro"),
        ),
        ScriptFontChoices(
            ScriptId.MYANMAR, R.string.ime_font_script_myanmar_label, "မင်္ဂလာပါ · ကခဂ",
            listOf("Noto Serif Myanmar", "Padauk"),
        ),
        ScriptFontChoices(
            ScriptId.ETHIOPIC, R.string.ime_font_script_ethiopic_label, "ሰላም · ሀለሐመ",
            listOf("Noto Serif Ethiopic", "Abyssinica SIL"),
        ),
        ScriptFontChoices(
            ScriptId.HANGUL, R.string.ime_font_script_korean_label, "안녕하세요 · 가나다라",
            listOf(
                "Noto Serif KR",
                "Nanum Gothic",
                "Nanum Myeongjo",
                "Gowun Dodum",
                "Do Hyeon",
                "Jua",
                "Gothic A1",
                "Black Han Sans",
                "Sunflower",
            ),
        ),
        ScriptFontChoices(
            ScriptId.JAPANESE, R.string.ime_font_script_japanese_label, "こんにちは · 日本語 あいう",
            listOf(
                "Noto Serif JP",
                "M PLUS 1p",
                "M PLUS Rounded 1c",
                "Sawarabi Mincho",
                "Sawarabi Gothic",
                "Kosugi Maru",
                "Zen Maru Gothic",
                "Shippori Mincho",
                "Dela Gothic One",
            ),
        ),
        ScriptFontChoices(
            ScriptId.HAN, R.string.ime_font_script_chinese_label, "你好 · 汉字 中文",
            listOf(
                "Noto Serif SC",
                "Noto Sans TC",
                "Noto Serif TC",
                "ZCOOL XiaoWei",
                "ZCOOL QingKe HuangYou",
                "ZCOOL KuaiLe",
                "Ma Shan Zheng",
                "Long Cang",
                "Zhi Mang Xing",
            ),
        ),
    )

    private val scriptFontChoicesById: Map<ScriptId, ScriptFontChoices> =
        scriptFontChoices.associateBy { it.script }

    fun scriptFontChoices(scriptId: ScriptId): ScriptFontChoices? = scriptFontChoicesById[scriptId]

    /**
     * The [FontFamily] a [scriptId] wants, honouring the user's per-script pick
     * ([selectedId]: a `google:<Name>` id, an imported-file id, or [DEFAULT_ID])
     * and otherwise the script's automatic Noto face. Null for the scripts that
     * follow the user's own font choice (Latin/Cyrillic/Greek). Used by the
     * keyboard theme to pick a face per active script.
     */
    fun scriptFamily(
        context: Context,
        scriptId: ScriptId,
        selectedId: String = DEFAULT_ID,
    ): FontFamily? {
        if (selectedId != DEFAULT_ID) family(context, selectedId)?.let { return it }
        return scriptGoogleFonts[scriptId]?.let { googleFamily(it) }
    }

    fun googleId(name: String): String = GOOGLE_PREFIX + name

    /**
     * The generic name for a font id, without asking the installed-font library
     * for the real name of a font it holds. [displayName] does ask, and is what
     * a screen that shows the user's own fonts wants.
     */
    fun genericDisplayName(context: Context, id: String, customName: String = ""): String = when {
        id == CUSTOM_ID || id == CUSTOM_BENGALI_ID ->
            customName.ifBlank { context.getString(R.string.ime_font_custom_label) }
        id.startsWith(GOOGLE_PREFIX) -> id.removePrefix(GOOGLE_PREFIX)
        FontStore.storeIdOf(id) != null -> context.getString(R.string.ime_font_installed_label)
        else -> context.getString(R.string.ime_font_system_default_label)
    }

    /**
     * [genericDisplayName] with the installed-font library available, so a font
     * from the library reads as its own name rather than a generic label.
     */
    fun displayName(context: Context, id: String, customName: String = ""): String {
        val storeId = FontStore.storeIdOf(id)
            ?: return genericDisplayName(context, id, customName)
        return FontStore.get(context).font(storeId)?.name
            ?: context.getString(R.string.ime_font_installed_label)
    }

    fun customFontFile(context: Context): File =
        File(context.filesDir, "fonts/custom_font.ttf")

    /** Where the imported file behind a `custom…` id lives. */
    private fun customFontFile(context: Context, customId: String): File = when (customId) {
        CUSTOM_BENGALI_ID -> File(context.filesDir, "fonts/custom_font_bn.ttf")
        else -> customFontFile(context)
    }

    fun customEmojiFontFile(context: Context): File =
        File(context.filesDir, "fonts/custom_emoji.ttf")

    private val provider by lazy {
        GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs,
        )
    }

    private val cache = HashMap<String, FontFamily>()

    /** The family for a stored font id, or null for the system default. */
    fun family(context: Context, id: String): FontFamily? = when {
        id == CUSTOM_ID -> fileFamily(customFontFile(context))
        id == CUSTOM_BENGALI_ID -> fileFamily(customFontFile(context, CUSTOM_BENGALI_ID))
        id.startsWith(GOOGLE_PREFIX) -> googleFamily(id.removePrefix(GOOGLE_PREFIX))
        // A font installed into the library, from a repository or the user's
        // own file. Null when it has since been deleted, which falls back to
        // the system face rather than leaving keys blank.
        else -> installedFontFile(context, id)?.let { fileFamily(it) }
    }

    /**
     * The file behind an `installed:<id>` font id, if it is still there.
     *
     * What follows the prefix resolves as the store id first, then as an
     * installed font's display name. The id is minted per device at install
     * time, so a *distributed* theme has no way to know it — but an
     * addon-installed font keeps its catalogue name on every device, which is
     * what a theme's `fontId` can carry (`installed:Bloxat`) next to a
     * `requires` on that font's addon; the sound player resolves
     * `soundCustomId` the same way. Text faces only, so an emoji font that
     * happens to share the name can never end up on the key labels.
     */
    private fun installedFontFile(context: Context, id: String): File? {
        val storeId = FontStore.storeIdOf(id) ?: return null
        val store = FontStore.get(context)
        store.existingFileFor(storeId)?.let { return it }
        val byName = store.textFonts().firstOrNull { it.name.equals(storeId, ignoreCase = true) }
        return byName?.let { store.existingFileFor(it.id) }
    }

    /**
     * The family emojis render with, or null for the system emoji font.
     *
     * [installedId] is the [FontStore] id behind [EmojiFontChoice.INSTALLED] —
     * an emoji face from an addon repository or the font library. A blank id, or
     * one whose file has since gone, falls back to the system font rather than
     * leaving the panel empty.
     */
    fun emojiFamily(
        context: Context,
        choice: EmojiFontChoice,
        installedId: String = "",
    ): FontFamily? = when (choice) {
        EmojiFontChoice.SYSTEM -> null
        EmojiFontChoice.NOTO -> googleFamily(NOTO_COLOR_EMOJI)
        EmojiFontChoice.CUSTOM -> fileFamily(customEmojiFontFile(context))
        EmojiFontChoice.INSTALLED -> installedEmojiFile(context, installedId)?.let { fileFamily(it) }
    }

    /** The file behind an installed emoji font id, if it is still there. */
    private fun installedEmojiFile(context: Context, installedId: String): File? =
        installedId.takeIf { it.isNotBlank() }
            ?.let { FontStore.get(context).existingFileFor(it) }

    /**
     * The font file emojis are drawn from, whose tables say which emoji it can
     * actually draw and how they have to be spelled — see
     * [com.wasimaster.wmkeyboard.core.emoji.EmojiFontCoverage].
     *
     * Only a chosen file resolves here. [EmojiFontChoice.SYSTEM] and
     * [EmojiFontChoice.NOTO] return null, meaning "the system emoji font": Noto
     * is fetched asynchronously and has no file to read, and it exists
     * precisely to fill the system font's gaps.
     */
    fun emojiFontFile(
        context: Context,
        choice: EmojiFontChoice,
        installedId: String = "",
    ): File? = when (choice) {
        EmojiFontChoice.SYSTEM, EmojiFontChoice.NOTO -> null
        EmojiFontChoice.CUSTOM -> customEmojiFontFile(context)
        EmojiFontChoice.INSTALLED -> installedEmojiFile(context, installedId)
    }?.takeIf { it.exists() }

    /**
     * The chosen emoji font as a [Typeface] — what a `Canvas` draws with, as
     * opposed to the [FontFamily] Compose lays out with.
     *
     * Null means "the phone's own emoji font", which is both what
     * [EmojiFontChoice.SYSTEM] asks for and what everything else falls back to.
     * [EmojiFontChoice.NOTO] has no file of its own, so it is fetched from the
     * same downloadable-font provider Compose draws it with and then kept for
     * the process — the provider caches the file itself, so this is a lookup
     * rather than a download after the first time.
     *
     * Suspends: the provider call is a binder round trip and, once in the life
     * of a device, an actual download.
     */
    suspend fun emojiTypeface(
        context: Context,
        choice: EmojiFontChoice,
        installedId: String = "",
    ): Typeface? = when (choice) {
        EmojiFontChoice.SYSTEM -> null
        EmojiFontChoice.NOTO -> notoEmojiTypeface(context)
        EmojiFontChoice.CUSTOM, EmojiFontChoice.INSTALLED ->
            emojiFontFile(context, choice, installedId)
                ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
    }

    private suspend fun notoEmojiTypeface(context: Context): Typeface? {
        notoEmoji?.let { return it }
        val request = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            NOTO_COLOR_EMOJI,
            R.array.com_google_android_gms_fonts_certs,
        )
        val fetched = suspendCancellableCoroutine { continuation ->
            FontsContractCompat.requestFont(
                context,
                request,
                object : FontsContractCompat.FontRequestCallback() {
                    override fun onTypefaceRetrieved(typeface: Typeface) {
                        if (continuation.isActive) continuation.resume(typeface)
                    }

                    // Every failure is the same failure here: no Play Services,
                    // no network on a device that has never fetched it, or a
                    // provider that does not know the name. All of them mean
                    // "draw it in the phone's own font instead".
                    override fun onTypefaceRequestFailed(reason: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
        return fetched?.also { notoEmoji = it }
    }

    @Volatile
    private var notoEmoji: Typeface? = null

    private const val NOTO_COLOR_EMOJI = "Noto Color Emoji"

    /** Family for any Google Fonts name (also used directly by tool panels). */
    fun googleFamily(name: String): FontFamily = cache.getOrPut("google:$name") {
        val font = GoogleFont(name)
        FontFamily(
            DownloadableFont(googleFont = font, fontProvider = provider, weight = FontWeight.Normal),
            DownloadableFont(googleFont = font, fontProvider = provider, weight = FontWeight.Medium),
            DownloadableFont(googleFont = font, fontProvider = provider, weight = FontWeight.SemiBold),
            DownloadableFont(googleFont = font, fontProvider = provider, weight = FontWeight.Bold),
        )
    }

    private fun fileFamily(file: File): FontFamily? {
        if (!file.exists()) return null
        // Key on the modification time so re-importing a different file
        // under the same fixed name is picked up immediately.
        val key = "file:${file.path}:${file.lastModified()}"
        cache[key]?.let { return it }
        // Reject files Android cannot parse up front; otherwise the lazy
        // resolver would throw mid-frame instead.
        val parsed = runCatching { android.graphics.Typeface.createFromFile(file) }.getOrNull()
            ?: return null
        if (parsed == android.graphics.Typeface.DEFAULT) return null
        return FontFamily(Font(file)).also { cache[key] = it }
    }
}
