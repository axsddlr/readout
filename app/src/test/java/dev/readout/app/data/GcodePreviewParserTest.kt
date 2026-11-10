package dev.readout.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GcodePreviewParserTest {

    private fun parse(gcode: String, max: Int = 150_000): GcodePreview =
        GcodePreviewParser.parse(gcode.trimIndent().reader().buffered(), max)

    private val square = """
        G90
        M83
        G1 Z0.2
        G1 X0 Y0 F1200
        G1 X10 Y0 E1
        G1 X10 Y10 E1
        G1 X0 Y10 E1
        G1 X0 Y0 E1
    """

    @Test
    fun `extrusion moves become one layer with the right bounds`() {
        val preview = parse(square)

        assertEquals(1, preview.layers.size)
        assertEquals(4, preview.layers[0].lines.count { it.extruding })
        assertEquals(0f, preview.minX, 0.001f)
        assertEquals(10f, preview.maxX, 0.001f)
        assertEquals(0f, preview.minY, 0.001f)
        assertEquals(10f, preview.maxY, 0.001f)
        assertFalse(preview.truncated)
    }

    @Test
    fun `travel moves are kept but marked`() {
        val preview = parse(
            """
            G90
            M83
            G1 Z0.2
            G1 X5 Y5
            G1 X10 Y0 E1
            """
        )
        val lines = preview.layers.flatMap { it.lines }

        assertTrue(lines.any { !it.extruding })
        assertTrue(lines.any { it.extruding })
    }

    @Test
    fun `a Z change starts a new layer`() {
        val preview = parse(
            """
            G90
            M83
            G1 Z0.2
            G1 X0 Y0
            G1 X5 Y0 E1
            G1 Z0.4
            G1 X0 Y0
            G1 X5 Y5 E1
            """
        )

        assertEquals(2, preview.layers.size)
    }

    @Test
    fun `a Z hop without extrusion does not start a layer`() {
        val preview = parse(
            """
            G90
            M83
            G1 Z0.2
            G1 X0 Y0
            G1 X5 Y0 E1
            G1 Z0.6
            G1 X6 Y0
            G1 Z0.2
            G1 X7 Y0 E1
            """
        )

        assertEquals(1, preview.layers.size)
    }

    @Test
    fun `absolute extrusion resets do not create phantom extrusions`() {
        val preview = parse(
            """
            G90
            M82
            G1 Z0.2
            G1 X0 Y0 E0
            G1 X5 Y0 E1
            G92 E0
            G1 X10 Y0 E1
            """
        )

        assertEquals(2, preview.layers[0].lines.count { it.extruding })
    }

    @Test
    fun `relative XYZ mode is honoured`() {
        val preview = parse(
            """
            G91
            M83
            G1 Z0.2
            G1 X1 Y1
            G1 X4 Y0 E1
            G1 X0 Y4 E1
            """
        )

        assertEquals(1, preview.layers.size)
        assertEquals(5f, preview.maxX, 0.001f)
        assertEquals(5f, preview.maxY, 0.001f)
    }

    @Test
    fun `the segment budget truncates instead of growing without bound`() {
        val preview = parse(square, max = 2)

        assertTrue(preview.truncated)
    }

    @Test
    fun `trailing travel moves do not become an empty layer`() {
        val preview = parse(
            """
            G90
            M83
            G1 Z0.2
            G1 X0 Y0
            G1 X5 Y0 E1
            G1 Z5
            G1 X0 Y0
            """
        )

        assertEquals(1, preview.layers.size)
        assertTrue(preview.layers.last().lines.any { it.extruding })
    }

    @Test
    fun `a file with no extrusion still yields a drawable preview`() {
        val preview = parse("G90\nG1 X10 Y10\nG1 X20 Y20")

        assertFalse(preview.truncated)
        assertEquals(1f, preview.maxX, 0.001f)
    }
}
