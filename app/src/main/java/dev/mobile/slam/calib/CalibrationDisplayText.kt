package dev.mobile.slam.calib

import java.util.Locale

/** Human-readable pinhole matrix and Brown-Conrady coefficients in decoded-image pixel coordinates. */
internal object CalibrationDisplayText {
    fun matrix(profile: MeasuredCalibration): String =
        listOf(
            "[ ${number(profile.fx)}     0.000  ${number(profile.cx)} ]",
            "[    0.000  ${number(profile.fy)}  ${number(profile.cy)} ]",
            "[    0.000     0.000     1.000 ]",
        ).joinToString("\n")

    fun distortion(profile: MeasuredCalibration): String =
        listOf("k1", "k2", "p1", "p2", "k3")
            .zip(profile.distortion)
            .joinToString("\n") { (name, value) -> "$name = ${String.format(Locale.US, "%.6f", value)}" }

    private fun number(value: Double): String = String.format(Locale.US, "%.3f", value)
}
