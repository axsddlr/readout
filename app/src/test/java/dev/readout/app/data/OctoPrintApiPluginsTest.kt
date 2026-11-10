package dev.readout.app.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OctoPrintApiPluginsTest {

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
    fun `octorelay reads an index-keyed object and a wrapped relays object`() {
        val api = OctoPrintApi(server.url("/").toString(), "key")

        server.enqueue(
            MockResponse().setBody(
                """{"0":{"id":"0","name":"Light","active":false},"1":{"id":"1","name":"Fan","active":true}}"""
            )
        )
        val plain = api.octoRelays().orEmpty()
        assertEquals(2, plain.size)
        assertEquals("Light", plain[0].name)
        assertFalse(plain[0].on)
        assertTrue(plain[1].on)

        server.enqueue(
            MockResponse().setBody("""{"relays":{"2":{"name":"Camera light","active":true}}}""")
        )
        val wrapped = api.octoRelays().orEmpty()
        assertEquals(1, wrapped.size)
        assertEquals("2", wrapped[0].id)
        assertEquals("Camera light", wrapped[0].name)
        assertTrue(wrapped[0].on)
    }

    @Test
    fun `an absent octorelay plugin yields null`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val api = OctoPrintApi(server.url("/").toString(), "key")
        assertNull(api.octoRelays())
    }

    @Test
    fun `display layer progress reads the layer pair and tolerates the plugin being absent`() {
        val api = OctoPrintApi(server.url("/").toString(), "key")

        server.enqueue(MockResponse().setBody("""{"layer":{"current":41,"total":105}}"""))
        assertEquals(41 to 105, api.displayLayerProgress())
        assertEquals("/api/plugin/DisplayLayerProgress/values", server.takeRequest().path)

        // The plugin reports strings in some versions.
        server.enqueue(MockResponse().setBody("""{"layer":{"current":"7","total":"9"}}"""))
        assertEquals(7 to 9, api.displayLayerProgress())

        // Nothing counted yet, and the plugin's own error shape.
        server.enqueue(MockResponse().setBody("""{"layer":{"current":0,"total":0}}"""))
        assertNull(api.displayLayerProgress())
        server.enqueue(MockResponse().setBody("""{"error":"printer is not printing"}"""))
        assertNull(api.displayLayerProgress())

        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(api.displayLayerProgress())
    }

    @Test
    fun `relay commands post the documented update payload`() {
        server.enqueue(MockResponse().setBody("{}"))
        val api = OctoPrintApi(server.url("/").toString(), "key")

        api.setRelay("1", true)

        val request = server.takeRequest()
        assertEquals("/api/plugin/octorelay", request.path)
        assertEquals("POST", request.method)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("update", body.getString("command"))
        assertEquals("1", body.getString("id"))
        assertTrue(body.getBoolean("active"))
    }

    @Test
    fun `an absent psu plugin is an answer, not an error`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not found"}"""))
        val api = OctoPrintApi(server.url("/").toString(), "key")

        assertNull(api.psuState())
    }

    @Test
    fun `psu state is read and commands are sent as json`() {
        server.enqueue(MockResponse().setBody("""{"isPSUOn":true}"""))
        val api = OctoPrintApi(server.url("/").toString(), "key")

        assertEquals(true, api.psuState())

        server.enqueue(MockResponse().setBody("""{"isPSUOn":false}"""))
        api.setPsu(false)

        val stateRequest = server.takeRequest()
        val commandRequest = server.takeRequest()
        assertEquals("/api/plugin/psucontrol", stateRequest.path)
        assertEquals("GET", stateRequest.method)
        assertEquals("/api/plugin/psucontrol", commandRequest.path)
        assertEquals("POST", commandRequest.method)
        assertEquals("""{"command":"turnPSUOff"}""", commandRequest.body.readUtf8())
    }

    @Test
    fun `wled reads lights_on and toggles by command`() {
        val api = OctoPrintApi(server.url("/").toString(), "key")

        server.enqueue(MockResponse().setBody("""{"lights_on":true,"status":"started"}"""))
        assertEquals(true, api.wledState())

        server.enqueue(MockResponse().setBody("""{"lights_on":false}"""))
        api.setWled(false)

        val state = server.takeRequest()
        val command = server.takeRequest()
        assertEquals("/api/plugin/wled", state.path)
        assertEquals("/api/plugin/wled", command.path)
        assertEquals("""{"command":"lights_off"}""", command.body.readUtf8())
    }

    @Test
    fun `an absent wled plugin yields null`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val api = OctoPrintApi(server.url("/").toString(), "key")
        assertNull(api.wledState())
    }
}
