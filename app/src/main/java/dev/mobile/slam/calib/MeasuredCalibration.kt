package dev.mobile.slam.calib

import kotlin.math.abs
import kotlin.math.hypot

/** Calibration must originate from a measured image sequence, never teaching defaults. */
internal enum class CalibrationProvenance {
    CHESSBOARD_MEASURED,
    VERIFIED_IMPORT,
}

/** Supported five-parameter Brown-Conrady model [k1, k2, p1, p2, k3]. */
internal enum class DistortionModel {
    BROWN_CONRADY_5,
}

/** Full-frame unrotated pixel coordinate, prior to crop and preview rotation. */
internal data class PixelPoint(val x: Double, val y: Double) {
    init {
        require(x.isFinite() && y.isFinite())
    }
}

/** Exclusive crop bounds in the unrotated analysis image. */
internal data class CalibrationCrop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun validFor(width: Int, height: Int): Boolean =
        left >= 0 && top >= 0 && right > left && bottom > top && right <= width && bottom <= height
}

/**
 * Intrinsics measured at reference full-frame dimensions for one physical camera.
 * The reprojection RMS is in reference-image pixels and is recorded, not assumed.
 * Crop bounds describe the calibration image region used during estimation.
 * Camera2 metadata alone is not sufficient provenance for this type.
 */
internal data class MeasuredCalibration(
    val cameraId: String,
    val referenceWidth: Int,
    val referenceHeight: Int,
    val referenceCrop: CalibrationCrop,
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val distortion: List<Double>,
    val rmsReprojectionPx: Double,
    val provenance: CalibrationProvenance,
    val distortionModel: DistortionModel = DistortionModel.BROWN_CONRADY_5,
    val focusDistanceDiopters: Double? = null,
) {
    init {
        require(cameraId.isNotBlank())
        require(referenceWidth > 0 && referenceHeight > 0)
        require(referenceCrop.validFor(referenceWidth, referenceHeight))
        require(listOf(fx, fy, cx, cy).all { it.isFinite() })
        require(fx > 0.0 && fy > 0.0)
        require(cx >= 0.0 && cx < referenceWidth && cy >= 0.0 && cy < referenceHeight)
        require(distortion.size == 5 && distortion.all { it.isFinite() })
        require(rmsReprojectionPx.isFinite() && rmsReprojectionPx >= 0.0)
        require(focusDistanceDiopters == null || (focusDistanceDiopters.isFinite() && focusDistanceDiopters >= 0.0))
    }

    /**
     * Only uniform full-frame scaling is supported. A changed camera, lens/crop
     * pipeline or nonuniform aspect ratio must be recalibrated independently.
     * The resulting principal point is relative to the requested crop.
     */
    fun forAnalysis(
        activeCameraId: String,
        fullWidth: Int,
        fullHeight: Int,
        crop: CalibrationCrop,
    ): CameraIntrinsics? {
        if (activeCameraId != cameraId || fullWidth <= 0 || fullHeight <= 0 ||
            !crop.validFor(fullWidth, fullHeight)
        ) {
            return null
        }
        val sx = fullWidth.toDouble() / referenceWidth
        val sy = fullHeight.toDouble() / referenceHeight
        if (abs(sx - sy) > 1e-6 * maxOf(sx, sy)) return null
        // A profile calibrated on a subcrop cannot be applied to an unrelated
        // CameraX full-frame stream without a verified sensor-coordinate transform.
        if (referenceCrop != CalibrationCrop(0, 0, referenceWidth, referenceHeight)) return null
        return CameraIntrinsics(
            fx = fx * sx,
            fy = fy * sy,
            cx = cx * sx - crop.left,
            cy = cy * sy - crop.top,
            distortion = distortion.toList(),
            imageWidth = crop.width,
            imageHeight = crop.height,
        )
    }

    /**
     * Iteratively inverts Brown-Conrady distortion in normalized camera coordinates.
     * Input is in the cropped image coordinates of the derived intrinsics.
     * Returns null if the inverse is singular or fails to converge.
     */
    fun undistort(pixel: PixelPoint, intrinsics: CameraIntrinsics): PixelPoint? {
        if (intrinsics.distortion.size != 5 || intrinsics.fx <= 0.0 || intrinsics.fy <= 0.0) return null
        val xd = (pixel.x - intrinsics.cx) / intrinsics.fx
        val yd = (pixel.y - intrinsics.cy) / intrinsics.fy
        if (!xd.isFinite() || !yd.isFinite()) return null
        val (k1, k2, p1, p2, k3) = intrinsics.distortion
        var x = xd
        var y = yd
        repeat(30) {
            val r2 = x * x + y * y
            val radial = 1.0 + k1 * r2 + k2 * r2 * r2 + k3 * r2 * r2 * r2
            if (!radial.isFinite() || abs(radial) < 1e-10) return null
            val dx = 2.0 * p1 * x * y + p2 * (r2 + 2.0 * x * x)
            val dy = p1 * (r2 + 2.0 * y * y) + 2.0 * p2 * x * y
            val nextX = (xd - dx) / radial
            val nextY = (yd - dy) / radial
            if (!nextX.isFinite() || !nextY.isFinite()) return null
            x = nextX
            y = nextY
            val projected = distortNormalized(PixelPoint(x, y))
            if (projected != null && hypot(projected.x - xd, projected.y - yd) < 1e-9) {
                return PixelPoint(x, y)
            }
        }
        return null
    }

    /** Forward distortion of an undistorted normalized camera point. */
    fun distortNormalized(point: PixelPoint): PixelPoint? {
        val (k1, k2, p1, p2, k3) = distortion
        val r2 = point.x * point.x + point.y * point.y
        val radial = 1.0 + k1 * r2 + k2 * r2 * r2 + k3 * r2 * r2 * r2
        val x = point.x * radial + 2.0 * p1 * point.x * point.y + p2 * (r2 + 2.0 * point.x * point.x)
        val y = point.y * radial + p1 * (r2 + 2.0 * point.y * point.y) + 2.0 * p2 * point.x * point.y
        return if (x.isFinite() && y.isFinite()) PixelPoint(x, y) else null
    }
}
