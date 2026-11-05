package dev.mobile.slam.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FrameOrderGateTest {
    private val portrait = FrameGeometry(640, 480, PixelCrop(25, 0, 616, 480), 90)
    private val landscape = FrameGeometry(640, 480, PixelCrop(0, 0, 640, 480), 0)

    @Test
    fun acceptsStrictlyIncreasingCameraTimestampsWithStableSegment() {
        val gate = FrameOrderGate()
        val a = gate.accept(100L, portrait)!!
        val b = gate.accept(150L, portrait)!!
        assertEquals(1L, a.frameId)
        assertEquals(2L, b.frameId)
        assertEquals(a.segmentId, b.segmentId)
        assertEquals(150L, b.timestampNs)
        assertEquals(0L, gate.rejectedFrames)
    }

    @Test
    fun rejectsDuplicateBackwardAndInvalidTimestampsWithoutAdvancingId() {
        val gate = FrameOrderGate()
        assertNull(gate.accept(0L, portrait))
        assertNotNull(gate.accept(100L, portrait))
        assertNull(gate.accept(100L, portrait))
        assertNull(gate.accept(99L, portrait))
        assertNull(gate.accept(-10L, portrait))
        val next = gate.accept(101L, portrait)!!
        assertEquals(2L, next.frameId)
        assertEquals(4L, gate.rejectedFrames)
    }

    @Test
    fun geometryChangesCreateNewSegmentWithoutResettingIdsOrTime() {
        val gate = FrameOrderGate()
        val a = gate.accept(100L, portrait)!!
        val b = gate.accept(101L, landscape)!!
        assertEquals(1L, a.segmentId)
        assertEquals(2L, b.segmentId)
        assertEquals(2L, b.frameId)
        assertNull(gate.accept(50L, portrait))
        val c = gate.accept(200L, portrait)!!
        assertEquals(3L, c.segmentId)
        assertEquals(3L, c.frameId)
    }

    @Test
    fun sessionResetStartsNewIdsAndDiscardsPreviousOrderingState() {
        val gate = FrameOrderGate()
        gate.accept(500L, portrait)
        gate.accept(500L, portrait)
        gate.reset()
        val first = gate.accept(100L, portrait)!!
        assertEquals(1L, first.frameId)
        assertEquals(1L, first.segmentId)
        assertEquals(0L, gate.rejectedFrames)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCropOutsideImageBounds() {
        FrameGeometry(640, 480, PixelCrop(0, 0, 641, 480), 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedRotation() {
        FrameGeometry(640, 480, PixelCrop(0, 0, 640, 480), 45)
    }
}
