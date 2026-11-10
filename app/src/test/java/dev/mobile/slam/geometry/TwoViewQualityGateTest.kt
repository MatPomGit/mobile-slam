package dev.mobile.slam.geometry

import dev.mobile.slam.calib.CameraIntrinsics
import dev.mobile.slam.calib.PixelPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TwoViewQualityGateTest {
    private val intrinsics = CameraIntrinsics(
        fx = 800.0,
        fy = 790.0,
        cx = 640.0,
        cy = 480.0,
        distortion = listOf(0.0, 0.0, 0.0, 0.0, 0.0),
        imageWidth = 1280,
        imageHeight = 960,
    )

    private fun matches(motion: Double = 12.0): List<TwoViewCorrespondence> = (0 until 8).flatMap { row ->
        (0 until 10).map { column ->
            val x = 120.0 + column * 100.0
            val y = 90.0 + row * 105.0
            TwoViewCorrespondence(
                trackId = (row * 10 + column + 1).toLong(),
                first = PixelPoint(x, y),
                second = PixelPoint(x + motion, y + motion / 4.0),
            )
        }
    }

    @Test
    fun acceptsWellDistributedMovingFeatures() {
        assertNull(TwoViewQualityGate.reject(matches(), intrinsics, TwoViewConfig()))
    }

    @Test
    fun rejectsTooFewOrTooManyFeatures() {
        assertEquals(
            TwoViewFailure.TOO_FEW_MATCHES,
            TwoViewQualityGate.reject(matches().take(10), intrinsics, TwoViewConfig()),
        )
        assertEquals(
            TwoViewFailure.TOO_MANY_MATCHES,
            TwoViewQualityGate.reject(matches(), intrinsics, TwoViewConfig(maxMatches = 50)),
        )
    }

    @Test
    fun rejectsRepeatedIdentifiersAndOutOfBoundsCoordinates() {
        val repeated = matches().toMutableList()
        repeated[1] = repeated[1].copy(trackId = repeated[0].trackId)
        assertEquals(
            TwoViewFailure.DUPLICATE_IDS,
            TwoViewQualityGate.reject(repeated, intrinsics, TwoViewConfig()),
        )
        val outside = matches().toMutableList()
        outside[0] = outside[0].copy(first = PixelPoint(-1.0, 90.0))
        assertEquals(
            TwoViewFailure.INVALID_COORDINATES,
            TwoViewQualityGate.reject(outside, intrinsics, TwoViewConfig()),
        )
    }

    @Test
    fun rejectsInsufficientCoverageAndSmallMotion() {
        val clustered = matches().mapIndexed { index, feature ->
            feature.copy(
                first = PixelPoint(10.0 + index % 5, 10.0 + index / 5),
                second = PixelPoint(22.0 + index % 5, 13.0 + index / 5),
            )
        }
        assertEquals(
            TwoViewFailure.LOW_COVERAGE,
            TwoViewQualityGate.reject(clustered, intrinsics, TwoViewConfig()),
        )
        assertEquals(
            TwoViewFailure.LOW_MOTION,
            TwoViewQualityGate.reject(matches(0.01), intrinsics, TwoViewConfig()),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRansacProbabilityIsRejected() {
        TwoViewConfig(confidence = 1.0)
    }
}
