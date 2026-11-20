package com.ankan.netfreeze

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.max
import kotlin.math.min

/** Tiny SVG-like path parser (absolute commands) so icons can be written as strings. */
object SvgPath {
    private val re = Regex("[A-Za-z]|-?\\d*\\.?\\d+")

    fun parse(d: String): Path {
        val p = Path()
        val t = re.findAll(d).map { it.value }.toList()
        var i = 0
        var cx = 0f
        var cy = 0f
        fun n(): Float {
            val v = t[i].toFloat()
            i++
            return v
        }
        while (i < t.size) {
            val c = t[i]
            i++
            when (c) {
                "M" -> { cx = n(); cy = n(); p.moveTo(cx, cy) }
                "L" -> { cx = n(); cy = n(); p.lineTo(cx, cy) }
                "H" -> { cx = n(); p.lineTo(cx, cy) }
                "V" -> { cy = n(); p.lineTo(cx, cy) }
                "C" -> {
                    val a = n(); val b = n(); val c2 = n(); val d2 = n()
                    cx = n(); cy = n()
                    p.cubicTo(a, b, c2, d2, cx, cy)
                }
                "Z" -> p.close()
                "O" -> {
                    val x = n(); val y = n(); val r = n()
                    p.addCircle(x, y, r, Path.Direction.CW)
                }
                "R" -> {
                    val x = n(); val y = n(); val w = n(); val h = n(); val r = n()
                    p.addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW)
                }
                else -> { }
            }
        }
        return p
    }
}

object Ic {
    const val SPARKLE = 0
    const val SHIELD_ALERT = 1
    const val BOLT = 2
    const val GRID = 3
    const val DROP = 4
    const val LOCK = 5
    const val PHONE = 6
    const val BATTERY = 7
    const val GAUGE = 8
    const val SNOW = 9
    const val TRASH = 10
    const val BOX = 11
    const val DOWNLOAD = 12
    const val EYE = 13
    const val CHECK = 14
    const val BACK = 15
    const val CHEVRON = 16
    const val MORE = 17
    const val WARN = 18
    const val CHAT = 19
    const val CAMERA = 20
    const val MIC = 21
    const val PIN = 22
    const val USER = 23
    const val IMAGE = 24
    const val VIDEO = 25
    const val FILE = 26
    const val CLOCK = 27
    const val CPU = 28
    const val SEARCH = 29
    const val SHIELD = 30
    const val UPDOWN = 31
    const val MUSIC = 32
    const val ARCHIVE = 33
    const val INFO = 34
    const val SEND = 35
    const val FOLDER = 36
    const val SLIDERS = 37
    const val CLIPBOARD = 38
    const val COPY = 39
    const val PIE = 40
    const val HOURGLASS = 41
    const val KEY = 42
    const val SUN = 43
    const val BACKSPACE = 44
    const val WIFI = 45
    const val BLUETOOTH = 46
    const val VOLUME = 47
    const val CODE = 48
    const val HOME = 49

    val PATHS = arrayOf(
        "M11 4 C11.6 9 13 10.6 18 11.5 C13 12.4 11.6 14 11 19 C10.4 14 9 12.4 4 11.5 C9 10.6 10.4 9 11 4 Z M19 3 V7 M17 5 H21",
        "M12 3 L19 6 V11 C19 16 16 19.5 12 21 C8 19.5 5 16 5 11 V6 Z M12 8.5 V13 M12 15.8 V15.9",
        "M13 3 L5 13.5 H11 L10 21 L19 10 H13 Z",
        "R4 4 7 7 2 R13 4 7 7 2 R4 13 7 7 2 R13 13 7 7 2",
        "M12 3 C9 7 5.5 10.5 5.5 14.5 C5.5 18.1 8.4 21 12 21 C15.6 21 18.5 18.1 18.5 14.5 C18.5 10.5 15 7 12 3 Z",
        "R5 11 14 10 3 M8 11 V8 C8 5.8 9.8 4 12 4 C14.2 4 16 5.8 16 8 V11",
        "R7 2.5 10 19 2.5 M11 18.5 H13",
        "R3 8 16 9 2 M21 11 V14 M7 11 V14",
        "M4 17 C4 11 7.6 6.5 12 6.5 C16.4 6.5 20 11 20 17 M12 17 L16 11 M4 17 H6 M18 17 H20",
        "M12 3 V21 M4.2 7.5 L19.8 16.5 M4.2 16.5 L19.8 7.5",
        "M5 7 H19 M9 7 V4.5 H15 V7 M7 7 L8 20 H16 L17 7 M10 11 V16 M14 11 V16",
        "M12 3 L20 7.5 V16.5 L12 21 L4 16.5 V7.5 Z M4 7.5 L12 12 L20 7.5 M12 12 V21",
        "M5 8 L6 4.5 H18 L19 8 M5 8 H19 V19 H5 Z M12 11 V16 M9.8 13.8 L12 16 L14.2 13.8",
        "M2.5 12 C5 7.5 8.3 5.5 12 5.5 C15.7 5.5 19 7.5 21.5 12 C19 16.5 15.7 18.5 12 18.5 C8.3 18.5 5 16.5 2.5 12 Z O12 12 2.8",
        "M5 12.5 L10 17.5 L19 7",
        "M20 12 H4 M10 6 L4 12 L10 18",
        "M9 6 L15 12 L9 18",
        "O12 5 1.3 O12 12 1.3 O12 19 1.3",
        "M12 4 L21 19.5 H3 Z M12 10 V14.5 M12 17 V17.1",
        "M5 5 H19 V16 H11 L7 20 V16 H5 Z",
        "R3 7 18 13 3 O12 13.5 3.5 M8.5 7 L9.5 4.5 H14.5 L15.5 7",
        "R9 3 6 11 3 M6 11 C6 14.5 8.7 17 12 17 C15.3 17 18 14.5 18 11 M12 17 V21",
        "M12 21 C12 21 5 14.5 5 10 C5 6.1 8.1 3 12 3 C15.9 3 19 6.1 19 10 C19 14.5 12 21 12 21 Z O12 10 2.5",
        "O12 8 3.5 M5 20 C5 15.5 8 13.5 12 13.5 C16 13.5 19 15.5 19 20",
        "R4 5 16 14 3 O9 10 1.6 M4 17 L9 13 L13 16 L16 13.5 L20 17",
        "R3 6 13 12 3 M16 10.5 L21 7.5 V16.5 L16 13.5",
        "M6 3 H14 L19 8 V21 H6 Z M14 3 V8 H19",
        "O12 12 8.5 M12 7 V12 L15.5 14",
        "R7 7 10 10 2 M10 3 V7 M14 3 V7 M10 17 V21 M14 17 V21 M3 10 H7 M3 14 H7 M17 10 H21 M17 14 H21",
        "O11 11 6.5 M16 16 L20.5 20.5",
        "M12 3 L19 6 V11 C19 16 16 19.5 12 21 C8 19.5 5 16 5 11 V6 Z M8.5 11.5 C9.8 9.8 10.9 9 12 9 C13.1 9 14.2 9.8 15.5 11.5 C14.2 13.2 13.1 14 12 14 C10.9 14 9.8 13.2 8.5 11.5 Z",
        "M8 9 L12 5 L16 9 M8 15 L12 19 L16 15",
        "M9 18 V6 L19 4 V16 O7 18 2 O17 16 2",
        "R4 4 16 5 1.5 M5 9 V19 H19 V9 M10 13 H14",
        "O12 12 9 M12 11 V16.5 M12 7.7 V7.8",
        "M21 4 L3 11 L10 13 L12 20 Z M10 13 L21 4",
        "M3 7 C3 5.9 3.9 5 5 5 H9.5 L11.5 7.5 H19 C20.1 7.5 21 8.4 21 9.5 V17 C21 18.1 20.1 19 19 19 H5 C3.9 19 3 18.1 3 17 Z",
        "M4 7 H14 M18 7 H20 M4 17 H6 M10 17 H20 O16 7 2 O8 17 2",
        "R5 4.5 14 16.5 2.5 M9 4.5 V3 H15 V4.5 M8.5 10 H15.5 M8.5 14 H13",
        "R8 8 12 12 2 M16 8 V6 C16 5.4 15.6 5 15 5 H6 C5.4 5 5 5.4 5 6 V15 C5 15.6 5.4 16 6 16 H8",
        "M12 3 V12 H21 M12 3 C7 3 3 7 3 12 C3 17 7 21 12 21 C17 21 21 17 21 12",
        "M6 3 H18 M6 21 H18 M7 3 C7 8 11 10 12 12 C13 14 17 16 17 21 M17 3 C17 8 13 10 12 12 C11 14 7 16 7 21",
        "O8 12 4 M12 12 H21 M18 12 V16 M21 12 V15",
        "O12 12 4 M12 3 V5 M12 19 V21 M3 12 H5 M19 12 H21 M5.6 5.6 L7 7 M17 17 L18.4 18.4 M5.6 18.4 L7 17 M17 7 L18.4 5.6",
        "M9 5 H20 C20.6 5 21 5.4 21 6 V18 C21 18.6 20.6 19 20 19 H9 L3 12 Z M12 9 L17 15 M17 9 L12 15",
        "M2.5 9 C8 4 16 4 21.5 9 M5.5 12.5 C9 9.5 15 9.5 18.5 12.5 M8.5 16 C10.5 14.3 13.5 14.3 15.5 16 M12 19.5 V19.6",
        "M7 7 L17 17 L12 22 V2 L17 7 L7 17",
        "M4 9 H8 L13 5 V19 L8 15 H4 Z M16.5 9 C18 10.5 18 13.5 16.5 15",
        "M8 7 L3 12 L8 17 M16 7 L21 12 L16 17 M14 5 L10 19",
        "M3 11 L12 4 L21 11 V20 H15 V14 H9 V20 H3 Z"
    )
}

/** Line icon drawn from [Ic.PATHS]. */
class IconView(context: Context, kind: Int) : View(context) {
    var tint: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }
    private var path = SvgPath.parse(Ic.PATHS[kind])
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setKind(k: Int) {
        path = SvgPath.parse(Ic.PATHS[k])
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height) / 24f
        canvas.save()
        canvas.translate((width - 24f * s) / 2f, (height - 24f * s) / 2f)
        canvas.scale(s, s)
        paint.strokeWidth = 1.8f
        paint.color = tint
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

/** The Blutheist shield. Can show a progress ring, spin while scanning, show the eye or a check. */
class ShieldView(context: Context) : View(context) {
    companion object {
        const val EYE = 0
        const val NONE = 1
        const val CHECK = 2
    }

    var mode: Int = EYE
        set(v) {
            field = v
            invalidate()
        }

    /** -1 = solid outline, 0..1 = ring progress on a dim track. */
    var progress: Float = -1f
        set(v) {
            field = v
            invalidate()
        }

    var eyeAlpha: Int = 255
        set(v) {
            field = v
            invalidate()
        }

    var centerText: String = ""
        set(v) {
            field = v
            invalidate()
        }

    var centerUnit: String = ""
        set(v) {
            field = v
            invalidate()
        }

    private val textBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = UI.bold()
        textSize = 120f
    }
    private val textSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9AA2")
        textAlign = Paint.Align.CENTER
        textSize = 34f
    }
    private var wantSpin = false
    private var spinAnim: ValueAnimator? = null
    private var spinT = 0f

    private val outline = SvgPath.parse("M256 564 C145 508 54 422 54 288 L54 95 L256 22 L458 95 L458 288 C458 422 367 508 256 564 Z")
    private val inner = SvgPath.parse("M256 530 C160 480 80 404 80 288 L80 115 L256 52 L432 115 L432 288 C432 404 352 480 256 530 Z")
    private val eyeOuter = SvgPath.parse("M627 100 C627 260 680 320 770 370 L1010 490 C1090 530 1130 585 1240 597 C1130 609 1090 665 1010 705 L770 830 C680 880 627 940 627 1100 C627 940 575 880 485 830 L245 705 C165 665 125 609 15 597 C125 585 165 530 245 490 L485 370 C575 320 627 260 627 100 Z")
    private val almond = SvgPath.parse("M268 597 C400 505 480 475 565 465 L690 465 C775 475 855 505 985 597 C855 690 775 720 690 733 L565 733 C480 720 400 690 268 597 Z")
    private val star = SvgPath.parse("M627 465 C627 540 575 595 505 600 C575 605 627 660 627 733 C627 660 680 605 750 600 C680 595 627 540 627 465 Z")
    private val check = SvgPath.parse("M212 292 L246 326 L304 254")
    private val measure = PathMeasure(outline, false)
    private val len = measure.length
    private val seg = Path()

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = Color.parseColor("#2C2C30")
        strokeJoin = Paint.Join.ROUND
    }
    private val bright = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#5A5A60")
        strokeJoin = Paint.Join.ROUND
    }
    private val fillW = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val fillB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 13f
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setSpinning(on: Boolean) {
        wantSpin = on
        updateSpin()
    }

    private fun updateSpin() {
        if (wantSpin && isAttachedToWindow) {
            if (spinAnim == null) {
                val a = ValueAnimator.ofFloat(0f, 1f)
                a.duration = 1500
                a.repeatCount = ValueAnimator.INFINITE
                a.interpolator = LinearInterpolator()
                a.addUpdateListener {
                    spinT = it.animatedValue as Float
                    invalidate()
                }
                spinAnim = a
                a.start()
            }
        } else {
            spinAnim?.cancel()
            spinAnim = null
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateSpin()
    }

    override fun onDetachedFromWindow() {
        spinAnim?.cancel()
        spinAnim = null
        super.onDetachedFromWindow()
    }

    private fun segment(canvas: Canvas, a: Float, b: Float) {
        if (b <= a) return
        seg.rewind()
        measure.getSegment(a, b, seg, true)
        canvas.drawPath(seg, bright)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val sc = min(w / 424f, h / 562f)
        canvas.save()
        canvas.translate((w - 424f * sc) / 2f - 44f * sc, (h - 562f * sc) / 2f - 12f * sc)
        canvas.scale(sc, sc)

        if (progress < 0f && !wantSpin) {
            canvas.drawPath(outline, bright)
        } else {
            canvas.drawPath(outline, track)
            if (wantSpin) {
                val start = spinT * len
                val end = start + 0.24f * len
                if (end > len) {
                    segment(canvas, start, len)
                    segment(canvas, 0f, end - len)
                } else {
                    segment(canvas, start, end)
                }
            } else if (progress > 0f) {
                segment(canvas, 0f, len * min(progress, 1f))
            }
        }
        canvas.drawPath(inner, thin)

        if (mode == EYE) {
            fillW.alpha = eyeAlpha
            canvas.save()
            canvas.translate(256f, 288f)
            canvas.scale(0.25f, 0.25f)
            canvas.translate(-627.5f, -600f)
            canvas.drawPath(eyeOuter, fillW)
            canvas.drawPath(almond, fillB)
            canvas.save()
            canvas.clipPath(almond)
            canvas.drawCircle(627f, 599f, 153f, fillW)
            canvas.drawPath(star, fillB)
            canvas.restore()
            canvas.restore()
            fillW.alpha = 255
        } else if (mode == CHECK) {
            canvas.drawPath(check, checkPaint)
        } else if (centerText.isNotEmpty()) {
            canvas.drawText(centerText, 256f, 320f, textBig)
            if (centerUnit.isNotEmpty()) canvas.drawText(centerUnit, 256f, 372f, textSmall)
        }
        canvas.restore()
    }
}

/** Small on/off switch, white when on. */
class PillSwitch(context: Context) : View(context) {
    private var on = false
    private var t = 0f
    private var anim: ValueAnimator? = null
    private val argb = ArgbEvaluator()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackOff = Color.parseColor("#2A2A2E")
    private val knobOff = Color.parseColor("#8E8E95")

    fun isOn(): Boolean = on

    fun set(value: Boolean, animate: Boolean) {
        on = value
        anim?.cancel()
        val target = if (value) 1f else 0f
        if (!animate || !isAttachedToWindow) {
            t = target
            invalidate()
            return
        }
        val a = ValueAnimator.ofFloat(t, target)
        a.duration = 170
        a.addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
        anim = a
        a.start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(UI.dp(context, 50), UI.dp(context, 30))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        trackPaint.color = argb.evaluate(t, trackOff, UI.ACCENT) as Int
        canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, trackPaint)
        val pad = h * 0.15f
        val r = h / 2f - pad
        val cx = pad + r + (w - 2f * pad - 2f * r) * t
        knobPaint.color = argb.evaluate(t, knobOff, UI.ON_ACCENT) as Int
        canvas.drawCircle(cx, h / 2f, r, knobPaint)
    }
}

/** Rounded square check box. */
class CheckView(context: Context) : View(context) {
    var checked: Boolean = false
        set(v) {
            val changed = field != v
            field = v
            if (changed && v && UI.anim) {
                scaleX = 0.78f
                scaleY = 0.78f
                animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2.4f)).start()
            }
            invalidate()
        }
    private val box = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tick = SvgPath.parse("M5.5 12.5 L10 17 L18.5 7.5")

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(UI.dp(context, 28), UI.dp(context, 28))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = UI.dpf(context, 8f)
        if (checked) {
            box.style = Paint.Style.FILL
            box.color = UI.ACCENT
            mark.color = UI.ON_ACCENT
            canvas.drawRoundRect(0f, 0f, w, h, r, r, box)
            canvas.save()
            canvas.scale(w / 24f, h / 24f)
            mark.strokeWidth = 2.2f
            canvas.drawPath(tick, mark)
            canvas.restore()
        } else {
            box.style = Paint.Style.STROKE
            box.strokeWidth = UI.dpf(context, 1.6f)
            box.color = Color.parseColor("#55555C")
            val i = box.strokeWidth / 2f
            canvas.drawRoundRect(i, i, w - i, h - i, r, r, box)
        }
    }
}

/** Storage bar made of grey segments. */
class SegBar(context: Context) : View(context) {
    private var parts: List<Pair<Float, Int>> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setParts(list: List<Pair<Float, Int>>) {
        parts = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rad = h / 2f
        p.color = UI.CARD2
        canvas.drawRoundRect(0f, 0f, w, h, rad, rad, p)
        val clip = Path()
        clip.addRoundRect(RectF(0f, 0f, w, h), rad, rad, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        var x = 0f
        val gap = UI.dpf(context, 2f)
        for (pt in parts) {
            val seg = pt.first.coerceIn(0f, 1f) * w
            if (seg <= 0.5f) continue
            p.color = pt.second
            canvas.drawRect(x, 0f, min(x + seg - gap, w), h, p)
            x += seg
        }
        canvas.restore()
    }
}

/** Vertical bars with a letter box underneath (screen time). Bars rise from the bottom one after another. */
class BarsView(context: Context) : View(context) {
    private var bars: List<Pair<Float, String>> = emptyList()
    private var t = 0f
    private var anim: ValueAnimator? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boxP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = UI.dpf(context, 1f)
    }
    private val boxF = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = UI.bold()
        textSize = UI.dpf(context, 14f)
    }

    fun setBars(list: List<Pair<Float, String>>) {
        bars = list
        anim?.cancel()
        if (list.isEmpty() || !UI.anim) {
            t = 1f
            invalidate()
            return
        }
        t = 0f
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1100
        a.addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
        anim = a
        a.start()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val labelH = UI.dpf(context, 30f)
        val barArea = h - labelH - UI.dpf(context, 12f)
        val col = w / bars.size
        val bw = min(col * 0.55f, UI.dpf(context, 40f))
        val rad = bw / 2f
        track.color = UI.CARD2
        boxP.color = UI.STROKE
        boxF.color = UI.CARD
        fill.shader = LinearGradient(0f, 0f, 0f, barArea, UI.ACCENT, UI.blend(UI.ACCENT, Color.BLACK, 0.3f), Shader.TileMode.CLAMP)
        val n = bars.size
        for (i in bars.indices) {
            val cx = col * i + col / 2f
            canvas.drawRoundRect(cx - bw / 2f, 0f, cx + bw / 2f, barArea, rad, rad, track)
            val raw = (t * (1f + 0.3f * (n - 1)) - 0.3f * i).coerceIn(0f, 1f)
            val e = 1f - (1f - raw) * (1f - raw) * (1f - raw)
            val f = bars[i].first.coerceIn(0f, 1f) * e
            if (f > 0.002f) {
                val fh = max(f * barArea, min(bw, barArea * e))
                canvas.drawRoundRect(cx - bw / 2f, barArea - fh, cx + bw / 2f, barArea, rad, rad, fill)
            }
            val top = h - labelH
            val r = RectF(cx - labelH / 2f, top, cx + labelH / 2f, h)
            canvas.drawRoundRect(r, UI.dpf(context, 9f), UI.dpf(context, 9f), boxF)
            canvas.drawRoundRect(r, UI.dpf(context, 9f), UI.dpf(context, 9f), boxP)
            canvas.drawText(bars[i].second, cx, top + labelH / 2f + txt.textSize * 0.35f, txt)
        }
    }
}

class ThinProgress(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shown = 0f
    private var va: ValueAnimator? = null

    var value: Float
        get() = shown
        set(v) {
            va?.cancel()
            shown = v.coerceIn(0f, 1f)
            invalidate()
        }

    /** Fills smoothly from the current value to [v]. */
    fun animateTo(v: Float, ms: Long = 800) {
        va?.cancel()
        val a = ValueAnimator.ofFloat(shown, v.coerceIn(0f, 1f))
        a.duration = UI.dur(ms)
        a.interpolator = android.view.animation.DecelerateInterpolator(1.8f)
        a.addUpdateListener {
            shown = it.animatedValue as Float
            invalidate()
        }
        va = a
        a.start()
    }

    override fun onDetachedFromWindow() {
        va?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        fill.color = UI.ACCENT
        track.color = UI.DIM
        canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, track)
        if (shown > 0.001f) canvas.drawRoundRect(0f, 0f, max(w * shown, h), h, h / 2f, h / 2f, fill)
    }
}

/** Rolling latency graph; null sample = lost ping. */
class LineChartView(context: Context) : View(context) {
    private val maxPoints = 40
    private val samples = ArrayList<Float>()
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#232327")
        strokeWidth = UI.dpf(context, 1f)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = UI.dpf(context, 2.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33FFFFFF") }
    private val lossP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8E8E95") }

    fun add(ms: Double?) {
        samples.add(if (ms == null) -1f else ms.toFloat())
        while (samples.size > maxPoints) samples.removeAt(0)
        invalidate()
    }

    fun clear() {
        samples.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        area.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x44FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val padR = UI.dpf(context, 8f)
        val padT = UI.dpf(context, 8f)
        val padB = UI.dpf(context, 8f)
        val cw = w - padR
        val ch = h - padT - padB
        for (i in 0..2) {
            val y = padT + ch * i / 2f
            canvas.drawLine(0f, y, w, y, grid)
        }
        line.color = UI.ACCENT
        dotP.color = UI.ACCENT
        if (samples.isEmpty()) return
        var maxV = 60f
        for (v in samples) if (v * 1.25f > maxV) maxV = v * 1.25f
        val step = cw / (maxPoints - 1)
        val offset = maxPoints - samples.size
        val dot = UI.dpf(context, 3f)

        val segs = ArrayList<ArrayList<PointF>>()
        var cur: ArrayList<PointF>? = null
        for (i in samples.indices) {
            val v = samples[i]
            val x = (offset + i) * step
            if (v < 0f) {
                cur = null
                canvas.drawCircle(x, padT + ch, dot, lossP)
                continue
            }
            val y = padT + ch * (1f - (v / maxV).coerceIn(0f, 1f))
            if (cur == null) {
                cur = ArrayList()
                segs.add(cur)
            }
            cur.add(PointF(x, y))
        }
        for (sg in segs) {
            if (sg.size == 1) {
                canvas.drawCircle(sg[0].x, sg[0].y, dot, dotP)
                continue
            }
            val path = Path()
            path.moveTo(sg[0].x, sg[0].y)
            for (k in 1 until sg.size) {
                val a = sg[k - 1]
                val b = sg[k]
                val mx = (a.x + b.x) / 2f
                path.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
            val fillPath = Path(path)
            fillPath.lineTo(sg[sg.size - 1].x, padT + ch)
            fillPath.lineTo(sg[0].x, padT + ch)
            fillPath.close()
            canvas.drawPath(fillPath, area)
            canvas.drawPath(path, line)
        }
        val last = samples[samples.size - 1]
        if (last >= 0f) {
            val x = (offset + samples.size - 1) * step
            val y = padT + ch * (1f - (last / maxV).coerceIn(0f, 1f))
            canvas.drawCircle(x, y, dot * 2.4f, halo)
            canvas.drawCircle(x, y, dot, dotP)
        }
    }
}


/** Round gauge: bright ring (progress), dark gap ring, light disc with a number, an icon or a check. */
class RingView(context: Context) : View(context) {
    var progress: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var valueText: String = ""
        set(v) {
            field = v
            invalidate()
        }
    var unitText: String = ""
        set(v) {
            field = v
            invalidate()
        }
    var iconKind: Int = -1
        set(v) {
            field = v
            iconPath = if (v >= 0) SvgPath.parse(Ic.PATHS[v]) else null
            invalidate()
        }
    var check: Boolean = false
        set(v) {
            field = v
            invalidate()
        }
    var dimmed: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    private var iconPath: Path? = null
    private var spinning = false
    private var spinT = 0f
    private var spinAnim: ValueAnimator? = null
    private var moveAnim: ValueAnimator? = null
    private val tickPath = SvgPath.parse("M5.5 12.5 L10 17 L18.5 7.5")
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UI.bold()
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#0B0B0C")
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#6B6B72")
    }
    private val rect = RectF()

    fun animateTo(p: Float, ms: Long = 900) {
        moveAnim?.cancel()
        val a = ValueAnimator.ofFloat(progress, p.coerceIn(0f, 1f))
        a.duration = UI.dur(ms)
        a.interpolator = android.view.animation.DecelerateInterpolator(1.8f)
        a.addUpdateListener { progress = it.animatedValue as Float }
        moveAnim = a
        a.start()
    }

    fun setSpinning(on: Boolean) {
        spinning = on
        updateSpin()
    }

    private fun updateSpin() {
        if (spinning && isAttachedToWindow) {
            if (spinAnim == null) {
                val a = ValueAnimator.ofFloat(0f, 1f)
                a.duration = 1300
                a.repeatCount = ValueAnimator.INFINITE
                a.interpolator = LinearInterpolator()
                a.addUpdateListener {
                    spinT = it.animatedValue as Float
                    invalidate()
                }
                spinAnim = a
                a.start()
            }
        } else {
            spinAnim?.cancel()
            spinAnim = null
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateSpin()
    }

    override fun onDetachedFromWindow() {
        spinAnim?.cancel()
        spinAnim = null
        moveAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val stroke = s * 0.075f
        val r = s / 2f - stroke / 2f - s * 0.012f

        ring.strokeWidth = stroke
        ring.shader = null
        ring.color = if (dimmed) UI.DIM else UI.blend(UI.DIM, UI.BG, 0.2f)
        canvas.drawCircle(cx, cy, r, ring)
        val accent = if (dimmed) UI.blend(UI.ACCENT, UI.BG, 0.6f) else UI.ACCENT
        rect.set(cx - r, cy - r, cx + r, cy + r)
        if (spinning) {
            // comet: the tail fades into the track, so the moving part blends in smoothly
            val clear = Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent))
            ring.shader = SweepGradient(cx, cy, intArrayOf(clear, accent), floatArrayOf(0f, 0.34f))
            canvas.save()
            canvas.rotate(spinT * 360f, cx, cy)
            canvas.drawArc(rect, 0f, 122f, false, ring)
            canvas.restore()
            ring.shader = null
        } else if (progress >= 0.995f) {
            ring.color = accent
            canvas.drawCircle(cx, cy, r, ring)
        } else if (progress > 0.002f) {
            ring.color = accent
            canvas.drawArc(rect, -90f, 360f * progress, false, ring)
        }

        // dark separator ring, then the light disc
        ring.strokeWidth = s * 0.05f
        ring.color = UI.BG
        val r2 = r - stroke * 1.05f
        canvas.drawCircle(cx, cy, r2, ring)
        val r3 = r2 - s * 0.03f
        fill.color = if (dimmed) UI.blend(UI.discColor(), UI.BG, 0.45f) else UI.discColor()
        canvas.drawCircle(cx, cy, r3, fill)

        val ink = Color.parseColor("#0B0B0C")
        if (check) {
            drawGlyph(canvas, tickPath, cx, cy, r3 * 1.05f, ink)
        } else if (iconPath != null) {
            drawGlyph(canvas, iconPath!!, cx, cy, r3 * 1.05f, ink)
        } else if (valueText.isNotEmpty()) {
            var size = s * 0.29f
            big.textSize = size
            val maxW = r3 * 1.7f
            while (big.measureText(valueText) > maxW && size > s * 0.08f) {
                size -= s * 0.01f
                big.textSize = size
            }
            small.textSize = s * 0.075f
            val hasUnit = unitText.isNotEmpty()
            val baseY = if (hasUnit) cy + size * 0.28f else cy + size * 0.36f
            canvas.drawText(valueText, cx, baseY, big)
            if (hasUnit) canvas.drawText(unitText, cx, baseY + s * 0.115f, small)
        }
    }

    private fun drawGlyph(canvas: Canvas, p: Path, cx: Float, cy: Float, size: Float, color: Int) {
        val sc = size * 0.9f / 24f
        canvas.save()
        canvas.translate(cx - 12f * sc, cy - 12f * sc)
        canvas.scale(sc, sc)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 1.9f
        ring.color = color
        canvas.drawPath(p, ring)
        canvas.restore()
    }
}

/** Small progress indicator for task rows: idle ring, spinner, check or warning. */
class StatusView(context: Context) : View(context) {
    companion object {
        const val IDLE = 0
        const val RUNNING = 1
        const val DONE = 2
        const val WARN = 3
    }

    private var st = IDLE
    private var t = 0f
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tick = SvgPath.parse("M5.5 12.5 L10 17 L18.5 7.5")
    private val bang = SvgPath.parse("M12 6 V13.5 M12 17.5 V17.6")

    fun setState(s: Int) {
        if (s == st) return
        st = s
        if (s == DONE || s == WARN) {
            scaleX = 0.6f
            scaleY = 0.6f
            animate().scaleX(1f).scaleY(1f).setDuration(UI.dur(320)).setInterpolator(OvershootInterpolator(2.2f)).start()
        }
        updateAnim()
        invalidate()
    }

    private fun updateAnim() {
        if (st == RUNNING && isAttachedToWindow) {
            if (anim == null) {
                val a = ValueAnimator.ofFloat(0f, 1f)
                a.duration = 900
                a.repeatCount = ValueAnimator.INFINITE
                a.interpolator = LinearInterpolator()
                a.addUpdateListener {
                    t = it.animatedValue as Float
                    invalidate()
                }
                anim = a
                a.start()
            }
        } else {
            anim?.cancel()
            anim = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnim()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(UI.dp(context, 30), UI.dp(context, 30))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cx = w / 2f
        val r = w * 0.36f
        p.strokeWidth = w * 0.09f
        when (st) {
            IDLE -> {
                p.color = UI.DIM
                canvas.drawCircle(cx, cx, r, p)
            }
            RUNNING -> {
                p.color = UI.DIM
                canvas.drawCircle(cx, cx, r, p)
                val clear = Color.argb(0, Color.red(UI.TEXT), Color.green(UI.TEXT), Color.blue(UI.TEXT))
                p.shader = SweepGradient(cx, cx, intArrayOf(clear, UI.TEXT), floatArrayOf(0f, 0.3f))
                canvas.save()
                canvas.rotate(t * 360f, cx, cx)
                canvas.drawArc(cx - r, cx - r, cx + r, cx + r, 0f, 108f, false, p)
                canvas.restore()
                p.shader = null
            }
            else -> {
                canvas.save()
                canvas.scale(w / 24f, w / 24f)
                p.strokeWidth = 2.1f
                p.color = if (st == DONE) UI.TEXT else UI.MUTED
                canvas.drawPath(if (st == DONE) tick else bang, p)
                canvas.restore()
            }
        }
    }
}
