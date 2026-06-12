package com.neonrain.game.renderer

import android.opengl.GLES30

/**
 * 最終合成（技術設計書 §3.4）。
 * scene + bloom加算 → ACESトーンマップ → 彩度制御 → 色収差 → ビネット
 * → フィルムグレイン → レンズ上の雨滴歪み（画面上部1/3）を1パスで行う。
 */
class PostProcess {

    private lateinit var shader: ShaderProgram
    private lateinit var quad: Mesh

    // GameWorld側から毎フレーム更新されるuniform群
    var saturation = 1.0f
    var aberration = 0.0012f
    var vignette = 0.32f
    var bloomBoost = 0.5f
    var hitFlash = 0f
    var rainOnLens = 0.5f

    fun init(quadMesh: Mesh) {
        quad = quadMesh
        shader = ShaderProgram(VERT, FRAG)
    }

    fun render(sceneTexture: Int, bloomTexture: Int, screenW: Int, screenH: Int, time: Float) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenW, screenH)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        shader.use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTexture)
        shader.setUniform1i("uScene", 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexture)
        shader.setUniform1i("uBloom", 1)

        shader.setUniform1f("uSaturation", saturation)
        shader.setUniform1f("uAberration", aberration)
        shader.setUniform1f("uVignette", vignette)
        shader.setUniform1f("uBloomBoost", bloomBoost)
        shader.setUniform1f("uHitFlash", hitFlash)
        shader.setUniform1f("uRainAmount", rainOnLens)
        shader.setUniform1f("uTime", time)

        quad.bind()
        quad.draw()
        quad.unbind()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    fun delete() = shader.delete()

    companion object {
        private const val VERT = """#version 300 es
precision mediump float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vUV;
void main() {
    gl_Position = vec4(aPosition, 1.0);
    vUV = aTexCoord;
}
"""
        private const val FRAG = """#version 300 es
precision mediump float;
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uSaturation;
uniform float uAberration;
uniform float uVignette;
uniform float uBloomBoost;
uniform float uHitFlash;
uniform float uRainAmount;
uniform float uTime;

float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// レンズ上の雨滴（"Heartfelt"系の簡略版）。画面上部のみ・低密度
vec2 lensDrops(vec2 uv, float t) {
    if (uv.y < 0.6) return vec2(0.0);
    vec2 grid = vec2(14.0, 8.0);
    vec2 cell = floor(uv * grid);
    float rnd = hash2(cell);
    if (rnd > uRainAmount * 0.35) return vec2(0.0);
    // セル内で流れ落ちる水滴
    vec2 f = fract(uv * grid);
    float fallT = fract(t * (0.05 + rnd * 0.08) + rnd * 7.0);
    vec2 dropPos = vec2(0.3 + 0.4 * hash2(cell + 5.0), 1.0 - fallT);
    vec2 d = f - dropPos;
    d.y *= 1.8;
    float r = length(d);
    float drop = smoothstep(0.13, 0.0, r);
    return d * drop * 0.6;
}

void main() {
    vec2 uv = vUV;
    uv += lensDrops(uv, uTime);

    // 色収差（中心からの距離に応じてRGBをずらす）
    vec2 toC = uv - 0.5;
    float distC = length(toC);
    vec2 ab = toC * uAberration * (1.0 + distC * 3.0) * 10.0;
    vec3 scene;
    scene.r = texture(uScene, uv + ab).r;
    scene.g = texture(uScene, uv).g;
    scene.b = texture(uScene, uv - ab).b;

    vec3 bloom = texture(uBloom, uv).rgb;
    vec3 col = scene + bloom * uBloomBoost;

    // ACESトーンマップ
    col = aces(col);

    // 彩度制御（被弾0.4〜最大コンボ1.4）
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(luma), col, uSaturation);

    // 被弾時の赤ビネットフラッシュ
    float vig = smoothstep(0.85, 0.25, distC);
    col *= mix(1.0 - uVignette, 1.0, vig);
    col += vec3(1.0, 0.1, 0.18) * uHitFlash * (1.0 - vig) * 0.8;

    // フィルムグレイン
    float grain = hash2(vUV * vec2(1280.0, 720.0) + fract(uTime) * 100.0) - 0.5;
    col += grain * 0.02;

    fragColor = vec4(col, 1.0);
}
"""
    }
}
