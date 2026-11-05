package dev.mobile.slam.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackManagerTest {
    private val settings = Settings(
        kltMaxTracks = 6,
        kltMinTracks = 3,
        kltGridColumns = 2,
        kltGridRows = 1,
        kltMinSeparationPx = 5f,
    )
    private val geometry = FrameGeometry(100, 80, PixelCrop(0, 0, 100, 80), 0)

    private fun frame(id: Long) = FrameMetadata(id, 1, id * 1_000_000L, geometry)

    @Test
    fun seedsGetUniqueIdsAndBecomeTrackedOnlyAfterValidatedFlow() {
        val manager = TrackManager(settings)
        val first = manager.update(frame(1), emptyList(), listOf(ImageFeature(10f, 10f), ImageFeature(70f, 30f)))
        assertEquals(2, first.metrics.detected)
        assertEquals(0, first.metrics.tracked)
        assertTrue(first.tracks.all { it.ageFrames == 1 && it.status == FeatureStatus.DETECTED })

        val a = first.tracks[0]
        val b = first.tracks[1]
        val second = manager.update(
            frame(2),
            listOf(
                FlowEstimate(a.id, ImageFeature(12f, 10f), 0.2f, true, true),
                FlowEstimate(b.id, ImageFeature(72f, 30f), 0.1f, true, true),
            ),
            emptyList(),
        )
        assertEquals(2, second.metrics.tracked)
        assertEquals(0, second.metrics.detected)
        assertEquals(listOf(a.id, b.id), second.tracks.map { it.id })
        assertTrue(second.tracks.all { it.ageFrames == 2 && it.status == FeatureStatus.TRACKED })
        assertEquals(ImageFeature(10f, 10f), second.tracks.first().previous)
    }

    @Test
    fun rejectsBadForwardBackwardResultsAndDoesNotReuseIds() {
        val manager = TrackManager(settings)
        val first = manager.update(frame(1), emptyList(), listOf(ImageFeature(10f, 10f), ImageFeature(70f, 30f)))
        val idA = first.tracks.first().id
        val idB = first.tracks.last().id
        val second = manager.update(
            frame(2),
            listOf(
                FlowEstimate(idA, ImageFeature(12f, 10f), 4f, true, true),
                FlowEstimate(idB, ImageFeature(72f, 30f), 0f, true, false),
            ),
            listOf(ImageFeature(50f, 20f)),
        )
        assertEquals(0, second.metrics.tracked)
        assertEquals(2, second.metrics.rejected)
        assertEquals(1, second.metrics.detected)
        assertNotEquals(idA, second.tracks.first().id)
        assertNotEquals(idB, second.tracks.first().id)
        assertFalse(second.tracks.any { it.status == FeatureStatus.TRACKED })
    }

    @Test
    fun boundedSpatialSelectionPreventsDuplicatesAndOutsideCrop() {
        val manager = TrackManager(settings)
        val points = listOf(
            ImageFeature(10f, 10f),
            ImageFeature(10.1f, 10.1f),
            ImageFeature(20f, 20f),
            ImageFeature(30f, 30f),
            ImageFeature(40f, 40f),
            ImageFeature(70f, 10f),
            ImageFeature(80f, 20f),
            ImageFeature(90f, 30f),
            ImageFeature(95f, 79f),
            ImageFeature(100f, 20f),
        )
        val output = manager.update(frame(1), emptyList(), points)
        assertEquals(6, output.tracks.size)
        assertEquals(6, output.metrics.detected)
        assertEquals(output.tracks.size, output.tracks.map { it.id }.toSet().size)
        assertFalse(output.tracks.any { it.current.xPx >= 100f })
    }

    @Test
    fun resetDropsTemporalHistoryButPreservesIdUniqueness() {
        val manager = TrackManager(settings)
        val first = manager.update(frame(1), emptyList(), listOf(ImageFeature(10f, 10f)))
        manager.reset()
        assertTrue(manager.previousTracks().isEmpty())
        val next = manager.update(frame(2), emptyList(), listOf(ImageFeature(12f, 10f)))
        assertTrue(next.tracks.single().id > first.tracks.single().id)
        assertEquals(1, next.tracks.single().ageFrames)
    }

    @Test
    fun flowOutsideCropCannotPersistTrack() {
        val manager = TrackManager(settings)
        val first = manager.update(frame(1), emptyList(), listOf(ImageFeature(20f, 20f)))
        val second = manager.update(
            frame(2),
            listOf(FlowEstimate(first.tracks.single().id, ImageFeature(101f, 20f), 0f, true, true)),
            emptyList(),
        )
        assertEquals(1, second.metrics.rejected)
        assertTrue(second.tracks.isEmpty())
    }
}
