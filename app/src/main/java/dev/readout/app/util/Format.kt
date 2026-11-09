package dev.readout.app.util

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

    fun etaOrigin(origin: String?): String? = when (origin) {
        null, "" -> null
        "average" -> "ETA from completed prints"
        "estimate" -> "ETA estimated from the file"
        "analysis" -> "ETA from file analysis"
        "mixed-average" -> "ETA blended with history"
        "mixed-analysis" -> "ETA blended with analysis"
        "genius" -> "ETA from PrintTimeGenius"
        "linear" -> "ETA by linear extrapolation"
        else -> "ETA source: $origin"
    }
}
