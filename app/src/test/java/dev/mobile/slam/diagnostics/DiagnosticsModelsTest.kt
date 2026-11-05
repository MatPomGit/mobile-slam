package dev.mobile.slam.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsModelsTest {
    @Test
    fun clockGateNeverEquatesUnknownCameraTimeWithImu() {
        assertTrue(ClockDomainSafety.cameraImuComparable(CameraClockSource.REALTIME))
        assertFalse(ClockDomainSafety.cameraImuComparable(CameraClockSource.UNKNOWN))
        assertFalse(ClockDomainSafety.cameraImuComparable(CameraClockSource.UNAVAILABLE))
        assertTrue(ClockDomainSafety.explanation(CameraClockSource.UNKNOWN).contains("nie wolno"))
    }

    @Test
    fun sensorCadenceUsesActualTimestampsAndBoundedHistory() {
        val window = TimestampWindow(maxEvents = 3)
        window.record(0)
        window.record(20_000_000)
        window.record(40_000_000)
        window.record(60_000_000)
        val stats = window.summary()
        assertEquals(4, stats.sampleCount)
        assertEquals(20.0, stats.medianPeriodMs!!, 0.0001)
        assertEquals(50.0, stats.frequencyHz!!, 0.0001)
        assertEquals(0.0, stats.p95AbsoluteJitterMs!!, 0.0001)
        assertEquals(0, stats.nonMonotonic)
    }

    @Test
    fun nonMonotonicSamplesAreCountedButDoNotYieldNegativeFrequencies() {
        val window = TimestampWindow()
        assertNull(window.summary().frequencyHz)
        window.record(100)
        window.record(100)
        window.record(99)
        assertEquals(2, window.summary().nonMonotonic)
        assertNull(window.summary().frequencyHz)
    }

    @Test
    fun cameraObservationsExposeImageGeometryWithoutEstimatingCalibration() {
        val recorder = CameraObservationRecorder()
        recorder.cameraBound("0")
        recorder.observe(
            FrameObservation(
                timestampNs = 100_000_000,
                widthPx = 640,
                heightPx = 480,
                rotationDegrees = 90,
                crop = "0,0,640,480",
                rowStrideBytes = 672,
                pixelStrideBytes = 1,
                processingDurationNs = 15_000_000,
            ),
        )
        val snapshot = recorder.snapshot()
        assertEquals("0", snapshot.cameraId)
        assertEquals(640, snapshot.lastFrame?.widthPx)
        assertEquals(15.0, snapshot.medianProcessingMs!!, 0.0001)
        assertEquals(1, snapshot.cadence.sampleCount)
    }
}
