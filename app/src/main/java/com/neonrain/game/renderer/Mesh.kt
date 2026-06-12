package com.neonrain.game.renderer

import android.opengl.GLES30
import com.neonrain.game.util.BufferUtil

/** VAO/VBOメッシュ（danmaku-3dより流用） */
class Mesh(
    private val vertexData: FloatArray,
    private val vertexCount: Int,
    private val stride: Int,
    private val hasNormals: Boolean = false,
    private val hasUVs: Boolean = false
) {
    private var vbo = 0
    private var vao = 0
    private var initialized = false

    fun init() {
        if (initialized) return
        val vaoArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        vao = vaoArr[0]

        val vboArr = IntArray(1)
        GLES30.glGenBuffers(1, vboArr, 0)
        vbo = vboArr[0]

        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)

        val buffer = BufferUtil.floatArrayToBuffer(vertexData)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexData.size * 4, buffer, GLES30.GL_STATIC_DRAW)

        val strideBytes = stride * 4
        var offset = 0

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, strideBytes, offset)
        offset += 3 * 4

        if (hasNormals) {
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, strideBytes, offset)
            offset += 3 * 4
        }

        if (hasUVs) {
            val uvAttrib = if (hasNormals) 2 else 1
            GLES30.glEnableVertexAttribArray(uvAttrib)
            GLES30.glVertexAttribPointer(uvAttrib, 2, GLES30.GL_FLOAT, false, strideBytes, offset)
        }

        GLES30.glBindVertexArray(0)
        initialized = true
    }

    fun bind() = GLES30.glBindVertexArray(vao)
    fun draw() = GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
    fun drawInstanced(instanceCount: Int) =
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, vertexCount, instanceCount)
    fun unbind() = GLES30.glBindVertexArray(0)

    fun delete() {
        if (vbo > 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (vao > 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        initialized = false
    }
}
