package com.neonrain.game.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

object BufferUtil {
    fun createFloatBuffer(capacity: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(capacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    fun createIntBuffer(capacity: Int): IntBuffer {
        return ByteBuffer.allocateDirect(capacity * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
    }

    fun floatArrayToBuffer(array: FloatArray): FloatBuffer {
        val buffer = createFloatBuffer(array.size)
        buffer.put(array)
        buffer.position(0)
        return buffer
    }
}
