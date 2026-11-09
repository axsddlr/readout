package dev.readout.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A real OctoPrint sends `size` and `date` as plain JSON integers. The file list used to sort with
 * `it.dateSeconds ?: 0`, which widens the selector to `Comparable<*>`; comparing a Long entry
 * against the Int literal threw "Integer cannot be cast to Long" before anything could render.
 */
class FilesOrderingTest {

    private val payload = JSONArray(
        """
        [
          {"name":"z.gcode","path":"z.gcode","origin":"local","type":"machinecode","size":1234,"date":1762695840},
          {"name":"a.gcode","path":"a.gcode","origin":"local","type":"machinecode","size":42,"date":1762695000},
          {"name":"folder","path":"folder","origin":"local","type":"folder"},
          {"name":"nodate.gcode","path":"nodate.gcode","origin":"local","type":"machinecode","size":7}
        ]
        """.trimIndent()
    )

    @Test
    fun `integer dates and sizes do not throw`() {
        val files = SnapshotParser.parseFiles(payload)

        assertEquals(4, files.size)
        assertEquals(1234L, files.first { it.name == "z.gcode" }.sizeBytes)
        assertEquals(1762695840L, files.first { it.name == "z.gcode" }.dateSeconds)
        assertEquals(null, files.first { it.name == "nodate.gcode" }.dateSeconds)
    }

    @Test
    fun `folders come first and the newest file follows`() {
        val names = SnapshotParser.parseFiles(payload).map { it.name }

        assertEquals("folder", names.first())
        assertEquals(listOf("z.gcode", "a.gcode", "nodate.gcode"), names.drop(1))
    }

    @Test
    fun `a payload without dates still parses`() {
        val files = SnapshotParser.parseFiles(
            JSONArray("""[{"name":"x.gcode","path":"x.gcode","size":0}]""")
        )

        assertEquals(1, files.size)
        assertTrue(files.single().dateSeconds == null)
    }
}
