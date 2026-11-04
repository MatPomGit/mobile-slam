package dev.mobile.slam.sensors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingGateTest {
    @Test
    fun publishesImmediatelyAndAtConfiguredInterval() {
        val gate = SamplingGate(intervalNs = 100L)
        assertTrue(gate.shouldPublish(1_000L))
        assertFalse(gate.shouldPublish(1_099L))
        assertTrue(gate.shouldPublish(1_100L))
        assertFalse(gate.shouldPublish(1_199L))
        assertTrue(gate.shouldPublish(1_200L))
    }

    @Test
    fun handlesClockResetAndLifecycleRestart() {
        val gate = SamplingGate(intervalNs = 100L)
        assertTrue(gate.shouldPublish(1_000L))
        assertTrue(gate.shouldPublish(10L))
        assertFalse(gate.shouldPublish(20L))
        gate.reset()
        assertTrue(gate.shouldPublish(20L))
    }
}
