package com.neonrain.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import com.neonrain.game.core.GameRenderer
import com.neonrain.game.game.GameWorld

/**
 * 暫定HUD（Android Viewオーバーレイ）。
 * 上端1行のみ: スコア / 残機 / 距離。判定テキストとコンボは中央に一時表示。
 * Phase 3でネオン質感のGL HUDに置き換える予定。
 */
class HudOverlay(context: Context) : View(context) {

    var world: GameWorld? = null
    var renderer: GameRenderer? = null

    private val density = resources.displayMetrics.density

    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 17f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#0090AA"))
    }
    private val lifePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2E88")
        textSize = 16f * density
        textAlign = Paint.Align.CENTER
        setShadowLayer(10f * density, 0f, 0f, Color.parseColor("#99003B"))
    }
    private val distPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A86A8")
        textSize = 15f * density
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }
    private val comboPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2E88")
        textSize = 34f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(16f * density, 0f, 0f, Color.parseColor("#FF2E88"))
    }
    private val judgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8E6F0")
        textSize = 30f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(14f * density, 0f, 0f, Color.parseColor("#00E5FF"))
    }
    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55556A")
        textSize = 11f * density
        typeface = Typeface.MONOSPACE
    }

    private val sb = StringBuilder(32)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = world ?: return
        val pad = 14f * density
        val topY = 34f * density

        // スコア（左上）
        sb.setLength(0)
        sb.append(w.combo.score)
        canvas.drawText(sb, 0, sb.length, pad, topY, scorePaint)

        // 残機（上中央）●＝雨粒
        sb.setLength(0)
        for (i in 0 until 3) sb.append(if (i < w.player.hp) "●" else "○").append(' ')
        canvas.drawText(sb, 0, sb.length, width / 2f, topY, lifePaint)

        // 距離（右上）
        sb.setLength(0)
        val km = w.distance / 1000f
        sb.append((km * 10).toInt() / 10f).append("km")
        canvas.drawText(sb, 0, sb.length, width - pad, topY, distPaint)

        // コンボ（中央やや上）
        if (w.combo.combo >= 2) {
            sb.setLength(0)
            sb.append('×').append(w.combo.combo)
            canvas.drawText(sb, 0, sb.length, width / 2f, height * 0.30f, comboPaint)
        }

        // 判定ポップ（1秒表示）
        val since = System.currentTimeMillis() - w.lastJudgementTime
        if (since < 900 && w.lastJudgement.isNotEmpty()) {
            judgePaint.color = when (w.lastJudgement) {
                "PERFECT" -> Color.parseColor("#3DFFC8")
                "GOOD" -> Color.parseColor("#00E5FF")
                "NEAR MISS" -> Color.parseColor("#9D4DFF")
                else -> Color.parseColor("#FF3355")
            }
            judgePaint.alpha = (255 * (1f - since / 900f)).toInt()
            judgePaint.setShadowLayer(12f * density, 0f, 0f, judgePaint.color)
            canvas.drawText(w.lastJudgement, width / 2f, height * 0.38f, judgePaint)
        }

        // ゲームオーバー
        if (w.state == GameWorld.State.GAMEOVER) {
            canvas.drawText("R U N   O V E R", width / 2f, height * 0.46f, centerPaint)
            canvas.drawText("TAP TO RETRY", width / 2f, height * 0.54f, centerPaint)
        }

        // FPS（開発用・左下）
        renderer?.let {
            sb.setLength(0)
            sb.append(it.fps).append(" fps")
            canvas.drawText(sb, 0, sb.length, pad, height - pad, fpsPaint)
        }

        postInvalidateDelayed(66) // 約15Hzで更新（GLとは独立）
    }
}
