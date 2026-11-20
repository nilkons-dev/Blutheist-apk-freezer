package com.ankan.netfreeze

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

object Tools {
    const val FREEZE = 0
    const val SPEED = 1
    const val DATA = 2
    const val BATTERY = 3
    const val DEVICE = 4
    const val PRIVACY = 5
    const val APPLOCK = 6
    const val DUPES = 7
    const val ANALYZER = 8
    const val QUICK = 9
    const val UNUSED = 10
    const val CLIPBOARD = 11

    fun open(ctx: Context, nav: Nav, id: Int) {
        when (id) {
            FREEZE -> nav.push(FreezeScreen(ctx, nav))
            SPEED -> nav.push(SpeedScreen(ctx, nav))
            DATA -> nav.push(AppMgmtScreen(ctx, nav, 1))
            BATTERY -> nav.push(AppMgmtScreen(ctx, nav, 2))
            DEVICE -> nav.push(DeviceScreen(ctx, nav))
            PRIVACY -> nav.push(PrivacyScreen(ctx, nav))
            APPLOCK -> nav.push(AppLockScreen(ctx, nav))
            DUPES -> nav.push(DuplicatesScreen(ctx, nav))
            ANALYZER -> nav.push(StorageScreen(ctx, nav, Cleaner.root()))
            QUICK -> nav.push(QuickSettingsScreen(ctx, nav))
            UNUSED -> nav.push(UnusedAppsScreen(ctx, nav))
            else -> nav.toast(if (Sys.clearClipboard(ctx)) "Clipboard cleared" else "Could not clear clipboard")
        }
    }
}

/** Label + value + thin bar block used in the device overview card. */
private class Metric(ctx: Context, icon: Int, title: String) {
    val root = LinearLayout(ctx)
    val value = UI.text(ctx, "—", 14f, UI.MUTED)
    val bar = ThinProgress(ctx)

    init {
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(UI.dp(ctx, 18), UI.dp(ctx, 14), UI.dp(ctx, 18), UI.dp(ctx, 14))
        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val ic = IconView(ctx, icon)
        ic.tint = UI.TEXT
        top.addView(ic, UI.lp(UI.dp(ctx, 22), UI.dp(ctx, 22)))
        top.addView(UI.text(ctx, title, 16f, UI.TEXT, UI.medium()), UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 12), 0, 0, 0))
        top.addView(value, UI.lp(UI.WRAP, UI.WRAP))
        root.addView(top)
        root.addView(bar, UI.lp(UI.MATCH, UI.dp(ctx, 6), 0f, 0, UI.dp(ctx, 10), 0, 0))
    }
}

class HomeScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val root = FrameLayout(ctx)
    override val view: View get() = root

    private val ring = RingView(ctx)
    private val statusTv: TextView
    private val optimizeBtn: TextView
    private val storageSub: TextView
    private val securitySub: TextView
    private val boostSub: TextView
    private val mRam = Metric(ctx, Ic.BOLT, "Memory")
    private val mStorage = Metric(ctx, Ic.PIE, "Storage")
    private val mBattery = Metric(ctx, Ic.BATTERY, "Battery")
    private val mTemp = Metric(ctx, Ic.SUN, "Temperature")
    private val activityHost = LinearLayout(ctx)
    private var shownScore = 0
    private var scoreAnim: ValueAnimator? = null

    init {
        root.background = UI.glow(ctx)
        val sv = ScrollView(ctx)
        sv.isVerticalScrollBarEnabled = false
        sv.overScrollMode = View.OVER_SCROLL_NEVER
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 18), UI.dp(ctx, 20), UI.dp(ctx, 120))

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
        bar.setBackgroundColor(UI.DIM)
        head.addView(bar, UI.lp(UI.dp(ctx, 1), UI.dp(ctx, 24), 0f, UI.dp(ctx, 14), 0, UI.dp(ctx, 14), 0))
        head.addView(UI.text(ctx, "Phone Manager", 19f, Color.parseColor("#C9C9CF")), UI.lp(0, UI.WRAP, 1f))
        val menu = FrameLayout(ctx)
        menu.isClickable = true
        menu.setOnClickListener {
            UI.tap(it)
            nav.openTab(4)
        }
        menu.addView(IconView(ctx, Ic.SLIDERS), FrameLayout.LayoutParams(UI.dp(ctx, 26), UI.dp(ctx, 26), Gravity.CENTER))
        UI.press(menu, 0.88f)
        head.addView(menu, UI.lp(UI.dp(ctx, 44), UI.dp(ctx, 44)))
        col.addView(head, UI.lp(UI.MATCH, UI.dp(ctx, 48)))

        // gauge
        val rs = minOf(UI.dp(ctx, 230), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 150))
        ring.unitText = "pts"
        col.addView(ring, UI.lp(rs, rs, 0f, 0, UI.dp(ctx, 22), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        statusTv = UI.text(ctx, "", 17f, Color.parseColor("#E4E4E8"))
        statusTv.gravity = Gravity.CENTER
        col.addView(statusTv, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0))
        optimizeBtn = UI.pill(ctx, "Optimize", true)
        optimizeBtn.setOnClickListener {
            UI.tap(it)
            nav.push(OptimizeScreen(ctx, nav))
        }
        val bw = minOf(UI.dp(ctx, 300), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 80))
        col.addView(optimizeBtn, UI.lp(bw, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })

        // feature cards
        storageSub = UI.text(ctx, "", 14f, UI.MUTED)
        securitySub = UI.text(ctx, "", 14f, UI.MUTED)
        boostSub = UI.text(ctx, "", 14f, UI.MUTED)
        val appsSub = UI.text(ctx, "Install, upgrade and uninstall apps", 14f, UI.MUTED)
        val r1 = LinearLayout(ctx)
        r1.orientation = LinearLayout.HORIZONTAL
        r1.addView(featureCard(Ic.SPARKLE, "Storage cleanup", storageSub) { nav.push(CleanupScreen(ctx, nav, false)) }, UI.lp(0, UI.MATCH, 1f, 0, 0, UI.dp(ctx, 6), 0))
        r1.addView(featureCard(Ic.SHIELD_ALERT, "Viruses & risks", securitySub) { nav.push(SecurityScreen(ctx, nav, false)) }, UI.lp(0, UI.MATCH, 1f, UI.dp(ctx, 6), 0, 0, 0))
        col.addView(r1, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 30), 0, 0))
        val r2 = LinearLayout(ctx)
        r2.orientation = LinearLayout.HORIZONTAL
        r2.addView(featureCard(Ic.BOLT, "System boost", boostSub) { nav.push(BoostScreen(ctx, nav)) }, UI.lp(0, UI.MATCH, 1f, 0, 0, UI.dp(ctx, 6), 0))
        r2.addView(featureCard(Ic.GRID, "App management", appsSub) { nav.push(AppMgmtScreen(ctx, nav, 0)) }, UI.lp(0, UI.MATCH, 1f, UI.dp(ctx, 6), 0, 0, 0))
        col.addView(r2, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, 0))

        // device overview
        col.addView(UI.sectionLabel(ctx, "Device overview"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 28), 0, UI.dp(ctx, 10)))
        val ov = UI.card(ctx)
        val ms = arrayOf(mRam, mStorage, mBattery, mTemp)
        for (i in ms.indices) {
            ov.addView(ms[i].root)
            if (i < ms.size - 1) ov.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 18), 0, UI.dp(ctx, 18), 0))
        }
        col.addView(ov)

        // recent activity
        col.addView(UI.sectionLabel(ctx, "Recent activity"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 28), 0, UI.dp(ctx, 10)))
        activityHost.orientation = LinearLayout.VERTICAL
        col.addView(activityHost)

        // tools
        col.addView(UI.sectionLabel(ctx, "Tools"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 28), 0, UI.dp(ctx, 10)))
        val t1 = LinearLayout(ctx)
        t1.orientation = LinearLayout.HORIZONTAL
        t1.addView(toolTile(Ic.KEY, "App Lock", Tools.APPLOCK), UI.lp(0, UI.WRAP, 1f, 0, 0, UI.dp(ctx, 6), 0))
        t1.addView(toolTile(Ic.SNOW, "Net Freeze", Tools.FREEZE), UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 6), 0, 0, 0))
        col.addView(t1)
        val t2 = LinearLayout(ctx)
        t2.orientation = LinearLayout.HORIZONTAL
        t2.addView(toolTile(Ic.GAUGE, "Speed test", Tools.SPEED), UI.lp(0, UI.WRAP, 1f, 0, 0, UI.dp(ctx, 6), 0))
        t2.addView(toolTile(Ic.COPY, "Duplicate files", Tools.DUPES), UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 6), 0, 0, 0))
        col.addView(t2, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, 0))
        val more = UI.card(ctx)
        more.addView(UI.row(ctx, Ic.GRID, "All tools", "", true) { nav.openTab(3) }.root)
        col.addView(more, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, 0))

        sv.addView(col, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP))
        root.addView(sv, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        refresh(false)
    }

    private fun featureCard(icon: Int, title: String, sub: TextView, onClick: () -> Unit): View {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = UI.rounded(ctx, UI.CARD, 28f, UI.STROKE, 1f)
        c.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 16), UI.dp(ctx, 20))
        c.minimumHeight = UI.dp(ctx, 158)
        c.isClickable = true
        c.setOnClickListener {
            UI.tap(it)
            onClick()
        }
        UI.press(c)
        c.addView(UI.iconBox(ctx, icon, 50, 25f), UI.lp(UI.dp(ctx, 50), UI.dp(ctx, 50)))
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
        c.setOnClickListener {
            UI.tap(it)
            Tools.open(ctx, nav, id)
        }
        UI.press(c)
        val ic = IconView(ctx, icon)
        ic.tint = Color.parseColor("#D4D4DA")
        c.addView(ic, UI.lp(UI.dp(ctx, 24), UI.dp(ctx, 24)))
        val t = UI.text(ctx, label, 16f, Color.parseColor("#D4D4DA"), UI.medium())
        t.isSingleLine = true
        c.addView(t, UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 12), 0, 0, 0))
        return c
    }

    override fun onShow() {
        refresh(true)
    }

    private fun refresh(animate: Boolean) {
        val st = Sys.storage()
        val usedS = st.total - st.free
        storageSub.text = Sys.fmtBytes(usedS) + " / " + Sys.fmtBytes(st.total)
        val m = Sys.mem(ctx)
        val memUsed = m.total - m.avail
        val memPct = (memUsed * 100 / m.total.coerceAtLeast(1L)).toInt()
        boostSub.text = "RAM $memPct% used"
        val risk = Prefs.getInt(ctx, "risk_count", 0)
        securitySub.text = when {
            Prefs.getLong(ctx, "last_scan", 0L) == 0L -> "Tap to check your phone"
            risk == 0 -> "No risky apps"
            else -> "$risk item(s) to review"
        }
        mRam.value.text = Sys.fmtBytes(memUsed) + " of " + Sys.fmtBytes(m.total)
        if (animate) mRam.bar.value = 0f
        mRam.bar.animateTo(memUsed.toFloat() / m.total.coerceAtLeast(1L), 900)
        mStorage.value.text = Sys.fmtBytes(usedS) + " of " + Sys.fmtBytes(st.total)
        if (animate) mStorage.bar.value = 0f
        mStorage.bar.animateTo(usedS.toFloat() / st.total.coerceAtLeast(1L), 1000)
        val b = Sys.battery(ctx)
        mBattery.value.text = b.level.toString() + "% · " + b.statusText
        if (animate) mBattery.bar.value = 0f
        mBattery.bar.animateTo(b.level / 100f, 1100)
        mTemp.value.text = Sys.fmtTemp(ctx, b.tempC)
        if (animate) mTemp.bar.value = 0f
        mTemp.bar.animateTo((b.tempC.toFloat() / 60f), 1200)
        buildActivity()

        val score = Sys.score(ctx)
        UI.swapText(
            statusTv,
            when {
                score >= 90 -> "Your system is in good condition."
                score >= 70 -> "Your system could be better."
                else -> "Your system needs attention."
            }
        )
        animateScore(score, animate)
    }

    private fun buildActivity() {
        activityHost.removeAllViews()
        val c = UI.card(ctx)
        val recs = Prefs.records(ctx).take(4)
        if (recs.isEmpty()) {
            val t = UI.text(ctx, "No activity yet. Tap Optimize to clean, boost and check your phone.", 14.5f, UI.MUTED)
            t.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20), UI.dp(ctx, 20))
            c.addView(t)
        } else {
            for (i in recs.indices) {
                val p = recs[i].split("|")
                if (p.size < 3) continue
                val r = ItemRow(ctx)
                r.showIcon(Ic.CHECK)
                r.title.text = p[1]
                r.sub.text = p[2]
                r.rtext.visibility = View.VISIBLE
                val t = p[0].toLongOrNull() ?: 0L
                r.rtext.text = Sys.ago(t)
                c.addView(r)
                if (i < recs.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
            }
        }
        activityHost.addView(c)
    }

    private fun animateScore(target: Int, animate: Boolean) {
        scoreAnim?.cancel()
        if (!animate) {
            shownScore = target
            ring.valueText = target.toString()
            ring.progress = target / 100f
            return
        }
        val a = ValueAnimator.ofInt(shownScore, target)
        a.duration = UI.dur(900)
        a.addUpdateListener {
            val v = it.animatedValue as Int
            shownScore = v
            ring.valueText = v.toString()
            ring.progress = v / 100f
        }
        scoreAnim = a
        a.start()
    }
}

/** Runs cleanup, privacy, security and memory steps one after another. */
class OptimizeScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Optimize")
    override val view: View get() = page.root
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val ring = RingView(ctx)
    private val label = UI.text(ctx, "Getting ready…", 20f, UI.TEXT, UI.medium())
    private val steps = ArrayList<StepRow>()
    private val btn = UI.pill(ctx, "Cancel", false)
    private val cancel = AtomicBoolean(false)
    private var started = false
    private var finished = false

    init {
        val rs = minOf(UI.dp(ctx, 220), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 160))
        ring.unitText = "pts"
        ring.valueText = Sys.score(ctx).toString()
        ring.progress = 0f
        page.add(ring, UI.lp(rs, rs, 0f, 0, UI.dp(ctx, 14), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        label.gravity = Gravity.CENTER
        label.isSingleLine = true
        label.ellipsize = android.text.TextUtils.TruncateAt.END
        page.add(label, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 10), UI.dp(ctx, 34), UI.dp(ctx, 10), 0))
        page.add(UI.sectionLabel(ctx, "Scan records"), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 38), 0, UI.dp(ctx, 10)))
        val card = UI.card(ctx)
        val defs = arrayOf(
            Triple(Ic.SPARKLE, "Cleanup", "Waiting…"),
            Triple(Ic.USER, "Privacy", "Waiting…"),
            Triple(Ic.SHIELD, "Security protection", "Waiting…"),
            Triple(Ic.GAUGE, "General", "Waiting…")
        )
        for (i in defs.indices) {
            val s = StepRow(ctx, defs[i].first, defs[i].second, defs[i].third)
            steps.add(s)
            card.addView(s)
            if (i < defs.size - 1) card.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 76), 0, UI.dp(ctx, 16), 0))
        }
        page.add(card)
        btn.setOnClickListener {
            if (finished) nav.pop() else {
                cancel.set(true)
                btn.text = "Stopping…"
            }
        }
        page.setBottom(btn)
    }

    override fun onShow() {
        if (started) return
        started = true
        ring.setSpinning(true)
        run()
    }

    private fun ui(block: () -> Unit) {
        main.post { block() }
    }

    private fun run() {
        val app = ctx.applicationContext
        Thread {
            var freedStorage = 0L
            var freedRam = 0L
            var stopped = 0
            var issues = 0
            var summary = ""

            // 1. cleanup
            ui {
                steps[0].state(StatusView.RUNNING, "Clearing app caches…")
                label.text = "Scanning: cache files"
            }
            freedStorage += Cleaner.clearSystemCaches(app)
            freedStorage += Cleaner.clearOwnCache(app)
            if (Sys.hasStorageAccess(app)) {
                freedStorage += Cleaner.clearLegacyAppCaches(Cleaner.root())
                if (!cancel.get()) {
                    val r = Cleaner.scan(Cleaner.root(), cancel) { n, _ ->
                        ui { steps[0].subTv.text = "Scanning storage… $n files" }
                    }
                    if (!cancel.get()) {
                        freedStorage += Cleaner.deleteJunk(r)
                        Cleaner.last = r
                    }
                }
            }
            val s1 = if (freedStorage > 0L) Sys.fmtBytes(freedStorage) + " cleared" else "Storage is already clean"
            ui {
                steps[0].state(StatusView.DONE, s1)
                ring.animateTo(0.25f, 500)
            }

            // 2. privacy
            if (!cancel.get()) {
                ui {
                    steps[1].state(StatusView.RUNNING, "Clearing clipboard…")
                    label.text = "Scanning: privacy"
                }
                val clip = Sys.clearClipboard(app)
                var sensitive = 0
                val pm = app.packageManager
                for (a in AppRepo.items) {
                    if (!a.hasLauncher || cancel.get()) continue
                    try {
                        val pi: PackageInfo = pm.getPackageInfo(a.pkg, PackageManager.GET_PERMISSIONS)
                        val perms = pi.requestedPermissions ?: continue
                        val fl = pi.requestedPermissionsFlags ?: continue
                        for (i in perms.indices) {
                            val p = perms[i]
                            if ((p.endsWith("CAMERA") || p.endsWith("RECORD_AUDIO") || p.endsWith("ACCESS_FINE_LOCATION")) &&
                                i < fl.size && (fl[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                            ) {
                                sensitive++
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                val s2 = (if (clip) "Clipboard cleared · " else "") + sensitive + " apps use camera, mic or location"
                ui {
                    steps[1].state(StatusView.DONE, s2)
                    ring.animateTo(0.45f, 500)
                }
            }

            // 3. security
            if (!cancel.get()) {
                ui { steps[2].state(StatusView.RUNNING, "Checking settings and apps…") }
                val rep = Scanner.run(app, false, cancel) { _, stage, detail, f ->
                    ui {
                        label.text = "Scanning: $detail"
                        steps[2].subTv.text = stage + ": " + detail
                        ring.animateTo(0.45f + 0.35f * f, 200)
                    }
                }
                if (!cancel.get()) {
                    Scanner.last = rep
                    issues = rep.risky() + rep.failedChecks()
                    Prefs.putInt(app, "risk_count", rep.risky())
                    Prefs.putLong(app, "last_scan", System.currentTimeMillis())
                    Prefs.addRecord(app, "Security check", rep.apps.toString() + " apps checked. " + (if (rep.risky() == 0) "No risks found." else rep.risky().toString() + " need review."))
                    val s3 = if (issues == 0) "No risks found" else issues.toString() + " item(s) to review"
                    ui { steps[2].state(if (issues == 0) StatusView.DONE else StatusView.WARN, s3) }
                }
            }

            // 4. general (memory)
            if (!cancel.get()) {
                ui {
                    steps[3].state(StatusView.RUNNING, "Stopping background apps…")
                    label.text = "Scanning: memory"
                    ring.animateTo(0.85f, 300)
                }
                val before = Sys.mem(app).avail
                stopped = Sys.killBackground(app)
                System.gc()
                try {
                    Thread.sleep(1400)
                } catch (e: InterruptedException) {
                    // ignore
                }
                freedRam = (Sys.mem(app).avail - before).coerceAtLeast(0L)
                val s4 = (if (freedRam > 0L) Sys.fmtBytes(freedRam) + " memory freed · " else "") + stopped + " apps stopped"
                ui { steps[3].state(StatusView.DONE, s4) }
            }

            val wasCancelled = cancel.get()
            if (!wasCancelled) {
                Prefs.putLong(app, "last_optimize", System.currentTimeMillis())
                summary = Sys.fmtBytes(freedStorage) + " storage, " + Sys.fmtBytes(freedRam) + " memory"
                Prefs.addRecord(app, "Optimize", "Freed $summary")
            }
            ui {
                finished = true
                ring.setSpinning(false)
                ring.valueText = Sys.score(app).toString()
                ring.animateTo(Sys.score(app) / 100f, 700)
                label.text = if (wasCancelled) "Optimization stopped" else "Your phone is optimized"
                btn.text = "Done"
                btn.background = UI.pillBg(ctx, true)
                btn.setTextColor(UI.ON_ACCENT)
            }
        }.start()
    }
}

/** Bottom-bar tab: every tool with a short description. */
class ToolsScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Tools", scroll = true, back = false)
    override val view: View get() = page.root

    init {
        page.add(
            UI.text(ctx, "Everything you need to keep your phone fast, clean and private.", 14.5f, UI.MUTED),
            UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), 0, UI.dp(ctx, 6), UI.dp(ctx, 6))
        )
        section("Protect", listOf(
            Tools.APPLOCK to Triple(Ic.KEY, "App Lock", "Lock apps behind a PIN or fingerprint."),
            Tools.PRIVACY to Triple(Ic.LOCK, "Privacy Center", "See which apps use camera, mic, location, contacts and SMS."),
            Tools.CLIPBOARD to Triple(Ic.CLIPBOARD, "Clear clipboard", "Remove copied passwords and text in one tap.")
        ))
        section("Performance", listOf(
            Tools.FREEZE to Triple(Ic.SNOW, "Net Freeze", "Block background internet for chosen apps."),
            Tools.SPEED to Triple(Ic.GAUGE, "Speed test", "Download, upload, ping and live latency."),
            Tools.UNUSED to Triple(Ic.HOURGLASS, "Unused apps", "Find apps you have not opened in 30 days.")
        ))
        section("Files", listOf(
            Tools.DUPES to Triple(Ic.COPY, "Duplicate files", "Find identical files and free the wasted space."),
            Tools.ANALYZER to Triple(Ic.PIE, "Storage analyzer", "See which folders take the most space.")
        ))
        section("System", listOf(
            Tools.DEVICE to Triple(Ic.PHONE, "Device info", "Model, Android version, processor, memory, screen."),
            Tools.BATTERY to Triple(Ic.BATTERY, "Battery", "Level, temperature, voltage and health."),
            Tools.DATA to Triple(Ic.DROP, "Data usage", "Which apps use Wi-Fi and mobile data."),
            Tools.QUICK to Triple(Ic.SLIDERS, "Quick settings", "Jump to Wi-Fi, display, sound, storage and more.")
        ))
    }

    private fun section(title: String, items: List<Pair<Int, Triple<Int, String, String>>>) {
        page.add(UI.sectionLabel(ctx, title), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, UI.dp(ctx, 10)))
        val c = UI.card(ctx)
        for (i in items.indices) {
            val id = items[i].first
            val t = items[i].second
            c.addView(descRow(ctx, t.first, t.second, t.third) { Tools.open(ctx, nav, id) })
            if (i < items.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 76), 0, UI.dp(ctx, 16), 0))
        }
        page.add(c)
    }
}
