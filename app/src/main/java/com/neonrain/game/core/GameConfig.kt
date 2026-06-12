package com.neonrain.game.core

/**
 * 全定数。技術設計書 §1 / 企画書付録の数値に準拠。
 */
object GameConfig {
    // --- ゲームループ（danmaku-3d踏襲: 固定タイムステップ120Hz） ---
    const val FIXED_DT = 1f / 120f
    const val MAX_ACCUMULATOR = 0.25f
    const val MAX_STEPS_PER_FRAME = 4

    // --- レンダリング ---
    const val RENDER_HEIGHT = 1280       // 縦解像度固定（フィルレート一定化）
    const val BLOOM_DIVISOR = 4          // ブルームは1/4解像度
    const val BLOOM_THRESHOLD = 1.0f     // HDR値のみ滲む
    const val CAMERA_FOV = 64f
    const val CAMERA_BOOST_FOV = 74f
    const val CAMERA_NEAR = 0.5f
    const val CAMERA_FAR = 260f

    // --- 道路・車線 ---
    const val LANE_COUNT = 3
    const val LANE_WIDTH = 2.4f
    const val ROAD_LENGTH = 200f         // 路面平面の奥行き
    const val CHUNK_LENGTH = 20f         // 道路チャンク長
    const val CHUNK_COUNT = 10           // リングバッファ数（200m分）

    // --- プレイヤー ---
    const val LANE_CHANGE_RATE = 14f     // 車線スムーズ移動の収束率
    const val PLAYER_Z = 0f              // 自車は常にz=0（世界が流れる）
    const val BOOST_DURATION = 1.5f
    const val BOOST_COOLDOWN = 5f
    const val BOOST_SPEED_MUL = 1.5f
    const val HIT_INVINCIBLE = 1.5f
    const val MAX_HP = 3

    // --- スクロール速度 ---
    const val BASE_SPEED = 10f           // m/s（開始）
    const val MAX_SPEED = 18f
    const val SPEED_GAIN_PER_M = 0.0008f // 距離に応じた漸増

    // --- ビート・判定 ---
    const val DEFAULT_BPM = 80f
    const val PERFECT_MS = 80f
    const val GOOD_MS = 160f
    const val SPAWN_LEAD_BEATS = 8f      // 何拍先までスポーン予約するか

    // --- 障害物 ---
    const val OBSTACLE_POOL = 64
    const val HIT_Z_RANGE = 1.6f         // 衝突とみなすzの絶対値
    const val NEAR_MISS_RANGE = 3.2f

    // --- コンボ段階（企画書 4.3） ---
    val COMBO_STEPS = intArrayOf(10, 25, 50, 100, 200)

    // --- 雨 ---
    const val RAIN_COUNT = 1500

    // --- カラーパレット（画面設計書 §3.1） ---
    val COL_BASE = floatArrayOf(0.051f, 0.043f, 0.118f)        // #0D0B1E
    val COL_BUILDING = floatArrayOf(0.102f, 0.086f, 0.200f)    // #1A1633
    val COL_NEON_PINK = floatArrayOf(1.0f, 0.180f, 0.533f)     // #FF2E88
    val COL_NEON_CYAN = floatArrayOf(0.0f, 0.898f, 1.0f)       // #00E5FF
    val COL_VIOLET = floatArrayOf(0.616f, 0.302f, 1.0f)        // #9D4DFF
    val COL_AMBER = floatArrayOf(1.0f, 0.702f, 0.278f)         // #FFB347
    val COL_MINT = floatArrayOf(0.239f, 1.0f, 0.784f)          // #3DFFC8
}
