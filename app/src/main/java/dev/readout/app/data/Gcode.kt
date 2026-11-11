package dev.readout.app.data

/**
 * The handful of printer commands the Control tab sends directly, in one place so the strings can
 * be checked without a printer attached.
 */
object Gcode {

    const val FAN_ON = "M106 S255"
    const val FAN_OFF = "M107"

    /** Cuts the steppers. Only ever sent while no job is running: mid-print it drops the head. */
    const val MOTORS_OFF = "M18"

    /** Percentage of the print's own feed rate, as OctoPrint's Control tab defines it. */
    fun feedRate(percent: Int): String = "M220 S${percent.coerceIn(1, 300)}"

    /** Percentage of extruded volume. */
    fun flowRate(percent: Int): String = "M221 S${percent.coerceIn(1, 300)}"
}
