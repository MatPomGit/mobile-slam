package dev.mobile.slam.calib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LiveCalibrationValidatorTest {
    private val profile = MeasuredCalibration(
        cameraId = "0",
        referenceWidth = 1280,
        referenceHeight = 960,
        referenceCrop = CalibrationCrop(0, 0, 1280, 960),
        fx = 1020.0,
        fy = 1000.0,
        cx = 640.0,
        cy = 480.0,
        distortion = listOf(-0.1, 0.01, 0.001, -0.001, 0.003),
        rmsReprojectionPx = 0.4,
        provenance = CalibrationProvenance.CHESSBOARD_MEASURED,
    )

    @Test
    fun missingProfileNeverProvidesIntrinsicCandidate() {
        val assessment = LiveCalibrationValidator.assess(
            null,
            "0",
            640,
            480,
            CalibrationCrop(0, 0, 640, 480),
        )
        assertEquals(LiveCalibrationState.NOT_CALIBRATED, assessment.state)
        assertNull(assessment.candidateIntrinsics)
    }

    @Test
    fun cameraIdentityMustBeConfirmedBeforeGeometry() {
        val unknown = LiveCalibrationValidator.assess(
            profile,
            null,
            640,
            480,
            CalibrationCrop(0, 0, 640, 480),
        )
        assertEquals(LiveCalibrationState.CAMERA_ID_UNKNOWN, unknown.state)
        assertNull(unknown.candidateIntrinsics)

        val wrong = LiveCalibrationValidator.assess(
            profile,
            "1",
            640,
            480,
            CalibrationCrop(0, 0, 640, 480),
        )
        assertEquals(LiveCalibrationState.CAMERA_MISMATCH, wrong.state)
        assertNull(wrong.candidateIntrinsics)
    }

    @Test
    fun waitsForObservedFrameInsteadOfRequestedResolution() {
        val assessment = LiveCalibrationValidator.assess(profile, "0", null, null, null)
        assertEquals(LiveCalibrationState.WAITING_FOR_FRAME, assessment.state)
        assertNull(assessment.candidateIntrinsics)
    }

    @Test
    fun differentAspectRatioOrInvalidCropCannotActivateCandidate() {
        val aspect = LiveCalibrationValidator.assess(
            profile,
            "0",
            640,
            360,
            CalibrationCrop(0, 0, 640, 360),
        )
        assertEquals(LiveCalibrationState.GEOMETRY_MISMATCH, aspect.state)
        assertNull(aspect.candidateIntrinsics)
        val crop = LiveCalibrationValidator.assess(
            profile,
            "0",
            640,
            480,
            CalibrationCrop(0, 0, 641, 480),
        )
        assertEquals(LiveCalibrationState.GEOMETRY_MISMATCH, crop.state)
        assertNull(crop.candidateIntrinsics)
    }

    @Test
    fun scaledCompatibleCandidateRemainsExplicitlyUnverified() {
        val assessment = LiveCalibrationValidator.assess(
            profile,
            "0",
            640,
            480,
            CalibrationCrop(20, 10, 620, 470),
        )
        assertEquals(LiveCalibrationState.GEOMETRY_COMPATIBLE_UNVERIFIED, assessment.state)
        val k = assessment.candidateIntrinsics
        assertNotNull(k)
        assertEquals(510.0, k!!.fx, 1e-9)
        assertEquals(500.0, k.fy, 1e-9)
        assertEquals(300.0, k.cx, 1e-9)
        assertEquals(230.0, k.cy, 1e-9)
        assertEquals(600, k.imageWidth)
        assertEquals(460, k.imageHeight)
        assertEquals(profile.distortion, k.distortion)
    }
}
