package dev.readout.app.wear

import android.content.Context

/** Config lives on the watch so the watch works without the phone nearby. */
class WearStore(context: Context) {

    private val prefs = context.getSharedPreferences("readout.wear", Context.MODE_PRIVATE)

    fun load(): Pair<String, String>? {
        val url = prefs.getString("base_url", null) ?: return null
        val key = prefs.getString("api_key", null) ?: return null
        if (url.isBlank() || key.isBlank()) return null
        return url to key
    }

    fun save(url: String, key: String) {
        prefs.edit().putString("base_url", url).putString("api_key", key).apply()
    }

    fun clear() {
        prefs.edit().remove("base_url").remove("api_key").apply()
    }
}
