package dev.readout.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is the app's only source of truth about the machine, so it is tested against
 * recorded OctoPrint payload shapes rather than mocks of our own API.
 */
class SnapshotParserTest {

    private fun printingJob(): JSONObject = JSONObject(
        """
        {
          "job": { "file": { "name": "calibration_cube.gcode", "origin": "local", "path": "calibration_cube.gcode" },
                   "estimatedPrintTime": 4200 },
          "progress": { "completion": 41.7, "printTime": 1720, "printTimeLeft": 2410,
                        "printTimeLeftOrigin": "average", "filepos": 812345 },
          "state": "Printing"
        }
        """.trimIndent()
    )

    private fun printingPrinter(): JSONObject = JSONObject(
        """
        {
          "temperature": {
            "tool0": { "actual": 209.6, "target": 210.0, "offset": 0 },
            "bed": { "actual": 59.4, "target": 60.0, "offset": 0 }
          },
          "state": { "text": "Printing", "flags": {
            "operational": true, "paused": false, "printing": true, "cancelling": false,
            "pausing": false, "sdReady": true, "error": false, "ready": false,
            "finishing": false, "resuming": false, "closedOrError": false, "starting": false } }
        }
        """.trimIndent()
    )

    @Test
    fun `printing job is mapped with progress and eta provenance`() {
        val snapshot = SnapshotParser.parse(printingPrinter(), printingJob(), "OctoPrint 1.10.2")

        assertEquals(MachineState.Printing, snapshot.state)
        assertEquals("Printing", snapshot.stateLabel)
        assertEquals("calibration_cube.gcode", snapshot.jobName)
        assertEquals(0.417f, snapshot.progress!!, 0.001f)
        assertEquals(1720L, snapshot.printTimeSeconds)
        assertEquals(2410L, snapshot.printTimeLeftSeconds)
        assertEquals("average", snapshot.etaOrigin)
        assertEquals("OctoPrint 1.10.2", snapshot.octoPrintVersion)
    }

    @Test
    fun `temperatures are labelled and ordered nozzle then bed`() {
        val snapshot = SnapshotParser.parse(printingPrinter(), printingJob(), null)
        assertEquals(listOf("Nozzle", "Bed"), snapshot.temperatures.map { it.label })
        assertEquals(210f, snapshot.temperatures[0].target)
        assertEquals(60f, snapshot.temperatures[1].target)
    }

    @Test
    fun `idle printer has no progress rather than zero percent`() {
        val printer = JSONObject(
            """{"temperature":{"tool0":{"actual":21.0,"target":0.0}},"state":{"text":"Operational","flags":{"operational":true,"printing":false,"ready":true}}}"""
        )
        val job = JSONObject("""{"job":{"file":null},"progress":{"completion":null,"printTime":null,"printTimeLeft":null},"state":"Operational"}""")

        val snapshot = SnapshotParser.parse(printer, job, null)

        assertEquals(MachineState.Idle, snapshot.state)
        assertNull(snapshot.progress)
        assertNull(snapshot.jobName)
        assertNull(snapshot.printTimeLeftSeconds)
    }

    @Test
    fun `closed or error flags win over printing`() {
        val printer = JSONObject(
            """{"temperature":{},"state":{"text":"Offline","flags":{"printing":true,"closedOrError":true}}}"""
        )
        val snapshot = SnapshotParser.parse(printer, null, null)
        assertEquals(MachineState.Error, snapshot.state)
    }

    @Test
    fun `files are sorted folders first then newest`() {
        val raw = org.json.JSONArray(
            """
            [
              {"name":"old.gcode","path":"old.gcode","origin":"local","size":100,"date":1000000},
              {"name":"new.gcode","path":"new.gcode","origin":"local","size":200,"date":2000000},
              {"name":"folder","path":"folder","origin":"local","type":"folder"}
            ]
            """.trimIndent()
        )

        val files = SnapshotParser.parseFiles(raw)

        assertEquals(3, files.size)
        assertTrue(files.first().isDirectory)
        assertEquals("new.gcode", files[1].name)
        assertEquals("old.gcode", files[2].name)
    }
}
