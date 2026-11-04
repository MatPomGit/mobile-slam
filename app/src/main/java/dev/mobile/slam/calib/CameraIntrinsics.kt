package dev.mobile.slam.calib

/**
 * Pinhole camera parameters used to map camera-space points into pixels:
 * u = fx * X/Z + cx and v = fy * Y/Z + cy, with distortion coefficients applied separately.
 */
data class CameraIntrinsics(
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val distortion: List<Double>,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    companion object {
        /**
         * Returns a teaching-only estimate for the initial 640x480 CameraX stream.
         * These values are not a substitute for calibration of a device camera.
         */
        fun default640x480() = CameraIntrinsics(
            fx = 500.0,
            fy = 500.0,
            cx = 320.0,
            cy = 240.0,
            distortion = listOf(0.0, 0.0, 0.0, 0.0, 0.0),
            imageWidth = 640,
            imageHeight = 480,
        )
    }
}
