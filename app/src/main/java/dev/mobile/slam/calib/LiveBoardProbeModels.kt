package dev.mobile.slam.calib

import kotlin.math.abs

/**
 * Explicit live validation uses the checkerboard's INNER corner counts.
 * Chessboard scale does not change single-view pinhole reprojection residuals,
 * so unit-spaced board coordinates avoid an unjustified physical pitch value.
 */
internal data class LiveBoardProbeSettings(
    val columns: Int = 7,
    val rows: Int = 10,
    val intervalNs: Long = 2_000_000_000L,
) {
    init {
        require(columns in 4..16 && rows in 4..16)
        require(intervalNs in 500_000_000L..10_000_000_000L)
    }
}

internal data class LiveBoardCandidateScore(
    val rotationDegrees: Int,
    val lastRmsPx: Double,
    val medianRmsPx: Double,
    val distinctViews: Int,
)

internal data class LiveBoardProbeSnapshot(
    val status: String,
    val attempted: Int = 0,
    val found: Int = 0,
    val distinctViews: Int = 0,
    val candidateScores: List<LiveBoardCandidateScore> = emptyList(),
)

/**
 * Bounded, deterministic diversity/reprojection bookkeeping. Distinct frames
 * require a meaningful board centre OR area difference. This is *not* acceptance
 * of camera optics: every view's PnP pose is fitted independently.
 */
internal class LiveBoardProbeLedger {
    private val views = ArrayList<Triple<Double, Double, Double>>(40)
    private val residuals = linkedMapOf<Int, MutableList<Double>>()
    var attempted = 0
        private set
    var found = 0
        private set

    val distinctViews: Int get() = views.size

    fun attempt() {
        attempted++
    }

    fun add(centerX: Double, centerY: Double, area: Double, errors: Map<Int, Double>): Boolean {
        if (!centerX.isFinite() || !centerY.isFinite() || !area.isFinite() ||
            centerX !in 0.0..1.0 || centerY !in 0.0..1.0 || area <= 0.0 ||
            errors.isEmpty() || errors.values.any { !it.isFinite() || it < 0.0 }
        ) {
            return false
        }
        // Only consistent candidate sets represent a valid detected measurement.
        if (residuals.isNotEmpty() && residuals.keys != errors.keys) return false
        found++
        val repeated = views.any { (x, y, a) ->
            kotlin.math.hypot(centerX - x, centerY - y) < 0.06 && abs(area - a) < 0.025
        }
        if (repeated || views.size >= 40) return false
        views.add(Triple(centerX, centerY, area))
        errors.forEach { (rotation, error) ->
            residuals.getOrPut(rotation) { mutableListOf() }.add(error)
        }
        return true
    }

    fun scores(): List<LiveBoardCandidateScore> = residuals.map { (rotation, samples) ->
        val sorted = samples.sorted()
        LiveBoardCandidateScore(rotation, samples.last(), sorted[sorted.size / 2], sorted.size)
    }

    fun reset() {
        views.clear()
        residuals.clear()
        attempted = 0
        found = 0
    }
}
