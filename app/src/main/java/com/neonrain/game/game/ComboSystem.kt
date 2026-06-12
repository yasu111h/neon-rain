package com.neonrain.game.game

import com.neonrain.game.core.GameConfig
import com.neonrain.game.util.MathUtil

/**
 * コンボ→世界の美しさ（WorldGlow）変換（企画書 4.3/4.4）。
 */
class ComboSystem {
    var combo = 0; private set
    var maxCombo = 0; private set
    var score = 0L; private set

    /** 0..5 の美しさレベル */
    val glowLevel: Int
        get() {
            val steps = GameConfig.COMBO_STEPS
            var lv = 0
            for (s in steps) if (combo >= s) lv++
            return lv
        }

    val multiplier: Float
        get() = when (glowLevel) {
            0 -> 1.0f; 1 -> 1.5f; 2 -> 2.0f; 3 -> 3.0f; 4 -> 4.0f; else -> 5.0f
        }

    // 描画パラメータ（指数補間で滑らかに追従）
    var saturation = 0.85f; private set
    var bloomBoost = 0.45f; private set
    var neonBase = 2.0f; private set

    private var hitFlash = 0f // 被弾直後の彩度ドロップ

    fun reset() {
        combo = 0
        maxCombo = 0
        score = 0
        saturation = 0.85f
        bloomBoost = 0.45f
        hitFlash = 0f
    }

    fun onPerfect() {
        combo++
        if (combo > maxCombo) maxCombo = combo
        score += (100 * multiplier).toLong()
    }

    fun onGood() {
        score += (50 * multiplier).toLong()
    }

    fun onNearMiss() {
        combo += 1
        if (combo > maxCombo) maxCombo = combo
        score += (200 * multiplier).toLong()
    }

    fun addDistanceScore(meters: Float) {
        score += (meters * multiplier).toLong()
    }

    fun onHit() {
        combo = 0
        hitFlash = 1f
    }

    fun update(dt: Float) {
        // 目標値: Lv0=0.85 → Lv5=1.4（被弾直後は0.4へ落として2秒で回復）
        val lv = glowLevel
        var targetSat = 0.85f + lv * 0.11f
        val targetBloom = 0.4f + lv * 0.1f
        val targetNeon = 2.0f + lv * 0.5f

        if (hitFlash > 0f) {
            hitFlash -= dt / 2f
            targetSat = MathUtil.lerp(targetSat, 0.4f, MathUtil.clamp01(hitFlash))
        }

        saturation = MathUtil.damp(saturation, targetSat, 5f, dt)
        bloomBoost = MathUtil.damp(bloomBoost, targetBloom, 3f, dt)
        neonBase = MathUtil.damp(neonBase, targetNeon, 3f, dt)
    }
}
