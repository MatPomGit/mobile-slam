package dev.mobile.slam.sensors

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mobile.slam.frontend.FeatureStatus
import dev.mobile.slam.frontend.FrameGeometry
import dev.mobile.slam.frontend.FrameMetadata
import dev.mobile.slam.frontend.PixelCrop
import dev.mobile.slam.frontend.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Native Android OpenCV smoke test with two synthetic translated image frames.
 * This test is compiled in CI but requires a device/emulator to execute.
 */
@RunWith(AndroidJUnit4::class)
class KltFeatureTrackerTest {
    @Test
    fun preservesSomeFeatureIdsAcrossSmallImageTranslation() {
        assertTrue(OpenCVLoader.initLocal())
        val firstImage = Mat.zeros(160, 160, CvType.CV_8UC1)
        val secondImage = Mat.zeros(160, 160, CvType.CV_8UC1)
        val tracker = KltFeatureTracker(Settings())
        try {
            val seeds = mutableListOf<FeaturePoint>()
            for (y in listOf(20, 60, 100)) {
                for (x in listOf(20, 60, 100)) {
                    Imgproc.rectangle(
                        firstImage, Point(x.toDouble(), y.toDouble()),
                        Point((x + 16).toDouble(), (y + 16).toDouble()), Scalar(255.0), -1,
                    )
                    Imgproc.rectangle(
                        secondImage, Point((x + 2).toDouble(), (y + 1).toDouble()),
                        Point((x + 18).toDouble(), (y + 17).toDouble()), Scalar(255.0), -1,
                    )
                    seeds.add(FeaturePoint(x.toFloat(), y.toFloat()))
                }
            }
            val geometry = FrameGeometry(160, 160, PixelCrop(0, 0, 160, 160), 0)
            val first = tracker.process(firstImage, FrameMetadata(1L, 1L, 1_000_000_000L, geometry), seeds)
            assertTrue(first.tracks.isNotEmpty())
            val second = tracker.process(
                secondImage,
                FrameMetadata(2L, 1L, 1_033_333_333L, geometry),
                emptyList(),
            )
            assertTrue(second.metrics.tracked > 0)
            assertEquals(0, second.metrics.detected)
            assertTrue(second.tracks.any { it.status == FeatureStatus.TRACKED && it.ageFrames == 2 })
            assertTrue(first.tracks.map { it.id }.toSet().containsAll(second.tracks.map { it.id }))
        } finally {
            tracker.close()
            firstImage.release()
            secondImage.release()
        }
    }
}
