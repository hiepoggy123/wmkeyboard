package com.wasimaster.wmkeyboard.core.settings

import com.wasimaster.wmkeyboard.core.theme.PhotoAttribution
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One photo held ready for the rotating background.
 *
 * The palette seed and the scrim are worked out when the photo is downloaded,
 * not when it is shown: a rotation happens as the keyboard is opening, and
 * decoding a photo to measure it at that moment is exactly the kind of work
 * that has no business on that path.
 */
@Serializable
data class PoolEntry(
    /** File name inside the pool directory, or inside `saved/` or `local/`. */
    val fileName: String,
    val source: RotationSourceKind,
    val credit: PhotoAttribution? = null,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val bytes: Long = 0L,
    val seedColor: Long = 0L,
    val scrimAlpha: Float = -1f,
    val addedAt: Long = 0L,
    /** 0 means never shown, which is what makes a photo next in line. */
    val lastShownAt: Long = 0L,
    val showCount: Int = 0,
    /** The topic or search term that produced it, for the library to group by. */
    val sourceTag: String = "",
) {
    /** Wide enough to suit a keyboard, which is a wide, short strip. */
    val wide: Boolean get() = heightPx > 0 && widthPx.toFloat() / heightPx >= WIDE_RATIO

    /** Photos the user chose keep their place; downloaded ones are a cache. */
    val pinned: Boolean
        get() = source == RotationSourceKind.SAVED || source == RotationSourceKind.DEVICE

    private companion object {
        const val WIDE_RATIO = 1.2f
    }
}

/** The pool as stored on disk, next to the files it describes. */
@Serializable
data class PoolIndex(
    val entries: List<PoolEntry> = emptyList(),
    /**
     * Photo keys thrown out recently, so the next top-up does not fetch back
     * what was just made room for.
     */
    val recentlyEvicted: List<String> = emptyList(),
    val lastTopUpAt: Long = 0L,
)

/** Reads and writes the pool index, tolerating a file that will not parse. */
object PoolIndexCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** How many evicted keys to remember. */
    const val EVICTION_MEMORY = 64

    fun encode(index: PoolIndex): String = json.encodeToString(index)

    /** A pool index that will not parse costs a re-download, nothing more. */
    fun decode(raw: String): PoolIndex =
        runCatching { json.decodeFromString<PoolIndex>(raw) }.getOrDefault(PoolIndex())
}

/** What a sweep should delete, worked out without touching the filesystem. */
data class PoolPlan(
    val keep: List<PoolEntry>,
    val deleteFiles: List<String>,
    val evictedKeys: List<String>,
)

/**
 * Which photo to show next.
 *
 * Photos never shown come first, oldest first, so a fresh download is seen
 * rather than sitting behind a rotation through everything already seen. After
 * that it is least-recently-shown. The photo currently up is never picked
 * again while any alternative exists, because a "rotation" that lands on the
 * same picture reads as broken.
 *
 * [random] is injected so the shuffle is genuinely random in the app and
 * entirely predictable in a test.
 */
fun pickNextPhoto(
    entries: List<PoolEntry>,
    currentFileName: String?,
    wantWide: Boolean = false,
    random: Random = Random.Default,
): PoolEntry? {
    if (entries.isEmpty()) return null
    val suitable = entries.filter { !wantWide || it.wide }.ifEmpty { entries }
    val others = suitable.filterNot { it.fileName == currentFileName }
    val pool = others.ifEmpty { suitable }

    val unseen = pool.filter { it.lastShownAt == 0L }
    if (unseen.isNotEmpty()) return unseen.minByOrNull { it.addedAt }
    val oldest = pool.minOf { it.lastShownAt }
    // Several photos can tie on "longest ago", which is the whole pool once it
    // has been round once; picking among them at random is what stops the
    // rotation settling into a fixed order.
    val tied = pool.filter { it.lastShownAt == oldest }
    return tied[random.nextInt(tied.size)]
}

/**
 * What to drop so the pool fits its budget.
 *
 * A photo the user has not seen yet is never dropped — holding photos ready is
 * the entire point of the pool — and neither is one the user chose, saved or
 * from the device. Everything else goes least-recently-shown first.
 */
fun poolEvictionPlan(
    entries: List<PoolEntry>,
    existingFiles: Set<String>,
    maxEntries: Int,
    maxBytes: Long,
): PoolPlan {
    // A record whose file has gone is dropped for free: there is nothing to
    // delete and nothing to show.
    var kept = entries.filter { it.fileName in existingFiles }
    val dropped = ArrayList<PoolEntry>()

    val removable = { entry: PoolEntry -> !entry.pinned && entry.lastShownAt != 0L }
    while (kept.size > maxEntries || kept.sumOf { it.bytes } > maxBytes) {
        val victim = kept.filter(removable).minByOrNull { it.lastShownAt } ?: break
        dropped += victim
        kept = kept - victim
    }
    return PoolPlan(
        keep = kept,
        deleteFiles = dropped.map { it.fileName },
        evictedKeys = dropped.mapNotNull { entry ->
            entry.credit?.let { "${it.provider}:${it.photoId}" }
        },
    )
}

/** Conditions a top-up has to get past before it may use the network. */
data class PhotoNetworkConditions(
    val unlocked: Boolean,
    val online: Boolean,
    val metered: Boolean,
    val powerSaving: Boolean,
    val highContrastKeys: Boolean,
)

/**
 * Whether the pool may be topped up from the network right now.
 *
 * Rotation itself is never gated on any of this: a phone in a tunnel for a
 * week still rotates through the photos it already has. Only refilling stops.
 */
fun PhotoBackgroundSettings.mayFetch(conditions: PhotoNetworkConditions): Boolean = when {
    !rotateEnabled -> false
    !usesNetwork -> false
    // Locked, the pool is in credential-encrypted storage: there is nothing to
    // read and nowhere to write.
    !conditions.unlocked -> false
    !conditions.online -> false
    // High-contrast keys drops background images entirely, so these would be
    // photos nobody ever sees.
    conditions.highContrastKeys -> false
    // A top-up nobody asked for is exactly what power saving is meant to stop.
    conditions.powerSaving -> false
    conditions.metered && !fetchOnMetered -> false
    else -> true
}

/**
 * Whether a top-up is worth making now: below the floor it is urgent, and
 * otherwise only once the pool has had time to go stale.
 */
fun PhotoBackgroundSettings.needsTopUp(
    readyCount: Int,
    lastTopUpAt: Long,
    nowMs: Long,
): Boolean = when {
    readyCount < POOL_MIN -> true
    readyCount >= poolTarget -> false
    else -> nowMs - lastTopUpAt >= PhotoBackgroundSettings.MIN_FETCH_PERIOD_MS * TOP_UP_IDLE_PERIODS
}

/** Below this the pool cannot do its job, so a top-up is urgent. */
const val POOL_MIN = 4

/** Most photos one top-up will fetch, so one run cannot spend the budget. */
const val MAX_FETCH_PER_RUN = 6

/** How many minimum periods to wait before topping a partly full pool. */
private const val TOP_UP_IDLE_PERIODS = 6
