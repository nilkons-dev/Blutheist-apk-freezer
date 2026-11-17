package com.ankan.netfreeze

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), Host {

    private val reqVpn = 100
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var content: FrameLayout
    private lateinit var home: HomeTab
    private lateinit var apps: AppsTab
    private lateinit var speed: SpeedTab
    private lateinit var tabs: List<Tab>
    private val navIcons = ArrayList<TabIconView>()
    private val navLabels = ArrayList<TextView>()

    private var current = -1
    private var resumed = false
    private var reapplyPending = false

    private val reapply = Runnable {
        reapplyPending = false
        if (NetBlockService.running) sendStart()
    }

    private val ticker = object : Runnable {
        override fun run() {
            home.refresh()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UI.BG
        window.navigationBarColor = UI.SURFACE

        home = HomeTab(this, this)
        apps = AppsTab(this, this)
        speed = SpeedTab(this, this)
        tabs = listOf(home, apps, speed)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(UI.BG)

        content = FrameLayout(this)
        root.addView(content, UI.lp(UI.MATCH, 0, 1f))
        root.addView(buildNav(), UI.lp(UI.MATCH, UI.WRAP))
        setContentView(root)

        select(0)
        AppRepo.load(this) { home.refresh() }
    }

    private fun buildNav(): View {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setBackgroundColor(UI.SURFACE)

        val line = View(this)
        line.setBackgroundColor(UI.STROKE)
        wrap.addView(line, UI.lp(UI.MATCH, 1))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val labels = arrayOf("Freeze", "Apps", "Speed")
        for (i in labels.indices) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(0, UI.dp(this, 9), 0, UI.dp(this, 9))
            item.isClickable = true
            val sel = UI.selectable(this)
            if (sel != null) item.background = sel
            item.setOnClickListener { select(i) }

            val icon = TabIconView(this, i)
            item.addView(icon, UI.lp(UI.dp(this, 26), UI.dp(this, 26)))
            val label = UI.text(this, labels[i], 11f, UI.MUTED, UI.medium())
            label.gravity = Gravity.CENTER
            item.addView(label, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(this, 4), 0, 0))

            row.addView(item, UI.lp(0, UI.WRAP, 1f))
            navIcons.add(icon)
            navLabels.add(label)
        }
        wrap.addView(row, UI.lp(UI.MATCH, UI.WRAP))
        return wrap
    }

    private fun select(index: Int) {
        if (index == current) return
        if (current >= 0 && resumed) tabs[current].onHide()
        current = index
        content.removeAllViews()
        content.addView(tabs[index].view, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        for (k in navIcons.indices) {
            val on = k == index
            navIcons[k].setSelectedState(on)
            navLabels[k].setTextColor(if (on) UI.ACCENT else UI.MUTED)
        }
        if (resumed) tabs[index].onShow()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (current >= 0) tabs[current].onShow()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        // apply a pending selection change while we are still in the foreground
        if (reapplyPending) {
            handler.removeCallbacks(reapply)
            reapply.run()
        }
        if (current >= 0) tabs[current].onHide()
        resumed = false
        super.onPause()
    }

    // ------------------------------------------------------------------ Host

    override fun toggleFreeze() {
        if (NetBlockService.running) {
            try {
                startService(
                    Intent(this, NetBlockService::class.java).setAction(NetBlockService.ACTION_STOP)
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Could not stop: " + e.message, Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "VPN is not available on this device", Toast.LENGTH_LONG).show()
                }
            } else {
                sendStart()
            }
        }
        handler.postDelayed({ home.refresh() }, 300L)
    }

    override fun exemptChanged() {
        home.refresh()
        if (NetBlockService.running) {
            reapplyPending = true
            handler.removeCallbacks(reapply)
            handler.postDelayed(reapply, 800L)
        }
    }

    private fun sendStart() {
        try {
            startService(
                Intent(this, NetBlockService::class.java).setAction(NetBlockService.ACTION_START)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqVpn) {
            if (resultCode == RESULT_OK) {
                sendStart()
                handler.postDelayed({ home.refresh() }, 300L)
            } else {
                Toast.makeText(this, "VPN permission is required to freeze apps", Toast.LENGTH_LONG).show()
            }
        }
    }
}
