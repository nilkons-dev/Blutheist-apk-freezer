package com.ankan.netfreeze

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

interface Nav {
    fun push(s: Screen)
    fun pop()
    fun toast(msg: String)
    fun toggleFreeze()
    fun exemptChanged()
    fun openAllFiles()
    fun openUsage()
    fun requestStorage()
    fun launch(i: Intent)
    fun openTab(i: Int)
    fun restartUi()
}

abstract class Screen(val ctx: Context, val nav: Nav) {
    abstract val view: View
    open fun onShow() {}
    open fun onHide() {}
}

/** Standard page: glow background, back arrow + title, scrolling content and a floating bottom button. */
class Page(
    private val ctx: Context,
    nav: Nav,
    title: String,
    scroll: Boolean = true,
    back: Boolean = true,
    menu: (() -> Unit)? = null
) {
    val root = FrameLayout(ctx)
    val content = LinearLayout(ctx)
    val titleView: TextView
    private val floating = FrameLayout(ctx)

    init {
        root.background = UI.glow(ctx)
        content.orientation = LinearLayout.VERTICAL
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(UI.dp(ctx, 10), 0, UI.dp(ctx, 10), 0)
        if (back) {
            val b = FrameLayout(ctx)
            b.isClickable = true
            b.setOnClickListener { nav.pop() }
            b.addView(IconView(ctx, Ic.BACK), FrameLayout.LayoutParams(UI.dp(ctx, 26), UI.dp(ctx, 26), Gravity.CENTER))
            head.addView(b, UI.lp(UI.dp(ctx, 48), UI.dp(ctx, 48)))
        } else {
            head.addView(View(ctx), UI.lp(UI.dp(ctx, 10), 1))
        }
        titleView = UI.text(ctx, title, 22f, UI.TEXT, UI.medium())
        head.addView(titleView, UI.lp(0, UI.WRAP, 1f, UI.dp(ctx, 6), 0, 0, 0))
        if (menu != null) {
            val m = FrameLayout(ctx)
            m.isClickable = true
            m.setOnClickListener { menu() }
            m.addView(IconView(ctx, Ic.MORE), FrameLayout.LayoutParams(UI.dp(ctx, 26), UI.dp(ctx, 26), Gravity.CENTER))
            head.addView(m, UI.lp(UI.dp(ctx, 48), UI.dp(ctx, 48)))
        }
        col.addView(head, UI.lp(UI.MATCH, UI.dp(ctx, 64), 0f, 0, UI.dp(ctx, 4), 0, 0))

        if (scroll) {
            val sv = ScrollView(ctx)
            sv.isVerticalScrollBarEnabled = false
            sv.overScrollMode = View.OVER_SCROLL_NEVER
            content.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 8), UI.dp(ctx, 20), UI.dp(ctx, 130))
            sv.addView(content, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP))
            col.addView(sv, UI.lp(UI.MATCH, 0, 1f))
        } else {
            col.addView(content, UI.lp(UI.MATCH, 0, 1f))
        }
        root.addView(col, FrameLayout.LayoutParams(UI.MATCH, UI.MATCH))
        root.addView(floating, FrameLayout.LayoutParams(UI.MATCH, UI.WRAP, Gravity.BOTTOM))
    }

    /** Puts a pill button at the bottom centre (null removes it). */
    fun setBottom(btn: View?) {
        floating.removeAllViews()
        if (btn != null) {
            val w = minOf(UI.dp(ctx, 300), ctx.resources.displayMetrics.widthPixels - UI.dp(ctx, 56))
            val p = FrameLayout.LayoutParams(w, UI.WRAP, Gravity.CENTER_HORIZONTAL)
            p.bottomMargin = UI.dp(ctx, 28)
            floating.addView(btn, p)
        }
    }

    fun add(v: View, lp: LinearLayout.LayoutParams = UI.lp(UI.MATCH, UI.WRAP)) {
        content.addView(v, lp)
    }
}
