package dev.readout.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSamplingTest {

    @Test
    fun `frames within the target are decoded as they are`() {
        assertEquals(1, sampleSizeFor(640, 1280))
        assertEquals(1, sampleSizeFor(1280, 1280))
        assertEquals(1, sampleSizeFor(1920, 1920))
    }

    @Test
    fun `the card halves 1080p and keeps 4K at a quarter`() {
        assertEquals(2, sampleSizeFor(1920, 1280))
        assertEquals(4, sampleSizeFor(3840, 1280))
    }

    @Test
    fun `fullscreen keeps 1080p native and halves 4K`() {
        assertEquals(1, sampleSizeFor(1920, 1920))
        assertEquals(2, sampleSizeFor(3840, 1920))
    }

    @Test
    fun `a broken size never asks for a zero sample`() {
        assertEquals(1, sampleSizeFor(0, 1920))
        assertEquals(1, sampleSizeFor(1920, 0))
    }
}
