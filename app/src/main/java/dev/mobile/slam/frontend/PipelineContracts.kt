package dev.mobile.slam.frontend

/**
 * Integer pixel bounds in the unrotated CameraX ImageProxy coordinate system.
 * Right and bottom are exclusive, as in android.graphics.Rect.
 */
internal data class PixelCrop(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Geometry reported for one analyzed Y plane. Feature coordinates use the full, unrotated
 * ImageProxy pixel grid before applying cropRect and rotation to the camera preview.
 */
internal data class FrameGeometry(
    val widthPx: Int,
    val heightPx: Int,
    val crop: PixelCrop,
    val rotationDegrees: Int,
) {
    init {
        require(widthPx > 0 && heightPx > 0)
        require(crop.left >= 0 && crop.top >= 0)
        require(crop.right <= widthPx && crop.bottom <= heightPx)
        require(crop.width > 0 && crop.height > 0)
        require(rotationDegrees in setOf(0, 90, 180, 270))
    }
}

/**
 * Scalar-only frame contract for deterministic processing and UI snapshots.
 * Timestamp units are ns in the CameraX clock domain; comparisons with IMU timestamps
 * are forbidden without an explicit compatible Camera2 time source.
 *
 * No native Mat, ImageProxy, or mutable image buffer may escape the analyzer thread.
 */
internal data class FrameMetadata(
    val frameId: Long,
    val segmentId: Long,
    val timestampNs: Long,
    val geometry: FrameGeometry,
) {
    init {
        require(frameId > 0 && segmentId > 0 && timestampNs > 0)
    }
}

/** Image-space point in unrotated ImageProxy pixels; not a triangulated map point. */
internal data class ImageFeature(val xPx: Float, val yPx: Float) {
    init {
        require(xPx.isFinite() && yPx.isFinite())
    }
}

/** Status of a correspondence after future tracking; DETECTED alone is not a track. */
internal enum class FeatureStatus {
    DETECTED,
    TRACKED,
    REJECTED,
    LOST,
}

/** Stable ID is assigned across actual correspondences, not FAST rediscoveries. */
internal data class FeatureTrack(
    val id: Long,
    val current: ImageFeature,
    val ageFrames: Int,
    val status: FeatureStatus,
    val previous: ImageFeature? = null,
) {
    init {
        require(id > 0 && ageFrames >= 1)
    }
}

/** Vision tracking is independent from pose/mapping and starts with detections only. */
internal enum class TrackingPhase {
    WAITING_FOR_FRAME,
    DETECTING,
    INITIALIZING,
    TRACKING,
    LOST,
}

/**
 * Explicit transitions prevent treating independent FAST detections as initialized SLAM.
 * Methods are intended for the single tracking thread; the current M1 HUD does not use this.
 */
internal class TrackingStateMachine {
    var phase: TrackingPhase = TrackingPhase.WAITING_FOR_FRAME
        private set

    fun onDetections() {
        if (phase == TrackingPhase.WAITING_FOR_FRAME || phase == TrackingPhase.LOST) {
            phase = TrackingPhase.DETECTING
        }
    }

    fun beginInitialization(): Boolean {
        if (phase != TrackingPhase.DETECTING) return false
        phase = TrackingPhase.INITIALIZING
        return true
    }

    fun initializationSucceeded(): Boolean {
        if (phase != TrackingPhase.INITIALIZING) return false
        phase = TrackingPhase.TRACKING
        return true
    }

    fun initializationFailed() {
        if (phase == TrackingPhase.INITIALIZING) phase = TrackingPhase.DETECTING
    }

    fun trackingLost() {
        if (phase == TrackingPhase.TRACKING) phase = TrackingPhase.LOST
    }

    fun reset() {
        phase = TrackingPhase.WAITING_FOR_FRAME
    }
}

/** Right-handed 3D vector in arbitrary monocular reconstruction units. */
internal data class Vector3(val x: Double, val y: Double, val z: Double)

/** Scalar-first quaternion; future estimators must normalize before use. */
internal data class RotationQuaternion(val w: Double, val x: Double, val y: Double, val z: Double)

/**
 * Transformation world<-camera, i.e. p_world = R_world_camera * p_camera + t_world_camera.
 * Translation has unknown metric scale; it must not be labelled metres without a scale source.
 */
internal data class CameraPose(
    val rotationWorldFromCamera: RotationQuaternion,
    val translationWorldFromCamera: Vector3,
    val metricScaleKnown: Boolean = false,
)

/** A reconstructed candidate position and its observation support; not used by FAST mode. */
internal data class MapPoint(
    val id: Long,
    val positionWorld: Vector3,
    val observations: Int,
) {
    init {
        require(id > 0 && observations >= 1)
    }
}

/** Immutable keyframe reference without any native image ownership. */
internal data class Keyframe(
    val id: Long,
    val sourceFrame: FrameMetadata,
    val pose: CameraPose,
    val featureIds: List<Long>,
) {
    init {
        require(id > 0 && featureIds.all { it > 0 })
    }
}

/** Non-overlapping per-frame counters; tracking is not a camera pose estimate. */
internal data class TrackingMetrics(
    val frameId: Long,
    val detected: Int,
    val tracked: Int,
    val rejected: Int,
    val lost: Int,
) {
    init {
        require(frameId > 0)
        require(detected >= 0 && tracked >= 0 && rejected >= 0 && lost >= 0)
    }
}
