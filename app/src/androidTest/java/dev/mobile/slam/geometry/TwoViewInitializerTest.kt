package dev.mobile.slam.geometry

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mobile.slam.calib.CalibrationCrop
import dev.mobile.slam.calib.CalibrationProvenance
import dev.mobile.slam.calib.MeasuredCalibration
import dev.mobile.slam.calib.PixelPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic known-pose test. CI compiles this test APK; execution requires Android OpenCV
 * on an emulator/device and does not constitute optical hardware acceptance.
 */
@RunWith(AndroidJUnit4::class)
class TwoViewInitializerTest {
    private val calibration = MeasuredCalibration(
        cameraId = "0",
        referenceWidth = 1280,
        referenceHeight = 960,
        referenceCrop = CalibrationCrop(0, 0, 1280, 960),
        fx = 800.0,
        fy = 800.0,
        cx = 640.0,
        cy = 480.0,
        distortion = List(5) { 0.0 },
        rmsReprojectionPx = 0.25,
        provenance = CalibrationProvenance.CHESSBOARD_MEASURED,
    )

    @Test
    fun recoversSyntheticRelativePoseAndSparsePointsWithOutliers() {
        val random = Random(20261110)
        val angle = Math.toRadians(3.0)
        val c = cos(angle)
        val s = sin(angle)
        val t = doubleArrayOf(0.6, 0.03, 0.02)
        val points = (0 until 11).flatMap { row ->
            (0 until 13).map { column ->
                val x = (column - 6) * 0.24
                val y = (row - 5) * 0.21
                val z = 4.0 + (row * 3 + column * 7) % 13 * 0.19
                doubleArrayOf(x, y, z)
            }
        }
        val matches = points.mapIndexed { index, xyz ->
            val x = xyz[0]
            val y = xyz[1]
            val z = xyz[2]
            val x2 = c * x + s * z + t[0]
            val y2 = y + t[1]
            val z2 = -s * x + c * z + t[2]
            val jitter1x = (random.nextDouble() - 0.5) * 0.35
            val jitter1y = (random.nextDouble() - 0.5) * 0.35
            val jitter2x = (random.nextDouble() - 0.5) * 0.35
            val jitter2y = (random.nextDouble() - 0.5) * 0.35
            TwoViewCorrespondence(
                trackId = (index + 1).toLong(),
                first = PixelPoint(800.0 * x / z + 640.0 + jitter1x, 800.0 * y / z + 480.0 + jitter1y),
                second = if (index % 13 == 0) {
                    PixelPoint(150.0 + random.nextDouble() * 980.0, 120.0 + random.nextDouble() * 700.0)
                } else {
                    PixelPoint(800.0 * x2 / z2 + 640.0 + jitter2x, 800.0 * y2 / z2 + 480.0 + jitter2y)
                },
            )
        }

        val result = TwoViewInitializer.initialize(
            calibration = calibration,
            opticalPathVerified = true, // Synthetic reference has explicitly known matching optics.
            cameraId = "0",
            fullWidth = 1280,
            fullHeight = 960,
            crop = CalibrationCrop(0, 0, 1280, 960),
            matches = matches,
        )
        assertTrue("Unexpected result: $result", result is TwoViewResult.Initialized)
        val model = result as TwoViewResult.Initialized
        assertTrue(model.cloud.size >= 55)
        assertTrue(model.medianParallaxDeg > 1.0)
        assertTrue(model.rmsReprojectionPx < 2.0)
        assertTrue(model.arbitraryScale)
        val direction = model.pose.translationUnit
        val dot = (direction[0] * t[0] + direction[1] * t[1] + direction[2] * t[2]) /
            kotlin.math.sqrt(t.sumOf { it * it })
        assertTrue("Incorrect translation direction: $dot", dot > 0.8)
        assertEquals(model.cloud.size, model.cloud.map { it.trackId }.toSet().size)
    }

    @Test
    fun zeroMotionAndUnverifiedOpticsNeverInitialize() {
        val stationary = (0 until 80).map { index ->
            val x = 80.0 + (index % 10) * 100
            val y = 70.0 + (index / 10) * 100
            TwoViewCorrespondence(index.toLong() + 1L, PixelPoint(x, y), PixelPoint(x, y))
        }
        val unverified = TwoViewInitializer.initialize(
            calibration,
            false,
            "0",
            1280,
            960,
            CalibrationCrop(0, 0, 1280, 960),
            stationary,
        )
        assertEquals(
            TwoViewFailure.UNVERIFIED_CALIBRATION,
            (unverified as TwoViewResult.Rejected).reason,
        )
        val stationaryResult = TwoViewInitializer.initialize(
            calibration,
            true,
            "0",
            1280,
            960,
            CalibrationCrop(0, 0, 1280, 960),
            stationary,
        )
        assertEquals(TwoViewFailure.LOW_MOTION, (stationaryResult as TwoViewResult.Rejected).reason)
    }
}
