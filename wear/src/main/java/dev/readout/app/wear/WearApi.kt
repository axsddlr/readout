package dev.readout.app.wear

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** A deliberately small OctoPrint client for the watch: one read, two commands. */
class WearApi(baseUrl: String, private val apiKey: String) {

    private val root = baseUrl.trim().trimEnd('/').let {
        if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "http://$it"
    }

    fun snapshot(): WearSnapshot {
        val printer = get("/api/printer")
        val job = runCatching { get("/api/job") }.getOrNull()
        return WearParser.parse(printer, job)
    }

    fun pause() = post("/api/job", """{"command":"pause"}""")

    fun resume() = post("/api/job", """{"command":"resume"}""")

    private fun get(path: String): String = call("GET", path, null)

    private fun post(path: String, body: String) {
        call("POST", path, body)
    }

    private fun call(method: String, path: String, body: String?): String {
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 5_000
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
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream: InputStream? = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (code == 401 || code == 403) throw WearException("API key rejected")
            if (code !in 200..299) throw WearException("Printer answered $code")
            return text
        } catch (e: WearException) {
            throw e
        } catch (e: Exception) {
            throw WearException(e.message ?: "Could not reach the printer")
        } finally {
            connection.disconnect()
        }
    }
}

class WearException(message: String) : Exception(message)
