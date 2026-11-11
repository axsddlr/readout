package dev.readout.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemInfoTest {

    private val payload = JSONObject(
        """
        {
          "systeminfo": {
            "octoprint.version": "1.11.8",
            "env.plugins.pi_support.octopi_version": "1.1.0",
            "env.plugins.pi_support.model": "Raspberry Pi Zero 2 W Rev 1.0",
            "printer.firmware": "Marlin V1.0.6 (Sep 19 2023 09:14:17)",
            "env.python.version": "3.11.2",
            "env.hardware.ram": "379863040",
            "env.hardware.cores": "4",
            "env.plugins.pi_support.throttle_state": "0x0",
            "octoprint.safe_mode": false
          }
        }
        """
    )

    @Test
    fun `reads the flat dotted map OctoPrint sends`() {
        val info = SystemInfo.from(payload)

        assertEquals("1.11.8", info.octoPrintVersion)
        assertEquals("1.1.0", info.octoPiVersion)
        assertEquals("Raspberry Pi Zero 2 W Rev 1.0", info.model)
        assertTrue(info.firmware!!.startsWith("Marlin V1.0.6"))
        assertEquals("3.11.2", info.pythonVersion)
        assertEquals(362, info.ramMegabytes)
        assertEquals(4, info.cpuCount)
        assertEquals(0, info.throttleState)
        assertTrue(info.throttleWarnings.isEmpty())
    }

    @Test
    fun `absent values stay unknown instead of becoming zero`() {
        val info = SystemInfo.from(JSONObject("""{"systeminfo":{}}"""))

        assertNull(info.octoPrintVersion)
        assertNull(info.throttleState)
        assertNull(info.ramMegabytes)
        assertTrue(info.throttleWarnings.isEmpty())
    }

    @Test
    fun `throttle bits explain themselves`() {
        // 0x50000: under-voltage and a throttling episode have happened since boot.
        val info = SystemInfo(throttleState = 0x50000)
        val warnings = info.throttleWarnings

        assertEquals(2, warnings.size)
        assertTrue(warnings[0].contains("Under-voltage"))
        assertTrue(warnings[1].contains("throttled since boot"))
    }

    @Test
    fun `throttling right now is said in the present tense`() {
        val warnings = SystemInfo(throttleState = 0x4).throttleWarnings

        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("right now"))
    }

    @Test
    fun `a healthy throttle word warns about nothing`() {
        assertTrue(SystemInfo(throttleState = 0x0).throttleWarnings.isEmpty())
    }

    @Test
    fun `unparseable throttle words are unknown rather than zero`() {
        assertNull(SystemInfo.parseThrottle("not a number"))
        assertEquals(0, SystemInfo.parseThrottle("0x0"))
        assertEquals(16, SystemInfo.parseThrottle("0x10"))
    }

    @Test
    fun `server commands are ordered core first and de-html'd`() {
        val commands = SystemCommand.list(
            JSONObject(
                """
                {
                  "core": [
                    {"action": "reboot", "name": "Reboot system",
                     "confirm": "<strong>You are about to reboot</strong></p><p>Careful &amp; sure?"},
                    {"action": "shutdown", "name": "Shutdown system", "confirm": ""}
                  ],
                  "plugin": [
                    {"action": "update", "name": "Update all", "confirm": "Run the update?"}
                  ]
                }
                """
            )
        )

        assertEquals(3, commands.size)
        assertEquals("Reboot system", commands[0].name)
        assertEquals("core", commands[0].source)
        assertEquals("You are about to reboot Careful & sure?", commands[0].confirm)
        assertNull(commands[1].confirm)
        assertEquals("plugin", commands[2].source)
    }
}
