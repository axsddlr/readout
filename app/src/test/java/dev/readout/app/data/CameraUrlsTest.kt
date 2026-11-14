package dev.readout.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraUrlsTest {

    private fun config(webcam: String? = null) =
        PrinterConfig(baseUrl = "http://192.168.1.176/", apiKey = "key", webcamUrl = webcam)

    @Test
    fun `a blank setting uses the printer's own webcam for both`() {
        val urls = cameraUrls(config())

        assertEquals("http://192.168.1.176/webcam/?action=snapshot", urls.still)
        assertEquals("http://192.168.1.176/webcam/?action=stream", urls.stream)
    }

    @Test
    fun `a configured stream URL is streamed and reversed for stills`() {
        val urls = cameraUrls(config("http://192.168.1.176/webcam/?action=stream"))

        assertEquals("http://192.168.1.176/webcam/?action=stream", urls.stream)
        assertEquals("http://192.168.1.176/webcam/?action=snapshot", urls.still)
    }

    @Test
    fun `a configured snapshot URL is stilled and reversed for streaming`() {
        val urls = cameraUrls(config("http://octopi.local/webcam/?action=snapshot"))

        assertEquals("http://octopi.local/webcam/?action=snapshot", urls.still)
        assertEquals("http://octopi.local/webcam/?action=stream", urls.stream)
    }

    @Test
    fun `an unknown endpoint is tried as both rather than replaced`() {
        val urls = cameraUrls(config("http://192.168.1.176:8080/cam"))

        assertEquals("http://192.168.1.176:8080/cam", urls.still)
        assertEquals("http://192.168.1.176:8080/cam", urls.stream)
    }

    @Test
    fun `the experimental stack base gets the classic action paths`() {
        val urls = cameraUrls(config("http://192.168.1.193/webcam/"))

        assertEquals("http://192.168.1.193/webcam/?action=snapshot", urls.still)
        assertEquals("http://192.168.1.193/webcam/?action=stream", urls.stream)
    }

    @Test
    fun `a base without the trailing slash still resolves`() {
        val urls = cameraUrls(config("http://192.168.1.193/webcam"))

        assertEquals("http://192.168.1.193/webcam/?action=snapshot", urls.still)
        assertEquals("http://192.168.1.193/webcam/?action=stream", urls.stream)
    }

    @Test
    fun `camera-streamer stream url implies its snapshot sibling`() {
        val urls = cameraUrls(config("http://192.168.1.193/webcam/stream"))

        assertEquals("http://192.168.1.193/webcam/stream", urls.stream)
        assertEquals("http://192.168.1.193/webcam/snapshot", urls.still)
    }

    @Test
    fun `camera-streamer snapshot url implies its stream sibling`() {
        val urls = cameraUrls(config("http://192.168.1.193/webcam/snapshot"))

        assertEquals("http://192.168.1.193/webcam/snapshot", urls.still)
        assertEquals("http://192.168.1.193/webcam/stream", urls.stream)
    }

    @Test
    fun `a blank string counts as unset`() {
        val urls = cameraUrls(config("   "))

        assertEquals("http://192.168.1.176/webcam/?action=snapshot", urls.still)
        assertEquals("http://192.168.1.176/webcam/?action=stream", urls.stream)
    }
}
