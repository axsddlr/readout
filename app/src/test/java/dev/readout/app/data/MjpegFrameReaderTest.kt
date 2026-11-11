package dev.readout.app.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MjpegFrameReaderTest {

    /** A fake but structurally valid JPEG: SOI, some payload, EOI. */
    private fun jpeg(tag: String): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + tag.toByteArray() + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private fun multipart(vararg frames: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        frames.forEach { frame ->
            val header = ("--boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n")
            out.write(header.toByteArray())
            out.write(frame)
            out.write("\r\n".toByteArray())
        }
        return out.toByteArray()
    }

    @Test
    fun `frames are extracted from a multipart body`() {
        val first = jpeg("first")
        val second = jpeg("second")
        val collected = mutableListOf<ByteArray>()

        val count = MjpegFrameReader { collected += it }
            .read(ByteArrayInputStream(multipart(first, second)))

        assertEquals(2, count)
        assertArrayEquals(first, collected[0])
        assertArrayEquals(second, collected[1])
    }

    @Test
    fun `a boundary split across reads still yields whole frames`() {
        val frame = jpeg("split")
        val body = multipart(frame)
        // A one-byte-at-a-time stream is the worst case for marker detection.
        val trickle = object : java.io.InputStream() {
            var index = 0
            override fun read(): Int = if (index < body.size) body[index++].toInt() and 0xFF else -1
        }
        val collected = mutableListOf<ByteArray>()

        assertEquals(1, MjpegFrameReader { collected += it }.read(trickle))
        assertArrayEquals(frame, collected[0])
    }

    @Test
    fun `a truncated stream ends without emitting a partial frame`() {
        val partial = jpeg("unfinished").copyOf(6)
        val collected = mutableListOf<ByteArray>()

        val count = MjpegFrameReader { collected += it }.read(ByteArrayInputStream(partial))

        assertEquals(0, count)
        assertEquals(0, collected.size)
    }

    @Test
    fun `a body that is not MJPEG yields nothing`() {
        val html = "<html><body>not a camera</body></html>".toByteArray()
        assertEquals(0, MjpegFrameReader { error("must not emit") }.read(ByteArrayInputStream(html)))
    }

    @Test
    fun `an empty frame is skipped`() {
        val empty = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val real = jpeg("real")
        val collected = mutableListOf<ByteArray>()

        val count = MjpegFrameReader { collected += it }.read(ByteArrayInputStream(empty + real))

        assertEquals(1, count)
        assertArrayEquals(real, collected.single())
    }

    @Test
    fun `a frame over the budget is dropped instead of growing memory`() {
        val huge = ByteArray(64) { 0x41 } // no end marker
        val body = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + huge
        val collected = mutableListOf<ByteArray>()

        val count = MjpegFrameReader(maxFrameBytes = 16) { collected += it }
            .read(ByteArrayInputStream(body))

        assertEquals(0, count)
        assertEquals(0, collected.size)
    }
}
