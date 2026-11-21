package dev.mobile.slam.frontend

/**
 * Bounded retry budget for transient CameraX binding failures in one Live session.
 * The owner cancels pending work on disposal; this class schedules no callbacks itself.
 */
internal class CameraBindRetryBudget(private val maxAttempts: Int = 12) {
    private var attempts = 0

    init {
        require(maxAttempts > 0)
    }

    fun acquire(disposed: Boolean): Boolean {
        if (disposed || attempts >= maxAttempts) return false
        attempts++
        return true
    }

    fun reset() {
        attempts = 0
    }
}
