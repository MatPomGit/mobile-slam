package dev.mobile.slam.sensors

import dev.mobile.slam.frontend.FeatureTrack
import dev.mobile.slam.frontend.FlowEstimate
import dev.mobile.slam.frontend.FrameMetadata
import dev.mobile.slam.frontend.ImageFeature
import dev.mobile.slam.frontend.Settings
import dev.mobile.slam.frontend.TrackManager
import dev.mobile.slam.frontend.TrackResult
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.video.Video
import kotlin.math.hypot

/**
 * Pyramidal sparse Lucas-Kanade optical flow on consecutive unrotated grayscale frames.
 *
 * The analyzer's single serial thread owns both the current image and the previous
 * cloned Mat. No native pointers cross threads. Forward/backward checks validate
 * correspondences in pixel coordinates, not pose, map structure or scene scale.
 */
internal class KltFeatureTracker(private val settings: Settings) : AutoCloseable {
    private val manager = TrackManager(settings)
    private var previousGray: Mat? = null
    private var previousSegmentId: Long? = null
    private var previousTimestampNs: Long? = null

    fun process(current: Mat, frame: FrameMetadata, detected: List<FeaturePoint>): TrackResult {
        val previousTime = previousTimestampNs
        if (previousSegmentId != frame.segmentId ||
            previousTime == null ||
            frame.timestampNs - previousTime > settings.kltMaxGapMs * 1_000_000L
        ) {
            reset()
        }
        return try {
            val previous = previousGray
            val estimates = if (previous != null && manager.previousTracks().isNotEmpty()) {
                opticalFlow(previous, current, manager.previousTracks())
            } else {
                emptyList()
            }
            val result = manager.update(
                frame,
                estimates,
                detected.map { ImageFeature(it.x, it.y) },
            )
            val nextReference = current.clone()
            previousGray?.release()
            previousGray = nextReference
            previousSegmentId = frame.segmentId
            previousTimestampNs = frame.timestampNs
            result
        } catch (error: RuntimeException) {
            reset()
            throw error
        }
    }

    private fun opticalFlow(previous: Mat, current: Mat, tracks: List<FeatureTrack>): List<FlowEstimate> {
        val prev = MatOfPoint2f()
        val next = MatOfPoint2f()
        val forwardStatus = MatOfByte()
        val forwardError = MatOfFloat()
        val backward = MatOfPoint2f()
        val backwardStatus = MatOfByte()
        val backwardError = MatOfFloat()
        try {
            prev.fromArray(*tracks.map { Point(it.current.xPx.toDouble(), it.current.yPx.toDouble()) }.toTypedArray())
            val size = Size(settings.kltWindowSize.toDouble(), settings.kltWindowSize.toDouble())
            val criteria = TermCriteria(TermCriteria.COUNT or TermCriteria.EPS, 30, 0.01)
            Video.calcOpticalFlowPyrLK(
                previous, current, prev, next, forwardStatus, forwardError,
                size, settings.kltMaxLevel, criteria, 0, 1e-4,
            )
            Video.calcOpticalFlowPyrLK(
                current, previous, next, backward, backwardStatus, backwardError,
                size, settings.kltMaxLevel, criteria, 0, 1e-4,
            )
            val nextPositions = next.toArray()
            val backwardPositions = backward.toArray()
            val previousPositions = prev.toArray()
            val forwardFlags = forwardStatus.toArray()
            val backwardFlags = backwardStatus.toArray()
            return tracks.indices.map { index ->
                val forwardOk = index < forwardFlags.size && forwardFlags[index].toInt() != 0 &&
                    index < nextPositions.size
                val reverseOk = index < backwardFlags.size && backwardFlags[index].toInt() != 0 &&
                    index < backwardPositions.size
                val newPosition = if (forwardOk) nextPositions[index] else previousPositions[index]
                val returned = if (reverseOk) backwardPositions[index] else previousPositions[index]
                val residual = hypot(
                    previousPositions[index].x - returned.x,
                    previousPositions[index].y - returned.y,
                ).toFloat()
                FlowEstimate(
                    trackId = tracks[index].id,
                    next = ImageFeature(newPosition.x.toFloat(), newPosition.y.toFloat()),
                    forwardBackwardErrorPx = residual,
                    forwardValid = forwardOk,
                    backwardValid = reverseOk,
                )
            }
        } finally {
            prev.release()
            next.release()
            forwardStatus.release()
            forwardError.release()
            backward.release()
            backwardStatus.release()
            backwardError.release()
        }
    }

    private fun reset() {
        previousGray?.release()
        previousGray = null
        previousTimestampNs = null
        previousSegmentId = null
        manager.reset()
    }

    override fun close() {
        reset()
    }
}
