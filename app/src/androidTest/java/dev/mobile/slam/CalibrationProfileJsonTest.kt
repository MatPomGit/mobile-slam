package dev.mobile.slam

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mobile.slam.calib.CalibrationCrop
import dev.mobile.slam.calib.CalibrationProfileJson
import dev.mobile.slam.calib.CalibrationProvenance
import dev.mobile.slam.calib.MeasuredCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalibrationProfileJsonTest {
    private val profile = MeasuredCalibration(
        cameraId = "0",
        referenceWidth = 1020,
        referenceHeight = 765,
        referenceCrop = CalibrationCrop(0, 0, 1020, 765),
        fx = 707.0,
        fy = 705.0,
        cx = 506.0,
        cy = 387.0,
        distortion = listOf(0.066, -0.113, 0.0, 0.0, 0.065),
        rmsReprojectionPx = 0.47,
        provenance = CalibrationProvenance.CHESSBOARD_MEASURED,
    )

    @Test
    fun preservesMeasuredCameraGeometryAndProvenance() {
        val result = CalibrationProfileJson.decode(CalibrationProfileJson.encode(profile))
        assertEquals(profile, result)
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val source = CalibrationProfileJson.encode(profile)
        val tampered = source.replace("\"schema_version\": 1", "\"schema_version\": 2")
        assertThrows(IllegalArgumentException::class.java) {
            CalibrationProfileJson.decode(tampered)
        }
    }
}
