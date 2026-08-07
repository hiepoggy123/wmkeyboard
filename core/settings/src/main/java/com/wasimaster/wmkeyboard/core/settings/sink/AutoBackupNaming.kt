package com.wasimaster.wmkeyboard.core.settings.sink

import com.wasimaster.wmkeyboard.core.settings.ConfigBackup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * What an automatic backup is called, and which of them rotation may delete.
 *
 * Pure on purpose. The destination is a folder the user picked, which may well
 * be Downloads, and [rotation] hands its answer straight to
 * [BackupSink.delete] — so the rules about what counts as ours are the only
 * thing standing between a rotation pass and somebody's tax return. They are
 * kept here, away from any [android.content.ContentResolver], so they can be
 * tested.
 */
object AutoBackupNaming {

    /**
     * Distinct from the `wmkeyboard-backup-` that a hand-made export uses, so
     * rotation can never eat a file the user made deliberately.
     */
    const val PREFIX = "wmkeyboard-auto-"

    /** A backup that is still being written, or was being written when we died. */
    const val PART_SUFFIX = ".part"

    /**
     * [Locale.US] because a Thai-Buddhist locale stamps 2569 for 2026, and a
     * name whose job is to sort by date has to mean the same thing on every
     * device. Same format and same reason as the manual export's name.
     */
    private const val STAMP_FORMAT = "yyyyMMdd-HHmmss"

    /**
     * The name for a backup taken at [stampMs].
     *
     * [zone] is a parameter only so tests are not at the mercy of the machine
     * running them; every caller in the app takes the default.
     */
    fun name(
        stampMs: Long,
        encrypted: Boolean,
        zone: TimeZone = TimeZone.getDefault(),
    ): String {
        val stamp = SimpleDateFormat(STAMP_FORMAT, Locale.US)
            .apply { timeZone = zone }
            .format(Date(stampMs))
        val extension =
            if (encrypted) ConfigBackup.ENCRYPTED_FILE_EXTENSION else ConfigBackup.FILE_EXTENSION
        return "$PREFIX$stamp.$extension"
    }

    /**
     * Whether [displayName] is a finished automatic backup of ours.
     *
     * Deliberately strict. A file we do not recognise is left alone forever,
     * which costs nothing; a file we wrongly claim is deleted.
     */
    fun isOurs(displayName: String): Boolean =
        displayName.startsWith(PREFIX) &&
            !displayName.endsWith(PART_SUFFIX) &&
            (
                displayName.endsWith(".${ConfigBackup.FILE_EXTENSION}") ||
                    displayName.endsWith(".${ConfigBackup.ENCRYPTED_FILE_EXTENSION}")
                )

    /** Whether [displayName] is one of our half-written files, ours to sweep. */
    fun isPart(displayName: String): Boolean =
        displayName.startsWith(PREFIX) && displayName.endsWith(PART_SUFFIX)

    /**
     * The entries to delete so that at most [keep] remain, oldest first.
     *
     * Sorted by [SinkEntry.modifiedAtMs] with the name as tiebreaker, because
     * some providers report a coarse modified time and some report none at all
     * — the name embeds the second, so it is the better key of the two whenever
     * the times collide.
     *
     * [keep] is floored at 1. A rotation that empties the folder is never what
     * the user meant, whatever they typed in the box.
     */
    fun rotation(entries: List<SinkEntry>, keep: Int): List<SinkEntry> {
        val survivors = keep.coerceAtLeast(1)
        if (entries.size <= survivors) return emptyList()
        val oldestFirst = entries.sortedWith(compareBy({ it.modifiedAtMs }, { it.name }))
        return oldestFirst.take(entries.size - survivors)
    }
}
