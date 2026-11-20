package com.ankan.netfreeze

import android.animation.ObjectAnimator
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** PIN dots and a number pad. Calls [onFull] when all digits are entered. */
class PinPad(
    context: Context,
    private val len: Int,
    showBio: Boolean,
    private val onFull: (String) -> Unit,
    private val onBio: (() -> Unit)?
) : LinearLayout(context) {
    private val pin = StringBuilder()
    private val dotViews = ArrayList<View>()
    private val dots = LinearLayout(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        dots.orientation = HORIZONTAL
        for (i in 0 until len) {
            val d = View(context)
            d.background = UI.rounded(context, UI.DIM, 8f)
            dotViews.add(d)
            dots.addView(d, UI.lp(UI.dp(context, 14), UI.dp(context, 14), 0f, UI.dp(context, 8), 0, UI.dp(context, 8), 0))
        }
        addView(dots, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, 0, 0, UI.dp(context, 26)))
        val keys = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        for (r in 0 until 4) {
            val row = LinearLayout(context)
            row.orientation = HORIZONTAL
            for (c in 0 until 3) {
                row.addView(
                    keyView(keys[r * 3 + c], showBio),
                    UI.lp(UI.dp(context, 74), UI.dp(context, 74), 0f, UI.dp(context, 9), UI.dp(context, 7), UI.dp(context, 9), UI.dp(context, 7))
                )
            }
            addView(row)
        }
    }

    private fun keyView(k: String, showBio: Boolean): View {
        val f = FrameLayout(context)
        if (k == "bio" && !showBio) return f
        f.background = UI.rounded(context, UI.CARD2, 37f, UI.STROKE, 1f)
        f.isClickable = true
        when (k) {
            "del" -> f.addView(IconView(context, Ic.BACKSPACE), FrameLayout.LayoutParams(UI.dp(context, 28), UI.dp(context, 28), Gravity.CENTER))
            "bio" -> f.addView(IconView(context, Ic.KEY), FrameLayout.LayoutParams(UI.dp(context, 28), UI.dp(context, 28), Gravity.CENTER))
            else -> f.addView(UI.text(context, k, 28f, UI.TEXT, UI.medium()), FrameLayout.LayoutParams(UI.WRAP, UI.WRAP, Gravity.CENTER))
        }
        f.setOnClickListener {
            UI.tap(it)
            when (k) {
                "del" -> {
                    if (pin.isNotEmpty()) {
                        pin.deleteCharAt(pin.length - 1)
                        refresh()
                    }
                }
                "bio" -> {
                    if (onBio != null) onBio.invoke()
                }
                else -> {
                    if (pin.length < len) {
                        pin.append(k)
                        refresh()
                        if (pin.length == len) onFull(pin.toString())
                    }
                }
            }
        }
        UI.press(f, 0.88f)
        return f
    }

    private fun refresh() {
        for (i in dotViews.indices) {
            val on = i < pin.length
            dotViews[i].background = UI.rounded(context, if (on) UI.ACCENT else UI.DIM, 8f)
            if (on && i == pin.length - 1 && UI.anim) {
                dotViews[i].scaleX = 0.4f
                dotViews[i].scaleY = 0.4f
                dotViews[i].animate().scaleX(1f).scaleY(1f).setDuration(200).start()
            }
        }
    }

    fun clear() {
        pin.setLength(0)
        refresh()
    }

    /** Shake the dots (wrong PIN) and clear the entry. */
    fun shake() {
        if (UI.anim) {
            ObjectAnimator.ofFloat(dots, "translationX", 0f, 26f, -26f, 18f, -18f, 8f, 0f).setDuration(380).start()
        }
        dots.postDelayed({ clear() }, if (UI.anim) 380L else 0L)
    }
}

/** Create or change the App Lock PIN: confirm old PIN (if any), choose length, enter, confirm. */
class PinSetupScreen(ctx: Context, nav: Nav, private val onDone: () -> Unit) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "App Lock PIN", scroll = false)
    override val view: View get() = page.root
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val ring = RingView(ctx)
    private val titleTv = UI.text(ctx, "", 24f, UI.TEXT, UI.bold())
    private val subTv = UI.text(ctx, "", 14.5f, UI.MUTED)
    private val chipRow = LinearLayout(ctx)
    private val padHost = FrameLayout(ctx)
    private val chips = ArrayList<TextView>()
    private var step = 0
    private var len = 4
    private var first = ""
    private var started = false

    init {
        val col = page.content
        col.gravity = Gravity.CENTER_HORIZONTAL
        val rs = UI.dp(ctx, 104)
        ring.iconKind = Ic.KEY
        ring.progress = 1f
        col.addView(ring, UI.lp(rs, rs, 0f, 0, UI.dp(ctx, 4), 0, 0))
        titleTv.gravity = Gravity.CENTER
        col.addView(titleTv, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 20), 0, 0))
        subTv.gravity = Gravity.CENTER
        col.addView(subTv, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 30), UI.dp(ctx, 8), UI.dp(ctx, 30), 0))

        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.gravity = Gravity.CENTER
        for (n in 4..6) {
            val c = UI.smallPill(ctx, "$n digits")
            c.setOnClickListener {
                len = n
                styleChips()
                buildPad()
            }
            chips.add(c)
            chipRow.addView(c, UI.lp(UI.WRAP, UI.WRAP, 0f, UI.dp(ctx, 4), 0, UI.dp(ctx, 4), 0))
        }
        col.addView(chipRow, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 18), 0, 0))
        col.addView(padHost, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, 0))
        len = AppLockStore.length(ctx)
        styleChips()
    }

    private fun styleChips() {
        for (i in chips.indices) {
            val on = (i + 4) == len
            chips[i].setTextColor(if (on) UI.ON_ACCENT else UI.TEXT)
            chips[i].background = if (on) UI.rounded(ctx, UI.ACCENT, 20f) else UI.rounded(ctx, UI.CARD2, 20f, UI.STROKE, 1f)
        }
    }

    override fun onShow() {
        if (started) return
        started = true
        step = if (AppLockStore.hasPin(ctx)) 0 else 1
        showStep()
    }

    private fun showStep() {
        chipRow.visibility = if (step == 1) View.VISIBLE else View.GONE
        when (step) {
            0 -> {
                UI.swapText(titleTv, "Enter your current PIN")
                UI.swapText(subTv, "Confirm it is you before changing the PIN.")
            }
            1 -> {
                UI.swapText(titleTv, if (AppLockStore.hasPin(ctx)) "Choose a new PIN" else "Create your PIN")
                UI.swapText(subTv, "Pick a PIN you will remember. It protects every locked app.")
            }
            2 -> {
                UI.swapText(titleTv, "Confirm your PIN")
                UI.swapText(subTv, "Enter the same PIN once more.")
            }
            else -> {
                UI.swapText(titleTv, "PIN saved")
                UI.swapText(subTv, "Your locked apps are now protected.")
            }
        }
        if (step < 3) buildPad() else {
            padHost.removeAllViews()
            ring.check = true
            ring.animateTo(1f, 500)
        }
    }

    private fun buildPad() {
        padHost.removeAllViews()
        val n = if (step == 0) AppLockStore.length(ctx) else len
        val pad = PinPad(ctx, n, false, { entered -> onEntered(entered) }, null)
        padHost.addView(pad)
        if (UI.anim) {
            pad.alpha = 0f
            pad.translationY = UI.dpf(ctx, 24f)
            pad.animate().alpha(1f).translationY(0f).setDuration(320).start()
        }
        padRef = pad
    }

    private var padRef: PinPad? = null

    private fun onEntered(entered: String) {
        val pad = padRef
        when (step) {
            0 -> {
                if (AppLockStore.check(ctx, entered)) {
                    step = 1
                    main.postDelayed({ showStep() }, 180)
                } else {
                    UI.swapText(subTv, "Wrong PIN. Try again.")
                    pad?.shake()
                }
            }
            1 -> {
                first = entered
                step = 2
                main.postDelayed({ showStep() }, 180)
            }
            2 -> {
                if (entered == first) {
                    AppLockStore.setPin(ctx, first)
                    step = 3
                    main.postDelayed({ showStep() }, 180)
                    main.postDelayed({
                        nav.pop()
                        onDone()
                    }, 1300)
                } else {
                    UI.swapText(subTv, "PINs do not match. Start again.")
                    pad?.shake()
                    step = 1
                    main.postDelayed({ showStep() }, 900)
                }
            }
            else -> { }
        }
    }
}
