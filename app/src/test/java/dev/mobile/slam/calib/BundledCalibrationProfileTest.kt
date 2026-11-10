package dev.mobile.slam.calib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression test for the exact user-supplied measured calibration (not demo intrinsics). */
class BundledCalibrationProfileTest {
    private val profile = BundledCalibrationProfile.measured

    @Test
    fun containsTheExactMeasuredCameraProfile() {
        assertEquals("0", profile.cameraId)
        assertEquals(750, profile.referenceWidth)
        assertEquals(1000, profile.referenceHeight)
        assertEquals(CalibrationCrop(0, 0, 750, 1000), profile.referenceCrop)
        assertEquals(703.0517873666174, profile.fx, 0.0)
        assertEquals(701.6570335584057, profile.fy, 0.0)
        assertEquals(380.0172550637061, profile.cx, 0.0)
        assertEquals(500.5981210229705, profile.cy, 0.0)
        assertEquals(
            listOf(
                0.11424208067093562,
                -0.2456484575699898,
                8.216670123752652E-4,
                0.006777393441683128,
                0.2172513995442215,
            ),
            profile.distortion,
        )
        assertEquals(0.4875927303103622, profile.rmsReprojectionPx, 0.0)
        assertEquals(CalibrationProvenance.CHESSBOARD_MEASURED, profile.provenance)
        assertEquals(DistortionModel.BROWN_CONRADY_5, profile.distortionModel)
    }

    @Test
    fun defaultDoesNotPretendToMatchAnotherPhysicalCamera() {
        assertNull(profile.forAnalysis("1", 750, 1000, CalibrationCrop(0, 0, 750, 1000)))
    }

    @Test
    fun landscapeBufferRequiresExplicitOrientationValidation() {
        val snapshot = LiveCalibrationValidator.assess(
            profile,
            "0",
            640,
            480,
            CalibrationCrop(0, 0, 640, 480),
        )
        assertEquals(LiveCalibrationState.ORIENTATION_AMBIGUOUS, snapshot.state)
        assertNull(snapshot.candidateIntrinsics)
        assertEquals(listOf(90, 270), snapshot.rotatedCandidates.map { it.clockwiseDegrees })
    }

    @Test
    fun calibratedPortraitBufferUsesScaledMeasuredValues() {
        val k = profile.forAnalysis("0", 600, 800, CalibrationCrop(0, 0, 600, 800))!!
        assertEquals(profile.fx * 0.8, k.fx, 1e-9)
        assertEquals(profile.fy * 0.8, k.fy, 1e-9)
        assertEquals(profile.distortion, k.distortion)
        assertFalse(k.fx == 500.0)
    }

    @Test
    fun bundledFallbackIsExplicitlyDifferentFromStoredProfile() {
        val bundled = CalibrationStorageSnapshot(profile, bundledDefault = true)
        val saved = CalibrationStorageSnapshot(profile.copy(fx = 710.0))
        assertTrue(bundled.bundledDefault)
        assertFalse(saved.bundledDefault)
    }
}
