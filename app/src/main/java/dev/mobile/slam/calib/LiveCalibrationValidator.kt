package dev.mobile.slam.calib

/**
 * Read-only compatibility gate for observed, unrotated CameraX ImageAnalysis buffers.
 * Compatibility of scalar geometry alone does NOT validate the lens, focus, crop
 * pipeline, distortion or device-specific EXIF/photo-to-sensor transformation.
 */
internal enum class LiveCalibrationState {
    NOT_CALIBRATED,
    CAMERA_ID_UNKNOWN,
    CAMERA_MISMATCH,
    WAITING_FOR_FRAME,
    GEOMETRY_MISMATCH,
    ORIENTATION_AMBIGUOUS,
    GEOMETRY_COMPATIBLE_UNVERIFIED,
}

internal data class LiveCalibrationAssessment(
    val state: LiveCalibrationState,
    val message: String,
    val candidateIntrinsics: CameraIntrinsics? = null,
    val rotatedCandidates: List<OrientationIntrinsicsCandidate> = emptyList(),
)

internal object LiveCalibrationValidator {
    fun assess(
        profile: MeasuredCalibration?,
        cameraId: String?,
        width: Int?,
        height: Int?,
        crop: CalibrationCrop?,
    ): LiveCalibrationAssessment {
        if (profile == null) {
            return LiveCalibrationAssessment(
                LiveCalibrationState.NOT_CALIBRATED,
                "Brak zmierzonego profilu; dostępne jedynie parametry demonstracyjne.",
            )
        }
        if (cameraId.isNullOrBlank()) {
            return LiveCalibrationAssessment(
                LiveCalibrationState.CAMERA_ID_UNKNOWN,
                "Profil zapisany, lecz ID fizycznej kamery nie zostało potwierdzone.",
            )
        }
        if (cameraId != profile.cameraId) {
            return LiveCalibrationAssessment(
                LiveCalibrationState.CAMERA_MISMATCH,
                "Profil kamery ${profile.cameraId} nie pasuje do aktywnej kamery $cameraId.",
            )
        }
        if (width == null || height == null || crop == null) {
            return LiveCalibrationAssessment(
                LiveCalibrationState.WAITING_FOR_FRAME,
                "ID kamery zgodne. Oczekiwanie na rzeczywistą geometrię klatki.",
            )
        }
        val intrinsics = profile.forAnalysis(cameraId, width, height, crop)
        if (intrinsics == null) {
            val rotated = CalibrationOrientationCandidates.forAnalysis(
                profile,
                cameraId,
                width,
                height,
                crop,
            )
            if (rotated.isNotEmpty()) {
                return LiveCalibrationAssessment(
                    LiveCalibrationState.ORIENTATION_AMBIGUOUS,
                    "Profil ${profile.referenceWidth}×${profile.referenceHeight} ma pionowy układ osi; " +
                        "obraz CameraX ${width}×${height} może odpowiadać obrotowi 90° lub 270°. " +
                        "Żadnej z tych orientacji nie wolno przyjąć automatycznie.",
                    rotatedCandidates = rotated,
                )
            }
            return LiveCalibrationAssessment(
                LiveCalibrationState.GEOMETRY_MISMATCH,
                "Profil kamery zgodny, ale rozdzielczość, proporcje lub crop są niezgodne.",
            )
        }
        return LiveCalibrationAssessment(
            LiveCalibrationState.GEOMETRY_COMPATIBLE_UNVERIFIED,
            "Macierz K przeliczona do bufora CameraX. Zgodność obiektywu i ostrości niezweryfikowana.",
            intrinsics,
        )
    }
}
