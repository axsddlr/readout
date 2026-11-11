package dev.readout.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSettingsTest {

    @Test
    fun `defaults follow the camera and show nothing extra`() {
        val settings = CameraSettings()

        assertEquals(CameraAspect.FromImage, settings.aspect)
        assertFalse(settings.showResolution)
        assertFalse(settings.showName)
        assertFalse(settings.dataSaver)
        assertFalse(settings.ignoreRotationLock)
        assertTrue(settings.streamLive)
    }

    @Test
    fun `fixed aspects have ratios and the image option has none`() {
        assertNull(CameraAspect.FromImage.ratio)
        assertEquals(16f / 9f, CameraAspect.Wide.ratio ?: 0f, 0.001f)
        assertEquals(4f / 3f, CameraAspect.Standard.ratio ?: 0f, 0.001f)
    }

    @Test
    fun `data saver turns the stream off`() {
        val saver = CameraSettings(dataSaver = true)

        assertFalse(saver.streamLive)
    }
}
