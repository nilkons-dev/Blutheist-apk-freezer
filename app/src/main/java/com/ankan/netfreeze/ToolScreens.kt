package com.ankan.netfreeze

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

// ------------------------------------------------------------------------------------- net freeze

class FreezeScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Net Freeze")
    override val view: View get() = page.root
    private val handler = Handler(Looper.getMainLooper())
    private val shield = RingView(ctx)
    private val titleTv = UI.text(ctx, "", 26f, UI.TEXT, UI.bold())
    private val subTv = UI.text(ctx, "", 14.5f, UI.MUTED)
    private val statApps = UI.text(ctx, "—", 20f, UI.TEXT, UI.bold())
    private val statReq = UI.text(ctx, "—", 20f, UI.TEXT, UI.bold())
    private val statTime = UI.text(ctx, "—", 20f, UI.TEXT, UI.bold())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500L)
        }
    }

    init {
        shield.iconKind = Ic.SNOW
        shield.setOnClickListener {
            UI.tap(it)
            nav.toggleFreeze()
        }
        UI.press(shield, 0.94f)
        val rs = minOf(UI.dp(ctx, 200), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 170))
        page.add(shield, UI.lp(rs, rs, 0f, 0, UI.dp(ctx, 8), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        titleTv.gravity = Gravity.CENTER
        page.add(titleTv, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0))
        subTv.gravity = Gravity.CENTER
        page.add(subTv, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 10), UI.dp(ctx, 8), UI.dp(ctx, 10), 0))

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val g = UI.dp(ctx, 5)
        row.addView(stat("Apps frozen", statApps), UI.lp(0, UI.WRAP, 1f, 0, 0, g, 0))
        row.addView(stat("Blocked", statReq), UI.lp(0, UI.WRAP, 1f, g, 0, g, 0))
        row.addView(stat("Frozen for", statTime), UI.lp(0, UI.WRAP, 1f, g, 0, 0, 0))
        page.add(row, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 28), 0, 0))

        val card = UI.card(ctx)
        card.addView(UI.row(ctx, Ic.GRID, "Choose apps", "", true) { nav.push(FreezeAppsScreen(ctx, nav)) }.root)
        page.add(card, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 16), 0, 0))
        val info = UI.text(ctx, "Frozen apps get no internet. Your hotspot and the apps you keep allowed get the full bandwidth. This app is never frozen.", 13f, UI.MUTED)
        info.gravity = Gravity.CENTER
        page.add(info, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 8), UI.dp(ctx, 18), UI.dp(ctx, 8), 0))
    }

    private fun stat(label: String, value: TextView): View {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.background = UI.rounded(ctx, UI.CARD, 22f, UI.STROKE, 1f)
        c.setPadding(UI.dp(ctx, 6), UI.dp(ctx, 16), UI.dp(ctx, 6), UI.dp(ctx, 16))
        value.gravity = Gravity.CENTER
        c.addView(value)
        val l = UI.text(ctx, label, 12f, UI.MUTED)
        l.gravity = Gravity.CENTER
        c.addView(l, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, 0))
        return c
    }

    override fun onShow() {
        AppRepo.load(ctx) { refresh() }
        handler.post(ticker)
    }

    override fun onHide() {
        handler.removeCallbacks(ticker)
    }

    private fun refresh() {
        val on = NetBlockService.running
        shield.progress = if (on) 1f else 0f
        shield.dimmed = !on
        val apps = if (AppRepo.loaded) AppRepo.frozenCount(Prefs.exempt(ctx)).toString() else "…"
        UI.setIfChanged(statApps, apps)
        if (on) {
            UI.swapText(titleTv, "Frozen")
            UI.setIfChanged(subTv, "Background apps have no internet access")
            UI.setIfChanged(statReq, compact(NetBlockService.packets.get()))
            val secs = ((SystemClock.elapsedRealtime() - NetBlockService.startedAt) / 1000L).coerceAtLeast(0L)
            UI.setIfChanged(statTime, duration(secs))
        } else {
            UI.swapText(titleTv, "Tap the circle to freeze")
            UI.setIfChanged(subTv, "Block internet for background apps with one tap")
            UI.setIfChanged(statReq, "—")
            UI.setIfChanged(statTime, "—")
        }
    }

    private fun compact(n: Long): String = when {
        n < 1000L -> n.toString()
        n < 1_000_000L -> String.format(Locale.US, "%.1fK", n / 1000.0)
        else -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    }

    private fun duration(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }
}

class FreezeAppsScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Choose apps", scroll = false)
    override val view: View get() = page.root
    private val list = Lists.cardList(ctx)
    private val subtitle = UI.text(ctx, " ", 13.5f, UI.MUTED)
    private val empty = Lists.emptyText(ctx, "No apps found")
    private val sysChip = UI.smallPill(ctx, "+ System")
    private val adapter: ItemAdapter
    private var shown: List<AppItem> = emptyList()
    private var exempt = HashSet<String>()
    private var query = ""
    private var showSystem = false

    init {
        adapter = ItemAdapter(ctx, { shown.size }) { row, pos ->
            val a = shown[pos]
            val frozen = a.pkg !in exempt
            bindAppIcon(ctx, row, a.pkg)
            row.title.text = a.label
            row.sub.text = if (frozen) "Frozen" else "Internet allowed"
            row.pill.visibility = View.VISIBLE
            row.pill.set(frozen, false)
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos in shown.indices) {
                val p = shown[pos].pkg
                if (p in exempt) exempt.remove(p) else exempt.add(p)
                commit()
            }
        }

        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.VERTICAL
        top.addView(
            UI.text(ctx, "Switch ON = frozen. Switch OFF = keeps internet. Turn OFF the apps you still need, like your browser or messenger.", 13f, UI.MUTED),
            UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), 0, UI.dp(ctx, 6), UI.dp(ctx, 12))
        )
        val search = EditText(ctx)
        search.hint = "Search apps"
        search.setHintTextColor(UI.MUTED)
        search.setTextColor(UI.TEXT)
        search.textSize = 15f
        search.isSingleLine = true
        search.imeOptions = EditorInfo.IME_ACTION_DONE
        search.background = UI.rounded(ctx, UI.CARD, 26f, UI.STROKE, 1f)
        search.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 13), UI.dp(ctx, 20), UI.dp(ctx, 13))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
            }
        })
        top.addView(search, UI.lp(UI.MATCH, UI.WRAP))
        val btns = LinearLayout(ctx)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.gravity = Gravity.CENTER_VERTICAL
        btns.addView(subtitle, UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 6), 0, 0, 0))
        val fa = UI.smallPill(ctx, "Freeze all")
        fa.setOnClickListener { exempt.clear(); commit() }
        val aa = UI.smallPill(ctx, "Allow all")
        aa.setOnClickListener {
            for (a in AppRepo.items) exempt.add(a.pkg)
            commit()
        }
        sysChip.setOnClickListener { showSystem = !showSystem; styleChip(); applyFilter() }
        btns.addView(sysChip, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, 0, UI.dp(ctx, 6), 0))
        btns.addView(fa, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, 0, UI.dp(ctx, 6), 0))
        btns.addView(aa, UI.lp(UI.WRAP, UI.WRAP))
        top.addView(btns, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, UI.dp(ctx, 12)))
        page.content.addView(top, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 20), 0, UI.dp(ctx, 20), 0))

        val frame = FrameLayout(ctx)
        frame.addView(list, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        frame.addView(empty, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP, Gravity.CENTER))
        page.content.addView(frame, UI.lp(UI.MATCH, 0, 1f, UI.dp(ctx, 20), 0, UI.dp(ctx, 20), UI.dp(ctx, 20)))
        styleChip()
    }

    private fun styleChip() {
        if (showSystem) {
            sysChip.setTextColor(Color.BLACK)
            sysChip.background = UI.rounded(ctx, Color.WHITE, 20f)
        } else {
            sysChip.setTextColor(UI.TEXT)
            sysChip.background = UI.rounded(ctx, UI.CARD2, 20f, UI.STROKE, 1f)
        }
    }

    override fun onShow() {
        exempt = HashSet(Prefs.exempt(ctx))
        AppRepo.load(ctx) { applyFilter() }
    }

    private fun applyFilter() {
        val q = query.trim().lowercase()
        val out = ArrayList<AppItem>()
        var frozenN = 0
        var allowedN = 0
        for (a in AppRepo.items) {
            if (!showSystem && !a.hasLauncher) continue
            if (a.pkg in exempt) allowedN++ else frozenN++
            if (q.isNotEmpty() && !a.label.lowercase().contains(q) && !a.pkg.lowercase().contains(q)) continue
            out.add(a)
        }
        shown = out
        adapter.notifyDataSetChanged()
        subtitle.text = "$frozenN frozen · $allowedN allowed"
        empty.visibility = if (out.isEmpty() && AppRepo.loaded) View.VISIBLE else View.GONE
    }

    private fun commit() {
        Prefs.saveExempt(ctx, exempt)
        applyFilter()
        nav.exemptChanged()
    }
}

// ----------------------------------------------------------------------------------------- speed

class SpeedScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Speed test")
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())

    private val connLabel = UI.text(ctx, "—", 13f, UI.TEXT, UI.medium())
    private val stageLabel = UI.text(ctx, "READY", 12.5f, UI.MUTED, UI.medium())
    private val bigValue = UI.text(ctx, "0.0", 64f, UI.TEXT, UI.bold())
    private val unitLabel = UI.text(ctx, "Mbps", 15f, UI.MUTED)
    private val bar = ThinProgress(ctx)
    private val startBtn = UI.pill(ctx, "Start test", true)
    private val pingV = UI.text(ctx, "—", 24f, UI.TEXT, UI.bold())
    private val jitterV = UI.text(ctx, "—", 24f, UI.TEXT, UI.bold())
    private val downV = UI.text(ctx, "—", 24f, UI.TEXT, UI.bold())
    private val upV = UI.text(ctx, "—", 24f, UI.TEXT, UI.bold())
    private val liveValue = UI.text(ctx, "— ms", 30f, UI.TEXT, UI.bold())
    private val liveQuality = UI.text(ctx, "Waiting…", 13f, UI.MUTED, UI.medium())
    private val chart = LineChartView(ctx)

    private var visible = false
    private var testing = false
    private var token = 0
    private var test: SpeedTest? = null
    private val live = LiveLatency { ms -> onLiveSample(ms) }

    init {
        // connection badge
        connLabel.setPadding(UI.dp(ctx, 14), UI.dp(ctx, 7), UI.dp(ctx, 14), UI.dp(ctx, 7))
        connLabel.background = UI.rounded(ctx, UI.CARD, 18f, UI.STROKE, 1f)
        page.add(connLabel, UI.lp(UI.WRAP, UI.WRAP, 0f, UI.dp(ctx, 4), 0, 0, 0))

        val hero = UI.card(ctx)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 24), UI.dp(ctx, 20), UI.dp(ctx, 22))
        stageLabel.letterSpacing = 0.18f
        hero.addView(stageLabel)
        hero.addView(bigValue, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, 0))
        hero.addView(unitLabel, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))
        hero.addView(bar, UI.lp(UI.MATCH, UI.dp(ctx, 5), 0f, 0, UI.dp(ctx, 22), 0, 0))
        startBtn.setOnClickListener { if (testing) stopTest() else startTest() }
        hero.addView(startBtn, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0))
        page.add(hero, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 14), 0, 0))

        val gap = UI.dp(ctx, 5)
        val r1 = LinearLayout(ctx)
        r1.orientation = LinearLayout.HORIZONTAL
        r1.addView(metric("PING", pingV, "ms"), UI.lp(0, UI.WRAP, 1f, 0, 0, gap, 0))
        r1.addView(metric("JITTER", jitterV, "ms"), UI.lp(0, UI.WRAP, 1f, gap, 0, 0, 0))
        page.add(r1, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, 0))
        val r2 = LinearLayout(ctx)
        r2.orientation = LinearLayout.HORIZONTAL
        r2.addView(metric("DOWNLOAD", downV, "Mbps"), UI.lp(0, UI.WRAP, 1f, 0, 0, gap, 0))
        r2.addView(metric("UPLOAD", upV, "Mbps"), UI.lp(0, UI.WRAP, 1f, gap, 0, 0, 0))
        page.add(r2, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 10), 0, 0))

        val liveCard = UI.card(ctx)
        liveCard.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 18), UI.dp(ctx, 20), UI.dp(ctx, 16))
        val lh = LinearLayout(ctx)
        lh.orientation = LinearLayout.HORIZONTAL
        lh.gravity = Gravity.CENTER_VERTICAL
        val lt = UI.text(ctx, "LIVE LATENCY", 12f, UI.MUTED, UI.medium())
        lt.letterSpacing = 0.15f
        lh.addView(lt, UI.lp(0, UI.WRAP, 1f))
        lh.addView(liveQuality, UI.lp(UI.WRAP, UI.WRAP))
        liveCard.addView(lh)
        liveCard.addView(liveValue, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 10), 0, 0))
        liveCard.addView(chart, UI.lp(UI.MATCH, UI.dp(ctx, 110), 0f, 0, UI.dp(ctx, 10), 0, 0))
        liveCard.addView(UI.text(ctx, "TCP latency to a public server, once a second", 12f, UI.MUTED), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        page.add(liveCard, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, 0))

        val foot = UI.text(ctx, "This app is never frozen, so these numbers always show your real connection. Turn on Net Freeze and watch the latency drop.", 12.5f, UI.MUTED)
        foot.gravity = Gravity.CENTER
        page.add(foot, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), UI.dp(ctx, 18), UI.dp(ctx, 6), 0))
    }

    private fun metric(label: String, value: TextView, unit: String): View {
        val card = UI.card(ctx)
        card.setPadding(UI.dp(ctx, 18), UI.dp(ctx, 16), UI.dp(ctx, 18), UI.dp(ctx, 16))
        val l = UI.text(ctx, label, 12f, UI.MUTED, UI.medium())
        l.letterSpacing = 0.15f
        card.addView(l)
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.BOTTOM
        row.addView(value, UI.lp(UI.WRAP, UI.WRAP))
        row.addView(UI.text(ctx, unit, 13f, UI.MUTED), UI.lp(UI.WRAP, UI.WRAP, 0f, UI.dp(ctx, 6), 0, 0, UI.dp(ctx, 3)))
        card.addView(row, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        return card
    }

    private fun resetResults() {
        pingV.text = "—"
        jitterV.text = "—"
        downV.text = "—"
        upV.text = "—"
    }

    private fun styleButton(running: Boolean) {
        startBtn.text = if (running) "Stop" else "Start test"
        startBtn.alpha = 1f
    }

    override fun onShow() {
        visible = true
        UI.setIfChanged(connLabel, connectionLabel())
        if (!testing) live.start()
    }

    override fun onHide() {
        visible = false
        live.stop()
    }

    private fun onLiveSample(ms: Double?) {
        chart.add(ms)
        if (ms == null) {
            liveValue.text = "— ms"
            liveQuality.text = "Timeout"
        } else {
            liveValue.text = String.format(Locale.US, "%.0f ms", ms)
            liveQuality.text = when {
                ms < 50.0 -> "Excellent"
                ms < 100.0 -> "Good"
                ms < 200.0 -> "Fair"
                else -> "Poor"
            }
        }
    }

    private fun stopTest() {
        token += 1
        test?.cancel()
        test = null
        testing = false
        styleButton(false)
        bar.value = 0f
        stageLabel.text = "CANCELLED"
        if (visible) live.start()
    }

    private fun finishTest() {
        testing = false
        test = null
        styleButton(false)
        if (visible) live.start()
    }

    private fun startTest() {
        if (testing) return
        testing = true
        token += 1
        val my = token
        live.stop()
        resetResults()
        styleButton(true)
        bar.value = 0f
        bigValue.text = "0.0"
        unitLabel.text = "ms"
        stageLabel.text = "STARTING"
        UI.setIfChanged(connLabel, connectionLabel())

        val t = SpeedTest(object : SpeedTest.Callback {
            override fun onStage(stage: Int) {
                main.post {
                    if (my != token) return@post
                    when (stage) {
                        0 -> { stageLabel.text = "MEASURING LATENCY"; unitLabel.text = "ms" }
                        1 -> { stageLabel.text = "TESTING DOWNLOAD"; unitLabel.text = "Mbps"; bigValue.text = "0.0" }
                        else -> { stageLabel.text = "TESTING UPLOAD"; unitLabel.text = "Mbps"; bigValue.text = "0.0" }
                    }
                }
            }

            override fun onPing(sample: Double?, index: Int, total: Int) {
                main.post {
                    if (my != token) return@post
                    onLiveSample(sample)
                    if (sample != null) {
                        bigValue.text = String.format(Locale.US, "%.0f", sample)
                        pingV.text = String.format(Locale.US, "%.0f", sample)
                    }
                    bar.value = 0.15f * index / total
                }
            }

            override fun onSpeed(stage: Int, mbps: Double, progress: Float) {
                main.post {
                    if (my != token) return@post
                    val s = fmtSpeed(mbps)
                    bigValue.text = s
                    if (stage == 1) {
                        downV.text = s
                        bar.value = 0.15f + 0.45f * progress
                    } else {
                        upV.text = s
                        bar.value = 0.6f + 0.4f * progress
                    }
                }
            }

            override fun onDone(result: SpeedTest.Outcome) {
                main.post {
                    if (my != token) return@post
                    pingV.text = result.ping?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
                    jitterV.text = result.jitter?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
                    downV.text = result.down?.let { fmtSpeed(it) } ?: "—"
                    upV.text = result.up?.let { fmtSpeed(it) } ?: "—"
                    unitLabel.text = "Mbps"
                    bigValue.text = result.down?.let { fmtSpeed(it) } ?: "—"
                    bar.value = 1f
                    stageLabel.text = if (result.lossPercent > 0) "COMPLETE · " + result.lossPercent + "% PACKET LOSS" else "TEST COMPLETE"
                    finishTest()
                }
            }

            override fun onError(message: String) {
                main.post {
                    if (my != token) return@post
                    stageLabel.text = message.uppercase()
                    bar.value = 0f
                    finishTest()
                }
            }
        })
        test = t
        t.start()
    }

    private fun fmtSpeed(v: Double): String =
        if (v >= 100.0) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)

    private fun connectionLabel(): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return "Offline"
            val caps = cm.getNetworkCapabilities(n) ?: return "Offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
        } catch (e: Exception) {
            "—"
        }
    }
}

// ------------------------------------------------------------------------------------- device info

class DeviceScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Device info")
    override val view: View get() = page.root

    override fun onShow() {
        page.content.removeAllViews()
        val dm = ctx.resources.displayMetrics
        val m = Sys.mem(ctx)
        val st = Sys.storage()
        val b = Sys.battery(ctx)
        val abi = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "—"
        val rows = arrayOf(
            Pair("Device", Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL),
            Pair("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"),
            Pair("Security patch", Build.VERSION.SECURITY_PATCH),
            Pair("Processor", Build.HARDWARE + " · " + Runtime.getRuntime().availableProcessors() + " cores"),
            Pair("Architecture", abi),
            Pair("Memory", Sys.fmtBytes(m.total) + " (" + Sys.fmtBytes(m.avail) + " free)"),
            Pair("Storage", Sys.fmtBytes(st.total - st.free) + " of " + Sys.fmtBytes(st.total)),
            Pair("Screen", dm.widthPixels.toString() + " × " + dm.heightPixels + " · " + dm.densityDpi + " dpi"),
            Pair("Battery", b.level.toString() + "% · " + b.statusText),
            Pair("Uptime", Sys.fmtDuration(SystemClock.elapsedRealtime())),
            Pair("Build", Build.DISPLAY)
        )
        val c = UI.card(ctx)
        for (i in rows.indices) {
            val r = UI.row(ctx, -1, rows[i].first, rows[i].second, false, null)
            r.value.setTextColor(UI.TEXT)
            r.value.maxLines = 3
            c.addView(r.root)
            if (i < rows.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        page.add(c)
    }
}

// ---------------------------------------------------------------------------------- privacy center

class PrivacyScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Privacy Center")
    override val view: View get() = page.root
    private val main = Handler(Looper.getMainLooper())
    private val host = LinearLayout(ctx)
    private var loaded = false

    private class Group(val icon: Int, val title: String, val perms: List<String>) {
        val apps = ArrayList<Pair<String, String>>()
    }

    init {
        page.add(
            UI.text(ctx, "See which of your apps can use sensitive features right now. Tap a group, then an app to change its permissions.", 14f, UI.MUTED),
            UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), 0, UI.dp(ctx, 6), UI.dp(ctx, 16))
        )
        host.orientation = LinearLayout.VERTICAL
        page.add(host)
    }

    override fun onShow() {
        if (loaded) return
        host.removeAllViews()
        host.addView(UI.text(ctx, "Checking apps…", 15f, UI.MUTED))
        AppRepo.load(ctx) { scan() }
    }

    private fun scan() {
        val app = ctx.applicationContext
        Thread {
            val groups = listOf(
                Group(Ic.CAMERA, "Camera", listOf("android.permission.CAMERA")),
                Group(Ic.MIC, "Microphone", listOf("android.permission.RECORD_AUDIO")),
                Group(Ic.PIN, "Location", listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")),
                Group(Ic.USER, "Contacts", listOf("android.permission.READ_CONTACTS")),
                Group(Ic.CHAT, "SMS", listOf("android.permission.READ_SMS", "android.permission.RECEIVE_SMS")),
                Group(Ic.CLOCK, "Call log", listOf("android.permission.READ_CALL_LOG"))
            )
            val pm = app.packageManager
            for (a in AppRepo.items) {
                if (!a.hasLauncher) continue
                val pi: PackageInfo = try {
                    pm.getPackageInfo(a.pkg, PackageManager.GET_PERMISSIONS)
                } catch (e: Exception) {
                    continue
                }
                val perms = pi.requestedPermissions ?: continue
                val flags = pi.requestedPermissionsFlags ?: continue
                for (g in groups) {
                    var has = false
                    for (p in g.perms) {
                        val i = perms.indexOf(p)
                        if (i >= 0 && i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) has = true
                    }
                    if (has) g.apps.add(Pair(a.pkg, a.label))
                }
            }
            main.post {
                loaded = true
                build(groups)
            }
        }.start()
    }

    private fun build(groups: List<Group>) {
        host.removeAllViews()
        val c = UI.card(ctx)
        for (i in groups.indices) {
            val g = groups[i]
            c.addView(UI.row(ctx, g.icon, g.title, g.apps.size.toString() + " apps", true) { showApps(g) }.root)
            if (i < groups.size - 1) c.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        }
        host.addView(c)
    }

    private fun showApps(g: Group) {
        if (g.apps.isEmpty()) {
            nav.toast("No apps use " + g.title.lowercase())
            return
        }
        val names = g.apps.map { it.second }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle(g.title + " access")
            .setItems(names) { _, which -> openAppSettings(nav, g.apps[which].first) }
            .setNegativeButton("Close", null)
            .show()
    }
}
