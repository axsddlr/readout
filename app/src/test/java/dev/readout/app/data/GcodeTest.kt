package dev.readout.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GcodeTest {

    @Test
    fun `fan and motors use the plain forms`() {
        assertEquals("M106 S255", Gcode.FAN_ON)
        assertEquals("M107", Gcode.FAN_OFF)
        assertEquals("M18", Gcode.MOTORS_OFF)
    }

    @Test
    fun `feed and flow carry the percentage`() {
        assertEquals("M220 S80", Gcode.feedRate(80))
        assertEquals("M221 S95", Gcode.flowRate(95))
    }

    @Test
    fun `percentages outside the usable range are clamped`() {
        assertEquals("M220 S1", Gcode.feedRate(0))
        assertEquals("M220 S300", Gcode.feedRate(500))
        assertEquals("M221 S1", Gcode.flowRate(-10))
        assertEquals("M221 S300", Gcode.flowRate(999))
    }
}
