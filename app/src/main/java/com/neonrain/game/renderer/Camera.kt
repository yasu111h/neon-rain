package com.neonrain.game.renderer

import android.opengl.Matrix
import com.neonrain.game.core.GameConfig
import com.neonrain.game.util.MathUtil

/**
 * 自車後方のチェイスカメラ。縦持ち画面向けに低め・FOV広め（画面設計書 §5）。
 */
class Camera {
    val viewMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)
    val vpMatrix = FloatArray(16)

    var camX = 0f; private set
    var camY = 3.1f; private set
    var camZ = -6.5f; private set

    private var currentFov = GameConfig.CAMERA_FOV
    private var aspect = 9f / 16f

    fun setAspect(width: Int, height: Int) {
        aspect = width.toFloat() / height.toFloat()
    }

    fun update(playerX: Float, boost: Float, dt: Float) {
        // 横は車線移動に少し遅れて追従（世界の固定感を出す）
        camX = MathUtil.damp(camX, playerX * 0.55f, 6f, dt)

        val targetFov = MathUtil.lerp(GameConfig.CAMERA_FOV, GameConfig.CAMERA_BOOST_FOV, boost)
        currentFov = MathUtil.damp(currentFov, targetFov, 8f, dt)

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, camY, camZ,
            playerX * 0.75f, 1.35f, 14f,
            0f, 1f, 0f
        )
        Matrix.perspectiveM(
            projectionMatrix, 0,
            currentFov, aspect, GameConfig.CAMERA_NEAR, GameConfig.CAMERA_FAR
        )
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }
}
