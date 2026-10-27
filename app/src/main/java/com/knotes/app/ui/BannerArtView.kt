package com.knotes.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** Ilustración procedural de los dos banners del inicio (Discover / Tutoriales). */
class BannerArtView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var kind: Int = 0   // 0 Discover · 1 Tutoriales

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        p.style = Paint.Style.FILL; p.color = color
        c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (kind == 0) drawDiscover(c, w, h) else drawTutorial(c, w, h)
    }

    private fun drawDiscover(c: Canvas, w: Float, h: Float) {
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFFFFF3E2.toInt(), 0xFFFFE6D6.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        // cinta «Discover»
        val ribbon = Path().apply {
            moveTo(w * 0.22f, 0f); lineTo(w * 0.78f, 0f); lineTo(w * 0.74f, h * 0.11f)
            quadTo(w * 0.5f, h * 0.14f, w * 0.26f, h * 0.11f); close()
        }
        p.color = 0xFFE9CFA2.toInt(); c.drawPath(ribbon, p)
        tp.color = 0xFF8A5E2B.toInt(); tp.textAlign = Paint.Align.CENTER
        tp.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); tp.textSize = h * 0.085f
        c.drawText("Discover", w / 2f, h * 0.085f, tp)
        // dos hojas de stickers
        val cols = intArrayOf(0xFF8FD3C9.toInt(), 0xFFF4C8A8.toInt(), 0xFFE3B9D4.toInt(), 0xFFB9CDF2.toInt(), 0xFFF2E196.toInt())
        for (sheet in 0..1) {
            val l = w * (0.08f + sheet * 0.46f); val t = h * 0.16f
            rr(c, l, t, l + w * 0.42f, t + h * 0.27f, 6f, 0xFFFFFAF2.toInt())
            for (i in 0 until 12) {
                val cx = l + w * (0.04f + (i % 4) * 0.095f); val cy = t + h * (0.03f + (i / 4) * 0.08f)
                rr(c, cx, cy, cx + w * 0.075f, cy + h * 0.06f, 4f, cols[(i + sheet) % cols.size])
            }
        }
        // tres tabletas
        for (i in 0..2) {
            val l = w * (0.02f + i * 0.34f)
            rr(c, l, h * 0.46f, l + w * 0.3f, h * 0.62f, 6f, 0xFF2B2B2B.toInt())
            rr(c, l + 5f, h * 0.475f, l + w * 0.3f - 5f, h * 0.61f, 4f, 0xFFF6F2EA.toInt())
            for (k in 0..3) rr(c, l + 12f, h * (0.49f + k * 0.028f), l + w * (0.12f + k * 0.03f), h * (0.502f + k * 0.028f), 2f, cols[k % cols.size])
        }
        // cuadernos de colores (laterales)
        rr(c, w * 0.03f, h * 0.66f, w * 0.17f, h * 0.76f, 6f, 0xFFF4B94E.toInt())
        rr(c, w * 0.03f, h * 0.79f, w * 0.17f, h * 0.89f, 6f, 0xFFF0A8B0.toInt())
        rr(c, w * 0.85f, h * 0.66f, w * 0.99f, h * 0.76f, 6f, 0xFF7A5568.toInt())
        rr(c, w * 0.85f, h * 0.79f, w * 0.99f, h * 0.89f, 6f, 0xFF7B4B3A.toInt())
        // tableta grande «To Do»
        rr(c, w * 0.18f, h * 0.56f, w * 0.82f, h * 1.05f, 14f, 0xFF1E1E1E.toInt())
        rr(c, w * 0.2f, h * 0.585f, w * 0.8f, h * 1.05f, 10f, 0xFFF4F7FB.toInt())
        tp.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL); tp.color = 0xFF333333.toInt()
        tp.textAlign = Paint.Align.LEFT; tp.textSize = h * 0.045f
        c.drawText("To Do", w * 0.23f, h * 0.65f, tp)
        c.drawText("Goals", w * 0.52f, h * 0.65f, tp)
        for (k in 0..6) rr(c, w * 0.23f, h * (0.675f + k * 0.045f), w * 0.46f, h * (0.703f + k * 0.045f), 3f, 0xFFB9D3EE.toInt())
        for (k in 0..3) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0xFF555555.toInt()
            c.drawRect(w * 0.52f, h * (0.675f + k * 0.07f), w * 0.545f, h * (0.7f + k * 0.07f), p)
            p.style = Paint.Style.FILL; p.color = 0xFFCCCCCC.toInt()
            c.drawRect(w * 0.56f, h * (0.685f + k * 0.07f), w * 0.78f, h * (0.692f + k * 0.07f), p)
            c.drawRect(w * 0.56f, h * (0.705f + k * 0.07f), w * 0.72f, h * (0.712f + k * 0.07f), p)
        }
        // destellos
        tp.textAlign = Paint.Align.CENTER; tp.textSize = h * 0.07f; tp.color = 0xFFF4B94E.toInt()
        c.drawText("✦", w * 0.9f, h * 0.18f, tp)
        tp.textSize = h * 0.04f; c.drawText("✦", w * 0.82f, h * 0.24f, tp)
    }

    private fun drawTutorial(c: Canvas, w: Float, h: Float) {
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFFEFF5FF.toInt(), 0xFFE3EDFF.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        // nubes
        p.color = Color.WHITE
        for ((cx, cy, r) in listOf(Triple(0.3f, 0.07f, 0.06f), Triple(0.38f, 0.065f, 0.075f), Triple(0.46f, 0.08f, 0.055f),
            Triple(0.78f, 0.1f, 0.05f), Triple(0.85f, 0.095f, 0.06f)))
            c.drawCircle(w * cx, h * cy, w * r, p)
        // burbujas de chat
        rr(c, w * 0.52f, h * 0.17f, w * 0.68f, h * 0.24f, 5f, 0xFF6C8CE8.toInt())
        rr(c, w * 0.67f, h * 0.43f, w * 0.88f, h * 0.51f, 5f, 0xFF6C8CE8.toInt())
        rr(c, w * 0.1f, h * 0.43f, w * 0.2f, h * 0.5f, 5f, 0xFF6C8CE8.toInt())
        tp.color = Color.WHITE; tp.textAlign = Paint.Align.CENTER; tp.textSize = h * 0.045f
        tp.typeface = Typeface.DEFAULT_BOLD
        c.drawText("?", w * 0.775f, h * 0.495f, tp)
        // libro/tableta abierta
        rr(c, w * 0.14f, h * 0.3f, w * 0.52f, h * 0.84f, 10f, 0xFF1F6A86.toInt())
        rr(c, w * 0.17f, h * 0.32f, w * 0.52f, h * 0.82f, 6f, 0xFFE9EEF4.toInt())
        val page = Path().apply {
            moveTo(w * 0.5f, h * 0.31f); lineTo(w * 0.86f, h * 0.36f); lineTo(w * 0.86f, h * 0.84f); lineTo(w * 0.5f, h * 0.8f); close()
        }
        p.color = Color.WHITE; c.drawPath(page, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = 0xFF1F6A86.toInt(); c.drawPath(page, p)
        p.style = Paint.Style.FILL
        // bombilla
        p.color = 0xFF6C8CE8.toInt(); c.drawCircle(w * 0.33f, h * 0.5f, w * 0.06f, p)
        rr(c, w * 0.305f, h * 0.55f, w * 0.355f, h * 0.575f, 3f, 0xFF6C8CE8.toInt())
        // pila de libros
        rr(c, w * 0.56f, h * 0.84f, w * 0.97f, h * 0.9f, 6f, 0xFF8EA4F0.toInt())
        rr(c, w * 0.6f, h * 0.78f, w * 0.97f, h * 0.84f, 6f, 0xFFB9C6F7.toInt())
        rr(c, w * 0.52f, h * 0.9f, w * 0.97f, h * 0.97f, 6f, 0xFF6C8CE8.toInt())
        // persona
        p.color = 0xFF2B3A67.toInt(); c.drawCircle(w * 0.58f, h * 0.45f, w * 0.045f, p)
        rr(c, w * 0.545f, h * 0.49f, w * 0.625f, h * 0.68f, 10f, 0xFF6C8CE8.toInt())
        rr(c, w * 0.55f, h * 0.67f, w * 0.585f, h * 0.84f, 6f, 0xFF2B3A67.toInt())
        rr(c, w * 0.59f, h * 0.67f, w * 0.62f, h * 0.83f, 6f, 0xFF2B3A67.toInt())
        rr(c, w * 0.61f, h * 0.5f, w * 0.73f, h * 0.53f, 6f, 0xFF6C8CE8.toInt())
    }
}
