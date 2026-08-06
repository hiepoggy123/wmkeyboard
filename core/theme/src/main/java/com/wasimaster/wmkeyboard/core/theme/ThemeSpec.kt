package com.wasimaster.wmkeyboard.core.theme

import android.content.Context
import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.wasimaster.wmkeyboard.theme.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

/**
 * A complete keyboard theme. Colors are ARGB longs (0xAARRGGBB) so alpha is
 * first-class — a translucent board over a background image, ghosted keys,
 * and so on. Nullable fields fall back to a value derived from the required
 * ones (see KbTheme resolution), so a minimal theme only needs the core
 * colors; nullable radii fall back to the global appearance sliders.
 */
/** How a [GradientSpec] paints its color stops. */
enum class GradientType { LINEAR, RADIAL, SWEEP }

/**
 * A multi-stop gradient. Colors are ARGB longs like everything else in the
 * theme, so translucent stops work (a see-through gradient over a background
 * image acts as a tinted scrim). [angleDeg] rotates LINEAR and SWEEP
 * gradients; RADIAL ignores it.
 */
@Serializable
data class GradientSpec(
    val colors: List<Long>,
    val type: GradientType = GradientType.LINEAR,
    val angleDeg: Float = 45f,
)

/**
 * Outline used for every key. ROUNDED, CUT and TICKET follow the corner-radius
 * sliders; the rest set their own corners from the key's size, because their
 * whole look is the proportion (a half-height arch, a hexagon's points).
 *
 * Order is the order the shape picker lists them in: the four plain outlines
 * first, then the decorative ones.
 *
 * A value added here is not readable by an older build — `ThemeCodec.decode`
 * drops a theme whose enum name it does not know — so a theme exported with
 * HEXAGON does not import into a build from before it existed. That is the
 * price of the field being an enum, which it already was.
 */
enum class KeyShapeKind {
    ROUNDED,
    SHARP,
    PILL,
    CUT,
    SQUIRCLE,
    ARCH,
    LEAF,
    SLANT,
    HEXAGON,
    SCALLOP,
    TICKET,
    CIRCLE,
}

/**
 * A [KeyShapeKind] by name, or null when this build has no such shape — for
 * the shape fields that travel as strings so an unknown name costs the field
 * rather than the whole theme.
 */
fun keyShapeKindOrNull(name: String?): KeyShapeKind? =
    name?.let { wanted -> KeyShapeKind.entries.firstOrNull { it.name == wanted } }

/**
 * Who took a background photo and where it came from, kept beside the image so
 * the credit survives an export, an import and going offline.
 *
 * Both photo services require the photographer to be named wherever their photo
 * is shown, with a link back, so this is not decoration: an image with no
 * attribution beside it is a licence problem rather than a cosmetic one.
 *
 * [provider] is a plain string, not an enum. kotlinx.serialization throws on an
 * enum constant it does not know, and [ThemeCodec.decode] swallows that in a
 * `runCatching` — so a theme made by a later build that added a third service
 * would silently fail to decode **in full**, losing the user's colours and not
 * merely the credit. A name this build does not recognise is shown as it stands.
 */
@Serializable
data class PhotoAttribution(
    val provider: String,
    val photoId: String,
    val photographer: String,
    /** Stored untagged; referral parameters are added where the link is used. */
    val photographerUrl: String = "",
    val photoUrl: String = "",
    val altText: String = "",
    val avgColor: String = "",
)

/**
 * Live theme animation. FLOW drifts the board gradient along its axis;
 * HUE_CYCLE slowly rotates the hue of the board gradient (or the solid board
 * color). Only runs while the keyboard is on screen.
 */
enum class ThemeAnimation { NONE, FLOW, HUE_CYCLE }

@Serializable
data class ThemeSpec(
    val id: String,
    /**
     * The stored name. A theme the user makes keeps the name the user typed. A
     * built-in theme holds its English name here, which is what an export and a
     * copy start from; the name on screen comes from [themeName] instead, so it
     * follows the language of the device.
     */
    val name: String,
    val dark: Boolean = true,
    // Board
    val boardBackground: Long = 0xFF17181C,
    /** Painted instead of [boardBackground] when set; stops may be translucent. */
    val boardGradient: GradientSpec? = null,
    /** Absolute path of a local background image; never set in exports. */
    val backgroundImage: String? = null,
    /**
     * Absolute path of a separate landscape background image; used only when
     * the screen is landscape, falling back to [backgroundImage] when null.
     * Lets a portrait photo and a wide crop each fit their orientation instead
     * of one image being letterboxed or over-cropped in the other. Never set in
     * exports (travels as [backgroundImageLandscapeBase64]).
     */
    val backgroundImageLandscape: String? = null,
    /** Alpha applied to the background image itself (shared by both orientations). */
    val backgroundImageOpacity: Float = 1f,
    /** Blur radius applied to the background image (0 = sharp, 25 = heavy). */
    val backgroundImageBlur: Float = 0f,
    /**
     * The background image(s) are animated (GIF / animated WebP) and should
     * play. Off, or on a build from before this field, the same file draws as
     * its first frame — which is what makes an animated theme degrade to a
     * still one instead of failing. Mutually exclusive with
     * [backgroundImageBlur] (blurring every frame re-renders the effect per
     * frame); the editor enforces it and the renderer double-checks.
     */
    val backgroundAnimated: Boolean = false,
    /** Image bytes for export/import payloads only; stripped after import. */
    val backgroundImageBase64: String? = null,
    /** Landscape image bytes for export/import payloads only; stripped after import. */
    val backgroundImageLandscapeBase64: String? = null,
    /** Who took [backgroundImage], when it came from a photo service. */
    val backgroundPhoto: PhotoAttribution? = null,
    /** Who took [backgroundImageLandscape], when it came from a photo service. */
    val backgroundPhotoLandscape: PhotoAttribution? = null,
    // Keys
    val keyShape: KeyShapeKind = KeyShapeKind.ROUNDED,
    /**
     * Absolute path of an image drawn on every letter key, clipped to the key
     * shape, over the key colour. Never set in exports — the bytes travel in
     * [assets] under `"keyTexture"`. The class-specific slots below fall back
     * sensibly (space → this, pressed → the pressed colour over this), so a
     * theme with one texture already themes the whole board.
     */
    val keyTexture: String? = null,
    /** Texture for the modifier keys (shift, delete, mode…); null follows [keyTexture]. */
    val keyTextureModifier: String? = null,
    /** Texture for the enter key; null follows [keyTextureModifier]'s chain. */
    val keyTextureEnter: String? = null,
    /** Texture for the space bar; null follows [keyTexture]. */
    val keyTextureSpace: String? = null,
    /** Texture drawn while a key is held; null keeps the pressed colour overlay. */
    val keyTexturePressed: String? = null,
    /**
     * How a texture fits its key: `"crop"` (default — centre-crop, keeps
     * aspect), `"stretch"`, or `"tile"`. A string so a mode from a later build
     * costs the field, not the theme; read through [keyTextureScaleOrDefault].
     */
    val keyTextureScale: String? = null,
    /** Alpha the textures draw with over the key colour. */
    val keyTextureOpacity: Float = 1f,
    /** Painted over letter keys when set (subtle sheen); stops may be translucent. */
    val keyGradient: GradientSpec? = null,
    val keyBackground: Long = 0xFF303338,
    val keyText: Long = 0xFFE9E9EE,
    val modifierKeyBackground: Long = 0xFF222428,
    val modifierKeyText: Long? = null,
    val enterKeyBackground: Long = 0xFF4C8DF6,
    val enterKeyText: Long = 0xFF0B1220,
    val pressedKeyBackground: Long? = null,
    val keyBorderColor: Long? = null,
    val keyBorderWidthDp: Float = 0f,
    // Accent (shift-on tint, gesture trail, active tools, links/buttons in panels)
    val accent: Long = 0xFF8AB4F8,
    /**
     * Colour of the glide-typing trail. Null follows [accent]; set it to give
     * the swipe trail its own colour independent of the accent tint.
     */
    val gestureTrailColor: Long? = null,
    // Popups (key preview bubble + long-press alternates)
    val popupBackground: Long? = null,
    val popupText: Long? = null,
    /**
     * Where the key-preview bubble sits, as a name: `"key"` (the bubble grows
     * out of the pressed key, stock style) or `"float"` (a detached bubble
     * hovering above it). Null follows the global popup setting. A string for
     * the usual forward-compat reason; read through [popupOnKeyOrNull].
     */
    val popupPlacement: String? = null,
    /** Outline around the preview bubble; null draws none, like [keyBorderColor]. */
    val popupBorderColor: Long? = null,
    val popupBorderWidthDp: Float = 0f,
    /**
     * Image painted inside the preview bubble, over [popupBackground] and
     * under the label, clipped to the popup shape. Fit and alpha follow
     * [keyTextureScale] and [keyTextureOpacity]. Never set in exports — the
     * bytes travel in [assets] under `"popupTexture"`.
     */
    val popupTexture: String? = null,
    // Toolbar
    val toolbarIcon: Long? = null,
    val toolCircleBackground: Long? = null,
    val toolCircleActiveBackground: Long? = null,
    /**
     * Outline around the background of every toolbar tool. Null draws none,
     * exactly like [keyBorderColor], and [toolBorderWidthDp] still has to be
     * above 0 for it to show.
     */
    val toolBorderColor: Long? = null,
    val toolBorderWidthDp: Float = 0f,
    // Panels (clipboard/snippet cards, emoji search bar)
    val chipBackground: Long? = null,
    val suggestionText: Long? = null,
    // Chips (tool-panel buttons, style strips, plugin buttons)
    /** Text on an unselected chip; null derives from the modifier-key text. */
    val chipText: Long? = null,
    /** A selected chip's fill; null follows [toolCircleActiveBackground]'s chain. */
    val chipActiveBackground: Long? = null,
    /** Text on a selected chip; null derives a legible colour from the fill. */
    val chipActiveText: Long? = null,
    /** Outline around every chip; null draws none, like [keyBorderColor]. */
    val chipBorderColor: Long? = null,
    val chipBorderWidthDp: Float = 0f,
    /**
     * Chip outline shape, as a [KeyShapeKind] name; null keeps the soft
     * rectangle every chip has always drawn. A string for the same reason
     * [popupShape] is one.
     */
    val chipShape: String? = null,
    /** Chip corner radius for the rounded and cut shapes; null keeps 12. */
    val chipCornerRadiusDp: Int? = null,
    // Radii overrides; null = follow the global appearance sliders
    val keyCornerRadiusDp: Int? = null,
    val popupCornerRadiusDp: Int? = null,
    val toolCircleRadiusDp: Int? = null,
    /**
     * Outline for every popup surface, as a [KeyShapeKind] name; null follows
     * the global setting. A string rather than the enum for the reason spelled
     * out above [PhotoAttribution]: a name from a later build that added a
     * shape has to leave the rest of the theme intact. Read it through
     * [keyShapeKindOrNull].
     */
    val popupShape: String? = null,
    /**
     * Outline for the background behind every toolbar tool, as a [KeyShapeKind]
     * name; null follows the global setting. A string for the same reason
     * [popupShape] is one. The tool radius still decides how round the rounded
     * and cut shapes are, and 0 still means no background at all.
     */
    val toolShape: String? = null,
    // Layout/type overrides; null = follow the global appearance settings.
    // Primitives only — an enum here would drop the whole theme on older
    // builds (see the note above PhotoAttribution.provider).
    val toolWidthDp: Int? = null,
    val toolbarHeightDp: Int? = null,
    /**
     * Height of the key-preview bubble, in dp. Null follows the global slider,
     * which keeps a separate value for the on-key and the floating bubble; the
     * override applies to whichever of the two is switched on.
     */
    val popupHeightDp: Int? = null,
    val keyHeightDp: Int? = null,
    val keyGapScale: Float? = null,
    val sidePadScale: Float? = null,
    val fontScale: Float? = null,
    val boldKeyLabels: Boolean? = null,
    val hintFontScale: Float? = null,
    val gestureTrailWidthDp: Float? = null,
    val gestureTrailOpacity: Float? = null,
    // Animation
    val animation: ThemeAnimation = ThemeAnimation.NONE,
    /** Multiplier on the animation cycle speed; 1 = one cycle every ~16 s. */
    val animationSpeed: Float = 1f,
    /**
     * Key-label font, as a font id the font system already understands
     * (`serif`, `google:<Name>`, `installed:<id>`, …). Null follows the global
     * font setting. A font the device does not have falls back to the global
     * setting rather than erroring: fonts travel as their own addon, listed as
     * a dependency of the theme in a repo, never embedded in the theme file.
     * Per-script faces still win over this, so a display face never blanks a
     * non-Latin board.
     */
    val fontId: String? = null,
    /**
     * Key sound, as a KeySoundStyle name. A string rather than the enum for
     * the reason spelled out above [PhotoAttribution]; an unknown name costs
     * the field, not the theme. Null follows the global sound setting.
     */
    val soundStyle: String? = null,
    /**
     * Which installed sound to play when [soundStyle] is `CUSTOM`. Sounds are
     * their own addon like fonts; a missing id falls back to the global sound.
     */
    val soundCustomId: String? = null,
    /**
     * Decorative stickers over the key grid; see [DecalSpec]. One unknown
     * JSON key to an older build.
     */
    val decals: List<DecalSpec> = emptyList(),
    /**
     * Particle burst on every key press, as a [KeyEffectKind] name; null for
     * none. A string for the usual forward-compat reason; read through
     * [keyEffectKindOrNull]. Never plays under reduce motion or power saving.
     */
    val keyEffect: String? = null,
    /** The emoji the `EMOJI` effect throws; each glyph is one particle kind. */
    val keyEffectParam: String? = null,
    /** Scales how many particles a press throws. */
    val keyEffectIntensity: Float = 1f,
    /**
     * The images the `CUSTOM_IMAGE` effect throws — up to [MAX_EFFECT_IMAGES]
     * local paths, each one a particle kind, the way each emoji in
     * [keyEffectParam] is one. Transparent PNGs look best. Never set in
     * exports; the bytes travel in [assets] under `effectImage:<index>`.
     */
    val keyEffectImages: List<String> = emptyList(),
    /**
     * Per-key style overrides — a single key's own colours, keyed by the
     * key's lowercase label (letter keys) or its action name (special keys);
     * see [KeyOverride]. One unknown JSON key to an older build, which
     * imports the theme without them.
     */
    val keyOverrides: Map<String, KeyOverride> = emptyMap(),
    /**
     * Auxiliary image bytes for transport, keyed by slot name — the key
     * textures today (`"keyTexture"`, `"keyTextureModifier"`, …), whatever
     * needs to travel tomorrow. The generic sibling of
     * [backgroundImageBase64]: populated by [withEmbeddedImages] on the way
     * out, written to files and emptied by [withExtractedImages] on the way
     * in, never non-empty at rest. A map of strings so an older build ignores
     * the whole thing and a slot it doesn't know costs that slot alone.
     */
    val assets: Map<String, String> = emptyMap(),
)

/**
 * One key's own style, overriding the class-level colours. Everything is
 * nullable: a key that only recolours its popup carries exactly that.
 *
 * The map key naming these (see [ThemeSpec.keyOverrides]) is the key's
 * lowercase label for letter keys — so the override follows the letter across
 * layouts and languages — and the action's name (`ENTER`, `SHIFT`, `SPACE`,
 * `DELETE`, `SYMBOLS`, …) for the special keys.
 */
@Serializable
data class KeyOverride(
    val background: Long? = null,
    val text: Long? = null,
    val border: Long? = null,
    val popupBackground: Long? = null,
    val popupText: Long? = null,
) {
    val isEmpty: Boolean
        get() = background == null && text == null && border == null &&
            popupBackground == null && popupText == null
}

/**
 * One decorative sticker laid over the key grid — a character leaning on the
 * keys, hearts in a corner. Purely visual: the layer it draws on takes no
 * touches. Coordinates are normalized to the key grid's rect so a decal keeps
 * its place across widths and orientations.
 */
@Serializable
data class DecalSpec(
    /** Unique within the theme; names the image's transport slot (`decal:<id>`). */
    val id: String,
    /** Absolute local path of the sticker image; never set in exports. */
    val image: String? = null,
    /** Centre, as fractions of the grid's width and height. */
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    /** Width, as a fraction of the grid's width; height follows the image. */
    val scale: Float = 0.25f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
)

/** The most decals a theme may carry; past a handful they are just occlusion. */
const val MAX_DECALS = 6

/** A key-press particle burst's kind. Never serialized — travels as a string. */
enum class KeyEffectKind { STARS, HEARTS, SPARKLE, CONFETTI, EMOJI, CUSTOM_IMAGE }

/** The most images the CUSTOM_IMAGE press effect may carry. */
const val MAX_EFFECT_IMAGES = 6

/**
 * The effect behind [ThemeSpec.keyEffect]; null for an absent or unknown
 * name, which is also what a name from a later build resolves to — the field
 * costs itself, never the theme.
 */
fun keyEffectKindOrNull(name: String?): KeyEffectKind? =
    name?.let { wanted -> KeyEffectKind.entries.firstOrNull { it.name.equals(wanted, true) } }

/** How a key texture fits its key. Never serialized — travels as a string. */
enum class KeyTextureScale { CROP, STRETCH, TILE }

/**
 * The placement behind [ThemeSpec.popupPlacement]: true for `"key"`, false for
 * `"float"`, and null for an absent or unknown name — which follows the global
 * setting, so a placement from a later build costs the field, not the theme.
 */
fun popupOnKeyOrNull(name: String?): Boolean? = when {
    name == null -> null
    name.equals("key", ignoreCase = true) -> true
    name.equals("float", ignoreCase = true) -> false
    else -> null
}

/**
 * The scale mode behind [ThemeSpec.keyTextureScale]; an unknown or absent
 * name is CROP, the mode that never distorts.
 */
fun keyTextureScaleOrDefault(name: String?): KeyTextureScale =
    KeyTextureScale.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: KeyTextureScale.CROP

/** Follows system light/dark + Material You; not a stored [ThemeSpec]. */
const val DEFAULT_THEME_ID = "default"

private val themeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object ThemeCodec {

    /** Matches the settings backup's `wmsettings.json` shape. */
    const val FILE_EXTENSION = "wmtheme.json"

    /**
     * Plain JSON rather than a vendor type, for the reason spelled out in
     * [com.wasimaster.wmkeyboard.core.layout.LayoutFile.MIME_TYPE]: a custom
     * MIME would stop most file managers and chat apps from offering the file.
     */
    const val MIME_TYPE = "application/json"

    /**
     * What the import picker accepts. Permissive on purpose: providers report a
     * `.json` file as `text/plain` or `application/octet-stream` as often as not.
     */
    val IMPORT_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

    fun encodeList(themes: List<ThemeSpec>): String = themeJson.encodeToString(themes)

    fun decodeList(json: String): List<ThemeSpec> {
        // A blank value is what a user with no themes of their own has stored,
        // which is most of them; parsing it only to catch the exception cost a
        // filled stack trace per settings emission.
        if (json.isBlank()) return emptyList()
        return runCatching { themeJson.decodeFromString<List<ThemeSpec>>(json) }
            .getOrDefault(emptyList())
    }

    fun encode(theme: ThemeSpec): String = themeJson.encodeToString(theme)

    fun decode(json: String): ThemeSpec? {
        if (json.isBlank()) return null
        return runCatching { themeJson.decodeFromString<ThemeSpec>(json) }.getOrNull()
    }
}

/**
 * Reads the local background image file(s) into base64 for a transport payload
 * (single-theme export or full-config backup) and nulls the local paths, so the
 * image travels with the theme instead of a device-local path that only resolves
 * on the source phone. A path that no longer resolves becomes null.
 */
fun ThemeSpec.withEmbeddedImages(): ThemeSpec {
    fun encode(path: String?) = path?.let {
        runCatching { Base64.encodeToString(File(it).readBytes(), Base64.NO_WRAP) }.getOrNull()
    }
    val portrait = encode(backgroundImage)
    val landscape = encode(backgroundImageLandscape)
    // The generic slots: every auxiliary image the theme points at, keyed by
    // the field it came from. A path that no longer resolves is simply absent.
    val embedded = buildMap {
        for ((slot, path) in assetPaths()) {
            encode(path)?.let { put(slot, it) }
        }
        for (decal in decals) {
            encode(decal.image)?.let { put("$ASSET_DECAL_PREFIX${decal.id}", it) }
        }
        keyEffectImages.forEachIndexed { index, path ->
            encode(path)?.let { put("$ASSET_EFFECT_IMAGE_PREFIX$index", it) }
        }
    }
    return copy(
        backgroundImage = null,
        backgroundImageBase64 = portrait,
        backgroundImageLandscape = null,
        backgroundImageLandscapeBase64 = landscape,
        // A credit only travels with the image it belongs to. A path that no
        // longer resolves already becomes null above, and leaving the
        // attribution behind would name a photographer whose photo is not in
        // the file — which is worse than no credit at all.
        backgroundPhoto = backgroundPhoto?.takeIf { portrait != null },
        backgroundPhotoLandscape = backgroundPhotoLandscape?.takeIf { landscape != null },
        keyTexture = null,
        keyTextureModifier = null,
        keyTextureEnter = null,
        keyTextureSpace = null,
        keyTexturePressed = null,
        popupTexture = null,
        decals = decals.map { it.copy(image = null) },
        keyEffectImages = emptyList(),
        assets = embedded,
    )
}

/** Slot name → local path, for every auxiliary image field. */
private fun ThemeSpec.assetPaths(): List<Pair<String, String?>> = listOf(
    ASSET_KEY_TEXTURE to keyTexture,
    ASSET_KEY_TEXTURE_MODIFIER to keyTextureModifier,
    ASSET_KEY_TEXTURE_ENTER to keyTextureEnter,
    ASSET_KEY_TEXTURE_SPACE to keyTextureSpace,
    ASSET_KEY_TEXTURE_PRESSED to keyTexturePressed,
    ASSET_POPUP_TEXTURE to popupTexture,
)

const val ASSET_KEY_TEXTURE = "keyTexture"
const val ASSET_KEY_TEXTURE_MODIFIER = "keyTextureModifier"
const val ASSET_KEY_TEXTURE_ENTER = "keyTextureEnter"
const val ASSET_KEY_TEXTURE_SPACE = "keyTextureSpace"
const val ASSET_KEY_TEXTURE_PRESSED = "keyTexturePressed"
const val ASSET_POPUP_TEXTURE = "popupTexture"

/** Prefix of a decal image's transport slot; the decal's id follows it. */
const val ASSET_DECAL_PREFIX = "decal:"

/** Prefix of a press-effect image's transport slot; its list index follows. */
const val ASSET_EFFECT_IMAGE_PREFIX = "effectImage:"

/**
 * Inverse of [withEmbeddedImages]: writes any embedded base64 image(s) into
 * [dir] (filenames keyed off [id]) and returns a copy pointing at the local
 * files with the base64 stripped. When no base64 is present the existing path is
 * kept — old backups that carry only paths, and themes with no image, are
 * unchanged.
 */
fun ThemeSpec.withExtractedImages(dir: File): ThemeSpec {
    fun write(name: String, b64: String?): String? = b64?.let {
        runCatching {
            dir.mkdirs()
            File(dir, name).apply { writeBytes(Base64.decode(it, Base64.DEFAULT)) }.absolutePath
        }.getOrNull()
    }
    return copy(
        backgroundImage = write("$id.img", backgroundImageBase64) ?: backgroundImage,
        backgroundImageBase64 = null,
        backgroundImageLandscape = write("${id}_land.img", backgroundImageLandscapeBase64)
            ?: backgroundImageLandscape,
        backgroundImageLandscapeBase64 = null,
        keyTexture = write("${id}_tex.img", assets[ASSET_KEY_TEXTURE]) ?: keyTexture,
        keyTextureModifier = write("${id}_tex_mod.img", assets[ASSET_KEY_TEXTURE_MODIFIER])
            ?: keyTextureModifier,
        keyTextureEnter = write("${id}_tex_enter.img", assets[ASSET_KEY_TEXTURE_ENTER])
            ?: keyTextureEnter,
        keyTextureSpace = write("${id}_tex_space.img", assets[ASSET_KEY_TEXTURE_SPACE])
            ?: keyTextureSpace,
        keyTexturePressed = write("${id}_tex_press.img", assets[ASSET_KEY_TEXTURE_PRESSED])
            ?: keyTexturePressed,
        popupTexture = write("${id}_tex_popup.img", assets[ASSET_POPUP_TEXTURE])
            ?: popupTexture,
        decals = decals.map { decal ->
            decal.copy(
                image = write("${id}_decal_${decal.id}.img", assets["$ASSET_DECAL_PREFIX${decal.id}"])
                    ?: decal.image,
            )
        },
        keyEffectImages = run {
            // Extracted by index; a theme edited on-device keeps its paths.
            val extracted = (0 until MAX_EFFECT_IMAGES).mapNotNull { index ->
                write("${id}_fx_$index.img", assets["$ASSET_EFFECT_IMAGE_PREFIX$index"])
            }
            extracted.ifEmpty { keyEffectImages }
        },
        assets = emptyMap(),
    )
}

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** Readable text color (near-black or near-white) for a solid background. */
fun onColorFor(background: Long): Long =
    if (Color(background).luminance() > 0.5f) 0xFF15161A else 0xFFF4F4F8

/**
 * The same ARGB colour at [fraction] opacity, 0..1. Used for the board scrim,
 * where the alpha of the board colour is what dims a background photo.
 */
fun Long.withAlphaFraction(fraction: Float): Long {
    val alpha = (fraction.coerceIn(0f, 1f) * 255f).roundToInt().toLong()
    return (this and 0x00FFFFFFL) or (alpha shl 24)
}

/** How opaque an ARGB colour is, 0..1. */
fun Long.alphaFraction(): Float = ((this ushr 24) and 0xFFL) / 255f

/**
 * Rebuilds the palette from [seed] and [dark] while keeping everything about
 * the theme that is not a generated colour: gradients, the background image and
 * its credit, key shape and border, the gesture-trail colour, radii, animation.
 *
 * Written as "start from this theme and overwrite what the seed decides",
 * rather than "start from a generated theme and copy back a list of fields to
 * keep". The two produce the same result today, but the list version drops
 * anything added to [ThemeSpec] later — silently, and with no compiler
 * complaint — which is how attribution would have gone missing on every reseed.
 *
 * The five nullable overrides below are deliberately cleared rather than kept:
 * they are per-theme exceptions to a palette that no longer exists, so a reseed
 * returns them to following the new colours.
 */
fun ThemeSpec.reseeded(seed: Long, dark: Boolean): ThemeSpec {
    val generated = themeFromSeed(id, name, seed, dark)
    // A generated palette is fully opaque, which over a background photo means
    // the photo disappears — and "match the colours to this photo" erasing the
    // photo is the least useful thing it could do. So when there is an image,
    // the see-through-ness the user set is carried across even though the
    // colours themselves are replaced.
    val hasImage = backgroundImage != null || backgroundImageLandscape != null
    fun keepAlpha(fresh: Long, previous: Long): Long =
        if (hasImage) fresh.withAlphaFraction(previous.alphaFraction()) else fresh
    return copy(
        dark = generated.dark,
        boardBackground = keepAlpha(generated.boardBackground, boardBackground),
        keyBackground = keepAlpha(generated.keyBackground, keyBackground),
        keyText = generated.keyText,
        modifierKeyBackground = keepAlpha(generated.modifierKeyBackground, modifierKeyBackground),
        enterKeyBackground = generated.enterKeyBackground,
        enterKeyText = generated.enterKeyText,
        pressedKeyBackground = generated.pressedKeyBackground,
        accent = generated.accent,
        popupBackground = generated.popupBackground,
        toolCircleBackground = generated.toolCircleBackground,
        chipBackground = generated.chipBackground,
        modifierKeyText = null,
        popupText = null,
        toolbarIcon = null,
        toolCircleActiveBackground = null,
        suggestionText = null,
    )
}

/**
 * Builds a full theme from one seed color — the "pick a color, get a
 * sensible theme, then tweak" flow. All colors are opaque; the editor can
 * add alpha afterwards.
 */
fun themeFromSeed(id: String, name: String, seed: Long, dark: Boolean): ThemeSpec {
    val seedColor = Color(seed)
    return if (dark) {
        val board = lerp(Color(0xFF121318), seedColor, 0.08f)
        val key = lerp(Color(0xFF2B2D34), seedColor, 0.12f)
        ThemeSpec(
            id = id,
            name = name,
            dark = true,
            boardBackground = board.argb(),
            keyBackground = key.argb(),
            keyText = 0xFFE9E9EE,
            modifierKeyBackground = lerp(Color(0xFF1E2026), seedColor, 0.12f).argb(),
            enterKeyBackground = seed,
            enterKeyText = onColorFor(seed),
            pressedKeyBackground = lerp(key, seedColor, 0.45f).argb(),
            accent = lerp(seedColor, Color.White, 0.30f).argb(),
            popupBackground = lerp(Color(0xFF34363E), seedColor, 0.12f).argb(),
            toolCircleBackground = lerp(Color(0xFF2A2C33), seedColor, 0.14f).argb(),
            chipBackground = lerp(Color(0xFF24262C), seedColor, 0.10f).argb(),
        )
    } else {
        val board = lerp(Color(0xFFE8EAF0), seedColor, 0.06f)
        ThemeSpec(
            id = id,
            name = name,
            dark = false,
            boardBackground = board.argb(),
            keyBackground = 0xFFFFFFFF,
            keyText = 0xFF1B1C20,
            modifierKeyBackground = lerp(Color(0xFFD7DAE2), seedColor, 0.10f).argb(),
            enterKeyBackground = seed,
            enterKeyText = onColorFor(seed),
            pressedKeyBackground = lerp(Color.White, seedColor, 0.30f).argb(),
            accent = lerp(seedColor, Color.Black, 0.12f).argb(),
            popupBackground = 0xFFFFFFFF,
            toolCircleBackground = 0xFFFFFFFF,
            chipBackground = lerp(Color(0xFFDDE0E7), seedColor, 0.08f).argb(),
        )
    }
}

/**
 * Built-in gallery themes. Users can't edit these in place — the editor
 * duplicates one into a custom theme instead — so ids stay stable.
 *
 * [PaletteThemes] (ports of well-known editor colour schemes) are appended
 * rather than inlined: they are transcriptions of external palettes with
 * their own attribution rules, so they live in their own file.
 */
val BuiltInThemes: List<ThemeSpec> = listOf(
    themeFromSeed("builtin_ocean", "Ocean", 0xFF3B82C4, dark = true),
    themeFromSeed("builtin_forest", "Forest", 0xFF3E8E5A, dark = true),
    themeFromSeed("builtin_sunset", "Sunset", 0xFFE07B39, dark = true),
    themeFromSeed("builtin_berry", "Berry", 0xFFB84A8E, dark = true),
    themeFromSeed("builtin_crimson", "Crimson", 0xFFCE4257, dark = true),
    themeFromSeed("builtin_slate", "Slate", 0xFF7A8699, dark = true),
    // Pitch black: AMOLED-friendly, near-black keys on true black.
    themeFromSeed("builtin_pitch", "Pitch black", 0xFF4C8DF6, dark = true).copy(
        boardBackground = 0xFF000000,
        keyBackground = 0xFF1A1C21,
        modifierKeyBackground = 0xFF101216,
        toolCircleBackground = 0xFF1E2025,
        chipBackground = 0xFF15171B,
        popupBackground = 0xFF24262C,
    ),
    themeFromSeed("builtin_snow", "Snow", 0xFF5B7DB1, dark = false),
    themeFromSeed("builtin_mint", "Mint", 0xFF4FA98F, dark = false),
    themeFromSeed("builtin_rose", "Rose", 0xFFC96A85, dark = false),
    themeFromSeed("builtin_sand", "Sand", 0xFFA98052, dark = false),
    // Gradient themes. Keys are translucent so the gradient shows through;
    // their flattened (opaque) versions still contrast with the key text, which
    // matters because panel surfaces flatten alpha (see schemeFor).
    themeFromSeed("builtin_nebula", "Nebula", 0xFF8E5AC8, dark = true).copy(
        boardGradient = GradientSpec(
            listOf(0xFF2A1758, 0xFF15306B, 0xFF0B1B3A), GradientType.LINEAR, 135f,
        ),
        keyBackground = 0x59453076,
        modifierKeyBackground = 0x40311F5C,
        keyText = 0xFFF2ECFF,
        enterKeyBackground = 0xFF8E5AC8,
        enterKeyText = 0xFFF6F0FF,
        accent = 0xFFB79CFF,
        popupBackground = 0xFF352759,
        toolCircleBackground = 0x59453076,
        chipBackground = 0x40311F5C,
    ),
    themeFromSeed("builtin_sunset_drift", "Sunset drift", 0xFFE07B39, dark = true).copy(
        boardGradient = GradientSpec(
            listOf(0xFF3B1035, 0xFF7A1E3C, 0xFFC2542B), GradientType.LINEAR, 100f,
        ),
        keyBackground = 0x59561F33,
        modifierKeyBackground = 0x403D1428,
        keyText = 0xFFFFEFE4,
        accent = 0xFFFFB07C,
        popupBackground = 0xFF57203A,
        toolCircleBackground = 0x59561F33,
        chipBackground = 0x403D1428,
        animation = ThemeAnimation.FLOW,
        animationSpeed = 1f,
    ),
    themeFromSeed("builtin_aurora", "Aurora", 0xFF3E8E5A, dark = true).copy(
        boardGradient = GradientSpec(
            listOf(0xFF0E3B43, 0xFF14543B, 0xFF14324F), GradientType.SWEEP, 0f,
        ),
        keyBackground = 0x59204A44,
        modifierKeyBackground = 0x40163830,
        keyText = 0xFFE6FFF4,
        accent = 0xFF7CE8B5,
        popupBackground = 0xFF1C4A42,
        toolCircleBackground = 0x59204A44,
        chipBackground = 0x40163830,
        animation = ThemeAnimation.HUE_CYCLE,
        animationSpeed = 0.6f,
    ),
    themeFromSeed("builtin_deep_sea", "Deep sea", 0xFF3B82C4, dark = true).copy(
        boardGradient = GradientSpec(
            listOf(0xFF0E2A4A, 0xFF071523), GradientType.RADIAL, 0f,
        ),
        keyBackground = 0x591E4568,
        modifierKeyBackground = 0x4014304A,
        popupBackground = 0xFF1B3C5C,
        toolCircleBackground = 0x591E4568,
        chipBackground = 0x4014304A,
    ),
    themeFromSeed("builtin_glacier", "Glacier", 0xFF5B7DB1, dark = false).copy(
        boardGradient = GradientSpec(
            listOf(0xFFDDE9F7, 0xFFEAF3EE, 0xFFF6EEE7), GradientType.LINEAR, 20f,
        ),
    ),
    // Shape showcases: pill keys with a soft radial glow, cut-corner steel.
    themeFromSeed("builtin_bubble", "Bubble", 0xFF00897B, dark = true).copy(
        keyShape = KeyShapeKind.PILL,
        boardGradient = GradientSpec(
            listOf(0xFF16302E, 0xFF0D1517), GradientType.RADIAL, 0f,
        ),
        keyBackground = 0x5926504B,
        modifierKeyBackground = 0x401B3B37,
        toolCircleBackground = 0x5926504B,
        chipBackground = 0x401B3B37,
    ),
    themeFromSeed("builtin_facet", "Facet", 0xFF7A8699, dark = true).copy(
        keyShape = KeyShapeKind.CUT,
        boardGradient = GradientSpec(
            listOf(0xFF23262C, 0xFF14161A), GradientType.LINEAR, 90f,
        ),
        keyGradient = GradientSpec(
            listOf(0x14FFFFFF, 0x00FFFFFF), GradientType.LINEAR, 90f,
        ),
        keyBorderColor = 0x338A94A6,
        keyBorderWidthDp = 1f,
    ),
) + PaletteThemes

/**
 * The name resource of every theme in [BuiltInThemes], keyed by the id of the
 * theme. The ids stay exactly as they are stored and as they are written into a
 * `.wmtheme.json` file; only the label on screen comes from the resource.
 *
 * The entries for [PaletteThemes] live next to those themes, in the same file.
 */
private val BuiltInThemeNameRes: Map<String, Int> = mapOf(
    "builtin_ocean" to R.string.core_theme_builtin_ocean_label,
    "builtin_forest" to R.string.core_theme_builtin_forest_label,
    "builtin_sunset" to R.string.core_theme_builtin_sunset_label,
    "builtin_berry" to R.string.core_theme_builtin_berry_label,
    "builtin_crimson" to R.string.core_theme_builtin_crimson_label,
    "builtin_slate" to R.string.core_theme_builtin_slate_label,
    "builtin_pitch" to R.string.core_theme_builtin_pitch_label,
    "builtin_snow" to R.string.core_theme_builtin_snow_label,
    "builtin_mint" to R.string.core_theme_builtin_mint_label,
    "builtin_rose" to R.string.core_theme_builtin_rose_label,
    "builtin_sand" to R.string.core_theme_builtin_sand_label,
    "builtin_nebula" to R.string.core_theme_builtin_nebula_label,
    "builtin_sunset_drift" to R.string.core_theme_builtin_sunset_drift_label,
    "builtin_aurora" to R.string.core_theme_builtin_aurora_label,
    "builtin_deep_sea" to R.string.core_theme_builtin_deep_sea_label,
    "builtin_glacier" to R.string.core_theme_builtin_glacier_label,
    "builtin_bubble" to R.string.core_theme_builtin_bubble_label,
    "builtin_facet" to R.string.core_theme_builtin_facet_label,
) + PaletteThemeNameRes

/**
 * The name resource of the built-in theme with this [id], or null when the id
 * belongs to a theme the user made. Use [themeName] to get the text itself.
 */
@StringRes
fun builtInThemeNameRes(id: String): Int? = BuiltInThemeNameRes[id]

/**
 * The name to show for [theme]. A built-in theme gets its translated name. A
 * theme the user made keeps the name the user typed, which is never translated.
 */
@Composable
fun themeName(theme: ThemeSpec): String {
    val nameRes = builtInThemeNameRes(theme.id)
    return if (nameRes == null) theme.name else stringResource(nameRes)
}

/** [themeName] for code that has a [Context] and is not a composable. */
fun themeName(context: Context, theme: ThemeSpec): String {
    val nameRes = builtInThemeNameRes(theme.id)
    return if (nameRes == null) theme.name else context.getString(nameRes)
}

/** Seed swatches offered by the editor's "start from a color" row. */
val SeedSwatches: List<Long> = listOf(
    0xFF4C8DF6, 0xFF3B82C4, 0xFF00897B, 0xFF3E8E5A, 0xFF7CB342,
    0xFFF9A825, 0xFFE07B39, 0xFFCE4257, 0xFFB84A8E, 0xFF8E5AC8,
    0xFF5C6BC0, 0xFF7A8699, 0xFF6D4C41, 0xFF546E7A, 0xFF37393F,
)
