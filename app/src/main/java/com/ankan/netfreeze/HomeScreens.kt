package com.ankan.netfreeze

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Tools {
    const val FREEZE = 0
    const val SPEED = 1
    const val DATA = 2
    const val BATTERY = 3
    const val DEVICE = 4
    const val PRIVACY = 5

    fun open(ctx: Context, nav: Nav, id: Int) {
        when (id) {
            FREEZE -> nav.push(FreezeScreen(ctx, nav))
            SPEED -> nav.push(SpeedScreen(ctx, nav))
            DATA -> nav.push(AppMgmtScreen(ctx, nav, 1))
            BATTERY -> nav.push(AppMgmtScreen(ctx, nav, 2))
            DEVICE -> nav.push(DeviceScreen(ctx, nav))
            else -> nav.push(PrivacyScreen(ctx, nav))
        }
    }
}

class HomeScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val root = FrameLayout(ctx)
    override val view: View get() = root
    private val main = Handler(Looper.getMainLooper())

    private val shield = ShieldView(ctx)
    private val scoreTv: TextView
    private val statusTv: TextView
    private val optimizeBtn: TextView
    private val storageSub: TextView
    private val securitySub: TextView
    private val boostSub: TextView
    private var shownScore = 0
    private var busy = false
    private var scoreAnim: ValueAnimator? = null

    init {
        root.background = UI.glow(ctx)
        val sv = ScrollView(ctx)
        sv.isVerticalScrollBarEnabled = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 18), UI.dp(ctx, 20), UI.dp(ctx, 130))

        // header
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val wm = ImageView(ctx)
        wm.setImageResource(R.drawable.wordmark)
        wm.adjustViewBounds = true
        wm.scaleType = ImageView.ScaleType.FIT_CENTER
        head.addView(wm, UI.lp(UI.WRAP, UI.dp(ctx, 28)))
        val bar = View(ctx)
        bar.setBackgroundColor(Color.parseColor("#3A3A40"))
        head.addView(bar, UI.lp(UI.dp(ctx, 1), UI.dp(ctx, 24), 0f, UI.dp(ctx, 14), 0, UI.dp(ctx, 14), 0))
        head.addView(UI.text(ctx, "Phone Manager", 19f, Color.parseColor("#C9C9CF")), UI.lp(0, UI.WRAP, 1f))
        val menu = FrameLayout(ctx)
        menu.isClickable = true
        menu.setOnClickListener { nav.push(SettingsScreen(ctx, nav)) }
        menu.addView(IconView(ctx, Ic.MORE), FrameLayout.LayoutParams(UI.dp(ctx, 26), UI.dp(ctx, 26), Gravity.CENTER))
        head.addView(menu, UI.lp(UI.dp(ctx, 44), UI.dp(ctx, 44)))
        col.addView(head, UI.lp(UI.MATCH, UI.dp(ctx, 48)))

        // shield + score
        col.addView(shield, UI.lp(UI.dp(ctx, 200), UI.dp(ctx, 250), 0f, 0, UI.dp(ctx, 16), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val scoreRow = LinearLayout(ctx)
        scoreRow.orientation = LinearLayout.HORIZONTAL
        scoreRow.gravity = Gravity.BOTTOM
        scoreTv = UI.text(ctx, "0", 66f, UI.TEXT, UI.bold())
        scoreRow.addView(scoreTv, UI.lp(UI.WRAP, UI.WRAP))
        scoreRow.addView(UI.text(ctx, "pts", 18f, UI.MUTED), UI.lp(UI.WRAP, UI.WRAP, 0f, UI.dp(ctx, 8), 0, 0, UI.dp(ctx, 10)))
        col.addView(scoreRow, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        statusTv = UI.text(ctx, "", 15f, Color.parseColor("#C9C9CF"))
        statusTv.gravity = Gravity.CENTER
        col.addView(statusTv, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 10), 0, 0))

        optimizeBtn = UI.pill(ctx, "Optimize", true)
        optimizeBtn.setOnClickListener { optimize() }
        val bw = minOf(UI.dp(ctx, 270), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 90))
        col.addView(optimizeBtn, UI.lp(bw, UI.WRAP, 0f, 0, UI.dp(ctx, 20), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })

        // feature cards
        storageSub = UI.text(ctx, "", 14f, UI.MUTED)
        securitySub = UI.text(ctx, "", 14f, UI.MUTED)
        boostSub = UI.text(ctx, "", 14f, UI.MUTED)
        val appsSub = UI.text(ctx, "Install, upgrade and uninstall apps", 14f, UI.MUTED)

        val r1 = LinearLayout(ctx)
        r1.orientation = LinearLayout.HORIZONTAL
        r1.addView(featureCard(Ic.SPARKLE, "Storage cleanup", storageSub) { nav.push(CleanupScreen(ctx, nav)) }, UI.lp(0, UI.MATCH, 1f, 0, 0, UI.dp(ctx, 6), 0))
        r1.addView(featureCard(Ic.SHIELD_ALERT, "Viruses & risks", securitySub) { nav.push(SecurityScreen(ctx, nav)) }, UI.lp(0, UI.MATCH, 1f, UI.dp(ctx, 6), 0, 0, 0))
        col.addView(r1, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 30), 0, 0))
        val r2 = LinearLayout(ctx)
        r2.orientation = LinearLayout.HORIZONTAL
        r2.addView(featureCard(Ic.BOLT, "System boost", boostSub) { nav.push(BoostScreen(ctx, nav)) }, UI.lp(0, UI.MATCH, 1f, 0, 0, UI.dp(ctx, 6), 0))
        r2.addView(featureCard(Ic.GRID, "App management", appsSub) { nav.push(AppMgmtScreen(ctx, nav, 0)) }, UI.lp(0, UI.MATCH, 1f, UI.dp(ctx, 6), 0, 0, 0))
        col.addView(r2, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, 0))

        // tools
        col.addView(UI.sectionLabel(ctx, "Tools"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 26), 0, UI.dp(ctx, 10)))
        val t1 = LinearLayout(ctx)
        t1.orientation = LinearLayout.HORIZONTAL
        t1.addView(toolTile(Ic.SNOW, "Net Freeze", Tools.FREEZE), UI.lp(0, UI.WRAP, 1f, 0, 0, UI.dp(ctx, 6), 0))
        t1.addView(toolTile(Ic.GAUGE, "Speed test", Tools.SPEED), UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 6), 0, 0, 0))
        col.addView(t1, UI.lp(UI.MATCH, UI.WRAP))
        val t2 = LinearLayout(ctx)
        t2.orientation = LinearLayout.HORIZONTAL
        t2.addView(toolTile(Ic.DROP, "Data usage", Tools.DATA), UI.lp(0, UI.WRAP, 1f, 0, 0, UI.dp(ctx, 6), 0))
        t2.addView(toolTile(Ic.LOCK, "Privacy Center", Tools.PRIVACY), UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 6), 0, 0, 0))
        col.addView(t2, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, 0))

        sv.addView(col, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP))
        root.addView(sv, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        refresh(false)
    }

    private fun featureCard(icon: Int, title: String, sub: TextView, onClick: () -> Unit): View {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = UI.rounded(ctx, UI.CARD, 28f, UI.STROKE, 1f)
        c.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 16), UI.dp(ctx, 20))
        c.minimumHeight = UI.dp(ctx, 150)
        c.isClickable = true
        c.setOnClickListener { onClick() }
        c.addView(IconView(ctx, icon), UI.lp(UI.dp(ctx, 30), UI.dp(ctx, 30)))
        c.addView(UI.text(ctx, title, 19f, UI.TEXT, UI.medium()), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 16), 0, 0))
        c.addView(sub, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 5), 0, 0))
        return c
    }

    private fun toolTile(icon: Int, label: String, id: Int): View {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.background = UI.rounded(ctx, UI.CARD, 26f, UI.STROKE, 1f)
        c.setPadding(UI.dp(ctx, 18), UI.dp(ctx, 18), UI.dp(ctx, 12), UI.dp(ctx, 18))
        c.isClickable = true
        c.setOnClickListener { Tools.open(ctx, nav, id) }
        val ic = IconView(ctx, icon)
        ic.tint = Color.parseColor("#C9C9CF")
        c.addView(ic, UI.lp(UI.dp(ctx, 24), UI.dp(ctx, 24)))
        val t = UI.text(ctx, label, 16f, Color.parseColor("#C9C9CF"), UI.medium())
        t.isSingleLine = true
        c.addView(t, UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 12), 0, 0, 0))
        return c
    }

    override fun onShow() {
        refresh(true)
    }

    private fun refresh(animate: Boolean) {
        val st = Sys.storage()
        storageSub.text = Sys.fmtBytes(st.total - st.free) + " / " + Sys.fmtBytes(st.total)
        val m = Sys.mem(ctx)
        val memPct = 100 - (m.avail * 100 / m.total.coerceAtLeast(1L))
        boostSub.text = "RAM $memPct% used"
        val risk = Prefs.getInt(ctx, "risk_count", 0)
        securitySub.text = when {
            Prefs.getLong(ctx, "last_scan", 0L) == 0L -> "Tap to check your apps"
            risk == 0 -> "No risky apps"
            else -> "$risk app(s) to review"
        }
        if (!busy) {
            val last = Prefs.getLong(ctx, "last_optimize", 0L)
            statusTv.text = if (last == 0L) "•  Shield active · Not optimized yet" else "•  Shield active · Optimized " + Sys.ago(last)
        }
        animateScore(Sys.score(ctx), animate)
    }

    private fun animateScore(target: Int, animate: Boolean) {
        scoreAnim?.cancel()
        if (!animate) {
            shownScore = target
            scoreTv.text = target.toString()
            shield.progress = target / 100f
            return
        }
        val a = ValueAnimator.ofInt(shownScore, target)
        a.duration = 800
        a.addUpdateListener {
            val v = it.animatedValue as Int
            shownScore = v
            scoreTv.text = v.toString()
            shield.progress = v / 100f
        }
        scoreAnim = a
        a.start()
    }

    private fun optimize() {
        if (busy) return
        busy = true
        optimizeBtn.text = "Optimizing…"
        optimizeBtn.alpha = 0.6f
        statusTv.text = "•  Cleaning memory and cache…"
        shield.setSpinning(true)
        val app = ctx.applicationContext
        Thread {
            val before = Sys.mem(app).avail
            Sys.killBackground(app)
            Cleaner.clearOwnCache(app)
            try {
                Thread.sleep(1500)
            } catch (e: InterruptedException) {
                // ignore
            }
            val after = Sys.mem(app).avail
            main.post {
                busy = false
                shield.setSpinning(false)
                optimizeBtn.text = "Optimize"
                optimizeBtn.alpha = 1f
                Prefs.putLong(app, "last_optimize", System.currentTimeMillis())
                val freed = (after - before).coerceAtLeast(0L)
                refresh(true)
                statusTv.text = if (freed > 0L) {
                    "•  Optimized · " + Sys.fmtBytes(freed) + " memory freed"
                } else {
                    "•  Optimized · memory already in good shape"
                }
            }
        }.start()
    }
}

class UtilitiesScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Utilities", scroll = true, back = false)
    override val view: View get() = page.root
    private val freezeRow: UI.RowH

    init {
        val card = UI.card(ctx)
        val items = arrayOf(
            Triple(Ic.SNOW, "Net Freeze", Tools.FREEZE),
            Triple(Ic.GAUGE, "Speed test", Tools.SPEED),
            Triple(Ic.DROP, "Data usage", Tools.DATA),
            Triple(Ic.BATTERY, "Battery", Tools.BATTERY),
            Triple(Ic.PHONE, "Device info", Tools.DEVICE),
            Triple(Ic.LOCK, "Privacy Center", Tools.PRIVACY)
        )
        var first: UI.RowH? = null
        for (i in items.indices) {
            val it = items[i]
            val r = UI.row(ctx, it.first, it.second, "", true) { Tools.open(ctx, nav, it.third) }
            if (i == 0) first = r
            card.addView(r.root)
            if (i < items.size - 1) card.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        freezeRow = first!!
        page.add(card)
        val card2 = UI.card(ctx)
        card2.addView(UI.row(ctx, Ic.SPARKLE, "Settings", "", true) { nav.push(SettingsScreen(ctx, nav)) }.root)
        page.add(card2, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 14), 0, 0))
    }

    override fun onShow() {
        freezeRow.value.text = if (NetBlockService.running) "On" else "Off"
    }
}
