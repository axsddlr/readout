package dev.readout.app.wear

import org.json.JSONObject

/** What the watch needs to know, and nothing more. */
data class WearSnapshot(
    val stateLabel: String,
    val printing: Boolean,
    val paused: Boolean,
    val progress: Float?,
    val etaSeconds: Int?,
    val nozzleActual: Float?,
    val nozzleTarget: Int?,
    val bedActual: Float?,
    val bedTarget: Int?
) {
    /** One line for the watch face: 41% printing, or "Idle". */
    val headline: String
        get() = when {
            printing && paused -> "PAUSED"
            printing -> "PRINTING"
            else -> stateLabel.uppercase().ifBlank { "UNKNOWN" }
        }
}

/**
 * Parses OctoPrint's `/api/printer` and `/api/job` payloads. Kept free of Android types so it
 * can be tested on the JVM.
 */
object WearParser {

    fun parse(printerJson: String, jobJson: String?): WearSnapshot {
        val printer = JSONObject(printerJson)
        val state = printer.optJSONObject("state")
        val flags = state?.optJSONObject("flags") ?: JSONObject()
        val stateText = state?.optString("text").orEmpty()

        val job = jobJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val progress = job?.optJSONObject("progress")

        val completion = progress?.takeIf { it.has("completion") && !it.isNull("completion") }
            ?.let { (it.optDouble("completion", 0.0) / 100.0).toFloat() }
        val eta = progress?.takeIf { it.has("printTimeLeft") && !it.isNull("printTimeLeft") }
            ?.optInt("printTimeLeft")

        val temperature = printer.optJSONObject("temperature")
        val tool = temperature?.optJSONObject("tool0")
        val bed = temperature?.optJSONObject("bed")

        return WearSnapshot(
            stateLabel = stateText,
            printing = flags.optBoolean("printing", false),
            paused = flags.optBoolean("paused", false) || flags.optBoolean("pausing", false),
            progress = completion,
            etaSeconds = eta,
            nozzleActual = tool?.optDouble("actual")?.toFloat(),
            nozzleTarget = tool?.optInt("target"),
            bedActual = bed?.optDouble("actual")?.toFloat(),
            bedTarget = bed?.optInt("target")
        )
    }
}
