package dev.mobile.slam.calib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBoardProbeLedgerTest {
    @Test
    fun uniqueViewsAccumulateIndependentErrorSeries() {
        val ledger = LiveBoardProbeLedger()
        ledger.attempt()
        assertTrue(ledger.add(0.25, 0.25, 0.18, mapOf(90 to 0.7, 270 to 2.1)))
        ledger.attempt()
        assertTrue(ledger.add(0.50, 0.50, 0.25, mapOf(90 to 0.5, 270 to 1.9)))
        ledger.attempt()
        assertTrue(ledger.add(0.75, 0.25, 0.11, mapOf(90 to 0.9, 270 to 2.5)))
        assertEquals(3, ledger.attempted)
        assertEquals(3, ledger.found)
        assertEquals(3, ledger.distinctViews)
        val scores = ledger.scores().associateBy { it.rotationDegrees }
        assertEquals(2, scores.size)
        assertEquals(0.7, scores.getValue(90).medianRmsPx, 1e-9)
        assertEquals(2.1, scores.getValue(270).medianRmsPx, 1e-9)
        assertEquals(3, scores.getValue(90).distinctViews)
    }

    @Test
    fun nearDuplicatesDoNotInflateIndependentViewCount() {
        val ledger = LiveBoardProbeLedger()
        assertTrue(ledger.add(0.3, 0.3, 0.2, mapOf(0 to 0.4)))
        assertFalse(ledger.add(0.32, 0.32, 0.21, mapOf(0 to 0.2)))
        assertEquals(2, ledger.found)
        assertEquals(1, ledger.distinctViews)
        assertEquals(0.4, ledger.scores().single().medianRmsPx, 1e-9)
    }

    @Test
    fun inconsistentHypothesesAndNonFiniteErrorsAreRejected() {
        val ledger = LiveBoardProbeLedger()
        assertTrue(ledger.add(0.2, 0.2, 0.1, mapOf(90 to 1.2, 270 to 2.1)))
        assertFalse(ledger.add(0.8, 0.8, 0.2, mapOf(90 to 1.4)))
        assertFalse(ledger.add(0.8, 0.8, 0.2, mapOf(90 to Double.NaN, 270 to 2.2)))
        assertFalse(ledger.add(-0.3, 0.8, 0.2, mapOf(90 to 1.4, 270 to 2.2)))
        assertEquals(1, ledger.distinctViews)
        assertEquals(1, ledger.found)
    }

    @Test
    fun resetClearsAllStateAndDoesNotReuseMeasurements() {
        val ledger = LiveBoardProbeLedger()
        ledger.attempt()
        ledger.add(0.3, 0.3, 0.2, mapOf(0 to 0.4))
        ledger.reset()
        assertEquals(0, ledger.attempted)
        assertEquals(0, ledger.found)
        assertEquals(0, ledger.distinctViews)
        assertTrue(ledger.scores().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCheckerboardIsNotAccepted() {
        LiveBoardProbeSettings(columns = 3, rows = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tooFrequentSamplingIsNotAccepted() {
        LiveBoardProbeSettings(intervalNs = 100_000_000L)
    }
}
