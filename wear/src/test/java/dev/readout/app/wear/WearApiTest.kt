package dev.readout.app.wear

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WearApiTest {

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
    fun `snapshot reads printer and job with the api key`() {
        server.enqueue(
            MockResponse().setBody(
                """{"temperature":{"tool0":{"actual":209.6,"target":210},"bed":{"actual":59.4,"target":60}},
                   "state":{"text":"Printing","flags":{"printing":true}}}"""
            )
        )
        server.enqueue(MockResponse().setBody("""{"progress":{"completion":41.7,"printTimeLeft":2410}}"""))

        val snapshot = WearApi(server.url("/").toString(), "secret").snapshot()

        assertEquals("PRINTING", snapshot.headline)
        assertEquals(0.417f, snapshot.progress!!, 0.001f)

        val printer = server.takeRequest()
        assertEquals("/api/printer", printer.path)
        assertEquals("secret", printer.getHeader("X-Api-Key"))
        assertEquals("/api/job", server.takeRequest().path)
    }

    @Test
    fun `pause posts the documented command`() {
        server.enqueue(MockResponse().setBody("{}"))

        WearApi(server.url("/").toString(), "secret").pause()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/job", request.path)
        assertEquals("""{"command":"pause"}""", request.body.readUtf8())
    }

    @Test
    fun `a rejected key fails loudly`() {
        server.enqueue(MockResponse().setResponseCode(403))
        val failure = runCatching { WearApi(server.url("/").toString(), "bad").snapshot() }.exceptionOrNull()
        assertTrue(failure is WearException)
        assertTrue(failure?.message.orEmpty().contains("API key rejected"))
    }
}
