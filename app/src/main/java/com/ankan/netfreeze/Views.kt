package com.ankan.netfreeze

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The big round "eye" button on the home screen. */
class PowerButtonView(context: Context) : View(context) {

    private var active = false
    private var progress = 0f
    private var pulse = 0f
    private var progAnim: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null
    private var lastSat = -1f

    private val argb = ArgbEvaluator()
    private val logo: Drawable? = context.getDrawable(R.drawable.ic_logo_mark)?.mutate()

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val ringOff = Color.parseColor("#2A3B58")
    private val outerOff = Color.parseColor("#16233A")

    init {
        isClickable = true
        isFocusable = true
    }

    fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        progAnim?.cancel()
        val anim = ValueAnimator.ofFloat(progress, if (on) 1f else 0f)
        anim.duration = 450
        anim.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        progAnim = anim
        anim.start()
        updatePulse()
    }

    private fun updatePulse() {
        if (active && isAttachedToWindow) {
            if (pulseAnim == null) {
                val a = ValueAnimator.ofFloat(0f, 1f)
                a.duration = 2400
                a.repeatCount = ValueAnimator.INFINITE
                a.interpolator = LinearInterpolator()
                a.addUpdateListener {
                    pulse = it.animatedValue as Float
                    invalidate()
                }
                pulseAnim = a
                a.start()
            }
        } else {
            pulseAnim?.cancel()
            pulseAnim = null
            pulse = 0f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        progress = if (active) 1f else 0f
        updatePulse()
    }

    override fun onDetachedFromWindow() {
        pulseAnim?.cancel()
        pulseAnim = null
        progAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val sc = if (pressed) 0.95f else 1f
        animate().scaleX(sc).scaleY(sc).setDuration(110).start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = min(w, h).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        glowPaint.shader = RadialGradient(
            cx, cy, s * 0.5f,
            intArrayOf(0x7035D4FF, 0x0035D4FF),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val r = s * 0.36f
        discPaint.shader = RadialGradient(
            cx, cy - r * 0.35f, r * 1.5f,
            Color.parseColor("#182B4D"), Color.parseColor("#0A1322"),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val s = min(w, h)
        val p = progress
        val r = s * 0.36f
        val d = resources.displayMetrics.density

        // soft glow
        if (p > 0.01f) {
            glowPaint.alpha = (255f * p * (0.6f + 0.4f * (1f - pulse))).toInt().coerceIn(0, 255)
            val sc = 1f + 0.05f * pulse
            canvas.save()
            canvas.scale(sc, sc, cx, cy)
            canvas.drawCircle(cx, cy, s * 0.5f, glowPaint)
            canvas.restore()
        }

        // outer hairline ring
        outerPaint.strokeWidth = 1.2f * d
        outerPaint.color = argb.evaluate(p, outerOff, Color.parseColor("#2A6B80")) as Int
        canvas.drawCircle(cx, cy, s * 0.455f, outerPaint)

        // expanding ripple while frozen
        if (p > 0.05f) {
            ripplePaint.strokeWidth = 2f * d
            ripplePaint.color = UI.ACCENT
            ripplePaint.alpha = (150f * p * (1f - pulse)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r + (s * 0.5f - r) * pulse, ripplePaint)
        }

        // disc + ring
        canvas.drawCircle(cx, cy, r, discPaint)
        ringPaint.strokeWidth = 3f * d
        ringPaint.color = argb.evaluate(p, ringOff, UI.ACCENT) as Int
        canvas.drawCircle(cx, cy, r, ringPaint)

        // logo (grey + dim when off, full colour when on)
        val lg = logo
        if (lg != null) {
            val lw = s * 0.46f
            val lh = lw * (114f / 160f)
            lg.setBounds(
                (cx - lw / 2f).toInt(), (cy - lh / 2f).toInt(),
                (cx + lw / 2f).toInt(), (cy + lh / 2f).toInt()
            )
            if (lastSat != p) {
                val cm = ColorMatrix()
                cm.setSaturation(p)
                lg.colorFilter = ColorMatrixColorFilter(cm)
                lg.alpha = (255f * (0.45f + 0.55f * p)).toInt().coerceIn(0, 255)
                lastSat = p
            }
            lg.draw(canvas)
        }
    }
}

/** Small on/off pill used in the app list. */
class PillSwitch(context: Context) : View(context) {
    private var on = false
    private var t = 0f
    private var anim: ValueAnimator? = null
    private val argb = ArgbEvaluator()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackOff = Color.parseColor("#22324D")
    private val knobOff = Color.parseColor("#8496B2")

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
        setMeasuredDimension(UI.dp(context, 46), UI.dp(context, 28))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        trackPaint.color = argb.evaluate(t, trackOff, UI.ACCENT) as Int
        canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, trackPaint)
        val pad = h * 0.16f
        val r = h / 2f - pad
        val cx = pad + r + (w - 2f * pad - 2f * r) * t
        knobPaint.color = argb.evaluate(t, knobOff, Color.parseColor("#04121C")) as Int
        canvas.drawCircle(cx, h / 2f, r, knobPaint)
    }
}

/** Bottom-bar icons drawn with plain canvas strokes. kind: 0 = freeze, 1 = apps, 2 = speed. */
class TabIconView(context: Context, private val kind: Int) : View(context) {
    private var selectedState = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setSelectedState(sel: Boolean) {
        selectedState = sel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        paint.strokeWidth = s * 0.085f
        paint.color = if (selectedState) UI.ACCENT else UI.MUTED
        when (kind) {
            0 -> {
                val r = (s * 0.44f).toDouble()
                for (k in 0 until 6) {
                    val a = Math.toRadians(-90.0 + k * 60.0)
                    val ex = cx + (cos(a) * r).toFloat()
                    val ey = cy + (sin(a) * r).toFloat()
                    canvas.drawLine(cx, cy, ex, ey, paint)
                    val bx = cx + (cos(a) * r * 0.60).toFloat()
                    val by = cy + (sin(a) * r * 0.60).toFloat()
                    for (off in intArrayOf(-50, 50)) {
                        val b = Math.toRadians(-90.0 + k * 60.0 + off)
                        canvas.drawLine(
                            bx, by,
                            bx + (cos(b) * r * 0.30).toFloat(),
                            by + (sin(b) * r * 0.30).toFloat(),
                            paint
                        )
                    }
                }
            }
            1 -> {
                val gap = s * 0.14f
                val sz = s * 0.30f
                val rad = s * 0.07f
                for (row in 0..1) {
                    for (col in 0..1) {
                        val l = cx - gap / 2f - sz + col * (sz + gap)
                        val t = cy - gap / 2f - sz + row * (sz + gap)
                        canvas.drawRoundRect(l, t, l + sz, t + sz, rad, rad, paint)
                    }
                }
            }
            else -> {
                val r = s * 0.38f
                val oy = s * 0.05f
                val rect = RectF(cx - r, cy - r + oy, cx + r, cy + r + oy)
                canvas.drawArc(rect, 150f, 240f, false, paint)
                val ang = Math.toRadians(-52.0)
                val len = (r * 0.74f).toDouble()
                canvas.drawLine(
                    cx, cy + oy,
                    cx + (cos(ang) * len).toFloat(),
                    cy + oy + (sin(ang) * len).toFloat(),
                    paint
                )
            }
        }
    }
}

/** Thin rounded progress bar. */
class ThinProgress(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UI.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UI.ACCENT }

    var value: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, track)
        if (value > 0f) {
            canvas.drawRoundRect(0f, 0f, max(w * value, h), h, h / 2f, h / 2f, fill)
        }
    }
}

/** Rolling latency graph. A sample of null means the ping was lost. */
class LineChartView(context: Context) : View(context) {
    private val maxPoints = 40
    private val samples = ArrayList<Float>()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UI.STROKE
        strokeWidth = UI.dpf(context, 1f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UI.ACCENT
        style = Paint.Style.STROKE
        strokeWidth = UI.dpf(context, 2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UI.ACCENT }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3335D4FF") }
    private val lossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UI.BAD }

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
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0x5535D4FF, 0x0035D4FF,
            Shader.TileMode.CLAMP
        )
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
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        if (samples.isEmpty()) return

        var maxV = 60f
        for (v in samples) {
            if (v * 1.25f > maxV) maxV = v * 1.25f
        }
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
                canvas.drawCircle(x, padT + ch, dot, lossPaint)
                continue
            }
            val y = padT + ch * (1f - (v / maxV).coerceIn(0f, 1f))
            if (cur == null) {
                cur = ArrayList()
                segs.add(cur)
            }
            cur.add(PointF(x, y))
        }

        for (seg in segs) {
            if (seg.size == 1) {
                canvas.drawCircle(seg[0].x, seg[0].y, dot, dotPaint)
                continue
            }
            val path = Path()
            path.moveTo(seg[0].x, seg[0].y)
            for (k in 1 until seg.size) {
                val a = seg[k - 1]
                val b = seg[k]
                val mx = (a.x + b.x) / 2f
                path.cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
            val area = Path(path)
            area.lineTo(seg[seg.size - 1].x, padT + ch)
            area.lineTo(seg[0].x, padT + ch)
            area.close()
            canvas.drawPath(area, fillPaint)
            canvas.drawPath(path, linePaint)
        }

        val last = samples[samples.size - 1]
        if (last >= 0f) {
            val x = (offset + samples.size - 1) * step
            val y = padT + ch * (1f - (last / maxV).coerceIn(0f, 1f))
            canvas.drawCircle(x, y, dot * 2.4f, haloPaint)
            canvas.drawCircle(x, y, dot, dotPaint)
        }
    }
}
