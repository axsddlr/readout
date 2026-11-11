package dev.readout.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, offline storage for printer instances and app preferences.
 *
 * Instances are a list with one active id; the single-printer keys from earlier builds are
 * migrated once so nobody loses their printer after an update.
 */
class PrinterStore(context: Context) {

    private val prefs = context.getSharedPreferences("readout.printer", Context.MODE_PRIVATE)

    fun instances(): List<PrinterConfig> {
        migrateIfNeeded()
        val raw = prefs.getString(KEY_INSTANCES, null) ?: return emptyList()
        return runCatching { decode(raw) }.getOrDefault(emptyList())
    }

    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun active(): PrinterConfig? {
        val id = activeId() ?: return null
        return instances().firstOrNull { it.id == id }
    }

    fun saveInstance(config: PrinterConfig) {
        val current = instances().toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) current[index] = config else current.add(config)
        writeInstances(current)
    }

    fun removeInstance(id: String) {
        val remaining = instances().filterNot { it.id == id }
        writeInstances(remaining)
        if (activeId() == id) {
            prefs.edit().putString(KEY_ACTIVE, remaining.firstOrNull()?.id).apply()
        }
    }

    fun setActive(id: String?) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun setWebcam(url: String?) {
        val active = active() ?: return
        saveInstance(active.copy(webcamUrl = url))
    }

    fun themeMode(): ThemePreference = when (prefs.getString(KEY_THEME, null)) {
        "light" -> ThemePreference.Light
        "dark" -> ThemePreference.Dark
        else -> ThemePreference.System
    }

    fun setThemeMode(mode: ThemePreference) {
        prefs.edit().putString(
            KEY_THEME,
            when (mode) {
                ThemePreference.System -> "system"
                ThemePreference.Light -> "light"
                ThemePreference.Dark -> "dark"
            }
        ).apply()
    }

    fun dynamicColor(): Boolean = prefs.getBoolean(KEY_DYNAMIC, true)

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun notificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS, false)

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    /**
     * Off by default: jogging an axis mid-print can shift a layer or drive the head into the part.
     * The opt-in exists for the cases where nudging is the lesser evil.
     */
    fun moveDuringPrint(): Boolean = prefs.getBoolean(KEY_MOVE_DURING_PRINT, false)

    fun setMoveDuringPrint(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOVE_DURING_PRINT, enabled).apply()
    }

    /** The preview's own settings, shared across printers: they describe how to draw, not what to draw. */
    fun previewSettings(): PreviewSettings = PreviewSettings(
        previousLayer = prefs.getBoolean(KEY_PREVIEW_PREVIOUS, true),
        currentLayer = prefs.getBoolean(KEY_PREVIEW_CURRENT, true),
        quality = PreviewQuality.entries
            .firstOrNull { it.name == prefs.getString(KEY_PREVIEW_QUALITY, null) }
            ?: PreviewQuality.Medium
    )

    fun setPreviewSettings(settings: PreviewSettings) {
        prefs.edit()
            .putBoolean(KEY_PREVIEW_PREVIOUS, settings.previousLayer)
            .putBoolean(KEY_PREVIEW_CURRENT, settings.currentLayer)
            .putString(KEY_PREVIEW_QUALITY, settings.quality.name)
            .apply()
    }

    /** Camera card behaviour; one camera per printer, so these are app-wide like the preview's. */
    fun cameraSettings(): CameraSettings = CameraSettings(
        aspect = CameraAspect.entries
            .firstOrNull { it.name == prefs.getString(KEY_CAMERA_ASPECT, null) }
            ?: CameraAspect.FromImage,
        showResolution = prefs.getBoolean(KEY_CAMERA_SHOW_RESOLUTION, false),
        showName = prefs.getBoolean(KEY_CAMERA_SHOW_NAME, false),
        dataSaver = prefs.getBoolean(KEY_CAMERA_DATA_SAVER, false)
    )

    fun setCameraSettings(settings: CameraSettings) {
        prefs.edit()
            .putString(KEY_CAMERA_ASPECT, settings.aspect.name)
            .putBoolean(KEY_CAMERA_SHOW_RESOLUTION, settings.showResolution)
            .putBoolean(KEY_CAMERA_SHOW_NAME, settings.showName)
            .putBoolean(KEY_CAMERA_DATA_SAVER, settings.dataSaver)
            .apply()
    }

    // -- persistence -----------------------------------------------------------------------

    private fun migrateIfNeeded() {
        if (prefs.contains(KEY_INSTANCES)) return
        val url = prefs.getString(LEGACY_URL, null) ?: return
        val key = prefs.getString(LEGACY_KEY, null) ?: return
        if (url.isBlank() || key.isBlank()) return
        val migrated = PrinterConfig(
            id = "primary",
            name = prefs.getString(LEGACY_NAME, null) ?: "Printer",
            baseUrl = url,
            apiKey = key,
            webcamUrl = prefs.getString(LEGACY_WEBCAM, null)
        )
        writeInstances(listOf(migrated))
        prefs.edit()
            .putString(KEY_ACTIVE, migrated.id)
            .remove(LEGACY_URL)
            .remove(LEGACY_KEY)
            .remove(LEGACY_NAME)
            .remove(LEGACY_WEBCAM)
            .apply()
    }

    private fun writeInstances(instances: List<PrinterConfig>) {
        val array = JSONArray()
        instances.forEach { instance ->
            array.put(
                JSONObject()
                    .put("id", instance.id)
                    .put("name", instance.name)
                    .put("baseUrl", instance.baseUrl)
                    .put("apiKey", instance.apiKey)
                    .put("webcamUrl", instance.webcamUrl)
            )
        }
        prefs.edit().putString(KEY_INSTANCES, array.toString()).apply()
    }

    private fun decode(raw: String): List<PrinterConfig> {
        val array = JSONArray(raw)
        val result = mutableListOf<PrinterConfig>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").ifBlank { "printer-$index" }
            val url = item.optString("baseUrl")
            val key = item.optString("apiKey")
            if (url.isBlank() || key.isBlank()) continue
            result += PrinterConfig(
                id = id,
                baseUrl = url,
                apiKey = key,
                name = item.optString("name").ifBlank { "Printer" },
                webcamUrl = item.optString("webcamUrl").takeIf { it.isNotBlank() && it != "null" }
            )
        }
        return result
    }

    private companion object {
        const val KEY_INSTANCES = "instances"
        const val KEY_ACTIVE = "active_id"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_NOTIFICATIONS = "live_notifications"
        const val KEY_MOVE_DURING_PRINT = "move_during_print"
        const val KEY_PREVIEW_PREVIOUS = "preview_previous_layer"
        const val KEY_PREVIEW_CURRENT = "preview_current_layer"
        const val KEY_PREVIEW_QUALITY = "preview_quality"
        const val KEY_CAMERA_ASPECT = "camera_aspect"
        const val KEY_CAMERA_SHOW_RESOLUTION = "camera_show_resolution"
        const val KEY_CAMERA_SHOW_NAME = "camera_show_name"
        const val KEY_CAMERA_DATA_SAVER = "camera_data_saver"

        const val LEGACY_URL = "base_url"
        const val LEGACY_KEY = "api_key"
        const val LEGACY_NAME = "name"
        const val LEGACY_WEBCAM = "webcam_url"
    }
}

enum class ThemePreference { System, Light, Dark }
