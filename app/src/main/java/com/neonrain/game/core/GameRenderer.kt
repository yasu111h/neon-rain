package com.neonrain.game.core

import android.opengl.GLSurfaceView
import com.neonrain.game.game.GameWorld
import com.neonrain.game.input.SwipeInput
import com.neonrain.game.renderer.Camera
import com.neonrain.game.renderer.RenderPipeline
import com.neonrain.game.util.MathUtil
import com.neonrain.game.world.CityGenerator
import com.neonrain.game.world.RainSystem
import com.neonrain.game.world.SceneRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView.Renderer 実装。全体を束ねる（技術設計書 §1.2）。
 */
class GameRenderer(
    private val world: GameWorld,
    private val input: SwipeInput
) : GLSurfaceView.Renderer {

    private val gameLoop = GameLoop()
    private val camera = Camera()
    private val pipeline = RenderPipeline()
    private val scene = SceneRenderer()
    private val rain = RainSystem()
    private val city = CityGenerator()

    @Volatile var fps = 0
        private set

    private var time = 0f
    private var initialized = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // コンテキスト再生成に備えてGLリソースを作り直す
        scene.init()
        rain.init()
        initialized = false
        gameLoop.reset()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (!initialized) {
            pipeline.init(width, height)
            initialized = true
        } else {
            pipeline.resize(width, height)
        }
        camera.setAspect(width, height)
        gameLoop.reset()
    }

    override fun onDrawFrame(gl: GL10?) {
        // --- 固定タイムステップ更新 ---
        val steps = gameLoop.beginFrame()
        val dt = GameConfig.FIXED_DT
        for (i in 0 until steps) {
            world.update(dt, input)
            city.update(dt, world.effectiveSpeed)
        }
        fps = gameLoop.fps

        val frameDt = steps * dt
        time += frameDt

        val boost = if (world.player.isBoosting) 1f else 0f
        camera.update(world.player.x, boost, frameDt.coerceAtLeast(0.0001f))

        val beatPhase = world.beatClock.beatPhase
        val glow = world.combo.glowLevel / 5f

        // --- Pass 0: HDRシーン（路面以外） ---
        pipeline.beginScene()
        scene.drawSky(time, beatPhase, glow)
        scene.drawBuildings(camera, city, time, glow)
        scene.drawNeonSigns(camera, city, world.beatClock.songBeat, world.combo.neonBase)
        scene.drawObstacles(camera, world.obstacles, time)
        scene.drawPlayer(camera, world, time, beatPhase)

        // --- Pass 1: 反射コピー → 路面描画 ---
        pipeline.copyReflection()
        scene.drawRoad(
            camera, pipeline.reflectTexture,
            world.scroll, time, beatPhase,
            world.player.x, glow,
            pipeline.sceneWidth, pipeline.sceneHeight
        )

        // --- Pass 0d: 雨（半透明・最後） ---
        rain.draw(camera, time, boost, glow)

        // --- ポストエフェクトパラメータ（コンボ連動 §3.4） ---
        pipeline.post.saturation = world.combo.saturation
        pipeline.post.bloomBoost = world.combo.bloomBoost
        pipeline.post.aberration = 0.0012f + boost * 0.004f
        pipeline.post.vignette = MathUtil.lerp(0.32f, 0.55f, MathUtil.clamp01(world.hitFlashTimer * 2f))
        pipeline.post.hitFlash = MathUtil.clamp01(world.hitFlashTimer * 2f)
        pipeline.post.rainOnLens = 0.5f + boost * 0.4f

        // --- Pass 2+3: ブルーム → 最終合成 ---
        pipeline.finish(time)
    }
}
