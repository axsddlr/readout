package dev.readout.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSettingsTest {

    @Test
    fun `defaults match what the preview shows today`() {
        val settings = PreviewSettings()

        assertTrue(settings.previousLayer)
        assertTrue(settings.currentLayer)
        assertEquals(PreviewQuality.Medium, settings.quality)
    }

    @Test
    fun `quality samples the ghost more coarsely as it drops`() {
        assertEquals(4, PreviewSettings(quality = PreviewQuality.Low).ghostStep)
        assertEquals(2, PreviewSettings(quality = PreviewQuality.Medium).ghostStep)
        assertEquals(1, PreviewSettings(quality = PreviewQuality.High).ghostStep)
    }

    @Test
    fun `quality thickens the stroke as it rises`() {
        assertTrue(
            PreviewSettings(quality = PreviewQuality.High).strokeWidth >
                PreviewSettings(quality = PreviewQuality.Medium).strokeWidth
        )
        assertTrue(
            PreviewSettings(quality = PreviewQuality.Medium).strokeWidth >
                PreviewSettings(quality = PreviewQuality.Low).strokeWidth
        )
    }

    @Test
    fun `every quality explains itself`() {
        PreviewQuality.entries.forEach { quality ->
            assertTrue(PreviewSettings(quality = quality).qualityDescription.isNotBlank())
        }
    }
}
