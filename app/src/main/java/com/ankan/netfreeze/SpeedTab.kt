package com.ankan.netfreeze

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** Speed test (ping, jitter, download, upload) plus a live latency graph. */
class SpeedTab(private val ctx: Context, private val host: Host) : Tab {

    private val scroll = ScrollView(ctx)
    override val view: View get() = scroll

    private val main = Handler(Looper.getMainLooper())

    private val connLabel: TextView
    private val stageLabel: TextView
    private val bigValue: TextView
    private val unitLabel: TextView
    private val bar = ThinProgress(ctx)
    private val startBtn: TextView
    private val pingV: TextView
    private val jitterV: TextView
    private val downV: TextView
    private val upV: TextView
    private val liveValue: TextView
    private val liveQuality: TextView
    private val chart = LineChartView(ctx)

    private var visible = false
    private var testing = false
    private var token = 0
    private var test: SpeedTest? = null
    private val live = LiveLatency { ms -> onLiveSample(ms) }

    init {
        scroll.setBackgroundColor(UI.BG)
        scroll.isVerticalScrollBarEnabled = false

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val pad = UI.dp(ctx, 20)
        col.setPadding(pad, UI.dp(ctx, 22), pad, UI.dp(ctx, 24))

        // title row
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(UI.text(ctx, "Speed", 26f, UI.TEXT, UI.medium()), UI.lp(0, UI.WRAP, 1f))
        connLabel = UI.text(ctx, "—", 12f, UI.ACCENT, UI.medium())
        connLabel.setPadding(UI.dp(ctx, 12), UI.dp(ctx, 6), UI.dp(ctx, 12), UI.dp(ctx, 6))
        connLabel.background = UI.rounded(ctx, UI.CARD, 14f, UI.STROKE, 1f)
        head.addView(connLabel, UI.lp(UI.WRAP, UI.WRAP))
        col.addView(head, UI.lp(UI.MATCH, UI.WRAP))

        // hero card
        val hero = LinearLayout(ctx)
        hero.orientation = LinearLayout.VERTICAL
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.background = UI.rounded(ctx, UI.CARD, 22f, UI.STROKE, 1f)
        hero.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 22), UI.dp(ctx, 20), UI.dp(ctx, 20))

        stageLabel = UI.text(ctx, "READY", 12f, UI.MUTED, UI.medium())
        stageLabel.letterSpacing = 0.2f
        hero.addView(stageLabel, UI.lp(UI.WRAP, UI.WRAP))

        bigValue = UI.text(ctx, "0.0", 64f, UI.TEXT, UI.light())
        hero.addView(bigValue, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 10), 0, 0))
        unitLabel = UI.text(ctx, "Mbps", 14f, UI.ACCENT, UI.medium())
        hero.addView(unitLabel, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 4), 0, 0))

        hero.addView(bar, UI.lp(UI.MATCH, UI.dp(ctx, 4), 0f, 0, UI.dp(ctx, 20), 0, 0))

        startBtn = UI.text(ctx, "START TEST", 15f, Color.parseColor("#04121C"), UI.medium())
        startBtn.letterSpacing = 0.12f
        startBtn.gravity = Gravity.CENTER
        startBtn.setPadding(0, UI.dp(ctx, 15), 0, UI.dp(ctx, 15))
        startBtn.setOnClickListener { if (testing) stopTest() else startTest() }
        hero.addView(startBtn, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 20), 0, 0))
        styleButton(false)
        col.addView(hero, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 18), 0, 0))

        // results
        pingV = valueText()
        jitterV = valueText()
        downV = valueText()
        upV = valueText()
        val r1 = LinearLayout(ctx)
        r1.orientation = LinearLayout.HORIZONTAL
        val gap = UI.dp(ctx, 5)
        r1.addView(metric("PING", pingV, "ms"), UI.lp(0, UI.WRAP, 1f, 0, 0, gap, 0))
        r1.addView(metric("JITTER", jitterV, "ms"), UI.lp(0, UI.WRAP, 1f, gap, 0, 0, 0))
        col.addView(r1, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 10), 0, 0))
        val r2 = LinearLayout(ctx)
        r2.orientation = LinearLayout.HORIZONTAL
        r2.addView(metric("DOWNLOAD", downV, "Mbps"), UI.lp(0, UI.WRAP, 1f, 0, 0, gap, 0))
        r2.addView(metric("UPLOAD", upV, "Mbps"), UI.lp(0, UI.WRAP, 1f, gap, 0, 0, 0))
        col.addView(r2, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 10), 0, 0))

        // live latency
        val liveCard = LinearLayout(ctx)
        liveCard.orientation = LinearLayout.VERTICAL
        liveCard.background = UI.rounded(ctx, UI.CARD, 22f, UI.STROKE, 1f)
        liveCard.setPadding(UI.dp(ctx, 18), UI.dp(ctx, 16), UI.dp(ctx, 18), UI.dp(ctx, 14))

        val lh = LinearLayout(ctx)
        lh.orientation = LinearLayout.HORIZONTAL
        lh.gravity = Gravity.CENTER_VERTICAL
        val lt = UI.text(ctx, "LIVE LATENCY", 11f, UI.MUTED, UI.medium())
        lt.letterSpacing = 0.15f
        lh.addView(lt, UI.lp(0, UI.WRAP, 1f))
        liveQuality = UI.text(ctx, "Waiting…", 12f, UI.MUTED, UI.medium())
        lh.addView(liveQuality, UI.lp(UI.WRAP, UI.WRAP))
        liveCard.addView(lh, UI.lp(UI.MATCH, UI.WRAP))

        liveValue = UI.text(ctx, "— ms", 30f, UI.TEXT, UI.light())
        liveCard.addView(liveValue, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        liveCard.addView(chart, UI.lp(UI.MATCH, UI.dp(ctx, 110), 0f, 0, UI.dp(ctx, 8), 0, 0))
        val note = UI.text(ctx, "TCP latency to a public server · updates every second", 11f, UI.MUTED)
        liveCard.addView(note, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, 0))
        col.addView(liveCard, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 10), 0, 0))

        val foot = UI.text(
            ctx,
            "This app is never frozen, so these numbers always show your real connection. " +
                "Turn the freeze on and watch the latency drop.",
            12f, UI.MUTED
        )
        foot.gravity = Gravity.CENTER
        col.addView(foot, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 16), 0, 0))

        scroll.addView(col, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP))
        resetResults()
    }

    private fun valueText(): TextView = UI.text(ctx, "—", 24f, UI.TEXT, UI.medium())

    private fun metric(label: String, value: TextView, unit: String): View {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.background = UI.rounded(ctx, UI.CARD, 18f, UI.STROKE, 1f)
        card.setPadding(UI.dp(ctx, 16), UI.dp(ctx, 14), UI.dp(ctx, 16), UI.dp(ctx, 14))
        val l = UI.text(ctx, label, 11f, UI.MUTED, UI.medium())
        l.letterSpacing = 0.15f
        card.addView(l, UI.lp(UI.WRAP, UI.WRAP))
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.BOTTOM
        row.addView(value, UI.lp(UI.WRAP, UI.WRAP))
        val u = UI.text(ctx, unit, 12f, UI.MUTED)
        row.addView(u, UI.lp(UI.WRAP, UI.WRAP, 0f, UI.dp(ctx, 5), 0, 0, UI.dp(ctx, 3)))
        card.addView(row, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))
        return card
    }

    private fun styleButton(running: Boolean) {
        if (running) {
            startBtn.text = "STOP"
            startBtn.setTextColor(UI.BAD)
            startBtn.background = UI.rounded(ctx, UI.CARD_HI, 16f, UI.BAD, 1f)
        } else {
            startBtn.text = "START TEST"
            startBtn.setTextColor(Color.parseColor("#04121C"))
            startBtn.background = UI.pressable(ctx, UI.ACCENT, UI.ACCENT_DEEP, 16f)
        }
    }

    private fun resetResults() {
        pingV.text = "—"
        jitterV.text = "—"
        downV.text = "—"
        upV.text = "—"
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onShow() {
        visible = true
        UI.setIfChanged(connLabel, connectionLabel())
        if (!testing) live.start()
    }

    override fun onHide() {
        visible = false
        live.stop()
    }

    // ---------------------------------------------------------------- live latency

    private fun onLiveSample(ms: Double?) {
        chart.add(ms)
        if (ms == null) {
            liveValue.text = "— ms"
            liveQuality.text = "Timeout"
            liveQuality.setTextColor(UI.BAD)
        } else {
            liveValue.text = String.format(Locale.US, "%.0f ms", ms)
            val q = quality(ms)
            liveQuality.text = q.first
            liveQuality.setTextColor(q.second)
        }
    }

    private fun quality(ms: Double): Pair<String, Int> = when {
        ms < 50.0 -> Pair("Excellent", UI.GOOD)
        ms < 100.0 -> Pair("Good", UI.GOOD)
        ms < 200.0 -> Pair("Fair", UI.WARN)
        else -> Pair("Poor", UI.BAD)
    }

    // ---------------------------------------------------------------- speed test

    private fun stopTest() {
        token += 1
        test?.cancel()
        test = null
        testing = false
        styleButton(false)
        bar.value = 0f
        stageLabel.text = "CANCELLED"
        stageLabel.setTextColor(UI.MUTED)
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
        stageLabel.setTextColor(UI.MUTED)
        stageLabel.text = "STARTING"
        UI.setIfChanged(connLabel, connectionLabel())

        val t = SpeedTest(object : SpeedTest.Callback {
            override fun onStage(stage: Int) {
                main.post {
                    if (my != token) return@post
                    when (stage) {
                        0 -> {
                            stageLabel.text = "MEASURING LATENCY"
                            unitLabel.text = "ms"
                        }
                        1 -> {
                            stageLabel.text = "TESTING DOWNLOAD"
                            unitLabel.text = "Mbps"
                            bigValue.text = "0.0"
                        }
                        else -> {
                            stageLabel.text = "TESTING UPLOAD"
                            unitLabel.text = "Mbps"
                            bigValue.text = "0.0"
                        }
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
                    stageLabel.text = if (result.lossPercent > 0) {
                        "COMPLETE · " + result.lossPercent + "% PACKET LOSS"
                    } else {
                        "TEST COMPLETE"
                    }
                    finishTest()
                }
            }

            override fun onError(message: String) {
                main.post {
                    if (my != token) return@post
                    stageLabel.text = message.uppercase()
                    stageLabel.setTextColor(UI.BAD)
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
