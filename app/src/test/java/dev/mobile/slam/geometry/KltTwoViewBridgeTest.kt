package dev.mobile.slam.geometry

import dev.mobile.slam.calib.CalibrationCrop
import dev.mobile.slam.calib.CalibrationProvenance
import dev.mobile.slam.calib.MeasuredCalibration
import dev.mobile.slam.calib.PixelPoint
import dev.mobile.slam.frontend.FrameGeometry
import dev.mobile.slam.frontend.FrameMetadata
import dev.mobile.slam.frontend.PixelCrop
import org.junit.Assert.assertEquals
import org.junit.Test

class KltTwoViewBridgeTest {
    private val shape = FrameGeometry(1280, 960, PixelCrop(30, 20, 1250, 940), 0)
    private val first = FrameMetadata(5L, 2L, 100_000_000L, shape)
    private val second = FrameMetadata(12L, 2L, 330_000_000L, shape)
    private val candidate = TwoViewFramePair(
        first,
        second,
        (1L..35L).map { id ->
            TwoViewCorrespondence(id, PixelPoint(10.0 + id, 20.0 + id), PixelPoint(20.0 + id, 21.0 + id))
        },
    )
    private val profile = MeasuredCalibration(
        cameraId = "0",
        referenceWidth = 1280,
        referenceHeight = 960,
        referenceCrop = CalibrationCrop(0, 0, 1280, 960),
        fx = 850.0,
        fy = 845.0,
        cx = 640.0,
        cy = 480.0,
        distortion = List(5) { 0.0 },
        rmsReprojectionPx = 0.45,
        provenance = CalibrationProvenance.CHESSBOARD_MEASURED,
    )

    @Test
    fun refusesUnverifiedCameraGeometryBeforeNativeComputation() {
        val result = KltTwoViewBridge.initialize(candidate, profile, "0", opticalPathVerified = false)
        assertEquals(TwoViewFailure.UNVERIFIED_CALIBRATION, (result as TwoViewResult.Rejected).reason)
    }

    @Test
    fun refusesWrongCameraEvenWithVerificationFlag() {
        val result = KltTwoViewBridge.initialize(candidate, profile, "1", opticalPathVerified = true)
        assertEquals(TwoViewFailure.INCOMPATIBLE_GEOMETRY, (result as TwoViewResult.Rejected).reason)
    }

    @Test
    fun refusesUncalibratedCallRegardlessOfVerificationFlag() {
        val result = KltTwoViewBridge.initialize(candidate, null, "0", opticalPathVerified = true)
        assertEquals(TwoViewFailure.UNVERIFIED_CALIBRATION, (result as TwoViewResult.Rejected).reason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesMismatchedFrameGeometry() {
        val different = second.copy(geometry = shape.copy(rotationDegrees = 90))
        TwoViewFramePair(first, different, candidate.matches)
    }
}
