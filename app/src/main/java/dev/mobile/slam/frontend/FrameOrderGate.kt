package dev.mobile.slam.frontend

/**
 * Single-worker frame ordering for one Live camera session.
 * CameraX KEEP_ONLY_LATEST bounds image acquisition; this gate rejects duplicate,
 * zero or out-of-order timestamps before native image processing.
 *
 * A geometry change starts a new segment (the tracking reference must be invalidated),
 * but frame IDs and timestamp order remain monotonic within the same session.
 * Create a fresh gate per analyzer; [reset] is for explicit session restart only.
 */
internal class FrameOrderGate {
    private var lastTimestampNs = 0L
    private var lastGeometry: FrameGeometry? = null
    private var nextFrameId = 0L
    private var segmentId = 0L

    var rejectedFrames: Long = 0L
        private set

    fun accept(timestampNs: Long, geometry: FrameGeometry): FrameMetadata? {
        if (timestampNs <= 0L || timestampNs <= lastTimestampNs) {
            rejectedFrames++
            return null
        }
        if (geometry != lastGeometry) {
            segmentId++
            lastGeometry = geometry
        }
        lastTimestampNs = timestampNs
        nextFrameId++
        return FrameMetadata(nextFrameId, segmentId, timestampNs, geometry)
    }

    fun reset() {
        lastTimestampNs = 0L
        lastGeometry = null
        nextFrameId = 0L
        segmentId = 0L
        rejectedFrames = 0L
    }
}
