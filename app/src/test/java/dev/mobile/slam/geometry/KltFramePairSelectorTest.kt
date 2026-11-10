package dev.mobile.slam.geometry

import dev.mobile.slam.frontend.FeatureStatus
import dev.mobile.slam.frontend.FeatureTrack
import dev.mobile.slam.frontend.FrameGeometry
import dev.mobile.slam.frontend.FrameMetadata
import dev.mobile.slam.frontend.ImageFeature
import dev.mobile.slam.frontend.PixelCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KltFramePairSelectorTest {
    private val geometry = FrameGeometry(640, 480, PixelCrop(20, 10, 620, 470), 0)

    private fun frame(
        id: Long,
        segment: Long = 1L,
        timeNs: Long = 1_000_000_000L + id * 33_000_000L,
        shape: FrameGeometry = geometry,
    ) = FrameMetadata(id, segment, timeNs, shape)

    private fun tracks(dx: Float = 0f, dy: Float = 0f, status: FeatureStatus = FeatureStatus.TRACKED, count: Int = 64): List<FeatureTrack> =
        (0 until count).map { index ->
            val x = 70f + index % 8 * 65f + dx
            val y = 50f + index / 8 * 50f + dy
            FeatureTrack(
                id = index + 1L,
                current = ImageFeature(x, y),
                ageFrames = if (status == FeatureStatus.DETECTED) 1 else 2,
                status = status,
            )
        }

    @Test
    fun collectsStableIdsAndProducesCropRelativeCandidateOnlyOnce() {
        val selector = KltFramePairSelector(FramePairConfig(minFrameGap = 2L))
        val first = selector.observe(frame(1), tracks(status = FeatureStatus.DETECTED))
        assertEquals(FramePairState.WAITING, first.state)
        assertNull(first.pair)
        val second = selector.observe(frame(2), tracks(dx = 5f))
        assertEquals(FramePairState.TOO_SOON, second.state)
        assertNull(second.pair)
        val third = selector.observe(frame(3), tracks(dx = 12f))
        assertEquals(FramePairState.READY, third.state)
        assertEquals(1L, third.acceptedPairs)
        assertEquals(64, third.sharedTracks)
        val pair = third.pair!!
        assertEquals(1L, pair.first.frameId)
        assertEquals(3L, pair.second.frameId)
        assertEquals(64, pair.matches.size)
        assertEquals((1L..64L).toList(), pair.matches.map { it.trackId })
        assertEquals(50.0, pair.matches.first().first.x, 1e-9)
        assertEquals(62.0, pair.matches.first().second.x, 1e-9)
        assertEquals(40.0, pair.matches.first().first.y, 1e-9)
        assertEquals(12.0, third.medianMotionPx, 1e-9)
        val next = selector.observe(frame(4), tracks(dx = 15f))
        assertEquals(FramePairState.TOO_SOON, next.state)
        assertEquals(1L, next.acceptedPairs)
    }

    @Test
    fun changingFrameGeometryOrSegmentDiscardsOldAnchor() {
        val selector = KltFramePairSelector(FramePairConfig(minFrameGap = 1L))
        selector.observe(frame(1), tracks(status = FeatureStatus.DETECTED))
        val rotated = geometry.copy(rotationDegrees = 90)
        val result = selector.observe(frame(2, segment = 2, shape = rotated), tracks(dx = 12f))
        assertEquals(FramePairState.GEOMETRY_CHANGED, result.state)
        assertNull(result.pair)
        val next = selector.observe(frame(3, segment = 2, shape = rotated), tracks(dx = 24f))
        assertEquals(FramePairState.READY, next.state)
        assertEquals(2L, next.pair!!.first.frameId)
    }

    @Test
    fun timestampGapAndAnchorAgePreventStalePairing() {
        val selector = KltFramePairSelector(
            FramePairConfig(minFrameGap = 3L, maxFrameGapMs = 100L, maxAnchorAgeMs = 150L),
        )
        selector.observe(frame(1, timeNs = 1_000_000_000L), tracks(status = FeatureStatus.DETECTED))
        val gap = selector.observe(frame(2, timeNs = 1_300_000_000L), tracks(dx = 12f))
        assertEquals(FramePairState.TIME_GAP, gap.state)
        assertNull(gap.pair)
        assertEquals(
            FramePairState.TOO_SOON,
            selector.observe(frame(3, timeNs = 1_390_000_000L), tracks(dx = 24f)).state,
        )
        val expired = selector.observe(frame(4, timeNs = 1_480_000_000L), tracks(dx = 30f))
        assertEquals(FramePairState.ANCHOR_EXPIRED, expired.state)
        assertNull(expired.pair)
    }

    @Test
    fun ignoresNewDetectionsAndRequiresSharedTrackIds() {
        val selector = KltFramePairSelector(FramePairConfig(minFrameGap = 1L))
        selector.observe(frame(1), tracks(status = FeatureStatus.DETECTED))
        val fresh = tracks(dx = 12f).map {
            it.copy(id = it.id + 1000L, status = FeatureStatus.DETECTED, ageFrames = 1)
        }
        val result = selector.observe(frame(2), fresh)
        assertEquals(FramePairState.TOO_FEW_SHARED, result.state)
        assertEquals(0, result.sharedTracks)
        assertNull(result.pair)
    }

    @Test
    fun rejectsDuplicateTrackIdsWithoutReusingAnchor() {
        val selector = KltFramePairSelector(FramePairConfig(minFrameGap = 1L))
        selector.observe(frame(1), tracks(status = FeatureStatus.DETECTED))
        val corrupt = tracks(dx = 12f).toMutableList()
        corrupt[1] = corrupt[1].copy(id = corrupt[0].id)
        assertEquals(FramePairState.INVALID_TRACKS, selector.observe(frame(2), corrupt).state)
        assertEquals(FramePairState.WAITING, selector.observe(frame(3), tracks()).state)
    }

    @Test
    fun rejectsLowCoverageAndLowMotionWithoutInferringTranslation() {
        val selector = KltFramePairSelector(FramePairConfig(minFrameGap = 1L))
        val clustered = (0 until 60).map { index ->
            FeatureTrack(
                id = index + 1L,
                current = ImageFeature(70f + index % 10 * 2f, 50f + index / 10 * 2f),
                ageFrames = 2,
                status = FeatureStatus.TRACKED,
            )
        }
        selector.observe(frame(1), clustered)
        assertEquals(
            FramePairState.LOW_COVERAGE,
            selector.observe(
                frame(2),
                clustered.map { it.copy(current = ImageFeature(it.current.xPx + 15f, it.current.yPx)) },
            ).state,
        )

        val motion = KltFramePairSelector(FramePairConfig(minFrameGap = 1L))
        motion.observe(frame(1), tracks(status = FeatureStatus.DETECTED))
        assertEquals(
            FramePairState.LOW_MOTION,
            motion.observe(frame(2), tracks(dx = 0.1f)).state,
        )
    }

    @Test
    fun boundsMemoryAndReturnsIndependentValueCopies() {
        val selector = KltFramePairSelector(FramePairConfig(minFrameGap = 1L, maxStoredTracks = 32))
        selector.observe(frame(1), tracks(status = FeatureStatus.DETECTED))
        val result = selector.observe(frame(2), tracks(dx = 12f))
        assertEquals(FramePairState.READY, result.state)
        assertEquals(32, result.pair!!.matches.size)
        assertNotNull(result.pair)
        selector.reset()
        val fresh = selector.observe(frame(3), tracks())
        assertEquals(FramePairState.WAITING, fresh.state)
        assertEquals(0L, fresh.acceptedPairs)
        assertEquals(1L, selector.observe(frame(4), tracks(dx = 12f)).acceptedPairs)
        assertTrue(result.pair.matches.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCandidateCapacityIsRejected() {
        FramePairConfig(minSharedTracks = 30, maxStoredTracks = 20)
    }
}
