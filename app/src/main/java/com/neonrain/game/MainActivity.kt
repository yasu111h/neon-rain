package com.neonrain.game

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.neonrain.game.core.GameRenderer
import com.neonrain.game.core.NeonSurfaceView
import com.neonrain.game.game.GameWorld
import com.neonrain.game.input.SwipeInput
import com.neonrain.game.ui.HudOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var surfaceView: NeonSurfaceView
    private lateinit var gameWorld: GameWorld
    private lateinit var gameRenderer: GameRenderer
    private lateinit var swipeInput: SwipeInput
    private lateinit var hudOverlay: HudOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        swipeInput = SwipeInput(resources.displayMetrics.density)
        gameWorld = GameWorld(this)
        gameRenderer = GameRenderer(gameWorld, swipeInput)

        val rootLayout = FrameLayout(this)

        surfaceView = NeonSurfaceView(this)
        surfaceView.swipeInput = swipeInput
        surfaceView.setRenderer(gameRenderer)
        surfaceView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
        rootLayout.addView(
            surfaceView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        hudOverlay = HudOverlay(this)
        hudOverlay.world = gameWorld
        hudOverlay.renderer = gameRenderer
        rootLayout.addView(
            hudOverlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(rootLayout)
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    override fun onResume() {
        super.onResume()
        surfaceView.onResume()
        gameWorld.resume()
        hideSystemUI()
    }

    override fun onPause() {
        super.onPause()
        surfaceView.onPause()
        gameWorld.pause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
}
