package dev.readout.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrPayloadTest {

    @Test
    fun `bare key from OctoPrint's QR code is accepted`() {
        val result = QrPayload.parse("A1B2C3D4E5F60718293A4B5C6D7E8F90")
        assertEquals(QrPayload("A1B2C3D4E5F60718293A4B5C6D7E8F90", null), result)
    }

    @Test
    fun `surrounding whitespace and newlines are trimmed`() {
        assertEquals("abcDEF0123456789_-xyz", QrPayload.parse("  abcDEF0123456789_-xyz\n")?.apiKey)
    }

    @Test
    fun `url with apikey query parameter yields address and key`() {
        val result = QrPayload.parse("http://octopi.local:5000/?apikey=0123456789ABCDEF0123")
        assertEquals(QrPayload("0123456789ABCDEF0123", "http://octopi.local:5000"), result)
    }

    @Test
    fun `url keeps a subpath and accepts api_key and key spellings`() {
        assertEquals(
            QrPayload("0123456789ABCDEF0123", "https://example.lan/octoprint"),
            QrPayload.parse("https://example.lan/octoprint/?api_key=0123456789ABCDEF0123")
        )
        assertEquals(
            QrPayload("0123456789ABCDEF0123", "http://192.168.1.20"),
            QrPayload.parse("http://192.168.1.20?key=0123456789ABCDEF0123&x=1")
        )
    }

    @Test
    fun `json with url and key is accepted in common spellings`() {
        assertEquals(
            QrPayload("0123456789ABCDEF0123", "http://octopi.local"),
            QrPayload.parse("""{"url":"http://octopi.local/","apikey":"0123456789ABCDEF0123"}""")
        )
        assertEquals(
            QrPayload("0123456789ABCDEF0123", null),
            QrPayload.parse("""{"apiKey":"0123456789ABCDEF0123"}""")
        )
    }

    @Test
    fun `url without a key is rejected`() {
        assertNull(QrPayload.parse("http://octopi.local:5000/"))
    }

    @Test
    fun `short or non key text is rejected`() {
        assertNull(QrPayload.parse(""))
        assertNull(QrPayload.parse("hello"))
        assertNull(QrPayload.parse("WIFI:S:home;T:WPA;P:secret;;"))
        assertNull(QrPayload.parse("this has spaces in it and is long"))
    }
}
