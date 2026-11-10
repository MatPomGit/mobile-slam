package dev.mobile.slam.geometry

import dev.mobile.slam.calib.CameraIntrinsics
import dev.mobile.slam.calib.PixelPoint
import kotlin.math.hypot

/** Pixel coordinates are measured relative to the SAME unrotated analysis crop in both frames. */
internal data class TwoViewCorrespondence(
    val trackId: Long,
    val first: PixelPoint,
    val second: PixelPoint,
)

internal data class TwoViewConfig(
    val minMatches: Int = 30,
    val minInliers: Int = 20,
    val maxMatches: Int = 2000,
    val minOccupiedCells: Int = 6,
    val gridColumns: Int = 4,
    val gridRows: Int = 3,
    val minMedianMotionPx: Double = 1.0,
    val minMedianParallaxDeg: Double = 1.0,
    val ransacThresholdPx: Double = 1.5,
    val maxReprojectionPx: Double = 3.0,
    val confidence: Double = 0.999,
) {
    init {
        require(minMatches in 8..maxMatches && maxMatches in 8..5000)
        require(minInliers in 8..minMatches)
        require(gridColumns in 2..16 && gridRows in 2..16)
        require(minOccupiedCells in 2..gridColumns * gridRows)
        require(minMedianMotionPx.isFinite() && minMedianMotionPx > 0.0)
        require(minMedianParallaxDeg.isFinite() && minMedianParallaxDeg in 0.1..20.0)
        require(ransacThresholdPx.isFinite() && ransacThresholdPx in 0.1..10.0)
        require(maxReprojectionPx.isFinite() && maxReprojectionPx in 0.1..10.0)
        require(confidence > 0.5 && confidence < 1.0)
    }
}

internal enum class TwoViewFailure(val label: String) {
    UNVERIFIED_CALIBRATION("Brak potwierdzonej kalibracji optycznej"),
    INCOMPATIBLE_GEOMETRY("Niezgodna geometria klatki lub kamery"),
    TOO_FEW_MATCHES("Zbyt mało korespondencji"),
    TOO_MANY_MATCHES("Przekroczony limit korespondencji"),
    DUPLICATE_IDS("Powtarzające się identyfikatory cech"),
    INVALID_COORDINATES("Punkty poza obrazem lub nieprawidłowe parametry K"),
    LOW_COVERAGE("Korespondencje są zbyt słabo rozmieszczone w kadrze"),
    LOW_MOTION("Zbyt małe przemieszczenie cech"),
    UNDISTORTION_FAILED("Nie udało się odwrócić dystorsji"),
    OPENCV_UNAVAILABLE("Moduł geometrii OpenCV jest niedostępny"),
    ESSENTIAL_FAILED("Nie udało się wyznaczyć macierzy istotnej"),
    TOO_FEW_INLIERS("Zbyt mało inlierów RANSAC"),
    POSE_FAILED("Nie udało się wyznaczyć poprawnej pozy względnej"),
    CHEIRALITY_FAILED("Zbyt mało punktów o dodatniej głębokości"),
    LOW_PARALLAX("Zbyt mała paralaksa po uwzględnieniu obrotu"),
    REPROJECTION_FAILED("Zbyt duży błąd reprojekcji"),
    INTERNAL_ERROR("Błąd obliczeń geometrii"),
}

internal data class ReconstructedPoint(
    val trackId: Long,
    val x: Double,
    val y: Double,
    val z: Double,
    val parallaxDeg: Double,
    val reprojectionErrorPx: Double,
)

/**
 * X2 = R21 * X1 + t21, camera 1 defines the world frame.
 * Translation is a unit direction and cloud scale is arbitrary (not metres).
 */
internal data class RelativePose(
    val rotationRowMajor: List<Double>,
    val translationUnit: List<Double>,
) {
    init {
        require(rotationRowMajor.size == 9 && rotationRowMajor.all { it.isFinite() })
        require(translationUnit.size == 3 && translationUnit.all { it.isFinite() })
    }
}

internal sealed interface TwoViewResult {
    data class Rejected(
        val reason: TwoViewFailure,
        val inputCount: Int,
        val inlierCount: Int = 0,
    ) : TwoViewResult

    data class Initialized(
        val pose: RelativePose,
        val cloud: List<ReconstructedPoint>,
        val inputCount: Int,
        val inlierCount: Int,
        val medianParallaxDeg: Double,
        val rmsReprojectionPx: Double,
        val arbitraryScale: Boolean = true,
    ) : TwoViewResult
}

/** Pure-JVM preflight; never treats 2D flow or a homography as a solved pose. */
internal object TwoViewQualityGate {
    fun reject(
        matches: List<TwoViewCorrespondence>,
        intrinsics: CameraIntrinsics,
        config: TwoViewConfig,
    ): TwoViewFailure? {
        if (matches.size < config.minMatches) return TwoViewFailure.TOO_FEW_MATCHES
        if (matches.size > config.maxMatches) return TwoViewFailure.TOO_MANY_MATCHES
        if (matches.map { it.trackId }.toSet().size != matches.size) return TwoViewFailure.DUPLICATE_IDS
        val width = intrinsics.imageWidth
        val height = intrinsics.imageHeight
        if (width <= 0 || height <= 0 ||
            !intrinsics.fx.isFinite() || !intrinsics.fy.isFinite() ||
            intrinsics.fx <= 0.0 || intrinsics.fy <= 0.0 ||
            !intrinsics.cx.isFinite() || !intrinsics.cy.isFinite() ||
            intrinsics.distortion.size != 5 || intrinsics.distortion.any { !it.isFinite() }
        ) {
            return TwoViewFailure.INVALID_COORDINATES
        }
        fun inside(point: PixelPoint) =
            point.x >= 0.0 && point.x < width && point.y >= 0.0 && point.y < height

        if (matches.any { !inside(it.first) || !inside(it.second) }) {
            return TwoViewFailure.INVALID_COORDINATES
        }
        val occupied = matches.map { match ->
            val column = (match.first.x / width * config.gridColumns).toInt().coerceIn(0, config.gridColumns - 1)
            val row = (match.first.y / height * config.gridRows).toInt().coerceIn(0, config.gridRows - 1)
            row * config.gridColumns + column
        }.toSet().size
        if (occupied < config.minOccupiedCells) return TwoViewFailure.LOW_COVERAGE
        val movement = matches.map { hypot(it.second.x - it.first.x, it.second.y - it.first.y) }.sorted()
        if (movement[movement.size / 2] < config.minMedianMotionPx) return TwoViewFailure.LOW_MOTION
        return null
    }
}
