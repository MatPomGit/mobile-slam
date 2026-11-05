package dev.mobile.slam.frontend

/** LK forward/backward validation output for one previously tracked feature. */
internal data class FlowEstimate(
    val trackId: Long,
    val next: ImageFeature,
    val forwardBackwardErrorPx: Float,
    val forwardValid: Boolean,
    val backwardValid: Boolean,
)

/** Immutable, image-space correspondence results; they contain no native OpenCV data. */
internal data class TrackResult(
    val tracks: List<FeatureTrack>,
    val metrics: TrackingMetrics,
)

/**
 * Single-threaded stable-ID lifecycle for sparse KLT correspondences.
 *
 * New FAST detections become DETECTED seeds (age 1); only independently validated
 * optical flow promotes them to TRACKED (age >1). Crop checks, forward/backward
 * error, spatial quotas and minimum distances keep the active set bounded.
 * Resetting the temporal reference does not reuse previously assigned IDs.
 */
internal class TrackManager(private val settings: Settings) {
    private var active = emptyList<FeatureTrack>()
    private var nextId = 1L

    fun previousTracks(): List<FeatureTrack> = active

    fun reset() {
        active = emptyList()
    }

    fun update(frame: FrameMetadata, estimates: List<FlowEstimate>, detections: List<ImageFeature>): TrackResult {
        val geometry = frame.geometry
        val prior = active.associateBy { it.id }
        val selected = ArrayList<FeatureTrack>(settings.kltMaxTracks)
        val occupied = IntArray(settings.kltGridColumns * settings.kltGridRows)
        val capacityPerCell = (settings.kltMaxTracks + occupied.size - 1) / occupied.size
        val usedIds = HashSet<Long>()

        for (estimate in estimates) {
            val previous = prior[estimate.trackId] ?: continue
            if (selected.size >= settings.kltMaxTracks) break
            if (!usedIds.add(previous.id)) continue
            if (!estimate.forwardValid || !estimate.backwardValid) continue
            if (!estimate.forwardBackwardErrorPx.isFinite() ||
                estimate.forwardBackwardErrorPx > settings.kltForwardBackwardPx
            ) {
                continue
            }
            val position = estimate.next
            if (!insideCrop(position, geometry)) continue
            val cell = cellIndex(position, geometry)
            if (occupied[cell] >= capacityPerCell) continue
            if (tooClose(position, selected)) continue
            selected.add(
                FeatureTrack(
                    id = previous.id,
                    current = position,
                    previous = previous.current,
                    ageFrames = previous.ageFrames + 1,
                    status = FeatureStatus.TRACKED,
                ),
            )
            occupied[cell]++
        }

        val retained = selected.size
        if (selected.size < settings.kltMinTracks) {
            // Limit work in pathological high-texture scenes; detection ordering is
            // deterministic for a given FAST output and grid occupancy.
            for (point in detections.take(settings.kltMaxTracks * 8)) {
                if (selected.size >= settings.kltMaxTracks) break
                if (!insideCrop(point, geometry)) continue
                val cell = cellIndex(point, geometry)
                if (occupied[cell] >= capacityPerCell || tooClose(point, selected)) continue
                selected.add(FeatureTrack(nextId++, point, 1, FeatureStatus.DETECTED))
                occupied[cell]++
            }
        }

        active = selected.toList()
        return TrackResult(
            tracks = active,
            metrics = TrackingMetrics(
                frameId = frame.frameId,
                detected = selected.size - retained,
                tracked = retained,
                rejected = (prior.size - retained).coerceAtLeast(0),
                lost = 0,
            ),
        )
    }

    private fun insideCrop(point: ImageFeature, geometry: FrameGeometry): Boolean =
        point.xPx >= geometry.crop.left && point.xPx < geometry.crop.right &&
            point.yPx >= geometry.crop.top && point.yPx < geometry.crop.bottom

    private fun cellIndex(point: ImageFeature, geometry: FrameGeometry): Int {
        val x = (((point.xPx - geometry.crop.left) / geometry.crop.width) * settings.kltGridColumns)
            .toInt().coerceIn(0, settings.kltGridColumns - 1)
        val y = (((point.yPx - geometry.crop.top) / geometry.crop.height) * settings.kltGridRows)
            .toInt().coerceIn(0, settings.kltGridRows - 1)
        return y * settings.kltGridColumns + x
    }

    private fun tooClose(point: ImageFeature, selected: List<FeatureTrack>): Boolean {
        val minSquared = settings.kltMinSeparationPx * settings.kltMinSeparationPx
        return selected.any {
            val dx = it.current.xPx - point.xPx
            val dy = it.current.yPx - point.yPx
            dx * dx + dy * dy < minSquared
        }
    }
}
