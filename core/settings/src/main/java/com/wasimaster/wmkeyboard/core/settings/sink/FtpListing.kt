package com.wasimaster.wmkeyboard.core.settings.sink

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Reads what an FTP server answers `MLSD` and `LIST` with.
 *
 * The reason this file exists at all: **`LIST` has no defined format.** The
 * output is whatever the server felt like printing, and in practice that is
 * either Unix `ls -l` or a DOS-style listing from an old Windows server. Worse,
 * the Unix form omits the year for recent files and gives a time instead, so a
 * date cannot be read without guessing which side of today it falls on.
 *
 * `MLSD` fixed all of this in RFC 3659 by defining a machine-readable format,
 * and most servers made in this century support it. So the sink asks for `MLSD`
 * first and only falls back to parsing prose.
 *
 * Pure, so the three formats can be tested without a server.
 */
object FtpListing {

    data class Entry(
        val name: String,
        val sizeBytes: Long,
        val modifiedAtMs: Long,
        val isDirectory: Boolean,
    )

    /**
     * `MLSD`: `type=file;size=4096;modify=20250807140233; name.json`
     *
     * Facts before the space, name after it. The name may itself contain
     * spaces, so it is everything past the *first* one.
     */
    fun parseMlsd(lines: List<String>): List<Entry> = lines.mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val separator = line.indexOf(' ')
        if (separator < 0) return@mapNotNull null
        val name = line.substring(separator + 1).trim()
        if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null

        val facts = line.substring(0, separator).split(';')
            .mapNotNull { fact ->
                val eq = fact.indexOf('=')
                if (eq <= 0) null else fact.substring(0, eq).lowercase(Locale.US) to
                    fact.substring(eq + 1)
            }
            .toMap()

        Entry(
            name = name,
            sizeBytes = facts["size"]?.toLongOrNull() ?: -1L,
            modifiedAtMs = facts["modify"]?.let(::parseMlsdTime) ?: 0L,
            // A server may say "cdir" or "pdir" for . and .., already dropped.
            isDirectory = facts["type"]?.equals("dir", ignoreCase = true) == true,
        )
    }

    /** `20250807140233`, always UTC by the RFC. */
    fun parseMlsdTime(value: String): Long = runCatching {
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(value.substringBefore('.'))
            ?.time
            ?: 0L
    }.getOrDefault(0L)

    /**
     * `LIST`, in either of the two shapes servers actually emit.
     *
     * [nowMs] is only used to resolve the Unix format's missing year, and is a
     * parameter so the tests are not at the mercy of the calendar.
     */
    fun parseList(lines: List<String>, nowMs: Long): List<Entry> = lines.mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        parseUnix(trimmed, nowMs) ?: parseDos(trimmed)
    }

    /** `-rw-r--r-- 1 owner group 4096 Aug 7 14:02 name.json` */
    private fun parseUnix(line: String, nowMs: Long): Entry? {
        if (line.length < 10) return null
        val kind = line[0]
        if (kind != '-' && kind != 'd' && kind != 'l') return null
        // Eight fields then the name, which may contain spaces.
        val parts = line.split(Regex("\\s+"), limit = 9)
        if (parts.size < 9) return null
        val name = parts[8].substringBefore(" -> ").trim()
        if (name.isEmpty() || name == "." || name == "..") return null
        return Entry(
            name = name,
            sizeBytes = parts[4].toLongOrNull() ?: -1L,
            modifiedAtMs = parseUnixDate(parts[5], parts[6], parts[7], nowMs),
            isDirectory = kind == 'd',
        )
    }

    /** `08-07-25  02:02PM               4096 name.json` */
    private fun parseDos(line: String): Entry? {
        val parts = line.split(Regex("\\s+"), limit = 4)
        if (parts.size < 4) return null
        // A date, not merely something with a hyphen in it. Testing for the
        // hyphen alone matched every Unix line, because `drwxr-xr-x` has three.
        if (!DOS_DATE.matches(parts[0])) return null
        val sizeOrDir = parts[2]
        val isDirectory = sizeOrDir.equals("<DIR>", ignoreCase = true)
        val name = parts[3].trim()
        if (name.isEmpty() || name == "." || name == "..") return null
        return Entry(
            name = name,
            sizeBytes = if (isDirectory) -1L else sizeOrDir.toLongOrNull() ?: -1L,
            modifiedAtMs = runCatching {
                SimpleDateFormat("MM-dd-yy hh:mma", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse("${parts[0]} ${parts[1]}")
                    ?.time
                    ?: 0L
            }.getOrDefault(0L),
            isDirectory = isDirectory,
        )
    }

    /**
     * The Unix listing's date, which is `Aug 7 14:02` for anything in the last
     * six months and `Aug 7 2024` for anything older.
     *
     * The recent form has no year, so it has to be inferred: take this year,
     * and if that lands more than a day in the future, it meant last year.
     * Only ordering depends on this, and rotation falls back to the name in the
     * file, so a wrong guess costs nothing.
     */
    private fun parseUnixDate(month: String, day: String, yearOrTime: String, nowMs: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        return runCatching {
            if (yearOrTime.contains(':')) {
                calendar.timeInMillis = nowMs
                val thisYear = calendar.get(Calendar.YEAR)
                val parsed = SimpleDateFormat("MMM d yyyy HH:mm", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse("$month $day $thisYear $yearOrTime")
                    ?.time
                    ?: return 0L
                if (parsed > nowMs + DAY_MS) parsed - YEAR_MS else parsed
            } else {
                SimpleDateFormat("MMM d yyyy", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse("$month $day $yearOrTime")
                    ?.time
                    ?: 0L
            }
        }.getOrDefault(0L)
    }

    private val DOS_DATE = Regex("""\d{1,2}-\d{1,2}-\d{2,4}""")

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val YEAR_MS = 365L * DAY_MS

    /**
     * The host and port out of a `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)`.
     *
     * The port arrives as two bytes of a big-endian number, which is the part
     * everyone gets wrong once.
     */
    fun parsePasv(reply: String): Pair<String, Int>? {
        val numbers = Regex("""(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)""")
            .find(reply)
            ?.groupValues
            ?.drop(1)
            ?.mapNotNull { it.toIntOrNull() }
            ?: return null
        if (numbers.size != 6 || numbers.any { it !in 0..255 }) return null
        val host = numbers.take(4).joinToString(".")
        return host to (numbers[4] * 256 + numbers[5])
    }

    /**
     * The port out of an `229 Entering Extended Passive Mode (|||port|)`.
     *
     * `EPSV` gives no address, on purpose: the data connection goes back to the
     * same host as the control connection, which is what makes it work through
     * NAT where `PASV` hands out an unroutable private address.
     */
    fun parseEpsv(reply: String): Int? =
        Regex("""\(\|\|\|(\d+)\|\)""").find(reply)?.groupValues?.get(1)?.toIntOrNull()
}
