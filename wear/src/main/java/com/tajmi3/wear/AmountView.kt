package com.tajmi3.wear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Touch UI drawn by hand so every button stays inside the round screen.
 * Mode 1: up to four quick amounts + "other amount". Mode 2: number pad.
 */
class AmountView(
    ctx: Context,
    private val onSend: (Double) -> Unit,
    private val onClose: () -> Unit
) : View(ctx) {

    var quick: List<Double> = emptyList()
    var cur = "₪"

    private var pad = false
    private var digits = ""
    private var done = ""
    private var pressed: String? = null

    private class Btn(val r: RectF, val id: String)
    private var btns = listOf<Btn>()

    private val amber = 0xFFFFC247.toInt()
    private val green = 0xFF5BD68A.toInt()
    private val white = 0xFFF2F2F5.toInt()
    private val muted = 0xFF9A9AA3.toInt()
    private val key = 0xFF1E1E22.toInt()
    private val keyDown = 0xFF3A3A40.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun fmt(n: Double) = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).format(n)

    private fun label(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, bold: Boolean, maxW: Float) {
        text.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        text.color = color
        text.textSize = size
        val w = text.measureText(s)
        if (w > maxW) text.textSize = size * maxW / w
        val fm = text.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, text)
    }

    private fun button(
        c: Canvas, list: ArrayList<Btn>, r: RectF, id: String, s: String,
        bg: Int, fg: Int, size: Float
    ) {
        list += Btn(r, id)
        fill.color = if (pressed == id) keyDown else bg
        val rad = min(r.width(), r.height()) / 2
        c.drawRoundRect(r, rad, rad, fill)
        label(c, s, r.centerX(), r.centerY(), size, fg, true, r.width() * 0.85f)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF000000.toInt())
        val side = min(width, height).toFloat()
        val ox = (width - side) / 2
        val oy = (height - side) / 2
        fun rect(x0: Float, y0: Float, x1: Float, y1: Float) =
            RectF(ox + x0 * side, oy + y0 * side, ox + x1 * side, oy + y1 * side)

        val list = ArrayList<Btn>()

        if (done.isNotEmpty()) {
            label(c, "✓", width / 2f, height / 2f - side * 0.1f, side * 0.25f, green, true, side * 0.5f)
            label(c, done, width / 2f, height / 2f + side * 0.12f, side * 0.075f, white, true, side * 0.6f)
            btns = emptyList()
            return
        }

        if (!pad) {
            label(c, "أضف مبلغ", width / 2f, oy + side * 0.13f, side * 0.07f, muted, false, side * 0.5f)
            val qs = quick.take(4)
            qs.forEachIndexed { i, q ->
                val col = i % 2
                val row = i / 2
                val x0 = if (col == 0) 0.14f else 0.52f
                val y0 = 0.22f + row * 0.18f
                button(c, list, rect(x0, y0, x0 + 0.34f, y0 + 0.15f), "q$i", "+" + fmt(q), key, amber, side * 0.07f)
            }
            button(c, list, rect(0.22f, 0.60f, 0.78f, 0.75f), "other", "مبلغ آخر", amber, 0xFF000000.toInt(), side * 0.065f)
        } else {
            val shown = (if (digits.isEmpty()) "0" else digits) + " " + cur
            label(c, shown, width / 2f, oy + side * 0.17f, side * 0.11f, amber, true, side * 0.5f)
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
            keys.forEachIndexed { i, k ->
                val col = i % 3
                val row = i / 3
                val x0 = 0.17f + col * 0.22f
                val y0 = 0.27f + row * 0.145f
                val r = rect(x0 + 0.005f, y0, x0 + 0.215f, y0 + 0.135f)
                val id = when (k) { "⌫" -> "del"; "✓" -> "ok"; else -> "d$k" }
                val bg = if (k == "✓") amber else key
                val fg = if (k == "✓") 0xFF000000.toInt() else white
                button(c, list, r, id, k, bg, fg, side * 0.075f)
            }
        }
        btns = list
    }

    private fun hit(x: Float, y: Float) = btns.firstOrNull { it.r.contains(x, y) }?.id

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = hit(e.x, e.y); invalidate() }
            MotionEvent.ACTION_MOVE -> {
                val h = hit(e.x, e.y)
                if (h != pressed) { pressed = null; invalidate() }
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
            MotionEvent.ACTION_UP -> {
                val id = hit(e.x, e.y)
                val was = pressed
                pressed = null
                if (id != null && id == was) press(id)
                invalidate()
            }
        }
        return true
    }

    private fun press(id: String) {
        when {
            id.startsWith("q") -> finishWith(quick[id.substring(1).toInt()])
            id == "other" -> { pad = true; digits = "" }
            id == "del" -> digits = digits.dropLast(1)
            id == "ok" -> {
                val a = digits.toDoubleOrNull() ?: 0.0
                if (a > 0) finishWith(a)
            }
            id.startsWith("d") -> if (digits.length < 7) digits = (digits + id.substring(1)).trimStart('0').ifEmpty { if (id == "d0") "" else id.substring(1) }
        }
    }

    private fun finishWith(a: Double) {
        onSend(a)
        done = "أُضيف +" + fmt(a) + " " + cur
        invalidate()
        postDelayed({ onClose() }, 1100)
    }
}
