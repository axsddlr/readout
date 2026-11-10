package dev.readout.app.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {

    fun duration(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return "—"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes.toString().padStart(2, '0')}m"
            minutes > 0 -> "${minutes}m ${(seconds % 60).toString().padStart(2, '0')}s"
            else -> "${seconds}s"
        }
    }

    fun eta(seconds: Long?): String = if (seconds == null || seconds <= 0) "—" else duration(seconds)

    /** When the job should finish, as wall-clock time — a duration is not something you can plan around. */
    fun etaEpochMillis(nowMillis: Long, secondsLeft: Long?): Long? =
        if (secondsLeft == null || secondsLeft <= 0) null else nowMillis + secondsLeft * 1000

    /** Formatted in the device's locale and 12/24-hour setting. */
    fun clockTime(context: Context, epochMillis: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(epochMillis))

    fun percent(fraction: Float?): String =
        if (fraction == null) "—" else "${(fraction * 100).toInt()}%"

    fun celsius(value: Float): String = "${value.toInt()}°"

    fun targetAndActual(actual: Float, target: Float): String =
        if (target <= 0f) "${actual.toInt()}°" else "${actual.toInt()}° / ${target.toInt()}°"

    fun bytes(value: Long): String {
        if (value <= 0) return "—"
        val units = listOf("B", "KB", "MB", "GB")
        var size = value.toDouble()
        var index = 0
        while (size >= 1024 && index < units.lastIndex) {
            size /= 1024
            index++
        }
        return if (index == 0) "${value} B" else String.format(Locale.US, "%.1f %s", size, units[index])
    }

    fun fileDate(seconds: Long?): String {
        if (seconds == null) return ""
        return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(seconds * 1000))
    }

    fun age(milliseconds: Long?): String {
        if (milliseconds == null) return "no reading"
        val age = (System.currentTimeMillis() - milliseconds).coerceAtLeast(0)
        return when {
            age < 1_500 -> "just now"
            age < 60_000 -> "${age / 1000}s ago"
            age < 3_600_000 -> "${age / 60_000}m ago"
            else -> "${age / 3_600_000}h ago"
        }
    }

    // Provenance of the estimate, shown under the clock time. It deliberately does not repeat
    // "ETA": the line above already says it.
    fun etaOrigin(origin: String?): String? = when (origin) {
        null, "" -> null
        "average" -> "From completed prints"
        "estimate" -> "Estimated from the file"
        "analysis" -> "From file analysis"
        "mixed-average" -> "Blended with history"
        "mixed-analysis" -> "Blended with analysis"
        "genius" -> "From PrintTimeGenius"
        "linear" -> "Linear extrapolation from progress"
        else -> "Source: $origin"
    }
}
