package dev.mobile.slam.sensors

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.FastFeatureDetector
import dev.mobile.slam.frontend.Settings
import java.util.concurrent.Executor

internal data class FeaturePoint(val x: Float, val y: Float)

internal data class FrameKeypoints(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampNs: Long,
    val points: List<FeaturePoint>,
    val error: String? = null,
)

internal class CameraAnalyzer(
    settings: Settings,
    private val callbackExecutor: Executor,
    private val onFrame: (FrameKeypoints) -> Unit,
) : ImageAnalysis.Analyzer {
    private var detector: FastFeatureDetector? = null
    @Volatile
    private var openCvUnavailable = false

    override fun analyze(image: ImageProxy) {
        try {
            if (openCvUnavailable || !OpenCVLoader.initLocal()) {
                openCvUnavailable = true
                publish(
                    FrameKeypoints(
                        width = image.width,
                        height = image.height,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        timestampNs = image.imageInfo.timestamp,
                        points = emptyList(),
                        error = "Nie udało się uruchomić OpenCV.",
                    ),
                )
                return
            }

            val fastDetector = detector ?: FastFeatureDetector.create(settings.fastThreshold, true)
                .also { detector = it }
            val plane = image.planes.first()
            val buffer = plane.buffer.duplicate()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val sourceOffset = buffer.position()
            val pixels = ByteArray(image.width * image.height)
            for (row in 0 until image.height) {
                for (column in 0 until image.width) {
                    pixels[row * image.width + column] =
                        buffer.get(sourceOffset + row * rowStride + column * pixelStride)
                }
            }

            val grayscale = Mat(image.height, image.width, CvType.CV_8UC1)
            val keypoints = MatOfKeyPoint()
            try {
                grayscale.put(0, 0, pixels)
                fastDetector.detect(grayscale, keypoints)
                val points = keypoints.toArray().map { keypoint ->
                    FeaturePoint(keypoint.pt.x.toFloat(), keypoint.pt.y.toFloat())
                }
                publish(
                    FrameKeypoints(
                        width = image.width,
                        height = image.height,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        timestampNs = image.imageInfo.timestamp,
                        points = points,
                    ),
                )
            } finally {
                keypoints.release()
                grayscale.release()
            }
        } catch (_: Exception) {
            publish(
                FrameKeypoints(
                    width = image.width,
                    height = image.height,
                    rotationDegrees = image.imageInfo.rotationDegrees,
                    timestampNs = image.imageInfo.timestamp,
                    points = emptyList(),
                    error = "Nie udało się przetworzyć klatki.",
                ),
            )
        } catch (_: LinkageError) {
            openCvUnavailable = true
            publish(
                FrameKeypoints(
                    width = image.width,
                    height = image.height,
                    rotationDegrees = image.imageInfo.rotationDegrees,
                    timestampNs = image.imageInfo.timestamp,
                    points = emptyList(),
                    error = "Nie udało się uruchomić natywnego modułu OpenCV.",
                ),
            )
        } finally {
            image.close()
        }
    }

    private fun publish(frame: FrameKeypoints) {
        callbackExecutor.execute { onFrame(frame) }
    }
}
