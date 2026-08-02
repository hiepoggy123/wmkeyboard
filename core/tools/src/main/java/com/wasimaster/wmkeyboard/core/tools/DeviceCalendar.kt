package com.wasimaster.wmkeyboard.core.tools

import android.content.Context
import android.provider.CalendarContract
import android.text.format.DateUtils
import com.wasimaster.wmkeyboard.tools.R
import java.util.Calendar
import java.util.TimeZone

/**
 * One event instance read from the device calendar for a single day.
 * [begin]/[end] are epoch millis; an [allDay] event covers the whole day and
 * carries no meaningful clock time.
 */
data class DeviceCalendarEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    /** The calendar's display colour (ARGB int); 0 when the provider gives none. */
    val color: Int,
)

/**
 * Read-only view of the device's calendar through [CalendarContract.Instances].
 * The Instances table expands recurring events, so a weekly meeting shows up
 * on every day it actually lands on. Callers must hold `READ_CALENDAR`;
 * without it the query throws and we return an empty list rather than crash.
 */
object DeviceCalendar {

    private val PROJECTION = arrayOf(
        CalendarContract.Instances.EVENT_ID,      // 0
        CalendarContract.Instances.TITLE,         // 1
        CalendarContract.Instances.BEGIN,         // 2
        CalendarContract.Instances.END,           // 3
        CalendarContract.Instances.ALL_DAY,       // 4
        CalendarContract.Instances.EVENT_LOCATION, // 5
        CalendarContract.Instances.DISPLAY_COLOR, // 6
    )

    /**
     * Events touching the local day [year]-[month]-[day] (month 1-12), all-day
     * first then by start time. Returns empty on any failure — no permission,
     * no provider, or a grant revoked mid-session.
     */
    fun eventsForDay(context: Context, year: Int, month: Int, day: Int): List<DeviceCalendarEvent> {
        val dayStart = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayEnd = dayStart + DateUtils.DAY_IN_MILLIS
        // All-day events are stored at UTC midnight, so one can sit just
        // outside a local-time window; widen the query a day each side and
        // re-filter to the exact day below.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath((dayStart - DateUtils.DAY_IN_MILLIS).toString())
            .appendPath((dayEnd + DateUtils.DAY_IN_MILLIS).toString())
            .build()

        val targetEpochDay = utcEpochDay(year, month, day)
        // Read once, outside the cursor loop.
        val noTitle = context.getString(R.string.core_tools_calendar_event_no_title)
        val events = ArrayList<DeviceCalendarEvent>()
        runCatching {
            context.contentResolver.query(
                uri, PROJECTION, null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val begin = c.getLong(2)
                    val end = c.getLong(3)
                    val allDay = c.getInt(4) != 0
                    val onDay = if (allDay) {
                        // BEGIN/END are UTC midnights; compare whole dates.
                        targetEpochDay in (begin / DateUtils.DAY_IN_MILLIS) until
                            (end / DateUtils.DAY_IN_MILLIS)
                    } else {
                        end > dayStart && begin < dayEnd
                    }
                    if (!onDay) continue
                    events.add(
                        DeviceCalendarEvent(
                            id = c.getLong(0),
                            title = c.getString(1)?.takeIf { it.isNotBlank() } ?: noTitle,
                            begin = begin,
                            end = end,
                            allDay = allDay,
                            location = c.getString(5)?.takeIf { it.isNotBlank() },
                            color = c.getInt(6),
                        ),
                    )
                }
            }
        }
        return events.sortedWith(
            compareByDescending<DeviceCalendarEvent> { it.allDay }.thenBy { it.begin },
        )
    }

    /** The UTC-midnight epoch day of a Gregorian date, matching all-day storage. */
    private fun utcEpochDay(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }
        return cal.timeInMillis / DateUtils.DAY_IN_MILLIS
    }
}
