package com.knotes.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.knotes.app.R
import com.knotes.app.canvas.CanvasPageView
import com.knotes.app.canvas.Decors
import com.knotes.app.canvas.Tools
import com.knotes.app.data.LinkElement
import com.knotes.app.data.Pt
import com.knotes.app.data.Shape
import com.knotes.app.data.TextElement

/* ============================================================
   Paneles del editor: lápiz, goma, texto, enlace, stickers, páginas
   Todos en tema CLARO fijo → legibles en modo oscuro del sistema.
   ============================================================ */
object Panels {

    fun label(text: String, ctx: Context): TextView = TextView(ctx).apply {
        this.text = text
        this.textSize = 13f
        this.setTextColor(Color.parseColor("#6E5D66"))
        this.setPadding(0, 18, 0, 8)
    }

    /** Envuelve contenido en un ScrollView: NUNCA se esconde una sección
     *  (antes el «Fondos» y la «Tipografía» quedaban fuera de la pantalla). */
    fun scrollWrap(ctx: Context, content: View): android.widget.ScrollView {
        return android.widget.ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = true
            addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
        }
    }

    /** Selector de color REUTILIZABLE (barra inferior → botón 🎨): rejilla + RGB en texto. */
    fun colorDialog(ctx: Context, initial: Int, title: String = "🎨 Color del lápiz", block: (Int) -> Unit) {
        var chosen = initial
        val big = View(ctx)
        fun refreshBig(c: Int) {
            big.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(c)
                setStroke(3, Color.parseColor("#2B2B2B"))
            }
        }
        refreshBig(initial)
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 22), dp(ctx, 10), dp(ctx, 22), 0)
            addView(TextView(ctx).apply {
                text = "Elegido:"; textSize = 13f; setTextColor(Color.parseColor("#6E5D66"))
            })
            addView(big, LinearLayout.LayoutParams(dp(ctx, 56), dp(ctx, 56)).also { it.setMargins(dp(ctx, 14), 0, 0, 0) })
        }
        cell.addView(TextView(ctx).apply {
            text = "  ·  toca un color o escribe el código ↓"; textSize = 12f
            setTextColor(Color.parseColor("#9A9186"))
        })
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            addView(cell)
            addView(colorGrid(ctx, initial) { c -> chosen = c; refreshBig(c) })
        }
        Dialogs.light(ctx)
            .setTitle(title)
            .setView(scrollWrap(ctx, wrap))
            .setPositiveButton("Aplicar") { _, _ -> block(chosen) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /* ---------- paleta AMPLIA + feedback al elegir ---------- */
    private val WIDE_PALETTE = intArrayOf(
        Color.parseColor("#1C1C22"), Color.parseColor("#2E2A33"), Color.parseColor("#55555F"),
        Color.parseColor("#8A8A93"), Color.parseColor("#C0BFC7"), Color.parseColor("#FFFFFF"),
        Color.parseColor("#E8494E"), Color.parseColor("#C4383E"), Color.parseColor("#EE7C32"),
        Color.parseColor("#F5A65E"), Color.parseColor("#E9C400"), Color.parseColor("#F2CE5E"),
        Color.parseColor("#8A6D2B"), Color.parseColor("#55B24B"), Color.parseColor("#8CC98F"),
        Color.parseColor("#5FA366"), Color.parseColor("#2FA7C9"), Color.parseColor("#8CE0F2"),
        Color.parseColor("#3956C9"), Color.parseColor("#4A90D9"), Color.parseColor("#7FB3E8"),
        Color.parseColor("#1B3A6B"), Color.parseColor("#9C2BC9"), Color.parseColor("#B58ECC"),
        Color.parseColor("#6E3FA3"), Color.parseColor("#E9619A"), Color.parseColor("#EE8CAC"),
        Color.parseColor("#F7C6D8"), Color.parseColor("#8A5A3A"), Color.parseColor("#B98A6A"),
        Color.parseColor("#6B4423"), Color.parseColor("#D4B48C"), Color.parseColor("#FCEAEE"),
        Color.parseColor("#E7E0D0"), Color.parseColor("#FBEEDA"), Color.parseColor("#C9C3B4")
    )

    private fun smallRing(ctx: Context, c: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(c)
            setStroke(2, Color.parseColor("#C9B8A8"))
        }

    /** Rejilla de colores con VISTA clara (círculos con borde), RGB en texto
     *  (#hex o "R,G,B") + deslizadores. Paleta legible en claro/oscuro. */
    fun colorGrid(ctx: Context, initial: Int, onPick: (Int) -> Unit): View {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        var current = initial

        val preview = View(ctx)
        val hex = TextView(ctx).apply {
            text = "#%06X".format(initial and 0xFFFFFF)
            textSize = 12f; setTextColor(Color.parseColor("#9A9186")); setPadding(dp(ctx, 8), 0, 0, 0)
        }
        val prevRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 2))
            addView(View(ctx).apply { background = smallRing(ctx, initial) },
                LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)))
            addView(TextView(ctx).apply {
                text = "Color elegido"; textSize = 12.5f; setTextColor(Color.parseColor("#6E5D66"))
                setPadding(dp(ctx, 8), 0, 0, 0)
            })
            addView(hex)
        }
        root.addView(prevRow)

        var selected: View? = null
        fun selRing() = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0x00000000)
            setStroke(4, Gender.accent(ctx))
        }
        fun updatePreview(c: Int) {
            preview.background = smallRing(ctx, c)
            hex.text = "#%06X".format(c and 0xFFFFFF)
        }
        fun pick(c: Int, markView: View?) {
            current = c
            // el círculo ANTERIOR vuelve a su color original (guardado en su tag)
            selected?.let { old ->
                val oc = (old.tag as? Int) ?: 0xFFFFFFFF.toInt()
                old.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(oc)
                    setStroke(2, if (0.299f * Color.red(oc) + 0.587f * Color.green(oc) + 0.114f * Color.blue(oc) < 150f)
                        Color.parseColor("#E8E2D8") else Color.parseColor("#8A8178"))
                }
            }
            selected = markView
            selected?.background = selRing()
            updatePreview(c)
        }

        val grid = GridLayout(ctx).apply { columnCount = 6; setPadding(0, dp(ctx, 8), 0, 0) }
        for (c in WIDE_PALETTE) {
            val cell = View(ctx).apply {
                tag = c   // para restaurar el color si deja de estar seleccionado
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(c)
                    setStroke(3, if (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c) < 150f)
                        Color.parseColor("#DED8CC") else Color.parseColor("#6E6560"))
                }
                setOnClickListener {
                    pick(c, this)
                    onPick(c)
                    animate()?.scaleX(1.25f)?.scaleY(1.25f)?.setDuration(80)
                        ?.withEndAction { animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(80)?.start() }
                }
            }
            grid.addView(cell, GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).also {
                it.width = dp(ctx, 44); it.height = dp(ctx, 44)
                it.setMargins(8, 8, 8, 8)
            })
            if (c == initial) { selected = cell; cell.background = selRing() }
        }
        root.addView(grid)

        /* color PERSONALIZADO: deslizadores + CÓDIGO EN TEXTO */
        root.addView(label("🎨 Tu color (desliza o escribe el código)", ctx))
        val customPreview = View(ctx).apply { background = smallRing(ctx, initial) }
        val customRow = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val bars = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        bars.addView(customPreview, LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 30)).also { it.setMargins(0, dp(ctx, 4), dp(ctx, 10), 0) })
        val sliders = ArrayList<SeekBar>(3)
        for (i in 0 until 3) {
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; weightSum = 1f }
            col.addView(TextView(ctx).apply {
                text = arrayOf("R", "G", "B")[i]; textSize = 10.5f
                setTextColor(Color.parseColor("#6E5D66")); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2))
            val sb = SeekBar(ctx).apply { max = 255; progress = when (i) { 0 -> Color.red(initial); 1 -> Color.green(initial); else -> Color.blue(initial) } }
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    val r = if (i == 0) p else Color.red(current)
                    val g = if (i == 1) p else Color.green(current)
                    val bl = if (i == 2) p else Color.blue(current)
                    current = Color.rgb(r, g, bl)
                    customPreview.background = smallRing(ctx, current)
                    updatePreview(current)
                    onPick(current)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb, LinearLayout.LayoutParams(-1, -2))
            bars.addView(col, LinearLayout.LayoutParams(0, -2, 1f).also { it.setMargins(6, 0, 6, 0) })
            sliders.add(sb)
        }
        customRow.addView(bars)

        // —— CÓDIGO de color: «255,010,090» o «#FF6E02» ——
        val codeRow = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val code = EditText(ctx).apply {
            hint = "R,G,B o #RRGGBB"
            setSingleLine(true); textSize = 13f
            setTextColor(Color.parseColor("#2B2B2B")); setHintTextColor(Color.parseColor("#9A9288"))
            background = ctx.getDrawable(R.drawable.bg_cell_white)
            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9))
        }
        codeRow.addView(code, LinearLayout.LayoutParams(0, -2, 1f).also { it.setMargins(0, dp(ctx, 8), dp(ctx, 8), 0) })
        codeRow.addView(TextView(ctx).apply {
            text = "Aplicar"; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 18f; setColor(Gender.accentDeep(ctx)) }
            setPadding(dp(ctx, 16), dp(ctx, 9), dp(ctx, 16), dp(ctx, 9))
            setOnClickListener {
                val t = code.text.toString().trim().replace(" ", "").replace("RGB:", "").replace("rgb:", "")
                val applied = try {
                    when {
                        t.startsWith("#") && t.length >= 7 -> Color.parseColor(t.substring(0, 7))
                        t.contains(",") -> {
                            val p = t.split(",").map { it.trim().toInt().coerceIn(0, 255) }
                            if (p.size >= 3) Color.rgb(p[0], p[1], p[2]) else null
                        }
                        else -> null
                    }
                } catch (_: Exception) { null }
                if (applied != null) {
                    current = applied
                    customPreview.background = smallRing(ctx, applied)
                    updatePreview(applied)
                    sliders[0].progress = Color.red(applied)
                    sliders[1].progress = Color.green(applied)
                    sliders[2].progress = Color.blue(applied)
                    onPick(applied)
                    Toast.makeText(ctx, "Color #%06X aplicado 🎨".format(applied and 0xFFFFFF), Toast.LENGTH_SHORT).show()
                } else Toast.makeText(ctx, "Código no válido — ejemplos: 255,10,90  ·  #FF0A5A", Toast.LENGTH_SHORT).show()
            }
        })
        customRow.addView(codeRow)
        root.addView(customRow, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(ctx, 2) })
        return root
    }

    /* ---------- plumas: herramienta de escritura ---------- */
    fun penOptions(ctx: Context, canvas: CanvasPageView) {
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 22), dp(ctx, 14), dp(ctx, 22), dp(ctx, 8))
        }
        // —— RUEDA DE COLORES (fuera del papel: vive aquí, en la configuración) ——
        val wheel = com.knotes.app.ui.ColorWheelView(ctx)
        inner.addView(wheel, LinearLayout.LayoutParams(dp(ctx, 118), dp(ctx, 118)).also {
            it.gravity = android.view.Gravity.CENTER_HORIZONTAL
        })

        // —— Color elegido: pelotita + HEX que cambian AL INSTANTE ——
        inner.addView(label("Color elegido", ctx))
        val dot = android.view.View(ctx)
        val hex = TextView(ctx).apply {
            textSize = 12f; setTextColor(Color.parseColor("#6E5D66"))
        }
        fun applyPicked(c: Int) {
            canvas.penColor = c
            dot.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(c); setStroke(2, Color.WHITE)
            }
            hex.text = "#%06X".format(c and 0xFFFFFF)
        }
        val swrow = LinearLayout(ctx).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        swrow.addView(dot, LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 30)).also { it.setMargins(0, 0, dp(ctx, 10), 0) })
        swrow.addView(hex)
        inner.addView(swrow)
        applyPicked(canvas.penColor)          // estado actual
        wheel.onPick = { applyPicked(it) }    // en vivo al tocar la rueda ✨

        inner.addView(label("Tipo de trazo (cada uno se siente DIFERENTE)", ctx))
        val types = arrayOf(
            "Marcador 🖊 (firme y grueso)", "Lápiz ✏️ (gris con grano)", "Bolígrafo ✍️ (fino y preciso)",
            "Subrayar 〰️ (línea fina saturada)", "Puntitos 🌙 (a perlitas)", "Resaltado Neón ✨ (brillo de color)",
            "3D Biselado 💛 (con volumen)", "Resaltador 🟩 (grueso translúcido)", "3D Perla 🫧 (degradado pastel)"
        )
        val toolIds = intArrayOf(Tools.MARKER, Tools.PENCIL, Tools.PEN, Tools.HIGHLIGHTER,
            Tools.DOTTED, Tools.NEON, Tools.BEVEL, Tools.HILITER, Tools.PEARL)
        val rg = RadioGroup(ctx)
        for (i in types.indices) {
            val rb = RadioButton(ctx).apply { text = types[i]; setTextColor(Color.parseColor("#3A3A42")); textSize = 14.5f }
            rb.id = View.generateViewId()
            rg.addView(rb)
            if (canvas.tool == toolIds[i]) rb.isChecked = true
        }
        inner.addView(rg)
        inner.addView(label("Paleta rápida", ctx))
        inner.addView(colorGrid(ctx, canvas.penColor) { applyPicked(it) })
        inner.addView(label("Grosor del trazo", ctx))
        val slider = SeekBar(ctx).apply { max = 40; progress = canvas.penWidth.toInt().coerceIn(1, 40) }
        inner.addView(slider, LinearLayout.LayoutParams(-1, -2).also { it.setMargins(0, 6, 0, 12) })

        val root = android.widget.ScrollView(ctx).apply { addView(inner) }
        Dialogs.light(ctx)
            .setTitle("✍️ Herramienta de escritura")
            .setView(root)
            .setPositiveButton("Listo") { _, _ -> }
            .create()
            .show()

        rg.setOnCheckedChangeListener { _, id ->
            val idx = rg.indexOfChild(rg.findViewById(id))
            canvas.tool = toolIds[idx.coerceIn(0, toolIds.size - 1)]
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, value: Int, user: Boolean) {
                canvas.penWidth = value.coerceAtLeast(1).toFloat()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    /* ---------- goma ---------- */
    fun eraserOptions(ctx: Context, canvas: CanvasPageView) {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 22), dp(ctx, 14), dp(ctx, 22), 0)
        }
        val cbWhole = CheckBox(ctx).apply {
            text = "Borrar trazos completos (si no: borra solo la zona que tocas)"
            setTextColor(Color.parseColor("#3A3A42"))
            isChecked = canvas.eraserWhole
        }
        root.addView(cbWhole)
        root.addView(label("Tamaño de la goma", ctx))
        val slider = SeekBar(ctx).apply { max = 60; progress = canvas.eraserRadius.toInt().coerceIn(1, 60) }
        root.addView(slider)
        var r = canvas.eraserRadius
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, value: Int, user: Boolean) { r = value.coerceAtLeast(6).toFloat() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        Dialogs.light(ctx)
            .setTitle("🧽 Goma")
            .setView(root)
            .setPositiveButton("Listo") { _, _ ->
                canvas.eraserWhole = cbWhole.isChecked
                canvas.eraserRadius = r
            }
            .show()
    }

    /* ---------- texto ---------- */
    fun textDialog(ctx: Context, canvas: CanvasPageView, existing: TextElement?, pt: Pt) {
        val target = existing
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 22), dp(ctx, 14), dp(ctx, 22), 0)
        }
        val input = EditText(ctx).apply {
            hint = "Escribe tu texto..."
            setTextColor(Color.parseColor("#2B2B2B")); setHintTextColor(Color.parseColor("#9A9288"))
            minLines = 2; maxLines = 6
            setSingleLine(false)
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
            background = ctx.getDrawable(R.drawable.bg_cell_white)
            if (target != null) setText(target.text)
        }
        root.addView(input)
        root.addView(label("Color", ctx))
        var chosen = target?.color ?: canvas.penColor
        root.addView(colorGrid(ctx, chosen) { chosen = it })
        root.addView(label("Tamaño", ctx))
        var size = target?.size ?: 34f
        val slider = SeekBar(ctx).apply { max = 96; progress = size.toInt().coerceIn(12, 96) }
        root.addView(slider)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { size = p.coerceAtLeast(12).toFloat() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // —— TIPOGRAFÍA: el texto se dibuja en la fuente elegida (ej. Times New Roman) ——
        root.addView(label("Tipografía", ctx))
        val fonts = arrayOf(
            "Estándar" to "",
            "Times New Roman" to "serif",
            "Courier" to "monospace",
            "Manuscrita" to "cursive",
            "Condensada" to "sans-serif-condensed",
            "Oscura" to "sans-serif-black"
        )
        var fontChosen = target?.font ?: ""
        val fontRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val fontChips = arrayOfNulls<TextView>(fonts.size)
        fun refreshFontChips() {
            for (j in fonts.indices) {
                val on = fonts[j].second == fontChosen
                fontChips[j]!!.text = if (on) "✓ ${fonts[j].first}" else fonts[j].first
                fontChips[j]!!.background = ctx.getDrawable(if (on) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                fontChips[j]!!.setTextColor(if (on) Color.WHITE else Color.parseColor("#6E5D66"))
            }
        }
        for (i in fonts.indices) {
            val chip = TextView(ctx).apply {
                text = fonts[i].first; textSize = 12.5f
                typeface = if (fonts[i].second.isNotEmpty())
                    android.graphics.Typeface.create(fonts[i].second, android.graphics.Typeface.NORMAL)
                else null
                setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7))
                background = ctx.getDrawable(if (fonts[i].second == fontChosen) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                setTextColor(if (fonts[i].second == fontChosen) Color.WHITE else Color.parseColor("#6E5D66"))
                setOnClickListener { fontChosen = fonts[i].second; refreshFontChips() }
            }
            fontChips[i] = chip
            fontRow.addView(chip, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(0, 0, 6, 6) })
        }
        root.addView(HorizontalScrollView(ctx).apply { addView(fontRow); scrollBarDefaultDelayBeforeFade = 0 })

        Dialogs.light(ctx)
            .setTitle(if (target == null) "Añadir texto" else "Editar texto")
            .setView(scrollWrap(ctx, root))
            .setPositiveButton("Guardar") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    if (target != null) canvas.replaceTextElement(target, txt, size, chosen, fontChosen)
                    else canvas.addTextElement(txt, size, chosen, (pt.x - 40f).coerceAtLeast(60f), pt.y, fontChosen)
                } else if (target != null) canvas.deleteSelection()
            }
            .setNeutralButton(if (target == null) null else "Eliminar") { _, _ ->
                canvas.deleteElement(target!!)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /* ---------- enlace ---------- */
    fun linkDialog(ctx: Context, canvas: CanvasPageView, existing: LinkElement?, pt: Pt) {
        if (existing != null) {
            Dialogs.light(ctx)
                .setTitle("🔗 ${existing.label}")
                .setMessage(existing.url)
                .setPositiveButton("Abrir") { _, _ ->
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(existing.url))) }
                    catch (_: Exception) { Toast.makeText(ctx, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show() }
                }
                .setNeutralButton("Copiar URL") { _, _ ->
                    val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("url", existing.url))
                    Toast.makeText(ctx, "URL copiada", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cerrar", null)
                .show()
            return
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 22), dp(ctx, 14), dp(ctx, 22), 0)
        }
        val name = EditText(ctx).apply {
            hint = "Texto del enlace (ej. Mi web)"; setTextColor(Color.parseColor("#2B2B2B"))
            background = ctx.getDrawable(R.drawable.bg_cell_white)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }
        val url = EditText(ctx).apply {
            hint = "https://..."; setText("https://"); setTextColor(Color.parseColor("#2B2B2B"))
            background = ctx.getDrawable(R.drawable.bg_cell_white)
            setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        }
        root.addView(name); root.addView(url, LinearLayout.LayoutParams(-1, -2).also { it.topMargin = dp(ctx, 8) })
        Dialogs.light(ctx)
            .setTitle("🔗 Añadir enlace")
            .setView(root)
            .setPositiveButton("Añadir") { _, _ ->
                if (url.text.toString().startsWith("http")) {
                    canvas.addLinkElement(name.text.toString().ifBlank { "Enlace" }, url.text.toString(), pt.x - 60f, pt.y - 30f)
                    Toast.makeText(ctx, "Enlace añadido · tócalo para abrir", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(ctx, "La URL debe empezar por http(s)://", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /* ---------- stickers ---------- */
    fun stickerPicker(ctx: Context, canvas: CanvasPageView, onPickImage: (() -> Unit)? = null) {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), 0)
        }
        root.addView(TextView(ctx).apply {
            text = "Elige un sticker → luego toca el papel para colocarlo"
            setTextColor(Color.parseColor("#6E5D66")); setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), 0)
        })
        val tabsScroll = HorizontalScrollView(ctx).apply { scrollBarDefaultDelayBeforeFade = 0 }
        val tabsRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4)) }
        tabsScroll.addView(tabsRow)

        val scroll = android.widget.ScrollView(ctx).apply {
            scrollBarDefaultDelayBeforeFade = 0
            isVerticalScrollBarEnabled = true
        }

        val names = ArrayList<String>()
        val packs = ArrayList<List<String>>()
        names.add("Míos ✚"); packs.add(emptyList())           // se rellena al abrir
        Catalog.stickerPacks.forEach { (n, l) -> names.add(n); packs.add(l) }

        val dialog = Dialogs.light(ctx).setView(root).setPositiveButton("Listo", null).create()

        fun fill(idx: Int) {
            tabsRow.removeAllViews()
            names.forEachIndexed { i, name ->
                val chip = TextView(ctx).apply {
                    text = name; textSize = 13f
                    setTextColor(if (i == idx) Color.WHITE else Color.parseColor("#6E5D66"))
                    setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
                    background = ctx.getDrawable(if (i == idx) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                    setSingleLine(true)
                    setOnClickListener { fill(i) }
                }
                tabsRow.addView(chip, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(4, 0, 4, 0) })
            }
            scroll.removeAllViews()
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 16)) }

            if (idx == 0) {
                // —— MÍOS: emojis guardados + agregar libre + cargar imagen como sticker —— //
                val input = EditText(ctx).apply {
                    hint = "Pega o escribe tu emoji (ej. 🐶) y toca AGREGAR"
                    setTextColor(Color.parseColor("#2B2B2B")); setHintTextColor(Color.parseColor("#9A9288"))
                    background = ctx.getDrawable(R.drawable.bg_cell_white)
                    setSingleLine(true)
                    setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
                }
                box.addView(input, LinearLayout.LayoutParams(-1, -2).also { it.setMargins(0, 0, 0, dp(ctx, 8)) })
                val rowBtns = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                fun chipBtn(t: String, bgInt: Int, act: () -> Unit): TextView = TextView(ctx).apply {
                    text = t; textSize = 13f; gravity = Gravity.CENTER
                    setPadding(dp(ctx, 14), dp(ctx, 9), dp(ctx, 14), dp(ctx, 9))
                    setTextColor(Color.WHITE)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 20f; setColor(bgInt)
                    }
                    setOnClickListener { act() }
                }
                rowBtns.addView(chipBtn("✚ AGREGAR", 0xFF3956C9.toInt()) {
                    val e = input.text.toString().trim()
                    if (e.isNotEmpty()) {
                        com.knotes.app.data.Prefs.addCustomSticker(e)
                        Toast.makeText(ctx, "Sticker guardado en «Míos» ✅", Toast.LENGTH_SHORT).show()
                        input.setText("")
                        fill(0)
                    } else Toast.makeText(ctx, "Escribe o pega un emoji primero", Toast.LENGTH_SHORT).show()
                }, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(0, 0, dp(ctx, 8), 0) })
                rowBtns.addView(chipBtn("🖼 CARGAR IMAGEN (PNG/JPG)",
                    if (onPickImage != null) Gender.accentDeep(ctx) else 0xFFB0B0B8.toInt()) {
                    if (onPickImage == null) Toast.makeText(ctx, "Solo dentro del editor", Toast.LENGTH_SHORT).show()
                    else {
                        dialog.dismiss(); onPickImage.invoke()
                    }
                }, LinearLayout.LayoutParams(-2, -2))
                box.addView(rowBtns)

                box.addView(TextView(ctx).apply {
                    text = "Mis stickers guardados"
                    textSize = 12.5f; setTextColor(Color.parseColor("#6E5D66"))
                    setPadding(dp(ctx, 4), dp(ctx, 12), dp(ctx, 4), dp(ctx, 6))
                })
                val mine = com.knotes.app.data.Prefs.customStickers().toList()
                if (mine.isEmpty()) box.addView(TextView(ctx).apply {
                    text = "Aún no tienes: agrega emojis libres o carga una imagen ☝️"
                    textSize = 13f; setTextColor(Color.parseColor("#9A9288")); setPadding(dp(ctx, 4), dp(ctx, 2), dp(ctx, 4), dp(ctx, 10))
                })
                // filas de 4 celdas — SIEMPRE con el emoji a la vista
                val list = mine
                var r = 0
                while (r * 4 < list.size) {
                    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                    for (c in 0 until 4) {
                        val i = r * 4 + c
                        val cell = TextView(ctx).apply {
                            gravity = Gravity.CENTER
                            background = ctx.getDrawable(R.drawable.bg_cell_white)
                            textSize = 30f
                            setTextColor(Color.parseColor("#2B2B2B"))
                            text = if (i < list.size) list[i] else ""
                            setOnClickListener {
                                if (i < list.size) {
                                    canvas.stickerPending = list[i]
                                    dialog.dismiss()
                                    Toast.makeText(ctx, "Toca el papel cuantas veces quieras ✓ · mantén presionado el botón 🖊 para cambiarlo", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        row.addView(cell, LinearLayout.LayoutParams(0, dp(ctx, 64), 1f).also { it.setMargins(5, 5, 5, 5) })
                    }
                    box.addView(row)
                    r++
                }
            } else {
                val items = packs[idx]
                var r = 0
                while (r * 4 < items.size) {
                    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                    for (c in 0 until 4) {
                        val i = r * 4 + c
                        val cell = TextView(ctx).apply {
                            gravity = Gravity.CENTER
                            background = ctx.getDrawable(R.drawable.bg_cell_white)
                            textSize = 30f
                            setTextColor(Color.parseColor("#2B2B2B"))
                            text = if (i < items.size) items[i] else ""
                            setOnClickListener {
                                if (i < items.size) {
                                    canvas.stickerPending = items[i]
                                    dialog.dismiss()
                                    Toast.makeText(ctx, "Toca el papel cuantas veces quieras ✓ · mantén presionado el botón 🖊 para cambiarlo", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        row.addView(cell, LinearLayout.LayoutParams(0, dp(ctx, 64), 1f).also { it.setMargins(5, 5, 5, 5) })
                    }
                    box.addView(row)
                    r++
                }
            }
            scroll.addView(box)
        }

        root.addView(tabsScroll)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(ctx, 330)))
        fill(0)
        dialog.show()
    }

    /* ---------- páginas ---------- */
    val TPL_NAMES = arrayOf(
        "Rayado", "Cuadrícula", "Puntos", "En blanco", "Rayado fino",
        "Cuadrícula fina", "Puntos finos", "Con margen", "Dos columnas", "Agenda"
    )
    val PAPER_NAMES = arrayOf(
        "Crema", "Blanco", "Oscuro", "Rosa", "Menta", "Lavanda", "Celeste", "🎨 Portada del diario"
    )

    /** Selector completo de página:
     *  0) PLANTILLAS REALES con contenido · 1) TIPO · 2) ADORNO · 3) PAPEL · 4) FONDOS.
     *  Todo envuelto en scroll para que TODAS las secciones sean visibles. */
    fun pageStylePicker(ctx: Context, onPick: (tpl: Int, decor: Int, paper: Int, real: Int) -> Unit,
                        title: String = "Estilo de página",
                        curTpl: Int = 0, curDecor: Int = 0, curPaper: Int = 0) {
        var selTpl = curTpl
        var selDecor = curDecor
        var selPaper = curPaper
        var selReal = 0
        var tplTouched = false    // ¿el usuario tocó él mismo el tipo/papel en este diálogo?
        var paperTouched = false
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 22), dp(ctx, 6), dp(ctx, 22), dp(ctx, 8))
        }
        val root = android.widget.ScrollView(ctx).apply { addView(inner) }

        // —— 0) PLANTILLAS REALES: páginas que VIENEN con su contenido listo ——
        inner.addView(label("⭐ Plantillas con contenido (reales)", ctx))
        val realRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val realChips = arrayOfNulls<TextView>(CanvasPageView.realTplCount() + 1)
        fun refreshRealChips() {
            val all = (listOf("Sola (papel vacío)") + CanvasPageView.REAL_TPL_NAMES.toList())
            for (j in all.indices) {
                val chip = realChips[j]!!
                chip.text = if (j == selReal) "✓ ${all[j]}" else all[j]
                chip.background = ctx.getDrawable(if (j == selReal) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                chip.setTextColor(if (j == selReal) Color.WHITE else Color.parseColor("#6E5D66"))
            }
        }
        val allReals = (listOf("Sola (papel vacío)") + CanvasPageView.REAL_TPL_NAMES.toList())
        for (i in allReals.indices) {
            val chip = TextView(ctx).apply {
                text = allReals[i]; textSize = 13f
                setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
                background = ctx.getDrawable(R.drawable.bg_chip_light)
                setTextColor(Color.parseColor("#6E5D66"))
                setOnClickListener {
                    selReal = i
                    refreshRealChips()
                }
            }
            realChips[i] = chip
            realRow.addView(chip, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(0, 0, 8, 0) })
        }
        inner.addView(HorizontalScrollView(ctx).apply { addView(realRow); scrollBarDefaultDelayBeforeFade = 0 })

        // —— 1) TIPO DE PÁGINA (10 estilos) ——
        inner.addView(label("Tipo de página", ctx))
        val tplRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val tplChips = arrayOfNulls<TextView>(TPL_NAMES.size)
        fun refreshTplChips() {
            for (j in TPL_NAMES.indices) {
                tplChips[j]!!.text = if (j == selTpl) "✓ ${TPL_NAMES[j]}" else TPL_NAMES[j]
                tplChips[j]!!.background = ctx.getDrawable(if (j == selTpl) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                tplChips[j]!!.setTextColor(if (j == selTpl) Color.WHITE else Color.parseColor("#6E5D66"))
            }
        }
        for (i in TPL_NAMES.indices) {
            val chip = TextView(ctx).apply {
                text = if (i == selTpl) "✓ ${TPL_NAMES[i]}" else TPL_NAMES[i]; textSize = 13f
                setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
                background = ctx.getDrawable(if (i == selTpl) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                setTextColor(if (i == selTpl) Color.WHITE else Color.parseColor("#6E5D66"))
                setOnClickListener { selTpl = i; tplTouched = true; refreshTplChips() }
            }
            tplChips[i] = chip
            tplRow.addView(chip, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(0, 0, 8, 0) })
        }
        inner.addView(HorizontalScrollView(ctx).apply { addView(tplRow); scrollBarDefaultDelayBeforeFade = 0 })

        // —— 2) ADORNO DE ESQUINAS ——
        inner.addView(label("Adorno de esquinas 🌸🍓💕", ctx))
        val decorRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val decorChips = arrayOfNulls<TextView>(14)
        fun refreshDecorChips() {
            for (j in 0..13) {
                val base = if (j == 0) "Ninguna" else "${Decors.shortEmoji(j)} ${Decors.name(j).substringAfter(' ')}"
                decorChips[j]!!.text = if (j == selDecor) "✓ $base" else base
                decorChips[j]!!.background = ctx.getDrawable(if (j == selDecor) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                decorChips[j]!!.setTextColor(if (j == selDecor) Color.WHITE else Color.parseColor("#6E5D66"))
            }
        }
        for (d in 0..13) {
            val chip = TextView(ctx).apply {
                text = if (d == 0) "Ninguna" else "${Decors.shortEmoji(d)} ${Decors.name(d).substringAfter(' ')}"
                textSize = 12.5f
                setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7))
                background = ctx.getDrawable(if (d == selDecor) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                setTextColor(if (d == selDecor) Color.WHITE else Color.parseColor("#6E5D66"))
                setOnClickListener { selDecor = d; refreshDecorChips() }
            }
            decorChips[d] = chip
            decorRow.addView(chip, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(0, 0, 6, 6) })
        }
        inner.addView(HorizontalScrollView(ctx).apply { addView(decorRow); scrollBarDefaultDelayBeforeFade = 0 })

        // —— 3) COLOR / FONDO DEL PAPEL (0..7; 7 = la PORTADA del diario) ——
        inner.addView(label("Fondo de papel", ctx))
        val papRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val papChips = arrayOfNulls<TextView>(PAPER_NAMES.size)
        fun refreshPaperChips() {
            for (j in PAPER_NAMES.indices) {
                papChips[j]!!.text = if (j == selPaper) "✓ ${PAPER_NAMES[j]}" else PAPER_NAMES[j]
                papChips[j]!!.background = ctx.getDrawable(if (j == selPaper) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                papChips[j]!!.setTextColor(if (j == selPaper) Color.WHITE else Color.parseColor("#6E5D66"))
            }
        }
        for (i in PAPER_NAMES.indices) {
            val chip = TextView(ctx).apply {
                text = if (i == selPaper) "✓ ${PAPER_NAMES[i]}" else PAPER_NAMES[i]; textSize = 13f
                setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
                background = ctx.getDrawable(if (i == selPaper) Gender.chipStrongBg() else R.drawable.bg_chip_light)
                setTextColor(if (i == selPaper) Color.WHITE else Color.parseColor("#6E5D66"))
                setOnClickListener { selPaper = i; paperTouched = true; refreshPaperChips() }
            }
            papChips[i] = chip
            papRow.addView(chip, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(0, 0, 8, 0) })
        }
        inner.addView(HorizontalScrollView(ctx).apply { addView(papRow); scrollBarDefaultDelayBeforeFade = 0 })

        // —— 4) FONDOS: combina tipo+papel+adorno(+contenido) de las PLANTILLAS de la biblioteca ——
        inner.addView(label("Fondos ✨ (plantillas de la biblioteca)", ctx))
        val fonds = TemplatesLibrary.visibleTemplates()
        val fondRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in fonds) {
            val p = t.pages.firstOrNull() ?: continue
            val chip = TextView(ctx).apply {
                text = "${t.emoji} ${t.name}"; textSize = 12.5f
                setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7))
                background = ctx.getDrawable(R.drawable.bg_chip_light)
                setTextColor(Color.parseColor("#6E5D66"))
                setOnClickListener {
                    selTpl = p.first.coerceIn(0, TPL_NAMES.size - 1)
                    selPaper = p.second.coerceIn(0, PAPER_NAMES.size - 1)
                    selDecor = p.third.coerceIn(0, 13)
                    tplTouched = true; paperTouched = true
                    if (t.real > 0) selReal = t.real   // las temáticas traen CONTENIDO real
                    refreshTplChips(); refreshPaperChips(); refreshDecorChips(); refreshRealChips()
                }
            }
            fondRow.addView(chip, LinearLayout.LayoutParams(-2, -2).also { it.setMargins(0, 0, 6, 6) })
        }
        inner.addView(HorizontalScrollView(ctx).apply { addView(fondRow); scrollBarDefaultDelayBeforeFade = 0 })

        Dialogs.light(ctx)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("Listo") { _, _ ->
                // al aplicar una plantilla REAL: hoja limpia + papel claro
                // (salvo que el usuario mismo eligiera oscuro/portada en este diálogo)
                if (selReal > 0) {
                    if (!tplTouched) selTpl = 3
                    if (!paperTouched && selPaper !in 2..7) selPaper = 1
                }
                onPick(selTpl, selDecor, selPaper, selReal)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** ELIGE la forma a dibujar (arrastras sobre el papel y sale limpia). */
    fun shapePicker(ctx: Context, canvas: CanvasPageView) {
        Dialogs.light(ctx)
            .setTitle("⬟ Añadir formas")
            .setItems(Shape.NAMES) { _, w ->
                canvas.shapeKind = w
                canvas.tool = Tools.SHAPE
                Toast.makeText(ctx, "Dibuja arrastrando sobre el papel — ${Shape.NAMES[w].substringBefore(' ')} ✓ despúes gírala con la manija ⟳", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** LISTA DE HERRAMIENTAS (botón punta del lápiz ▭ en la barra inferior):
     *  el resto de lo que no son lápices: cursor, lazo, goma, texto, formas,
     *  regla, imagen, enlace, stickers. */
    fun toolsPopup(ctx: Context, canvas: CanvasPageView, onPickImage: (() -> Unit)?, onPickStickers: () -> Unit) {
        val names = arrayOf(
            "👆 Cursor (mover objetos)", "➰ Lazo / seleccionar zona", "🧽 Goma",
            "🅰 Texto", "⬟ Formas", "📏 Regla", "🖼 Imagen", "🔗 Enlace", "😊 Stickers"
        )
        Dialogs.light(ctx)
            .setTitle("🧰 Herramientas")
            .setItems(names) { _, w ->
                when (w) {
                    0 -> canvas.tool = Tools.SELECT
                    1 -> canvas.tool = Tools.LASSO
                    2 -> { canvas.tool = Tools.ERASER; eraserOptions(ctx, canvas) }
                    3 -> canvas.tool = Tools.TEXT
                    4 -> shapePicker(ctx, canvas)
                    5 -> { canvas.tool = Tools.RULER; canvas.rulerOn = !canvas.rulerOn }
                    6 -> if (onPickImage != null) onPickImage()
                    7 -> canvas.tool = Tools.LINK
                    else -> { canvas.tool = Tools.STICKER; onPickStickers() }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    fun pagesOverview(ctx: Context, canvas: CanvasPageView) {
        val n = canvas.pages.size
        val names = arrayOfNulls<String>(n)
        for (i in 0 until n) {
            val d = canvas.pages[i].decor
            names[i] = "Página ${i + 1} — ${TPL_NAMES[canvas.pages[i].template.coerceIn(0, TPL_NAMES.size - 1)]}" +
                (if (d in 1..13) " ${Decors.shortEmoji(d)}" else "")
        }
        Dialogs.light(ctx)
            .setTitle("Páginas ($n)")
            .setItems(names) { _, w -> canvas.goToPage(w) }
            .setPositiveButton("Nueva página") { _, _ ->
                pageStylePicker(ctx, { t, dec, pap, real ->
                    canvas.addPage(t, dec, pap)
                    if (real > 0) canvas.applyRealTemplate(real)
                }, "Nueva página",
                    canvas.curPage.template, 0, canvas.curPage.paper)
            }
            .setNeutralButton("Cambiar estilo") { _, _ ->
                pageStylePicker(ctx, { t, dec, pap, real ->
                    canvas.setTemplate(t); canvas.setPageDecor(dec); canvas.setPaperColor(pap)
                    if (real > 0) canvas.applyRealTemplate(real)
                }, "Cambiar estilo", canvas.curPage.template, canvas.curPage.decor, canvas.curPage.paper)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}
