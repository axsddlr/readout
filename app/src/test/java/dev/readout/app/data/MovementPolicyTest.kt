package dev.readout.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementPolicyTest {

    @Test
    fun `movement is locked while a job is running`() {
        assertFalse(movementAllowed(MachineState.Printing, allowWhilePrinting = false))
        assertFalse(movementAllowed(MachineState.Paused, allowWhilePrinting = false))
        assertFalse(movementAllowed(MachineState.Pausing, allowWhilePrinting = false))
        assertFalse(movementAllowed(MachineState.Resuming, allowWhilePrinting = false))
        assertFalse(movementAllowed(MachineState.Starting, allowWhilePrinting = false))
        assertFalse(movementAllowed(MachineState.Finishing, allowWhilePrinting = false))
    }

    @Test
    fun `movement is live when nothing is printing`() {
        assertTrue(movementAllowed(MachineState.Idle, allowWhilePrinting = false))
        assertTrue(movementAllowed(MachineState.Unknown, allowWhilePrinting = false))
        assertTrue(movementAllowed(MachineState.Error, allowWhilePrinting = false))
        assertTrue(movementAllowed(null, allowWhilePrinting = false))
    }

    @Test
    fun `the opt-in unlocks movement during a print`() {
        assertTrue(movementAllowed(MachineState.Printing, allowWhilePrinting = true))
        assertTrue(movementAllowed(MachineState.Paused, allowWhilePrinting = true))
    }
}
