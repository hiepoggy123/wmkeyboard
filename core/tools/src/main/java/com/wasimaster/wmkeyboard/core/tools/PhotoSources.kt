package com.wasimaster.wmkeyboard.core.tools

import androidx.annotation.StringRes
import com.wasimaster.wmkeyboard.tools.R

/**
 * Where a background photo came from. A plain enum with `when` dispatch rather
 * than a client interface, following [GifSource]: the closed set is two, there
 * is no dependency-injection container to hold instances, and every other
 * network client in the app is a top-level `object` with blocking functions.
 */
enum class PhotoSource { UNSPLASH, PEXELS }

/** Which way round a photo is. [ANY] leaves the parameter off the request. */
enum class PhotoOrientation { ANY, LANDSCAPE, PORTRAIT, SQUARE }

/**
 * The colour filter, as the union of what the two providers offer. [swatch] is
 * the circle the picker draws (0 for [ANY], which draws its own "no filter"
 * mark), and neither provider honours every value — see
 * [UnsplashClient.colorParam] and [PexelsClient.colorParam], which drop what
 * they cannot express rather than send a value the provider rejects.
 */
enum class PhotoColor(val swatch: Long) {
    ANY(0x00000000),
    MONOCHROME(0xFF9E9E9E),
    BLACK(0xFF212121),
    WHITE(0xFFFAFAFA),
    GRAY(0xFF757575),
    BROWN(0xFF795548),
    RED(0xFFE53935),
    ORANGE(0xFFFB8C00),
    YELLOW(0xFFFDD835),
    GREEN(0xFF43A047),
    TEAL(0xFF00897B),
    BLUE(0xFF1E88E5),
    PURPLE(0xFF8E24AA),
    MAGENTA(0xFFD81B60),
}

/**
 * One fixed size a provider offers for a photo. Pexels serves a handful of
 * named sizes and nothing between them; Unsplash resizes on demand and so
 * reports no variants at all (see [PhotoItem.resizable]).
 */
data class PhotoVariant(val url: String, val width: Int, val height: Int)

/**
 * One photo result. Carries everything three separate consumers need: the grid
 * (thumbnail, aspect, average colour), the download (a full URL plus whatever
 * sizing the provider allows), and the credit that has to survive export,
 * import and going offline.
 */
data class PhotoItem(
    val id: String,
    val source: PhotoSource,
    /**
     * Hotlinked straight into the grid, never copied to storage. Both
     * providers count views from their own CDN and pass the numbers to the
     * photographer, so serving a thumbnail from anywhere else takes credit
     * away from them.
     */
    val thumbUrl: String,
    /** The provider's own full-size URL, before any sizing is applied. */
    val fullUrl: String,
    /** The photo's page on the provider, which the credit links to. */
    val pageUrl: String,
    val photographer: String,
    val photographerUrl: String,
    val width: Int,
    val height: Int,
    /** `#RRGGBB` average colour, painted under the grid tile while it loads. */
    val avgColor: String,
    val altText: String,
    /**
     * Whether the provider resizes on demand (Unsplash, through imgix) or only
     * serves the fixed sizes in [variants] (Pexels).
     */
    val resizable: Boolean,
    /** The fixed sizes on offer, in no particular order. Empty when [resizable]. */
    val variants: List<PhotoVariant> = emptyList(),
    /**
     * Unsplash `links.download_location`, blank for Pexels. Their guidelines
     * require one authorized GET here per real download, which is how a
     * photographer's download count is kept honest.
     */
    val downloadLocation: String = "",
    /** Parsed but unused: the placeholder is [avgColor], which costs nothing. */
    val blurHash: String = "",
) {
    /** Stable across providers, so a mixed grid cannot collide on ids. */
    val key: String get() = "$source:$id"
}

/**
 * One search or feed request, in terms both providers can be asked to honour.
 * Blank [text] with no [topicId] means the provider's own curated feed.
 */
data class PhotoQuery(
    val text: String = "",
    val page: Int = 1,
    val perPage: Int = 24,
    val orientation: PhotoOrientation = PhotoOrientation.LANDSCAPE,
    val color: PhotoColor = PhotoColor.ANY,
    /** Unsplash `content_filter=high`. Pexels offers no equivalent. */
    val safe: Boolean = true,
    /** An Unsplash topic slug. Pexels has no topics and falls back to curated. */
    val topicId: String = "",
)

/**
 * One page of results with both providers' paging normalised away: Unsplash
 * search reports `total_pages`, its feed endpoints report nothing at all, and
 * Pexels reports a `next_page` URL. Callers only ever read [hasMore].
 */
data class PhotoPage(
    val items: List<PhotoItem>,
    val page: Int,
    val totalResults: Int,
    val hasMore: Boolean,
    val source: PhotoSource,
)

/** A curated subject the picker offers as a chip. [slug] is an API value. */
enum class PhotoTopic(val slug: String, @StringRes val labelRes: Int) {
    WALLPAPERS("wallpapers", R.string.core_tools_photo_topic_wallpapers),
    NATURE("nature", R.string.core_tools_photo_topic_nature),
    TEXTURES("textures-patterns", R.string.core_tools_photo_topic_textures),
    MINIMAL("minimal", R.string.core_tools_photo_topic_minimal),
    ARCHITECTURE("architecture-interior", R.string.core_tools_photo_topic_architecture),
    SPACE("space", R.string.core_tools_photo_topic_space),
    ANIMALS("animals", R.string.core_tools_photo_topic_animals),
    TRAVEL("travel", R.string.core_tools_photo_topic_travel),
    FOOD("food-drink", R.string.core_tools_photo_topic_food),
    STREET("street-photography", R.string.core_tools_photo_topic_street),
    COLOR("color-of-water", R.string.core_tools_photo_topic_color),
    DARK("dark", R.string.core_tools_photo_topic_dark),
}

/** The size a downloaded background is wanted at, in device pixels. */
data class PhotoTarget(val width: Int, val height: Int, val maxBytes: Long)

/**
 * Turning a result into a file small enough to live in the keyboard process.
 *
 * The background is drawn behind the board with `ContentScale.Crop`, so the
 * only thing that matters is covering the widest screen and the tallest board.
 * Downloading the provider's original instead would mean a 6000x4000 JPEG,
 * which decodes to about 96 MB of ARGB_8888 inside an input method the system
 * is already quick to kill.
 */
object PhotoSizing {

    /** Covers a 1080 px wide phone with room for the tallest full-bleed panel. */
    val PORTRAIT_STRIP = PhotoTarget(width = 1080, height = 560, maxBytes = 4L * 1024 * 1024)

    /** The long edge of the same phone turned sideways, with a shorter board. */
    val LANDSCAPE_STRIP = PhotoTarget(width = 2160, height = 640, maxBytes = 8L * 1024 * 1024)

    /** JPEG quality asked of imgix. High enough that banding stays invisible. */
    private const val QUALITY = 72

    /**
     * The URL to actually download for [target].
     *
     * A resizable provider gets exact imgix parameters. A fixed-size provider
     * gets the smallest variant that still covers the target on both axes,
     * because anything smaller is upscaled by `Crop` and looks soft; when
     * nothing covers it, the largest variant is the best available.
     */
    fun downloadUrl(item: PhotoItem, target: PhotoTarget): String {
        if (item.resizable) {
            return withImgix(
                item.fullUrl,
                mapOf(
                    "w" to target.width.toString(),
                    "h" to target.height.toString(),
                    "fit" to "crop",
                    // Keeps the interesting part: cropping a 3:2 photo down to a
                    // keyboard strip throws away a third of its height, and the
                    // default centre crop has no opinion about which third.
                    "crop" to "entropy",
                    // Not `auto=format`: ToolHttp.download sends no Accept header,
                    // so imgix would fall back to the source format anyway and the
                    // output would stop being predictable.
                    "fm" to "jpg",
                    "q" to QUALITY.toString(),
                    // Explicit, so a stray dpr on a URL we were handed cannot
                    // quietly double the request.
                    "dpr" to "1",
                ),
            )
        }
        val covering = item.variants
            .filter { it.width >= target.width && it.height >= target.height }
            .minByOrNull { it.width.toLong() * it.height }
        return (covering ?: item.variants.maxByOrNull { it.width.toLong() * it.height })?.url
            ?: item.fullUrl
    }
}

/**
 * Adds imgix parameters to a provider URL while keeping every parameter we do
 * not own. That matters for Unsplash specifically: their URLs arrive carrying
 * `ixid` and `ixlib`, and their terms require both to survive a resize, so the
 * URL has to be edited rather than rebuilt. Keys being set are dropped first,
 * so a value already on the URL cannot win over the one asked for.
 */
fun withImgix(url: String, params: Map<String, String>): String {
    if (params.isEmpty()) return url
    val base = url.substringBefore('?')
    val kept = url.substringAfter('?', "")
        .split('&')
        .filter { it.isNotBlank() }
        .filterNot { it.substringBefore('=') in params.keys }
    val added = params.map { (key, value) -> "$key=${ToolHttp.encode(value)}" }
    return base + "?" + (kept + added).joinToString("&")
}

/** Names, links and the attribution both providers require. */
object PhotoLinks {

    /**
     * Must match the application name registered on Unsplash, which is what
     * their referral reporting joins on.
     */
    const val UTM_SOURCE = "wm_keyboard"

    /**
     * Unsplash requires every link back to carry these. Pexels asks only for a
     * plain link, so its URLs pass through untouched.
     *
     * Links are stored untagged and tagged here, at the point of use: the slug
     * is a build constant, so a theme exported from one build and imported into
     * another should carry the importing build's slug rather than a stale one.
     */
    fun credited(url: String, source: PhotoSource): String {
        if (source != PhotoSource.UNSPLASH || url.isBlank()) return url
        val separator = if ('?' in url) '&' else '?'
        return "$url${separator}utm_source=$UTM_SOURCE&utm_medium=referral"
    }

    fun homeUrl(source: PhotoSource): String = when (source) {
        PhotoSource.UNSPLASH -> credited("https://unsplash.com/", source)
        PhotoSource.PEXELS -> "https://www.pexels.com/"
    }

    /** The name a provider goes by on screen. The UI resolves the id. */
    @StringRes
    fun displayNameRes(source: PhotoSource): Int = when (source) {
        PhotoSource.UNSPLASH -> R.string.core_tools_photo_source_unsplash
        PhotoSource.PEXELS -> R.string.core_tools_photo_source_pexels
    }

    /** Stored on a theme so the credit reads the same after an import. */
    fun providerKey(source: PhotoSource): String = source.name.lowercase()

    /**
     * The inverse, tolerating a name this build does not know: a theme made by
     * a later build that added a third provider still shows its credit instead
     * of failing to decode.
     */
    fun sourceOf(providerKey: String): PhotoSource? =
        PhotoSource.entries.firstOrNull { it.name.equals(providerKey, ignoreCase = true) }
}

/** Round-robin merge, so neither provider owns the top of a mixed grid. */
fun interleavePhotos(lists: List<List<PhotoItem>>): List<PhotoItem> {
    val iterators = lists.map { it.iterator() }
    val merged = ArrayList<PhotoItem>(lists.sumOf { it.size })
    while (iterators.any { it.hasNext() }) {
        for (iterator in iterators) {
            if (iterator.hasNext()) merged += iterator.next()
        }
    }
    // Two providers can hand back the same photo id only by coincidence, but a
    // provider reshuffling between page requests really does repeat its own —
    // and a duplicate key crashes a LazyGrid.
    return merged.distinctBy { it.key }
}
