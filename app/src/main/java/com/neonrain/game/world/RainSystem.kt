package com.neonrain.game.world

import android.opengl.GLES30
import com.neonrain.game.core.GameConfig
import com.neonrain.game.renderer.Camera
import com.neonrain.game.renderer.InstanceRenderer
import com.neonrain.game.renderer.Mesh
import com.neonrain.game.renderer.MeshBuilder
import com.neonrain.game.renderer.ShaderProgram
import com.neonrain.game.util.MathUtil

/**
 * 雨パーティクル（技術設計書 §3.2 遠景層）。
 * インスタンスド・ビルボード細長クアッド×1500本。落下アニメはシェーダー内
 * （y = mod(y0 - uTime*speed, boxH)）で行い、CPU更新ゼロ・ドローコール1。
 */
class RainSystem {

    private lateinit var shader: ShaderProgram
    private lateinit var quad: Mesh
    private lateinit var instances: InstanceRenderer

    fun init() {
        shader = ShaderProgram(VERT, FRAG)
        quad = MeshBuilder.buildQuad().also { it.init() }
        instances = InstanceRenderer(GameConfig.RAIN_COUNT).also { it.init() }

        // 雨筋の初期配置はビルド時に一度だけ書き込む（以後VBO更新なし）
        instances.beginUpdate()
        for (i in 0 until GameConfig.RAIN_COUNT) {
            val x = (MathUtil.hash(i * 3 + 1) - 0.5f) * 30f
            val y = MathUtil.hash(i * 3 + 2) * BOX_H
            val z = MathUtil.hash(i * 3 + 3) * 70f - 5f
            val speed = 18f + MathUtil.hash(i * 7) * 10f
            val len = 0.45f + MathUtil.hash(i * 11) * 0.5f
            instances.addInstance(
                x, y, z,
                0.022f, len, 1f,
                0.62f, 0.70f, 0.85f, 0.30f,
                speed, MathUtil.hash(i * 13)
            )
        }
        instances.endUpdate()
    }

    /** Pass 0d: 半透明・加算気味αブレンド・深度書き込みOFF */
    fun draw(camera: Camera, time: Float, boost: Float, glow: Float) {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)

        shader.use()
        shader.setUniformMatrix4fv("uVP", camera.vpMatrix)
        shader.setUniform1f("uTime", time)
        shader.setUniform1f("uBoxH", BOX_H)
        shader.setUniform1f("uTilt", 0.10f + boost * 0.30f) // 速度に応じて雨筋を後方に傾ける
        shader.setUniform1f("uGlow", glow)
        shader.setUniform3f("uCamPos", camera.camX, camera.camY, camera.camZ)

        quad.bind()
        instances.bindAttributes()
        quad.drawInstanced(instances.instanceCount)
        instances.unbindAttributes()
        quad.unbind()

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun delete() {
        shader.delete()
        quad.delete()
        instances.delete()
    }

    companion object {
        const val BOX_H = 26f

        private const val VERT = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
layout(location = 3) in vec3 aIPos;
layout(location = 4) in vec3 aIScale;
layout(location = 5) in vec4 aIColor;
layout(location = 6) in vec2 aIMisc;
uniform mat4 uVP;
uniform float uTime;
uniform float uBoxH;
uniform float uTilt;
uniform vec3 uCamPos;
out vec2 vUV;
out vec4 vColor;
void main() {
    float speed = aIMisc.x;
    float y = mod(aIPos.y - uTime * speed, uBoxH);
    vec3 center = vec3(aIPos.x, y, aIPos.z);

    // 落下方向（後方へ傾く）に沿った細長クアッド
    vec3 fall = normalize(vec3(0.0, -1.0, -uTilt * 3.0));
    vec3 toCam = normalize(uCamPos - center);
    vec3 side = normalize(cross(fall, toCam));
    vec3 world = center
        + side * aPosition.x * aIScale.x
        + fall * (-aPosition.y) * aIScale.y;

    gl_Position = uVP * vec4(world, 1.0);
    vUV = aTexCoord;
    // 奥は薄く・地面付近で僅かにフェード
    float distFade = clamp(1.0 - center.z / 80.0, 0.15, 1.0);
    float headFade = clamp(y / 3.0, 0.3, 1.0);
    vColor = vec4(aIColor.rgb, aIColor.a * distFade * headFade);
}
"""
        private const val FRAG = """#version 300 es
precision mediump float;
in vec2 vUV;
in vec4 vColor;
out vec4 fragColor;
uniform float uGlow;
void main() {
    // 中心が明るい雨筋。コンボで僅かにネオン色を帯びる
    float core = pow(1.0 - abs(vUV.x - 0.5) * 2.0, 2.0);
    float tail = smoothstep(0.0, 0.25, vUV.y) * smoothstep(1.0, 0.6, vUV.y);
    vec3 col = mix(vColor.rgb, vec3(0.55, 0.85, 1.05), uGlow * 0.4);
    fragColor = vec4(col, vColor.a * core * tail);
}
"""
    }
}
