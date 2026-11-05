package dev.mobile.slam.calib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MeasuredCalibrationTest {
    private fun profile(
        coefficients: List<Double> = listOf(0.0, 0.0, 0.0, 0.0, 0.0),
        crop: CalibrationCrop = CalibrationCrop(0, 0, 1280, 960),
    ) = MeasuredCalibration(
        cameraId = "0",
        referenceWidth = 1280,
        referenceHeight = 960,
        referenceCrop = crop,
        fx = 1000.0,
        fy = 900.0,
        cx = 640.0,
        cy = 480.0,
        distortion = coefficients,
        rmsReprojectionPx = 0.35,
        provenance = CalibrationProvenance.CHESSBOARD_MEASURED,
    )

    @Test
    fun scalesFullFrameIntrinsicsAndSubtractsAnalysisCropOrigin() {
        val intrinsics = profile().forAnalysis("0", 640, 480, CalibrationCrop(20, 10, 620, 470))
        assertNotNull(intrinsics)
        assertEquals(500.0, intrinsics!!.fx, 1e-9)
        assertEquals(450.0, intrinsics.fy, 1e-9)
        assertEquals(300.0, intrinsics.cx, 1e-9)
        assertEquals(230.0, intrinsics.cy, 1e-9)
        assertEquals(600, intrinsics.imageWidth)
        assertEquals(460, intrinsics.imageHeight)
    }

    @Test
    fun refusesDifferentCameraAndAspectRatio() {
        val p = profile()
        assertNull(p.forAnalysis("1", 640, 480, CalibrationCrop(0, 0, 640, 480)))
        assertNull(p.forAnalysis("0", 640, 360, CalibrationCrop(0, 0, 640, 360)))
        assertNull(p.forAnalysis("0", 640, 480, CalibrationCrop(0, 0, 641, 480)))
    }

    @Test
    fun refusesUnverifiedCalibrationSubcropTransformation() {
        val p = profile(crop = CalibrationCrop(10, 10, 1270, 950))
        assertNull(p.forAnalysis("0", 640, 480, CalibrationCrop(0, 0, 640, 480)))
    }

    @Test
    fun zeroDistortionReturnsNormalizedPinholeCoordinates() {
        val p = profile()
        val k = p.forAnalysis("0", 640, 480, CalibrationCrop(0, 0, 640, 480))!!
        val point = p.undistort(PixelPoint(370.0, 285.0), k)!!
        assertEquals(0.1, point.x, 1e-9)
        assertEquals(0.1, point.y, 1e-9)
    }

    @Test
    fun invertsSyntheticBrownConradyDistortion() {
        val p = profile(listOf(-0.12, 0.015, 0.001, -0.002, 0.001))
        val k = p.forAnalysis("0", 640, 480, CalibrationCrop(0, 0, 640, 480))!!
        val ideal = PixelPoint(0.2, -0.16)
        val distorted = p.distortNormalized(ideal)!!
        val pixel = PixelPoint(distorted.x * k.fx + k.cx, distorted.y * k.fy + k.cy)
        val recovered = p.undistort(pixel, k)!!
        assertEquals(ideal.x, recovered.x, 1e-7)
        assertEquals(ideal.y, recovered.y, 1e-7)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesNonFiniteCoefficients() {
        profile(listOf(0.0, 0.0, Double.NaN, 0.0, 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesNegativeReprojectionError() {
        MeasuredCalibration(
            "0", 640, 480, CalibrationCrop(0, 0, 640, 480),
            500.0, 500.0, 320.0, 240.0, listOf(0.0, 0.0, 0.0, 0.0, 0.0),
            -1.0, CalibrationProvenance.CHESSBOARD_MEASURED,
        )
    }
}
