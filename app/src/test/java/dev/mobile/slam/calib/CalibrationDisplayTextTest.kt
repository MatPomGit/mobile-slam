package dev.mobile.slam.calib

import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationDisplayTextTest {
    private val profile = MeasuredCalibration(
        cameraId = "0",
        referenceWidth = 1200,
        referenceHeight = 900,
        referenceCrop = CalibrationCrop(0, 0, 1200, 900),
        fx = 821.125,
        fy = 823.5,
        cx = 599.375,
        cy = 451.0,
        distortion = listOf(0.001234, -0.2, 0.000001, -0.000002, 0.4),
        rmsReprojectionPx = 0.47,
        provenance = CalibrationProvenance.CHESSBOARD_MEASURED,
    )

    @Test
    fun matrixContainsIntrinsicsAndIdentityRow() {
        val rows = CalibrationDisplayText.matrix(profile).lines()
        assertTrue(rows.size == 3)
        assertTrue(rows[0].contains("821.125"))
        assertTrue(rows[0].contains("599.375"))
        assertTrue(rows[1].contains("823.500"))
        assertTrue(rows[1].contains("451.000"))
        assertTrue(rows[2].contains("1.000"))
    }

    @Test
    fun distortionPreservesSmallNonzeroCoefficients() {
        val text = CalibrationDisplayText.distortion(profile)
        assertTrue(text.contains("k1 = 0.001234"))
        assertTrue(text.contains("p1 = 0.000001"))
        assertTrue(text.contains("p2 = -0.000002"))
    }
}
