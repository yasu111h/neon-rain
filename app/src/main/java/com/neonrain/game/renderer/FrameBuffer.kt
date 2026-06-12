package com.neonrain.game.renderer

import android.opengl.GLES30

/**
 * FBO（danmaku-3dより流用、HDR対応版）。
 * RGBA16Fが使えない端末向けに R11G11B10F フォールバックを持つ。
 */
class FrameBuffer(
    val width: Int,
    val height: Int,
    private val useHDR: Boolean = false,
    private val useDepth: Boolean = false
) {
    var fboId = 0; private set
    var textureId = 0; private set
    private var depthRBO = 0

    fun init() {
        val fboArr = IntArray(1)
        GLES30.glGenFramebuffers(1, fboArr, 0)
        fboId = fboArr[0]

        val texArr = IntArray(1)
        GLES30.glGenTextures(1, texArr, 0)
        textureId = texArr[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        if (useHDR) {
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F,
                width, height, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null
            )
        } else {
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
                width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, textureId, 0
        )

        if (useDepth) {
            val rboArr = IntArray(1)
            GLES30.glGenRenderbuffers(1, rboArr, 0)
            depthRBO = rboArr[0]
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRBO)
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, width, height)
            GLES30.glFramebufferRenderbuffer(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
                GLES30.GL_RENDERBUFFER, depthRBO
            )
        }

        // HDRが未対応の場合のフォールバック
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE && useHDR) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R11F_G11F_B10F,
                width, height, 0, GLES30.GL_RGB, GLES30.GL_UNSIGNED_INT_10F_11F_11F_REV, null
            )
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glViewport(0, 0, width, height)
    }

    fun bindTexture(unit: Int = 0) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
    }

    fun delete() {
        if (fboId > 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
        if (textureId > 0) GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
        if (depthRBO > 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(depthRBO), 0)
        fboId = 0; textureId = 0; depthRBO = 0
    }
}
