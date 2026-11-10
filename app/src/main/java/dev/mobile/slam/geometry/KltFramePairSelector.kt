package dev.mobile.slam.geometry

import dev.mobile.slam.calib.PixelPoint
import dev.mobile.slam.frontend.FeatureStatus
import dev.mobile.slam.frontend.FeatureTrack
import dev.mobile.slam.frontend.FrameMetadata
import kotlin.math.hypot

/** Pure 2D candidate selection. This is not a pose or a reconstructed keyframe. */
internal data class FramePairConfig(
    val minSharedTracks: Int = 30,
    val minFrameGap: Long = 3L,
    val maxAnchorAgeMs: Long = 2500L,
    val maxFrameGapMs: Long = 350L,
    val minMedianDisplacementPx: Double = 5.0,
    val minOccupiedCells: Int = 6,
    val gridColumns: Int = 4,
    val gridRows: Int = 3,
    val maxStoredTracks: Int = 400,
) {
    init {
        require(minSharedTracks in 8..maxStoredTracks)
        require(maxStoredTracks in 8..2000)
        require(minFrameGap in 1L..120L)
        require(maxAnchorAgeMs in 100L..10_000L)
        require(maxFrameGapMs in 30L..maxAnchorAgeMs)
        require(minMedianDisplacementPx.isFinite() && minMedianDisplacementPx in 0.1..100.0)
        require(gridColumns in 2..16 && gridRows in 2..16)
        require(minOccupiedCells in 2..gridColumns * gridRows)
    }
}

internal enum class FramePairState(val label: String) {
    WAITING("zbieranie punktów odniesienia"),
    TOO_SOON("oczekiwanie na odstęp klatek"),
    TOO_FEW_SHARED("zbyt mało wspólnych identyfikatorów KLT"),
    LOW_COVERAGE("punkty skupione w niewielkim obszarze"),
    LOW_MOTION("zbyt małe przemieszczenie obrazu"),
    READY("wybrano parę 2D"),
    GEOMETRY_CHANGED("zmiana geometrii: nowa para"),
    TIME_GAP("przerwa czasowa: nowa para"),
    ANCHOR_EXPIRED("odświeżono klatkę odniesienia"),
    INVALID_TRACKS("niespójne identyfikatory cech"),
}

/**
 * Coordinates are crop-relative, unrotated ImageProxy pixels; downstream native
 * geometry additionally requires independent validation of the optical path.
 */
internal data class TwoViewFramePair(
    val first: FrameMetadata,
    val second: FrameMetadata,
    val matches: List<TwoViewCorrespondence>,
) {
    init {
        require(first.segmentId == second.segmentId)
        require(first.geometry == second.geometry)
        require(first.frameId < second.frameId)
        require(first.timestampNs < second.timestampNs)
    }
}

/** Small UI status plus an optional immutable and bounded pair for further processing. */
internal data class FramePairSelection(
    val state: FramePairState,
    val sharedTracks: Int = 0,
    val medianMotionPx: Double = 0.0,
    val referenceFrameId: Long? = null,
    val acceptedPairs: Long = 0L,
    val pair: TwoViewFramePair? = null,
)

/**
 * Runs on the same serial worker as KLT. Keeps only one bounded set of point
 * coordinates; no images, native Mats, pose estimates or IMU samples are stored.
 *
 * READY is a *candidate* for geometry, never proof of translation/parallax:
 * observed 2D motion can be produced entirely by camera rotation.
 */
internal class KltFramePairSelector(private val config: FramePairConfig = FramePairConfig()) {
    private data class Anchor(val frame: FrameMetadata, val points: Map<Long, PixelPoint>)

    private var anchor: Anchor? = null
    private var previousFrame: FrameMetadata? = null
    private var acceptedPairs = 0L

    fun observe(frame: FrameMetadata, tracks: List<FeatureTrack>): FramePairSelection {
        val crop = frame.geometry.crop
        val supported = tracks.filter { track ->
            track.status == FeatureStatus.DETECTED || track.status == FeatureStatus.TRACKED
        }.filter { track ->
            track.current.xPx >= crop.left && track.current.xPx < crop.right &&
                track.current.yPx >= crop.top && track.current.yPx < crop.bottom
        }.take(config.maxStoredTracks)

        if (supported.map { it.id }.toSet().size != supported.size) {
            reset()
            return FramePairSelection(FramePairState.INVALID_TRACKS)
        }

        val positions = supported.associate { track ->
            track.id to PixelPoint(
                track.current.xPx.toDouble() - crop.left,
                track.current.yPx.toDouble() - crop.top,
            )
        }
        val last = previousFrame
        val reference = anchor
        if (last != null) {
            if (frame.segmentId != last.segmentId || frame.geometry != last.geometry ||
                frame.frameId <= last.frameId
            ) {
                return begin(frame, positions, FramePairState.GEOMETRY_CHANGED)
            }
            if (frame.timestampNs <= last.timestampNs ||
                frame.timestampNs - last.timestampNs > config.maxFrameGapMs * 1_000_000L
            ) {
                return begin(frame, positions, FramePairState.TIME_GAP)
            }
        }
        previousFrame = frame
        if (reference == null) return begin(frame, positions, FramePairState.WAITING)
        if (frame.timestampNs - reference.frame.timestampNs > config.maxAnchorAgeMs * 1_000_000L) {
            return begin(frame, positions, FramePairState.ANCHOR_EXPIRED)
        }

        val trackedIds = supported.asSequence()
            .filter { it.status == FeatureStatus.TRACKED }
            .map { it.id }
            .toSet()
        val common = positions.entries.asSequence()
            .filter { it.key in trackedIds }
            .mapNotNull { (id, second) ->
                reference.points[id]?.let { first ->
                    TwoViewCorrespondence(id, first, second)
                }
            }
            .sortedBy { it.trackId }
            .toList()

        val shared = common.size
        val displacements = common.map { match ->
            hypot(match.second.x - match.first.x, match.second.y - match.first.y)
        }.sorted()
        val median = if (shared == 0) 0.0 else displacements[shared / 2]
        fun status(state: FramePairState) = FramePairSelection(
            state = state,
            sharedTracks = shared,
            medianMotionPx = median,
            referenceFrameId = reference.frame.frameId,
            acceptedPairs = acceptedPairs,
        )

        if (frame.frameId - reference.frame.frameId < config.minFrameGap) {
            return status(FramePairState.TOO_SOON)
        }
        if (shared < config.minSharedTracks) return status(FramePairState.TOO_FEW_SHARED)
        val occupied = common.map { match ->
            val col = (match.first.x / crop.width * config.gridColumns).toInt()
                .coerceIn(0, config.gridColumns - 1)
            val row = (match.first.y / crop.height * config.gridRows).toInt()
                .coerceIn(0, config.gridRows - 1)
            row * config.gridColumns + col
        }.toSet().size
        if (occupied < config.minOccupiedCells) return status(FramePairState.LOW_COVERAGE)
        if (median < config.minMedianDisplacementPx) return status(FramePairState.LOW_MOTION)

        val candidate = TwoViewFramePair(reference.frame, frame, common)
        // A successful pair is consumed once. New observations form the next anchor.
        anchor = Anchor(frame, positions)
        acceptedPairs++
        return FramePairSelection(
            state = FramePairState.READY,
            sharedTracks = shared,
            medianMotionPx = median,
            referenceFrameId = reference.frame.frameId,
            acceptedPairs = acceptedPairs,
            pair = candidate,
        )
    }

    private fun begin(frame: FrameMetadata, points: Map<Long, PixelPoint>, state: FramePairState): FramePairSelection {
        anchor = Anchor(frame, points)
        previousFrame = frame
        return FramePairSelection(state, referenceFrameId = frame.frameId, acceptedPairs = acceptedPairs)
    }

    fun reset() {
        anchor = null
        previousFrame = null
        acceptedPairs = 0L
    }
}
