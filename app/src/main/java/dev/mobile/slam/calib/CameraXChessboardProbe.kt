package dev.mobile.slam.calib

import dev.mobile.slam.frontend.FrameGeometry
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point3
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * Opt-in, single-worker CameraX chessboard measurement.
 * Returns residuals of separately fitted PnP poses, never a verified K or pose.
 * The caller owns and releases the source grayscale Mat.
 */
internal class CameraXChessboardProbe(
    private val calibration: MeasuredCalibration?,
    private val settings: LiveBoardProbeSettings,
) {
    private val ledger = LiveBoardProbeLedger()
    private var lastTimestampNs = 0L
    private var lastGeometry: FrameGeometry? = null
    private var lastCameraId: String? = null
    private var lastSnapshot = LiveBoardProbeSnapshot("Rozpocznij pomiar, pokazując pełną szachownicę.")

    fun observe(gray: Mat, geometry: FrameGeometry, timestampNs: Long, cameraId: String?): LiveBoardProbeSnapshot {
        if (geometry != lastGeometry || cameraId != lastCameraId || timestampNs <= lastTimestampNs) {
            ledger.reset()
            lastTimestampNs = 0L
            lastGeometry = geometry
            lastCameraId = cameraId
        }
        if (timestampNs > 0L && lastTimestampNs > 0L &&
            timestampNs - lastTimestampNs < settings.intervalNs
        ) {
            return lastSnapshot
        }
        lastTimestampNs = timestampNs
        val crop = geometry.crop
        val assessment = LiveCalibrationValidator.assess(
            calibration,
            cameraId,
            geometry.widthPx,
            geometry.heightPx,
            CalibrationCrop(crop.left, crop.top, crop.right, crop.bottom),
        )
        val candidates = when (assessment.state) {
            LiveCalibrationState.GEOMETRY_COMPATIBLE_UNVERIFIED -> listOf(
                OrientationIntrinsicsCandidate(0, assessment.candidateIntrinsics!!),
            )
            LiveCalibrationState.ORIENTATION_AMBIGUOUS -> assessment.rotatedCandidates
            else -> emptyList()
        }
        if (candidates.isEmpty()) {
            lastSnapshot = summary("Pomiar niemożliwy: ${assessment.message}")
            return lastSnapshot
        }
        ledger.attempt()
        val roi = gray.submat(crop.top, crop.bottom, crop.left, crop.right)
        val corners = MatOfPoint2f()
        try {
            val size = Size(settings.columns.toDouble(), settings.rows.toDouble())
            val flags = Calib3d.CALIB_CB_ADAPTIVE_THRESH or
                Calib3d.CALIB_CB_NORMALIZE_IMAGE or Calib3d.CALIB_CB_FAST_CHECK
            if (!Calib3d.findChessboardCorners(roi, size, corners, flags)) {
                lastSnapshot = summary("Nie wykryto pełnej szachownicy ${settings.columns}×${settings.rows}.")
                return lastSnapshot
            }
            Imgproc.cornerSubPix(
                roi,
                corners,
                Size(5.0, 5.0),
                Size(-1.0, -1.0),
                TermCriteria(TermCriteria.EPS or TermCriteria.MAX_ITER, 30, 0.01),
            )
            val observed = corners.toArray()
            if (observed.size != settings.columns * settings.rows) {
                lastSnapshot = summary("Niespójna liczba wykrytych narożników.")
                return lastSnapshot
            }
            val objectPoints = MatOfPoint3f()
            try {
                objectPoints.fromList(
                    (0 until settings.rows).flatMap { row ->
                        (0 until settings.columns).map { column ->
                            Point3(column.toDouble(), row.toDouble(), 0.0)
                        }
                    },
                )
                val errors = linkedMapOf<Int, Double>()
                for (candidate in candidates) {
                    val rms = reprojectionRms(objectPoints, corners, candidate.intrinsics)
                    if (rms == null) {
                        lastSnapshot = summary("Nie udało się dopasować pozy planszy do profilu kamery.")
                        return lastSnapshot
                    }
                    errors[candidate.clockwiseDegrees] = rms
                }
                val cx = (observed.minOf { it.x } + observed.maxOf { it.x }) / (2.0 * roi.cols())
                val cy = (observed.minOf { it.y } + observed.maxOf { it.y }) / (2.0 * roi.rows())
                val area = (observed.maxOf { it.x } - observed.minOf { it.x }) *
                    (observed.maxOf { it.y } - observed.minOf { it.y }) / (roi.cols() * roi.rows())
                val distinct = ledger.add(cx, cy, area, errors)
                lastSnapshot = summary(
                    if (distinct) {
                        "Wyznaczono RMS reprojekcji dla nowego ujęcia. To test zgodności, nie dowód kalibracji."
                    } else {
                        "Wzorzec znaleziony; ujęcie zbyt podobne lub nieprawidłowe. Zmień kąt i pozycję."
                    },
                )
                return lastSnapshot
            } finally {
                objectPoints.release()
            }
        } catch (_: Exception) {
            lastSnapshot = summary("Błąd detekcji lub projekcji; profil nie został zatwierdzony.")
            return lastSnapshot
        } finally {
            corners.release()
            roi.release()
        }
    }

    private fun reprojectionRms(objectPoints: MatOfPoint3f, corners: MatOfPoint2f, k: CameraIntrinsics): Double? {
        val camera = Mat.eye(3, 3, CvType.CV_64F)
        val distortion = MatOfDouble()
        val rvec = Mat()
        val tvec = Mat()
        val projected = MatOfPoint2f()
        try {
            camera.put(0, 0, k.fx)
            camera.put(1, 1, k.fy)
            camera.put(0, 2, k.cx)
            camera.put(1, 2, k.cy)
            distortion.fromArray(*k.distortion.toDoubleArray())
            if (!Calib3d.solvePnP(objectPoints, corners, camera, distortion, rvec, tvec)) return null
            Calib3d.projectPoints(objectPoints, rvec, tvec, camera, distortion, projected)
            val measured = corners.toArray()
            val predicted = projected.toArray()
            if (measured.size != predicted.size || predicted.isEmpty()) return null
            val error2 = measured.indices.sumOf { index ->
                val dx = measured[index].x - predicted[index].x
                val dy = measured[index].y - predicted[index].y
                dx * dx + dy * dy
            }
            val rms = sqrt(error2 / measured.size)
            return rms.takeIf { it.isFinite() }
        } finally {
            projected.release()
            tvec.release()
            rvec.release()
            distortion.release()
            camera.release()
        }
    }

    private fun summary(message: String) = LiveBoardProbeSnapshot(
        status = message,
        attempted = ledger.attempted,
        found = ledger.found,
        distinctViews = ledger.distinctViews,
        candidateScores = ledger.scores(),
    )

    fun close() {
        ledger.reset()
        lastTimestampNs = 0L
        lastGeometry = null
        lastCameraId = null
    }
}
