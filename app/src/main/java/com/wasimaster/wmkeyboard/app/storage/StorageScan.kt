package com.wasimaster.wmkeyboard.app.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.wasimaster.wmkeyboard.core.directboot.DirectBoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Measures what the app is using on disk.
 *
 * The headline number has to be the same one the system shows under Settings →
 * Apps → Storage, or the screen is worse than useless: a user who sees 4 GB
 * here and 6 GB there trusts neither. So the totals are not ours at all — they
 * come from [StorageStatsManager], which is what the system's own page reads.
 * Only the *breakdown* is ours, and whatever our categories fail to account for
 * is shown as a residual rather than quietly dropped. The bar therefore always
 * adds up to the system's figure.
 *
 * Three things a naive `File.length()` walk gets wrong, all handled here:
 *
 *  * **Apparent size is not disk usage.** A 40-byte SVG in an icon pack still
 *    costs a whole filesystem block. With a few thousand stickers and icons the
 *    difference runs to megabytes, which is enough to make the residual look
 *    like a bug. Every file is rounded up to the block size, `du`-style.
 *  * **`dataDir/lib` is a symlink** into `/data/app`, and following it counts
 *    the native libraries a second time — they are already in `appBytes`.
 *  * **Direct boot storage is real storage.** `queryStatsForPackage` covers the
 *    device-protected tree as well as the credential-encrypted one, so anything
 *    that only walks `filesDir` under-reports and inflates the residual.
 */

/** The six roots everything is measured under, plus the filesystem block size. */
internal data class StorageRoots(
    /** Credential-encrypted `files/`; readable only once the user has unlocked. */
    val files: File,
    val cache: File,
    val codeCache: File,
    /** Device-protected `files/`; readable from boot. See [DirectBoot]. */
    val deFiles: File,
    val deCache: File,
    val prefs: File,
    val dePrefs: File,
    val dataDir: File,
    val deDataDir: File,
    val blockSize: Long,
    /** False before first unlock, when the credential-encrypted roots cannot be read. */
    val unlocked: Boolean,
)

internal fun storageRoots(context: Context): StorageRoots {
    val de = DirectBoot.deviceContext(context)
    val dataDir = context.dataDir
    return StorageRoots(
        files = context.filesDir,
        cache = context.cacheDir,
        codeCache = context.codeCacheDir,
        deFiles = de.filesDir,
        deCache = de.cacheDir,
        prefs = File(dataDir, "shared_prefs"),
        dePrefs = File(de.dataDir, "shared_prefs"),
        dataDir = dataDir,
        deDataDir = de.dataDir,
        blockSize = runCatching { StatFs(dataDir.path).blockSizeLong }
            .getOrNull()?.takeIf { it > 0 } ?: DEFAULT_BLOCK,
        unlocked = DirectBoot.isUserUnlocked(context),
    )
}

private const val DEFAULT_BLOCK = 4096L

/** One category's measured share. */
internal data class CategorySize(val bytes: Long, val items: Int)

/**
 * A snapshot of the whole picture. Emitted repeatedly while a scan runs, with
 * [scanning] true until the last category has been measured.
 */
internal data class StorageReport(
    val appBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val deviceBytes: Long = 0L,
    val sizes: Map<String, CategorySize> = emptyMap(),
    /**
     * False when the totals are our own estimate rather than the system's.
     * Only happens below API 26, where [StorageStatsManager] does not exist.
     */
    val exact: Boolean = true,
    val scanning: Boolean = true,
) {
    /** What the system's App info page calls "Total". */
    val totalBytes: Long get() = appBytes + dataBytes

    /** What it calls "User data": everything but the cache. */
    val userDataBytes: Long get() = (dataBytes - cacheBytes).coerceAtLeast(0L)

    fun bytesOf(id: String): Long = sizes[id]?.bytes ?: 0L

    fun itemsOf(id: String): Int = sizes[id]?.items ?: 0
}

/**
 * Measures every category, emitting as it goes so the screen fills in rather
 * than staring at a spinner: the totals land immediately, then one category at
 * a time, largest trees last.
 */
internal fun scanStorage(
    context: Context,
    categories: List<StorageCategory> = StorageCategories.all,
): Flow<StorageReport> = flow {
    val roots = storageRoots(context)
    // The app's own size arrives with the totals, so it can be shown at once
    // rather than sitting at zero until the walk finishes.
    val sizes = linkedMapOf(
        StorageCategories.APP_PACKAGE to CategorySize(0L, 0),
    )
    var report = totals(context, roots)
    sizes[StorageCategories.APP_PACKAGE] = CategorySize(report.appBytes, 0)
    report = report.copy(sizes = LinkedHashMap(sizes))
    emit(report)
    for (category in categories) {
        if (category.synthetic) continue
        val paths = category.paths(roots)
        sizes[category.id] = CategorySize(
            bytes = paths.sumOf { diskUsage(it, roots.blockSize) },
            items = category.count(roots),
        )
        report = report.copy(sizes = LinkedHashMap(sizes))
        emit(report)
    }
    emit(report.withResiduals(categories).copy(scanning = false))
}.flowOn(Dispatchers.IO)

/**
 * The two read-only rows that make the breakdown add up: whatever the system
 * reports minus everything we could name. Clamped at zero, because block
 * accounting can make our walk marginally overshoot on a category the system
 * counts differently, and a negative row would be nonsense.
 */
internal fun StorageReport.withResiduals(categories: List<StorageCategory>): StorageReport {
    val measured = categories.filterNot { it.synthetic }
    val cacheSum = measured.filter { it.group == StorageGroup.CACHE }.sumOf { bytesOf(it.id) }
    val dataSum = measured.filterNot { it.group == StorageGroup.CACHE }.sumOf { bytesOf(it.id) }
    val withApp = sizes + (StorageCategories.APP_PACKAGE to CategorySize(appBytes, 0))
    return copy(
        sizes = withApp +
            (StorageCategories.OTHER_CACHE to CategorySize((cacheBytes - cacheSum).coerceAtLeast(0L), 0)) +
            (StorageCategories.OTHER_DATA to CategorySize((userDataBytes - dataSum).coerceAtLeast(0L), 0)),
    )
}

// ---- totals ----

private fun totals(context: Context, roots: StorageRoots): StorageReport {
    val stats = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) systemTotals(context) else null
    val device = deviceTotals(context, roots)
    return stats?.copy(freeBytes = device.first, deviceBytes = device.second)
        ?: estimatedTotals(context, roots).copy(freeBytes = device.first, deviceBytes = device.second)
}

/**
 * The system's own figures. No permission is needed to ask about our own
 * package — `PACKAGE_USAGE_STATS` is only for asking about somebody else's, and
 * this screen must keep working whether or not the user granted it for the
 * clipboard's "show source app" setting.
 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun systemTotals(context: Context): StorageReport? = runCatching {
    val manager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
    val stats = manager.queryStatsForPackage(
        StorageManager.UUID_DEFAULT,
        context.packageName,
        Process.myUserHandle(),
    )
    StorageReport(
        appBytes = stats.appBytes,
        // getDataBytes already includes the cache; the system's page subtracts
        // it to show "User data", and so does [StorageReport.userDataBytes].
        dataBytes = stats.dataBytes,
        cacheBytes = stats.cacheBytes,
    )
}.getOrNull()

/** Below API 26 there is no [StorageStatsManager], so measure it ourselves. */
private fun estimatedTotals(context: Context, roots: StorageRoots): StorageReport {
    val info = context.applicationInfo
    val code = buildList {
        add(File(info.sourceDir))
        info.splitSourceDirs?.forEach { add(File(it)) }
        info.nativeLibraryDir?.let { add(File(it)) }
    }
    val cache = diskUsage(roots.cache, roots.blockSize) +
        diskUsage(roots.codeCache, roots.blockSize) +
        diskUsage(roots.deCache, roots.blockSize)
    return StorageReport(
        appBytes = code.sumOf { diskUsage(it, roots.blockSize) },
        dataBytes = diskUsage(roots.dataDir, roots.blockSize) +
            diskUsage(roots.deDataDir, roots.blockSize),
        cacheBytes = cache,
        exact = false,
    )
}

/** Free and total bytes on the volume the app lives on: `free to total`. */
private fun deviceTotals(context: Context, roots: StorageRoots): Pair<Long, Long> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val fromSystem = runCatching {
            val manager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            manager.getFreeBytes(StorageManager.UUID_DEFAULT) to
                manager.getTotalBytes(StorageManager.UUID_DEFAULT)
        }.getOrNull()
        if (fromSystem != null) return fromSystem
    }
    return runCatching {
        val fs = StatFs(roots.dataDir.path)
        fs.availableBlocksLong * fs.blockSizeLong to fs.blockCountLong * fs.blockSizeLong
    }.getOrDefault(0L to 0L)
}

// ---- walking ----

/**
 * Disk usage of one path, the way `du` counts it: every file rounded up to a
 * whole block, one block per directory, symlinks measured as nothing and never
 * followed.
 */
internal fun diskUsage(root: File, blockSize: Long = DEFAULT_BLOCK): Long {
    if (!root.exists() || isLink(root)) return 0L
    if (root.isFile) return blocksFor(root.length(), blockSize)
    var total = 0L
    val pending = ArrayDeque<File>()
    pending.addLast(root)
    while (pending.isNotEmpty()) {
        val dir = pending.removeLast()
        total += blockSize
        val children = dir.listFiles() ?: continue
        for (child in children) {
            if (isLink(child)) continue
            if (child.isDirectory) pending.addLast(child) else total += blocksFor(child.length(), blockSize)
        }
    }
    return total
}

private fun blocksFor(bytes: Long, blockSize: Long): Long =
    if (bytes <= 0L) 0L else ((bytes + blockSize - 1) / blockSize) * blockSize

/**
 * Whether a path is a symbolic link.
 *
 * The obvious test — canonical path differs from absolute path — is wrong here.
 * `/data/user/0` is itself a link to `/data/data`, so under it *every* path
 * looks like a link. Comparing the canonical parents instead asks the question
 * that actually matters: does this entry live where it claims to.
 */
private fun isLink(file: File): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        java.nio.file.Files.isSymbolicLink(file.toPath())
    } else {
        val parent = file.parentFile?.canonicalFile ?: return@runCatching false
        file.canonicalFile.parentFile != parent
    }
}.getOrDefault(false)

/** Direct children of [dir], or an empty list when it is missing or unreadable. */
internal fun childrenOf(dir: File): List<File> = dir.listFiles()?.toList().orEmpty()

/** Every file under [root], flattened, links skipped. Used to list a category's items. */
internal fun filesUnder(root: File): List<File> {
    if (!root.exists() || isLink(root)) return emptyList()
    if (root.isFile) return listOf(root)
    val out = ArrayList<File>()
    val pending = ArrayDeque<File>()
    pending.addLast(root)
    while (pending.isNotEmpty()) {
        val children = pending.removeLast().listFiles() ?: continue
        for (child in children) {
            if (isLink(child)) continue
            if (child.isDirectory) pending.addLast(child) else out += child
        }
    }
    return out
}
