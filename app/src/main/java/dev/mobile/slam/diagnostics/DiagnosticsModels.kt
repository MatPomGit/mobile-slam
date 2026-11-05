package dev.mobile.slam.diagnostics

import kotlin.math.abs
import kotlin.math.ceil

/** Source distinguishes hardware claims, configured requests, observations and missing data. */
internal enum class EvidenceSource {
    REPORTED,
    REQUESTED,
    OBSERVED,
    UNAVAILABLE,
}

/** Single unit-labelled, provenance-aware hardware parameter. */
internal data class DiagnosticField(
    val name: String,
    val value: String?,
    val unit: String? = null,
    val source: EvidenceSource,
    val reason: String? = null,
)

/** Device and session values grouped for display and export. */
internal data class DiagnosticSection(val name: String, val fields: List<DiagnosticField>)

/** Camera timestamps can be compared to the sensor/elapsed clock only when sourced from REALTIME. */
internal enum class CameraClockSource {
    REALTIME,
    UNKNOWN,
    UNAVAILABLE,
}

internal object ClockDomainSafety {
    fun cameraImuComparable(source: CameraClockSource): Boolean = source == CameraClockSource.REALTIME

    fun explanation(source: CameraClockSource): String = when (source) {
        CameraClockSource.REALTIME ->
            "REALTIME: porównywalna domena zegara, ale nie potwierdzono opóźnienia ekspozycji ani kalibracji synchronizacji."
        CameraClockSource.UNKNOWN ->
            "UNKNOWN: nie wolno bezpośrednio odejmować znaczników czasu kamery i IMU."
        CameraClockSource.UNAVAILABLE ->
            "Nie ustalono źródła czasu aktywnej kamery. Synchronizacja niezweryfikowana."
    }
}

/** Observed intervals based on each sensor's own timestamps, not on HUD callback frequency. */
internal data class IntervalSummary(
    val sampleCount: Int,
    val medianPeriodMs: Double?,
    val frequencyHz: Double?,
    val p95AbsoluteJitterMs: Double?,
    val nonMonotonic: Int,
)

/** Bounded event history: stores scalar timestamps only, never sensor values or image data. */
internal class TimestampWindow(private val maxEvents: Int = 600) {
    init {
        require(maxEvents >= 2)
    }

    private val timestamps = ArrayDeque<Long>()
    private var nonMonotonic = 0
    private var previous: Long? = null
    private var total = 0

    fun record(timestampNs: Long) {
        val last = previous
        if (last != null && timestampNs <= last) nonMonotonic++
        previous = timestampNs
        total++
        if (timestamps.size == maxEvents) timestamps.removeFirst()
        timestamps.addLast(timestampNs)
    }

    fun summary(): IntervalSummary {
        val periods = timestamps.zipWithNext { a, b -> b - a }
            .filter { it > 0L }
            .map { it / 1_000_000.0 }
            .sorted()
        if (periods.isEmpty()) return IntervalSummary(total, null, null, null, nonMonotonic)
        val median = percentile(periods, 0.5)
        val jitter = periods.map { abs(it - median) }.sorted()
        return IntervalSummary(
            sampleCount = total,
            medianPeriodMs = median,
            frequencyHz = if (median > 0) 1000.0 / median else null,
            p95AbsoluteJitterMs = percentile(jitter, 0.95),
            nonMonotonic = nonMonotonic,
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        val index = (ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}

/** A single observed CameraX ImageAnalysis buffer and its own processing duration. */
internal data class FrameObservation(
    val timestampNs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val rotationDegrees: Int,
    val crop: String,
    val rowStrideBytes: Int?,
    val pixelStrideBytes: Int?,
    val processingDurationNs: Long,
)

/** Thread-safe scalar-only observations of the last Live camera session. */
internal class CameraObservationRecorder {
    private val timestamps = TimestampWindow(300)
    private val processing = ArrayDeque<Long>()
    private var lastFrame: FrameObservation? = null
    private var selectedCameraId: String? = null

    @Synchronized
    fun cameraBound(cameraId: String?) {
        selectedCameraId = cameraId
    }

    @Synchronized
    fun observe(frame: FrameObservation) {
        lastFrame = frame
        timestamps.record(frame.timestampNs)
        if (processing.size == 300) processing.removeFirst()
        processing.addLast(frame.processingDurationNs)
    }

    @Synchronized
    fun snapshot(): CameraObservationSnapshot {
        val durationMs = processing.map { it / 1_000_000.0 }.sorted()
        return CameraObservationSnapshot(
            cameraId = selectedCameraId,
            lastFrame = lastFrame,
            cadence = timestamps.summary(),
            medianProcessingMs = if (durationMs.isEmpty()) null else durationMs[(durationMs.size - 1) / 2],
            p95ProcessingMs = if (durationMs.isEmpty()) {
                null
            } else {
                durationMs[(ceil(0.95 * durationMs.size).toInt() - 1).coerceIn(0, durationMs.lastIndex)]
            },
        )
    }
}

/** Immutable copy of observed metadata; independent of the display's recomposition rate. */
internal data class CameraObservationSnapshot(
    val cameraId: String?,
    val lastFrame: FrameObservation?,
    val cadence: IntervalSummary,
    val medianProcessingMs: Double?,
    val p95ProcessingMs: Double?,
)
