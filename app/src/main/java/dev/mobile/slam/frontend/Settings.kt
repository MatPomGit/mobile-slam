package dev.mobile.slam.frontend

/**
 * Holds configurable frontend algorithm parameters so thresholds are not scattered in processing code.
 */
data class Settings(
    val fastThreshold: Int = 20,
    val imuHudIntervalMs: Long = 100,
)
