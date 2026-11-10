package dev.mobile.slam.calib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun automaticSearchIncludesAllSupportedPatternsWithoutDuplicates() {
        val patterns = ChessboardPatternSearch.candidates
        assertEquals(13 * 13, patterns.size)
        assertEquals(patterns.size, patterns.distinct().size)
        assertEquals(ChessboardPattern(7, 10), patterns.first())
        assertTrue(patterns.contains(ChessboardPattern(9, 6)))
        assertTrue(patterns.contains(ChessboardPattern(4, 16)))
        assertTrue(patterns.contains(ChessboardPattern(16, 4)))
    }

    @Test
    fun automaticModePreservesMeasuredSquarePitch() {
        val spec = ChessboardSpec(automaticPattern = true, squareMm = 25.5)
        assertTrue(spec.automaticPattern)
        assertEquals(25.5, spec.squareMm, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTooSmallDiscoveredPattern() {
        ChessboardPattern(3, 10)
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
