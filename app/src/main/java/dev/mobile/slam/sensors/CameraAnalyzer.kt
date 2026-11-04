package dev.mobile.slam.sensors

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import dev.mobile.slam.frontend.Settings
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.FastFeatureDetector
import java.util.concurrent.Executor

internal data class FeaturePoint(val x: Float, val y: Float)

internal data class FrameCrop(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal data class FrameKeypoints(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val crop: FrameCrop,
    val timestampNs: Long,
    val points: List<FeaturePoint>,
    val error: String? = null,
)

internal class CameraAnalyzer(
    private val settings: Settings,
    private val callbackExecutor: Executor,
    private val onFrame: (FrameKeypoints) -> Unit,
) : ImageAnalysis.Analyzer {
    private var detector: FastFeatureDetector? = null
    private var openCvInitialized = false
    private var openCvUnavailable = false

    override fun analyze(image: ImageProxy) {
        val cropRect = image.cropRect
        val crop = FrameCrop(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom)
        try {
            if (!openCvInitialized) {
                openCvInitialized = true
                openCvUnavailable = !OpenCVLoader.initLocal()
            }
            if (openCvUnavailable) {
                publish(errorFrame(image, crop, "Nie udało się uruchomić OpenCV."))
                return
            }

            val fastDetector = detector ?: FastFeatureDetector.create(settings.fastThreshold, true)
                .also { detector = it }
            val plane = image.planes.first()
            val pixels = LumaPlane.copy(
                source = plane.buffer,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
            )

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
                        crop = crop,
                        timestampNs = image.imageInfo.timestamp,
                        points = points,
                    ),
                )
            } finally {
                keypoints.release()
                grayscale.release()
            }
        } catch (_: Exception) {
            publish(errorFrame(image, crop, "Nie udało się przetworzyć klatki."))
        } catch (_: LinkageError) {
            openCvUnavailable = true
            publish(errorFrame(image, crop, "Nie udało się uruchomić natywnego modułu OpenCV."))
        } finally {
            image.close()
        }
    }

    private fun errorFrame(image: ImageProxy, crop: FrameCrop, error: String) = FrameKeypoints(
            width = image.width,
            height = image.height,
            rotationDegrees = image.imageInfo.rotationDegrees,
            crop = crop,
            timestampNs = image.imageInfo.timestamp,
            points = emptyList(),
            error = error,
        )

    private fun publish(frame: FrameKeypoints) {
        callbackExecutor.execute { onFrame(frame) }
    }
}
