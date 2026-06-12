package com.neonrain.game.input

import android.view.MotionEvent
import kotlin.math.abs

/**
 * 左右スワイプ（車線変更）とタップ（ブースト）の検出。
 * GLスレッドから読み取られるため、イベントはフラグとして蓄積する。
 */
class SwipeInput(private val density: Float) {

    private val swipeThresholdPx = 80f * density
    private val swipeTimeMs = 250L
    private val tapTimeMs = 200L
    private val tapSlopPx = 24f * density

    // GLスレッドが消費するペンディング入力（-1:左, +1:右）
    @Volatile var pendingLaneChange = 0
    @Volatile var pendingBoost = false

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var consumed = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                consumed = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!consumed) {
                    val dx = event.x - downX
                    if (abs(dx) > swipeThresholdPx &&
                        event.eventTime - downTime <= swipeTimeMs
                    ) {
                        pendingLaneChange = if (dx > 0) 1 else -1
                        consumed = true
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!consumed) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx < tapSlopPx && dy < tapSlopPx &&
                        event.eventTime - downTime <= tapTimeMs
                    ) {
                        pendingBoost = true
                    }
                }
            }
        }
        return true
    }

    /** GLスレッドから呼び出し、入力を取り出してクリア */
    fun consumeLaneChange(): Int {
        val v = pendingLaneChange
        pendingLaneChange = 0
        return v
    }

    fun consumeBoost(): Boolean {
        val v = pendingBoost
        pendingBoost = false
        return v
    }
}
