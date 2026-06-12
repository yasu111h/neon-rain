package com.neonrain.game.game

import com.neonrain.game.audio.BeatClock
import com.neonrain.game.core.GameConfig
import com.neonrain.game.util.MathUtil
import kotlin.math.abs

/**
 * 障害物のスポーン（ビート駆動・プロシージャル）＋プール＋当たり判定。
 * 位置は「判定時刻と現在の楽曲時刻の差 × スクロール速度」で毎フレーム直接配置する
 * （技術設計書 §4.3: 速度積分しないことで音とズレない）。
 */
class ObstacleSystem(private val beatClock: BeatClock) {

    companion object {
        const val TYPE_CAR = 0      // 対向車（車線塞ぎ）
        const val TYPE_PUDDLE = 1   // 水たまり
    }

    private val n = GameConfig.OBSTACLE_POOL
    val active = BooleanArray(n)
    val lane = IntArray(n)
    val type = IntArray(n)
    val noteTimeMs = DoubleArray(n)
    val z = FloatArray(n)
    private val passed = BooleanArray(n)
    private val seed = FloatArray(n)

    var activeCount = 0; private set

    /** 衝突コールバック（事前確保） */
    var onHit: ((type: Int) -> Unit)? = null
    var onNearMiss: (() -> Unit)? = null

    private var lastSpawnLane = -1
    private var nearMissCooldown = 0f

    fun reset() {
        for (i in 0 until n) active[i] = false
        activeCount = 0
        lastSpawnLane = -1
        nearMissCooldown = 0f
    }

    /** BeatClockのonBeatから呼ばれる。SPAWN_LEAD_BEATS先のビートに障害物を予約 */
    fun onBeat(beatIndex: Int) {
        if (beatIndex < 4) return // 開始直後は出さない
        val targetBeat = beatIndex + GameConfig.SPAWN_LEAD_BEATS.toDouble()
        val h = MathUtil.hash(beatIndex * 7919)
        if (h < 0.35f) return // 出さない拍も作る（密度調整）

        val laneSel = (MathUtil.hash(beatIndex * 104729) * GameConfig.LANE_COUNT).toInt()
            .coerceIn(0, GameConfig.LANE_COUNT - 1)
        val obstacleType = if (beatIndex % 4 == 2 && h > 0.7f) TYPE_PUDDLE else TYPE_CAR

        spawn(laneSel, obstacleType, beatClock.beatToMs(targetBeat))

        // 高コンボ帯では裏拍にも追加（別レーン）
        if (h > 0.8f) {
            var second = (laneSel + 1 + (MathUtil.hash(beatIndex * 31) * 2).toInt()) % GameConfig.LANE_COUNT
            if (second == laneSel) second = (second + 1) % GameConfig.LANE_COUNT
            spawn(second, TYPE_CAR, beatClock.beatToMs(targetBeat + 0.5))
        }
        lastSpawnLane = laneSel
    }

    private fun spawn(laneIdx: Int, t: Int, timeMs: Double) {
        for (i in 0 until n) {
            if (!active[i]) {
                active[i] = true
                lane[i] = laneIdx
                type[i] = t
                noteTimeMs[i] = timeMs
                passed[i] = false
                seed[i] = MathUtil.hash(i * 2654435761.toInt() + laneIdx)
                z[i] = 999f
                return
            }
        }
    }

    fun update(dt: Float, scrollSpeed: Float, player: PlayerRunner) {
        if (nearMissCooldown > 0f) nearMissCooldown -= dt
        val songMs = beatClock.songTimeMs
        activeCount = 0
        for (i in 0 until n) {
            if (!active[i]) continue
            // 時刻から直接配置（音ズレ防止）
            z[i] = ((noteTimeMs[i] - songMs) / 1000.0 * scrollSpeed).toFloat()

            if (z[i] < -25f) {
                active[i] = false
                continue
            }
            activeCount++

            // 当たり判定（1D: 同一車線かつ|z|が閾値内）
            if (!passed[i] && abs(z[i]) < GameConfig.HIT_Z_RANGE) {
                if (lane[i] == player.lane && !player.isInvincible) {
                    passed[i] = true
                    active[i] = false
                    onHit?.invoke(type[i])
                    continue
                }
            }
            // 通過判定（ニアミス: 隣レーンですれ違い）
            if (!passed[i] && z[i] < -GameConfig.HIT_Z_RANGE) {
                passed[i] = true
                val laneDist = abs(lane[i] - player.lane)
                if (laneDist == 1 && nearMissCooldown <= 0f && type[i] == TYPE_CAR) {
                    nearMissCooldown = 2f
                    onNearMiss?.invoke()
                }
            }
        }
    }

    fun laneToX(laneIdx: Int): Float = (laneIdx - 1) * GameConfig.LANE_WIDTH
    fun seedOf(i: Int): Float = seed[i]
}
