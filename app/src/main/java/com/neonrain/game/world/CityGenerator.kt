package com.neonrain.game.world

import com.neonrain.game.core.GameConfig
import com.neonrain.game.util.MathUtil
import kotlin.math.abs

/**
 * プロシージャル都市生成。道路チャンク（20m）×10個のリングバッファ。
 * プレイヤー後方に抜けたチャンクは前方に回して再生成（GC回避のため固定配列を書き換え）。
 */
class CityGenerator {

    companion object {
        const val MAX_BUILDINGS_PER_CHUNK = 8
        const val MAX_SIGNS_PER_CHUNK = 10
        const val B_FLOATS = 7  // x, z(local), width, depth, height, colorMix, seed
        const val S_FLOATS = 8  // x, y, z(local), w, h, colorIdx, seed, side(+1:左側壁が+x向き)
    }

    class Chunk {
        var zStart = 0f                       // チャンク先頭のワールドz
        var buildingCount = 0
        val buildings = FloatArray(MAX_BUILDINGS_PER_CHUNK * B_FLOATS)
        var signCount = 0
        val signs = FloatArray(MAX_SIGNS_PER_CHUNK * S_FLOATS)
    }

    val chunks = Array(GameConfig.CHUNK_COUNT) { Chunk() }
    private var genCounter = 0

    init {
        for ((i, c) in chunks.withIndex()) {
            c.zStart = -20f + i * GameConfig.CHUNK_LENGTH
            generate(c)
        }
    }

    /** スクロールに応じてチャンクを更新（毎フレーム） */
    fun update(dt: Float, speed: Float) {
        val total = GameConfig.CHUNK_COUNT * GameConfig.CHUNK_LENGTH
        for (c in chunks) {
            c.zStart -= speed * dt
            if (c.zStart + GameConfig.CHUNK_LENGTH < -22f) {
                c.zStart += total
                generate(c)
            }
        }
    }

    private fun rand(salt: Int): Float {
        genCounter++
        return MathUtil.hash(genCounter * 31 + salt * 1013)
    }

    private fun generate(c: Chunk) {
        c.buildingCount = 0
        c.signCount = 0

        // 両側にビルを配置（片側3〜4棟）
        for (side in 0..1) {
            val sideSign = if (side == 0) -1f else 1f
            var zCursor = 0f
            while (zCursor < GameConfig.CHUNK_LENGTH - 4f && c.buildingCount < MAX_BUILDINGS_PER_CHUNK) {
                val depth = 5f + rand(1) * 6f
                if (zCursor + depth > GameConfig.CHUNK_LENGTH) break
                val width = 5f + rand(2) * 7f
                val height = 7f + rand(3) * rand(4) * 38f
                val xDist = 6.5f + rand(5) * 7f + width * 0.5f
                val x = sideSign * xDist
                val colorMix = rand(6)
                val seed = rand(7) * 1000f

                val b = c.buildingCount * B_FLOATS
                c.buildings[b] = x
                c.buildings[b + 1] = zCursor + depth * 0.5f
                c.buildings[b + 2] = width
                c.buildings[b + 3] = depth
                c.buildings[b + 4] = height
                c.buildings[b + 5] = colorMix
                c.buildings[b + 6] = seed
                c.buildingCount++

                // 道路に面した壁にネオン看板を1〜2枚
                val signNum = 1 + (rand(8) * 1.8f).toInt()
                for (s in 0 until signNum) {
                    if (c.signCount >= MAX_SIGNS_PER_CHUNK) break
                    val wallX = x - sideSign * (width * 0.5f + 0.15f)
                    val sh = 1.2f + rand(9) * 3.5f      // 看板高さ
                    val sw = 0.7f + rand(10) * 1.6f     // 看板幅（壁に沿うz方向）
                    val sy = 2.5f + rand(11) * (height * 0.65f)
                    if (sy + sh > height) continue
                    val sz = zCursor + 1f + rand(12) * (depth - 2f)
                    val colorIdx = (rand(13) * 4f).toInt().toFloat()

                    val si = c.signCount * S_FLOATS
                    c.signs[si] = wallX
                    c.signs[si + 1] = sy
                    c.signs[si + 2] = sz
                    c.signs[si + 3] = sw
                    c.signs[si + 4] = sh
                    c.signs[si + 5] = colorIdx
                    c.signs[si + 6] = rand(14)
                    c.signs[si + 7] = -sideSign // 壁の法線方向（道路向き）
                    c.signCount++
                }

                zCursor += depth + 1.5f + rand(15) * 2f
            }
        }
    }
}
