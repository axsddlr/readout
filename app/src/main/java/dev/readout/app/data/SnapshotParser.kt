package dev.readout.app.data

import org.json.JSONObject

/**
 * Translates OctoPrint's `/api/printer` and `/api/job` payloads into one snapshot.
 *
 * The parser never invents a value: a missing field stays null so the UI can omit it rather
 * than display a zero that looks like a measurement.
 */
object SnapshotParser {

    fun parse(printer: JSONObject, job: JSONObject?, version: String?): PrinterSnapshot {
        val stateObject = printer.optJSONObject("state")
        val flags = stateObject?.optJSONObject("flags")
        val machineState = mapState(flags)
        val progress = job?.optJSONObject("progress")
        val jobInfo = job?.optJSONObject("job")
        val file = jobInfo?.optJSONObject("file")

        return PrinterSnapshot(
            state = machineState,
            stateLabel = stateObject?.optString("text").orEmpty().ifBlank { defaultLabel(machineState) },
            temperatures = parseTemperatures(printer),
            jobName = file?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
            progress = progress?.let { p ->
                if (p.has("completion") && !p.isNull("completion")) {
                    (p.optDouble("completion", 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                } else null
            },
            printTimeSeconds = progress?.seconds("printTime"),
            printTimeLeftSeconds = progress?.seconds("printTimeLeft"),
            etaOrigin = progress?.optString("printTimeLeftOrigin")?.takeIf { it.isNotBlank() },
            layer = progress?.optIntOrNull("layer"),
            layerCount = progress?.optIntOrNull("layerCount") ?: jobInfo?.optIntOrNull("layerCount"),
            octoPrintVersion = version,
            connectionText = printer.optJSONObject("connection")
                ?.optJSONObject("current")
                ?.optString("state")
                ?.takeIf { it.isNotBlank() }
        )
    }

    private fun JSONObject.seconds(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, -1.0)
        return if (value < 0) null else value.toLong()
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val value = optInt(key, -1)
        return if (value < 0) null else value
    }

    private fun mapState(flags: JSONObject?): MachineState {
        if (flags == null) return MachineState.Unknown
        fun on(key: String) = flags.optBoolean(key, false)
        return when {
            on("error") || on("closedOrError") -> MachineState.Error
            on("cancelling") -> MachineState.Cancelling
            on("pausing") -> MachineState.Pausing
            on("resuming") -> MachineState.Resuming
            on("paused") -> MachineState.Paused
            on("printing") -> MachineState.Printing
            on("finishing") -> MachineState.Finishing
            on("starting") -> MachineState.Starting
            on("operational") || on("ready") -> MachineState.Idle
            else -> MachineState.Unknown
        }
    }

    private fun defaultLabel(state: MachineState): String = when (state) {
        MachineState.Idle -> "Idle"
        MachineState.Starting -> "Starting"
        MachineState.Printing -> "Printing"
        MachineState.Paused -> "Paused"
        MachineState.Pausing -> "Pausing"
        MachineState.Resuming -> "Resuming"
        MachineState.Cancelling -> "Cancelling"
        MachineState.Finishing -> "Finishing"
        MachineState.Error -> "Fault"
        MachineState.Disconnected -> "Disconnected"
        MachineState.Unknown -> "Unknown"
    }

    private fun parseTemperatures(printer: JSONObject): List<ComponentTemperature> {
        val temperatures = printer.optJSONObject("temperature") ?: return emptyList()
        val result = mutableListOf<ComponentTemperature>()

        val keys = temperatures.keys().asSequence().toList()
        val tools = keys.filter { it.startsWith("tool") }.sortedBy { it.removePrefix("tool").toIntOrNull() ?: 0 }
        tools.forEachIndexed { index, key ->
            temperatures.optJSONObject(key)?.let { entry ->
                result += ComponentTemperature(
                    key = key,
                    label = if (index == 0) "Nozzle" else "Nozzle ${index + 1}",
                    actual = entry.optDouble("actual", 0.0).toFloat(),
                    target = entry.optDouble("target", 0.0).toFloat(),
                    index = index
                )
            }
        }
        temperatures.optJSONObject("bed")?.let { entry ->
            result += ComponentTemperature(
                key = "bed",
                label = "Bed",
                actual = entry.optDouble("actual", 0.0).toFloat(),
                target = entry.optDouble("target", 0.0).toFloat(),
                index = 0
            )
        }
        temperatures.optJSONObject("chamber")?.let { entry ->
            result += ComponentTemperature(
                key = "chamber",
                label = "Chamber",
                actual = entry.optDouble("actual", 0.0).toFloat(),
                target = entry.optDouble("target", 0.0).toFloat(),
                index = 0
            )
        }
        return result
    }

    fun parseFiles(raw: org.json.JSONArray): List<FileEntry> {
        val out = mutableListOf<FileEntry>()
        for (i in 0 until raw.length()) {
            val entry = raw.optJSONObject(i) ?: continue
            val name = entry.optString("name")
            val path = entry.optString("path").ifBlank { name }
            if (name.isBlank()) continue
            val type = entry.optString("type")
            out += FileEntry(
                name = name,
                path = path,
                origin = entry.optString("origin", "local"),
                sizeBytes = entry.optLong("size", 0L),
                dateSeconds = if (entry.has("date")) entry.optLong("date") else null,
                isDirectory = type == "folder"
            )
        }
        // Folders first, then newest first: the order a person scans a file list in.
        // The `0L` matters: `?: 0` widens the selector to Comparable<*>, and comparing a Long
        // entry against the Int literal throws Integer cannot be cast to Long.
        return out.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }
                .thenByDescending { it.dateSeconds ?: 0L }
        )
    }
}
