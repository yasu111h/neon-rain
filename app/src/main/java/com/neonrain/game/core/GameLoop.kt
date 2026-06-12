package com.neonrain.game.core

/**
 * 固定タイムステップのゲームループ（danmaku-3dより流用）。
 * 120Hz更新＋描画補間αでリズム判定の決定性を確保する。
 */
class GameLoop {
    private var previousTime = 0L
    private var accumulator = 0f
    var alpha = 0f; private set
    var fps = 0; private set

    private var frameCount = 0
    private var fpsTimer = 0f

    fun reset() {
        previousTime = System.nanoTime()
        accumulator = 0f
        alpha = 0f
        frameCount = 0
        fpsTimer = 0f
    }

    /** フレーム先頭で呼ぶ。実行すべき固定ステップ数を返す */
    fun beginFrame(): Int {
        val currentTime = System.nanoTime()
        val elapsed = (currentTime - previousTime) / 1_000_000_000f
        previousTime = currentTime

        accumulator += elapsed
        if (accumulator > GameConfig.MAX_ACCUMULATOR) {
            accumulator = GameConfig.MAX_ACCUMULATOR
        }

        frameCount++
        fpsTimer += elapsed
        if (fpsTimer >= 1f) {
            fps = frameCount
            frameCount = 0
            fpsTimer -= 1f
        }

        var steps = 0
        while (accumulator >= GameConfig.FIXED_DT && steps < GameConfig.MAX_STEPS_PER_FRAME) {
            accumulator -= GameConfig.FIXED_DT
            steps++
        }

        alpha = accumulator / GameConfig.FIXED_DT
        return steps
    }
}
