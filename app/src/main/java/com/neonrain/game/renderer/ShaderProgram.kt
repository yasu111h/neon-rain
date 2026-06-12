package com.neonrain.game.renderer

import android.opengl.GLES30
import android.util.Log

/** シェーダー管理（danmaku-3dより流用） */
class ShaderProgram(
    vertexSource: String,
    fragmentSource: String
) {
    val programId: Int
    private val uniformLocations = HashMap<String, Int>()

    init {
        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertShader)
        GLES30.glAttachShader(programId, fragShader)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            Log.e("ShaderProgram", "Link failed: $log")
        }

        GLES30.glDeleteShader(vertShader)
        GLES30.glDeleteShader(fragShader)
    }

    fun use() = GLES30.glUseProgram(programId)

    fun getUniformLocation(name: String): Int {
        return uniformLocations.getOrPut(name) {
            GLES30.glGetUniformLocation(programId, name)
        }
    }

    fun setUniformMatrix4fv(name: String, matrix: FloatArray) {
        GLES30.glUniformMatrix4fv(getUniformLocation(name), 1, false, matrix, 0)
    }

    fun setUniform1f(name: String, value: Float) {
        GLES30.glUniform1f(getUniformLocation(name), value)
    }

    fun setUniform2f(name: String, x: Float, y: Float) {
        GLES30.glUniform2f(getUniformLocation(name), x, y)
    }

    fun setUniform3f(name: String, x: Float, y: Float, z: Float) {
        GLES30.glUniform3f(getUniformLocation(name), x, y, z)
    }

    fun setUniform3f(name: String, v: FloatArray) {
        GLES30.glUniform3f(getUniformLocation(name), v[0], v[1], v[2])
    }

    fun setUniform4f(name: String, x: Float, y: Float, z: Float, w: Float) {
        GLES30.glUniform4f(getUniformLocation(name), x, y, z, w)
    }

    fun setUniform1i(name: String, value: Int) {
        GLES30.glUniform1i(getUniformLocation(name), value)
    }

    fun delete() = GLES30.glDeleteProgram(programId)

    companion object {
        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(shader)
                val typeName = if (type == GLES30.GL_VERTEX_SHADER) "vertex" else "fragment"
                Log.e("ShaderProgram", "$typeName shader compile failed: $log")
                GLES30.glDeleteShader(shader)
                return 0
            }
            return shader
        }
    }
}
