package com.ankan.netfreeze

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Colours, sizes and small builders shared by every screen. */
object UI {
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    val BG: Int = Color.parseColor("#060B14")
    val SURFACE: Int = Color.parseColor("#0A1220")
    val CARD: Int = Color.parseColor("#0F1A2D")
    val CARD_HI: Int = Color.parseColor("#14223A")
    val STROKE: Int = Color.parseColor("#1A2A44")
    val TEXT: Int = Color.parseColor("#EAF2FF")
    val MUTED: Int = Color.parseColor("#8496B2")
    val ACCENT: Int = Color.parseColor("#35D4FF")
    val ACCENT_DEEP: Int = Color.parseColor("#1FA8D6")
    val GOOD: Int = Color.parseColor("#3DDC97")
    val WARN: Int = Color.parseColor("#FFC857")
    val BAD: Int = Color.parseColor("#FF6B7A")

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dp(ctx: Context, v: Int): Int = dp(ctx, v.toFloat())
    fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun medium(): Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    fun light(): Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    fun regular(): Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    fun rounded(
        ctx: Context,
        fill: Int,
        radiusDp: Float,
        strokeColor: Int = 0,
        strokeDp: Float = 0f
    ): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.setColor(fill)
        d.cornerRadius = dpf(ctx, radiusDp)
        if (strokeDp > 0f) {
            d.setStroke(dp(ctx, strokeDp).coerceAtLeast(1), strokeColor)
        }
        return d
    }

    fun pressable(ctx: Context, normal: Int, pressed: Int, radiusDp: Float): StateListDrawable {
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, pressed, radiusDp))
        sl.addState(intArrayOf(), rounded(ctx, normal, radiusDp))
        return sl
    }

    fun selectable(ctx: Context): Drawable? {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            ctx.getDrawable(tv.resourceId)
        } else {
            null
        }
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

    fun lp(
        w: Int,
        h: Int,
        weight: Float = 0f,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.setMargins(left, top, right, bottom)
        return p
    }

    fun setIfChanged(tv: TextView, s: String) {
        if (tv.text.toString() != s) tv.text = s
    }
}
