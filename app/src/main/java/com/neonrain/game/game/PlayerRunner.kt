package com.neonrain.game.game

import com.neonrain.game.core.GameConfig
import com.neonrain.game.util.MathUtil

/**
 * 自機（車）。車線は離散値、描画位置はスムーズダンプで追従。
 */
class PlayerRunner {
    var lane = 1            // 0..2（中央スタート）
    var x = 0f; private set // 描画位置
    var bank = 0f; private set // バンク角（度）

    var hp = GameConfig.MAX_HP
    var invincibleTimer = 0f; private set

    // ブースト
    var boostTimer = 0f; private set
    var boostCooldown = 0f; private set
    val isBoosting: Boolean get() = boostTimer > 0f
    val boostAmount: Float get() = MathUtil.clamp01(boostTimer / 0.3f) // 演出用0..1

    val laneX: Float get() = (lane - 1) * GameConfig.LANE_WIDTH
    val isInvincible: Boolean get() = invincibleTimer > 0f
    val isAlive: Boolean get() = hp > 0

    fun reset() {
        lane = 1
        x = 0f
        bank = 0f
        hp = GameConfig.MAX_HP
        invincibleTimer = 0f
        boostTimer = 0f
        boostCooldown = 0f
    }

    /** 車線変更。成功したらtrue */
    fun changeLane(dir: Int): Boolean {
        val next = lane + dir
        if (next < 0 || next >= GameConfig.LANE_COUNT) return false
        lane = next
        return true
    }

    /** タップでブースト。発動したらtrue */
    fun tryBoost(): Boolean {
        if (boostCooldown > 0f || boostTimer > 0f) return false
        boostTimer = GameConfig.BOOST_DURATION
        boostCooldown = GameConfig.BOOST_COOLDOWN
        return true
    }

    fun onHit() {
        if (isInvincible) return
        hp--
        invincibleTimer = GameConfig.HIT_INVINCIBLE
    }

    fun update(dt: Float) {
        val prevX = x
        x = MathUtil.damp(x, laneX, GameConfig.LANE_CHANGE_RATE, dt)
        // 移動速度に応じたバンク（最大15度）
        val vx = (x - prevX) / dt
        bank = MathUtil.damp(bank, MathUtil.clamp(-vx * 4.5f, -15f, 15f), 10f, dt)

        if (invincibleTimer > 0f) invincibleTimer -= dt
        if (boostTimer > 0f) boostTimer -= dt
        if (boostCooldown > 0f) boostCooldown -= dt
    }
}
