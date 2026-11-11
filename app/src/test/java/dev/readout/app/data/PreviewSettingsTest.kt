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

    @Test
    fun `following maps a one-based layer onto a zero-based index`() {
        assertEquals(0, followedLayerIndex(1, 105))
        assertEquals(52, followedLayerIndex(53, 105))
        assertEquals(104, followedLayerIndex(105, 105))
    }

    @Test
    fun `following clamps a count that runs past the file`() {
        assertEquals(104, followedLayerIndex(200, 105))
        assertEquals(0, followedLayerIndex(0, 105))
    }

    @Test
    fun `following has nothing to show without a layer or a file`() {
        assertEquals(null, followedLayerIndex(null, 105))
        assertEquals(null, followedLayerIndex(3, 0))
    }
}
