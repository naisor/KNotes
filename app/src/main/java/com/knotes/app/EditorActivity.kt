package com.knotes.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.knotes.app.canvas.CanvasHost
import com.knotes.app.canvas.CanvasPageView
import com.knotes.app.canvas.Tools
import com.knotes.app.data.LinkElement
import com.knotes.app.data.Notebook
import com.knotes.app.data.Prefs
import com.knotes.app.data.Pt
import com.knotes.app.data.Store
import com.knotes.app.data.TextElement
import com.knotes.app.io.ExportUtil
import com.knotes.app.ui.Panels
import com.knotes.app.ui.Dialogs
import com.knotes.app.ui.Gender
import java.io.File

/**
 * Editor — papel interactivo.
 * Todo lo GUARDADO es editable/borrable a la vuelta: goma por zona o por trazo,
 * lazo para mover o borrar cualquier elemento (trazos, textos, stickers,
 * imágenes, FORMAS o enlaces). Además exporta TXT / PDF / Word / JPG / PNG.
 */
class EditorActivity : AppCompatActivity(), CanvasHost {

    private lateinit var canvas: CanvasPageView
    private lateinit var pageLabel: TextView
    private lateinit var popupSel: LinearLayout
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var toolRow: LinearLayout

    private var nb: Notebook? = null
    private var exportKind = 0 // 1 png, 2 jpg, 3 pdf, 4 txt, 5 docx

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val book = nb ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        val triple = Store.importImage(this, book, uri)
        if (triple != null) {
            canvas.addImageElement(triple.first, triple.second, triple.third)
            Toast.makeText(this, "🖼 Imagen añadida en el centro del papel · muévela/escálala con el lazo", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No se pudo cargar esa imagen 😕 (JPG, PNG, WEBP, HEIC, AVIF, BMP, GIF…)", Toast.LENGTH_LONG).show()
        }
    }

    /* IMPORTAR PDF: cada hoja del PDF se convierte en una página del diario */
    private val importPdfPick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importPdfPages(uri)
    }

    /* ESCANEAR: foto con la cámara → va al papel (recortable con el lazo) */
    private var scanUri: Uri? = null
    private val scanCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scanToPaper()
    }

    /* destinaciones SAF para las exportaciones */
    private val exportCreate = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val book = nb ?: return@registerForActivityResult
        val u = uri ?: return@registerForActivityResult
        when (exportKind) {
            1 -> ExportUtil.writeImage(this, u, canvas.renderPageBitmap(canvas.pageIndex, 1400), true)
            2 -> ExportUtil.writeImage(this, u, canvas.renderPageBitmap(canvas.pageIndex, 1400), false)
            3 -> ExportUtil.writePdf(this, u, { i -> canvas.renderPageBitmap(i, 1190, withDesk = false) }, canvas.pages.size)
            4 -> ExportUtil.writeTxt(this, u, ExportUtil.buildTxt(book, canvas.pages))
            else -> {
                // Word: SOLO el papel de cada página (imagen con todo su contenido)
                val imgs = ArrayList<ByteArray>()
                for (i in 0 until canvas.pages.size) {
                    val b = canvas.renderPageBitmap(i, 1240)
                    val bos = java.io.ByteArrayOutputStream()
                    b.compress(Bitmap.CompressFormat.PNG, 100, bos)
                    b.recycle()
                    imgs.add(bos.toByteArray())
                }
                ExportUtil.writeBytes(this, u, ExportUtil.buildDocxImages(imgs))
            }
        }
    }

    private val PEN_TOOLS = intArrayOf(
        Tools.PEN, Tools.MARKER, Tools.PENCIL, Tools.HIGHLIGHTER, Tools.DOTTED, Tools.NEON, Tools.BEVEL, Tools.HILITER, Tools.PEARL)

    private val TOOLS = arrayOf(
        Triple(Tools.SELECT, R.drawable.ic_cursor, "Cursor: toca un objeto y muévelo con libertad"),
        Triple(Tools.LASSO, R.drawable.ic_lasso, "Lazo / seleccionar zona"),
        Triple(Tools.PEN, R.drawable.ic_pen, "Escribir (bolígrafo, rotulador, lápiz, subrayador, puntitos, brillado)"),
        Triple(Tools.ERASER, R.drawable.ic_eraser, "Goma"),
        Triple(Tools.TEXT, R.drawable.ic_text, "Texto"),
        Triple(Tools.SHAPE, R.drawable.ic_shape, "Añadir formas: línea, rectángulo, círculo, triángulo, flecha, estrella"),
        Triple(Tools.RULER, R.drawable.ic_ruler, "Regla: guía para líneas derechas"),
        Triple(Tools.IMAGE, R.drawable.ic_image, "Imagen"),
        Triple(Tools.LINK, R.drawable.ic_link, "Enlace"),
        Triple(Tools.STICKER, R.drawable.ic_sticker, "Stickers")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_KNotes_Editor)
        com.knotes.app.ui.Gender.applyTheme(this)
        setContentView(R.layout.activity_editor)

        canvas = findViewById(R.id.canvas)
        canvas.host = this
        pageLabel = findViewById(R.id.page_label)
        popupSel = findViewById(R.id.selection_popup)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        toolRow = findViewById(R.id.tool_row)

        val id = intent.getStringExtra("id")
        val book: Notebook? = if (id != null) Store.byId(id) else null
        if (book == null) { finish(); return }
        nb = book
        canvas.setup(book, Store.loadPages(book), 0)
        canvas.scrollDir = Prefs.scrollDir   // dirección de desplazamiento del diario
        // viene del menú ⋮ del inicio → «Importar PDF»
        if (intent.getBooleanExtra("importPdf", false)) window.decorView.post { importPdfPick.launch("application/pdf") }
        // píldora con el nombre del diario (la configuración está en el botón ⋮ de al lado)
        findViewById<TextView>(R.id.tv_title).text = book.title

        // barra de herramientas compacta (cabe api 36 en móviles estrechos)
        for (t in TOOLS) {
            val b = ImageButton(this).apply {
                setImageResource(t.second)
                background = getDrawable(R.drawable.bg_circle_white)
                imageTintList = android.content.res.ColorStateList.valueOf(0xFF3A3540.toInt())
                contentDescription = t.third
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).also { it.setMargins(dp(3), 0, dp(3), 0) }
                setPadding(dp(9), dp(9), dp(9), dp(9))
            }
            b.setOnClickListener {
                // si ya es una pluma activa, conserva el tipo (rotulador/lápiz/subrayador/…)
                if (t.first == Tools.PEN && canvas.tool in PEN_TOOLS) {
                    selectTool(canvas.tool, b)
                } else selectTool(t.first, b)
            }
            b.tag = t.first
            // MANTENER PRESIONADO el botón 🖊 de stickers → vuelve a abrir el selector
            if (t.first == Tools.STICKER) {
                b.setOnLongClickListener {
                    Panels.stickerPicker(this, canvas) { pickImage.launch("image/*") }
                    true
                }
            }
            toolRow.addView(b)
        }
        selectTool(Tools.PEN, null)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            saveAll(false)
            finish()
        }
        btnUndo.setOnClickListener { canvas.undo() }
        btnRedo.setOnClickListener { canvas.redo() }
        findViewById<ImageButton>(R.id.btn_save).setOnClickListener { saveAll(true) }
        // acentos del modo sexo en el editor (rosa ↔ azul)
        findViewById<ImageButton>(R.id.btn_save).imageTintList =
            android.content.res.ColorStateList.valueOf(Gender.accentDeep(this))
        findViewById<ImageButton>(R.id.btn_float_pen).background = getDrawable(Gender.circleBg())
        val pageTint = android.content.res.ColorStateList.valueOf(Gender.accentDeep(this))
        findViewById<ImageButton>(R.id.btn_prev_page).imageTintList = pageTint
        findViewById<ImageButton>(R.id.btn_next_page).imageTintList = pageTint
        findViewById<ImageButton>(R.id.btn_more).setOnClickListener { v -> moreMenu(v) }

        findViewById<ImageButton>(R.id.btn_prev_page).setOnClickListener { canvas.goToPage(canvas.pageIndex - 1) }
        findViewById<ImageButton>(R.id.btn_next_page).setOnClickListener { canvas.goToPage(canvas.pageIndex + 1) }
        // ACCESO RÁPIDO: nueva página CON el estilo que estás usando ahora mismo
        findViewById<ImageButton>(R.id.btn_quick_page).setOnClickListener {
            val cur = canvas.curPage
            canvas.addPage(cur.template, cur.decor, cur.paper)
            Toast.makeText(this, "Página ${canvas.pages.size} con el estilo en uso ✓ (mismo papel y adornos)", Toast.LENGTH_SHORT).show()
        }

        val float = findViewById<ImageButton>(R.id.btn_float_pen)
        setupFloatPen(float)
        restoreFloatPos(float)

        findViewById<ImageButton>(R.id.sel_delete).setOnClickListener {
            canvas.deleteSelection(); popupSel.visibility = View.GONE
        }
        findViewById<ImageButton>(R.id.sel_close).setOnClickListener {
            canvas.clearSelection(); popupSel.visibility = View.GONE
        }
        // AGRANDAR / ACHICAR / GIRAR la selección a gusto
        findViewById<ImageButton>(R.id.sel_grow).setOnClickListener { canvas.selectionScale(1.15f) }
        findViewById<ImageButton>(R.id.sel_shrink).setOnClickListener { canvas.selectionScale(1f / 1.15f) }
        findViewById<ImageButton>(R.id.sel_rot_r).setOnClickListener { canvas.selectionRotate(15f) }
        findViewById<ImageButton>(R.id.sel_rot_l).setOnClickListener { canvas.selectionRotate(-15f) }

        setupPenBar()
        setupTextBar()
    }

    /* ================== BARRA INFERIOR FIJA (estilo captura) ==================
       ✎ punta azul + «Alejar» · bandeja de plumas (8) + regla · goma ·
       5 círculos de grosor · 4 colores rápidos + paleta */

    private val penBarBtns = ArrayList<Pair<Int, ImageButton>>()   // (tool, botón pluma)
    private val sizeBtns = ArrayList<ImageButton>()
    private val colorBtns = ArrayList<View>()
    private lateinit var eraserBar: ImageButton
    private lateinit var rulerBar: ImageButton

    private fun setupPenBar() {
        // orden de la captura: Marcador · Lápiz · Bolígrafo · Subrayar · Puntitos · Neón · 3D · Resaltador
        val pens = intArrayOf(Tools.MARKER, Tools.PENCIL, Tools.PEN, Tools.HIGHLIGHTER,
            Tools.DOTTED, Tools.NEON, Tools.BEVEL, Tools.HILITER, Tools.PEARL)
        val icons = intArrayOf(R.drawable.ic_p_marker, R.drawable.ic_p_pencil, R.drawable.ic_p_ball,
            R.drawable.ic_p_high, R.drawable.ic_p_dotted, R.drawable.ic_p_neon, R.drawable.ic_p_bevel, R.drawable.ic_p_hilite, R.drawable.ic_p_pearl)
        val names = arrayOf("Marcador", "Lápiz", "Bolígrafo", "Subrayar",
            "Puntitos", "Resaltado Neón", "3D Biselado", "Resaltador", "3D Perla")
        val barPens = findViewById<LinearLayout>(R.id.bar_pens)
        for (i in pens.indices) {
            val b = ImageButton(this).apply {
                setImageResource(icons[i])
                background = getDrawable(R.drawable.bg_pen_touch)
                imageTintList = null                      // cada pluma conserva SU punta de color
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = names[i]
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(64)).also { it.setMargins(dp(3), 0, dp(3), 0) }
                setPadding(dp(7), 0, dp(7), 0)
            }
            b.setOnClickListener { selectToolBar(pens[i]) }
            b.setOnLongClickListener { selectTool(pens[i], null); Panels.penOptions(this, canvas); refreshPenBar(); true }
            barPens.addView(b)
            penBarBtns.add(pens[i] to b)
        }

        // —— REGLA (al final de la bandeja) ——
        rulerBar = ImageButton(this).apply {
            setImageResource(R.drawable.ic_p_ruler)
            background = getDrawable(R.drawable.bg_pen_touch)
            imageTintList = null
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription = "Regla"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(64)).also { it.setMargins(dp(3), 0, dp(3), 0) }
            setPadding(dp(7), 0, dp(7), 0)
        }
        rulerBar.setOnClickListener {
            canvas.rulerOn = !canvas.rulerOn
            Toast.makeText(this, if (canvas.rulerOn) "Regla 📐 activada" else "Regla apagada ✓", Toast.LENGTH_SHORT).show()
            refreshPenBar()
        }
        barPens.addView(rulerBar)

        // —— GOMA (aparte, con su tapa rosa) ——
        eraserBar = findViewById(R.id.btn_eraser_bar)
        eraserBar.setOnClickListener {
            if (canvas.tool == Tools.ERASER) Panels.eraserOptions(this, canvas)
            else selectTool(Tools.ERASER, null)
            refreshPenBar()
        }

        // —— punta azul ➜ resto de herramientas (cursor, lazo, texto, formas, imagen, enlace, stickers) ——
        findViewById<ImageButton>(R.id.btn_nib).setOnClickListener {
            Panels.toolsPopup(this, canvas, { pickImage.launch("image/*") },
                { Panels.stickerPicker(this, canvas) { pickImage.launch("image/*") } })
        }

        // —— GROSORES: 5 círculos blancos con una línea cada vez más gruesa ——
        val sizesRow = findViewById<LinearLayout>(R.id.bar_sizes)
        val dashes = intArrayOf(R.drawable.ic_size_dash1, R.drawable.ic_size_dash2, R.drawable.ic_size_dash3,
            R.drawable.ic_size_dash4, R.drawable.ic_size_dash5)
        for (k in dashes.indices) {
            val b = ImageButton(this).apply {
                setImageResource(dashes[k])
                background = getDrawable(R.drawable.bg_size_circle)
                imageTintList = null
                contentDescription = "Grosor ${k + 1}"
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also { it.setMargins(dp(4), 0, dp(4), 0) }
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            b.setOnClickListener { canvas.penWidth = barSizeValue(k); refreshPenBar() }
            sizesRow.addView(b)
            sizeBtns.add(b)
        }

        // —— COLORES RÁPIDOS: violeta · rosa · celeste · oliva + paleta ——
        val colorsRow = findViewById<LinearLayout>(R.id.bar_colors)
        val cols = intArrayOf(0xFF932FFF.toInt(), 0xFFFF69A3.toInt(), 0xFF51B9FF.toInt(), 0xFFD2D320.toInt())
        for (c in cols) {
            val dot = View(this).apply {
                tag = c
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also { it.setMargins(dp(4), 0, dp(4), 0) }
            }
            dot.setOnClickListener { canvas.penColor = c; refreshPenBar() }
            colorsRow.addView(dot)
            colorBtns.add(dot)
        }
        val palette = ImageButton(this).apply {
            setImageResource(R.drawable.ic_palette_color)
            background = getDrawable(R.drawable.bg_size_circle)
            imageTintList = null
            contentDescription = "Todos los colores"
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).also { it.setMargins(dp(4), 0, dp(4), 0) }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        palette.setOnClickListener {
            Panels.colorDialog(this, canvas.penColor, "🎨 Color del lápiz") { c ->
                canvas.penColor = c
                refreshPenBar()
            }
        }
        colorsRow.addView(palette)

        // —— chip ACERCAR / ALEJAR ——
        findViewById<TextView>(R.id.chip_zoom).setOnClickListener { v ->
            val zoomed = canvas.toggleZoom()
            (v as TextView).text = if (zoomed) "Alejar" else "Acercar"
        }

        refreshPenBar()
    }

    private fun barSizeValue(i: Int): Float =
        when (i.coerceIn(0, 4)) { 0 -> 3f; 1 -> 6f; 2 -> 10f; 3 -> 16f; else -> 26f }

    /** elige la pluma desde la BARRA (silencioso: sin abrir opciones). */
    private fun selectToolBar(tool: Int) {
        selectTool(tool, null)
        refreshPenBar()
    }

    private fun refreshPenBar() {
        if (!this::eraserBar.isInitialized) return
        val lift = -dp(9).toFloat()
        for ((t, b) in penBarBtns) b.translationY = if (t == canvas.tool) lift else 0f
        eraserBar.translationY = if (canvas.tool == Tools.ERASER) lift else 0f
        rulerBar.translationY = if (canvas.rulerOn) lift else 0f
        // grosor MÁS PARECIDO se ilumina
        var bestI = 0; var bestD = Float.MAX_VALUE
        for (i in sizeBtns.indices) {
            val d = kotlin.math.abs(canvas.penWidth - barSizeValue(i))
            if (d < bestD) { bestD = d; bestI = i }
        }
        for (i in sizeBtns.indices) {
            sizeBtns[i].background = getDrawable(if (i == bestI) R.drawable.bg_size_circle_on else R.drawable.bg_size_circle)
        }
        // punto de color ACTIVO (coincidencia EXACTA se rodea de rosa)
        for (v in colorBtns) {
            val c = v.tag as Int
            val on = penColorMatches(c)
            v.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(c)
                setStroke(if (on) dp(3) else dp(2), if (on) com.knotes.app.ui.Gender.accent(this@EditorActivity) else 0xFF3A3A3A.toInt())
            }
        }
    }

    private fun penColorMatches(c: Int) = (canvas.penColor and 0xFFFFFF) == (c and 0xFFFFFF)

    /* ================== BARRA DE ESCRITURA INLINE ================== */

    private var textBarShown = false
    private var textTarget: com.knotes.app.data.TextElement? = null
    private var textPt = Pt(500f, 400f)

    private fun setupTextBar() {
        val bar = findViewById<View>(R.id.text_bar)
        val input = findViewById<android.widget.EditText>(R.id.text_input)
        // scroll interno: nunca pierdes de vista lo escrito arriba
        input.maxHeight = (150 * resources.displayMetrics.density).toInt()
        input.setHorizontallyScrolling(false)
        input.movementMethod = android.text.method.ScrollingMovementMethod()
        findViewById<ImageButton>(R.id.text_done).setOnClickListener {
            commitTextBar(input)
        }
        findViewById<ImageButton>(R.id.text_cancel).setOnClickListener {
            textBarShown = false
            bar.visibility = View.GONE
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)
        }
    }

    private fun commitTextBar(input: android.widget.EditText) {
        val txt = input.text.toString().trim()
        val bar = findViewById<View>(R.id.text_bar)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        textBarShown = false
        bar.visibility = View.GONE
        if (txt.isNotEmpty()) {
            val target = textTarget
            if (target != null) canvas.replaceTextElement(target, txt, target.size, canvas.penColor)
            else canvas.addTextElement(txt, 34f, canvas.penColor, textPt.x, textPt.y)
            saveAll(false)
        } else if (textTarget != null) {
            canvas.deleteElement(textTarget!!)
        }
        textTarget = null
        input.setText("")
    }

    /** el host pide abrir el escritor: barra INLINE con las líneas del papel */
    private fun showTextBar(el: com.knotes.app.data.TextElement?, pt: Pt) {
        textTarget = el
        textPt = pt
        val bar = findViewById<View>(R.id.text_bar)
        val input = findViewById<TextView>(R.id.text_input) as android.widget.EditText
        input.setText(el?.text ?: "")
        textBarShown = true
        bar.visibility = View.VISIBLE
        input.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    /* ================== LÁPIZ FLOTANTE: mover + acceso rápido ================== */

    private fun setupFloatPen(float: ImageButton) {
        // Toque RÁPIDO → abre la configuración (rueda de color/tipo/grosor).
        // ARRASTRAR → SOLO mueve el botón (no habilita nada).
        // Mantener presionado → NO abre nada (así se pedía).
        var downX = 0f; var downY = 0f
        var tx = 0f; var ty = 0f
        var dragging = false
        var downAt = 0L
        val slop = 18 * resources.displayMetrics.density
        float.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downAt = android.os.SystemClock.uptimeMillis()
                    downX = e.rawX; downY = e.rawY
                    tx = v.translationX; ty = v.translationY
                    dragging = false
                    v.isPressed = true
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dragging || Math.hypot(dx.toDouble(), dy.toDouble()) > slop) {
                        dragging = true
                        v.isPressed = false
                        v.translationX = tx + dx
                        v.translationY = ty + dy
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (dragging) {
                        val w = (v.rootView.width - v.width).coerceAtLeast(1)
                        val h = (v.rootView.height - v.height).coerceAtLeast(1)
                        com.knotes.app.data.Prefs.floatPenFx = (v.x / w).coerceIn(0f, 1f)
                        com.knotes.app.data.Prefs.floatPenFy = (v.y / h).coerceIn(0f, 1f)
                    } else if (android.os.SystemClock.uptimeMillis() - downAt < 350L) {
                        // solo un toque rápido habilita las herramientas
                        Panels.penOptions(this, canvas)
                    }
                    true
                }
                else -> { v.isPressed = false; dragging = false; true }
            }
        }
    }

    private fun restoreFloatPos(float: ImageButton) {
        val fx = com.knotes.app.data.Prefs.floatPenFx
        val fy = com.knotes.app.data.Prefs.floatPenFy
        if (fx >= 0f) {
            float.post {
                val w = (float.rootView.width - float.width).coerceAtLeast(1)
                val h = (float.rootView.height - float.height).coerceAtLeast(1)
                float.translationX = fx * w - float.left
                float.translationY = fy * h - float.top
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun selectTool(tool: Int, pressed: ImageButton?, silent: Boolean = false) {
        canvas.tool = tool
        // al pulsar los botones REALES: goma muestra opciones;
        // el lápiz abre SIEMPRE sus opciones (tipo, color, grosor) — es un solo botón
        if (pressed != null && tool == Tools.ERASER) Panels.eraserOptions(this, canvas)
        if (pressed != null && tool in PEN_TOOLS) Panels.penOptions(this, canvas)
        // FORMAS: elegir qué forma dibujar (se dibuja arrastrando en el papel)
        if (pressed != null && tool == Tools.SHAPE) Panels.shapePicker(this, canvas)
        // REGLA: tocar el botón la enciende/apaga; los trazos cerca de ella salen DERECHOS
        if (pressed != null && tool == Tools.RULER) {
            canvas.rulerOn = !canvas.rulerOn
            Toast.makeText(this,
                if (canvas.rulerOn) "Regla activada 📐 — arrastra el cuerpo para moverla, el punto azul para girarla; los trazos que empiezan cerca se pegan"
                else "Regla apagada ✓ (trazos libres)", Toast.LENGTH_LONG).show()
        }
        // stickers: toque corto en el botón → si hay sticker activo lo APAGA,
        // si no hay → abre el selector. (Mantener presionado SIEMPRE lo abre)
        if (pressed != null && tool == Tools.STICKER) {
            if (canvas.stickerPending != null) {
                canvas.stickerPending = null
                Toast.makeText(this, "Modo sticker apagado ✔ (los ya puestos siguen ahí)", Toast.LENGTH_SHORT).show()
            } else {
                Panels.stickerPicker(this, canvas) { pickImage.launch("image/*") }
            }
        }
        for (i in 0 until toolRow.childCount) {
            val c = toolRow.getChildAt(i) as ImageButton
            val tagged = (c.tag as Int)
            val active = tagged == tool ||
                (tagged == Tools.PEN && tool in PEN_TOOLS) ||
                (tagged == Tools.RULER && canvas.rulerOn)
            c.background = getDrawable(if (active) Gender.circleBg() else R.drawable.bg_circle_white)
        }
        canvas.clearSelection()
        refreshPenBar()
    }

    private fun export(book: Notebook, kind: Int) {
        exportKind = kind
        val safe = ExportUtil.sanit(book.title)
        val name = when (kind) {
            1 -> "KNotes_${safe}_p${canvas.pageIndex + 1}.png"
            2 -> "KNotes_${safe}_p${canvas.pageIndex + 1}.jpg"
            3 -> "KNotes_${safe}.pdf"
            4 -> "KNotes_${safe}.txt"
            else -> "KNotes_${safe}.docx"
        }
        exportCreate.launch(name)
    }

    private fun moreMenu(v: View) {
        val pm = PopupMenu(this, v)
        // —— ORDEN pedido —— Nueva Página · Duplicar · Importar PDF · Escanear ·
        // Estilo de página · Ir a la página · Exportar · Widget · Dirección ·
        // Consejo · Compartir · Tutoriales · Eliminar
        pm.menu.add("💾 Guardar")
        pm.menu.add("🆕 Nueva página…")
        pm.menu.add("✨ Duplicar página")
        pm.menu.add("📕 Importar PDF")
        pm.menu.add("📸 Escanear (cámara)")
        pm.menu.add("🎨 Cambiar estilo de página")
        pm.menu.add("🖌 Cambiar color del papel")
        pm.menu.add("🔢 Ir a la página…")
        val exportMenu = pm.menu.addSubMenu("📤 Exportar…")
        exportMenu.add("📄 Página actual → PNG")
        exportMenu.add("🖼 Página actual → JPG")
        exportMenu.add("📕 Diario completo → PDF")
        exportMenu.add("📝 Diario completo → TXT")
        exportMenu.add("📘 Diario completo → Word (.docx)")
        pm.menu.add("➕ Añadir widget")
        pm.menu.add("🧭 Dirección de desplazamiento")
        pm.menu.add("💡 Consejo de uso")
        pm.menu.add("📨 Compartir página")
        pm.menu.add("🎓 Tutoriales")
        pm.menu.add("🗑 Eliminar esta página")
        pm.menu.add("🗂 Ver páginas")
        pm.menu.add("🧹 Limpiar página")
        pm.menu.add("ℹ️ Acerca del guardado editable")
        pm.setOnMenuItemClickListener { item ->
            val book = nb ?: return@setOnMenuItemClickListener true
            when (item.title?.toString()) {
                "🆕 Nueva página…" -> Panels.pageStylePicker(this, { t, dec, pap, real ->
                    canvas.addPage(t, dec, pap)
                    if (real > 0) canvas.applyRealTemplate(real)
                }, "Nueva página", canvas.curPage.template, 0, canvas.curPage.paper)
                "💾 Guardar" -> saveAll(true)
                "✨ Duplicar página" -> {
                    canvas.duplicatePage()
                    Toast.makeText(this, "Página duplicada con TODO su contenido ✓ (ahora estás en la copia)", Toast.LENGTH_SHORT).show()
                }
                "📕 Importar PDF" -> importPdfPick.launch("application/pdf")
                "📸 Escanear (cámara)" -> startScan()
                "🎨 Cambiar estilo de página" -> Panels.pageStylePicker(this, { t, dec, pap, real ->
                    canvas.setTemplate(t); canvas.setPageDecor(dec); canvas.setPaperColor(pap)
                    if (real > 0) canvas.applyRealTemplate(real)
                }, "Cambiar estilo de página", canvas.curPage.template, canvas.curPage.decor, canvas.curPage.paper)
                "🖌 Cambiar color del papel" -> showPaperOptions()
                "🔢 Ir a la página…" -> gotoPageDialog()
                "📄 Página actual → PNG" -> export(book, 1)
                "🖼 Página actual → JPG" -> export(book, 2)
                "📕 Diario completo → PDF" -> export(book, 3)
                "📝 Diario completo → TXT" -> export(book, 4)
                "📘 Diario completo → Word (.docx)" -> export(book, 5)
                "➕ Añadir widget" -> widgetInfo()
                "🧭 Dirección de desplazamiento" -> scrollDirectionDialog()
                "💡 Consejo de uso" -> usageTip()
                "📨 Compartir página" -> sharePage()
                "🎓 Tutoriales" -> tutorials()
                "🗑 Eliminar esta página" -> if (canvas.pages.size > 1) {
                    confirm("¿Eliminar la página?", "Puedes deshacer tras borrar.") { canvas.deleteCurrentPage() }
                } else Toast.makeText(this, "Debe existir al menos una página", Toast.LENGTH_SHORT).show()
                "🗂 Ver páginas" -> Panels.pagesOverview(this, canvas)
                "🧹 Limpiar página" -> confirm("¿Vaciar la página?", "Se borrará todo su contenido (puedes deshacer).") { canvas.clearPage() }
                else -> aboutEdits()
            }
            true
        }
        pm.show()
    }

    /* ================== importar PDF como páginas ================== */

    private fun importPdfPages(uri: Uri) {
        val book = nb ?: return
        var pfd: android.os.ParcelFileDescriptor? = null
        try {
            pfd = contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) { Toast.makeText(this, "No se pudo abrir ese PDF 😕", Toast.LENGTH_SHORT).show(); return }
            val renderer = PdfRenderer(pfd)
            val count = minOf(renderer.pageCount, 12)
            val total = renderer.pageCount
            var added = 0
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val scale = 1200f / maxOf(page.width, page.height).coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val t = Store.importBitmap(book, bmp)
                if (t != null) { canvas.addPdfPage(t.first, t.second, t.third); added++ }
                page.close()
            }
            renderer.close()
            if (added > 0) {
                canvas.goToPage(canvas.pages.size - added)
                val extra = if (total > 12) " (se importaron las primeras 12 de $total páginas)" else ""
                Toast.makeText(this, "PDF añadido: $added páginas nuevas ✓$extra", Toast.LENGTH_LONG).show()
            } else Toast.makeText(this, "No se pudo importar ese PDF 😕", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Ese PDF está protegido o dañado 😕", Toast.LENGTH_LONG).show()
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /* ================== escanear documento ================== */

    private fun startScan() {
        val dir = File(cacheDir, "export"); dir.mkdirs()
        val f = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        scanUri = uri
        try {
            scanCapture.launch(uri)
        } catch (_: Exception) {
            Toast.makeText(this, "No hay cámara disponible en este dispositivo 😕", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanToPaper() {
        val book = nb ?: return
        val uri = scanUri ?: return
        val t = Store.importImage(this, book, uri)
        if (t != null) {
            canvas.addImageElement(t.first, t.second, t.third)
            Toast.makeText(this, "📸 Escaneo añadido: recórtalo/ajústalo con el lazo", Toast.LENGTH_LONG).show()
        } else Toast.makeText(this, "No se pudo importar el escaneo 😕", Toast.LENGTH_SHORT).show()
    }

    /* ================== ir a la página N ================== */

    private fun gotoPageDialog() {
        val input = EditText(this).apply {
            hint = "Número de página (1…${canvas.pages.size})"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(android.graphics.Color.parseColor("#2B2B2B"))
            setHintTextColor(android.graphics.Color.parseColor("#9A9288"))
            background = getDrawable(R.drawable.bg_cell_white)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        Dialogs.light(this)
            .setTitle("🔢 Ir a la página")
            .setView(input)
            .setPositiveButton("Ir") { _, _ ->
                val n = input.text.toString().toIntOrNull()
                if (n == null || n < 1 || n > canvas.pages.size)
                    Toast.makeText(this, "Ese número no existe (1…${canvas.pages.size})", Toast.LENGTH_SHORT).show()
                else canvas.goToPage(n - 1)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /* ================== widget en el escritorio ================== */

    private fun widgetInfo() {
        Dialogs.light(this)
            .setTitle("➕ Añadir widget")
            .setMessage("El widget de KNotes pone tu diario a UN toque del escritorio\n\n1. Sal del editor y ve a la pantalla de inicio.\n2. Mantén presionado un espacio vacío → «Widgets».\n3. Busca «KNotes» y arrástralo.\n\nTocándolo abres directamente tus diarios 📔")
            .setPositiveButton("Entendido", null)
            .show()
    }

    /* ================== dirección de desplazamiento ================== */

    private fun scrollDirectionDialog() {
        val opts = arrayOf("📱 Verticales — desliza ARRIBA/ABAJO con dos dedos para cambiar de página",
            "↔️ Horizontales — desliza a los LADOS (estilo libro)")
        Dialogs.light(this)
            .setTitle("🧭 Dirección de desplazamiento")
            .setSingleChoiceItems(opts, Prefs.scrollDir) { d, w ->
                Prefs.scrollDir = w
                canvas.scrollDir = w
                Toast.makeText(this, "Dirección ${if (w == 0) "VERTICAL" else "HORIZONTAL"} aplicada ✓", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /* ================== consejos / tutoriales ================== */

    private fun usageTip() {
        Dialogs.light(this)
            .setTitle("💡 Consejo de uso")
            .setMessage(if (canvas.pages.size <= 1)
                "Dibuja o escribe en esta página y luego toca «+» junto al número de página: se crea una hoja NUEVA con el mismo estilo de papel y adornos que estás usando, sin configurar nada otra vez. 📝"
            else
                "Para pasar de página con los dedos: pellizca «fijo» dos dedos y desliza (la dirección la eliges en «Dirección de desplazamiento»). Los trazos que empiezan cerca de la regla 📐 salen perfectamente derechos.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun tutorials() {
        Dialogs.light(this)
            .setTitle("🎓 Tutoriales rápidos")
            .setItems(arrayOf(
                "1) Lápiz: 6 tipos con estilo distinto — botón 🖊 flotante",
                "2) Regla 📐: actívala y los trazos cercanos salen derechos",
                "3) Formas ⬟: elige la forma y arrastra sobre el papel",
                "4) Dos dedos = zoom · y también CAMBIO DE PÁGINA",
                "5) PDF 📕: menú ⋮ → Exportar (solo la hoja, sin fondo)",
                "6) Todo lo guardado se puede borrar/mover después: goma o lazo"
            ), { _, _ -> })
            .setPositiveButton("OK", null)
            .show()
    }

    /* ================== compartir página ================== */

    private fun sharePage() {
        try {
            val bmp = canvas.renderPageBitmap(canvas.pageIndex, 1200)
            val dir = File(cacheDir, "export"); dir.mkdirs()
            val f = File(dir, "KNotes_pagina_${canvas.pageIndex + 1}.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "Compartir página 📤"))
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo compartir la página 😕", Toast.LENGTH_SHORT).show()
        }
    }

    private fun aboutEdits() {
        Dialogs.light(this)
            .setTitle("Trazos siempre editables")
            .setMessage("KNotes guarda trazos, texto, imágenes, stickers y enlaces como objetos vectoriales en JSON. Al salir y volver, la goma y el lazo siguen funcionando: borra por zona o trazo completo, mueve, escala o elimina lo que quieras.")
            .setPositiveButton("OK", null).show()
    }

    private fun showPaperOptions() {
        Dialogs.light(this)
            .setTitle("Color del papel (esta página)")
            .setItems(arrayOf("Crema", "Blanco", "Oscuro", "Rosa 🌸", "Menta 🍃", "Lavanda 🌷", "Celeste 🧊")) { _, w ->
                canvas.setPaperColor(w)
            }
            .show()
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        Dialogs.light(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Sí") { _, _ -> action() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /* ================== guardar ================== */

    private fun saveAll(toast: Boolean) {
        val book = nb ?: return
        val pages = canvas.pages
        Store.savePages(book, pages)
        Store.saveMeta(book)
        if (toast) Toast.makeText(this, "Guardado 💾", Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        saveAll(false)
    }

    /* ================== CanvasHost ================== */

    override fun onPageChanged(idx: Int, count: Int) {
        pageLabel.text = "${idx + 1}/$count"
        val ind = findViewById<View>(R.id.page_indicator)
        ind.visibility = View.VISIBLE   // siempre visible: el «+» crea página rápida aun con una sola hoja
    }

    override fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean) {
        // como la captura: círculo rosa intenso si se puede, rosa pálido si no
        btnUndo.background = getDrawable(if (canUndo) R.drawable.bg_undo_on else R.drawable.bg_undo_off)
        btnRedo.background = getDrawable(if (canRedo) R.drawable.bg_undo_on else R.drawable.bg_undo_off)
        btnUndo.alpha = if (canUndo) 1f else 0.6f
        btnRedo.alpha = if (canRedo) 1f else 0.6f
    }

    override fun onSelectionChanged(hasSel: Boolean) {
        popupSel.visibility = if (canvas.selectedCount() > 0) View.VISIBLE else View.GONE
    }

    override fun editText(el: TextElement?, pt: Pt) {
        showTextBar(el, pt)   // escritura INLINE: sigue las líneas del papel
    }

    override fun pickImage() {
        pickImage.launch("image/*")
    }

    override fun linkAt(el: LinkElement?, pt: Pt) {
        Panels.linkDialog(this, canvas, el, pt)
    }

    override fun openStickers() {
        // stickers propios: emojis guardados + CARGAR imagen como sticker
        Panels.stickerPicker(this, canvas) { pickImage.launch("image/*") }
    }

    override fun saveNow() {
        saveAll(true)
    }

    override fun onPageSwipe(dir: Int) {
        // dos dedos deslizaron: 0 = siguiente, 1 = anterior
        canvas.goToPage(canvas.pageIndex + if (dir == 0) 1 else -1)
    }
}
