package dev.readout.app.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OctoPrintApiAppKeyTest {

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
    fun `probe reports whether the app keys plugin is installed`() {
        val api = OctoPrintApi(server.url("/").toString(), "")

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(api.appKeysAvailable())

        server.enqueue(MockResponse().setResponseCode(404))
        assertFalse(api.appKeysAvailable())
    }

    @Test
    fun `request yields a token, pending polls return null, a granted poll returns the key`() {
        val api = OctoPrintApi(server.url("/").toString(), "")

        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"user_token":"tok"}"""))
        val token = api.requestAppKey("Readout")
        assertEquals("tok", token)

        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"pending"}"""))
        assertNull(api.pollAppKey(token))

        server.enqueue(MockResponse().setBody("""{"api_key":"abc123"}"""))
        assertEquals("abc123", api.pollAppKey(token))

        val request = server.takeRequest()
        assertEquals("/plugin/appkeys/request", request.path)
        assertEquals("POST", request.method)
        assertEquals("""{"app":"Readout"}""", request.body.readUtf8())

        val poll = server.takeRequest()
        assertEquals("/plugin/appkeys/request/tok", poll.path)
    }

    @Test
    fun `an invalid token fails loudly instead of polling forever`() {
        val api = OctoPrintApi(server.url("/").toString(), "")
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"unknown"}"""))

        val failure = runCatching { api.pollAppKey("stale") }.exceptionOrNull()
        assertTrue(failure is OctoPrintException)
        assertTrue(failure?.message.orEmpty().contains("no longer valid"))
    }
}
