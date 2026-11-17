package com.ankan.netfreeze

import android.content.Context
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class HomeTab(private val ctx: Context, private val host: Host) : Tab {

    private val scroll = ScrollView(ctx)
    override val view: View get() = scroll

    private val button = PowerButtonView(ctx)
    private val title: TextView
    private val sub: TextView
    private val statApps: TextView
    private val statReq: TextView
    private val statTime: TextView

    init {
        scroll.setBackgroundColor(UI.BG)
        scroll.isVerticalScrollBarEnabled = false
        scroll.isFillViewport = true

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val pad = UI.dp(ctx, 20)
        col.setPadding(pad, UI.dp(ctx, 22), pad, UI.dp(ctx, 20))

        // header: wordmark + product name
        val wordmark = ImageView(ctx)
        wordmark.setImageResource(R.drawable.wordmark)
        wordmark.adjustViewBounds = true
        wordmark.scaleType = ImageView.ScaleType.FIT_CENTER
        col.addView(wordmark, UI.lp(UI.WRAP, UI.dp(ctx, 30)))

        val product = UI.text(ctx, "NETFREEZE", 12f, UI.ACCENT, UI.medium())
        product.letterSpacing = 0.5f
        product.gravity = Gravity.CENTER
        col.addView(product, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))

        // spacer pushes the button towards the middle
        col.addView(View(ctx), UI.lp(UI.MATCH, 0, 1f))

        val size = minOf(
            UI.dp(ctx, 270),
            (ctx.resources.displayMetrics.widthPixels * 0.74f).toInt()
        )
        button.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            host.toggleFreeze()
        }
        col.addView(button, UI.lp(size, size))

        title = UI.text(ctx, "TAP TO FREEZE", 22f, UI.TEXT, UI.medium())
        title.letterSpacing = 0.12f
        title.gravity = Gravity.CENTER
        col.addView(title, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 18), 0, 0))

        sub = UI.text(ctx, "", 14f, UI.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 8), 0, 0))

        col.addView(View(ctx), UI.lp(UI.MATCH, 0, 1f))

        // stats
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        statApps = UI.text(ctx, "—", 20f, UI.TEXT, UI.medium())
        statReq = UI.text(ctx, "—", 20f, UI.TEXT, UI.medium())
        statTime = UI.text(ctx, "—", 20f, UI.TEXT, UI.medium())
        val gap = UI.dp(ctx, 5)
        row.addView(statCard("Apps frozen", statApps), UI.lp(0, UI.WRAP, 1f, 0, 0, gap, 0))
        row.addView(statCard("Requests blocked", statReq), UI.lp(0, UI.WRAP, 1f, gap, 0, gap, 0))
        row.addView(statCard("Frozen for", statTime), UI.lp(0, UI.WRAP, 1f, gap, 0, 0, 0))
        col.addView(row, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 26), 0, 0))

        // info
        val info = UI.text(
            ctx,
            "Your hotspot and the apps you keep allowed get the full bandwidth.",
            12.5f, UI.MUTED
        )
        info.gravity = Gravity.CENTER
        col.addView(info, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 14), 0, 0))

        scroll.addView(col, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP))
        refresh()
    }

    private fun statCard(label: String, value: TextView): View {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.background = UI.rounded(ctx, UI.CARD, 16f, UI.STROKE, 1f)
        card.setPadding(UI.dp(ctx, 6), UI.dp(ctx, 14), UI.dp(ctx, 6), UI.dp(ctx, 14))
        value.gravity = Gravity.CENTER
        card.addView(value, UI.lp(UI.WRAP, UI.WRAP))
        val l = UI.text(ctx, label, 11f, UI.MUTED)
        l.gravity = Gravity.CENTER
        card.addView(l, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 6), 0, 0))
        return card
    }

    override fun onShow() {
        refresh()
    }

    fun refresh() {
        val on = NetBlockService.running
        button.setActive(on)
        val exempt = Prefs.exempt(ctx)
        val appsText = if (AppRepo.loaded) AppRepo.frozenCount(exempt).toString() else "…"

        if (on) {
            UI.setIfChanged(title, "FROZEN")
            title.setTextColor(UI.ACCENT)
            UI.setIfChanged(sub, "Background apps have no internet access")
            UI.setIfChanged(statApps, appsText)
            UI.setIfChanged(statReq, compact(NetBlockService.packets.get()))
            val secs = ((SystemClock.elapsedRealtime() - NetBlockService.startedAt) / 1000L).coerceAtLeast(0L)
            UI.setIfChanged(statTime, duration(secs))
        } else {
            UI.setIfChanged(title, "TAP TO FREEZE")
            title.setTextColor(UI.TEXT)
            UI.setIfChanged(sub, "Block internet for background apps with one tap")
            UI.setIfChanged(statApps, appsText)
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
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}
