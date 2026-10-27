package com.knotes.app.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.knotes.app.data.AuxRectF
import com.knotes.app.data.Brush
import com.knotes.app.data.ImageElement
import com.knotes.app.data.KPage
import com.knotes.app.data.LinkElement
import com.knotes.app.data.Notebook
import com.knotes.app.data.PaperElement
import com.knotes.app.data.Pt
import com.knotes.app.data.Shape
import com.knotes.app.data.ShapeElement
import com.knotes.app.data.StickerElement
import com.knotes.app.data.Stroke
import com.knotes.app.data.TextElement
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/* ============================================================
   CanvasPageView — papel interactivo
   · Cada trazo es un objeto vectorial => tras guardar y volver,
     puede borrarse (goma por zona o por trazo) o moverse (lazo).
   · Gestos: 1 dedo dibuja/usar herramienta; 2 dedos zoom+pan.
   ============================================================ */

    object Tools {
        const val PEN = 0
        const val MARKER = 1
        const val PENCIL = 2
        const val HIGHLIGHTER = 3
        const val ERASER = 4
        const val LASSO = 5
        const val TEXT = 6
        const val IMAGE = 7
        const val LINK = 8
        const val STICKER = 9
        const val SELECT = 10
        const val DOTTED = 11   // pincel de PUNTITOS
        const val NEON = 12     // pincel BRILLADO neón
        const val SHAPE = 13    // añadir FORMAS
        const val RULER = 14    // REGLA
        const val BEVEL = 15    // pincel 3D BISELADO (dorado con volumen)
        const val HILITER = 16  // RESALTADOR grueso (lima)
        const val PEARL = 17    // 3D PERLA (degradado pastel con volumen)
    }

interface CanvasHost {
    fun onPageChanged(idx: Int, count: Int)
    fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean)
    fun onSelectionChanged(hasSel: Boolean)
    fun editText(el: TextElement?, pt: Pt)
    fun pickImage()
    fun linkAt(el: LinkElement?, pt: Pt)
    fun openStickers()
    fun saveNow()
    /** dos dedos deslizaron: dir 0 = adentro (página siguiente), 1 = afuera (anterior) */
    fun onPageSwipe(dir: Int) {}
}

class CanvasPageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var host: CanvasHost? = null

    /* ---------- página actual ---------- */
    lateinit var notebook: Notebook
    var pages: MutableList<KPage> = ArrayList()
    var pageIndex = 0
        private set

    val curPage: KPage get() = pages[pageIndex]
    val pageItems: MutableList<PaperElement> get() = curPage.items

    /* ---------- configuración herramienta ---------- */
    var tool = Tools.PEN
    var penColor = 0xFF2B2B2B.toInt()
    var penWidth = 5f
    var brushType = Brush.PEN
    var eraserWhole = false          // false: borra por zona (partes del trazo)
    var eraserRadius = 18f           // en unidades de papel
    var stickerPending: String? = null
    var shapeKind = Shape.RECT       // tipo de forma activo (elige y dibuja arrastrando)
    var rulerOn = false              // regla visible + trenza trazos cercanos
    var scrollDir = 1                // 0 vertical, 1 horizontal (dos dedos cambian de página)
    var shapePending: Boolean = false

    /** lista de botones-lápiz que comparten el botón 🖊 del lápiz */
    fun isPenTool(t: Int) = t in intArrayOf(Tools.PEN, Tools.MARKER, Tools.PENCIL, Tools.HIGHLIGHTER, Tools.DOTTED, Tools.NEON, Tools.BEVEL, Tools.HILITER, Tools.PEARL)

    /* ---------- transformación ---------- */
    companion object {
        const val PAPER_W = 1000f
        const val PAPER_H = 1414f
        val PAPER_COLORS = intArrayOf(
            0xFFFBF7ED.toInt(),  // 0 crema
            0xFFFFFFFF.toInt(),  // 1 blanco
            0xFF2E2A33.toInt(),  // 2 oscuro
            0xFFFCEDF1.toInt(),  // 3 rosa pastel
            0xFFEAF6EE.toInt(),  // 4 menta
            0xFFF1ECFA.toInt(),  // 5 lavanda
            0xFFE8F1FA.toInt(),  // 6 celeste
            0xFFFFFFFF.toInt()   // 7 = la PORTADA del diario se dibuja encima
        )
        val TEMPLATES = arrayOf(
            "Rayado", "Cuadrícula", "Puntos", "En blanco", "Rayado fino",
            "Cuadrícula fina", "Puntos finos", "Con margen", "Dos columnas", "Agenda"
        )
        fun tplCount() = TEMPLATES.size

        /* ====== PLANTILLAS REALES: páginas que VIENEN con contenido diseñado ======
           Título, casillas, columnas y renglones ya puestos (todo editable:
           el título es texto, las casillas son formas, como todo lo demás). */
        val REAL_TPL_NAMES = arrayOf(
            "🌱 Hábitos de la semana", "🕐 Agenda del día", "✅ Pendientes de hoy",
            "🧮 Presupuesto del mes", "🍰 Receta", "📅 Mi semana", "🧠 Apuntes Cornell",
            "😊 ¿Cómo me siento hoy?", "🍎 Menú de la semana", "📖 Reseña de libro"
        )
        fun realTplCount() = REAL_TPL_NAMES.size

        const val INK = 0xFF4A4448.toInt()
        const val SOFT = 0xFF8A8378.toInt()
        const val RULE = 0xFFC7C1B2.toInt()
        const val PINKY = 0xFFEE5F8B.toInt()
        const val BLU = 0xFF4A90D9.toInt()

        private fun rtText(x: Int, y: Int, s: String, size: Int, color: Int = INK) =
            TextElement(Geo.newId(), x.toFloat(), y.toFloat(), s, size.toFloat(), color)
        private fun rtLine(x: Int, y: Int, dx: Int, dy: Int, color: Int = RULE, lw: Float = 2.4f) =
            ShapeElement(Geo.newId(), Shape.LINE, x.toFloat(), y.toFloat(), max(dx, 0).toFloat().coerceAtLeast(0.01f), max(dy, 0).toFloat().coerceAtLeast(0.01f), color, lw)
        private fun rtBox(x: Int, y: Int, bw: Int, bh: Int, color: Int = SOFT, lw: Float = 2.8f) =
            ShapeElement(Geo.newId(), Shape.RECT, x.toFloat(), y.toFloat(), bw.toFloat(), bh.toFloat(), color, lw)

        /** Construye los ELEMENTOS de la plantilla real n (0-based). */
        fun buildRealTemplate(n: Int): List<PaperElement> {
            val out = ArrayList<PaperElement>()
            val dias = arrayOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
            when (n.coerceIn(0, REAL_TPL_NAMES.size - 1)) {
                0 -> { // HÁBITOS DE LA SEMANA
                    out.add(rtText(56, 96, "Hábitos de la semana", 46))
                    out.add(rtText(56, 158, "Semana del ______________", 28, SOFT))
                    out.add(rtLine(56, 186, 888, 0, PINKY, 3.4f))
                    for (i in 0..6) out.add(rtText(516 + i * 70, 242, "LMXJVSD"[i].toString(), 28, BLU))
                    for (r in 0..7) {
                        val yy = 316 + r * 92
                        out.add(rtText(56, yy + 8, "Hábito ________", 26, SOFT))
                        out.add(rtLine(506, yy, 388, 0))
                        for (i in 0..6) out.add(rtBox(522 + i * 70, yy - 46, 34, 34, SOFT, 2.8f))   // casilla SOBRE la línea (no la cruza)
                    }
                    out.add(rtLine(498, 206, 0, 1014, PINKY, 2.2f))
                }
                1 -> { // AGENDA DEL DÍA
                    out.add(rtText(56, 96, "Agenda del día", 46))
                    out.add(rtText(56, 156, "Fecha: ______________ · Lo más importante:", 26, SOFT))
                    out.add(rtLine(56, 184, 888, 0, PINKY, 3.4f))
                    out.add(rtBox(620, 210, 324, 104, SOFT, 2.8f))
                    out.add(rtText(640, 252, "_____________", 32, BLU))
                    out.add(rtText(640, 296, "(prioridad del día)", 22, SOFT))
                    for (h in 0..9) {
                        val yy = 380 + h * 74
                        out.add(rtText(58, yy + 8, "${7 + h}:00", 24, SOFT))
                        out.add(rtLine(150, yy, 400, 0))
                    }
                    out.add(rtBox(56, 1148, 888, 210, SOFT, 2.8f))
                    out.add(rtText(76, 1204, "Notas del día ✓", 28, PINKY))
                    out.add(rtLine(76, 1268, 840, 0))
                    out.add(rtLine(76, 1312, 840, 0))
                }
                2 -> { // PENDIENTES DE HOY
                    out.add(rtText(56, 96, "Pendientes de hoy", 46))
                    out.add(rtText(56, 154, "Fecha: ____________", 26, SOFT))
                    out.add(rtLine(56, 182, 888, 0, PINKY, 3.4f))
                    for (i in 0..9) {
                        val yy = 250 + i * 92
                        out.add(rtBox(60, yy - 30, 42, 42, SOFT, 2.8f))
                        out.add(rtLine(130, yy, 520, 0))
                    }
                    out.add(rtBox(56, 1214, 888, 144, SOFT, 2.8f))
                    out.add(rtText(76, 1262, "Lo dejaré para mañana si no alcanza:", 24, SOFT))
                    out.add(rtLine(76, 1310, 840, 0))
                }
                3 -> { // PRESUPUESTO
                    out.add(rtText(56, 96, "Presupuesto del mes", 44))
                    out.add(rtText(56, 152, "Mes: ____________ Dinero inicial: ____________", 26, SOFT))
                    out.add(rtLine(56, 184, 888, 0, PINKY, 3.4f))
                    out.add(rtText(76, 240, "Concepto", 28, PINKY))
                    out.add(rtText(490, 240, "Monto", 28, PINKY))
                    out.add(rtText(770, 240, "¿Listo?", 26, PINKY))
                    for (r in 0..9) {
                        val yy = 300 + r * 88
                        out.add(rtLine(56, yy, 888, 0))
                        out.add(rtLine(470, yy - 72, 0, 88))
                        out.add(rtLine(756, yy - 72, 0, 88))
                        out.add(rtBox(792, yy - 54, 32, 32, SOFT, 2.6f))   // casilla sobre la línea de la fila
                    }
                    out.add(rtLine(470, 212, 0, 668))
                    out.add(rtLine(756, 212, 0, 668))
                    out.add(rtLine(56, 1196, 888, 0, PINKY, 3f))
                    out.add(rtText(56, 1268, "TOTAL: ______________", 34, BLU))
                    out.add(rtText(56, 1326, "Ahorré: ____________  Lo que aprendí: ______________________", 22, SOFT))
                }
                4 -> { // RECETA
                    out.add(rtText(56, 96, "Receta: ______________", 44))
                    out.add(rtText(56, 152, "Para ____ personas · Tiempo: ______ · Horno: ____ °C", 24, SOFT))
                    out.add(rtLine(56, 184, 888, 0, PINKY, 3.4f))
                    out.add(rtText(56, 246, "🥣 Ingredientes", 32, PINKY))
                    for (r in 0..7) {
                        val yy = 306 + r * 64
                        out.add(rtLine(70, yy, 350, 0))
                        out.add(rtBox(56, yy - 16, 10, 10, SOFT, 1.8f))
                    }
                    out.add(rtText(480, 246, "👨‍🍳 Preparación", 32, PINKY))
                    for (r in 0..9) {
                        val yy = 306 + r * 66
                        out.add(rtText(486, yy, "${r + 1}.", 26, BLU))
                        out.add(rtLine(534, yy, 392, 0))
                    }
                }
                5 -> { // MI SEMANA
                    out.add(rtText(56, 96, "Mi semana", 46))
                    out.add(rtText(56, 152, "Objetivo de la semana: ____________________", 26, SOFT))
                    out.add(rtLine(56, 184, 888, 0, PINKY, 3.4f))
                    for (r in dias.indices) {
                        val yy = 268 + r * 158
                        out.add(rtText(56, yy, dias[r], 30, BLU))
                        out.add(rtLine(236, yy, 708, 0))          // línea de escribir a la altura del nombre del día (no lo cruza)
                        out.add(rtLine(56, yy + 44, 888, 0))
                    }
                }
                6 -> { // APUNTES CORNELL
                    out.add(rtText(56, 92, "Apuntes de ____________", 42))
                    out.add(rtText(56, 142, "Tema: ____________ · Fecha: ____________", 24, SOFT))
                    out.add(rtLine(56, 172, 888, 0, PINKY, 3.4f))
                    out.add(rtLine(300, 196, 0, 900, BLU, 2.2f))
                    out.add(rtText(70, 244, "Preguntas 🧠", 26, PINKY))
                    out.add(rtText(330, 244, "Apuntes 📝", 26, BLU))
                    for (r in 0..7) {
                        val yy = 308 + r * 92
                        out.add(rtLine(330, yy, 614, 0))
                        out.add(rtLine(70, yy + 8, 210, 0))
                    }
                    out.add(rtLine(56, 1136, 888, 0, BLU, 3f))
                    out.add(rtText(70, 1200, "✍ Resumen (al final de la clase)", 28, PINKY))
                    out.add(rtLine(70, 1256, 830, 0))
                    out.add(rtLine(70, 1310, 830, 0))
                }
                7 -> { // ¿CÓMO ME SIENTO HOY?
                    val caras = arrayOf("😢", "🙁", "😐", "🙂", "😄")
                    out.add(rtText(56, 96, "¿Cómo me siento hoy?", 42))
                    out.add(rtText(56, 152, "Encierra o pinta tu cara de hoy:", 24, SOFT))
                    out.add(rtLine(56, 184, 888, 0, PINKY, 3.4f))
                    for (i in caras.indices) {
                        out.add(rtText(110 + i * 168, 330, caras[i], 64))
                        out.add(rrBox(126 + i * 168, 360, 110, 110, SOFT))
                    }
                    out.add(rtText(56, 560, "Lo que pasó hoy:", 28, SOFT))
                    rrDias(out)
                    for (r in 0..7) {
                        val yy = 636 + r * 76
                        out.add(rtLine(56, yy, 844, 0))
                    }
                    out.add(rtText(56, 1290, "3 cosas buenas que pasaron:", 26, PINKY))
                    out.add(rtLine(56, 1330, 844, 0))
                }
                8 -> { // MENÚ DE LA SEMANA
                    out.add(rtText(56, 96, "Menú de la semana", 46))
                    out.add(rtText(56, 152, "lista del mercado al final ↓", 24, SOFT))
                    out.add(rtLine(56, 184, 888, 0, PINKY, 3.4f))
                    out.add(rtText(76, 236, "Comida 🍽", 28, BLU))
                    out.add(rtText(520, 236, "Postre 🍨", 28, BLU))
                    for (r in dias.indices) {
                        val yy = 300 + r * 138
                        out.add(rtText(56, yy, dias[r], 28, BLU))
                        out.add(rtLine(214, yy, 222, 0))         // comida (empieza DESPUÉS del nombre del día)
                        out.add(rtLine(510, yy, 276, 0))         // postre
                        out.add(rtLine(490, yy - 46, 0, 118))
                        out.add(rtLine(56, yy + 56, 888, 0))
                    }
                    out.add(rtBox(56, 1290, 888, 90, SOFT, 2.8f))
                    out.add(rtText(76, 1332, "🛒 Mercado: ______________________________", 26, SOFT))
                }
                else -> { // RESEÑA DE LIBRO
                    out.add(rtText(56, 92, "Reseña de libro", 44))
                    out.add(rtText(56, 144, "📖 Título: ______________________", 26, SOFT))
                    out.add(rtText(56, 192, "✍ Autor: ______________ · Género: ____________", 24, SOFT))
                    out.add(rtText(56, 262, "Calificación: ★ ★ ★ ★ ★", 36, PINKY))
                    out.add(rtLine(56, 292, 888, 0, PINKY, 3.4f))
                    out.add(rtText(56, 352, "Lo que me gustó ♥", 30, PINKY))
                    for (r in 0..5) out.add(rtLine(70, 416 + r * 66, 800, 0))
                    out.add(rtText(56, 900, "Lo que no me gustó ✕", 30, BLU))
                    for (r in 0..2) out.add(rtLine(70, 964 + r * 66, 800, 0))
                    out.add(rtText(56, 1196, "Mi parte favorita:", 28, PINKY))
                    out.add(rtLine(70, 1252, 800, 0))
                    out.add(rtLine(70, 1306, 800, 0))
                }
            }
            return out
        }
        private fun rrBox(x: Int, y: Int, bw: Int, bh: Int, color: Int) =
            ShapeElement(Geo.newId(), Shape.OVAL, x.toFloat(), y.toFloat(), bw.toFloat(), bh.toFloat(), color, 2.8f)
        private fun rrDias(out: ArrayList<PaperElement>) {
            out.add(rtText(56, 610, "Semana del ____________", 22, SOFT))
        }
    }

    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private var vmW = 1000f
    private var vmH = 1414f

    /* ---------- estado de dibujo ---------- */
    private val MODE_IDLE = 0
    private val MODE_DRAW = 1
    private val MODE_ERASE = 2
    private val MODE_LASSO = 3
    private val MODE_MOVESEL = 4
    private val MODE_SCALEEL = 5
    private val MODE_GEST = 6
    private val MODE_SHAPE = 7          // arrastrando una forma
    private val MODE_RULER_MOVE = 8     // moviendo la regla
    private val MODE_RULER_ROT = 9      // girando la regla
    private val MODE_ROTEL = 10         // girando lo seleccionado (manija ⟳)

    private var liveStroke: Stroke? = null
    private var liveShape: ShapeElement? = null
    private var mode = MODE_IDLE

    private val lasso = ArrayList<Pt>()
    private val selection = ArrayList<PaperElement>()
    private var lastSelected: PaperElement? = null
    private var downPt = PointF()
    private var movedFar = false
    private var scaleAnchor = RectF()

    /* ---------- historial ---------- */
    private val undoStack = ArrayList<HEntry>()
    private val redoStack = ArrayList<HEntry>()

    private class HEntry(val pageIdx: Int, val snapshot: List<PaperElement>)

    /* ---------- caché de páginas activas no necesaria: se recarga por elemento ---------- */

    /* ================== setup ================== */

    fun setup(nb: Notebook, pages: MutableList<KPage>, startIndex: Int) {
        this.notebook = nb
        this.pages = pages
        isSetup = true
        pageIndex = startIndex.coerceIn(0, pages.size - 1)
        undoStack.clear(); redoStack.clear()
        selection.clear(); lasso.clear()
        if (width > 0) fitPage() else requestLayout()
        invalidate()
        host?.onPageChanged(pageIndex, pages.size)
        host?.onHistoryChanged(false, false)
    }

    private fun fitPage() {
        vmW = width.toFloat(); vmH = height.toFloat()
        if (vmW <= 0) return
        scale = fitPad()
        tx = (vmW - PAPER_W * scale) / 2f
        ty = (vmH - PAPER_H * scale) / 2f
        invalidate()
    }

    /** vista inicial/página completa CON margen (la misma del arranque) */
    private fun fitPad() = min(vmW * 0.97f / PAPER_W, vmH * 0.93f / PAPER_H)

    /** Chip «Acercar/Alejar» de la barra inferior: alterna la vista completa
     *  con un zoom del 230 % para escribir a gusto. Devuelve true si quedó ACERCADO. */
    fun toggleZoom(): Boolean {
        val fit = fitPad()
        return if (vmW <= 0) false
        else if (scale > fit * 1.15f) { zoomTo(fit); false } else { zoomTo(fit * 2.3f); true }
    }

    fun zoomTo(target: Float) {
        if (vmW <= 0) return
        val tgt = target.coerceIn(fitPad(), 4f)
        val from = scale
        // zoom centrado en lo que el usuario ve ahora
        val cx = vmW / 2f; val cy = vmH / 2f
        val px = paperX(cx); val py = paperY(cy)
        val anim = android.animation.ValueAnimator.ofFloat(from, tgt)
        anim.duration = 230
        anim.addUpdateListener {
            val s = it.animatedValue as Float
            scale = s
            tx = cx - px * scale
            ty = cy - py * scale
            invalidate()
        }
        anim.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        vmW = w.toFloat(); vmH = h.toFloat()
        fitPage()
    }

    /* ================== coords ================== */
    private fun paperX(sx: Float) = (sx - tx) / scale
    private fun paperY(sy: Float) = (sy - ty) / scale

    /* ================== PLANTILLAS REALES (contenido diseñado) ================== */

    /** Pone el CONTENIDO de una plantilla real (1..10) en la página ACUTAL:
     *  reemplaza los elementos por el diseño listo (todo es editable igual). */
    fun applyRealTemplate(realOneBased: Int) {
        if (realOneBased <= 0) return
        record()
        // hoja LIMPIA y sin adornos de esquina: así el diseño real no choca con renglones ni dibujos
        curPage.template = 3
        if (curPage.paper != 2) curPage.paper = 1
        curPage.decor = 0
        pageItems.clear()
        pageItems.addAll(buildRealTemplate(realOneBased - 1))
        clearSelection()
        invalidate()
        host?.onHistoryChanged(true, false)
    }

    /* ================== historial ================== */

    private fun record() {
        undoStack.add(HEntry(pageIndex, pageItems.map { it.clone() }))
        if (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
        host?.onHistoryChanged(true, false)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val e = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(HEntry(e.pageIdx, pages[e.pageIdx].items.toList()))
        pages[e.pageIdx].items.apply { clear(); addAll(e.snapshot) }
        clearSelection()
        invalidate()
        host?.onHistoryChanged(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val e = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(HEntry(e.pageIdx, pages[e.pageIdx].items.toList()))
        pages[e.pageIdx].items.apply { clear(); addAll(e.snapshot) }
        clearSelection()
        invalidate()
        host?.onHistoryChanged(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }

    /* ================== páginas ================== */

    fun goToPage(i: Int) {
        if (i < 0 || i >= pages.size || i == pageIndex) return
        pageIndex = i
        clearSelection()
        fitPage()
        host?.onPageChanged(pageIndex, pages.size)
    }

    fun addPage(template: Int, decor: Int = 0, paper: Int = -1) {
        pages.add(KPage(Geo.newId(), template, if (paper >= 0) paper else curPage.paper, decor))
        goToPage(pages.size - 1)
    }

    fun deleteCurrentPage(): Boolean {
        if (pages.size <= 1) return false
        pages.removeAt(pageIndex)
        goToPage(if (pageIndex >= pages.size) pages.size - 1 else pageIndex)
        return true
    }

    /** Duplica la página ACTUAL con todo su contenido justo después (nueva hoja gemela) */
    fun duplicatePage() {
        val src = curPage
        val copy = KPage(Geo.newId(), src.template, src.paper, src.decor, ArrayList(src.items.map { el -> el.clone() }))
        pages.add(pageIndex + 1, copy)
        goToPage(pageIndex + 1)
    }

    fun setTemplate(t: Int) {
        curPage.template = t
        invalidate()
    }

    /** color/patrón de papel de la página actual (0..6 colores, 7 = portada del diario) */
    fun setPaperColor(idx: Int) {
        curPage.paper = idx.coerceIn(0, PAPER_COLORS.size - 1)
        invalidate()
    }

    /** adorno de esquinas del papel actual (ver canvas/Decors) */
    fun setPageDecor(d: Int) {
        curPage.decor = d.coerceIn(0, 13)
        invalidate()
    }

    fun curDecor(): Int = curPage.decor

    fun clearPage() {
        record()
        pageItems.clear()
        clearSelection()
        invalidate()
    }

    /* ================== acciones de escritorio ================== */

    fun addStickerAtPaper(emoji: String, x: Float, y: Float) {
        record()
        val size = 90f
        // el sticker SE MANTIENE dentro del papel (el límite es el papel)
        val cx = x.coerceIn(size * 0.45f, PAPER_W - size * 0.75f)
        val cy = y.coerceIn(size * 0.45f, PAPER_H - size * 1.05f)
        pageItems.add(StickerElement(Geo.newId(), cx, cy, emoji, size))
        invalidate()
    }

    fun addImageElement(path: String, w: Float, h: Float) {
        record()
        // SIEMPRE en lo que el usuario ve ahora (centro del papel visible),
        // no en el centro absoluto de la página que podía quedar fuera de la vista
        val cx = paperX(width / 2f)
        val cy = paperY(height / 2f)
        val x = (cx - w / 2f).coerceIn(40f, (PAPER_W - w - 40f).coerceAtLeast(40f))
        val y = (cy - h / 2f).coerceIn(40f, (PAPER_H - h - 40f).coerceAtLeast(40f))
        pageItems.add(ImageElement(Geo.newId(), x, y, w, h, path))
        invalidate()
    }

    /** Página NUEVA en blanco + una imagen que ocupa el ANCHO del papel
     *  (así quedan las hojas de un PDF importado: una página por hoja del PDF) */
    fun addPdfPage(path: String, imgW: Float, imgH: Float) {
        addPage(3, 0, curPage.paper)          // hoja limpia con el papel actual
        val w = (PAPER_W - 60f).coerceAtMost(imgW.coerceAtLeast(1f))
        val h = (imgH * (w / imgW.coerceAtLeast(1f))).coerceIn(40f, PAPER_H - 60f)
        pageItems.add(ImageElement(Geo.newId(), (PAPER_W - w) / 2f, 30f, w, h, path))
        invalidate()
    }

    /** separación de líneas del papel según plantilla (para que el texto SIGA los renglones) */
    fun lineSpacingOf(tpl: Int): Float = when (tpl.coerceIn(0, 9)) {
        0 -> 76f; 1 -> 74f; 2 -> 80f; 3 -> 76f
        4 -> 58f; 5 -> 48f; 6 -> 48f; 7 -> 76f; 8 -> 64f; 9 -> 70f
        else -> 76f
    }
    fun lineStartOf(tpl: Int): Float = when (tpl.coerceIn(0, 9)) {
        0 -> 130f; 1 -> 60f; 2 -> 110f; 3 -> 130f
        4 -> 116f; 5 -> 52f; 6 -> 96f; 7 -> 130f; 8 -> 110f; 9 -> 210f
        else -> 130f
    }

    /** y del renglón más cercano al toque (la escritura SIGUE las líneas del papel) */
    fun snapLineY(y: Float): Float {
        val tpl = curPage.template
        val sp = lineSpacingOf(tpl)
        val base = lineStartOf(tpl)
        val row = Math.round((y - base) / sp)
        return (base + row * sp).coerceIn(40f, PAPER_H - 60f)
    }

    fun addTextElement(text: String, size: Float, color: Int, x: Float, y: Float, font: String = "") {
        record()
        // el textó EMPIEZA donde tocaste y su PRIMERA línea cae EXACTAMENTE
        // sobre el renglón del papel más cercano (todos por igual, sin salto arriba/abajo)
        val rowY = snapLineY(y)
        val el = TextElement(Geo.newId(), x.coerceIn(40f, PAPER_W - 80f), rowY, text, size, color)
        el.baseY = rowY
        el.cacheTpl = curPage.template
        el.font = font
        pageItems.add(el)
        invalidate()
    }

    fun replaceTextElement(el: TextElement, newText: String, newSize: Float, newColor: Int, newFont: String? = null) {
        el.text = newText; el.size = newSize; el.color = newColor
        if (newFont != null) el.font = newFont
        el.invalidatePathCache()
        invalidate()
    }

    fun addLinkElement(label: String, url: String, x: Float, y: Float) {
        record()
        pageItems.add(LinkElement(Geo.newId(), x, y, label, url,
            if (com.knotes.app.ui.Gender.isMan) 0xFF4A90D9.toInt() else 0xFFEE5F8B.toInt()))
        invalidate()
    }

    fun clearSelection() {
        selection.clear(); lastSelected = null; lasso.clear()
        host?.onSelectionChanged(false)
        invalidate()
    }

    fun deleteSelection() {
        if (selection.isEmpty()) return
        record()
        pageItems.removeAll(selection.toSet())
        clearSelection()
        invalidate()
    }

    fun deleteElement(el: PaperElement) {
        record()
        pageItems.remove(el)
        clearSelection()
        invalidate()
    }

    fun editSelected() {
        val el = lastSelected ?: return
        if (el is TextElement) host?.editText(el, Pt(el.x, el.y))
        else if (el is LinkElement) host?.linkAt(el, Pt(el.x, el.y))
    }

    fun selectedCount() = selection.size

    /* ================== goma ==================
       · UNA sola instantánea de deshacer POR ARRASTRÓN completo
         (borrar con fuerza ya no llena el historial hasta desbordarlo).
       · Opera por OBJETO, no por índice: nunca más crash por índices corridos.
       · ¡Borra de TODO!: trazos por zona/trazo y, si tocas un sticker,
         un texto, una imagen o un enlace, los quita directo. */
    private var erasedDirty = false

    private fun eraseRecord() {
        if (!erasedDirty) {
            record()
            erasedDirty = true
        }
    }

    private fun eraseAt(p: Pt) {
        val r = eraserRadius * 2.2f
        val remove = HashSet<PaperElement>()
        val splitOps = ArrayList<Pair<PaperElement, List<Stroke>>>()

        if (eraserWhole) {
            for (el in pageItems) {
                when (el) {
                    is Stroke -> if (Geo.distToStroke(p, el) < r + el.width) remove.add(el)
                    else -> if (hitBounds(el.bounds(), p.x, p.y, r * 0.7f)) remove.add(el)
                }
            }
        } else {
            // goma por zona: parte los trazos que toca el borrador
            for (el in pageItems) {
                when (el) {
                    is Stroke -> {
                        val out = ArrayList<MutableList<Pt>>()
                        var cur: MutableList<Pt>? = null
                        for (pt in el.points) {
                            val near = hypot((pt.x - p.x).toDouble(), (pt.y - p.y).toDouble()) < r
                            if (!near) {
                                if (cur == null) cur = ArrayList()
                                cur.add(Pt(pt.x, pt.y))
                            } else {
                                if (cur != null && cur.size > 1) out.add(cur)
                                cur = null
                            }
                        }
                        if (cur != null && cur.size > 1) out.add(cur)
                        val changed = out.size > 1 || (out.size == 1 && out[0].size != el.points.size) || out.isEmpty()
                        if (changed) {
                            val newStrokes = ArrayList<Stroke>()
                            for (run in out) newStrokes.add(Stroke(Geo.newId(), el.color, el.width, el.brush, run))
                            splitOps.add(el to newStrokes)
                        }
                    }
                    else -> if (hitBounds(el.bounds(), p.x, p.y, r * 0.7f)) remove.add(el)
                }
            }
        }
        if (remove.isEmpty() && splitOps.isEmpty()) return

        // snapshot ANTES de mutar (solo la primera vez de cada arrastrón)
        eraseRecord()

        // aplicar partiendo los índices-robusto: busco el índice del objeto en ese momento
        for ((old, replaces) in splitOps) {
            val i = pageItems.indexOf(old)
            if (i < 0) continue
            pageItems.removeAt(i)
            if (replaces.isNotEmpty()) pageItems.addAll(i, replaces)
        }
        if (remove.isNotEmpty()) pageItems.removeAll(remove)
        invalidate()
    }

    /* ================== toque ================== */

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // —— PALM REJECTION —— si en la sesión ya detectamos UN LÁPIZ/stylus,
        // los toques con DEDO/PALMA no dibujan (ni manchan); el dedo vuelve a
        // dibujar cuando NO haya habido ningún stylus tocando. El pinch-zoom
        // de dos dedos sigue funcionando con ambos escenarios.
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            for (i in 0 until e.pointerCount) {
                val t = e.getToolType(i)
                if (t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER) stylusSeen = true
            }
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downPt = PointF(e.x, e.y)
                movedFar = false
                val onlyTouch = stylusSeen && !isStylus(e, 0)
                if (onlyTouch && isPenTool(tool) || (onlyTouch && tool == Tools.ERASER)) {
                    mode = MODE_IDLE          // palma mientras escribes con lápiz: ignorar
                    return true
                }
                drawPtr = 0
                drawStylus = stylusSeen && isStylus(e, 0)
                handleDown(e.x, e.y, e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER,
                    if (drawStylus) e.pressure.coerceIn(0.03f, 1f) else 1f)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                if (i < e.pointerCount && isStylus(e, i)) {
                    // el STYLUS entra mientras un dedo estaba abajo → el dedo cede
                    liveStroke?.let { commitLiveStrokeCancelled() }
                    if (mode == MODE_LASSO) lasso.clear()
                    drawPtr = i
                    drawStylus = true
                    handleDown(e.getX(i), e.getY(i), e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER,
                        e.pressure.coerceIn(0.03f, 1f))
                    return true
                }
                if (e.pointerCount >= 2 && drawPtr < 0) {
                    liveStroke?.let { if (it.points.size > 12) commitLiveStroke() else commitLiveStrokeCancelled() }
                    wipeShape()
                    if (mode == MODE_LASSO) lasso.clear()
                    startGesture(e)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.actionIndex == drawPtr) {
                    handleUp(e.getX(e.actionIndex), e.getY(e.actionIndex))
                    drawPtr = -1
                    return true
                }
                if (mode == MODE_GEST && e.pointerCount - 1 < 2) mode = MODE_IDLE
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (drawPtr >= 0 && drawPtr < e.pointerCount) {
                    val pr = if (drawStylus) e.getPressure(drawPtr).coerceIn(0.03f, 1f) else 1f
                    // el sistema agrupa muestras entre fotogramas: se procesan TODAS (trazo más preciso)
                    if (mode == MODE_DRAW && drawStylus) {
                        for (hi in 0 until e.historySize) {
                            handleMove(e.getHistoricalX(drawPtr, hi), e.getHistoricalY(drawPtr, hi),
                                e.getHistoricalPressure(drawPtr, hi).coerceIn(0.03f, 1f))
                        }
                    }
                    if (mode != MODE_GEST) handleMove(e.getX(drawPtr), e.getY(drawPtr), pr)
                    return true
                }
                if (e.pointerCount >= 2 && mode != MODE_GEST) {
                    liveStroke?.let { commitLiveStroke() }
                    wipeShape()
                    if (mode == MODE_LASSO) lasso.clear()
                    startGesture(e)
                }
                if (mode == MODE_GEST) gestureMove(e)
                else handleMove(e.x, e.y, 1f)
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleUp(e.x, e.y)
                drawPtr = -1
                drawStylus = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (liveStroke != null) commitLiveStroke()
                mode = MODE_IDLE
                erasedDirty = false
                drawPtr = -1
                drawStylus = false
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    /* —— palm rejection helpers —— */
    private var stylusSeen = false   // ¿Ya vimos un lápiz en esta sesión del editor?
    private var drawPtr = -1         // índice del puntero que dibuja
    private var drawStylus = false   // el puntero que dibuja ES un stylus → usa su PRESIÓN (hasta 4096 niveles)
    private fun isStylus(e: MotionEvent, i: Int): Boolean {
        val t = e.getToolType(i)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun startGesture(e: MotionEvent) {
        mode = MODE_GEST
        gestDist0 = spacing(e); gestScale0 = scale
        gestMid0 = midPt(e); gest0x = tx; gest0y = ty
        gestFlipped = 0f
    }

    private var gestDist0 = 1f
    private var gestScale0 = 1f
    private var gestMid0 = PointF()
    private var gest0x = 0f
    private var gest0y = 0f
    private var gestFlipped = 0f   // desplazamiento del eje de página acumulado (dos dedos)

    private fun spacing(e: MotionEvent): Float = hypot(
        (e.getX(0) - e.getX(1)).toDouble(),
        (e.getY(0) - e.getY(1)).toDouble()
    ).toFloat().coerceAtLeast(1f)

    private fun midPt(e: MotionEvent) = PointF((e.getX(0) + e.getX(1)) / 2f, (e.getY(0) + e.getY(1)) / 2f)

    private fun gestureMove(e: MotionEvent) {
        val d = spacing(e)
        var s2 = gestScale0 * (d / gestDist0)
        s2 = min(3.2f, max(fitScale() * 0.9f, s2))
        val m = midPt(e)
        // ancla: el punto que estaba bajo el medio inicial se mantiene
        tx = m.x - ((gestMid0.x - gest0x) / gestScale0) * s2
        ty = m.y - ((gestMid0.y - gest0y) / gestScale0) * s2
        scale = s2
        clampMat()
        // —— CAMBIO DE PÁGINA con DOS DEDOS ——
        // si el pellizco casi no escala y el dedo colectivo va por el eje de la
        // dirección elegida (vertical/horizontal), se acumula y cambia de página.
        val flat = abs(s2 / gestScale0 - 1f) < 0.05f
        if (flat) {
            val delta = if (scrollDir == 0) (m.y - gestMid0.y) else (m.x - gestMid0.x)
            gestFlipped += delta
            val step = (if (scrollDir == 0) vmH else vmW) * 0.20f
            while (abs(gestFlipped) >= step) {
                host?.onPageSwipe(if (gestFlipped < 0f) 1 else 0)
                gestFlipped -= sign(gestFlipped) * step
            }
        }
        invalidate()
    }

    private fun fitScale() = min(vmW / PAPER_W, vmH / PAPER_H).coerceAtLeast(0.2f)

    private fun clampMat() {
        val s = scale
        tx = tx.coerceIn(70f - PAPER_W * s, vmW - 70f)
        ty = ty.coerceIn(90f - PAPER_H * s, vmH - 90f)
    }

    /* —— límites del papel: TODO pasa por aquí —— */
    private fun insidePaper(p: Pt): Boolean =
        p.x >= 0f && p.y >= 0f && p.x <= PAPER_W && p.y <= PAPER_H
    private fun clampPaper(p: Pt) = Pt(p.x.coerceIn(0f, PAPER_W), p.y.coerceIn(0f, PAPER_H))

    /* ------- pulsaciones simples ------- */
    private fun handleDown(sx: Float, sy: Float, forceErase: Boolean = false, pressure: Float = 1f) {
        val raw = Pt(paperX(sx), paperY(sy))
        // FUERA del papel no pasa NADA (el límite es el papel físico)
        if (!insidePaper(raw)) {
            mode = MODE_IDLE
            return
        }
        val p = clampPaper(raw)
        when (tool) {
            Tools.PEN, Tools.MARKER, Tools.PENCIL, Tools.HIGHLIGHTER, Tools.ERASER, Tools.DOTTED, Tools.NEON, Tools.BEVEL, Tools.HILITER, Tools.PEARL -> {
                if (forceErase) {                      // pUNTA trasera del stylus = goma
                    mode = MODE_ERASE; eraseAt(p); return
                }
                if (tool == Tools.ERASER) { mode = MODE_ERASE; eraseAt(p); return }
                mode = MODE_DRAW
                val w = baseWidthOf(tool)
                val c = if (tool == Tools.PENCIL) blendColor(penColor, 0f) else penColor
                val first = jitter(p, w)
                strokeSnapped = nearRuler(first)
                if (strokeSnapped) { val sp = snapToRuler(first); first.x = sp.x; first.y = sp.y } // PEGADO a la regla
                first.p = if (pressure != 1f) pressure else 1f
                liveStroke = Stroke(Geo.newId(), c, w, when (tool.toBrushType()) {
                    Brush.PEN -> Brush.PEN
                    else -> tool.toBrushType()
                }, ArrayList(first.let { listOf(it) }))
                invalidate()
            }
            Tools.SHAPE -> {
                // arrastrar para dibujar la forma; toque corto = forma mínima en el toque
                mode = MODE_SHAPE
                record()
                shapeOrigin = p.x to p.y
                liveShape = ShapeElement(Geo.newId(), shapeKind, p.x, p.y, 0f, 0f, penColor, max(2.2f, penWidth * 0.8f))
                liveShape?.w = 0.01f; liveShape?.h = 0.01f
                invalidate()
            }
            Tools.RULER -> {
                if (!rulerOn) { rulerOn = true; ensureRuler() }
                // ¿tocó la manija de GIRO (extremo derecho) o el cuerpo de la regla?
                if (hypot((p.x - rulerTip().x).toDouble(), (p.y - rulerTip().y).toDouble()) < 46f) {
                    mode = MODE_RULER_ROT
                } else if (distToRuler(p) < 60f) {
                    mode = MODE_RULER_MOVE
                    rulerOffX = rulerCx - p.x
                    rulerOffY = rulerCy - p.y
                } else mode = MODE_IDLE
                invalidate()
            }
            Tools.LASSO -> beginSelectionGesture(sx, p)
            Tools.SELECT -> beginSelectGesture(sx, p)
            Tools.TEXT -> host?.editText(textHit(p), p)
            Tools.STICKER -> {
                val s = stickerPending
                if (s == null) host?.openStickers()
                // queda EN MODO STICKER: tocar el papel cuantas veces se quiera;
                // se cambia/apaga manteniendo presionado el botón 🖊 del editor
                else addStickerAtPaper(s, p.x, p.y)
            }
            Tools.IMAGE -> host?.pickImage()
            Tools.LINK -> {
                val hit = linkHit(p)
                if (hit != null) host?.linkAt(hit, p) else host?.linkAt(null, p)
            }
            else -> {}
        }
    }

    /** grosor BASE del trazo según el modo de lápiz elegido */
    private fun baseWidthOf(t: Int): Float = when (t) {
        Tools.PEN -> penWidth * 0.8f          // bolígrafo: fino y preciso
        Tools.MARKER -> penWidth * 2.0f       // marcador: trazo firme y más grueso
        Tools.PENCIL -> penWidth * 0.9f       // lápiz: gris con grano
        Tools.HIGHLIGHTER -> penWidth * 1.0f  // subrayar: línea fina saturada
        Tools.DOTTED -> penWidth * 1.5f
        Tools.NEON -> penWidth * 2.0f
        Tools.BEVEL -> penWidth * 2.4f
        Tools.HILITER -> penWidth * 3.4f      // resaltador: ancho y translúcido
        Tools.PEARL -> penWidth * 2.6f        // perla 3D: cuerpo con volumen
        else -> penWidth
    }

    private fun wipeShape() { liveShape = null }

    private fun Int.toBrushType() = when (this) {
        Tools.PEN -> Brush.PEN
        Tools.MARKER -> Brush.MARKER
        Tools.PENCIL -> Brush.PENCIL
        Tools.HIGHLIGHTER -> Brush.HIGHLIGHTER
        Tools.DOTTED -> Brush.DOTTED
        Tools.NEON -> Brush.NEON
        Tools.BEVEL -> Brush.BEVEL
        Tools.HILITER -> Brush.HILITER
        Tools.PEARL -> Brush.PEARL
        else -> Brush.HIGHLIGHTER
    }

    private fun blendColor(c: Int, extra: Float) = c

    private fun jitter(p: Pt, w: Float): Pt {
        if (tool != Tools.PENCIL) return p
        val j = w * 0.16f          // grano SUTIL: antes descolocaba, ahora da textura sin feo
        p.x += Random.nextFloat() * j * 2 - j
        p.y += Random.nextFloat() * j * 2 - j
        return p
    }

    private fun handleMove(sx: Float, sy: Float, pressure: Float = 1f) {
        // el trazo se DETIENE en el borde del papel: jamás trasciende al margen
        val p = clampPaper(Pt(paperX(sx), paperY(sy)))
        if (abs(sx - downPt.x) + abs(sy - downPt.y) > 12f) movedFar = true
        when (mode) {
            MODE_DRAW -> {
                val s = liveStroke ?: return
                val last = s.points.lastOrNull() ?: p
                liveRaw = p
                if (abs(last.x - p.x) + abs(last.y - p.y) > 1.2f) {
                    // ESTABILIZADOR: la punta sigue al lápiz con una pizca de inercia → curvas redondas y
                    // elegantes (sin temblor de mano). El lápiz-grafito y los puntitos van crudos.
                    val smoothBrush = s.brush != Brush.PENCIL && s.brush != Brush.DOTTED && s.points.size >= 2
                    val bp = if (smoothBrush) Pt(last.x + (p.x - last.x) * 0.58f, last.y + (p.y - last.y) * 0.58f) else p
                    val q = jitter(bp, s.width)
                    // PRESIÓN: el lápiz digital (hasta 4096 niveles) manda el grosor del trazo;
                    // un toque fuerte se marca GRANDE, un roce suave se queda en línea delgada
                    // presión SUAVIZADA con el punto anterior (sin dientes de sierra)
                    q.p = if (pressure != 1f) (last.p * 0.55f + pressure * 0.45f) else 1f
                    if (strokeSnapped) { val sp = snapToRuler(q); q.x = sp.x; q.y = sp.y }
                    s.points.add(q)
                    postInvalidateOnAnimation()
                }
            }
            MODE_ERASE -> eraseAt(p)
            MODE_LASSO -> { lasso.add(p); invalidate() }
            MODE_MOVESEL -> moveSelectionBy(p.x - prevPt.x, p.y - prevPt.y)
            MODE_SCALEEL -> scaleSelection(p)
            MODE_ROTEL -> {
                val el = a ?: return
                val b = selectionBounds() ?: return
                val cx = (b.left + b.right) / 2f; val cy = (b.top + b.bottom) / 2f
                val now = Math.toDegrees(
                    kotlin.math.atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()
                el.rot = rotStartVal + (now - rotStartAng)
                el.invalidatePathCache()
                invalidate()
            }
            MODE_SHAPE -> {
                val sh = liveShape ?: return
                val (x0, y0) = shapeOrigin
                sh.x = min(x0, p.x); sh.y = min(y0, p.y)
                sh.w = abs(p.x - x0).coerceAtLeast(2f); sh.h = abs(p.y - y0).coerceAtLeast(2f)
                invalidate()
            }
            MODE_RULER_MOVE -> {
                rulerCx = (p.x + rulerOffX).coerceIn(-200f, PAPER_W + 200f)
                rulerCy = (p.y + rulerOffY).coerceIn(-200f, PAPER_H + 200f)
                invalidate()
            }
            MODE_RULER_ROT -> {
                val (tx0, ty0) = rulerCenter()
                rulerAng = -Math.toDegrees(
                    kotlin.math.atan2((p.y - ty0).toDouble(), (p.x - tx0).toDouble())).toFloat()
                invalidate()
            }
        }
        prevPt = p
    }

    /* ================== REGLA ==================
       Línea guía arrastrable y girable: los trazos que empiezan CERCA de ella
       se quedan pegados a la regla (perpendiculares que salen derechas). */
    var rulerCx = 500f
    var rulerCy = 700f
    var rulerAng = 0f            // grados; 0 = horizontal
    private var rulerOffX = 0f
    private var rulerOffY = 0f
    private fun ensureRuler() {
        // la regla aparece en el CENTRO de lo que ves ahora
        rulerCx = (paperX(width / 2f)).coerceIn(200f, PAPER_W - 200f)
        rulerCy = (paperY(height / 2f)).coerceIn(200f, PAPER_H - 200f)
    }
    private fun rulerCenter(): Pair<Float, Float> = rulerCx to rulerCy
    private fun rulerDir(): Pair<Float, Float> =
        cos(Math.toRadians(rulerAng.toDouble())).toFloat() to -sin(Math.toRadians(rulerAng.toDouble())).toFloat()
    private fun rulerTip(): Pt {
        val (dx, dy) = rulerDir()
        return Pt(rulerCx + dx * 470f, rulerCy + dy * 470f)
    }
    private fun distToRuler(p: Pt): Float {
        val (dx, dy) = rulerDir()
        val nx = -dy; val ny = dx
        return abs((p.x - rulerCx) * nx + (p.y - rulerCy) * ny)
    }
    /** proyecta un punto SOBRE la línea de la regla (pegarlo) */
    private fun snapToRuler(p: Pt): Pt {
        val (dx, dy) = rulerDir()
        val t = (p.x - rulerCx) * dx + (p.y - rulerCy) * dy
        return Pt(rulerCx + dx * t, rulerCy + dy * t)
    }
    private fun nearRuler(p: Pt): Boolean = rulerOn && distToRuler(p) < 30f

    private var prevPt = Pt()
    private var shapeOrigin = Pair(0f, 0f)   // esquina fija de la forma mientras la arrastras
    private var strokeSnapped = false        // ¿el trazo actual va PEGADO a la regla?

    private fun beginSelectionGesture(sx: Float, p: Pt) {
        prevPt = p
        val b = selectionBounds()
        if (!selection.isEmpty() && b != null && hitBounds(b, p.x, p.y, 26f)) {
            mode = MODE_MOVESEL
            return
        }
        if (!selection.isEmpty() && lastSelected != null && !isStrokeEl(lastSelected!!)) {
            val hb = lastSelected!!.bounds()
            val hx = hb.right - 16f; val hy = hb.bottom - 16f
            if (hypot((p.x - hx).toDouble(), (p.y - hy).toDouble()) < 60f) {
                mode = MODE_SCALEEL
                // save start size
                a = lastSelected!!
                e0 = elemDims(a!!)
                eStart = p
                scaleAnchor = RectF(hb.left, hb.top, hb.right, hb.bottom)
                return
            }
            // MANIJA DE GIRO ⟳ (arriba): solo sabiendo que el objeto se puede girar
            if (selection.size == 1 && rotKnobPos() != null) {
                val (kx, ky) = rotKnobPos()!!
                if (hypot((p.x - kx).toDouble(), (p.y - ky).toDouble()) < 54f) {
                    mode = MODE_ROTEL
                    a = selection[0]
                    val cx = (hb.left + hb.right) / 2f; val cy = (hb.top + hb.bottom) / 2f
                    rotStartAng = Math.toDegrees(
                        kotlin.math.atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()
                    rotStartVal = a!!.rot
                    return
                }
            }
        }
        mode = MODE_LASSO
        lasso.clear(); lasso.add(p)
        invalidate()
    }

    /** CURSOR: toca un objeto (texto, línea, imagen, sticker, enlace) y muévelo con libertad. */
    private fun beginSelectGesture(sx: Float, p: Pt) {
        prevPt = p
        val b = selectionBounds()
        if (!selection.isEmpty() && b != null && hitBounds(b, p.x, p.y, 30f)) {
            mode = MODE_MOVESEL          // arrastrar lo ya seleccionado
            return
        }
        if (!selection.isEmpty() && rotKnobPos() != null) {
            val (kx, ky) = rotKnobPos()!!
            if (hypot((p.x - kx).toDouble(), (p.y - ky).toDouble()) < 54f) {
                mode = MODE_ROTEL
                a = selection[0]
                val hb = a!!.bounds()
                val cx = (hb.left + hb.right) / 2f; val cy = (hb.top + hb.bottom) / 2f
                rotStartAng = Math.toDegrees(
                    kotlin.math.atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()
                rotStartVal = a!!.rot
                return
            }
        }
        // tapping: seleccionar el objeto tocado (también grupos sueltos del dibujo)
        val hit = elementAt(p)
        selection.clear()
        if (hit != null) {
            selection.add(hit)
            lastSelected = hit
        }
        host?.onSelectionChanged(!selection.isEmpty())
        // si nada golpeado: abrir el lazo para marcar el área a mano
        mode = if (hit == null) MODE_LASSO else MODE_MOVESEL
        if (mode == MODE_LASSO) {
            lasso.clear(); lasso.add(p); invalidate()
        }
    }

    private var a: PaperElement? = null
    private var e0 = Pair(1f, 1f)
    private var eStart = Pt()
    private var rotStartAng = 0f
    private var rotStartVal = 0f

    /** posición de la MANIJA ⟳ (centro superior) — solo SELECCIÓN ÚNICA girable */
    private fun rotKnobPos(): Pair<Float, Float>? {
        if (selection.size != 1) return null
        val el = selection[0]
        if (el is Stroke) return null
        val b = el.bounds()
        return ((b.left + b.right) / 2f) to (b.top - 42f)
    }

    private fun elemDims(el: PaperElement): Pair<Float, Float> = when (el) {
        is TextElement -> el.size to 0f
        is ImageElement -> el.w to el.h
        is StickerElement -> el.size to 0f
        is LinkElement -> el.scale to 0f
        is ShapeElement -> el.w to el.h
        else -> 1f to 1f
    }

    private fun isStrokeEl(el: PaperElement) = el is Stroke

    private fun moveSelectionBy(dx: Float, dy: Float) {
        for (el in selection) el.moveTo(dx, dy)
        invalidate()
    }

    private fun scaleSelection(p: Pt) {
        val el = a ?: return
        val d0 = hypot((eStart.x - scaleAnchor.left).toDouble(), (eStart.y - scaleAnchor.top).toDouble()).toFloat().coerceAtLeast(40f)
        val d1 = hypot((p.x - scaleAnchor.left).toDouble(), (p.y - scaleAnchor.top).toDouble()).toFloat().coerceAtLeast(40f)
        val f = d1 / d0
        val (dw, _) = e0
        when (el) {
            is TextElement -> { el.size = (dw * f).coerceIn(10f, 160f); el.invalidatePathCache() }
            is ImageElement -> { el.w = dw * f; el.h = e0.second * f }
            is StickerElement -> el.size = (dw * f).coerceIn(16f, 320f)
            is LinkElement -> el.scale = (dw * f).coerceIn(0.5f, 3f)
            is ShapeElement -> { el.w = (dw * f).coerceIn(6f, PAPER_W * 1.5f); el.h = (e0.second * f).coerceIn(6f, PAPER_H * 1.5f) }
            else -> {}
        }
        invalidate()
    }

    /* ===== AGRANDAR / ACHICAR / GIRAR la selección (botones del popup del cursor) ===== */

    /** f > 1 agranda, f < 1 achica. Cada objeto escala alrededor de su PROPIO centro. */
    fun selectionScale(f0: Float) {
        if (selection.isEmpty()) return
        record()
        val f = f0.coerceIn(0.3f, 3f)
        for (el in selection) when (el) {
            is TextElement -> { el.size = (el.size * f).coerceIn(9f, 220f); el.invalidatePathCache() }
            is ImageElement -> {
                val nw = (el.w * f).coerceIn(30f, PAPER_W * 1.8f)
                el.h = nw / el.w * el.h     // mantiene la proporción exacta
                el.w = nw
            }
            is StickerElement -> el.size = (el.size * f).coerceIn(14f, 400f)
            is LinkElement -> el.scale = (el.scale * f).coerceIn(0.5f, 3f)
            is ShapeElement -> { el.w = (el.w * f).coerceIn(8f, PAPER_W * 1.5f); el.h = (el.h * f).coerceIn(8f, PAPER_H * 1.5f) }
            is Stroke -> scaleStrokePoints(el, f)
        }
        invalidate()
    }

    private fun scaleStrokePoints(s: Stroke, f: Float) {
        val b = s.bounds()
        val cx = (b.left + b.right) / 2f
        val cy = (b.top + b.bottom) / 2f
        for (p in s.points) {
            p.x = cx + (p.x - cx) * f
            p.y = cy + (p.y - cy) * f
        }
        s.width = (s.width * f).coerceIn(1f, 120f)
        s.invalidatePathCache()
    }

    /** Girar grados (positivo = horario). Un objeto gira sobre sí mismo;
     *  varios giran en órbita alrededor del centro del grupo. Los trazos giran punto a punto. */
    fun selectionRotate(deg: Float) {
        if (selection.isEmpty()) return
        record()
        val b = selectionBounds() ?: return
        val gx = b.centerX(); val gy = b.centerY()
        val rad = Math.toRadians(deg.toDouble())
        val cs = cos(rad).toFloat(); val sn = sin(rad).toFloat()
        for (el in selection) {
            if (el is Stroke) {
                for (p in el.points) {
                    val dx = p.x - gx; val dy = p.y - gy
                    p.x = gx + dx * cs - dy * sn
                    p.y = gy + dx * sn + dy * cs
                }
                el.invalidatePathCache()
            } else {
                val eb = el.bounds()
                val ecx = (eb.left + eb.right) / 2f; val ecy = (eb.top + eb.bottom) / 2f
                val dx = ecx - gx; val dy = ecy - gy
                val nx = gx + dx * cs - dy * sn    // órbita del centro propio
                val ny = gy + dx * sn + dy * cs
                el.moveTo(nx - ecx, ny - ecy)
                el.rot = ((el.rot + deg) % 360f + 360f) % 360f
            }
        }
        invalidate()
    }

    private fun selectionBounds(): RectF? {
        if (selection.isEmpty()) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (el in selection) {
            val bb = el.bounds()
            l = min(l, bb.left); t = min(t, bb.top); r = max(r, bb.right); b = max(b, bb.bottom)
        }
        return RectF(l, t, r, b)
    }

    private fun hitBounds(b: AuxRectF, x: Float, y: Float, pad: Float): Boolean =
        x >= b.left - pad && x <= b.right + pad && y >= b.top - pad && y <= b.bottom + pad

    private fun hitBounds(b: RectF, x: Float, y: Float, pad: Float): Boolean =
        x >= b.left - pad && x <= b.right + pad && y >= b.top - pad && y <= b.bottom + pad

    private fun handleUp(sx: Float, sy: Float) {
        val p = Pt(paperX(sx), paperY(sy))
        when (mode) {
            MODE_DRAW -> commitLiveStroke()
            MODE_SHAPE -> {
                val sh = liveShape
                liveShape = null
                if (sh != null) {
                    if (sh.w <= 3f || sh.h <= 3f) {   // toque corto: forma por defecto centrada en el toque
                        val cx0 = shapeOrigin.first; val cy0 = shapeOrigin.second
                        sh.x = cx0 - 120f; sh.y = cy0 - 120f
                        sh.w = 240f; sh.h = if (sh.shape == Shape.LINE) 160f else 240f
                    }
                    pageItems.add(sh)
                }
                invalidate()
            }
            MODE_RULER_MOVE, MODE_RULER_ROT -> invalidate()
            MODE_LASSO -> {
                val little = abs(sx - downPt.x) + abs(sy - downPt.y) < 14f
                if (little) {
                    val hit = elementAt(p)
                    selection.clear()
                    if (hit != null) { selection.add(hit); lastSelected = hit }
                    host?.onSelectionChanged(!selection.isEmpty())
                } else {
                    lassoSelect()
                }
                lasso.clear()
                invalidate()
            }
            MODE_MOVESEL -> host?.onSelectionChanged(!selection.isEmpty())
        }
        mode = MODE_IDLE
        movedFar = false
        erasedDirty = false   // termina el arrastrón de borrado: el próximo vuelve a grabar
    }

    private var liveRaw: Pt? = null

    private fun commitLiveStroke() {
        val s = liveStroke ?: return
        liveStroke = null
        if (s.points.isEmpty()) return
        // el estabilizador deja la punta un pelín atrás: se cierra el trazo EN el último toque real
        val rw = liveRaw
        val lp = s.points[s.points.size - 1]
        if (rw != null && s.points.size >= 2 && abs(rw.x - lp.x) + abs(rw.y - lp.y) > 0.8f) {
            val q = Pt(rw.x, rw.y); q.p = lp.p * 0.7f
            s.points.add(q)
        }
        liveRaw = null
        if (s.points.size == 1) {
            val p0 = s.points[0]
            s.points.add(Pt(p0.x + 0.6f, p0.y + 0.6f))
        }
        record()
        pageItems.add(s)
        invalidate()
    }

    private fun commitLiveStrokeCancelled() {
        liveStroke = null
        invalidate()
    }

    private fun lassoSelect() {
        if (lasso.size < 3) return
        val nsel = ArrayList<PaperElement>()
        for (el in pageItems) {
            when (el) {
                is Stroke -> {
                    var inside = 0
                    for (q in el.points) if (Geo.insidePolygon(lasso, q.x, q.y)) inside++
                    if (inside >= 2 || inside * 2 > el.points.size) nsel.add(el)
                }
                else -> {
                    val b = el.bounds()
                    val cx = (b.left + b.right) / 2; val cy = (b.top + b.bottom) / 2
                    if (Geo.insidePolygon(lasso, cx, cy)) nsel.add(el)
                }
            }
        }
        selection.clear(); selection.addAll(nsel); lastSelected = nsel.lastOrNull()
        host?.onSelectionChanged(!nsel.isEmpty())
    }

    private fun elementAt(p: Pt): PaperElement? {
        for (i in pageItems.indices.reversed()) {
            val el = pageItems[i]
            val b = el.bounds()
            val pad = if (el is TextElement) 10f else 14f
            if (hitBounds(b, p.x, p.y, pad)) return el
        }
        return null
    }

    private fun textHit(p: Pt): TextElement? = elementAt(p) as? TextElement
    private fun linkHit(p: Pt): LinkElement? = elementAt(p) as? LinkElement

    /* ================== dibujo ================== */

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.2f
        pathEffect = DashPathEffect(floatArrayOf(14f, 9f), 0f)
        color = 0xFF7A6A5A.toInt()
    }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.6f; color = 0xFFED64A6.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFED64A6.toInt(); style = Paint.Style.FILL
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
    }
    private val knobRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFED64A6.toInt(); style = Paint.Style.STROKE; strokeWidth = 2.4f
    }

    fun strokePath(s: Stroke): Path {
        // BUG Corregido: el trazo EN VIVO muta sus puntos cada fotograma, así que
        // nunca debe usar (ni almacenar en) el caché: si se cacheara en el primer
        // draw (1 punto), quedaría invisible mientras escribes y solo aparecería
        // tras guardar y volver a entrar (el caché no se serializa).
        val live = (s === liveStroke)
        if (!live) s.pathCache?.let { return it }
        val pth = Path()
        if (s.points.isNotEmpty()) {
            val p0 = s.points[0]
            pth.moveTo(p0.x, p0.y)
            for (i in 1 until s.points.size) {
                val pm = s.points[i - 1]; val pi = s.points[i]
                pth.quadTo(pm.x, pm.y, (pm.x + pi.x) / 2f, (pm.y + pi.y) / 2f)
            }
        }
        if (!live) s.pathCache = pth
        return pth
    }

    /** ¿el trazo guarda PRESIÓN (no todo punto en 1)? → pinta a grosor variable */
    fun hasPressure(s: Stroke): Boolean {
        if (s.points.size < 3) return false
        // presión REAL solo si cambió en el trayecto (dedo = todo 1)
        val first = s.points.first().p
        for (q in s.points) if (abs(q.p - first) > 0.06f || abs(q.p - 1f) > 0.06f) return true
        return false
    }

    /** Cinta de contorno variable: polígono cerrado que engorda/delgadea con la presión.
     *  GOZOSO para lápiz digital: apretar = marca GRANDE, rozar = línea delgada. */
    /** Trazo de grosor variable ya muestreado: segmentos agrupados por grosor (se pintan con drawLines). */
    class VarLine(val widths: FloatArray, val segs: Array<FloatArray>, val bounds: RectF,
                  val xs: FloatArray = FloatArray(0), val ys: FloatArray = FloatArray(0), val ws: FloatArray = FloatArray(0))

    /**
     * TINTA con presión, sin polígonos: la línea se muestrea a lo largo de la curva suavizada y se
     * pinta como segmentos de punta redonda cuyo grosor sigue la presión (más afilada al empezar y
     * al soltar). No hay contornos que se crucen, así que no aparecen «sombras» ni huecos.
     */
    fun varLine(s: Stroke, factor: Float, minR: Float): VarLine {
        val live = (s === liveStroke)
        val key = (factor * 100).toInt() * 1000 + (minR * 100).toInt()
        if (!live) (s.ribbonCache?.get(key) as? VarLine)?.let { return it }
        val pts = s.points
        val n = pts.size
        val pressured = hasPressure(s)
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val prs = ArrayList<Float>(); val spd = ArrayList<Float>()
        var curSpd = 0f
        fun add(x: Float, y: Float, pr: Float) { xs.add(x); ys.add(y); prs.add(pr); spd.add(curSpd) }
        if (n == 0) return VarLine(FloatArray(0), emptyArray(), RectF())
        add(pts[0].x, pts[0].y, pts[0].p)
        if (n == 1) add(pts[0].x + 0.05f, pts[0].y + 0.05f, pts[0].p)
        var ax = pts[0].x; var ay = pts[0].y; var ap = pts[0].p
        for (i in 1 until n) {
            val b = pts[i]
            // velocidad ≈ separación entre toques: rápido = más fino (como una pluma de verdad)
            curSpd = (hypot((b.x - pts[i - 1].x).toDouble(), (b.y - pts[i - 1].y).toDouble()).toFloat() - 2.5f).div(14f).coerceIn(0f, 1f)
            val ex: Float; val ey: Float; val ep: Float
            if (i < n - 1) {
                val nx = pts[i + 1]
                ex = (b.x + nx.x) / 2f; ey = (b.y + nx.y) / 2f; ep = (b.p + nx.p) / 2f
            } else { ex = b.x; ey = b.y; ep = b.p }
            val len = hypot((b.x - ax).toDouble(), (b.y - ay).toDouble()) +
                hypot((ex - b.x).toDouble(), (ey - b.y).toDouble())
            val steps = (len / 1.5).toInt().coerceIn(1, 18)
            for (k in 1..steps) {
                val t = k.toFloat() / steps; val u = 1f - t
                add(u * u * ax + 2 * u * t * b.x + t * t * ex,
                    u * u * ay + 2 * u * t * b.y + t * t * ey,
                    u * u * ap + 2 * u * t * b.p + t * t * ep)
            }
            ax = ex; ay = ey; ap = ep
        }
        val m = xs.size
        // longitud acumulada para afilar los extremos
        val cum = FloatArray(m)
        for (j in 1 until m) cum[j] = cum[j - 1] + hypot((xs[j] - xs[j - 1]).toDouble(), (ys[j] - ys[j - 1]).toDouble()).toFloat()
        val total = cum[m - 1]
        val base = s.width * factor
        val inR = 8f + base * 2.0f
        val outR = 16f + base * 3.5f
        val w = FloatArray(m)
        for (j in 0 until m) {
            val pr = prs[j].coerceIn(0.04f, 1f)
            var wj = if (pressured) base * 1.55f * (minR + (1f - minR) * pr) else base * 1.05f
            wj *= (1f - 0.32f * spd[j])                                        // más veloz → más fina
            val fin = 0.4f + 0.6f * (cum[j] / inR).coerceIn(0f, 1f)            // arranque afilado
            val fout = 0.22f + 0.78f * ((total - cum[j]) / outR).coerceIn(0f, 1f) // salida en punta (rabito)
            w[j] = (wj * fin * fout).coerceAtLeast(0.8f)
        }
        // suavizado del grosor (2 pasadas de promedio móvil): transiciones sedosas, sin escalones
        val tmp = FloatArray(m)
        for (pass in 0 until 2) {
            for (j in 0 until m) {
                var sum = 0f; var cnt = 0
                for (k in -3..3) { val q = j + k; if (q in 0 until m) { sum += w[q]; cnt++ } }
                tmp[j] = sum / cnt
            }
            for (j in 0 until m) w[j] = tmp[j]
        }
        var wMin = Float.MAX_VALUE; var wMax = 0f
        for (j in 0 until m) { if (w[j] < wMin) wMin = w[j]; if (w[j] > wMax) wMax = w[j] }
        val nb = if (wMax - wMin < 0.2f) 1 else 18
        val widths = FloatArray(nb) { if (nb == 1) wMax else wMin + it * (wMax - wMin) / (nb - 1) }
        val lists = Array(nb) { ArrayList<Float>() }
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var bt = -Float.MAX_VALUE
        for (j in 0 until m) {
            if (xs[j] < l) l = xs[j]; if (xs[j] > r) r = xs[j]
            if (ys[j] < t) t = ys[j]; if (ys[j] > bt) bt = ys[j]
            if (j == 0) continue
            val wj = (w[j] + w[j - 1]) / 2f
            val bi = if (nb == 1) 0 else (((wj - wMin) / (wMax - wMin)) * (nb - 1) + 0.5f).toInt().coerceIn(0, nb - 1)
            val ls = lists[bi]
            ls.add(xs[j - 1]); ls.add(ys[j - 1]); ls.add(xs[j]); ls.add(ys[j])
        }
        val segs = Array(nb) { lists[it].toFloatArray() }
        val out = VarLine(widths, segs, RectF(l - wMax, t - wMax, r + wMax, bt + wMax),
            xs.toFloatArray(), ys.toFloatArray(), w)
        if (!live) {
            val cache = s.ribbonCache ?: HashMap<Int, Any>().also { s.ribbonCache = it }
            cache[key] = out
        }
        return out
    }

    /** textura BAKEADA: grano del lápiz / perlitas del pincel de puntitos */
    fun stampPath(s: Stroke): Path {
        val live = (s === liveStroke)
        if (!live) s.stampCache?.let { return it }
        val out = Path()
        val rnd = Random(s.points.hashCode())
        if (s.brush == Brush.DOTTED) {
            val step = (s.width * 3.2f).coerceAtLeast(9f)
            var acc = -step * 0.4f
            for (i in 1 until s.points.size) {
                val a = s.points[i - 1]; val b = s.points[i]
                val d = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat() + acc
                var t = acc
                while (t < d) {
                    val f = t / d.coerceAtLeast(1f)
                    val x = a.x + (b.x - a.x) * f; val y = a.y + (b.y - a.y) * f
                    val r = s.width * 0.42f * (0.55f + 0.7f * (a.p * 0.5f + b.p * 0.5f).coerceIn(0.05f, 1f))
                    out.addCircle(x, y, r.coerceAtLeast(2.2f), Path.Direction.CW)
                    t += step
                }
                acc = t - d
            }
        } else { // PENCIL — grano: puntitos cortos pegados al trazo, deterministic
            val w0 = s.width
            for (i in 0 until s.points.size) {
                val q = s.points[i]
                val hr = w0 * 0.75f
                for (g in 0..2) {
                    val ox = (rnd.nextFloat() - 0.5f) * hr
                    val oy = (rnd.nextFloat() - 0.5f) * hr
                    if (hypot(ox.toDouble(), oy.toDouble()) < hr) {
                        out.addCircle(q.x + ox, q.y + oy, (w0 * 0.16f).coerceAtLeast(1.2f), Path.Direction.CW)
                    }
                }
            }
        }
        if (!live) s.stampCache = out
        return out
    }

    private class Pt2(val x: Float, val y: Float)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0xFFEFEEEA.toInt())   // mesa gris cálido de la captura
        if (!isSetup) return
        canvas.save()
        toCanvasCoord(canvas)
        drawPaper(canvas)
        drawElements(canvas, pageItems, 1f, curPage.template)
        liveStroke?.let { drawStroke(canvas, it) }
        liveShape?.let { drawShape(canvas, it) }      // forma mientras la arrastras
        // lazo y selección en coords papel
        if (lasso.isNotEmpty()) {
            val lp = Path()
            lp.moveTo(lasso[0].x, lasso[0].y)
            for (q in lasso) lp.lineTo(q.x, q.y)
            dashPaint.color = 0xFF7A6A5A.toInt()
            canvas.drawPath(lp, dashPaint)
        }
        if (selection.isNotEmpty()) {
            val b = selectionBounds()!!
            canvas.drawRoundRect(b, 6f, 6f, selPaint)
            val lsel = lastSelected
            if (lsel != null && !isStrokeEl(lsel)) {
                val hb = lsel.bounds()
                canvas.drawCircle(hb.right - 12f, hb.bottom - 12f, 12f, handlePaint)
                canvas.drawCircle(hb.right - 12f, hb.bottom - 12f, 5f, paperPaint)
            }
            // MANIJA ⟳ de giro (arriba al centro) — objetos girable: texto/img/sticker/forma/enlace
            rotKnobPos()?.let { (kx, ky) ->
                canvas.drawLine((b.left + b.right) / 2f, b.top, kx, ky, handlePaint)
                canvas.drawCircle(kx, ky, 15f, knobPaint)
                canvas.drawCircle(kx, ky, 15f, knobRing)
                val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF4A76C8.toInt(); textSize = 17f; textAlign = Paint.Align.CENTER
                }
                val fm = textP.fontMetrics
                val ty = ky - (fm.ascent + fm.descent) / 2f
                canvas.drawText("⟳", kx, ty, textP)
            }
        }
        // —— REGLA: guía de líneas derechas ——
        if (rulerOn) drawRuler(canvas)
        canvas.restore()
    }

    private fun drawRuler(c: Canvas) {
        val (dx, dy) = rulerDir()
        val nx = -dy; val ny = dx
        val L = 760f   // medio largo de la regla
        val x1 = rulerCx - dx * L; val y1 = rulerCy - dy * L
        val x2 = rulerCx + dx * L; val y2 = rulerCy + dy * L
        // cuerpo de la regla (una banda translúcida que ENCAJA con la línea)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x223A76C8; style = Paint.Style.FILL }
        val bp = Path()
        bp.moveTo(x1 + nx * 26f, y1 + ny * 26f)
        bp.lineTo(x2 + nx * 26f, y2 + ny * 26f)
        bp.lineTo(x2 - nx * 26f, y2 - ny * 26f)
        bp.lineTo(x1 - nx * 26f, y1 - ny * 26f)
        bp.close()
        c.drawPath(bp, body)
        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2C6FC4.toInt(); style = Paint.Style.STROKE; strokeWidth = 3.4f
        }
        c.drawLine(x1, y1, x2, y2, rp)
        // marcas graduadas cortas al costado
        for (i in -7..7) {
            val gx = rulerCx + dx * i * 90f; val gy = rulerCy + dy * i * 90f
            c.drawLine(gx, gy, gx + nx * 14f, gy + ny * 14f, rp)
        }
        // manija de giro = punto azul en el extremo
        val tip = rulerTip()
        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2C6FC4.toInt(); style = Paint.Style.FILL }
        c.drawCircle(tip.x, tip.y, 16f, hp)
        c.drawCircle(tip.x, tip.y, 7f, paperPaint)
    }

    private var isSetup = false

    /** Importante: render de exportación (no toca el lienzo vivo).
     *  Nada de lo que está FUERA del papel aparece: los trazos que se salen
     *  de la hoja se recortan en el borde (más nada de mesa se ve si quieres). */
    fun renderPageBitmap(pageIdx: Int, outWidth: Int, withDesk: Boolean = true): Bitmap {
        val page = pages[pageIdx.coerceIn(0, pages.size - 1)]
        val w = outWidth.coerceIn(200, 3200)
        val h = (w * PAPER_H / PAPER_W).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paperIdx = page.paper.coerceIn(0, PAPER_COLORS.size - 1)
        // fondo EXACTO del papel (evita esquinas blancas en la hoja redondeada)
        c.drawColor(PAPER_COLORS[paperIdx])
        c.save()
        c.scale(w / PAPER_W, h / PAPER_H)
        // CLIP DURO: SOLO lo que está dentro de la hoja
        val paperClip = Path().apply { addRoundRect(RectF(0f, 0f, PAPER_W, PAPER_H), 10f, 10f, Path.Direction.CW) }
        c.clipPath(paperClip)
        drawPaper(c, page, withShadow = false)
        drawElements(c, page.items, 1f, page.template)
        c.restore()
        return bmp
    }

    private fun toCanvasCoord(c: Canvas) {
        c.translate(tx, ty)
        c.scale(scale, scale)
    }

    private fun drawPaper(c: Canvas, page: KPage = curPage, withShadow: Boolean = true) {
        val r = 10f
        val paperIdx = page.paper.coerceIn(0, PAPER_COLORS.size - 1)
        val dark = paperIdx == 2
        if (withShadow) {
            shadowPaint.color = 0x36000000
            c.drawRoundRect(8f, 10f, PAPER_W + 8, PAPER_H + 12, r, r, shadowPaint)
        }
        if (paperIdx == 7) {
            // FONDO de la PORTADA del diario (la preferencia del usuario en este cuaderno)
            c.save()
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, PAPER_W, PAPER_H), r, r, Path.Direction.CW) }
            c.clipPath(clip)
            c.translate(-170f, -360f)
            com.knotes.app.ui.NotebookCoverView.drawCover(c, PAPER_W + 340f, PAPER_H + 720f,
                notebook.coverStyle, notebook.coverSeed)
            c.restore()
        } else {
            paperPaint.color = PAPER_COLORS[paperIdx]
            c.drawRoundRect(0f, 0f, PAPER_W, PAPER_H, r, r, paperPaint)
        }

        linePaint.style = Paint.Style.STROKE
        linePaint.color = if (dark) 0x33FFFFFF.toInt() else if (paperIdx == 1) 0xFFDADADA.toInt() else 0xFFC7C1B2.toInt()
        linePaint.strokeWidth = 2.1f
        when (page.template) {
            0 -> { // rayado clásico
                linePaint.color = if (dark) 0x22FFFFFF.toInt() else 0xFFE0C4B8.toInt()
                c.drawLine(86f, 40f, 86f, PAPER_H - 40f, linePaint)
                linePaint.color = if (dark) 0x33FFFFFF.toInt() else if (paperIdx == 1) 0xFFDADADA.toInt() else 0xFFC7C1B2.toInt()
                var y = 130f
                while (y < PAPER_H - 46f) { c.drawLine(36f, y, PAPER_W - 36f, y, linePaint); y += 76f }
            }
            1 -> { // cuadrícula
                var y = 60f
                while (y < PAPER_H) { c.drawLine(40f, y, PAPER_W - 40f, y, linePaint); y += 74f }
                var x = 40f
                while (x < PAPER_W) { c.drawLine(x, 40f, x, PAPER_H - 40f, linePaint); x += 74f }
            }
            2 -> { // puntos
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = linePaint.color; style = Paint.Style.FILL }
                var y = 110f
                while (y < PAPER_H - 60f) {
                    var x = 70f
                    while (x < PAPER_W - 40f) { c.drawCircle(x, y, 3f, dot); x += 80f }
                    y += 80f
                }
            }
            4 -> { // rayado fino (byte más apretado, ideal letra pequeña)
                var y = 116f
                while (y < PAPER_H - 50f) { c.drawLine(40f, y, PAPER_W - 40f, y, linePaint); y += 58f }
            }
            5 -> { // cuadrícula fina
                var y = 52f
                while (y < PAPER_H) { c.drawLine(36f, y, PAPER_W - 36f, y, linePaint); y += 48f }
                var x = 36f
                while (x < PAPER_W) { c.drawLine(x, 36f, x, PAPER_H - 36f, linePaint); x += 48f }
            }
            6 -> { // puntos finos
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = linePaint.color; style = Paint.Style.FILL }
                var y = 96f
                while (y < PAPER_H - 60f) {
                    var x = 60f
                    while (x < PAPER_W - 44f) { c.drawCircle(x, y, 2.2f, dot); x += 52f }
                    y += 52f
                }
            }
            7 -> { // rayado con MARGEN ancho (like cuaderno escolar)
                linePaint.color = if (dark) 0x22FFFFFF.toInt() else 0xFFE0C4B8.toInt()
                c.drawLine(150f, 40f, 150f, PAPER_H - 40f, linePaint)
                linePaint.color = if (dark) 0x22FFFFFF.toInt() else 0xFFC7C1B2.toInt()
                var y = 130f
                while (y < PAPER_H - 46f) { c.drawLine(40f, y, PAPER_W - 36f, y, linePaint); y += 76f }
            }
            8 -> { // DOS COLUMNAS
                val mid = PAPER_W / 2f
                linePaint.color = if (dark) 0x22FFFFFF.toInt() else 0xFFDBD3C0.toInt()
                c.drawLine(mid, 40f, mid, PAPER_H - 40f, linePaint)
                var y = 110f
                while (y < PAPER_H - 46f) {
                    c.drawLine(40f, y, mid - 30f, y, linePaint)
                    c.drawLine(mid + 30f, y, PAPER_W - 40f, y, linePaint)
                    y += 64f
                }
            }
            9 -> { // AGENDA: bloque de encabezado + renglones
                linePaint.color = if (dark) 0x22FFFFFF.toInt() else 0xFFE0C4B8.toInt()
                c.drawRoundRect(40f, 40f, PAPER_W - 40f, 180f, 12f, 12f, linePaint)
                linePaint.color = if (dark) 0x33FFFFFF.toInt() else if (paperIdx == 1) 0xFFDADADA.toInt() else 0xFFC7C1B2.toInt()
                var y = 210f
                while (y < PAPER_H - 46f) { c.drawLine(40f, y, PAPER_W - 40f, y, linePaint); y += 70f }
            }
            else -> {}
        }
        // adorno de esquinas (flores, frutitas, corazones, ... )
        if (page.decor in 1..13) Decors.draw(c, page.decor, PAPER_W, PAPER_H)
    }

    private fun drawElements(c: Canvas, items: List<PaperElement>, dscale: Float, tpl: Int) {
        for (el in items) drawElement(c, el, dscale, tpl)
    }

    private fun drawElement(c: Canvas, el: PaperElement, ds: Float, tpl: Int) {
        when (el) {
            is Stroke -> drawStroke(c, el, ds)
            is TextElement -> drawText(c, el, tpl)
            is ImageElement -> drawImage(c, el)
            is LinkElement -> drawLink(c, el)
            is StickerElement -> drawSticker(c, el)
            is ShapeElement -> drawShape(c, el)
        }
    }

    /** FORMAS geométricas limpias: línea, rectángulo, círculo, triángulo, flecha y estrella */
    private fun drawShape(c: Canvas, el: ShapeElement) {
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = el.color; style = Paint.Style.STROKE
            strokeWidth = max(1.8f, el.width); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        c.save()
        val b = el.bounds()
        if (el.rot != 0f) c.rotate(el.rot, (b.left + b.right) / 2f, (b.top + b.bottom) / 2f)
        val x = el.x; val y = el.y; val w = el.w.coerceAtLeast(2f); val h = el.h.coerceAtLeast(2f)
        when (el.shape.coerceIn(0, Shape.NAMES.size - 1)) {
            Shape.LINE -> { c.drawLine(x, y, x + el.w, y + el.h, sp)
                // permitido: la línea respeta el arrastre según se dibujó
            }
            Shape.RECT -> c.drawRoundRect(x, y, x + w, y + h, 18f, 18f, sp)
            Shape.OVAL -> c.drawOval(RectF(x, y, x + w, y + h), sp)
            Shape.TRIANGLE -> {
                val p = Path()
                p.moveTo(x + w / 2f, y)
                p.lineTo(x + w, y + h)
                p.lineTo(x, y + h)
                p.close()
                c.drawPath(p, sp)
            }
            Shape.ARROW -> {
                val ex = x + w; val ey = y + h
                c.drawLine(x, y, ex, ey, sp)
                // cabeza: dos aletas simétricas respecto de la dirección
                val ang = Math.atan2(el.h.toDouble(), el.w.toDouble())
                val size = min(w, h).coerceAtLeast(24f) * 0.42f
                val a1 = ang + Math.toRadians(150.0); val a2 = ang - Math.toRadians(150.0)
                c.drawLine(ex, ey, (ex + cos(a1) * size).toFloat(), (ey + sin(a1) * size).toFloat(), sp)
                c.drawLine(ex, ey, (ex + cos(a2) * size).toFloat(), (ey + sin(a2) * size).toFloat(), sp)
            }
            Shape.STAR -> {
                val cx = x + w / 2f; val cy = y + h / 2f
                val ro = min(w, h) / 2f; val ri = ro * 0.42f
                val p = Path()
                for (i in 0 until 10) {
                    val ang = Math.toRadians(-90.0 + i * 36.0)
                    val rr = if (i % 2 == 0) ro else ri
                    val px = cx + cos(ang).toFloat() * rr
                    val py = cy + sin(ang).toFloat() * rr
                    if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                p.close()
                c.drawPath(p, sp)
            }
            Shape.DIAMOND -> {   // ROMBO ◇
                val p = Path()
                p.moveTo(x + w / 2f, y)
                p.lineTo(x + w, y + h / 2f)
                p.lineTo(x + w / 2f, y + h)
                p.lineTo(x, y + h / 2f)
                p.close()
                c.drawPath(p, sp)
            }
            Shape.HEART -> {     // CORAZÓN ♥
                val cx = x + w / 2f
                val p = Path()
                p.moveTo(cx, y + h * 0.94f)
                p.cubicTo(x - w * 0.08f, y + h * 0.60f, x + w * 0.16f, y + h * 0.04f, cx, y + h * 0.30f)
                p.cubicTo(x + w * 0.84f, y + h * 0.04f, x + w * 1.08f, y + h * 0.60f, cx, y + h * 0.94f)
                p.close()
                c.drawPath(p, sp)
            }
            Shape.CLOUD -> {     // NUBE ☁
                val yb = y + h * 0.84f
                val p = Path()
                p.moveTo(x + w * 0.10f, yb)
                p.cubicTo(x - w * 0.04f, y + h * 0.44f, x + w * 0.16f, y + h * 0.10f, x + w * 0.40f, y + h * 0.16f)
                p.cubicTo(x + w * 0.46f, y - h * 0.04f, x + w * 0.78f, y - h * 0.04f, x + w * 0.84f, y + h * 0.18f)
                p.cubicTo(x + w * 1.06f, y + h * 0.22f, x + w * 1.04f, y + h * 0.70f, x + w * 0.88f, yb)
                p.close()
                c.drawPath(p, sp)
            }
            Shape.CROSS -> {     // CRUZ ✕ (dos líneas diagonales)
                c.drawLine(x, y, x + el.w, y + el.h, sp)
                c.drawLine(x + el.w, y, x, y + el.h, sp)
            }
            Shape.HEX -> {       // HEXÁGONO ⬡
                val cx = x + w / 2f; val cy = y + h / 2f
                val p = Path()
                for (i in 0 until 6) {
                    val ang = Math.toRadians(-90.0 + i * 60.0)
                    // radio rectangular: estira el hexágono al tamaño del arrastre
                    val xx = cx + cos(ang).toFloat() * (w / 2f)
                    val yy = cy + sin(ang).toFloat() * (h / 2f)
                    if (i == 0) p.moveTo(xx, yy) else p.lineTo(xx, yy)
                }
                p.close()
                c.drawPath(p, sp)
            }
        }
        c.restore()
    }

    /** cuerpo del trazo: a grosor constante (dedo) o CINTA de grosor variable (lápiz digital) */
    private fun drawBody(c: Canvas, s: Stroke, factor: Float, minR: Float, color: Int, alpha: Int,
                         ds: Float, minPx: Float, cap: Paint.Cap = Paint.Cap.ROUND) {
        if (hasPressure(s) || s.brush == Brush.PEN || s.brush == Brush.HIGHLIGHTER) {
            val vl = varLine(s, factor, minR)
            val layer = alpha < 255
            // translúcido = UNA capa con alfa (los segmentos solapados no se oscurecen entre sí)
            if (layer) c.saveLayerAlpha(vl.bounds, alpha)
            strokePaint.style = Paint.Style.STROKE
            strokePaint.color = color; strokePaint.alpha = 255
            strokePaint.strokeCap = Paint.Cap.ROUND; strokePaint.strokeJoin = Paint.Join.ROUND
            for (bi in vl.widths.indices) {
                val arr = vl.segs[bi]
                if (arr.isEmpty()) continue
                strokePaint.strokeWidth = max(minPx * 0.5f, vl.widths[bi] * ds)
                c.drawLines(arr, 0, arr.size, strokePaint)
            }
            if (layer) c.restore()
        } else {
            strokePaint.style = Paint.Style.STROKE
            strokePaint.color = color; strokePaint.alpha = alpha
            strokePaint.strokeCap = cap; strokePaint.strokeJoin = Paint.Join.ROUND
            strokePaint.strokeWidth = max(minPx, s.width * factor * ds)
            c.drawPath(strokePath(s), strokePaint)
        }
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        fun ch(sh: Int) = (((a shr sh) and 0xFF) * (1f - f) + ((b shr sh) and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** 3D PERLA: degradado pastel (rosa → celeste → lila → crema) con contorno, cuerpo y brillo. */
    private fun drawPearl(c: Canvas, s: Stroke, ds: Float) {
        val vl = varLine(s, 1.5f, 0.55f)
        val n = vl.xs.size
        if (n < 2) return
        val pal = intArrayOf(0xFFF9B8E6.toInt(), 0xFFA9EEF2.toInt(), 0xFFC9B8F8.toInt(), 0xFFFFF0B8.toInt(), 0xFFF9B8E6.toInt())
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeCap = Paint.Cap.ROUND
        for (pass in 0..2) {
            var dist = 0f
            for (j in 1 until n) {
                dist += hypot((vl.xs[j] - vl.xs[j - 1]).toDouble(), (vl.ys[j] - vl.ys[j - 1]).toDouble()).toFloat()
                val tt = ((dist / 230f) % 1f) * (pal.size - 1)
                val i0 = tt.toInt().coerceIn(0, pal.size - 2)
                val col = mix(pal[i0], pal[i0 + 1], tt - i0)
                val ww = (vl.ws[j] + vl.ws[j - 1]) / 2f * ds
                var ox = 0f; var oy = 0f
                when (pass) {
                    0 -> { strokePaint.color = shade(col, 0.66f); strokePaint.strokeWidth = ww * 1.4f }
                    1 -> { strokePaint.color = col; strokePaint.strokeWidth = ww * 1.0f }
                    else -> { strokePaint.color = 0xCCFFFFFF.toInt(); strokePaint.strokeWidth = max(0.8f, ww * 0.26f); ox = -ww * 0.14f; oy = -ww * 0.2f }
                }
                strokePaint.alpha = if (pass == 2) 200 else 255
                c.drawLine(vl.xs[j - 1] + ox, vl.ys[j - 1] + oy, vl.xs[j] + ox, vl.ys[j] + oy, strokePaint)
            }
        }
        strokePaint.alpha = 255
    }

    private fun shade(color: Int, f: Float): Int {
        val r = ((color shr 16) and 0xFF) * f; val g = ((color shr 8) and 0xFF) * f; val b = (color and 0xFF) * f
        return (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
    }

    private fun drawStroke(c: Canvas, s: Stroke, ds: Float = 1f) {
        strokePaint.isDither = false
        strokePaint.style = Paint.Style.STROKE
        when (s.brush) {
            Brush.HILITER -> {
                // RESALTADOR: banda ancha translúcida de punta cuadrada (lima en la captura)
                drawBody(c, s, 1f, 0.6f, s.color, 150, ds, 4f, Paint.Cap.SQUARE)
            }
            Brush.HIGHLIGHTER -> {
                // SUBRAYAR: línea fina y saturada con un halo suave debajo
                drawBody(c, s, 2.2f, 0.5f, s.color, 60, ds, 4f)
                drawBody(c, s, 1f, 0.5f, s.color, 225, ds, 2f)
            }
            Brush.MARKER -> {
                // MARCADOR: trazo firme y lleno
                drawBody(c, s, 1f, 0.55f, s.color, 238, ds, 2.6f)
            }
            Brush.PENCIL -> {
                // LÁPIZ: mina grisácea + grano de grafito
                drawBody(c, s, 0.62f, 0.4f, s.color, 205, ds, 1.6f)
                val grain = stampPath(s)
                strokePaint.style = Paint.Style.FILL; strokePaint.color = s.color; strokePaint.alpha = 125
                c.drawPath(grain, strokePaint)
                strokePaint.style = Paint.Style.STROKE
            }
            Brush.DOTTED -> {
                // PUNTITOS: perlas espaciadas a lo largo del trazo
                strokePaint.color = s.color; strokePaint.alpha = 235
                strokePaint.style = Paint.Style.FILL
                c.drawPath(stampPath(s), strokePaint)
                strokePaint.style = Paint.Style.STROKE
            }
            Brush.NEON -> {
                // RESALTADO NEÓN: resplandor de capas del color + núcleo blanco brillante
                drawBody(c, s, 2.6f, 0.5f, s.color, 26, ds, 4f)
                drawBody(c, s, 1.9f, 0.5f, s.color, 48, ds, 3.4f)
                drawBody(c, s, 1.3f, 0.5f, s.color, 105, ds, 2.6f)
                drawBody(c, s, 0.62f, 0.5f, 0xFFFFFFFF.toInt(), 250, ds, 1.6f)
            }
            Brush.PEARL -> drawPearl(c, s, ds)
            Brush.BEVEL -> {
                // 3D BISELADO: contorno oscuro + cuerpo + degradado de luz + brillo
                drawBody(c, s, 1.28f, 0.55f, shade(s.color, 0.42f), 255, ds, 2.6f)
                drawBody(c, s, 0.92f, 0.55f, s.color, 255, ds, 1.8f)
                drawBody(c, s, 0.5f, 0.55f, shade(s.color, 1.25f), 190, ds, 1.2f)
                c.save(); c.translate(-s.width * 0.26f * ds, -s.width * 0.28f * ds)
                drawBody(c, s, 0.26f, 0.55f, 0xFFFFFFFF.toInt(), 175, ds, 1f)
                c.restore()
            }
            else -> {
                // BOLÍGRAFO: tinta fina, firme y precisa
                drawBody(c, s, 1f, 0.35f, s.color, 255, ds, 1.4f)
            }
        }
        strokePaint.style = Paint.Style.STROKE
        strokePaint.alpha = 255
    }

    private fun fillPaints(color: Int, alpha: Int): Paint =
        fillPaint.apply { this.color = color; this.alpha = alpha }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun drawText(c: Canvas, el: TextElement, tpl: Int) {
        if (el.text.isBlank()) return
        var layout = el.pathCache
        if (layout == null || el.cacheTpl != tpl) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = el.color; textSize = el.size; isAntiAlias = true; isFakeBoldText = false
                // TIPOGRAFÍA elegida: "serif" = aire Times New Roman, "monospace" = Courier, etc.
                if (el.font.isNotBlank()) {
                    try { typeface = android.graphics.Typeface.create(el.font, android.graphics.Typeface.NORMAL) }
                    catch (_: Exception) {}
                }
            }
            val wmax = min(TextElement.wrapWidth(el.size), PAPER_W - 130f).toInt().coerceAtLeast(60)
            // espaciado que hace caer CADA renglón sobre la línea del papel
            val want = lineSpacingOf(tpl)
            val fm = tp.fontMetrics
            val natural = fm.descent - fm.ascent
            val extra = (want - natural)
            layout = StaticLayout.Builder.obtain(el.text, 0, el.text.length, tp, wmax)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(extra, 1f)
                .setIncludePad(false)
                .build()
            el.pathCache = layout
            el.cacheTpl = tpl
        }
        c.save()
        // GIRO alrededor del centro visual del texto
        if (el.rot != 0f) {
            val top = if (el.baseY >= 0f) el.y - layout.getLineBaseline(0) else el.y
            c.rotate(el.rot, el.x + layout.width / 2f, top + layout.height / 2f)
        }
        if (el.baseY >= 0f) {
            // longitud EXACTA: la primera línea base del LAYOUT cae justo en el renglón (el.y),
            // sin importar la fuente de respaldo (emoji/acentos) — siempre igual de alineada
            c.translate(el.x, el.y - layout.getLineBaseline(0))
        } else c.translate(el.x, el.y)   // textos antiguos: como estaban
        layout.draw(c)
        c.restore()
    }

    private fun drawImage(c: Canvas, el: ImageElement) {
        var bmp = el.bitmapCache
        if (bmp == null && !el.failedLoad) {
            val f = java.io.File(el.path)
            if (f.exists()) {
                bmp = BitmapFactory.decodeFile(f.absolutePath)
                if (bmp != null) el.bitmapCache = bmp else el.failedLoad = true
            } else el.failedLoad = true
        }
        bmp?.let {
            c.save()
            // GIRO alrededor del centro de la foto
            if (el.rot != 0f) c.rotate(el.rot, el.x + el.w / 2f, el.y + el.h / 2f)
            c.drawBitmap(it, null, RectF(el.x, el.y, el.x + el.w, el.y + el.h), paperPaint)
            c.restore()
        }
    }

    private fun drawLink(c: Canvas, el: LinkElement) {
        val sc = el.scale.coerceIn(0.5f, 3f)
        val b = el.bounds()
        c.save()
        // GIRO alrededor del centro del chip
        if (el.rot != 0f) c.rotate(el.rot, b.left + b.width() / 2f, b.top + b.height() / 2f)
        c.translate(b.left, b.top)
        c.scale(sc, sc)
        // medidas base (sin escala) — el lienzo ya viene escalado
        val w0 = (78f + el.label.length * 16f).coerceAtLeast(170f)
        val h0 = 74f
        // estilo "chip" sutil: papel suave + acento del enlace + NOMBRE visible
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6FFFFFF.toInt(); style = Paint.Style.FILL
        }
        c.drawRoundRect(0f, 0f, w0, h0, 14f, 14f, p)
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = el.color; style = Paint.Style.FILL; alpha = 40
        }
        c.drawRoundRect(0f, 0f, w0, h0, 14f, 14f, soft)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = el.color; style = Paint.Style.STROKE; strokeWidth = 1.7f; alpha = 200
        }
        c.drawRoundRect(0f, 0f, w0, h0, 14f, 14f, stroke)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF33304A.toInt(); textSize = 27f
        }
        val labelWidth = w0 - 84f
        val shown = android.text.TextUtils.ellipsize(el.label, tp, labelWidth, android.text.TextUtils.TruncateAt.END).toString()
        // icono del enlace bien centrado
        val ip = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f }
        c.drawText("🔗", 20f, h0 / 2f + 9f, ip)
        // NOMBRE (lo que el usuario escribió) siempre visible
        c.drawText(shown, 56f, h0 / 2f + 10f, tp)
        // subrayado con el acento (se ve como enlace real)
        val lw = tp.measureText(shown).coerceAtMost(labelWidth)
        val und = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = el.color; style = Paint.Style.STROKE; strokeWidth = 3f; alpha = 180
        }
        c.drawLine(56f, h0 - 12f, 56f + lw, h0 - 12f, und)
        c.restore()
    }

    private fun drawSticker(c: Canvas, el: StickerElement) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = el.size }
        c.save()
        // GIRO alrededor del centro del sticker
        if (el.rot != 0f) c.rotate(el.rot, el.x + el.size * 0.6f, el.y + el.size * 0.7f)
        c.drawText(el.emoji, el.x, el.y + el.size, tp)
        c.restore()
    }
}