package com.neonrain.game.game

import android.content.Context
import com.neonrain.game.audio.BeatClock
import com.neonrain.game.audio.BeatMap
import com.neonrain.game.core.GameConfig
import com.neonrain.game.input.SwipeInput
import com.neonrain.game.util.MathUtil
import kotlin.math.abs

/**
 * ゲーム状態の統括（danmaku-3dのGameWorld構成を踏襲）。
 */
class GameWorld(context: Context) {

    enum class State { RUNNING, GAMEOVER }

    var state = State.RUNNING; private set

    val beatMap: BeatMap = BeatMap.load(context, "midnight")
    val beatClock = BeatClock(bpm = beatMap.bpm, offsetMs = -beatMap.offsetMs)
    val player = PlayerRunner()
    val obstacles = ObstacleSystem(beatClock)
    val combo = ComboSystem()

    var distance = 0f; private set       // 走行距離(m)
    var scrollSpeed = GameConfig.BASE_SPEED; private set
    var scroll = 0f; private set         // 路面スクロール用累積値

    // 判定表示用（HUDが読む）
    @Volatile var lastJudgement = ""     // "PERFECT" / "GOOD" / ""
    @Volatile var lastJudgementTime = 0L

    // 被弾エフェクト用
    var hitFlashTimer = 0f; private set

    private val beatListener = object : BeatClock.BeatListener {
        override fun onBeat(beatIndex: Int) {
            obstacles.onBeat(beatIndex)
        }
    }

    init {
        beatClock.listener = beatListener
        obstacles.onHit = { _ -> onPlayerHit() }
        obstacles.onNearMiss = {
            combo.onNearMiss()
            postJudgement("NEAR MISS")
        }
        beatClock.start()
    }

    fun restart() {
        player.reset()
        obstacles.reset()
        combo.reset()
        distance = 0f
        scroll = 0f
        scrollSpeed = GameConfig.BASE_SPEED
        hitFlashTimer = 0f
        state = State.RUNNING
        beatClock.start()
    }

    fun pause() = beatClock.pause()
    fun resume() = beatClock.resume()

    private fun onPlayerHit() {
        player.onHit()
        combo.onHit()
        hitFlashTimer = 0.5f
        postJudgement("HIT!")
        if (!player.isAlive) {
            state = State.GAMEOVER
        }
    }

    private fun postJudgement(text: String) {
        lastJudgement = text
        lastJudgementTime = System.currentTimeMillis()
    }

    /** 固定タイムステップで呼ばれる */
    fun update(dt: Float, input: SwipeInput) {
        beatClock.update()

        if (state == State.GAMEOVER) {
            // タップでリスタート
            if (input.consumeBoost() || input.consumeLaneChange() != 0) {
                restart()
            }
            return
        }

        // --- 入力 ---
        val laneDir = input.consumeLaneChange()
        if (laneDir != 0 && player.changeLane(laneDir)) {
            judgeLaneChange()
        }
        if (input.consumeBoost()) {
            player.tryBoost()
        }

        // --- 進行 ---
        val speedMul = if (player.isBoosting) GameConfig.BOOST_SPEED_MUL else 1f
        scrollSpeed = MathUtil.clamp(
            GameConfig.BASE_SPEED + distance * GameConfig.SPEED_GAIN_PER_M,
            GameConfig.BASE_SPEED, GameConfig.MAX_SPEED
        )
        val effSpeed = scrollSpeed * speedMul
        distance += effSpeed * dt
        scroll += effSpeed * dt
        combo.addDistanceScore(effSpeed * dt * 0.1f)

        player.update(dt)
        obstacles.update(dt, effSpeed, player)
        combo.update(dt)

        if (hitFlashTimer > 0f) hitFlashTimer -= dt
    }

    /** 車線変更タイミングのリズム判定（PERFECT ±80ms / GOOD ±160ms） */
    private fun judgeLaneChange() {
        val diff = abs(beatClock.offsetFromNearestBeat(beatClock.songTimeMs))
        when {
            diff <= GameConfig.PERFECT_MS -> {
                combo.onPerfect()
                postJudgement("PERFECT")
            }
            diff <= GameConfig.GOOD_MS -> {
                combo.onGood()
                postJudgement("GOOD")
            }
        }
    }

    /** スクロール速度（描画側参照用） */
    val effectiveSpeed: Float
        get() = scrollSpeed * (if (player.isBoosting) GameConfig.BOOST_SPEED_MUL else 1f)
}
