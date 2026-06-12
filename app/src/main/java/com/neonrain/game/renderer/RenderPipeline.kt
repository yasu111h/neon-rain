package com.neonrain.game.renderer

import android.opengl.GLES30
import com.neonrain.game.core.GameConfig

/**
 * 描画パス全体の制御（技術設計書 §2.1）。
 * [Pass 0] HDRシーン → sceneFBO（路面以外）
 * [Pass 1] sceneFBOを1/2解像度reflectFBOへblit → 路面が反転サンプル
 * [Pass 2] ブルーム（1/4解像度）
 * [Pass 3] 最終合成 → 画面
 */
class RenderPipeline {

    private var sceneFBO: FrameBuffer? = null
    private var reflectFBO: FrameBuffer? = null
    val bloom = BloomPass()
    val post = PostProcess()

    private lateinit var fullQuad: Mesh

    var sceneWidth = 0; private set
    var sceneHeight = 0; private set
    private var screenWidth = 0
    private var screenHeight = 0

    val sceneTexture: Int get() = sceneFBO?.textureId ?: 0
    val reflectTexture: Int get() = reflectFBO?.textureId ?: 0

    fun init(width: Int, height: Int) {
        fullQuad = MeshBuilder.buildFullscreenQuad().also { it.init() }
        bloom.init(width, height, fullQuad)
        post.init(fullQuad)
        resize(width, height)
    }

    fun resize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        sceneWidth = width
        sceneHeight = height

        sceneFBO?.delete()
        reflectFBO?.delete()
        sceneFBO = FrameBuffer(sceneWidth, sceneHeight, useHDR = true, useDepth = true).also { it.init() }
        reflectFBO = FrameBuffer(sceneWidth / 2, sceneHeight / 2, useHDR = true).also { it.init() }
        bloom.resize(sceneWidth, sceneHeight)
    }

    /** Pass 0 開始 */
    fun beginScene() {
        sceneFBO?.bind()
        GLES30.glClearColor(
            GameConfig.COL_BASE[0] * 0.5f,
            GameConfig.COL_BASE[1] * 0.5f,
            GameConfig.COL_BASE[2] * 0.5f, 1f
        )
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
    }

    /**
     * Pass 1: 現時点のシーン（路面以外）を反射用FBOへ縮小コピー。
     * この後drawRoadがreflectTextureを反転サンプルする。
     */
    fun copyReflection() {
        val scene = sceneFBO ?: return
        val refl = reflectFBO ?: return
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, scene.fboId)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, refl.fboId)
        GLES30.glBlitFramebuffer(
            0, 0, scene.width, scene.height,
            0, 0, refl.width, refl.height,
            GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_LINEAR
        )
        // シーンFBOへ描画を戻す
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, scene.fboId)
        GLES30.glViewport(0, 0, scene.width, scene.height)
    }

    /** Pass 2 + 3: ブルーム→最終合成 */
    fun finish(time: Float) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        bloom.render(sceneTexture, GameConfig.BLOOM_THRESHOLD)
        post.render(sceneTexture, bloom.bloomTexture, screenWidth, screenHeight, time)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    fun delete() {
        sceneFBO?.delete()
        reflectFBO?.delete()
        bloom.delete()
        post.delete()
        fullQuad.delete()
    }
}
