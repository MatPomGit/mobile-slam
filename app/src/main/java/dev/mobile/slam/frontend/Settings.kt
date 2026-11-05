package dev.mobile.slam.frontend

/**
 * Immutable algorithm settings used by a newly started Live camera/IMU session.
 * The IMU interval controls the HUD only, not the physical sensor sampling rate.
 */
data class Settings(
    val fastThreshold: Int = DEFAULT_FAST_THRESHOLD,
    val imuHudIntervalMs: Long = DEFAULT_IMU_HUD_INTERVAL_MS,
    val kltMaxTracks: Int = 240,
    val kltMinTracks: Int = 90,
    val kltWindowSize: Int = 21,
    val kltMaxLevel: Int = 3,
    val kltForwardBackwardPx: Float = 1.5f,
    val kltMinSeparationPx: Float = 9f,
    val kltMaxGapMs: Long = 250L,
    val kltGridColumns: Int = 8,
    val kltGridRows: Int = 6,
) {
    init {
        require(fastThreshold in MIN_FAST_THRESHOLD..MAX_FAST_THRESHOLD)
        require(imuHudIntervalMs in MIN_IMU_HUD_INTERVAL_MS..MAX_IMU_HUD_INTERVAL_MS)
        require(kltMaxTracks in 1..2000)
        require(kltMinTracks in 1..kltMaxTracks)
        require(kltWindowSize in 5..61 && kltWindowSize % 2 == 1)
        require(kltMaxLevel in 0..5)
        require(kltForwardBackwardPx.isFinite() && kltForwardBackwardPx > 0f)
        require(kltMinSeparationPx.isFinite() && kltMinSeparationPx >= 0f)
        require(kltMaxGapMs in 50L..2000L)
        require(kltGridColumns in 1..32 && kltGridRows in 1..32)
    }

    companion object {
        const val DEFAULT_FAST_THRESHOLD = 20
        const val DEFAULT_IMU_HUD_INTERVAL_MS = 100L
        const val MIN_FAST_THRESHOLD = 1
        const val MAX_FAST_THRESHOLD = 255
        const val MIN_IMU_HUD_INTERVAL_MS = 100L
        const val MAX_IMU_HUD_INTERVAL_MS = 1000L
    }
}

/** Settings read from storage, optionally accompanied by a recoverable data-quality warning. */
data class SettingsSnapshot(
    val settings: Settings,
    val warning: String? = null,
)

/**
 * Pure codec for settings schema v1; unknown or malformed values cannot reach the live frontend.
 */
object SettingsCodec {
    const val VERSION = 1

    fun decode(version: Int?, fastThreshold: Int?, imuHudIntervalMs: Long?): SettingsSnapshot {
        if (version != null && version != VERSION) {
            return SettingsSnapshot(Settings(), "Nieobsługiwana wersja ustawień. Przyjęto wartości domyślne.")
        }
        val validFast = fastThreshold == null || fastThreshold in Settings.MIN_FAST_THRESHOLD..Settings.MAX_FAST_THRESHOLD
        val validImu = imuHudIntervalMs == null ||
            imuHudIntervalMs in Settings.MIN_IMU_HUD_INTERVAL_MS..Settings.MAX_IMU_HUD_INTERVAL_MS
        if (!validFast || !validImu) {
            return SettingsSnapshot(Settings(), "Wykryto nieprawidłowe parametry. Przyjęto wartości domyślne.")
        }
        return SettingsSnapshot(
            Settings(
                fastThreshold = fastThreshold ?: Settings.DEFAULT_FAST_THRESHOLD,
                imuHudIntervalMs = imuHudIntervalMs ?: Settings.DEFAULT_IMU_HUD_INTERVAL_MS,
            ),
        )
    }

    fun parse(fastThreshold: String, imuHudIntervalMs: String): Settings? {
        val fast = fastThreshold.trim().toIntOrNull() ?: return null
        val imu = imuHudIntervalMs.trim().toLongOrNull() ?: return null
        if (fast !in Settings.MIN_FAST_THRESHOLD..Settings.MAX_FAST_THRESHOLD) return null
        if (imu !in Settings.MIN_IMU_HUD_INTERVAL_MS..Settings.MAX_IMU_HUD_INTERVAL_MS) return null
        return Settings(fast, imu)
    }
}
