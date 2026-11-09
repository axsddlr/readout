package dev.readout.app.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearParserTest {

    private fun printer(flags: String, text: String) = """
        {"temperature": {
            "tool0": {"actual": 209.6, "target": 210.0},
            "bed": {"actual": 59.4, "target": 60.0}
        }, "state": {"text": "$text", "flags": $flags}}
    """.trimIndent()

    @Test
    fun `a printing job yields progress, eta and temperatures`() {
        val snapshot = WearParser.parse(
            printer("""{"printing": true, "paused": false}""", "Printing"),
            """{"progress": {"completion": 41.7, "printTimeLeft": 2410}}"""
        )

        assertEquals("PRINTING", snapshot.headline)
        assertTrue(snapshot.printing)
        assertFalse(snapshot.paused)
        assertEquals(0.417f, snapshot.progress!!, 0.001f)
        assertEquals(2410, snapshot.etaSeconds)
        assertEquals(209.6f, snapshot.nozzleActual!!, 0.01f)
        assertEquals(210, snapshot.nozzleTarget)
        assertEquals(59.4f, snapshot.bedActual!!, 0.01f)
    }

    @Test
    fun `a paused job is called paused`() {
        val snapshot = WearParser.parse(
            printer("""{"printing": true, "paused": true}""", "Paused"),
            """{"progress": {"completion": 62.0, "printTimeLeft": 900}}"""
        )

        assertEquals("PAUSED", snapshot.headline)
        assertTrue(snapshot.paused)
    }

    @Test
    fun `an idle printer keeps its state label and has no progress`() {
        val snapshot = WearParser.parse(
            printer("""{"printing": false, "operational": true}""", "Operational"),
            null
        )

        assertEquals("OPERATIONAL", snapshot.headline)
        assertFalse(snapshot.printing)
        assertEquals(null, snapshot.progress)
        assertEquals(null, snapshot.etaSeconds)
        assertEquals(209.6f, snapshot.nozzleActual!!, 0.01f)
    }

    @Test
    fun `a missing progress object does not throw`() {
        val snapshot = WearParser.parse(
            printer("""{"printing": true}""", "Printing"),
            """{"job": {"file": {"name": "x.gcode"}}}"""
        )

        assertEquals(null, snapshot.progress)
        assertEquals(null, snapshot.etaSeconds)
        assertEquals("PRINTING", snapshot.headline)
    }
}
