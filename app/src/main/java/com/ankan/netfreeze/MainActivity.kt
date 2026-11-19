package com.ankan.netfreeze

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), Nav {

    private val reqVpn = 100
    private val reqStorage = 200
    private val handler = Handler(Looper.getMainLooper())

    private val stack = ArrayList<Screen>()
    private val tabs = ArrayList<Screen>()
    private lateinit var host: FrameLayout
    private lateinit var navBar: FrameLayout
    private lateinit var indicator: View
    private val navIcons = ArrayList<IconView>()
    private val navLabels = ArrayList<TextView>()
    private var itemW = 0
    private var tab = 0
    private var resumed = false
    private var reapplyPending = false

    private val reapply = Runnable {
        reapplyPending = false
        if (NetBlockService.running) sendStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UI.loadTheme(this)
        window.statusBarColor = UI.BG
        window.navigationBarColor = UI.BG
        val reopen = Prefs.getInt(this, "reopen_tab", -1)
        if (reopen in 0..4) tab = reopen
        Prefs.remove(this, "reopen_tab")

        val root = FrameLayout(this)
        root.setBackgroundColor(UI.BG)
        host = FrameLayout(this)
        root.addView(host, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))

        tabs.add(HomeScreen(this, this))
        tabs.add(CleanupScreen(this, this, true))
        tabs.add(SecurityScreen(this, this, true))
        tabs.add(ToolsScreen(this, this))
        tabs.add(SettingsScreen(this, this, true))

        navBar = buildNav()
        val barW = minOf(UI.dp(this, 400), resources.displayMetrics.widthPixels - UI.dp(this, 24))
        val lp = FrameLayout.LayoutParams(barW, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = UI.dp(this, 14)
        root.addView(navBar, lp)
        setContentView(root)

        stack.add(tabs[tab])
        render(0)
        AppRepo.load(this) { }
    }

    private fun buildNav(): FrameLayout {
        val names = arrayOf("Home", "Clean", "Security", "Tools", "Settings")
        val icons = intArrayOf(Ic.HOME, Ic.SPARKLE, Ic.SHIELD, Ic.GRID, Ic.SLIDERS)
        val barW = minOf(UI.dp(this, 400), resources.displayMetrics.widthPixels - UI.dp(this, 24))
        val pad = UI.dp(this, 6)
        itemW = (barW - 2 * pad) / names.size
        val itemH = UI.dp(this, 60)

        val bar = FrameLayout(this)
        bar.background = UI.rounded(this, UI.blend(UI.BG, Color.WHITE, 0.07f), 36f, UI.STROKE, 1f)
        bar.elevation = UI.dpf(this, 8f)

        indicator = View(this)
        indicator.background = UI.rounded(this, UI.blend(UI.BG, Color.WHITE, 0.15f), 28f)
        val ilp = FrameLayout.LayoutParams(itemW, itemH)
        ilp.leftMargin = pad
        ilp.topMargin = pad
        bar.addView(indicator, ilp)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (i in names.indices) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.isClickable = true
            item.setOnClickListener {
                UI.tap(it)
                selectTab(i)
            }
            val ic = IconView(this, icons[i])
            item.addView(ic, UI.lp(UI.dp(this, 24), UI.dp(this, 24)))
            val label = UI.text(this, names[i], 11f, UI.MUTED, UI.medium())
            label.gravity = Gravity.CENTER
            item.addView(label, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(this, 4), 0, 0))
            row.addView(item, UI.lp(0, UI.MATCH, 1f))
            navIcons.add(ic)
            navLabels.add(label)
        }
        val rlp = FrameLayout.LayoutParams(barW - 2 * pad, itemH)
        rlp.leftMargin = pad
        rlp.topMargin = pad
        bar.addView(row, rlp)
        return bar
    }

    private fun top(): Screen = stack[stack.size - 1]

    private fun tintTo(i: Int, to: Int, animate: Boolean) {
        val ic = navIcons[i]
        val lbl = navLabels[i]
        val from = ic.tint
        if (!animate || !UI.anim) {
            ic.tint = to
            lbl.setTextColor(to)
            return
        }
        val a = ValueAnimator.ofObject(ArgbEvaluator(), from, to)
        a.duration = 240
        a.addUpdateListener {
            val c = it.animatedValue as Int
            ic.tint = c
            lbl.setTextColor(c)
        }
        a.start()
    }

    private fun applyNav(animate: Boolean) {
        for (k in navIcons.indices) {
            val on = k == tab
            tintTo(k, if (on) UI.ACCENT else UI.MUTED, animate)
            if (on && animate && UI.anim) {
                navIcons[k].animate().scaleX(1.22f).scaleY(1.22f).setDuration(130).withEndAction {
                    navIcons[k].animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                }.start()
            }
        }
        val target = (tab * itemW).toFloat()
        if (animate && UI.anim) {
            indicator.animate().translationX(target).setDuration(300).setInterpolator(DecelerateInterpolator(1.8f)).start()
        } else {
            indicator.translationX = target
        }
    }

    private fun render(dir: Int) {
        val t = top()
        val v = t.view
        host.removeAllViews()
        host.addView(v, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        v.animate().cancel()
        if (UI.anim) {
            v.alpha = 0f
            v.translationX = dir * UI.dpf(this, 56f)
            v.animate().alpha(1f).translationX(0f).setDuration(300).setInterpolator(DecelerateInterpolator(1.7f)).start()
        } else {
            v.alpha = 1f
            v.translationX = 0f
        }
        if (dir >= 0) UI.stagger(v)

        val showBar = stack.size == 1
        if (showBar && navBar.visibility != View.VISIBLE) {
            navBar.visibility = View.VISIBLE
            if (UI.anim) {
                navBar.translationY = UI.dpf(this, 90f)
                navBar.animate().translationY(0f).setDuration(320).setInterpolator(DecelerateInterpolator(1.6f)).start()
            }
        } else if (!showBar && navBar.visibility == View.VISIBLE) {
            if (UI.anim) {
                navBar.animate().translationY(UI.dpf(this, 90f)).setDuration(220).withEndAction { navBar.visibility = View.GONE }.start()
            } else {
                navBar.visibility = View.GONE
            }
        }
        applyNav(false)
        if (resumed) t.onShow()
    }

    private fun selectTab(i: Int) {
        if (i == tab) return
        if (stack.size > 1) {
            while (stack.size > 1) stack.removeAt(stack.size - 1)
        }
        if (resumed) top().onHide()
        val dir = if (i > tab) 1 else -1
        tab = i
        stack[0] = tabs[i]
        render(dir)
        applyNav(true)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        top().onShow()
    }

    override fun onPause() {
        if (reapplyPending) {
            handler.removeCallbacks(reapply)
            reapply.run()
        }
        top().onHide()
        resumed = false
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.size > 1) {
            pop()
        } else if (tab != 0) {
            selectTab(0)
        } else {
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ Nav

    override fun push(s: Screen) {
        if (resumed) top().onHide()
        stack.add(s)
        render(1)
    }

    override fun pop() {
        if (stack.size > 1) {
            if (resumed) top().onHide()
            stack.removeAt(stack.size - 1)
            render(-1)
        }
    }

    override fun openTab(i: Int) {
        if (i in 0..4) selectTab(i)
    }

    override fun restartUi() {
        Prefs.putInt(this, "reopen_tab", tab)
        recreate()
    }

    override fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun launch(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            toast("Not available on this phone")
        }
    }

    override fun openUsage() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            toast("Open Settings > Apps > Special access > Usage access")
        }
    }

    override fun openAllFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e2: Exception) {
                    toast("Open Settings > Apps > Special access > All files access")
                }
            }
        } else {
            requestStorage()
        }
    }

    override fun requestStorage() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), reqStorage)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reqStorage) top().onShow()
    }

    override fun toggleFreeze() {
        if (NetBlockService.running) {
            try {
                startService(Intent(this, NetBlockService::class.java).setAction(NetBlockService.ACTION_STOP))
            } catch (e: Exception) {
                toast("Could not stop: " + e.message)
            }
        } else {
            val prepare: Intent? = try {
                VpnService.prepare(this)
            } catch (e: Exception) {
                null
            }
            if (prepare != null) {
                try {
                    startActivityForResult(prepare, reqVpn)
                } catch (e: Exception) {
                    toast("VPN is not available on this device")
                }
            } else {
                sendStart()
            }
        }
    }

    override fun exemptChanged() {
        if (NetBlockService.running) {
            reapplyPending = true
            handler.removeCallbacks(reapply)
            handler.postDelayed(reapply, 800L)
        }
    }

    private fun sendStart() {
        try {
            startService(Intent(this, NetBlockService::class.java).setAction(NetBlockService.ACTION_START))
        } catch (e: Exception) {
            toast("Could not start: " + e.message)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqVpn) {
            if (resultCode == RESULT_OK) sendStart() else toast("VPN permission is required to freeze apps")
        }
    }
}
