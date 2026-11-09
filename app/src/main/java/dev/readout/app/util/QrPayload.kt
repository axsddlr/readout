package dev.readout.app.util

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/**
 * What a scanned QR code told us: an API key, and sometimes the printer's address.
 *
 * Accepted forms:
 *  - the bare key, which is what OctoPrint's own "show QR code" button encodes;
 *  - a printer URL with the key as `apikey`, `api_key` or `key` in the query string;
 *  - a small JSON object with `url` and `apikey` / `apiKey` / `api_key` / `key`.
 */
data class QrPayload(val apiKey: String, val baseUrl: String?) {
    companion object {
        private val KeyPattern = Regex("^[A-Za-z0-9_-]{16,128}$")
        private val KeyNames = listOf("apikey", "api_key", "key")

        fun parse(text: String): QrPayload? {
            val raw = text.trim()
            if (raw.isEmpty()) return null
            return when {
                raw.startsWith("{") -> fromJson(raw)
                raw.startsWith("http://", ignoreCase = true) ||
                    raw.startsWith("https://", ignoreCase = true) -> fromUrl(raw)
                else -> key(raw)?.let { QrPayload(it, null) }
            }
        }

        private fun key(candidate: String?): String? =
            candidate?.trim()?.takeIf { KeyPattern.matches(it) }

        private fun fromJson(raw: String): QrPayload? {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val apiKey = (listOf("apiKey") + KeyNames)
                .firstNotNullOfOrNull { name -> key(json.optString(name).takeIf { it.isNotBlank() }) }
                ?: return null
            val url = json.optString("url").takeIf { it.isNotBlank() }?.let(::addressOf)
            return QrPayload(apiKey, url)
        }

        private fun fromUrl(raw: String): QrPayload? {
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            val params = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                decode(parts[0]).lowercase() to decode(parts[1])
            }.toMap()
            val apiKey = KeyNames.firstNotNullOfOrNull { key(params[it]) } ?: return null
            return QrPayload(apiKey, addressOf(raw))
        }

        /** scheme://host[:port][/path] without query, fragment or trailing slash. */
        private fun addressOf(url: String): String? {
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host ?: return null
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val path = uri.rawPath.orEmpty().trimEnd('/')
            return "$scheme://$host$port$path"
        }

        private fun decode(value: String): String =
            runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}
