package com.knotes.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

/* ============================================================
   KNotes — portadas procedurales
   Cada cuaderno tiene un estilo + semilla: el arte se dibuja
   con gradientes, blobs orgánicos y anillados. Nada impacta.
   ============================================================ */

class NotebookCoverView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var style: Int = 0
    var seed: Long = 1
    /** portada con FOTO del usuario (ruta al jpg guardado en el diario) */
    var imgPath: String? = null
    /** portada de COLOR sólido (0 = usa el estilo procedural) */
    var solid: Int = 0

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val p = imgPath
        if (p != null && java.io.File(p).exists() && java.io.File(p).length() > 0) {
            drawImageCover(c, width.toFloat(), height.toFloat(), p)
        } else if (solid != 0) {
            drawSolid(c, width.toFloat(), height.toFloat(), solid)
        } else {
            drawCover(c, width.toFloat(), height.toFloat(), style, seed)
        }
    }

    /** cuaderno de un solo color: lomo más oscuro, aros y destello suave */
    private fun drawSolid(c: Canvas, w: Float, h: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = w * 0.07f
        p.color = color; c.drawRoundRect(0f, 0f, w, h, r, r, p)
        val dark = Color.argb(60, 0, 0, 0)
        p.color = dark; c.drawRoundRect(0f, 0f, w * 0.16f, h, r, r, p)
        p.color = Color.argb(40, 255, 255, 255); c.drawRect(w * 0.16f, 0f, w * 0.19f, h, p)
        p.color = Color.argb(150, 255, 255, 255)
        for (i in 0 until 7) c.drawRoundRect(w * 0.04f, h * (0.1f + i * 0.12f), w * 0.15f, h * (0.1f + i * 0.12f) + h * 0.022f, 3f, 3f, p)
        p.color = Color.argb(40, 255, 255, 255)
        c.drawRoundRect(w * 0.7f, h * 0.55f, w * 0.9f, h * 0.7f, 8f, 8f, p)
    }

    companion object {

        /* --- utilidades --- */
        class MyRnd(private var s: Long) {
            fun next(): Float {
                s = s * 6364136223846793005L + 1442695040888963407L
                return ((s ushr 33).toDouble() / Int.MAX_VALUE).toFloat().mod2()
            }
            private fun Float.mod2(): Float = this % 1f + { if (this < 0) 1f else 0f }()
        }

        private fun paint(fill: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill; style = Paint.Style.FILL
        }

        private fun blobPath(rnd: MyRnd, cx: Float, cy: Float, radius: Float): Path {
            val p = Path()
            val lobes = 5 + rnd.next() * 4f
            val seq = ArrayList<Pair<Float, Float>>()
            val r0 = radius * (0.75f + rnd.next() * 0.5f)
            val n = lobes.roundToInt()
            val pts = ArrayList<Canvas2.Pt2>()
            for (i in 0 until n) {
                val ang = i * (Math.PI * 2 / n).toFloat()
                val rr = r0 * (0.7f + rnd.next() * 0.55f)
                pts.add(Canvas2.Pt2(cx + Math.cos(ang.toDouble()).toFloat() * rr,
                    cy + Math.sin(ang.toDouble()).toFloat() * rr))
            }
            return Canvas2.smoothClosed(pts)
        }

        /* --- paleta por estilo --- */
        private data class StyleSpec(
            val baseA: Int, val baseB: Int,      // gradiente de fondo
            val blob: Int, val blob2: Int,       // blobs decorativos
            val binding: Int, val accent: Int,   // anillado / banda
            val kind: Int                        // 0=carrillo,1=anillado espiral,2=espiral+pocket,3=banda
        )

        private val SPECS = arrayOf(
            StyleSpec(0xFF2F2A34.toInt(), 0xFF241F2B.toInt(), 0xFFF3B7CB.toInt(), 0xFFE889B0.toInt(), 0xFF3A323E.toInt(), 0xFFF1A2C0.toInt(), 3), // 0 oscuro con sello rosa
            StyleSpec(0xFF8FE0DC.toInt(), 0xFF6FCBC8.toInt(), 0xFFDFF4F1.toInt(), 0xFFB7E7E2.toInt(), 0xFF6FBCB8.toInt(), 0xFFE8F7F5.toInt(), 3), // 1 menta con correa
            StyleSpec(0xFFF2E8D5.toInt(), 0xFFE7DDBE.toInt(), 0xFF20242C.toInt(), 0xFFC8A46B.toInt(), 0xFF8A5A3A.toInt(), 0xFF20242C.toInt(), 1), // 2 arte abstracto con espiral
            StyleSpec(0xFF67C4EC.toInt(), 0xFF52B4E4.toInt(), 0xFF4FA8DC.toInt(), 0xFF7FD0EF.toInt(), 0xFF3E90C4.toInt(), 0xFF42AEE0.toInt(), 2), // 3 azul anillado
            StyleSpec(0xFFDCC8E8.toInt(), 0xFFCEB8DC.toInt(), 0xFFF0CDD5.toInt(), 0xFFC8B4E8.toInt(), 0xFFB49CC8.toInt(), 0xFFE8E0D8.toInt(), 0),  // 4 óleo pastel
            StyleSpec(0xFFF3A793.toInt(), 0xFFE98F76.toInt(), 0xFFB04A3C.toInt(), 0xFFF6D8A0.toInt(), 0xFF7E3830.toInt(), 0xFFF2B9A6.toInt(), 2),  // 5 coral
            StyleSpec(0xFFB9DCC8.toInt(), 0xFFA3D0B8.toInt(), 0xFF67967E.toInt(), 0xFFEFF3EA.toInt(), 0xFF517A64.toInt(), 0xFFDCEFE2.toInt(), 1),  // 6 verde salvia
            StyleSpec(0xFFE8D4C8.toInt(), 0xFFDCC0B4.toInt(), 0xFFC88A70.toInt(), 0xFFEFD8CC.toInt(), 0xFF9E6A5A.toInt(), 0xFFF0E0D6.toInt(), 0),  // 7 terracota
            StyleSpec(0xFF2B3148.toInt(), 0xFF232838.toInt(), 0xFFD8DCEF.toInt(), 0xFF8D96C8.toInt(), 0xFF3A4058.toInt(), 0xFFE8EAF8.toInt(), 3),  // 8 noche
            StyleSpec(0xFFF8E8C8.toInt(), 0xFFF0D8A8.toInt(), 0xFFE8B84A.toInt(), 0xFFF8F0D8.toInt(), 0xFFC8963A.toInt(), 0xFFF4CE68.toInt(), 2),  // 9 mostaza
            StyleSpec(0xFFD8E2F0.toInt(), 0xFFC8D4EC.toInt(), 0xFF8DA2E8.toInt(), 0xFFEFF2FA.toInt(), 0xFF6A7EC4.toInt(), 0xFFE0E8F8.toInt(), 114), // 10 lila
            StyleSpec(0xFF2E2E2E.toInt(), 0xFF232323.toInt(), 0xFFE8E8E8.toInt(), 0xFF444.toInt(), 0xFF383838.toInt(), 0xFFE8E8E8.toInt(), 1),      // 11 minimal
            // —— 8 diseños NUEVOS (más variedad en el creador de diarios) ——
            StyleSpec(0xFFF8D7E3.toInt(), 0xFFF2C4D8.toInt(), 0xFFE88BAD.toInt(), 0xFFFFE8F0.toInt(), 0xFFD8A8C0.toInt(), 0xFFEE5F8B.toInt(), 2),  // 12 pétalos
            StyleSpec(0xFFE9DCC8.toInt(), 0xFFDDCBAA.toInt(), 0xFFB89868.toInt(), 0xFFF4EDDC.toInt(), 0xFF9A7848.toInt(), 0xFF7A5A34.toInt(), 1),  // 13 papel craft
            StyleSpec(0xFFFFF6E8.toInt(), 0xFFF8E8CE.toInt(), 0xFFE8C88A.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8A060.toInt(), 0xFFE8B84A.toInt(), 0),  // 14 rayas crema
            StyleSpec(0xFF5C7CAA.toInt(), 0xFF47618C.toInt(), 0xFFA8C4E0.toInt(), 0xFFE0ECF8.toInt(), 0xFF2E4262.toInt(), 0xFFD8E4F4.toInt(), 3),  // 15 denim
            StyleSpec(0xFFFCE8EE.toInt(), 0xFFF8D8E4.toInt(), 0xFFEE5F8B.toInt(), 0xFFFFFFFF.toInt(), 0xFFD898B0.toInt(), 0xFFEE5F8B.toInt(), 1),  // 16 vichy rosa
            StyleSpec(0xFF3E5C44.toInt(), 0xFF2E4634.toInt(), 0xFF88B898.toInt(), 0xFFE8F4EC.toInt(), 0xFF243628.toInt(), 0xFFC8E4D4.toInt(), 2),  // 17 bosque
            StyleSpec(0xFFF4EAD8.toInt(), 0xFFE4D4B8.toInt(), 0xFFD8B888.toInt(), 0xFFFDF8EC.toInt(), 0xFFA88458.toInt(), 0xFFC09868.toInt(), 0),  // 18 arena
            StyleSpec(0xFF222228.toInt(), 0xFF16161C.toInt(), 0xFF7EE0C0.toInt(), 0xFF4A88D8.toInt(), 0xFF101014.toInt(), 0xFF7EE0C0.toInt(), 3)   // 19 grafito neón
        )

        /** ¿cuántos diseños de portada puede elegir el usuario? */
        fun coverStyles() = SPECS.size

        // en modo HOMBRE: los rosados se cambian por equivalentes
        private val MAN_REMAP = intArrayOf(
            8, 1, 2, 3, 9, 5, 6, 7, 8, 9, 3, 11,
            15, 13, 14, 15, 14, 17, 18, 19
        )

        fun drawCover(canvas: Canvas, w: Float, h: Float, style: Int, seedLong: Long) {
            // En modo HOMBRE, los 3 estilos más rosados se cambian por equivalentes azules/oscuros
            val remap = if (Gender.isMan) MAN_REMAP else null
            val spec = SPECS[remap?.get(style % SPECS.size) ?: (style % SPECS.size)]
            val rnd = MyRnd(seedLong * 31 + style)
            val wpaint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Fondo
            val g = LinearGradient(0f, 0f, w, h, spec.baseA, spec.baseB, Shader.TileMode.CLAMP)
            wpaint.shader = g
            canvas.drawRoundRect(0f, 0f, w, h, w * 0.07f, w * 0.07f, wpaint)
            wpaint.shader = null

            // Lomo de sombra
            wpaint.color = Color.argb(40, 0, 0, 0)
            canvas.drawRect(w - w * 0.035f, 0f, w, h, wpaint)

            // Blobs orgánicos estilo acuarela
            when (spec.kind % 3) {
                0 -> {
                    wpaint.shader = RadialGradient(w * 0.32f, h * 0.34f, w * 0.55f, spec.blob2, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    canvas.drawCircle(w * 0.3f, h * 0.3f, w * 0.42f, wpaint)
                    wpaint.shader = null
                    wpaint.color = Color.argb(95, (spec.blob shr 16) and 0xFF, (spec.blob shr 8) and 0xFF, spec.blob and 0xFF)
                    canvas.drawPath(blobPath(rnd, w * 0.68f, h * 0.3f, w * 0.3f), wpaint)
                    wpaint.color = Color.argb(90, (spec.blob2 shr 16) and 0xFF, (spec.blob2 shr 8) and 0xFF, spec.blob2 and 0xFF)
                    canvas.drawPath(blobPath(rnd, w * 0.45f, h * 0.78f, w * 0.34f), wpaint)
                    canvas.drawPath(blobPath(rnd, w * 0.8f, h * 0.72f, w * 0.2f), wpaint)
                }
                1 -> {
                    wpaint.color = Color.argb(120, (spec.blob shr 16) and 0xFF, (spec.blob shr 8) and 0xFF, spec.blob and 0xFF)
                    canvas.drawPath(blobPath(rnd, w * 0.62f, h * 0.42f, w * 0.46f), wpaint)
                    wpaint.color = Color.argb(140, (spec.blob2 shr 16) and 0xFF, (spec.blob2 shr 8) and 0xFF, spec.blob2 and 0xFF)
                    canvas.drawPath(blobPath(rnd, w * 0.7f, h * 0.8f, w * 0.3f), wpaint)
                    // líneas doradas finas
                    wpaint.color = spec.accent
                    wpaint.style = Paint.Style.STROKE
                    wpaint.strokeWidth = w * 0.012f
                    for (i in 0..2) {
                        val p = Canvas2.wavyLine(rnd, w * (0.2f + i * 0.13f), w * 0.95f, h * (0.5f + i * 0.13f), w * 0.07f)
                        wpaint.style = Paint.Style.STROKE
                        canvas.drawPath(p, wpaint)
                    }
                    wpaint.style = Paint.Style.FILL
                }
                else -> {
                    wpaint.color = Color.argb(178, (spec.blob shr 16) and 0xFF, (spec.blob shr 8) and 0xFF, spec.blob and 0xFF)
                    canvas.drawPath(blobPath(rnd, w * 0.66f, h * 0.36f, w * 0.34f), wpaint)
                }
            }

            // Anillado / espiral / banda lateral
            drawBinding(canvas, w, h, spec, rnd)
        }

        private fun drawBinding(canvas: Canvas, w: Float, h: Float, spec: StyleSpec, rnd: MyRnd) {
            val p = paint(spec.binding)
            when (spec.kind) {
                1 -> { // espiral vertical (izquierda)
                    val n = 11
                    val seg = h / (n + 0.4f)
                    val x = w * 0.1f
                    loop@ for (i in 0 until n) {
                        val y = seg * (i + 0.6f)
                        p.color = Color.argb(230, 250, 245, 235)
                        canvas.drawRoundRect(x - w * 0.022f, y, x + w * 0.022f, y + seg * 0.66f, w * 0.02f, w * 0.02f, p)
                    }
                }
                2 -> { // anillado abierto (arriba y abajo como libreta)
                    val n = 6
                    val seg = h / (n + 1f)
                    for (i in 0 until n) {
                        val y = seg * (i + 0.8f)
                        p.color = Color.argb(228, 252, 250, 245)
                        canvas.drawRoundRect(w * 0.05f, y, w * 0.13f, y + seg * 0.6f, w * 0.02f, w * 0.02f, p)
                    }
                    // broche elástico derecho
                    p.color = Color.argb(200, (spec.accent shr 16) and 0xFF, (spec.accent shr 8) and 0xFF, spec.accent and 0xFF)
                    canvas.drawRoundRect(w * 0.9f, h * 0.04f, w * 0.94f, h * 0.96f, w * 0.02f, w * 0.02f, p)
                }
                3 -> { // banda horizontal tipo cuaderno
                    p.color = Color.argb(255, (spec.accent shr 16) and 0xFF, (spec.accent shr 8) and 0xFF, spec.accent and 0xFF)
                    canvas.drawRoundRect(w * 0.04f, h * 0.06f, w * 0.065f, h * 0.94f, w * 0.012f, w * 0.012f, p)
                    // broche página
                    p.color = Color.argb(240, 255, 255, 255)
                    canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.1f, p)
                    p.color = Color.argb(70, 0, 0, 0)
                    val rp = paint(Color.argb(60, 0, 0, 0))
                    canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.1f, rp)
                    // correa/clip
                    p.color = Color.argb(235, (spec.accent shr 16) and 0xFF, (spec.accent shr 8) and 0xFF, spec.accent and 0xFF)
                    canvas.drawRoundRect(w * 0.475f, h * 0.42f, w * 0.525f, h * 0.58f, w * 0.02f, w * 0.02f, p)
                }
                else -> { // bolsillo suave superior
                    p.color = Color.argb(60, 255, 255, 255)
                    canvas.drawRoundRect(w * 0.08f, h * 0.05f, w * 0.92f, h * 0.35f, w * 0.05f, w * 0.05f, p)
                    // clip metálico
                    p.color = Color.argb(150, 255, 255, 255)
                    canvas.drawRoundRect(w * 0.42f, h * 0.2f, w * 0.58f, h * 0.3f, w * 0.02f, w * 0.02f, p)
                }
            }
            paintPass(canvas, spec, rnd)
        }

        private fun paintPass(canvas: Canvas, spec: StyleSpec, rnd: MyRnd) {}

        /* ---------- PORTADA CON FOTO del usuario ---------- */
        private val imgCache = object : LinkedHashMap<String, android.graphics.Bitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, android.graphics.Bitmap>?): Boolean {
                // la galería puede mostrar muchas portadas: no acumulamos mapa infinito
                return size > 48
            }
        }

        fun getCoverBitmap(path: String): android.graphics.Bitmap? {
            val cached = imgCache[path]
            if (cached != null && !cached.isRecycled) return cached
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            val longest = maxOf(opts.outWidth, opts.outHeight)
            if (longest > 0) while (longest / (sample * 2) >= 480) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            imgCache[path] = bmp
            return bmp
        }

        /** Llena el área con la FOTO (recorte central estilo conjunto) + borde blanco */
        fun drawImageCover(canvas: Canvas, w: Float, h: Float, path: String): Boolean {
            val bmp = getCoverBitmap(path) ?: return false
            val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val s = maxOf(w / bmp.width, h / bmp.height)
            val dw = bmp.width * s; val dh = bmp.height * s
            val ox = (w - dw) / 2f; val oy = (h - dh) / 2f
            canvas.save()
            val clip = Path()
            clip.addRoundRect(RectF(0f, 0f, w, h), w * 0.07f, w * 0.07f, Path.Direction.CW)
            canvas.clipPath(clip)
            canvas.drawRect(0f, 0f, w, h, paint(0xFFFFFFFF.toInt()))
            canvas.drawBitmap(bmp, null, RectF(ox, oy, ox + dw, oy + dh), p)
            // borde blanco fino: se VE como un polaroid de portada
            p.style = Paint.Style.STROKE
            p.color = 0xCCFFFFFF.toInt()
            p.strokeWidth = maxOf(1.5f, w * 0.014f)
            canvas.drawRoundRect(0f, 0f, w, h, w * 0.07f, w * 0.07f, p)
            // espiral blanca arriba (coherencia con los anillados del diario)
            p.style = Paint.Style.FILL
            p.color = 0xB8FFFFFF.toInt()
            val n = 7
            val seg = h * 0.16f / (n + 0.4f)
            for (i in 0 until n) {
                val y = h * 0.035f + i * seg * 1.7f
                canvas.drawRoundRect(w * 0.09f, y, w * 0.155f, y + seg * 2.4f, w * 0.02f, w * 0.02f, p)
            }
            canvas.restore()
            return true
        }

        /** Helpers compartidos con el editor */
        object Canvas2 {
            class Pt2(val x: Float, val y: Float)

            fun smoothClosed(pts: List<Pt2>): Path {
                val p = Path()
                if (pts.isEmpty()) return p
                val n = pts.size
                val m = Matrix()
                p.moveTo((pts[0].x + pts[n - 1].x) / 2f, (pts[0].y + pts[n - 1].y) / 2f)
                for (i in 0 until n) {
                    val a = pts[i]
                    val b = pts[(i + 1) % n]
                    p.quadTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
                }
                p.close()
                return p
            }

            fun wavyLine(rnd: MyRnd, x1: Float, x2: Float, yMid: Float, amp: Float): Path {
                val p = Path()
                p.moveTo(x1, yMid)
                val steps = 8
                var x = x1
                for (i in 0 until steps) {
                    val nx = x + (x2 - x1) / steps
                    p.quadTo(x + (nx - x) / 2f, yMid + amp * (rnd.next() - 0.5f) * 2f, nx, yMid + amp * (rnd.next() - 0.5f) * 2f)
                    x = nx
                }
                return p
            }
        }
    }
}
