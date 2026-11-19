package com.ankan.netfreeze

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Design kit: theme palette, accent, animation helpers and small builders. */
object UI {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    var BG: Int = Color.BLACK
    var CARD: Int = Color.parseColor("#101012")
    var CARD2: Int = Color.parseColor("#1B1B1E")
    var STROKE: Int = Color.parseColor("#27272B")
    var TEXT: Int = Color.WHITE
    var MUTED: Int = Color.parseColor("#8E8E95")
    var DIM: Int = Color.parseColor("#34343A")
    var ACCENT: Int = Color.WHITE
    var ON_ACCENT: Int = Color.BLACK
    var anim: Boolean = true
    var haptics: Boolean = true

    val THEME_NAMES = arrayOf("AMOLED black", "Graphite", "Midnight")
    val ACCENT_NAMES = arrayOf("White", "Ice blue", "Mint", "Amber", "Rose", "Violet")
    val ACCENTS = intArrayOf(
        Color.WHITE, Color.parseColor("#7CC4FF"), Color.parseColor("#5EE3B0"),
        Color.parseColor("#FFC25A"), Color.parseColor("#FF7A95"), Color.parseColor("#B49CFF")
    )

    fun loadTheme(ctx: Context) {
        when (Prefs.getInt(ctx, "theme", 0)) {
            1 -> {
                BG = Color.parseColor("#131315"); CARD = Color.parseColor("#1C1C1F"); CARD2 = Color.parseColor("#28282C")
                STROKE = Color.parseColor("#36363B"); MUTED = Color.parseColor("#9C9CA4"); DIM = Color.parseColor("#44444A")
            }
            2 -> {
                BG = Color.parseColor("#05080F"); CARD = Color.parseColor("#0C1220"); CARD2 = Color.parseColor("#151E33")
                STROKE = Color.parseColor("#202C47"); MUTED = Color.parseColor("#8794B0"); DIM = Color.parseColor("#2B3855")
            }
            else -> {
                BG = Color.BLACK; CARD = Color.parseColor("#101012"); CARD2 = Color.parseColor("#1B1B1E")
                STROKE = Color.parseColor("#27272B"); MUTED = Color.parseColor("#8E8E95"); DIM = Color.parseColor("#34343A")
            }
        }
        val a = Prefs.getInt(ctx, "accent", 0).coerceIn(0, ACCENTS.size - 1)
        ACCENT = ACCENTS[a]
        anim = Prefs.getBool(ctx, "animations", true)
        haptics = Prefs.getBool(ctx, "haptics", true)
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * (1f - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1f - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    fun discColor(): Int = if (ACCENT == Color.WHITE) Color.parseColor("#EFEFEF") else blend(ACCENT, Color.WHITE, 0.75f)

    fun dur(ms: Long): Long = if (anim) ms else 0L

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

    /** Soft light from the top, fading into the background. */
    fun glow(ctx: Context): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(blend(BG, Color.WHITE, 0.10f), blend(BG, Color.WHITE, 0.025f), BG)
        )
        g.gradientType = GradientDrawable.RADIAL_GRADIENT
        g.setGradientCenter(0.5f, 0.08f)
        g.gradientRadius = dpf(ctx, 480f)
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

    /** Smooth text swap: fade out, change, fade in. */
    fun swapText(tv: TextView, s: String) {
        if (tv.text.toString() == s) return
        if (!anim) {
            tv.text = s
            return
        }
        tv.animate().cancel()
        tv.animate().alpha(0f).setDuration(110).withEndAction {
            tv.text = s
            tv.animate().alpha(1f).setDuration(200).start()
        }.start()
    }

    /** Press feedback: the view shrinks slightly while touched. Does not block clicks. */
    fun press(v: View, scale: Float = 0.96f) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(scale).scaleY(scale).setDuration(dur(110)).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(dur(180)).start()
                else -> { }
            }
            false
        }
    }

    fun tap(v: View) {
        if (haptics) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Children of the first scroll area slide up and fade in one after another. */
    fun stagger(root: View) {
        if (!anim) return
        val list = scrollContent(root) ?: return
        for (i in 0 until list.childCount) {
            val c = list.getChildAt(i)
            c.alpha = 0f
            c.translationY = 28f * c.resources.displayMetrics.density
            c.animate().alpha(1f).translationY(0f).setStartDelay(i * 55L).setDuration(420)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }

    private fun scrollContent(v: View): ViewGroup? {
        if (v is ScrollView) return v.getChildAt(0) as? ViewGroup
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val r = scrollContent(v.getChildAt(i))
                if (r != null) return r
            }
        }
        return null
    }

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

    fun pill(ctx: Context, label: String, primary: Boolean): TextView {
        val t = text(ctx, label, 17f, if (primary) ON_ACCENT else TEXT, medium())
        t.gravity = Gravity.CENTER
        t.minimumHeight = dp(ctx, 56)
        t.setPadding(dp(ctx, 28), 0, dp(ctx, 28), 0)
        t.isClickable = true
        t.background = pillBg(ctx, primary)
        press(t)
        return t
    }

    fun pillBg(ctx: Context, primary: Boolean): StateListDrawable {
        val sl = StateListDrawable()
        if (primary) {
            sl.addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, blend(ACCENT, Color.BLACK, 0.22f), 30f))
            sl.addState(intArrayOf(), rounded(ctx, ACCENT, 30f))
        } else {
            sl.addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, blend(CARD2, Color.WHITE, 0.08f), 30f, STROKE, 1f))
            sl.addState(intArrayOf(), rounded(ctx, CARD2, 30f, STROKE, 1f))
        }
        return sl
    }

    fun smallPill(ctx: Context, label: String): TextView {
        val t = text(ctx, label, 14f, TEXT, medium())
        t.gravity = Gravity.CENTER
        t.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8))
        t.isClickable = true
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, blend(CARD2, Color.WHITE, 0.08f), 20f, STROKE, 1f))
        sl.addState(intArrayOf(), rounded(ctx, CARD2, 20f, STROKE, 1f))
        t.background = sl
        press(t)
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
            sl.addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, blend(CARD, Color.WHITE, 0.07f), 20f))
            r.background = sl
            r.setOnClickListener {
                tap(it)
                onClick()
            }
            press(r, 0.985f)
        }
        return RowH(r, t, v, box)
    }

    fun sectionLabel(ctx: Context, s: String): TextView {
        val t = text(ctx, s, 15f, MUTED)
        t.setPadding(dp(ctx, 6), 0, 0, 0)
        return t
    }
}
