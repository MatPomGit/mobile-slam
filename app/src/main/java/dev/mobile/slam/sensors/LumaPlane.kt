package dev.mobile.slam.sensors

import java.nio.ByteBuffer

/** Copies the unrotated Y plane, respecting row padding, pixel stride and buffer position. */
internal object LumaPlane {
    fun copy(source: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): ByteArray {
        require(width > 0 && height > 0 && rowStride > 0 && pixelStride > 0)
        val buffer = source.duplicate()
        val offset = buffer.position()
        val output = ByteArray(width * height)
        if (pixelStride == 1) {
            for (row in 0 until height) {
                buffer.position(offset + row * rowStride)
                buffer.get(output, row * width, width)
            }
        } else {
            for (row in 0 until height) {
                for (column in 0 until width) {
                    output[row * width + column] =
                        buffer.get(offset + row * rowStride + column * pixelStride)
                }
            }
        }
        return output
    }
}
