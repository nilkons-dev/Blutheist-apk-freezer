package com.ankan.netfreeze

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout

/** Plays the opening animation (assets/splash.html) and then opens the app. */
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var opened = false
    private var createdAt = 0L
    private val go = Runnable { openMain() }

    companion object {
        private const val DURATION_MS = 3900L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UI.BG
        window.navigationBarColor = UI.BG
        createdAt = SystemClock.elapsedRealtime()

        val root = FrameLayout(this)
        root.setBackgroundColor(UI.BG)

        try {
            val w = WebView(this)
            w.setBackgroundColor(UI.BG)
            w.isVerticalScrollBarEnabled = false
            w.isHorizontalScrollBarEnabled = false
            w.overScrollMode = View.OVER_SCROLL_NEVER
            w.settings.javaScriptEnabled = false
            if (Build.VERSION.SDK_INT >= 29) {
                w.settings.forceDark = WebSettings.FORCE_DARK_OFF
            }
            w.loadUrl("file:///android_asset/splash.html")
            root.addView(w, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
            web = w
        } catch (e: Throwable) {
            web = null
        }

        // transparent overlay: tap anywhere to skip
        val overlay = View(this)
        overlay.setOnClickListener { openMain() }
        root.addView(overlay, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))

        setContentView(root)

        if (web == null) {
            // no WebView on this device: skip the animation
            handler.post(go)
        }
    }

    override fun onStart() {
        super.onStart()
        if (web != null) {
            val left = DURATION_MS - (SystemClock.elapsedRealtime() - createdAt)
            handler.postDelayed(go, if (left > 0L) left else 0L)
        }
    }

    override fun onStop() {
        handler.removeCallbacks(go)
        super.onStop()
    }

    private fun openMain() {
        if (opened) return
        opened = true
        handler.removeCallbacks(go)
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(go)
        val w = web
        if (w != null) {
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy()
        }
        web = null
        super.onDestroy()
    }
}
