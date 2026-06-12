package com.neonrain.game.audio

import kotlin.math.floor

/**
 * ビートクロック（Phase 2 暫定版）。
 * BGM実装前のため nanoTime 基準の固定BPMメトロノームとして動作する。
 * 将来は MusicConductor（AudioTrack の再生ヘッド位置）に songTimeMs の供給元を差し替える。
 */
class BeatClock(var bpm: Float = 80f, var offsetMs: Double = 0.0) {

    /** 拍イベントリスナー（GC回避のため事前確保で登録する） */
    interface BeatListener {
        fun onBeat(beatIndex: Int)
    }

    var songTimeMs = 0.0; private set
    var songBeat = 0.0; private set      // 連続ビート値
    var beatPhase = 0f; private set      // fract(songBeat)
    var beatIndex = -1; private set

    var listener: BeatListener? = null

    private var startNs = 0L
    private var running = false
    private var pausedAtMs = 0.0

    val beatLengthMs: Double get() = 60000.0 / bpm

    fun start() {
        startNs = System.nanoTime()
        running = true
        beatIndex = -1
        pausedAtMs = 0.0
    }

    fun pause() {
        if (!running) return
        pausedAtMs = currentMs()
        running = false
    }

    fun resume() {
        if (running) return
        startNs = System.nanoTime() - (pausedAtMs * 1_000_000.0).toLong()
        running = true
    }

    private fun currentMs(): Double = (System.nanoTime() - startNs) / 1_000_000.0

    fun update() {
        if (!running) return
        songTimeMs = currentMs() - offsetMs
        songBeat = songTimeMs / beatLengthMs
        if (songBeat < 0.0) {
            beatPhase = 0f
            return
        }
        beatPhase = (songBeat - floor(songBeat)).toFloat()
        val idx = floor(songBeat).toInt()
        while (beatIndex < idx) {
            beatIndex++
            listener?.onBeat(beatIndex)
        }
    }

    /** 指定ビートの楽曲時刻(ms) */
    fun beatToMs(beat: Double): Double = beat * beatLengthMs

    /** 直近ビートとの時間差(ms)。判定に使用 */
    fun offsetFromNearestBeat(timeMs: Double): Double {
        val len = beatLengthMs
        val nearest = Math.round(timeMs / len) * len
        return timeMs - nearest
    }
}
