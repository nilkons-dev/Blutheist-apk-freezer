package com.ankan.netfreeze

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Black & white design kit shared by every screen. */
object UI {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    val BG: Int = Color.BLACK
    val CARD: Int = Color.parseColor("#111113")
    val CARD2: Int = Color.parseColor("#1B1B1E")
    val STROKE: Int = Color.parseColor("#27272B")
    val TEXT: Int = Color.WHITE
    val MUTED: Int = Color.parseColor("#8E8E95")
    val DIM: Int = Color.parseColor("#34343A")

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dp(ctx: Context, v: Int): Int = dp(ctx, v.toFloat())
    fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun bold(): Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    fun medium(): Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    fun regular(): Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    fun rounded(ctx: Context, fill: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Float = 0f): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.setColor(fill)
        d.cornerRadius = dpf(ctx, radiusDp)
        if (strokeDp > 0f) d.setStroke(dp(ctx, strokeDp).coerceAtLeast(1), strokeColor)
        return d
    }

    /** Soft grey light from the top, fading to black. */
    fun glow(ctx: Context): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1D1D20"), Color.parseColor("#070708"), Color.BLACK)
        )
        g.gradientType = GradientDrawable.RADIAL_GRADIENT
        g.setGradientCenter(0.5f, 0.1f)
        g.gradientRadius = dpf(ctx, 460f)
        return g
    }

    fun text(ctx: Context, s: String, sp: Float, color: Int, face: Typeface = regular()): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        t.typeface = face
        t.includeFontPadding = false
        return t
    }

    fun lp(w: Int, h: Int, weight: Float = 0f, left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.setMargins(left, top, right, bottom)
        return p
    }

    fun setIfChanged(tv: TextView, s: String) {
        if (tv.text.toString() != s) tv.text = s
    }

    /** Rounded dark card (vertical). */
    fun card(ctx: Context): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(ctx, CARD, 26f, STROKE, 1f)
        return c
    }

    fun divider(ctx: Context): View {
        val v = View(ctx)
        v.setBackgroundColor(STROKE)
        return v
    }

    /** Big pill button. primary = white, otherwise dark. */
    fun pill(ctx: Context, label: String, primary: Boolean): TextView {
        val t = text(ctx, label, 17f, if (primary) Color.BLACK else TEXT, medium())
        t.gravity = Gravity.CENTER
        t.minimumHeight = dp(ctx, 56)
        t.setPadding(dp(ctx, 28), 0, dp(ctx, 28), 0)
        t.isClickable = true
        val sl = StateListDrawable()
        if (primary) {
            sl.addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, Color.parseColor("#C8C8CC"), 30f))
            sl.addState(intArrayOf(), rounded(ctx, Color.WHITE, 30f))
        } else {
            sl.addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, Color.parseColor("#2A2A2E"), 30f, STROKE, 1f))
            sl.addState(intArrayOf(), rounded(ctx, CARD2, 30f, STROKE, 1f))
        }
        t.background = sl
        return t
    }

    fun smallPill(ctx: Context, label: String): TextView {
        val t = text(ctx, label, 14f, TEXT, medium())
        t.gravity = Gravity.CENTER
        t.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        t.isClickable = true
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, Color.parseColor("#2A2A2E"), 20f, STROKE, 1f))
        sl.addState(intArrayOf(), rounded(ctx, CARD2, 20f, STROKE, 1f))
        t.background = sl
        return t
    }

    fun iconBox(ctx: Context, icon: Int, sizeDp: Int = 44, radiusDp: Float = 14f): FrameLayout {
        val f = FrameLayout(ctx)
        f.background = rounded(ctx, CARD2, radiusDp, STROKE, 1f)
        val iv = IconView(ctx, icon)
        val s = dp(ctx, sizeDp * 0.54f)
        f.addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        return f
    }

    /** Settings-style row: icon box, title, optional value, chevron. */
    class RowH(val root: LinearLayout, val title: TextView, val value: TextView, val box: FrameLayout?)

    fun row(ctx: Context, icon: Int, title: String, value: String, chevron: Boolean, onClick: (() -> Unit)?): RowH {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
        var box: FrameLayout? = null
        if (icon >= 0) {
            box = iconBox(ctx, icon)
            r.addView(box, lp(dp(ctx, 44), dp(ctx, 44), 0f, 0, 0, dp(ctx, 14), 0))
        }
        val t = text(ctx, title, 17f, TEXT, medium())
        r.addView(t, lp(0, WRAP, 1f))
        val v = text(ctx, value, 14f, MUTED)
        v.gravity = Gravity.END
        r.addView(v, lp(WRAP, WRAP, 0f, dp(ctx, 8), 0, 0, 0))
        if (chevron) {
            val c = IconView(ctx, Ic.CHEVRON)
            c.tint = MUTED
            r.addView(c, lp(dp(ctx, 18), dp(ctx, 18), 0f, dp(ctx, 6), 0, 0, 0))
        }
        if (onClick != null) {
            r.isClickable = true
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, Color.parseColor("#1A1A1D"), 20f))
            r.background = sl
            r.setOnClickListener { onClick() }
        }
        return RowH(r, t, v, box)
    }

    fun sectionLabel(ctx: Context, s: String): TextView {
        val t = text(ctx, s, 15f, MUTED)
        t.setPadding(dp(ctx, 6), 0, 0, 0)
        return t
    }
}
