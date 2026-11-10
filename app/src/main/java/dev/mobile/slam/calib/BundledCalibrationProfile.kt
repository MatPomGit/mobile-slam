package dev.mobile.slam.calib

/**
 * Measured chessboard profile supplied for the rear Camera2 device "0".
 * Image coordinates follow the EXIF-oriented 750x1000 calibration photos,
 * NOT the unrotated ImageProxy coordinates used by CameraX.
 *
 * This is a fallback only. Locally measured/imported profiles take precedence.
 * A bundled measurement is not independent live optical-path verification.
 */
internal object BundledCalibrationProfile {
    val measured: MeasuredCalibration = MeasuredCalibration(
        cameraId = "0",
        referenceWidth = 750,
        referenceHeight = 1000,
        referenceCrop = CalibrationCrop(0, 0, 750, 1000),
        fx = 703.0517873666174,
        fy = 701.6570335584057,
        cx = 380.0172550637061,
        cy = 500.5981210229705,
        distortion = listOf(
            0.11424208067093562,
            -0.2456484575699898,
            8.216670123752652E-4,
            0.006777393441683128,
            0.2172513995442215,
        ),
        rmsReprojectionPx = 0.4875927303103622,
        provenance = CalibrationProvenance.CHESSBOARD_MEASURED,
    )
}
