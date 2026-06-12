package com.neonrain.game.renderer

import android.opengl.GLES30

/**
 * ブルーム（hanabi BloomPass流用改造版）。
 * 明部抽出(threshold=1.0, HDR前提) → 13tap相当ガウスぼかし×2周（1/4解像度）。
 */
class BloomPass {

    private lateinit var brightShader: ShaderProgram
    private lateinit var blurShader: ShaderProgram
    private lateinit var quad: Mesh

    private var brightFBO: FrameBuffer? = null
    private var pingFBO: FrameBuffer? = null
    private var pongFBO: FrameBuffer? = null

    /** 最終的なブルームテクスチャ */
    val bloomTexture: Int get() = pongFBO?.textureId ?: 0

    fun init(sceneW: Int, sceneH: Int, quadMesh: Mesh) {
        quad = quadMesh
        brightShader = ShaderProgram(PASS_VERT, BRIGHT_FRAG)
        blurShader = ShaderProgram(PASS_VERT, BLUR_FRAG)
        resize(sceneW, sceneH)
    }

    fun resize(sceneW: Int, sceneH: Int) {
        brightFBO?.delete(); pingFBO?.delete(); pongFBO?.delete()
        val w = (sceneW / 4).coerceAtLeast(8)
        val h = (sceneH / 4).coerceAtLeast(8)
        brightFBO = FrameBuffer(w, h, useHDR = true).also { it.init() }
        pingFBO = FrameBuffer(w, h, useHDR = true).also { it.init() }
        pongFBO = FrameBuffer(w, h, useHDR = true).also { it.init() }
    }

    fun render(sceneTexture: Int, threshold: Float) {
        val bright = brightFBO ?: return
        val ping = pingFBO ?: return
        val pong = pongFBO ?: return

        // 明部抽出
        bright.bind()
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        brightShader.use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTexture)
        brightShader.setUniform1i("uTexture", 0)
        brightShader.setUniform1f("uThreshold", threshold)
        drawQuad()

        // ぼかし2周（横→縦→横→縦）で滲み半径を拡大
        var src: FrameBuffer = bright
        for (i in 0 until 2) {
            ping.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            blurShader.use()
            src.bindTexture(0)
            blurShader.setUniform1i("uTexture", 0)
            blurShader.setUniform2f("uDirection", 1f / ping.width, 0f)
            drawQuad()

            pong.bind()
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            blurShader.use()
            ping.bindTexture(0)
            blurShader.setUniform1i("uTexture", 0)
            blurShader.setUniform2f("uDirection", 0f, 1f / pong.height)
            drawQuad()
            src = pong
        }
    }

    private fun drawQuad() {
        quad.bind()
        quad.draw()
        quad.unbind()
    }

    fun delete() {
        brightFBO?.delete(); pingFBO?.delete(); pongFBO?.delete()
        brightShader.delete(); blurShader.delete()
    }

    companion object {
        private const val PASS_VERT = """#version 300 es
precision mediump float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vUV;
void main() {
    gl_Position = vec4(aPosition, 1.0);
    vUV = aTexCoord;
}
"""
        private const val BRIGHT_FRAG = """#version 300 es
precision mediump float;
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uTexture;
uniform float uThreshold;
void main() {
    vec3 c = texture(uTexture, vUV).rgb;
    float br = max(max(c.r, c.g), c.b);
    // ソフトニー付き抽出（HDR値だけが滲む）
    float knee = uThreshold * 0.5;
    float soft = clamp(br - uThreshold + knee, 0.0, knee * 2.0);
    soft = soft * soft / (4.0 * knee + 1e-4);
    float w = max(soft, br - uThreshold) / max(br, 1e-4);
    fragColor = vec4(c * max(w, 0.0), 1.0);
}
"""
        private const val BLUR_FRAG = """#version 300 es
precision mediump float;
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uTexture;
uniform vec2 uDirection;
void main() {
    vec3 result = texture(uTexture, vUV).rgb * 0.227027;
    vec2 off1 = uDirection * 1.3846153;
    vec2 off2 = uDirection * 3.2307692;
    result += texture(uTexture, vUV + off1).rgb * 0.3162162;
    result += texture(uTexture, vUV - off1).rgb * 0.3162162;
    result += texture(uTexture, vUV + off2).rgb * 0.0702703;
    result += texture(uTexture, vUV - off2).rgb * 0.0702703;
    fragColor = vec4(result, 1.0);
}
"""
    }
}
