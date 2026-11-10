package dev.mobile.slam.geometry

import dev.mobile.slam.calib.CalibrationCrop
import dev.mobile.slam.calib.CameraIntrinsics
import dev.mobile.slam.calib.MeasuredCalibration
import dev.mobile.slam.calib.PixelPoint
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Experimental, single-shot two-view reconstruction from matched *cropped* image coordinates.
 *
 * Calls require explicit independent confirmation of the physical optical path.
 * A compatible Camera2 ID or live-K candidate alone is NOT verification.
 * No automatic Live/CameraX connection or metric-scale claim is permitted.
 */
internal object TwoViewInitializer {
    fun initialize(
        calibration: MeasuredCalibration?,
        opticalPathVerified: Boolean,
        cameraId: String?,
        fullWidth: Int,
        fullHeight: Int,
        crop: CalibrationCrop,
        matches: List<TwoViewCorrespondence>,
        config: TwoViewConfig = TwoViewConfig(),
    ): TwoViewResult {
        fun rejected(reason: TwoViewFailure) = TwoViewResult.Rejected(reason, matches.size)
        if (calibration == null || !opticalPathVerified || cameraId.isNullOrBlank()) {
            return rejected(TwoViewFailure.UNVERIFIED_CALIBRATION)
        }
        val intrinsics = calibration.forAnalysis(cameraId, fullWidth, fullHeight, crop)
            ?: return rejected(TwoViewFailure.INCOMPATIBLE_GEOMETRY)
        TwoViewQualityGate.reject(matches, intrinsics, config)?.let { return rejected(it) }
        val corrected = matches.map { match ->
            val first = calibration.undistort(match.first, intrinsics)
                ?: return rejected(TwoViewFailure.UNDISTORTION_FAILED)
            val second = calibration.undistort(match.second, intrinsics)
                ?: return rejected(TwoViewFailure.UNDISTORTION_FAILED)
            first to second
        }
        return try {
            if (!OpenCVLoader.initLocal()) {
                rejected(TwoViewFailure.OPENCV_UNAVAILABLE)
            } else {
                estimateNative(matches, corrected, intrinsics, config)
            }
        } catch (_: Exception) {
            rejected(TwoViewFailure.INTERNAL_ERROR)
        } catch (_: LinkageError) {
            rejected(TwoViewFailure.OPENCV_UNAVAILABLE)
        }
    }

    private fun estimateNative(
        matches: List<TwoViewCorrespondence>,
        normalized: List<Pair<PixelPoint, PixelPoint>>,
        intrinsics: CameraIntrinsics,
        config: TwoViewConfig,
    ): TwoViewResult {
        val allocated = mutableListOf<Mat>()
        fun <T : Mat> own(value: T): T = value.also { allocated.add(it) }
        fun rejected(reason: TwoViewFailure, inliers: Int = 0): TwoViewResult = TwoViewResult.Rejected(
            reason,
            matches.size,
            inliers,
        )

        try {
            val first = own(
                MatOfPoint2f().apply {
    fromArray(*normalized.map { Point(it.first.x, it.first.y) }.toTypedArray())
                },
            )
            val second = own(
                MatOfPoint2f().apply {
    fromArray(*normalized.map { Point(it.second.x, it.second.y) }.toTypedArray())
                },
            )
            val mask = own(Mat())
            val focal = (intrinsics.fx + intrinsics.fy) / 2.0
            val essential = own(
                Calib3d.findEssentialMat(
                    first,
                    second,
                    1.0,
                    Point(0.0, 0.0),
                    Calib3d.RANSAC,
                    config.confidence,
                    config.ransacThresholdPx / focal,
                    2000,
                    mask,
                ),
            )
            if (essential.rows() != 3 || essential.cols() != 3 ||
                mask.total().toInt() != matches.size
            ) {
                return rejected(TwoViewFailure.ESSENTIAL_FAILED)
            }
            val r = own(Mat())
            val t = own(Mat())
            val posed = Calib3d.recoverPose(
                essential,
                first,
                second,
                r,
                t,
                1.0,
                Point(0.0, 0.0),
                mask,
            )
            if (posed < config.minInliers) {
                return rejected(TwoViewFailure.TOO_FEW_INLIERS, posed)
            }
            if (r.rows() != 3 || r.cols() != 3 || t.total() != 3L) {
                return rejected(TwoViewFailure.POSE_FAILED, posed)
            }
            val rotation = (0 until 3).flatMap { row ->
                (0 until 3).map { col -> r.get(row, col)[0] }
            }
            val translation = (0 until 3).map { index -> t.get(index, 0)[0] }
            val normT = sqrt(translation.sumOf { it * it })
            if (rotation.any { !it.isFinite() } ||
                translation.any { !it.isFinite() } ||
                !normT.isFinite() || normT < 0.9 || normT > 1.1
            ) {
                return rejected(TwoViewFailure.POSE_FAILED, posed)
            }
            val indices = matches.indices.filter { isInlier(mask, it) }
            if (indices.size < config.minInliers) {
                return rejected(TwoViewFailure.TOO_FEW_INLIERS, indices.size)
            }
            val pair1 = own(
                MatOfPoint2f().apply {
    fromArray(*indices.map { Point(normalized[it].first.x, normalized[it].first.y) }.toTypedArray())
                },
            )
            val pair2 = own(
                MatOfPoint2f().apply {
    fromArray(*indices.map { Point(normalized[it].second.x, normalized[it].second.y) }.toTypedArray())
                },
            )
            val p1 = own(Mat.zeros(3, 4, CvType.CV_64F))
            val p2 = own(Mat.zeros(3, 4, CvType.CV_64F))
            for (row in 0 until 3) {
                p1.put(row, row, 1.0)
                for (col in 0 until 3) p2.put(row, col, rotation[3 * row + col])
                p2.put(row, 3, translation[row])
            }
            val homogeneous = own(Mat())
            Calib3d.triangulatePoints(p1, p2, pair1, pair2, homogeneous)
            if (homogeneous.rows() != 4 || homogeneous.cols() != indices.size) {
                return rejected(TwoViewFailure.POSE_FAILED, indices.size)
            }
            // Camera 2 centre in the camera-1/world frame: C2 = -transpose(R21) * t21.
            val center2 = (0 until 3).map { col ->
                -(0 until 3).sumOf { row -> rotation[row * 3 + col] * translation[row] }
            }
            val cloud = mutableListOf<ReconstructedPoint>()
            for ((column, originalIndex) in indices.withIndex()) {
                val w = homogeneous.get(3, column)[0]
                if (!w.isFinite() || kotlin.math.abs(w) < 1e-9) continue
                val xyz = (0 until 3).map { homogeneous.get(it, column)[0] / w }
                if (xyz.any { !it.isFinite() } || xyz[2] <= 1e-6) continue
                val xyz2 = (0 until 3).map { row ->
                    (0 until 3).sumOf { col -> rotation[row * 3 + col] * xyz[col] } + translation[row]
                }
                if (!xyz2[2].isFinite() || xyz2[2] <= 1e-6) continue

                val point1 = normalized[originalIndex].first
                val point2 = normalized[originalIndex].second
                val e1 = hypot(
                    (xyz[0] / xyz[2] - point1.x) * intrinsics.fx,
                    (xyz[1] / xyz[2] - point1.y) * intrinsics.fy,
                )
                val e2 = hypot(
                    (xyz2[0] / xyz2[2] - point2.x) * intrinsics.fx,
                    (xyz2[1] / xyz2[2] - point2.y) * intrinsics.fy,
                )
                if (!e1.isFinite() || !e2.isFinite() ||
                    e1 > config.maxReprojectionPx || e2 > config.maxReprojectionPx
                ) {
                    continue
                }
                val secondRay = (0 until 3).map { xyz[it] - center2[it] }
                val firstNorm = sqrt(xyz.sumOf { it * it })
                val secondNorm = sqrt(secondRay.sumOf { it * it })
                if (firstNorm < 1e-9 || secondNorm < 1e-9) continue
                val cosine = (0 until 3).sumOf { xyz[it] * secondRay[it] } / (firstNorm * secondNorm)
                val parallaxDeg = Math.toDegrees(acos(cosine.coerceIn(-1.0, 1.0)))
                if (!parallaxDeg.isFinite()) continue
                cloud.add(
                    ReconstructedPoint(
                        trackId = matches[originalIndex].trackId,
                        x = xyz[0],
                        y = xyz[1],
                        z = xyz[2],
                        parallaxDeg = parallaxDeg,
                        reprojectionErrorPx = sqrt((e1 * e1 + e2 * e2) / 2.0),
                    ),
                )
            }
            if (cloud.size < config.minInliers) {
                return rejected(TwoViewFailure.CHEIRALITY_FAILED, cloud.size)
            }
            val angles = cloud.map { it.parallaxDeg }.sorted()
            val median = angles[angles.size / 2]
            if (median < config.minMedianParallaxDeg) {
                return rejected(TwoViewFailure.LOW_PARALLAX, cloud.size)
            }
            val rms = sqrt(cloud.sumOf { it.reprojectionErrorPx * it.reprojectionErrorPx } / cloud.size)
            if (!rms.isFinite() || rms > config.maxReprojectionPx) {
                return rejected(TwoViewFailure.REPROJECTION_FAILED, cloud.size)
            }
            return TwoViewResult.Initialized(
                pose = RelativePose(rotation, translation),
                cloud = cloud.toList(),
                inputCount = matches.size,
                inlierCount = cloud.size,
                medianParallaxDeg = median,
                rmsReprojectionPx = rms,
            )
        } finally {
            allocated.asReversed().forEach { it.release() }
        }
    }

    private fun isInlier(mask: Mat, index: Int): Boolean {
        val value = if (mask.rows() == 1) mask.get(0, index) else mask.get(index, 0)
        return value != null && value[0] != 0.0
    }
}
