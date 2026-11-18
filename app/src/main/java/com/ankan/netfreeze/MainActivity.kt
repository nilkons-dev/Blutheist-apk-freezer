package com.ankan.netfreeze

import android.Manifest
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), Nav {

    private val reqVpn = 100
    private val reqStorage = 200
    private val handler = Handler(Looper.getMainLooper())

    private val stack = ArrayList<Screen>()
    private lateinit var host: FrameLayout
    private lateinit var navBar: LinearLayout
    private lateinit var home: HomeScreen
    private lateinit var utilities: UtilitiesScreen
    private val navIcons = ArrayList<IconView>()
    private val navLabels = ArrayList<TextView>()
    private var tab = 0
    private var resumed = false
    private var reapplyPending = false

    private val reapply = Runnable {
        reapplyPending = false
        if (NetBlockService.running) sendStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        host = FrameLayout(this)
        root.addView(host, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))

        home = HomeScreen(this, this)
        utilities = UtilitiesScreen(this, this)

        navBar = buildNav()
        val w = minOf(UI.dp(this, 300), resources.displayMetrics.widthPixels - UI.dp(this, 40))
        val lp = FrameLayout.LayoutParams(w, UI.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = UI.dp(this, 16)
        root.addView(navBar, lp)
        setContentView(root)

        stack.add(home)
        render()
        AppRepo.load(this) { }
    }

    private fun buildNav(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.background = UI.rounded(this, Color.parseColor("#141416"), 36f, UI.STROKE, 1f)
        bar.setPadding(UI.dp(this, 8), UI.dp(this, 8), UI.dp(this, 8), UI.dp(this, 8))
        val names = arrayOf("Home", "Utilities")
        val icons = intArrayOf(Ic.SHIELD, Ic.GRID)
        for (i in names.indices) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(0, UI.dp(this, 8), 0, UI.dp(this, 8))
            item.isClickable = true
            item.setOnClickListener { selectTab(i) }
            val ic = IconView(this, icons[i])
            item.addView(ic, UI.lp(UI.dp(this, 26), UI.dp(this, 26)))
            val label = UI.text(this, names[i], 12.5f, UI.MUTED, UI.medium())
            label.gravity = Gravity.CENTER
            item.addView(label, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(this, 4), 0, 0))
            bar.addView(item, UI.lp(0, UI.WRAP, 1f))
            navIcons.add(ic)
            navLabels.add(label)
        }
        return bar
    }

    private fun top(): Screen = stack[stack.size - 1]

    private fun render() {
        val showUtil = Prefs.getBool(this, "show_utilities", true)
        if (!showUtil && tab == 1 && stack.size == 1) {
            tab = 0
            stack[0] = home
        }
        val t = top()
        host.removeAllViews()
        host.addView(t.view, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        t.view.alpha = 0f
        t.view.animate().alpha(1f).setDuration(150).start()
        navBar.visibility = if (stack.size == 1 && showUtil) View.VISIBLE else View.GONE
        for (k in navIcons.indices) {
            val on = k == tab
            navIcons[k].tint = if (on) Color.WHITE else UI.MUTED
            navLabels[k].setTextColor(if (on) Color.WHITE else UI.MUTED)
        }
        if (resumed) t.onShow()
    }

    private fun selectTab(i: Int) {
        if (stack.size != 1 || i == tab) return
        if (resumed) top().onHide()
        tab = i
        stack[0] = if (i == 0) home else utilities
        render()
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
        if (stack.size > 1) pop() else super.onBackPressed()
    }

    // ------------------------------------------------------------------ Nav

    override fun push(s: Screen) {
        if (resumed) top().onHide()
        stack.add(s)
        render()
    }

    override fun pop() {
        if (stack.size > 1) {
            if (resumed) top().onHide()
            stack.removeAt(stack.size - 1)
            render()
        }
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
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
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
        requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
            reqStorage
        )
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
