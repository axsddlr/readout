package dev.readout.app.data

import java.io.ByteArrayInputStream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The upload path is the one place where the app writes a request body by hand, so it is tested
 * against a real HTTP server rather than a mock of our own client.
 */
class OctoPrintApiUploadTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `upload sends a multipart body with filename, api key and progress`() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"done":true}"""))
        val api = OctoPrintApi(server.url("/").toString(), "secret-key")
        val payload = "G28\nG1 X10\n".toByteArray()
        var progressAtEnd = 0f

        api.uploadFile(
            fileName = "test part.gcode",
            mimeType = "application/octet-stream",
            sizeBytes = payload.size.toLong(),
            openStream = { ByteArrayInputStream(payload) },
            onProgress = { progressAtEnd = it }
        )

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/api/files/local", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
        assertEquals("secret-key", request.getHeader("X-Api-Key"))
        assertTrue(body.contains("filename=\"test part.gcode\""))
        assertTrue(body.contains("G28"))
        assertTrue(body.contains("name=\"select\""))
        assertTrue(body.trimEnd().endsWith("--"))
        assertEquals(1f, progressAtEnd, 0.0001f)
    }

    @Test
    fun `upload surfaces a rejected api key as a readable message`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))
        val api = OctoPrintApi(server.url("/").toString(), "bad")

        val failure = runCatching {
            api.uploadFile(
                "x.gcode",
                "application/octet-stream",
                1L,
                { ByteArrayInputStream(byteArrayOf(0)) }
            ) {}
        }.exceptionOrNull()

        assertTrue(failure is OctoPrintException)
        assertTrue(failure?.message.orEmpty().contains("API key rejected"))
    }
}
