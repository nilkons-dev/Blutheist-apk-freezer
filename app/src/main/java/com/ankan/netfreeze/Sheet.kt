package com.ankan.netfreeze

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView

/** Bottom sheets in the app's own style: choices, confirmations and action lists. */
object Sheet {

    private class MaxScroll(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }
    }

    private class Parts(val dialog: Dialog, val box: LinearLayout)

    private fun create(ctx: Context, title: String): Parts {
        val d = Dialog(ctx)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val r = UI.dpf(ctx, 30f)
        val bg = GradientDrawable()
        bg.setColor(UI.blend(UI.BG, Color.WHITE, 0.075f))
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        bg.setStroke(UI.dp(ctx, 1), UI.STROKE)
        box.background = bg
        box.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 12), UI.dp(ctx, 20), UI.dp(ctx, 22))
        val handle = View(ctx)
        handle.background = UI.rounded(ctx, UI.DIM, 3f)
        val hp = UI.lp(UI.dp(ctx, 42), UI.dp(ctx, 5))
        hp.gravity = Gravity.CENTER_HORIZONTAL
        box.addView(handle, hp)
        box.addView(UI.text(ctx, title, 20f, UI.TEXT, UI.bold()), UI.lp(UI.WRAP, UI.WRAP, 0f, UI.dp(ctx, 4), UI.dp(ctx, 18), 0, UI.dp(ctx, 6)))
        d.setContentView(box)
        d.setCanceledOnTouchOutside(true)
        val w = d.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setGravity(Gravity.BOTTOM)
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.66f)
        }
        return Parts(d, box)
    }

    private fun open(p: Parts) {
        val ctx = p.box.context
        p.box.translationY = UI.dpf(ctx, 640f)
        p.dialog.show()
        p.box.animate().translationY(0f).setDuration(UI.dur(360)).setInterpolator(DecelerateInterpolator(1.9f)).start()
        // the rows slide in one after another
        for (i in 2 until p.box.childCount) {
            val c = p.box.getChildAt(i)
            if (!UI.anim) break
            c.alpha = 0f
            c.animate().alpha(1f).setStartDelay(120L + (i - 2) * 35L).setDuration(260).start()
        }
    }

    private fun close(p: Parts, after: (() -> Unit)?) {
        val ctx = p.box.context
        p.box.animate().translationY(UI.dpf(ctx, 640f)).setDuration(UI.dur(240)).withEndAction {
            try {
                p.dialog.dismiss()
            } catch (e: Exception) {
                // already closed
            }
            if (after != null) after()
        }.start()
    }

    private fun optionRow(ctx: Context, label: String, swatch: Int?, selected: Boolean, chevron: Boolean): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(UI.dp(ctx, 16), UI.dp(ctx, 15), UI.dp(ctx, 16), UI.dp(ctx, 15))
        row.isClickable = true
        row.background = if (selected) UI.rounded(ctx, UI.CARD2, 20f, UI.STROKE, 1f) else UI.rounded(ctx, Color.TRANSPARENT, 20f)
        if (swatch != null) {
            val sw = View(ctx)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(swatch)
            g.setStroke(UI.dp(ctx, 1), Color.parseColor("#55FFFFFF"))
            sw.background = g
            row.addView(sw, UI.lp(UI.dp(ctx, 28), UI.dp(ctx, 28), 0f, 0, 0, UI.dp(ctx, 16), 0))
        }
        val t = UI.text(ctx, label, 17f, UI.TEXT, if (selected) UI.bold() else UI.medium())
        row.addView(t, UI.lp(0, UI.WRAP, 1f))
        if (selected) {
            val c = IconView(ctx, Ic.CHECK)
            c.tint = UI.ACCENT
            row.addView(c, UI.lp(UI.dp(ctx, 24), UI.dp(ctx, 24)))
        } else if (chevron) {
            val c = IconView(ctx, Ic.CHEVRON)
            c.tint = UI.MUTED
            row.addView(c, UI.lp(UI.dp(ctx, 20), UI.dp(ctx, 20)))
        }
        UI.press(row, 0.98f)
        return row
    }

    /** One choice out of several, with an optional colour swatch for each. */
    fun choose(ctx: Context, title: String, options: Array<String>, selected: Int, swatches: IntArray?, onPick: (Int) -> Unit) {
        val p = create(ctx, title)
        for (i in options.indices) {
            val row = optionRow(ctx, options[i], if (swatches != null && i < swatches.size) swatches[i] else null, i == selected, false)
            row.setOnClickListener {
                UI.tap(it)
                close(p) { if (i != selected) onPick(i) }
            }
            p.box.addView(row, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 6)))
        }
        val cancel = UI.pill(ctx, "Cancel", false)
        cancel.setOnClickListener { close(p, null) }
        p.box.addView(cancel, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 10), 0, 0))
        open(p)
    }

    /** A yes / no question. */
    fun confirm(ctx: Context, title: String, message: String, positive: String, onYes: () -> Unit) {
        val p = create(ctx, title)
        p.box.addView(UI.text(ctx, message, 15f, UI.MUTED), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 22)))
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val no = UI.pill(ctx, "Cancel", false)
        no.setOnClickListener { close(p, null) }
        val yes = UI.pill(ctx, positive, true)
        yes.setOnClickListener {
            UI.tap(it)
            close(p) { onYes() }
        }
        row.addView(no, UI.lp(0, UI.WRAP, 1f, 0, 0, UI.dp(ctx, 5), 0))
        row.addView(yes, UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 5), 0, 0, 0))
        p.box.addView(row)
        open(p)
    }

    /** A message with one OK button. */
    fun info(ctx: Context, title: String, message: String) {
        val p = create(ctx, title)
        p.box.addView(UI.text(ctx, message, 15f, UI.MUTED), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 22)))
        val ok = UI.pill(ctx, "OK", true)
        ok.setOnClickListener { close(p, null) }
        p.box.addView(ok, UI.lp(UI.MATCH, UI.WRAP))
        open(p)
    }

    /** A list of actions (or items) the user can tap. */
    fun actions(ctx: Context, title: String, message: String?, labels: List<String>, onPick: (Int) -> Unit) {
        val p = create(ctx, title)
        if (message != null && message.isNotEmpty()) {
            p.box.addView(UI.text(ctx, message, 14f, UI.MUTED), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 14)))
        }
        val maxH = (ctx.resources.displayMetrics.heightPixels * 0.5f).toInt()
        val scroll = MaxScroll(ctx, maxH)
        scroll.isVerticalScrollBarEnabled = false
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        for (i in labels.indices) {
            val row = optionRow(ctx, labels[i], null, false, true)
            row.setOnClickListener {
                UI.tap(it)
                close(p) { onPick(i) }
            }
            list.addView(row, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, 0, 0, UI.dp(ctx, 4)))
        }
        scroll.addView(list)
        p.box.addView(scroll, UI.lp(UI.MATCH, UI.WRAP))
        val cancel = UI.pill(ctx, "Close", false)
        cancel.setOnClickListener { close(p, null) }
        p.box.addView(cancel, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 12), 0, 0))
        open(p)
    }
}
