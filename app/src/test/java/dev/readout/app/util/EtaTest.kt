package dev.readout.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EtaTest {

    @Test
    fun `eta is now plus the remaining time`() {
        assertEquals(1_090_000L, Format.etaEpochMillis(1_000_000L, 90L))
    }

    @Test
    fun `no eta without a positive remaining time`() {
        assertNull(Format.etaEpochMillis(1_000_000L, null))
        assertNull(Format.etaEpochMillis(1_000_000L, 0L))
        assertNull(Format.etaEpochMillis(1_000_000L, -5L))
    }
}
