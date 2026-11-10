package dev.readout.app.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * A deliberately small OctoPrint REST client.
 *
 * No third-party HTTP stack: the surface this app needs is six endpoints and a header.
 * Every call throws [OctoPrintException] with a message that is safe to show a user.
 */
class OctoPrintApi(
    baseUrl: String,
    private val apiKey: String
) {
    private val root: String = normalize(baseUrl)

    private fun normalize(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    fun printer(): JSONObject = request("GET", "/api/printer").asObject()

    fun job(): JSONObject = request("GET", "/api/job").asObject()

    fun version(): JSONObject = request("GET", "/api/version").asObject()

    fun connection(): JSONObject = request("GET", "/api/connection").asObject()

    fun files(): JSONArray = request("GET", "/api/files?recursive=true").asObject().optJSONArray("files") ?: JSONArray()

    fun probe(): String {
        val version = version()
        val server = version.optString("server", "").ifBlank { "OctoPrint" }
        return "OctoPrint $server"
    }

    fun pause() = command("/api/job", JSONObject().put("command", "pause"))

    fun resume() = command("/api/job", JSONObject().put("command", "resume"))

    fun cancel() = command("/api/job", JSONObject().put("command", "cancel"))

    fun connectPrinter() = command("/api/connection", JSONObject().put("command", "connect"))

    fun disconnectPrinter() = command("/api/connection", JSONObject().put("command", "disconnect"))

    fun setToolTarget(toolIndex: Int, celsius: Int) = command(
        "/api/printer/tool",
        JSONObject()
            .put("command", "target")
            .put("targets", JSONObject().put("tool$toolIndex", celsius))
    )

    fun setBedTarget(celsius: Int) = command(
        "/api/printer/bed",
        JSONObject().put("command", "target").put("target", celsius)
    )

    fun setChamberTarget(celsius: Int) = command(
        "/api/printer/chamber",
        JSONObject().put("command", "target").put("target", celsius)
    )

    fun jog(axis: String, millimetres: Float) = command(
        "/api/printer/printhead",
        JSONObject().put("command", "jog").put(axis, millimetres.toDouble())
    )

    fun home(axes: List<String> = listOf("x", "y", "z")) = command(
        "/api/printer/printhead",
        JSONObject().put("command", "home").put("axes", JSONArray(axes))
    )

    fun extrude(millimetres: Float) = command(
        "/api/printer/tool",
        JSONObject().put("command", "extrude").put("amount", millimetres.toDouble())
    )

    fun sendGcode(lines: List<String>) = command(
        "/api/printer/command",
        JSONObject().put("commands", JSONArray(lines))
    )

    fun startPrint(origin: String, path: String) = command(
        "/api/files/${encode(origin)}/${encodePath(path)}",
        JSONObject().put("command", "select").put("print", true)
    )

    fun deleteFile(origin: String, path: String) {
        request("DELETE", "/api/files/${encode(origin)}/${encodePath(path)}", null)
    }

    fun timelapses(): List<TimelapseEntry> {
        val files = request("GET", "/api/timelapse").asObject().optJSONArray("files") ?: return emptyList()
        val result = mutableListOf<TimelapseEntry>()
        for (index in 0 until files.length()) {
            val item = files.optJSONObject(index) ?: continue
            val name = item.optString("name")
            if (name.isBlank()) continue
            result += TimelapseEntry(
                name = name,
                sizeBytes = item.optLong("size", 0L),
                dateSeconds = if (item.has("date")) item.optLong("date") else null
            )
        }
        return result.sortedByDescending { it.dateSeconds ?: 0L }
    }

    fun deleteTimelapse(name: String) {
        request("DELETE", "/api/timelapse/${encode(name)}", null)
    }

    /**
     * PSU Control state. Returns null when the plugin is not installed — a 404 from a plugin
     * endpoint is an answer, not a failure.
     */
    fun psuState(): Boolean? {
        val payload = requestOptional("/api/plugin/psucontrol") ?: return null
        return payload.asObject().optBoolean("isPSUOn", false)
    }

    fun setPsu(on: Boolean) {
        command("/api/plugin/psucontrol", JSONObject().put("command", if (on) "turnPSUOn" else "turnPSUOff"))
    }

    /**
     * OctoRelay's switch list, or null when the plugin is absent. The plugin returns an object
     * keyed by relay index; a wrapped `relays` object and a plain array are also accepted so a
     * version change degrades to "fewer relays" rather than a crash.
     */
    fun octoRelays(): List<RelayState>? {
        val payload = requestOptional("/api/plugin/octorelay") ?: return null
        val root = payload.asObject()
        val container: Any = root.optJSONObject("relays") ?: root.optJSONArray("relays") ?: root
        val result = mutableListOf<RelayState>()

        when (container) {
            is JSONObject -> {
                val keys = container.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = container.optJSONObject(key) ?: continue
                    result += RelayState(
                        id = item.optString("id").ifBlank { key },
                        name = item.optString("name").ifBlank { "Relay $key" },
                        on = item.optBoolean("active", false)
                    )
                }
            }

            is JSONArray -> {
                for (index in 0 until container.length()) {
                    val item = container.optJSONObject(index) ?: continue
                    result += RelayState(
                        id = item.optString("id").ifBlank { index.toString() },
                        name = item.optString("name").ifBlank { "Relay $index" },
                        on = item.optBoolean("active", false)
                    )
                }
            }
        }
        return result.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
    }

    fun setRelay(id: String, on: Boolean) {
        command(
            "/api/plugin/octorelay",
            JSONObject().put("command", "update").put("id", id).put("active", on)
        )
    }

    /**
     * The WLED plugin exposes its light state synchronously and toggles by command; the richer
     * device status only arrives over its websocket, so this reads just what is authoritative.
     */
    fun wledState(): Boolean? {
        val payload = requestOptional("/api/plugin/wled") ?: return null
        return payload.asObject().optBoolean("lights_on", false)
    }

    fun setWled(on: Boolean) {
        command("/api/plugin/wled", JSONObject().put("command", if (on) "lights_on" else "lights_off"))
    }

    // -- application keys (2FA) ---------------------------------------------------------

    /** True when the App Keys plugin is installed; the probe answers 204. */
    fun appKeysAvailable(): Boolean = runCatching {
        requestRaw("GET", "/plugin/appkeys/probe").first in 200..299
    }.getOrDefault(false)

    /** Starts a request and returns the token the decision is polled with. */
    fun requestAppKey(appName: String): String {
        val (code, text) = requestRaw("POST", "/plugin/appkeys/request", JSONObject().put("app", appName))
        if (code !in 200..299) throw OctoPrintException(describe(code, text))
        return runCatching { JSONObject(text).optString("user_token") }.getOrElse { text.trim() }
    }

    /** The granted key, or null while an operator has not decided yet (403). */
    fun pollAppKey(userToken: String): String? {
        val (code, text) = requestRaw("POST", "/plugin/appkeys/request/${encode(userToken)}")
        return when (code) {
            200 -> runCatching { JSONObject(text).optString("api_key") }.getOrElse { text.trim() }
            403 -> null
            404 -> throw OctoPrintException("That request is no longer valid. Start again.")
            else -> throw OctoPrintException(describe(code, text))
        }
    }

    /**
     * Multipart upload to `/api/files/local`. Chunked, so a large G-code file never has to be
     * buffered in memory; progress is reported as bytes leave the socket.
     */
    fun uploadFile(
        fileName: String,
        mimeType: String,
        sizeBytes: Long?,
        openStream: () -> InputStream,
        onProgress: (Float) -> Unit
    ) {
        if (root.isEmpty()) throw OctoPrintException("No printer address configured")
        val boundary = "----Readout${System.nanoTime()}"
        val connection = (URL("$root/api/files/local").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 60_000
            requestMethod = "POST"
            doOutput = true
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("X-Api-Key", apiKey)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val output = connection.outputStream.buffered()
            fun writeText(text: String) = output.write(text.toByteArray(Charsets.UTF_8))

            writeText("--$boundary\r\n")
            writeText("Content-Disposition: form-data; name=\"file\"; filename=\"${escape(fileName)}\"\r\n")
            writeText("Content-Type: $mimeType\r\n\r\n")

            openStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                var read = input.read(buffer)
                while (read > 0) {
                    output.write(buffer, 0, read)
                    written += read
                    if (sizeBytes != null && sizeBytes > 0) {
                        onProgress((written.toFloat() / sizeBytes).coerceIn(0f, 1f))
                    }
                    read = input.read(buffer)
                }
            }

            writeText("\r\n--$boundary\r\n")
            writeText("Content-Disposition: form-data; name=\"select\"\r\n\r\nfalse\r\n")
            writeText("--$boundary--\r\n")
            output.flush()

            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                if (code == 401 || code == 403) throw OctoPrintException("API key rejected.")
                throw OctoPrintException(describe(code, body))
            }
            onProgress(1f)
        } catch (e: OctoPrintException) {
            throw e
        } catch (e: Exception) {
            throw OctoPrintException(e.message ?: "Upload failed")
        } finally {
            connection.disconnect()
        }
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "")

    /**
     * Opens a stored file for streaming. The caller owns the stream; closing it disconnects.
     * G-code files can be tens of megabytes, so this never buffers the whole file.
     */
    fun openFile(origin: String, path: String): InputStream {
        val connection =
            (URL("$root/downloads/files/${encode(origin)}/${encodePath(path)}").openConnection()
                as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("X-Api-Key", apiKey)
            }
        val code = connection.responseCode
        if (code == 401 || code == 403) {
            connection.disconnect()
            throw OctoPrintException("API key rejected. Check the key in Settings.")
        }
        if (code !in 200..299) {
            connection.disconnect()
            throw OctoPrintException("Could not read the file ($code)")
        }
        return object : java.io.FilterInputStream(connection.inputStream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    fun fetchBytes(absoluteUrl: String): ByteArray {
        val connection = (URL(absoluteUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("X-Api-Key", apiKey)
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw OctoPrintException("Image fetch failed ($code)")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    // -- transport --------------------------------------------------------------------------

    private fun command(path: String, body: JSONObject) {
        request("POST", path, body)
    }

    private fun request(method: String, path: String, body: JSONObject? = null): Payload {
        val (code, text) = requestRaw(method, path, body)
        when {
            code == 401 || code == 403 -> throw OctoPrintException("API key rejected. Check the key in Settings.")
            code == 409 -> throw OctoPrintException("The printer is not connected to OctoPrint.")
            code !in 200..299 -> throw OctoPrintException(describe(code, text))
        }
        return Payload(text)
    }

    /** One transport for everything; callers decide what each status means. */
    private fun requestRaw(method: String, path: String, body: JSONObject? = null): Pair<Int, String> {
        if (root.isEmpty()) throw OctoPrintException("No printer address configured")
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            requestMethod = method
            setRequestProperty("X-Api-Key", apiKey)
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            // A 404 from a plugin probe usually has no body, and HttpURLConnection may return no
            // error stream at all. Reading it as nullable is what makes "plugin absent" work.
            val stream: InputStream? = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            return code to text
        } catch (e: OctoPrintException) {
            throw e
        } catch (e: Exception) {
            throw OctoPrintException(e.message ?: "Could not reach the printer")
        } finally {
            connection.disconnect()
        }
    }

    /** GET that treats 404 as "not there", for optional plugin endpoints. */
    private fun requestOptional(path: String): Payload? {
        val (code, text) = requestRaw("GET", path)
        if (code == 404) return null
        if (code == 401 || code == 403) throw OctoPrintException("API key rejected. Check the key in Settings.")
        if (code !in 200..299) throw OctoPrintException(describe(code, text))
        return Payload(text)
    }

    private fun describe(code: Int, body: String): String {
        // OctoPrint returns {"error": "..."} for most failures; fall back to the status code.
        val detail = runCatching { JSONObject(body).optString("error") }.getOrNull()
        return if (!detail.isNullOrBlank()) detail else "Request failed ($code)"
    }

    private fun encode(segment: String): String = URLEncoder.encode(segment, "UTF-8")

    /** Encodes a slash-separated path while keeping the separators intact. */
    private fun encodePath(path: String): String = path.split('/').joinToString("/") { encode(it) }

    private class Payload(private val text: String) {
        fun asObject(): JSONObject = if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}

class OctoPrintException(message: String) : Exception(message)
