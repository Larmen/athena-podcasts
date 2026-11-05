package se.anders.athenapodcaster.ui

import java.text.DateFormat
import java.util.Date

/** "1 h 05 min", "42 min", "50 s". Null or zero gives null so callers can omit it. */
fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    return when {
        h > 0 -> "%d h %02d min".format(h, m)
        m > 0 -> "%d min".format(m)
        else -> "%d s".format(seconds)
    }
}

/** Medium localised date, or null when the feed gave none. */
fun formatDate(epochMillis: Long): String? =
    if (epochMillis <= 0) null else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

/** Player clock: "3:07" or "1:02:03". */
fun formatClock(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
