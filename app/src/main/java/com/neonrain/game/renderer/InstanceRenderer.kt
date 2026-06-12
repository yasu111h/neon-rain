package com.neonrain.game.renderer

import android.opengl.GLES30
import com.neonrain.game.util.BufferUtil
import java.nio.FloatBuffer

/**
 * インスタンス描画（danmaku-3dの設計を流用・レイアウト拡張版）。
 * 1インスタンス = 12 float:
 *   pos(x,y,z) + scale(x,y,z) + color(r,g,b) + intensity + misc(seed, flag)
 * attribロケーション: 3=aIPos, 4=aIScale, 5=aIColor(rgb+intensity), 6=aIMisc
 */
class InstanceRenderer(private val maxInstances: Int) {
    companion object {
        const val FLOATS = 12
        const val ATTRIB_START = 3
    }

    private var instanceVBO = 0
    private val instanceBuffer: FloatBuffer = BufferUtil.createFloatBuffer(maxInstances * FLOATS)
    private val instanceData = FloatArray(maxInstances * FLOATS)
    var instanceCount = 0; private set

    fun init() {
        val vboArr = IntArray(1)
        GLES30.glGenBuffers(1, vboArr, 0)
        instanceVBO = vboArr[0]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVBO)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, maxInstances * FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun beginUpdate() {
        instanceCount = 0
    }

    fun addInstance(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, intensity: Float,
        seed: Float = 0f, flag: Float = 0f
    ) {
        if (instanceCount >= maxInstances) return
        val base = instanceCount * FLOATS
        instanceData[base] = x
        instanceData[base + 1] = y
        instanceData[base + 2] = z
        instanceData[base + 3] = sx
        instanceData[base + 4] = sy
        instanceData[base + 5] = sz
        instanceData[base + 6] = r
        instanceData[base + 7] = g
        instanceData[base + 8] = b
        instanceData[base + 9] = intensity
        instanceData[base + 10] = seed
        instanceData[base + 11] = flag
        instanceCount++
    }

    fun endUpdate() {
        if (instanceCount == 0) return
        instanceBuffer.clear()
        instanceBuffer.put(instanceData, 0, instanceCount * FLOATS)
        instanceBuffer.position(0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVBO)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, instanceCount * FLOATS * 4, instanceBuffer)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    /** メッシュVAOバインド後に呼ぶ */
    fun bindAttributes() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceVBO)
        val stride = FLOATS * 4
        val a = ATTRIB_START

        GLES30.glEnableVertexAttribArray(a)
        GLES30.glVertexAttribPointer(a, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glVertexAttribDivisor(a, 1)

        GLES30.glEnableVertexAttribArray(a + 1)
        GLES30.glVertexAttribPointer(a + 1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glVertexAttribDivisor(a + 1, 1)

        GLES30.glEnableVertexAttribArray(a + 2)
        GLES30.glVertexAttribPointer(a + 2, 4, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glVertexAttribDivisor(a + 2, 1)

        GLES30.glEnableVertexAttribArray(a + 3)
        GLES30.glVertexAttribPointer(a + 3, 2, GLES30.GL_FLOAT, false, stride, 40)
        GLES30.glVertexAttribDivisor(a + 3, 1)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun unbindAttributes() {
        val a = ATTRIB_START
        for (i in 0 until 4) {
            GLES30.glVertexAttribDivisor(a + i, 0)
            GLES30.glDisableVertexAttribArray(a + i)
        }
    }

    fun delete() {
        if (instanceVBO > 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(instanceVBO), 0)
            instanceVBO = 0
        }
    }
}
