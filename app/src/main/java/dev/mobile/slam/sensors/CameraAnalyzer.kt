package dev.mobile.slam.sensors

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import dev.mobile.slam.diagnostics.FrameObservation
import dev.mobile.slam.frontend.Settings
import dev.mobile.slam.frontend.FrameGeometry
import dev.mobile.slam.frontend.FrameOrderGate
import dev.mobile.slam.frontend.LatestSnapshotDispatcher
import dev.mobile.slam.frontend.PixelCrop
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
    val frameId: Long = 0L,
    val points: List<FeaturePoint>,
    val error: String? = null,
)

internal class CameraAnalyzer(
    private val settings: Settings,
    private val callbackExecutor: Executor,
    private val onObservation: ((FrameObservation) -> Unit)? = null,
    private val onFrame: (FrameKeypoints) -> Unit,
) : ImageAnalysis.Analyzer {
    private var detector: FastFeatureDetector? = null
    private var openCvInitialized = false
    private var openCvUnavailable = false
    private val frameOrder = FrameOrderGate()
    private val snapshots = LatestSnapshotDispatcher(callbackExecutor, onFrame)

    override fun analyze(image: ImageProxy) {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val cropRect = image.cropRect
        val crop = FrameCrop(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom)
        val yPlane = image.planes.firstOrNull()
        var acceptedFrameId = 0L
        try {
            val geometry = FrameGeometry(
                widthPx = image.width,
                heightPx = image.height,
                crop = PixelCrop(crop.left, crop.top, crop.right, crop.bottom),
                rotationDegrees = image.imageInfo.rotationDegrees,
            )
            val metadata = frameOrder.accept(image.imageInfo.timestamp, geometry) ?: return
            acceptedFrameId = metadata.frameId

            if (!openCvInitialized) {
                openCvInitialized = true
                openCvUnavailable = !OpenCVLoader.initLocal()
            }
            if (openCvUnavailable) {
                publish(errorFrame(image, crop, acceptedFrameId, "Nie udało się uruchomić OpenCV."))
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
                        timestampNs = metadata.timestampNs,
                        frameId = metadata.frameId,
                        points = points,
                    ),
                )
            } finally {
                keypoints.release()
                grayscale.release()
            }
        } catch (_: Exception) {
            publish(errorFrame(image, crop, acceptedFrameId, "Nie udało się przetworzyć klatki."))
        } catch (_: LinkageError) {
            openCvUnavailable = true
            publish(errorFrame(image, crop, acceptedFrameId, "Nie udało się uruchomić natywnego modułu OpenCV."))
        } finally {
            // Scalar-only diagnostic callback; a failure must never prevent ImageProxy.close().
            try {
                onObservation?.invoke(
                    FrameObservation(
                        timestampNs = image.imageInfo.timestamp,
                        widthPx = image.width,
                        heightPx = image.height,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        crop = "${crop.left},${crop.top},${crop.right},${crop.bottom}",
                        rowStrideBytes = yPlane?.rowStride,
                        pixelStrideBytes = yPlane?.pixelStride,
                        processingDurationNs = SystemClock.elapsedRealtimeNanos() - startedNs,
                    ),
                )
            } catch (_: Exception) {
                // Diagnostics must not break the teaching camera pipeline.
            } finally {
                image.close()
            }
        }
    }

    private fun errorFrame(image: ImageProxy, crop: FrameCrop, frameId: Long, error: String) = FrameKeypoints(
        width = image.width,
        height = image.height,
        rotationDegrees = image.imageInfo.rotationDegrees,
        crop = crop,
        timestampNs = image.imageInfo.timestamp,
        frameId = frameId,
        points = emptyList(),
        error = error,
    )

    private fun publish(frame: FrameKeypoints) {
        snapshots.publish(frame)
    }

    /**
     * Invoked after camera unbind on the analyzer's serial executor.
     * Current-frame OpenCV Mats are always released in analyze() finally.
     */
    fun close() {
        snapshots.close()
        detector?.clear()
        detector = null
    }
}
