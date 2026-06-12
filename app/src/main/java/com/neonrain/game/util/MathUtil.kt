package com.neonrain.game.util

import kotlin.math.abs
import kotlin.math.exp

object MathUtil {
    const val PI = 3.1415927f
    const val TWO_PI = 6.2831855f

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun clamp(v: Float, min: Float, max: Float): Float =
        if (v < min) min else if (v > max) max else v

    fun clamp01(v: Float): Float = clamp(v, 0f, 1f)

    /** フレームレート非依存の指数追従。rate: 1秒あたりの収束率 */
    fun damp(current: Float, target: Float, rate: Float, dt: Float): Float =
        lerp(current, target, 1f - exp(-rate * dt))

    fun approach(current: Float, target: Float, maxDelta: Float): Float {
        val d = target - current
        return if (abs(d) <= maxDelta) target else current + if (d > 0) maxDelta else -maxDelta
    }

    /** シンプルな1Dハッシュ（0..1） */
    fun hash(seed: Int): Float {
        var h = seed * -1640531527
        h = h xor (h ushr 16)
        h *= -2048144789
        h = h xor (h ushr 13)
        return (h and 0x7FFFFFFF) / 2147483647f
    }
}
