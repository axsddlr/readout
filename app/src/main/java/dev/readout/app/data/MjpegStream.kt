package dev.readout.app.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Pulls complete JPEG frames out of an MJPEG body (`multipart/x-mixed-replace`).
 *
 * The part headers differ between cameras — mjpg-streamer, ustreamer and webcamd all disagree on
 * the boundary string — but the payload is always back-to-back JPEGs, so this scans for the start
 * and end markers instead of trusting the boundary. JPEG escapes any 0xFF inside compressed data,
 * which makes the end marker unambiguous.
 *
 * Reads until the stream ends or the caller cancels, and hands each frame to [sink] on the calling
 * thread.
 */
class MjpegFrameReader(
    private val maxFrameBytes: Int = 8 * 1024 * 1024,
    private val sink: (ByteArray) -> Unit
) {

    /** Returns how many frames were read, which is zero when the body was not MJPEG at all. */
    fun read(input: InputStream): Int {
        val buffer = ByteArray(16 * 1024)
        val frame = ByteArrayOutputStream(256 * 1024)
        var previous = -1
        var inFrame = false
        var frames = 0

        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            var index = 0
            while (index < read) {
                val current = buffer[index].toInt() and 0xFF
                if (inFrame) {
                    if (previous == MARKER_START && current == END_IMAGE) {
                        frame.write(current)
                        val jpeg = frame.toByteArray()
                        // An empty frame is SOI immediately followed by EOI; not worth showing.
                        if (jpeg.size > 4) {
                            frames++
                            sink(jpeg)
                        }
                        inFrame = false
                        frame.reset()
                    } else {
                        frame.write(current)
                        if (frame.size() > maxFrameBytes) {
                            // Not a JPEG after all; drop what we collected and look again.
                            inFrame = false
                            frame.reset()
                        }
                    }
                } else if (previous == MARKER_START && current == START_IMAGE) {
                    inFrame = true
                    frame.reset()
                    frame.write(MARKER_START)
                    frame.write(START_IMAGE)
                }
                previous = current
                index++
            }
        }
        return frames
    }

    private companion object {
        const val MARKER_START = 0xFF
        const val START_IMAGE = 0xD8
        const val END_IMAGE = 0xD9
    }
}
