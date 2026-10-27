package com.knotes.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** Ilustraciones procedurales de las losetas de «Descubrir» (plantillas, stickers, libros, ejemplos, notas). */
class TileArtView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val PAGE = 0      // vista previa de página/plantilla
        const val STICKERS = 1  // pack de stickers (emojis sobre pastel)
        const val BOOK = 2      // diario abierto sobre gris
        const val SAMPLE = 3    // portada + dos páginas
        const val STICKY = 4    // 4 notas sobre pastel
    }

    var mode = PAGE
    var title = ""
    var variant = 0
    var bg = 0xFFF7D6DE.toInt()
    var accent = 0xFFE8A9B8.toInt()
    var emojis: List<String> = emptyList()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pastel = intArrayOf(0xFFE6D8F0.toInt(), 0xFFDCEBF5.toInt(), 0xFFE7EFDC.toInt(), 0xFFF3EEDC.toInt(), 0xFFF9DDE2.toInt())

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        p.style = Paint.Style.FILL; p.color = color
        c.drawRoundRect(RectF(l, t, r, b), rad, rad, p)
    }

    private fun ln(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, sw: Float = 1.5f) {
        p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = color
        c.drawLine(x1, y1, x2, y2, p); p.style = Paint.Style.FILL
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        when (mode) {
            STICKERS -> drawStickers(c, w, h)
            BOOK -> drawBook(c, w, h)
            SAMPLE -> drawSample(c, w, h)
            STICKY -> drawSticky(c, w, h)
            else -> drawPage(c, w, h)
        }
    }

    /* ---------- vista previa de una página ---------- */
    private fun drawPage(c: Canvas, w: Float, h: Float) {
        rr(c, 0f, 0f, w, h, w * 0.06f, Color.WHITE)
        val m = w * 0.06f
        tp.color = 0xFF222222.toInt(); tp.typeface = Typeface.DEFAULT_BOLD
        when (variant) {
            0 -> { // Daily routine (con espiral)
                rr(c, 0f, 0f, w, h, w * 0.06f, 0xFFFFFBF0.toInt())
                for (i in 0 until 9) { p.color = 0xFF7D7480.toInt(); c.drawCircle(w * 0.055f, h * (0.08f + i * 0.105f), w * 0.028f, p) }
                tp.textSize = h * 0.07f; tp.textAlign = Paint.Align.CENTER; c.drawText("Daily Routine", w * 0.5f, h * 0.1f, tp)
                rr(c, w * 0.14f, h * 0.15f, w * 0.5f, h * 0.22f, 6f, 0xFFD8CCE8.toInt())
                rr(c, w * 0.52f, h * 0.15f, w * 0.94f, h * 0.22f, 6f, 0xFFDDE8D8.toInt())
                for (k in 0..4) ln(c, w * 0.14f, h * (0.3f + k * 0.08f), w * 0.46f, h * (0.3f + k * 0.08f), 0xFFBFD7F2.toInt(), 2f)
                rr(c, w * 0.52f, h * 0.25f, w * 0.94f, h * 0.55f, 6f, 0xFFF4E3D0.toInt())
                rr(c, w * 0.52f, h * 0.58f, w * 0.94f, h * 0.85f, 6f, 0xFFC9E0F0.toInt())
                rr(c, w * 0.14f, h * 0.72f, w * 0.46f, h * 0.95f, 6f, 0xFFEEF0D8.toInt())
            }
            1 -> { // Travel checklist
                tp.textSize = h * 0.075f; tp.textAlign = Paint.Align.CENTER; c.drawText("Travel Checklist", w * 0.5f, h * 0.12f, tp)
                val cols = intArrayOf(0xFFE3D7EF.toInt(), 0xFFDCEAF5.toInt(), 0xFFDDEBD7.toInt(), 0xFFF3EFD8.toInt())
                for (i in 0..3) {
                    val l = w * (0.07f + (i % 2) * 0.46f); val t = h * (0.2f + (i / 2) * 0.36f)
                    rr(c, l, t, l + w * 0.41f, t + h * 0.32f, 6f, cols[i])
                }
                rr(c, w * 0.07f, h * 0.93f, w * 0.93f, h * 1.0f, 4f, 0xFFF6D4D8.toInt())
            }
            2 -> { // Daily plan horizontal
                tp.textSize = h * 0.05f; tp.textAlign = Paint.Align.LEFT; c.drawText("DAILY PLAN", m, h * 0.08f, tp)
                rr(c, m, h * 0.1f, w - m, h * 0.115f, 2f, 0xFFF3C9D3.toInt())
                for (k in 0..17) ln(c, m, h * (0.17f + k * 0.045f), w * 0.4f, h * (0.17f + k * 0.045f), 0xFFDDDDDD.toInt(), 1.2f)
                for (col in 0..1) for (k in 0..9) ln(c, w * (0.45f + col * 0.27f), h * (0.17f + k * 0.07f), w * (0.68f + col * 0.27f), h * (0.17f + k * 0.07f), 0xFFDDDDDD.toInt(), 1.2f)
            }
            3 -> { // Daily planning vertical
                tp.color = 0xFFB5B56A.toInt(); tp.textSize = h * 0.05f; tp.textAlign = Paint.Align.CENTER
                c.drawText("Daily planning", w * 0.3f, h * 0.1f, tp)
                for (k in 0..11) ln(c, m, h * (0.38f + k * 0.052f), w * 0.55f, h * (0.38f + k * 0.052f), 0xFFE0E0E0.toInt(), 1.2f)
                for (k in 0..11) ln(c, w * 0.62f, h * (0.38f + k * 0.052f), w - m, h * (0.38f + k * 0.052f), 0xFFE0E0E0.toInt(), 1.2f)
                ln(c, m, h * 0.2f, w * 0.55f, h * 0.2f, 0xFFDDDDDD.toInt()); ln(c, w * 0.62f, h * 0.2f, w - m, h * 0.2f, 0xFFDDDDDD.toInt())
            }
            4 -> { // Weekly to-do
                tp.textSize = h * 0.045f; tp.textAlign = Paint.Align.LEFT; c.drawText("WEEKLY TO DO", m, h * 0.07f, tp)
                for (k in 0..14) ln(c, m, h * (0.12f + k * 0.055f), w * 0.36f, h * (0.12f + k * 0.055f), 0xFFDDDDDD.toInt(), 1.2f)
                for (k in 0..2) { p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0xFFCCCCCC.toInt()
                    c.drawRect(w * 0.4f, h * (0.08f + k * 0.2f), w * 0.95f, h * (0.26f + k * 0.2f), p); p.style = Paint.Style.FILL }
            }
            5 -> { // Weekly schedule
                tp.textSize = h * 0.04f; tp.textAlign = Paint.Align.LEFT; c.drawText("WEEKLY SCHEDULE", m, h * 0.08f, tp)
                ln(c, m, h * 0.14f, w - m, h * 0.14f, 0xFF999999.toInt())
                for (k in 0..6) ln(c, m, h * (0.2f + k * 0.1f), w - m, h * (0.2f + k * 0.1f), 0xFFDDDDDD.toInt(), 1.2f)
                for (k in 0..7) ln(c, w * (0.06f + k * 0.12f), h * 0.14f, w * (0.06f + k * 0.12f), h * 0.9f, 0xFFE6E6E6.toInt(), 1f)
            }
            else -> { // Week list
                tp.textSize = h * 0.06f; tp.textAlign = Paint.Align.LEFT; c.drawText("Week :", m, h * 0.1f, tp)
                for (k in 0..5) { ln(c, m, h * (0.2f + k * 0.14f), w - m, h * (0.2f + k * 0.14f), 0xFFE4E4E4.toInt(), 1.2f)
                    p.color = 0xFFC8D66B.toInt(); c.drawCircle(m + 6f, h * (0.255f + k * 0.14f), 4f, p) }
            }
        }
    }

    /* ---------- pack de stickers ---------- */
    private fun drawStickers(c: Canvas, w: Float, h: Float) {
        rr(c, 0f, 0f, w, h, w * 0.08f, bg)
        // tarjeta-collage
        rr(c, w * 0.05f, h * 0.08f, w * 0.7f, h * 0.5f, w * 0.05f, accent)
        rr(c, w * 0.1f, h * 0.12f, w * 0.66f, h * 0.46f, w * 0.04f, 0x66FFFFFF)
        tp.textAlign = Paint.Align.CENTER
        val e = if (emojis.isEmpty()) listOf("⭐") else emojis
        fun em(i: Int) = e[i % e.size]
        tp.textSize = h * 0.105f
        for (i in 0 until 6) {
            val cx = w * (0.14f + (i % 3) * 0.17f); val cy = h * (0.2f + (i / 3) * 0.17f)
            c.drawText(em(i), cx + w * 0.04f, cy + h * 0.06f, tp)
        }
        // pegatinas grandes
        val bigs = listOf(Triple(0.84f, 0.2f, 6), Triple(0.84f, 0.45f, 7), Triple(0.18f, 0.74f, 8), Triple(0.5f, 0.74f, 9), Triple(0.84f, 0.7f, 10))
        tp.textSize = h * 0.17f
        for ((x, y, i) in bigs) {
            p.color = 0xCCFFFFFF.toInt(); c.drawCircle(w * x, h * (y + 0.01f), h * 0.12f, p)
            c.drawText(em(i), w * x, h * (y + 0.06f), tp)
        }
    }

    /* ---------- diario abierto sobre gris ---------- */
    private fun drawBook(c: Canvas, w: Float, h: Float) {
        rr(c, 0f, 0f, w, h, w * 0.08f, 0xFF6B6B6B.toInt())
        val covers = intArrayOf(0xFF8E7FD0.toInt(), 0xFFF0A0B0.toInt(), 0xFFC0405A.toInt(), 0xFFD9C7A0.toInt(),
            0xFF9C1F3F.toInt(), 0xFFE39A3A.toInt(), 0xFF222222.toInt(), 0xFF3A3A3A.toInt(), 0xFFE0A050.toInt(), 0xFFD7A3A0.toInt())
        val cv = covers[variant % covers.size]
        val l = w * 0.1f; val r = w * 0.9f; val t = h * 0.26f; val b = h * 0.74f
        rr(c, l - 4f, t - 4f, r + 4f, b + 6f, 10f, cv)
        val paper = if (variant % 5 == 3) 0xFFF8EFD8.toInt() else Color.WHITE
        rr(c, l + 6f, t + 3f, w * 0.5f, b, 6f, paper)
        rr(c, w * 0.5f, t + 3f, r - 6f, b, 6f, if (variant % 3 == 0) 0xFFF2EDFF.toInt() else paper)
        // lomo y anillas
        ln(c, w * 0.5f, t + 3f, w * 0.5f, b, 0x33000000, 2f)
        if (variant % 2 == 0) for (i in 0 until 7) { p.color = 0xFFBEBEC8.toInt(); c.drawCircle(w * 0.5f, t + (b - t) * (0.1f + i * 0.13f), 3.2f, p) }
        // pestañas de color
        val tabs = intArrayOf(0xFFF4C14E.toInt(), 0xFF63C9D6.toInt(), 0xFFF29AA5.toInt())
        for (i in 0..2) rr(c, r - 2f, t + (b - t) * (0.2f + i * 0.2f), r + 5f, t + (b - t) * (0.32f + i * 0.2f), 3f, tabs[i])
    }

    /* ---------- portada + 2 páginas ---------- */
    private fun drawSample(c: Canvas, w: Float, h: Float) {
        rr(c, 0f, 0f, w, h, w * 0.05f, 0xFF242424.toInt())
        val m = w * 0.04f
        val coverR = w * 0.4f
        // portada con textura según variante
        val cov = when (variant % 8) { 0 -> 0xFF8C8C8C; 1 -> 0xFF6B4B55; 2 -> 0xFFE9E9E9; 3 -> 0xFF222222
            4 -> 0xFF9FB8B0; 5 -> 0xFF4C4468; 6 -> 0xFFF0E6D0; else -> 0xFF8A3E2C }.toInt()
        rr(c, m, m, coverR, h - m, 6f, cov)
        when (variant % 8) {
            0 -> for (k in 0..14) ln(c, m + 6f, h * (0.08f + k * 0.06f), coverR - 6f, h * (0.05f + k * 0.06f), 0x22000000, 1.5f)
            1 -> for (k in 0..9) { val y = h * (0.06f + k * 0.09f); ln(c, m, y, coverR, y + h * 0.05f, 0xAAC99AA6.toInt(), 6f) }
            2 -> for (k in 0..6) { val pth = Path(); pth.moveTo(m, h * (0.1f + k * 0.14f)); pth.quadTo((m + coverR) / 2f, h * (0.0f + k * 0.14f), coverR, h * (0.12f + k * 0.14f))
                p.style = Paint.Style.STROKE; p.strokeWidth = 7f; p.color = 0xFFC9C9C9.toInt(); c.drawPath(pth, p); p.style = Paint.Style.FILL }
            4 -> { rr(c, m + 6f, h * 0.08f, coverR - 6f, h * 0.28f, 6f, 0xFF7DA39A.toInt()); tp.color = Color.WHITE; tp.textSize = h * 0.07f; tp.textAlign = Paint.Align.CENTER; c.drawText(title.take(12), (m + coverR) / 2f, h * 0.2f, tp) }
            else -> {}
        }
        if (variant % 8 == 1 || variant % 8 == 5) rr(c, coverR - 8f, h * 0.3f, coverR - 2f, h * 0.7f, 3f, 0xFFF0F0F0.toInt())
        // páginas
        val px = w * 0.46f
        rr(c, px, m, w - m, h * 0.55f, 5f, Color.WHITE)
        rr(c, px + w * 0.02f, h * 0.38f, w - m, h - m, 5f, 0xFFF4F4F6.toInt())
        for (k in 0..5) ln(c, px + 8f, h * (0.12f + k * 0.06f), w - m - 8f, h * (0.12f + k * 0.06f), 0xFFC9C9C9.toInt(), 1.2f)
        for (k in 0..4) ln(c, px + w * 0.05f, h * (0.5f + k * 0.08f), w - m - 8f, h * (0.5f + k * 0.08f), 0xFFC9D3E8.toInt(), 1.2f)
    }

    /* ---------- 4 notas adhesivas sobre pastel ---------- */
    private fun drawSticky(c: Canvas, w: Float, h: Float) {
        rr(c, 0f, 0f, w, h, w * 0.08f, bg)
        val cols = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFBE6D0.toInt(), 0xFFE0D8F5.toInt(), 0xFFD6E8F8.toInt())
        for (i in 0..3) {
            val l = w * (0.1f + (i % 2) * 0.43f); val t = h * (0.1f + (i / 2) * 0.43f)
            val r = l + w * 0.36f; val b = t + h * 0.36f
            when ((variant + i) % 4) {
                0 -> rr(c, l, t, r, b, 5f, cols[i])
                1 -> { p.color = cols[i]; c.drawOval(RectF(l, t, r, b), p) }
                2 -> { p.color = cols[i]; val s = Path(); s.moveTo(l, t); s.lineTo(r, t); s.lineTo(r, b - 10f)
                    s.quadTo((l + r) / 2f, b + 10f, l, b - 10f); s.close(); c.drawPath(s, p) }
                else -> { p.color = cols[i]; c.drawCircle((l + r) / 2f, (t + b) / 2f, (r - l) / 2f, p) }
            }
            for (k in 0..2) ln(c, l + 10f, t + (b - t) * (0.3f + k * 0.2f), r - 10f, t + (b - t) * (0.3f + k * 0.2f), 0x33000000, 1.2f)
            // washi tape
            if ((variant + i) % 3 == 0) { p.color = 0xAAF29AA5.toInt(); c.save(); c.rotate(-10f, (l + r) / 2f, t); c.drawRect((l + r) / 2f - 16f, t - 6f, (l + r) / 2f + 16f, t + 8f, p); c.restore() }
        }
    }
}
