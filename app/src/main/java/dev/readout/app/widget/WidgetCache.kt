package dev.readout.app.widget

import android.content.Context
import dev.readout.app.data.ComponentTemperature
import dev.readout.app.data.MachineState
import dev.readout.app.data.PrinterSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * Widgets render from a cached snapshot so they can paint instantly after a reboot or when the
 * app process is dead; a network refresh replaces it opportunistically.
 */
object WidgetCache {

    private const val PREFS = "readout.widgets"
    private const val KEY = "snapshot"
    private const val KEY_UPDATED = "updated_at"

    fun write(context: Context, snapshot: PrinterSnapshot?, updatedAt: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (snapshot == null) {
            prefs.edit().remove(KEY).remove(KEY_UPDATED).apply()
        } else {
            prefs.edit().putString(KEY, encode(snapshot)).putLong(KEY_UPDATED, updatedAt).apply()
        }
    }

    /** Wall-clock time of the cached reading, or null when there is none. */
    fun updatedAt(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) return null
        return prefs.getLong(KEY_UPDATED, 0L).takeIf { it > 0 }
    }

    fun read(context: Context): PrinterSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    private fun encode(snapshot: PrinterSnapshot): String {
        val temperatures = JSONArray()
        snapshot.temperatures.forEach { component ->
            temperatures.put(
                JSONObject()
                    .put("key", component.key)
                    .put("label", component.label)
                    .put("actual", component.actual)
                    .put("target", component.target)
                    .put("index", component.index)
            )
        }
        return JSONObject()
            .put("state", snapshot.state.name)
            .put("label", snapshot.stateLabel)
            .put("job", snapshot.jobName)
            .put("progress", snapshot.progress)
            .put("printTime", snapshot.printTimeSeconds)
            .put("printTimeLeft", snapshot.printTimeLeftSeconds)
            .put("etaOrigin", snapshot.etaOrigin)
            .put("version", snapshot.octoPrintVersion)
            .put("temperatures", temperatures)
            .toString()
    }

    private fun decode(raw: String): PrinterSnapshot {
        val json = JSONObject(raw)
        val temperatures = mutableListOf<ComponentTemperature>()
        val array = json.optJSONArray("temperatures") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            temperatures += ComponentTemperature(
                key = item.optString("key"),
                label = item.optString("label"),
                actual = item.optDouble("actual", 0.0).toFloat(),
                target = item.optDouble("target", 0.0).toFloat(),
                index = item.optInt("index", 0)
            )
        }
        return PrinterSnapshot(
            state = runCatching { MachineState.valueOf(json.optString("state")) }.getOrDefault(MachineState.Unknown),            stateLabel = json.optString("label", "Unknown"),
            temperatures = temperatures,
            jobName = json.optString("job").takeIf { it.isNotBlank() && it != "null" },
            progress = if (json.isNull("progress")) null else json.optDouble("progress").toFloat(),
            printTimeSeconds = if (json.isNull("printTime")) null else json.optLong("printTime"),
            printTimeLeftSeconds = if (json.isNull("printTimeLeft")) null else json.optLong("printTimeLeft"),
            etaOrigin = json.optString("etaOrigin").takeIf { it.isNotBlank() },
            octoPrintVersion = json.optString("version").takeIf { it.isNotBlank() }
        )
    }
}
