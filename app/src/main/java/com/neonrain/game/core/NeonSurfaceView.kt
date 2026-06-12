package com.neonrain.game.core

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.neonrain.game.input.SwipeInput

/**
 * GLSurfaceView拡張＋入力受付。
 * レンダー解像度は縦1280px相当に固定し、高解像度端末でもフィルレートを一定化する（§2.3）。
 */
class NeonSurfaceView(context: Context) : GLSurfaceView(context) {

    var swipeInput: SwipeInput? = null

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > GameConfig.RENDER_HEIGHT) {
            val scaledW = w * GameConfig.RENDER_HEIGHT / h
            holder.setFixedSize(scaledW, GameConfig.RENDER_HEIGHT)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return swipeInput?.onTouchEvent(event) ?: super.onTouchEvent(event)
    }
}
