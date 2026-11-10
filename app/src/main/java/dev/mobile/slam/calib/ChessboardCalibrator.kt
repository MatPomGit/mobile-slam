package dev.mobile.slam.calib

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot

/** Number of internal corners (not squares) and measured chessboard cell size. */
internal data class ChessboardSpec(
    val columns: Int = 9,
    val rows: Int = 6,
    val automaticPattern: Boolean = false,
    val squareMm: Double = 20.0,
    val minViews: Int = 8,
    val maxRmsPx: Double = 1.5,
) {
    init {
        require(columns in 4..16 && rows in 4..16)
        require(squareMm.isFinite() && squareMm in 1.0..100.0)
        require(minViews in 6..30)
        require(maxRmsPx.isFinite() && maxRmsPx in 0.1..5.0)
    }
}

/** Inner corner counts; each square axis has one more square than inner corners. */
internal data class ChessboardPattern(val columns: Int, val rows: Int) {
    init {
        require(columns in 4..16 && rows in 4..16)
    }
}

/**
 * Search a bounded set of board dimensions only until the first usable observation.
 * Prioritize common boards, then larger grids to avoid selecting a small sub-grid.
 * Discovery uses a reduced image; final corner locations come from the full image.
 */
internal object ChessboardPatternSearch {
    val candidates: List<ChessboardPattern> = (
        listOf(
            ChessboardPattern(7, 10),
            ChessboardPattern(9, 6),
            ChessboardPattern(10, 7),
            ChessboardPattern(6, 9),
            ChessboardPattern(8, 5),
            ChessboardPattern(5, 8),
        ) +
            (4..16).flatMap { columns ->
                (4..16).map { rows -> ChessboardPattern(columns, rows) }
            }.sortedWith(
                compareByDescending<ChessboardPattern> { it.columns * it.rows }
                    .thenBy { abs(it.columns - it.rows) },
            )
    ).distinct()

    fun detect(gray: Mat, corners: MatOfPoint2f): ChessboardPattern? {
        val maxDimension = maxOf(gray.cols(), gray.rows())
        val thumbnail = Mat()
        val sample = if (maxDimension > 900) {
            val scale = 900.0 / maxDimension
            Imgproc.resize(
                gray,
                thumbnail,
                Size(gray.cols() * scale, gray.rows() * scale),
                0.0,
                0.0,
                Imgproc.INTER_AREA,
            )
            thumbnail
        } else {
            gray
        }
        val trial = MatOfPoint2f()
        val flags = Calib3d.CALIB_CB_ADAPTIVE_THRESH or
            Calib3d.CALIB_CB_NORMALIZE_IMAGE or Calib3d.CALIB_CB_FAST_CHECK
        try {
            for (pattern in candidates) {
                val size = Size(pattern.columns.toDouble(), pattern.rows.toDouble())
                if (!Calib3d.findChessboardCorners(sample, size, trial, flags)) continue
                if (sample === gray) {
                    trial.copyTo(corners)
                    return pattern
                }
                if (Calib3d.findChessboardCorners(gray, size, corners, flags)) return pattern
            }
            return null
        } finally {
            trial.release()
            thumbnail.release()
        }
    }
}

/** Observed outcome, including rejected photos; no synthetic calibration fallback. */
internal data class ChessboardResult(
    val profile: MeasuredCalibration?,
    val accepted: Int,
    val rejected: Int,
    val reason: String,
    val pattern: ChessboardPattern? = null,
)

/**
 * OpenCV chessboard fit from user-selected photographs, run off the UI thread.
 * The operator must verify the photos share lens/zoom/focus/orientation and camera ID.
 * Native image matrices never survive the processing of their individual photo.
 */
internal object ChessboardCalibrator {
    fun calibrate(
        resolver: ContentResolver,
        uris: List<Uri>,
        cameraId: String,
        spec: ChessboardSpec = ChessboardSpec(),
    ): ChessboardResult {
        require(uris.size in spec.minViews..40 && cameraId.isNotBlank())
        if (!OpenCVLoader.initLocal()) {
            return ChessboardResult(null, 0, uris.size, "Biblioteka OpenCV jest niedostępna.")
        }
        val detected = mutableListOf<Mat>()
        val objects = mutableListOf<Mat>()
        val centers = mutableListOf<Triple<Double, Double, Double>>()
        var selectedPattern: ChessboardPattern? = null
        var width = 0
        var height = 0
        var rejected = 0
        try {
            uris.forEach { uri ->
                val gray = readGray(resolver, uri)
                if (gray == null) {
                    rejected++
                    return@forEach
                }
                val corners = MatOfPoint2f()
                var keep = false
                try {
                    if (width != 0 && (gray.cols() != width || gray.rows() != height)) {
                        rejected++
                        return@forEach
                    }
                    val activePattern = selectedPattern ?: if (spec.automaticPattern) {
                        ChessboardPatternSearch.detect(gray, corners)
                    } else {
                        ChessboardPattern(spec.columns, spec.rows)
                    }
                    if (activePattern == null) {
                        rejected++
                        return@forEach
                    }
                    val alreadyDetected = spec.automaticPattern && selectedPattern == null
                    if (!alreadyDetected &&
                        !Calib3d.findChessboardCorners(
                            gray,
                            Size(activePattern.columns.toDouble(), activePattern.rows.toDouble()),
                            corners,
                            Calib3d.CALIB_CB_ADAPTIVE_THRESH or
                                Calib3d.CALIB_CB_NORMALIZE_IMAGE or Calib3d.CALIB_CB_FAST_CHECK,
                        )
                    ) {
                        rejected++
                        return@forEach
                    }
                    Imgproc.cornerSubPix(
                        gray,
                        corners,
                        Size(7.0, 7.0),
                        Size(-1.0, -1.0),
                        TermCriteria(TermCriteria.EPS or TermCriteria.MAX_ITER, 30, 0.01),
                    )
                    val pts = corners.toArray()
                    if (pts.size != activePattern.columns * activePattern.rows) {
                        rejected++
                        return@forEach
                    }
                    val x0 = pts.minOf { it.x }
                    val x1 = pts.maxOf { it.x }
                    val y0 = pts.minOf { it.y }
                    val y1 = pts.maxOf { it.y }
                    val dx = (x1 - x0) / gray.cols()
                    val dy = (y1 - y0) / gray.rows()
                    val cx = (x0 + x1) / (2 * gray.cols())
                    val cy = (y0 + y1) / (2 * gray.rows())
                    val area = dx * dy
                    val duplicated = centers.any { (x, y, a) ->
                        hypot(cx - x, cy - y) < 0.05 && abs(area - a) < 0.02
                    }
                    if (dx < 0.12 || dy < 0.12 || duplicated) {
                        rejected++
                        return@forEach
                    }
                    val grid = (0 until activePattern.rows).flatMap { row ->
                        (0 until activePattern.columns).map { column ->
                            Point3(column * spec.squareMm, row * spec.squareMm, 0.0)
                        }
                    }
                    objects.add(MatOfPoint3f().also { it.fromList(grid) })
                    detected.add(corners)
                    centers.add(Triple(cx, cy, area))
                    if (selectedPattern == null) {
                        selectedPattern = activePattern
                        width = gray.cols()
                        height = gray.rows()
                    }
                    keep = true
                } finally {
                    if (!keep) corners.release()
                    gray.release()
                }
            }
            if (detected.size < spec.minViews) {
                return ChessboardResult(
                    null,
                    detected.size,
                    rejected,
                    "Niewystarczająca liczba różnorodnych zdjęć z pełną szachownicą.",
                    selectedPattern,
                )
            }
            val camera = Mat.eye(3, 3, CvType.CV_64F)
            val distort = Mat()
            val rotations = mutableListOf<Mat>()
            val translations = mutableListOf<Mat>()
            try {
                val rms = Calib3d.calibrateCamera(
                    objects,
                    detected,
                    Size(width.toDouble(), height.toDouble()),
                    camera,
                    distort,
                    rotations,
                    translations,
                    Calib3d.CALIB_FIX_K4 or Calib3d.CALIB_FIX_K5 or Calib3d.CALIB_FIX_K6,
                )
                val fx = camera.get(0, 0)[0]
                val fy = camera.get(1, 1)[0]
                val cx = camera.get(0, 2)[0]
                val cy = camera.get(1, 2)[0]
                val coefficients = (0..4).map { i ->
                    if (distort.rows() == 1) distort.get(0, i)[0] else distort.get(i, 0)[0]
                }
                if (!rms.isFinite() || rms > spec.maxRmsPx ||
                    !fx.isFinite() || !fy.isFinite() ||
                    fx < width * 0.1 || fy < height * 0.1 ||
                    fx > width * 10.0 || fy > height * 10.0
                ) {
                    return ChessboardResult(
                        null,
                        detected.size,
                        rejected,
                        "RMS lub ogniskowe poza zakresem jakości.",
                        selectedPattern,
                    )
                }
                val profile = MeasuredCalibration(
                    cameraId = cameraId,
                    referenceWidth = width,
                    referenceHeight = height,
                    referenceCrop = CalibrationCrop(0, 0, width, height),
                    fx = fx,
                    fy = fy,
                    cx = cx,
                    cy = cy,
                    distortion = coefficients,
                    rmsReprojectionPx = rms,
                    provenance = CalibrationProvenance.CHESSBOARD_MEASURED,
                )
                return ChessboardResult(
                    profile,
                    detected.size,
                    rejected,
                    "Pomiary obliczone; zweryfikuj tor kamery.",
                    selectedPattern,
                )
            } finally {
                camera.release()
                distort.release()
                rotations.forEach { it.release() }
                translations.forEach { it.release() }
            }
        } finally {
            detected.forEach { it.release() }
            objects.forEach { it.release() }
        }
    }

    private fun readGray(resolver: ContentResolver, uri: Uri): Mat? {
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: return null
        // No implicit EXIF rotation: physical camera coordinates must be explicit.
        if (orientation != ExifInterface.ORIENTATION_NORMAL &&
            orientation != ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val readable = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
            true
        } ?: false
        if (!readable) return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var scale = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / scale > 1600) scale *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = scale }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            return gray
        } catch (error: Exception) {
            gray.release()
            throw error
        } finally {
            rgba.release()
            bitmap.recycle()
        }
    }
}
