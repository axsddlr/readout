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

    @Test
    fun `each layer carries its height, commands, filament and move time`() {
        val preview = parse(
            """
            G90
            M82
            M104 S200
            G1 Z0.2
            G1 X10 Y0 E1 F600
            G1 X10 Y10 E2
            G1 Z0.4
            G1 X20 Y0 E3
            """
        )

        assertEquals(2, preview.layers.size)
        val first = preview.layers[0]
        assertEquals(0.2f, first.z, 0.001f)
        // G90, M82, M104, the Z move, two extrusions and the Z move that opens layer two.
        assertEquals(7, first.commands)
        assertEquals(2f, first.filamentMm, 0.001f)
        // Two 10 mm moves at 600 mm/min.
        assertEquals(2f, first.seconds, 0.01f)

        val second = preview.layers[1]
        assertEquals(0.4f, second.z, 0.001f)
        assertEquals(1, second.commands)
        assertEquals(1f, second.filamentMm, 0.001f)
        // (10,10) to (20,0) is 14.1 mm, still at the modal 600 mm/min.
        assertEquals(1.41f, second.seconds, 0.01f)
    }

    @Test
    fun `model extent and layer height come from the same parse`() {
        val preview = parse(
            """
            G90
            M83
            G1 Z0.2
            G1 X10 Y0 E1 F600
            G1 Z0.4
            G1 X20 Y0 E1
            G1 Z0.7
            G1 X30 Y0 E1
            """
        )

        assertEquals(0.2f, preview.minZ, 0.001f)
        assertEquals(0.7f, preview.maxZ, 0.001f)
        assertEquals(0.5f, preview.modelHeight, 0.001f)
        assertEquals(30f, preview.modelWidth, 0.001f)
        // Steps are 0.2 and 0.3 mm; the median of the pair is 0.25.
        assertEquals(0.25f, preview.layerHeight, 0.001f)
        assertEquals(3f, preview.filamentMm, 0.001f)
    }

    @Test
    fun `a slicer duration comment is preferred over the move estimate`() {
        val cura = parse(
            """
            ;TIME:3600
            G90
            M83
            G1 Z0.2
            G1 X10 Y0 E1 F600
            """
        )
        assertEquals(3600L, cura.slicerSeconds)
        assertEquals(3600L, cura.printSeconds ?: -1L)
        assertFalse(cura.printTimeIsEstimate)
        assertEquals(1L, cura.moveSeconds ?: -1L)

        val prusa = parse(
            """
            ; estimated printing time (normal mode) = 1h 22m 5s
            G90
            M83
            G1 Z0.2
            G1 X10 Y0 E1 F600
            """
        )
        assertEquals(4925L, prusa.slicerSeconds)
    }

    @Test
    fun `without a slicer comment the print time falls back to the moves`() {
        val preview = parse(square)

        assertEquals(null, preview.slicerSeconds)
        assertEquals(2L, preview.moveSeconds ?: -1L)
        assertTrue(preview.printTimeIsEstimate)
    }
}
