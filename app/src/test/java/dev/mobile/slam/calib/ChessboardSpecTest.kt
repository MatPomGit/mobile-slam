package dev.mobile.slam.calib

import org.junit.Assert.assertEquals
import org.junit.Test

class ChessboardSpecTest {
    @Test
    fun defaultsDescribeNineBySixInternalCorners() {
        val spec = ChessboardSpec()
        assertEquals(9, spec.columns)
        assertEquals(6, spec.rows)
        assertEquals(8, spec.minViews)
        assertEquals(20.0, spec.squareMm, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooFewInternalCorners() {
        ChessboardSpec(columns = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSquareSize() {
        ChessboardSpec(squareMm = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedRmsThreshold() {
        ChessboardSpec(maxRmsPx = Double.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooFewCalibrationViews() {
        ChessboardSpec(minViews = 3)
    }
}
