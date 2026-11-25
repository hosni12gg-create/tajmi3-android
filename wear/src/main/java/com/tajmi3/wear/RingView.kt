package com.tajmi3.wear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Round-screen progress view. All text rows are laid out one under the other and each row
 * is shrunk to fit the width of the circle at its own height, so nothing overlaps or gets cut.
 */
class RingView(ctx: Context) : View(ctx) {
    private var data: Store.Data? = null

    private val amber = 0xFFFFC247.toInt()
    private val track = 0xFF2A2A2E.toInt()
    private val white = 0xFFF2F2F5.toInt()
    private val muted = 0xFF9A9AA3.toInt()
    private val green = 0xFF5BD68A.toInt()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun fmt(n: Double) = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).format(n)

    fun show(d: Store.Data?) {
        data = d
        invalidate()
    }

    private class Row(val s: String, val size: Float, val color: Int, val bold: Boolean)

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF000000.toInt())
        val w = width.toFloat()
        val h = height.toFloat()
        val side = min(w, h)
        val cx = w / 2
        val cy = h / 2

        val stroke = side * 0.05f
        val r = side / 2 - stroke / 2 - side * 0.02f
        val inner = r - stroke / 2 - side * 0.05f   // usable text radius
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)

        val d = data
        val pct = if (d != null && d.target > 0) min(100.0, d.saved / d.target * 100) else 0.0

        ringPaint.strokeWidth = stroke
        ringPaint.color = track
        c.drawArc(rect, 0f, 360f, false, ringPaint)
        if (pct > 0) {
            ringPaint.color = if (pct >= 100) green else amber
            c.drawArc(rect, -90f, (pct / 100 * 360).toFloat(), false, ringPaint)
        }

        val rows = ArrayList<Row>()
        if (d == null) {
            rows += Row("افتح تطبيق تجميع", side * 0.08f, white, true)
            rows += Row("على الجوال", side * 0.08f, white, true)
            rows += Row("عشان يظهر التقدّم", side * 0.06f, muted, false)
        } else {
            val remaining = max(0.0, d.target - d.saved)
            if (d.name.isNotBlank()) rows += Row(d.name, side * 0.075f, muted, false)
            rows += Row("${pct.roundToInt()}%", side * 0.21f, if (pct >= 100) green else amber, true)
            rows += if (pct >= 100) Row("وصلت للهدف", side * 0.07f, green, true)
            else Row("المتبقي ${fmt(remaining)} ${d.cur}", side * 0.07f, white, true)
            rows += Row("${fmt(d.saved)} / ${fmt(d.target)} ${d.cur}", side * 0.055f, muted, false)
            rows += Row("اضغط لإضافة مبلغ", side * 0.045f, amber, false)
        }

        // 1) measure every row at its wanted size, 2) stack them centred, 3) shrink each to the chord.
        val gap = side * 0.018f
        val heights = rows.map { row ->
            text.textSize = row.size
            val fm = text.fontMetrics
            fm.descent - fm.ascent
        }
        val total = heights.sum() + gap * (rows.size - 1)
        var y = cy - total / 2
        for ((i, row) in rows.withIndex()) {
            val rh = heights[i]
            val dy = max(kotlin.math.abs(y - cy), kotlin.math.abs(y + rh - cy))
            val chord = 2 * sqrt(max(0f, inner * inner - dy * dy))
            text.typeface = if (row.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            text.color = row.color
            var size = row.size
            text.textSize = size
            val maxW = chord * 0.94f
            if (text.measureText(row.s) > maxW) {
                size = max(row.size * 0.5f, size * maxW / text.measureText(row.s))
                text.textSize = size
            }
            val fm = text.fontMetrics
            val shown = TextUtils.ellipsize(row.s, text, maxW, TextUtils.TruncateAt.END).toString()
            c.drawText(shown, cx, y - fm.ascent, text)
            y += rh + gap
        }
    }
}
