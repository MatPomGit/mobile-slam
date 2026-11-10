package dev.mobile.slam.calib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationOrientationCandidatesTest {
    // A synthetic portrait 3:4 camera profile; numeric values are illustrative.
    private val profile = MeasuredCalibration(
        cameraId = "0",
        referenceWidth = 750,
        referenceHeight = 1000,
        referenceCrop = CalibrationCrop(0, 0, 750, 1000),
        fx = 703.0,
        fy = 702.0,
        cx = 380.0,
        cy = 501.0,
        distortion = listOf(0.11, -0.24, 0.0008, 0.0067, 0.21),
        rmsReprojectionPx = 0.49,
        provenance = CalibrationProvenance.CHESSBOARD_MEASURED,
    )

    private fun variants(crop: CalibrationCrop = CalibrationCrop(0, 0, 640, 480)) =
        CalibrationOrientationCandidates.forAnalysis(profile, "0", 640, 480, crop)

    @Test
    fun portraitProfileProducesTwoUnverifiedQuarterTurnHypotheses() {
        val result = LiveCalibrationValidator.assess(
            profile,
            "0",
            640,
            480,
            CalibrationCrop(0, 0, 640, 480),
        )
        assertEquals(LiveCalibrationState.ORIENTATION_AMBIGUOUS, result.state)
        assertNull(result.candidateIntrinsics)
        assertEquals(listOf(90, 270), result.rotatedCandidates.map { it.clockwiseDegrees })
        assertTrue(result.message.contains("90°"))
        assertTrue(result.message.contains("270°"))
    }

    @Test
    fun ninetyDegreeRotationSwapsFocalLengthsAndRotatesTangentialTerms() {
        val candidate = variants().first().intrinsics
        assertEquals(702.0 * 0.64, candidate.fx, 1e-9)
        assertEquals(703.0 * 0.64, candidate.fy, 1e-9)
        assertEquals((999.0 - 501.0) * 0.64, candidate.cx, 1e-9)
        assertEquals(380.0 * 0.64, candidate.cy, 1e-9)
        assertEquals(listOf(0.11, -0.24, 0.0067, -0.0008, 0.21), candidate.distortion)
    }

    @Test
    fun twoSeventyDegreeRotationHasDifferentPrincipalPointAndTangentialSigns() {
        val candidate = variants()[1].intrinsics
        assertEquals(501.0 * 0.64, candidate.cx, 1e-9)
        assertEquals((749.0 - 380.0) * 0.64, candidate.cy, 1e-9)
        assertEquals(listOf(0.11, -0.24, -0.0067, 0.0008, 0.21), candidate.distortion)
    }

    @Test
    fun cropIsAppliedInRotatedPixelAxes() {
        val candidate = variants(CalibrationCrop(20, 10, 620, 470)).first().intrinsics
        assertEquals((999.0 - 501.0) * 0.64 - 20.0, candidate.cx, 1e-9)
        assertEquals(380.0 * 0.64 - 10.0, candidate.cy, 1e-9)
        assertEquals(600, candidate.imageWidth)
        assertEquals(460, candidate.imageHeight)
    }

    @Test
    fun invalidCropCameraOrAspectRatioNeverProducesHypothesis() {
        assertTrue(CalibrationOrientationCandidates.forAnalysis(profile, "1", 640, 480, CalibrationCrop(0, 0, 640, 480)).isEmpty())
        assertTrue(CalibrationOrientationCandidates.forAnalysis(profile, "0", 640, 360, CalibrationCrop(0, 0, 640, 360)).isEmpty())
        assertTrue(CalibrationOrientationCandidates.forAnalysis(profile, "0", 640, 480, CalibrationCrop(0, 0, 641, 480)).isEmpty())
        val partial = profile.copy(referenceCrop = CalibrationCrop(10, 10, 740, 990))
        assertTrue(
            CalibrationOrientationCandidates.forAnalysis(
                partial,
                "0",
                640,
                480,
                CalibrationCrop(0, 0, 640, 480),
            ).isEmpty(),
        )
    }

    @Test
    fun rotatedDistortionIsEquivalentToRotatedOriginalNormalizedRays() {
        val original = PixelPoint(0.12, -0.2)
        val distorted = profile.distortNormalized(original)!!
        val ninety = variants().first().intrinsics
        val twoSeventy = variants()[1].intrinsics
        val clockwise = profile.copy(distortion = ninety.distortion)
            .distortNormalized(PixelPoint(-original.y, original.x))!!
        val counterclockwise = profile.copy(distortion = twoSeventy.distortion)
            .distortNormalized(PixelPoint(original.y, -original.x))!!
        assertEquals(-distorted.y, clockwise.x, 1e-10)
        assertEquals(distorted.x, clockwise.y, 1e-10)
        assertEquals(distorted.y, counterclockwise.x, 1e-10)
        assertEquals(-distorted.x, counterclockwise.y, 1e-10)
    }

    @Test
    fun directPortraitProfileStillMatchesOnlyUnrotatedPortraitBuffer() {
        val result = LiveCalibrationValidator.assess(
            profile,
            "0",
            600,
            800,
            CalibrationCrop(0, 0, 600, 800),
        )
        assertEquals(LiveCalibrationState.GEOMETRY_COMPATIBLE_UNVERIFIED, result.state)
        assertTrue(result.rotatedCandidates.isEmpty())
        assertFalse(result.candidateIntrinsics == null)
    }
}
