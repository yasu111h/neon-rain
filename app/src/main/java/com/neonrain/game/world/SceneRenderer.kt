package com.neonrain.game.world

import android.opengl.GLES30
import android.opengl.Matrix
import com.neonrain.game.core.GameConfig
import com.neonrain.game.game.GameWorld
import com.neonrain.game.game.ObstacleSystem
import com.neonrain.game.renderer.Camera
import com.neonrain.game.renderer.InstanceRenderer
import com.neonrain.game.renderer.Mesh
import com.neonrain.game.renderer.MeshBuilder
import com.neonrain.game.renderer.ShaderProgram
import com.neonrain.game.util.MathUtil
import kotlin.math.exp
import kotlin.math.sin

/**
 * シーン描画: 空 → ビル群（手続き窓） → ネオン看板 → 障害物 → 自車 → 路面（反射） 。
 * すべてインスタンシングでドローコールを抑える（技術設計書 §7）。
 */
class SceneRenderer {

    private lateinit var skyShader: ShaderProgram
    private lateinit var buildingShader: ShaderProgram
    private lateinit var neonShader: ShaderProgram
    private lateinit var obstacleShader: ShaderProgram
    private lateinit var carShader: ShaderProgram
    private lateinit var roadShader: ShaderProgram

    private lateinit var fullQuad: Mesh
    private lateinit var unitBox: Mesh
    private lateinit var signQuad: Mesh
    private lateinit var roadPlane: Mesh

    private lateinit var buildingInstances: InstanceRenderer
    private lateinit var signInstances: InstanceRenderer
    private lateinit var obstacleInstances: InstanceRenderer

    private val modelMatrix = FloatArray(16)
    private val tmpMatrix = FloatArray(16)

    // ネオン色パレット（ピンク/シアン/バイオレット/アンバー）
    private val neonPalette = arrayOf(
        GameConfig.COL_NEON_PINK,
        GameConfig.COL_NEON_CYAN,
        GameConfig.COL_VIOLET,
        GameConfig.COL_AMBER
    )

    fun init() {
        skyShader = ShaderProgram(SKY_VERT, SKY_FRAG)
        buildingShader = ShaderProgram(BUILDING_VERT, BUILDING_FRAG)
        neonShader = ShaderProgram(NEON_VERT, NEON_FRAG)
        obstacleShader = ShaderProgram(OBSTACLE_VERT, OBSTACLE_FRAG)
        carShader = ShaderProgram(CAR_VERT, CAR_FRAG)
        roadShader = ShaderProgram(ROAD_VERT, ROAD_FRAG)

        fullQuad = MeshBuilder.buildFullscreenQuad().also { it.init() }
        unitBox = MeshBuilder.buildUnitBox().also { it.init() }
        signQuad = MeshBuilder.buildQuad().also { it.init() }
        roadPlane = MeshBuilder.buildRoadPlane(26f, -12f, GameConfig.ROAD_LENGTH).also { it.init() }

        buildingInstances = InstanceRenderer(128).also { it.init() }
        signInstances = InstanceRenderer(128).also { it.init() }
        obstacleInstances = InstanceRenderer(GameConfig.OBSTACLE_POOL).also { it.init() }
    }

    /** Pass 0a: 空（深度書き込みOFF・最奥） */
    fun drawSky(time: Float, beatPhase: Float, glow: Float) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        skyShader.use()
        skyShader.setUniform1f("uTime", time)
        skyShader.setUniform1f("uBeat", beatPhase)
        skyShader.setUniform1f("uGlow", glow)
        fullQuad.bind()
        fullQuad.draw()
        fullQuad.unbind()
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    /** Pass 0b: ビル群（インスタンシング、手続き窓シェーダー） */
    fun drawBuildings(camera: Camera, city: CityGenerator, time: Float, glow: Float) {
        buildingInstances.beginUpdate()
        for (c in city.chunks) {
            for (i in 0 until c.buildingCount) {
                val b = i * CityGenerator.B_FLOATS
                val x = c.buildings[b]
                val zLocal = c.buildings[b + 1]
                val w = c.buildings[b + 2]
                val d = c.buildings[b + 3]
                val h = c.buildings[b + 4]
                val mix = c.buildings[b + 5]
                val seed = c.buildings[b + 6]
                buildingInstances.addInstance(
                    x, 0f, c.zStart + zLocal,
                    w, h, d,
                    GameConfig.COL_BUILDING[0] * (0.7f + mix * 0.6f),
                    GameConfig.COL_BUILDING[1] * (0.7f + mix * 0.5f),
                    GameConfig.COL_BUILDING[2] * (0.8f + mix * 0.5f),
                    1f, seed, 0f
                )
            }
        }
        buildingInstances.endUpdate()
        if (buildingInstances.instanceCount == 0) return

        buildingShader.use()
        buildingShader.setUniformMatrix4fv("uVP", camera.vpMatrix)
        buildingShader.setUniform1f("uTime", time)
        buildingShader.setUniform1f("uGlow", glow)
        buildingShader.setUniform3f("uFogColor", FOG_R, FOG_G, FOG_B)
        unitBox.bind()
        buildingInstances.bindAttributes()
        unitBox.drawInstanced(buildingInstances.instanceCount)
        buildingInstances.unbindAttributes()
        unitBox.unbind()
    }

    /** Pass 0c: ネオン看板（エミッシブHDR、ビート連動明滅） */
    fun drawNeonSigns(camera: Camera, city: CityGenerator, songBeat: Double, neonBase: Float) {
        signInstances.beginUpdate()
        for (c in city.chunks) {
            for (i in 0 until c.signCount) {
                val s = i * CityGenerator.S_FLOATS
                val seed = c.signs[s + 6]
                val colorIdx = c.signs[s + 5].toInt().coerceIn(0, 3)
                val col = neonPalette[colorIdx]
                // 看板ごとに拍の表/裏/2拍ごとを割当ててリズムの層を作る（§3.3）
                val div = if (seed < 0.4f) 1.0 else if (seed < 0.75f) 2.0 else 4.0
                val offset = if (seed * 7f % 1f < 0.5f) 0.0 else 0.5
                val local = ((songBeat / div + offset) % 1.0)
                val env = exp(-local * 6.0).toFloat()
                val intensity = neonBase + env * 2.6f

                signInstances.addInstance(
                    c.signs[s], c.signs[s + 1], c.zStart + c.signs[s + 2],
                    c.signs[s + 3], c.signs[s + 4], 1f,
                    col[0], col[1], col[2], intensity,
                    seed, c.signs[s + 7]
                )
            }
        }
        signInstances.endUpdate()
        if (signInstances.instanceCount == 0) return

        neonShader.use()
        neonShader.setUniformMatrix4fv("uVP", camera.vpMatrix)
        signQuad.bind()
        signInstances.bindAttributes()
        signQuad.drawInstanced(signInstances.instanceCount)
        signInstances.unbindAttributes()
        signQuad.unbind()
    }

    /** Pass 0c': 障害物（対向車・水たまり） */
    fun drawObstacles(camera: Camera, obstacles: ObstacleSystem, time: Float) {
        obstacleInstances.beginUpdate()
        for (i in 0 until GameConfig.OBSTACLE_POOL) {
            if (!obstacles.active[i]) continue
            val zPos = obstacles.z[i]
            if (zPos > GameConfig.ROAD_LENGTH || zPos < -20f) continue
            val x = obstacles.laneToX(obstacles.lane[i])
            if (obstacles.type[i] == ObstacleSystem.TYPE_CAR) {
                obstacleInstances.addInstance(
                    x, 0f, zPos,
                    1.7f, 1.25f, 3.6f,
                    0.09f, 0.07f, 0.16f, 1f,
                    obstacles.seedOf(i), 0f
                )
            } else {
                obstacleInstances.addInstance(
                    x, 0.02f, zPos,
                    2.0f, 0.06f, 2.6f,
                    GameConfig.COL_NEON_CYAN[0], GameConfig.COL_NEON_CYAN[1], GameConfig.COL_NEON_CYAN[2], 1f,
                    obstacles.seedOf(i), 1f
                )
            }
        }
        obstacleInstances.endUpdate()
        if (obstacleInstances.instanceCount == 0) return

        obstacleShader.use()
        obstacleShader.setUniformMatrix4fv("uVP", camera.vpMatrix)
        obstacleShader.setUniform1f("uTime", time)
        obstacleShader.setUniform3f("uFogColor", FOG_R, FOG_G, FOG_B)
        unitBox.bind()
        obstacleInstances.bindAttributes()
        unitBox.drawInstanced(obstacleInstances.instanceCount)
        obstacleInstances.unbindAttributes()
        unitBox.unbind()
    }

    /** Pass 0c'': 自車（ボディ＋キャビン＋テールランプ。被弾無敵中は点滅） */
    fun drawPlayer(camera: Camera, world: GameWorld, time: Float, beatPhase: Float) {
        val p = world.player
        if (p.isInvincible && (time * 8f).toInt() % 2 == 0 && world.state == GameWorld.State.RUNNING) return

        carShader.use()
        carShader.setUniformMatrix4fv("uVP", camera.vpMatrix)

        // ボディ
        setCarModel(p.x, 0.12f, GameConfig.PLAYER_Z, 1.7f, 0.62f, 3.4f, p.bank)
        carShader.setUniformMatrix4fv("uModel", modelMatrix)
        carShader.setUniform3f("uColor", 0.10f, 0.09f, 0.20f)
        carShader.setUniform3f("uEmissive", 0f, 0f, 0f)
        drawBoxOnce()

        // キャビン
        setCarModel(p.x, 0.70f, GameConfig.PLAYER_Z - 0.35f, 1.35f, 0.42f, 1.7f, p.bank)
        carShader.setUniformMatrix4fv("uModel", modelMatrix)
        carShader.setUniform3f("uColor", 0.06f, 0.05f, 0.13f)
        carShader.setUniform3f("uEmissive", 0.04f, 0.16f, 0.22f)
        drawBoxOnce()

        // テールランプ（HDRピンク・ビートで脈動）
        val pulse = 2.2f + (1f - beatPhase) * 1.6f + (if (p.isBoosting) 2.5f else 0f)
        setCarModel(p.x, 0.34f, GameConfig.PLAYER_Z - 1.72f, 1.5f, 0.16f, 0.1f, p.bank)
        carShader.setUniformMatrix4fv("uModel", modelMatrix)
        carShader.setUniform3f("uColor", 0f, 0f, 0f)
        carShader.setUniform3f(
            "uEmissive",
            GameConfig.COL_NEON_PINK[0] * pulse,
            GameConfig.COL_NEON_PINK[1] * pulse,
            GameConfig.COL_NEON_PINK[2] * pulse
        )
        drawBoxOnce()

        // アンダーグロー（シアン）
        setCarModel(p.x, 0.04f, GameConfig.PLAYER_Z, 2.0f, 0.05f, 3.6f, p.bank)
        carShader.setUniformMatrix4fv("uModel", modelMatrix)
        carShader.setUniform3f("uColor", 0f, 0f, 0f)
        val ug = 1.4f + world.combo.glowLevel * 0.5f
        carShader.setUniform3f(
            "uEmissive",
            GameConfig.COL_NEON_CYAN[0] * ug,
            GameConfig.COL_NEON_CYAN[1] * ug,
            GameConfig.COL_NEON_CYAN[2] * ug
        )
        drawBoxOnce()
    }

    private fun drawBoxOnce() {
        unitBox.bind()
        unitBox.draw()
        unitBox.unbind()
    }

    private fun setCarModel(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, bankDeg: Float) {
        Matrix.setIdentityM(tmpMatrix, 0)
        Matrix.translateM(tmpMatrix, 0, x, y, z)
        Matrix.rotateM(tmpMatrix, 0, bankDeg, 0f, 0f, 1f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(tmpMatrix, 0, sx, sy, sz)
        System.arraycopy(tmpMatrix, 0, modelMatrix, 0, 16)
    }

    /**
     * Pass 1: 濡れた路面。直前までのシーン（reflectTex）を上下反転サンプル＋揺らぎ＋減光合成。
     * hanabiの湖面反射方式の発展形（技術設計書 §3.1）。
     */
    fun drawRoad(
        camera: Camera, reflectTexture: Int,
        scroll: Float, time: Float, beatPhase: Float,
        playerX: Float, glow: Float, width: Int, height: Int
    ) {
        roadShader.use()
        roadShader.setUniformMatrix4fv("uVP", camera.vpMatrix)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, reflectTexture)
        roadShader.setUniform1i("uReflect", 0)
        roadShader.setUniform2f("uResolution", width.toFloat(), height.toFloat())
        roadShader.setUniform1f("uHorizon", computeHorizon(camera))
        roadShader.setUniform1f("uScroll", scroll)
        roadShader.setUniform1f("uTime", time)
        roadShader.setUniform1f("uBeat", beatPhase)
        roadShader.setUniform1f("uPlayerX", playerX)
        roadShader.setUniform1f("uGlow", glow)
        roadShader.setUniform3f("uFogColor", FOG_R, FOG_G, FOG_B)
        roadPlane.bind()
        roadPlane.draw()
        roadPlane.unbind()
    }

    private val hClip = FloatArray(4)
    private val hDir = floatArrayOf(0f, 0f, 1f, 0f)

    /** 地平線のスクリーンY（0..1）。前方方向ベクトルを射影して求める */
    private fun computeHorizon(camera: Camera): Float {
        Matrix.multiplyMV(hClip, 0, camera.vpMatrix, 0, hDir, 0)
        if (hClip[3] == 0f) return 0.62f
        val ndcY = hClip[1] / hClip[3]
        return MathUtil.clamp((ndcY + 1f) * 0.5f, 0.3f, 0.9f)
    }

    fun delete() {
        skyShader.delete(); buildingShader.delete(); neonShader.delete()
        obstacleShader.delete(); carShader.delete(); roadShader.delete()
        fullQuad.delete(); unitBox.delete(); signQuad.delete(); roadPlane.delete()
        buildingInstances.delete(); signInstances.delete(); obstacleInstances.delete()
    }

    companion object {
        // 霧色（夜空に滲むネオンの照り返し）
        const val FOG_R = 0.075f
        const val FOG_G = 0.055f
        const val FOG_B = 0.16f

        // ============ 空 ============
        private const val SKY_VERT = """#version 300 es
precision mediump float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vUV;
void main() {
    gl_Position = vec4(aPosition, 1.0);
    vUV = aTexCoord;
}
"""
        private const val SKY_FRAG = """#version 300 es
precision mediump float;
in vec2 vUV;
out vec4 fragColor;
uniform float uTime;
uniform float uBeat;
uniform float uGlow;

float hash(float n) { return fract(sin(n) * 43758.5453); }
float noise1(float x) {
    float i = floor(x); float f = fract(x);
    return mix(hash(i), hash(i + 1.0), f * f * (3.0 - 2.0 * f));
}

void main() {
    vec3 deep = vec3(0.031, 0.024, 0.082);     // #0D0B1E より僅かに暗く
    vec3 horizonGlow = vec3(0.30, 0.10, 0.34); // 街明かりのマゼンタ
    vec3 cyanTint = vec3(0.05, 0.22, 0.30);

    float h = clamp((vUV.y - 0.45) * 2.2, 0.0, 1.0);
    vec3 sky = mix(horizonGlow, deep, pow(h, 0.65));
    sky += cyanTint * (1.0 - h) * 0.4 * (0.8 + 0.2 * sin(uTime * 0.3));

    // 遠景ビルシルエット（手続きスカイライン）
    float skyline = 0.50 + 0.10 * noise1(vUV.x * 14.0) + 0.05 * noise1(vUV.x * 41.0 + 7.0);
    if (vUV.y < skyline) {
        float win = step(0.985, hash(floor(vUV.x * 160.0) * 91.7 + floor(vUV.y * 110.0) * 13.3));
        vec3 sil = deep * 0.55;
        sil += vec3(1.0, 0.65, 0.35) * win * (0.5 + uGlow * 0.4);
        sky = mix(sky, sil, smoothstep(skyline, skyline - 0.015, vUV.y));
    }

    // ビートで微かに脈打つ低層の靄
    float fogBand = exp(-abs(vUV.y - 0.50) * 9.0);
    sky += vec3(0.16, 0.05, 0.20) * fogBand * (0.45 + 0.25 * (1.0 - uBeat) + uGlow * 0.2);

    fragColor = vec4(sky, 1.0);
}
"""

        // ============ ビル（手続き窓） ============
        private const val BUILDING_VERT = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 3) in vec3 aIPos;
layout(location = 4) in vec3 aIScale;
layout(location = 5) in vec4 aIColor;
layout(location = 6) in vec2 aIMisc;
uniform mat4 uVP;
out vec3 vLocal;
out vec3 vNormal;
out vec3 vColor;
out float vSeed;
out float vDist;
out float vHeight;
void main() {
    vec3 scaled = aPosition * aIScale;
    vec3 world = aIPos + scaled;
    gl_Position = uVP * vec4(world, 1.0);
    vLocal = scaled;
    vNormal = aNormal;
    vColor = aIColor.rgb;
    vSeed = aIMisc.x;
    vDist = world.z;
    vHeight = aIScale.y;
}
"""
        private const val BUILDING_FRAG = """#version 300 es
precision mediump float;
in vec3 vLocal;
in vec3 vNormal;
in vec3 vColor;
in float vSeed;
in float vDist;
in float vHeight;
out vec4 fragColor;
uniform float uTime;
uniform float uGlow;
uniform vec3 uFogColor;

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7)) + vSeed) * 43758.5453);
}

void main() {
    vec3 base = vColor;
    // 側面のみ手続き窓（ワールドローカル座標グリッド）
    float sideX = abs(vNormal.x);
    float sideZ = abs(vNormal.z);
    vec3 emis = vec3(0.0);
    if (sideX > 0.5 || sideZ > 0.5) {
        vec2 wuv = sideX > 0.5 ? vLocal.zy : vLocal.xy;
        vec2 cell = floor(wuv / vec2(1.1, 1.6));
        vec2 f = fract(wuv / vec2(1.1, 1.6));
        float lit = step(0.62 - uGlow * 0.08, hash2(cell));
        float inWin = step(0.18, f.x) * step(f.x, 0.82) * step(0.25, f.y) * step(f.y, 0.78);
        float warm = hash2(cell + 7.0);
        vec3 winCol = mix(vec3(1.0, 0.72, 0.42), vec3(0.45, 0.85, 1.0), step(0.6, warm));
        // たまに明滅する窓
        float flicker = 1.0 - 0.5 * step(0.97, hash2(cell + floor(uTime * 2.0)));
        emis = winCol * lit * inWin * flicker * 1.4;
    }
    // 上方向ほど僅かに明るく（空のグロー受け）
    float vGrad = clamp(vLocal.y / max(vHeight, 1.0), 0.0, 1.0);
    vec3 col = base * (0.5 + vGrad * 0.6) + emis;

    // 距離フォグ
    float fog = 1.0 - exp(-max(vDist, 0.0) * 0.012);
    col = mix(col, uFogColor, fog);
    fragColor = vec4(col, 1.0);
}
"""

        // ============ ネオン看板 ============
        private const val NEON_VERT = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
layout(location = 3) in vec3 aIPos;
layout(location = 4) in vec3 aIScale;
layout(location = 5) in vec4 aIColor;
layout(location = 6) in vec2 aIMisc;
uniform mat4 uVP;
out vec2 vUV;
out vec4 vColor;
out float vDist;
void main() {
    // 壁の法線方向(aIMisc.y = ±1)に向くYZ平面クアッド
    float facing = aIMisc.y;
    vec3 world = aIPos + vec3(0.0, aPosition.y * aIScale.y, aPosition.x * aIScale.x * facing);
    gl_Position = uVP * vec4(world, 1.0);
    vUV = aTexCoord;
    vColor = aIColor;
    vDist = world.z;
}
"""
        private const val NEON_FRAG = """#version 300 es
precision mediump float;
in vec2 vUV;
in vec4 vColor;
in float vDist;
out vec4 fragColor;
void main() {
    // ネオン管風: 枠が最も明るく、中は淡く光る
    vec2 d = abs(vUV - 0.5) * 2.0;
    float edge = max(d.x, d.y);
    float tube = smoothstep(0.55, 0.95, edge) * 1.4 + 0.35;
    float fog = exp(-max(vDist, 0.0) * 0.010);
    vec3 col = vColor.rgb * vColor.a * tube * fog;
    fragColor = vec4(col, 1.0);
}
"""

        // ============ 障害物 ============
        private const val OBSTACLE_VERT = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 3) in vec3 aIPos;
layout(location = 4) in vec3 aIScale;
layout(location = 5) in vec4 aIColor;
layout(location = 6) in vec2 aIMisc;
uniform mat4 uVP;
out vec3 vNormal;
out vec4 vColor;
out vec2 vMisc;
out float vDist;
out vec3 vLocal;
void main() {
    vec3 scaled = aPosition * aIScale;
    vec3 world = aIPos + scaled;
    gl_Position = uVP * vec4(world, 1.0);
    vNormal = aNormal;
    vColor = aIColor;
    vMisc = aIMisc;
    vDist = world.z;
    vLocal = aPosition;
}
"""
        private const val OBSTACLE_FRAG = """#version 300 es
precision mediump float;
in vec3 vNormal;
in vec4 vColor;
in vec2 vMisc;
in float vDist;
in vec3 vLocal;
out vec4 fragColor;
uniform float uTime;
uniform vec3 uFogColor;
void main() {
    vec3 col;
    if (vMisc.y > 0.5) {
        // 水たまり: 縁が光るシアンの面
        vec2 d = abs(vLocal.xz) * 2.0;
        float rim = smoothstep(0.7, 1.0, max(d.x, d.y));
        col = vColor.rgb * (0.25 + rim * 2.2);
    } else {
        // 対向車: 暗いボディ＋HDRヘッドライト（手前向き-z面）
        float lambert = 0.35 + 0.65 * max(vNormal.y, 0.0);
        col = vColor.rgb * lambert;
        if (vNormal.z < -0.5) {
            // ヘッドライト2灯
            float lx = abs(abs(vLocal.x) - 0.30);
            float ly = abs(vLocal.y - 0.35);
            float light = exp(-(lx * lx * 90.0 + ly * ly * 160.0));
            col += vec3(2.6, 2.5, 2.0) * light;
        }
        if (vNormal.z > 0.5) {
            float lx = abs(abs(vLocal.x) - 0.30);
            float light = exp(-lx * lx * 120.0);
            col += vec3(2.0, 0.15, 0.25) * light * 0.7;
        }
    }
    float fog = 1.0 - exp(-max(vDist, 0.0) * 0.012);
    col = mix(col, uFogColor, fog);
    fragColor = vec4(col, 1.0);
}
"""

        // ============ 自車 ============
        private const val CAR_VERT = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
uniform mat4 uVP;
uniform mat4 uModel;
out vec3 vNormal;
void main() {
    gl_Position = uVP * uModel * vec4(aPosition, 1.0);
    vNormal = mat3(uModel) * aNormal;
}
"""
        private const val CAR_FRAG = """#version 300 es
precision mediump float;
in vec3 vNormal;
out vec4 fragColor;
uniform vec3 uColor;
uniform vec3 uEmissive;
void main() {
    vec3 n = normalize(vNormal);
    float lambert = 0.4 + 0.6 * max(n.y, 0.0);
    // ネオン街のリムライト（左右からピンク/シアン）
    vec3 rim = vec3(1.0, 0.18, 0.53) * max(n.x, 0.0) * 0.25
             + vec3(0.0, 0.90, 1.0) * max(-n.x, 0.0) * 0.25;
    fragColor = vec4(uColor * lambert + rim + uEmissive, 1.0);
}
"""

        // ============ 路面（濡れ反射） ============
        private const val ROAD_VERT = """#version 300 es
precision highp float;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoord;
uniform mat4 uVP;
out vec3 vWorld;
void main() {
    gl_Position = uVP * vec4(aPosition, 1.0);
    vWorld = aPosition;
}
"""
        private const val ROAD_FRAG = """#version 300 es
precision highp float;
in vec3 vWorld;
out vec4 fragColor;
uniform sampler2D uReflect;
uniform vec2 uResolution;
uniform float uHorizon;
uniform float uScroll;
uniform float uTime;
uniform float uBeat;
uniform float uPlayerX;
uniform float uGlow;
uniform vec3 uFogColor;

float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1, 0)), f.x),
               mix(hash2(i + vec2(0, 1)), hash2(i + vec2(1, 1)), f.x), f.y);
}

void main() {
    vec2 screenUV = gl_FragCoord.xy / uResolution;
    vec2 wz = vec2(vWorld.x, vWorld.z + uScroll);

    // --- アスファルト基調 ---
    float grain = vnoise(wz * 6.0) * 0.05;
    vec3 asphalt = vec3(0.035, 0.030, 0.075) + grain;

    // --- 水たまりマスク（大きめノイズのしきい値処理） ---
    float pud = vnoise(wz * 0.22 + 31.7);
    float puddle = smoothstep(0.55, 0.72, pud);

    // --- 雨の波紋（リングSDFをタイル状に位相ランダム展開） ---
    vec2 cell = floor(wz * 0.8);
    vec2 cf = fract(wz * 0.8) - 0.5;
    float ph = fract(uTime * 0.7 + hash2(cell));
    float ringR = ph * 0.5;
    float ring = exp(-abs(length(cf) - ringR) * 28.0) * (1.0 - ph);

    // --- 画面反転反射（hanabi方式） ---
    float mirrorY = uHorizon * 2.0 - screenUV.y;
    vec2 ripple = vec2(
        vnoise(wz * 1.5 + uTime * 0.8) - 0.5,
        vnoise(wz * 1.5 - uTime * 0.6 + 50.0) - 0.5
    ) * (0.012 + 0.02 * puddle) + ring * 0.012;
    vec2 refUV = vec2(screenUV.x, mirrorY) + ripple;
    vec3 refl = vec3(0.0);
    if (refUV.y > 0.0 && refUV.y < 1.0) {
        refl = texture(uReflect, refUV).rgb;
    }
    float farFade = clamp(1.0 - vWorld.z / 130.0, 0.0, 1.0);
    float reflStrength = mix(0.20, 0.78, puddle) * farFade * (0.7 + uGlow * 0.3);
    vec3 col = asphalt * (1.0 - puddle * 0.55) + refl * reflStrength;
    col += vec3(0.4, 0.8, 1.0) * ring * 0.08;

    // --- 車線（3レーン: 境界±1.2m、端±3.6m） ---
    float lw = 2.4;
    float xa = abs(vWorld.x);
    // 破線（中央境界）
    float dashOn = step(fract((vWorld.z + uScroll) / 4.0), 0.55);
    float lane1 = exp(-pow((xa - lw * 0.5) * 14.0, 2.0)) * dashOn;
    // 実線（路肩）
    float edgeL = exp(-pow((xa - lw * 1.5) * 12.0, 2.0));
    float beatPulse = 0.7 + (1.0 - uBeat) * (0.9 + uGlow * 0.8);
    col += vec3(0.0, 0.90, 1.0) * lane1 * beatPulse * 1.2;
    col += vec3(1.0, 0.18, 0.53) * edgeL * beatPulse * 1.0;

    // --- 自車足元のビート同心円パルス（判定ガイド） ---
    float pd = distance(vWorld.xz, vec2(uPlayerX, 0.0));
    float pulseR = uBeat * 5.0;
    float pulse = exp(-abs(pd - pulseR) * 2.6) * (1.0 - uBeat) * 0.8;
    col += vec3(0.24, 1.0, 0.78) * pulse;

    // --- 距離フォグ ---
    float fog = 1.0 - exp(-max(vWorld.z, 0.0) * 0.012);
    col = mix(col, uFogColor, fog);
    fragColor = vec4(col, 1.0);
}
"""
    }
}
