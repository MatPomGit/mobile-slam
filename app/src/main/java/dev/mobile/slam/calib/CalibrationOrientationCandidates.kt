package dev.mobile.slam.calib

import kotlin.math.abs

/**
 * Geometry-only alternatives when a portrait calibration image may correspond to
 * a landscape unrotated ImageProxy. The EXIF-to-sensor transform is unknown.
 *
 * Both rotations are always kept as separate, UNVERIFIED candidates. Neither can
 * be used for pose estimation without independent image-coordinate validation.
 */
internal data class OrientationIntrinsicsCandidate(
    val clockwiseDegrees: Int,
    val intrinsics: CameraIntrinsics,
)

internal object CalibrationOrientationCandidates {
    fun forAnalysis(
        profile: MeasuredCalibration,
        cameraId: String,
        width: Int,
        height: Int,
        crop: CalibrationCrop,
    ): List<OrientationIntrinsicsCandidate> {
        if (profile.cameraId != cameraId || width <= 0 || height <= 0 ||
            !crop.validFor(width, height) ||
            profile.referenceCrop != CalibrationCrop(0, 0, profile.referenceWidth, profile.referenceHeight)
        ) {
            return emptyList()
        }
        // A quarter-turn swaps the axes of the measured, EXIF-oriented image.
        val scaleX = width.toDouble() / profile.referenceHeight
        val scaleY = height.toDouble() / profile.referenceWidth
        if (abs(scaleX - scaleY) > 1e-6 * maxOf(scaleX, scaleY)) return emptyList()

        val (k1, k2, p1, p2, k3) = profile.distortion
        return listOf(90, 270).map { clockwise ->
            val centerX = if (clockwise == 90) {
                profile.referenceHeight - 1.0 - profile.cy
            } else {
                profile.cy
            }
            val centerY = if (clockwise == 90) {
                profile.cx
            } else {
                profile.referenceWidth - 1.0 - profile.cx
            }
            // Brown-Conrady tangential terms rotate with the normalized x/y axes.
            val transformedP1 = if (clockwise == 90) p2 else -p2
            val transformedP2 = if (clockwise == 90) -p1 else p1
            OrientationIntrinsicsCandidate(
                clockwiseDegrees = clockwise,
                intrinsics = CameraIntrinsics(
                    fx = profile.fy * scaleX,
                    fy = profile.fx * scaleY,
                    cx = centerX * scaleX - crop.left,
                    cy = centerY * scaleY - crop.top,
                    distortion = listOf(k1, k2, transformedP1, transformedP2, k3),
                    imageWidth = crop.width,
                    imageHeight = crop.height,
                ),
            )
        }
    }
}
