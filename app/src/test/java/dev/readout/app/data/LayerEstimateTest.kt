package dev.readout.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerEstimateTest {

    private fun preview(vararg seconds: Float): GcodePreview {
        var z = 0f
        val layers = seconds.map { layerSeconds ->
            z += 0.2f
            PreviewLayer(
                lines = listOf(PreviewLine(0f, 0f, 1f, 0f, true)),
                z = z,
                commands = 1,
                filamentMm = 1f,
                seconds = layerSeconds
            )
        }
        return GcodePreview(
            layers = layers,
            minX = 0f,
            maxX = 10f,
            minY = 0f,
            maxY = 10f,
            minZ = 0.2f,
            maxZ = z,
            truncated = false
        )
    }

    @Test
    fun `completion maps onto the layer whose cumulative time covers it`() {
        val uniform = preview(4f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 4f, 4f)

        assertEquals(1, estimateLayer(uniform, 0f))
        assertEquals(1, estimateLayer(uniform, 0.05f))
        assertEquals(5, estimateLayer(uniform, 0.5f))
        assertEquals(10, estimateLayer(uniform, 0.99f))
        assertEquals(10, estimateLayer(uniform, 1f))
    }

    @Test
    fun `a long layer swallows a larger share of the progress`() {
        // 1 s then 19 s: half the time is still inside the second layer.
        val lopsided = preview(1f, 19f)

        assertEquals(1, estimateLayer(lopsided, 0.02f))
        assertEquals(2, estimateLayer(lopsided, 0.5f))
    }

    @Test
    fun `an empty or untimed file estimates nothing`() {
        assertNull(estimateLayer(null, 0.5f))
        assertNull(estimateLayer(preview(), 0.5f))
        assertNull(estimateLayer(preview(0f, 0f), 0.5f))
    }

    @Test
    fun `no completion means no estimate`() {
        assertNull(estimateLayer(preview(1f, 1f), null))
    }

    @Test
    fun `a completion outside zero to one is clamped rather than extrapolated`() {
        val two = preview(5f, 5f)

        assertEquals(1, estimateLayer(two, -3f))
        assertEquals(2, estimateLayer(two, 7f))
    }
}
