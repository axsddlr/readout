package dev.readout.app.data

import org.json.JSONObject

/**
 * What OctoPrint says about the machine it runs on, and the commands it offers to act on it.
 *
 * The fields come from `/api/system/info`, whose shape is one flat map of dotted keys. Anything a
 * given installation does not report stays null rather than being invented.
 */
data class SystemInfo(
    val octoPrintVersion: String? = null,
    val octoPiVersion: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val pythonVersion: String? = null,
    val safeMode: Boolean = false,
    val ramBytes: Long? = null,
    val cpuCount: Int? = null,
    /** Raspberry Pi throttle flags: the low nibble is "now", the high nibble "since boot". */
    val throttleState: Int? = null
) {
    /**
     * What the throttle bits mean in words. A Pi Zero 2 W running a camera is exactly the machine
     * that browns out, and the printer will not tell anyone, so this is worth surfacing.
     */
    val throttleWarnings: List<String>
        get() {
            val state = throttleState ?: return emptyList()
            val now = state and 0xF
            val before = (state shr 16) and 0xF
            val warnings = mutableListOf<String>()
            if (now and 0x1 != 0 || before and 0x1 != 0) {
                warnings += "Under-voltage detected — the power supply cannot keep up"
            }
            if (now and 0x2 != 0 || before and 0x2 != 0) {
                warnings += "CPU frequency capped, usually because of the power supply"
            }
            if (now and 0x4 != 0) {
                warnings += "CPU throttled right now"
            } else if (before and 0x4 != 0) {
                // Not happening now, but it did: this is the flag that explains a print that ran
                // slower than its own estimate.
                warnings += "CPU has throttled since boot"
            }
            if (now and 0x8 != 0 || before and 0x8 != 0) warnings += "CPU near its temperature limit"
            return warnings
        }

    val ramMegabytes: Int? get() = ramBytes?.let { (it / (1024 * 1024)).toInt() }

    companion object {
        fun from(json: JSONObject): SystemInfo {
            // `/api/system/info` nests everything under `systeminfo`; a few OctoPrint builds answer
            // with the flat map itself, so accept both.
            val map = json.optJSONObject("systeminfo") ?: json
            fun value(key: String): String? = map.optString(key).takeIf { it.isNotBlank() && it != "null" }

            return SystemInfo(
                octoPrintVersion = value("octoprint.version"),
                octoPiVersion = value("env.plugins.pi_support.octopi_version"),
                model = value("env.plugins.pi_support.model"),
                firmware = value("printer.firmware"),
                pythonVersion = value("env.python.version"),
                safeMode = map.optBoolean("octoprint.safe_mode", false),
                ramBytes = map.optLong("env.hardware.ram", 0L).takeIf { it > 0L },
                cpuCount = map.optInt("env.hardware.cores", 0).takeIf { it > 0 },
                throttleState = value("env.plugins.pi_support.throttle_state")?.let(::parseThrottle)
            )
        }

        /** "0x0" or "0" or decimal; anything else is unknown rather than zero. */
        fun parseThrottle(raw: String): Int? = runCatching {
            if (raw.startsWith("0x", ignoreCase = true)) raw.drop(2).toInt(16) else raw.toInt()
        }.getOrNull()
    }
}

/**
 * A command the OctoPrint server offers, with the warning it wants shown before it runs. The text
 * arrives as HTML and is stripped for display, so the confirmation reads like a sentence.
 */
data class SystemCommand(
    val source: String,
    val action: String,
    val name: String,
    val confirm: String?
) {
    companion object {
        fun list(json: JSONObject): List<SystemCommand> {
            val out = mutableListOf<SystemCommand>()
            json.keys().forEach { source ->
                val entries = json.optJSONArray(source) ?: return@forEach
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val action = entry.optString("action")
                    val name = entry.optString("name")
                    if (action.isBlank() || name.isBlank()) continue
                    out += SystemCommand(
                        source = source,
                        action = action,
                        name = name,
                        confirm = entry.optString("confirm").takeIf { it.isNotBlank() }?.let(::stripHtml)
                    )
                }
            }
            return out.sortedWith(compareBy({ it.source != "core" }, { it.name }))
        }

        /** The server's warnings are HTML fragments; keep the words, drop the markup. */
        fun stripHtml(raw: String): String = raw
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
