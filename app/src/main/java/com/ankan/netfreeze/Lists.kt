package com.ankan.netfreeze

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** One list row: leading icon/app icon, title, subtitle, right text, optional check box or switch. */
class ItemRow(ctx: Context) : LinearLayout(ctx) {
    val lead = FrameLayout(ctx)
    val glyph = IconView(ctx, Ic.FILE)
    val img = ImageView(ctx)
    val title = UI.text(ctx, "", 16.5f, UI.TEXT, UI.medium())
    val sub = UI.text(ctx, "", 13f, UI.MUTED)
    val rtext = UI.text(ctx, "", 14f, UI.MUTED)
    val check = CheckView(ctx)
    val pill = PillSwitch(ctx)
    var boundKey: String? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(UI.dp(ctx, 16), UI.dp(ctx, 10), UI.dp(ctx, 16), UI.dp(ctx, 10))
        minimumHeight = UI.dp(ctx, 68)
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        lead.background = UI.rounded(ctx, UI.CARD2, 14f, UI.STROKE, 1f)
        val g = UI.dp(ctx, 24)
        lead.addView(glyph, FrameLayout.LayoutParams(g, g, Gravity.CENTER))
        img.scaleType = ImageView.ScaleType.FIT_XY
        img.visibility = View.GONE
        img.clipToOutline = true
        val radius = UI.dpf(ctx, 13f)
        img.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        lead.addView(img, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        addView(lead, UI.lp(UI.dp(ctx, 46), UI.dp(ctx, 46)))

        val col = LinearLayout(ctx)
        col.orientation = VERTICAL
        title.isSingleLine = true
        title.ellipsize = TextUtils.TruncateAt.END
        sub.isSingleLine = true
        sub.ellipsize = TextUtils.TruncateAt.MIDDLE
        col.addView(title)
        col.addView(sub, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 3), 0, 0))
        addView(col, UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 14), 0, UI.dp(ctx, 10), 0))

        rtext.visibility = View.GONE
        addView(rtext, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, 0, UI.dp(ctx, 8), 0))
        check.visibility = View.GONE
        addView(check, UI.lp(UI.WRAP, UI.WRAP))
        pill.visibility = View.GONE
        addView(pill, UI.lp(UI.WRAP, UI.WRAP))
    }

    fun showIcon(kind: Int) {
        img.visibility = View.GONE
        glyph.visibility = View.VISIBLE
        glyph.setKind(kind)
    }

    fun showBitmap(b: android.graphics.Bitmap) {
        glyph.visibility = View.GONE
        img.visibility = View.VISIBLE
        img.setImageBitmap(b)
    }
}

class ItemAdapter(
    private val ctx: Context,
    private val count: () -> Int,
    private val bind: (ItemRow, Int) -> Unit
) : BaseAdapter() {
    override fun getCount(): Int = count()
    override fun getItem(position: Int): Any = position
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = if (convertView is ItemRow) convertView else ItemRow(ctx)
        bind(row, position)
        return row
    }
}

object Lists {
    /** A ListView styled as a rounded dark card. */
    fun cardList(ctx: Context): ListView {
        val lv = ListView(ctx)
        lv.background = UI.rounded(ctx, UI.CARD, 26f, UI.STROKE, 1f)
        lv.clipToOutline = true
        lv.outlineProvider = ViewOutlineProvider.BACKGROUND
        lv.divider = ColorDrawable(UI.STROKE)
        lv.dividerHeight = 1
        lv.selector = ColorDrawable(Color.parseColor("#14FFFFFF"))
        lv.overScrollMode = View.OVER_SCROLL_NEVER
        lv.isVerticalScrollBarEnabled = false
        lv.setHeaderDividersEnabled(false)
        lv.setFooterDividersEnabled(false)
        return lv
    }

    fun emptyText(ctx: Context, s: String): TextView {
        val t = UI.text(ctx, s, 15f, UI.MUTED)
        t.gravity = Gravity.CENTER
        t.visibility = View.GONE
        return t
    }
}
