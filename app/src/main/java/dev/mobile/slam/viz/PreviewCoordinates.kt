package dev.mobile.slam.viz

import dev.mobile.slam.sensors.FeaturePoint
import dev.mobile.slam.sensors.FrameCrop

/**
 * Projects raw Y-plane coordinates through the CameraX crop rectangle and rotation into
 * the matching FILL_CENTER PreviewView. Preview and analysis must share a UseCaseGroup ViewPort.
 */
internal object PreviewCoordinates {
    fun project(
        point: FeaturePoint,
        crop: FrameCrop,
        rotationDegrees: Int,
        previewWidth: Int,
        previewHeight: Int,
    ): FeaturePoint? {
        val width = crop.right - crop.left
        val height = crop.bottom - crop.top
        if (width <= 0 || height <= 0 || previewWidth <= 0 || previewHeight <= 0) return null
        if (point.x < crop.left || point.x > crop.right || point.y < crop.top || point.y > crop.bottom) {
            return null
        }
        val u = (point.x - crop.left) / width
        val v = (point.y - crop.top) / height
        val (x, y) = when (rotationDegrees.mod(360)) {
            0 -> u to v
            90 -> (1f - v) to u
            180 -> (1f - u) to (1f - v)
            270 -> v to (1f - u)
            else -> return null
        }
        return FeaturePoint(x * previewWidth, y * previewHeight)
    }
}
