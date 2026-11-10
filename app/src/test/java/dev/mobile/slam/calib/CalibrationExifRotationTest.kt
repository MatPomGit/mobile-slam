package dev.mobile.slam.calib

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationExifRotationTest {
    @Test
    fun quarterTurnsAreSupported() {
        assertEquals(0, CalibrationExifRotation.degrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, CalibrationExifRotation.degrees(ExifInterface.ORIENTATION_UNDEFINED))
        assertEquals(90, CalibrationExifRotation.degrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, CalibrationExifRotation.degrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, CalibrationExifRotation.degrees(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun mirroredOrientationsAreNotSilentlyApplied() {
        assertNull(CalibrationExifRotation.degrees(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertNull(CalibrationExifRotation.degrees(ExifInterface.ORIENTATION_TRANSVERSE))
    }
}
