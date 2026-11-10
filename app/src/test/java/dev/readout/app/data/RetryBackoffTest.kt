package dev.readout.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryBackoffTest {

    @Test
    fun `a printer that just dropped is retried quickly`() {
        assertEquals(2_000L, retryDelayMs(1))
        assertEquals(4_000L, retryDelayMs(2))
    }

    @Test
    fun `the delay grows but stays capped so recovery is noticed`() {
        assertEquals(8_000L, retryDelayMs(3))
        assertEquals(15_000L, retryDelayMs(4))
        assertEquals(15_000L, retryDelayMs(50))
    }
}
