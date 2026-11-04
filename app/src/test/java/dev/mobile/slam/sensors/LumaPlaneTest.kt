package dev.mobile.slam.sensors

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class LumaPlaneTest {
    @Test
    fun copiesPaddedRowsWithoutPaddingBytes() {
        val source = ByteBuffer.wrap(byteArrayOf(1, 2, 99, 99, 3, 4, 88, 88))
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4),
            LumaPlane.copy(source, width = 2, height = 2, rowStride = 4, pixelStride = 1),
        )
    }

    @Test
    fun copiesNonUnitPixelStrideAndHonoursBufferPosition() {
        val source = ByteBuffer.wrap(byteArrayOf(99, 1, 0, 2, 0, 3, 0, 4, 0))
        source.position(1)
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4),
            LumaPlane.copy(source, width = 2, height = 2, rowStride = 4, pixelStride = 2),
        )
    }
}
