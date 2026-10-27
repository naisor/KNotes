package com.knotes.app

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.knotes.app.canvas.CanvasPageView
import com.knotes.app.canvas.Decors
import com.knotes.app.data.Ids
import com.knotes.app.data.KPage
import com.knotes.app.data.Prefs
import com.knotes.app.data.Store
import com.knotes.app.ui.Gender
import com.knotes.app.ui.NotebookCoverView
import com.knotes.app.ui.Panels
import com.knotes.app.ui.TileArtView
import kotlin.random.Random

/** Vista previa de una página: color de papel + líneas del tipo elegido. */
class PagePreviewView @JvmOverloads constructor(
    context: android.content.Context, attrs: android.util.AttributeSet? = null
) : View(context, attrs) {
    var tpl = 3
    var paperColor = Color.WHITE
    var decor = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL; p.color = paperColor
        c.drawRoundRect(RectF(0f, 0f, w, h), 4f, 4f, p)
        val dark = Color.red(paperColor) < 90
        val ink = if (dark) 0x44FFFFFF else 0xFFD0D0D6.toInt()
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f; p.color = ink
        val fine = tpl == 4 || tpl == 5 || tpl == 6
        val gap = if (fine) h / 22f else h / 13f
        when (tpl) {
            0, 4, 7, 9 -> { var y = gap * 1.5f; while (y < h) { c.drawLine(w * 0.08f, y, w * 0.92f, y, p); y += gap }
                if (tpl == 7) { p.color = 0x88E2A0A0.toInt(); c.drawLine(w * 0.2f, 0f, w * 0.2f, h, p) } }
            1, 5 -> { var y = gap; while (y < h) { c.drawLine(0f, y, w, y, p); y += gap }
                var x = gap; while (x < w) { c.drawLine(x, 0f, x, h, p); x += gap } }
            2, 6 -> { p.style = Paint.Style.FILL; var y = gap; while (y < h) { var x = gap; while (x < w) { c.drawCircle(x, y, 1.2f, p); x += gap }; y += gap } }
            8 -> { c.drawLine(w / 2f, 0f, w / 2f, h, p); var y = gap * 1.5f; while (y < h) { c.drawLine(w * 0.06f, y, w * 0.46f, y, p); c.drawLine(w * 0.54f, y, w * 0.94f, y, p); y += gap } }
            else -> {}
        }
        if (decor > 0) { p.style = Paint.Style.FILL; p.textSize = h * 0.16f; p.textAlign = Paint.Align.LEFT; p.color = Color.BLACK
            c.drawText(Decors.shortEmoji(decor), w * 0.04f, h * 0.17f, p) }
    }
}

/**
 * ASISTENTE «NUEVO DIARIO» (como el video): pestañas Portada | Página.
 *  · Portada: nombre, imagen propia, colores, portadas por categorías → «siguiente»
 *  · Página: Personalizar · Papel · Plantillas → «Crear Nueva Página»
 */
class CreateDiaryActivity : AppCompatActivity() {

    private var mainTab = 0
    private var subTab = 0
    private var name = ""
    private var style = Random.nextInt(NotebookCoverView.coverStyles())
    private val seed = Random.nextLong()
    private var solid = 0
    private var imgUri: Uri? = null
    private var tpl = Prefs.defaultTemplate.coerceIn(0, 9)
    private var paper = 1
    private var decor = 0
    private var real = 0

    private lateinit var tabBar: LinearLayout
    private lateinit var topRight: TextView
    private lateinit var body: ScrollView
    private lateinit var bottomBtn: TextView
    private var nameField: EditText? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun accent() = Gender.accent(this)

    private val pickImg = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { imgUri = uri; solid = 0; Toast.makeText(this, "Imagen cargada ✓", Toast.LENGTH_SHORT).show(); render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Gender.applyTheme(this)
        val root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.bg_app)); fitsSystemWindows = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // —— barra superior: ← · [Portada|Página] · siguiente/restablecer ——
        val top = FrameLayout(this)
        top.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back); background = null
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary))
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL).also { it.marginStart = dp(8) })
        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(3), dp(3), dp(3), dp(3)); background = getDrawable(R.drawable.bg_chip_normal) }
        top.addView(tabBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        topRight = TextView(this).apply {
            textSize = 13f; setTextColor(0xFF1A1A1A.toInt()); gravity = Gravity.CENTER
            setPadding(dp(14), dp(7), dp(14), dp(7)); background = getDrawable(Gender.chipSelBg())
        }
        top.addView(topRight, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).also { it.marginEnd = dp(12) })
        col.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        body = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        col.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(col)

        bottomBtn = TextView(this).apply {
            textSize = 15f; setTextColor(0xFF1A1A1A.toInt()); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            background = getDrawable(Gender.chipSelBg()); elevation = dp(6).toFloat()
        }
        root.addView(bottomBtn, FrameLayout.LayoutParams(dp(300), dp(46), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { it.bottomMargin = dp(16) })
        setContentView(root)
        render()
    }

    /* ---------------- contenedor ---------------- */

    private fun render() {
        nameField?.let { name = it.text.toString() }
        tabBar.removeAllViews()
        for ((i, l) in listOf("Portada", "Página").withIndex()) {
            val on = i == mainTab
            tabBar.addView(TextView(this).apply {
                text = l; textSize = 13.5f; gravity = Gravity.CENTER
                setTextColor(if (on) 0xFF1A1A1A.toInt() else getColor(R.color.text_primary))
                setPadding(dp(18), dp(7), dp(18), dp(7))
                if (on) background = getDrawable(Gender.chipSelBg())
                setOnClickListener { mainTab = i; render() }
            })
        }
        topRight.text = if (mainTab == 0) "siguiente" else "restablecer"
        topRight.setOnClickListener {
            if (mainTab == 0) { mainTab = 1; render() }
            else { tpl = Prefs.defaultTemplate.coerceIn(0, 9); paper = 1; decor = 0; real = 0; render() }
        }
        bottomBtn.text = if (mainTab == 0) "siguiente" else "Crear Nueva Página"
        bottomBtn.setOnClickListener { if (mainTab == 0) { mainTab = 1; render() } else create() }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(90)) }
        if (mainTab == 0) buildCover(content) else buildPage(content)
        body.removeAllViews(); body.addView(content)
        nameField = if (mainTab == 0) content.findViewWithTag<EditText>("nameField") else null
    }

    private fun label(t: String, bold: Boolean = true) = TextView(this).apply {
        text = t; textSize = 14f; setTextColor(getColor(R.color.text_primary))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(2), dp(16), 0, dp(8))
    }

    private fun hrow(views: List<View>, gapDp: Int = 8): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for (v in views) row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(gapDp) })
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun ring(on: Boolean, radiusDp: Int = 10): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(if (on) dp(2) else dp(1), if (on) getColor(R.color.text_primary) else 0x00000000)
        }

    /* ---------------- PORTADA ---------------- */

    private fun buildCover(c: LinearLayout) {
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val prev = NotebookCoverView(this).apply { style = this@CreateDiaryActivity.style; seed = this@CreateDiaryActivity.seed; solid = this@CreateDiaryActivity.solid }
        imgUri?.let { u -> try { // vista previa con la foto elegida
            val bmp = android.graphics.BitmapFactory.decodeStream(contentResolver.openInputStream(u))
            val v = android.widget.ImageView(this).apply { setImageBitmap(bmp); scaleType = android.widget.ImageView.ScaleType.CENTER_CROP }
            head.addView(v, LinearLayout.LayoutParams(dp(96), dp(132)))
        } catch (_: Exception) { head.addView(prev, LinearLayout.LayoutParams(dp(96), dp(132))) } } ?: head.addView(prev, LinearLayout.LayoutParams(dp(96), dp(132)))

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        right.addView(TextView(this).apply { text = "Nombre del diario"; textSize = 15f; setTextColor(getColor(R.color.text_primary)); typeface = Typeface.DEFAULT_BOLD })
        val field = EditText(this).apply {
            tag = "nameField"; setText(name.ifBlank { "Diario 1" }); setSelection(text.length)
            textSize = 14f; setTextColor(getColor(R.color.text_primary)); inputType = InputType.TYPE_CLASS_TEXT
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setStroke(dp(1), getColor(R.color.text_primary)) }
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        right.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10) })
        right.addView(TextView(this).apply {
            text = "🖼  Subir imagen"; textSize = 13f; setTextColor(getColor(R.color.text_primary))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), accent()) }
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { name = field.text.toString(); pickImg.launch("image/*") }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) })
        head.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(16) })
        c.addView(head)

        // —— colores sólidos + paleta ——
        val colors = intArrayOf(0xFFF6D86A.toInt(), 0xFF8EC9F2.toInt(), 0xFFF4E27A.toInt(), 0xFFB9A0E8.toInt(), 0xFFF2C14E.toInt(),
            0xFF6E6E76.toInt(), 0xFFF0A5C0.toInt(), 0xFF8FD3A0.toInt(), 0xFF7FD6E0.toInt())
        val dots = ArrayList<View>()
        for (col in colors) dots.add(View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(col)
                if (solid == col && imgUri == null) setStroke(dp(2), getColor(R.color.text_primary))
            }
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            setOnClickListener { name = field.text.toString(); solid = col; imgUri = null; render() }
        })
        dots.add(ImageButton(this).apply {
            setImageResource(R.drawable.ic_palette_color); background = null; imageTintList = null
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener { Panels.colorDialog(this@CreateDiaryActivity, if (solid != 0) solid else 0xFFF6D86A.toInt(), "🎨 Color de la portada") { picked ->
                name = field.text.toString(); solid = picked; imgUri = null; render() } }
        })
        c.addView(hrow(dots, 12), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(16) })

        // —— portadas por categorías ——
        val total = NotebookCoverView.coverStyles()
        val cats = listOf("Art Covers", "Colorful Covers", "Vintage Covers", "Floral Covers")
        val per = (total + cats.size - 1) / cats.size
        for ((ci, cat) in cats.withIndex()) {
            val from = ci * per; val to = minOf(total, from + per)
            if (from >= to) continue
            c.addView(label(cat))
            val thumbs = ArrayList<View>()
            for (i in from until to) {
                val cell = FrameLayout(this).apply { setPadding(dp(3), dp(3), dp(3), dp(3)); background = ring(i == style && solid == 0 && imgUri == null) }
                cell.addView(NotebookCoverView(this).apply { this.style = i; this.seed = this@CreateDiaryActivity.seed + i }, FrameLayout.LayoutParams(dp(76), dp(104)))
                cell.setOnClickListener { name = field.text.toString(); style = i; solid = 0; imgUri = null; render() }
                thumbs.add(cell)
            }
            c.addView(hrow(thumbs, 6))
        }
    }

    /* ---------------- PÁGINA ---------------- */

    private fun buildPage(c: LinearLayout) {
        val subs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for ((i, l) in listOf("Personalizar", "Papel", "Plantillas").withIndex()) {
            val on = i == subTab
            val colv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setOnClickListener { subTab = i; render() } }
            colv.addView(TextView(this).apply {
                text = l; textSize = 13.5f; setPadding(dp(14), dp(8), dp(14), dp(5))
                setTextColor(if (on) getColor(R.color.text_primary) else getColor(R.color.text_dim)); if (on) typeface = Typeface.DEFAULT_BOLD
            })
            colv.addView(View(this).apply { setBackgroundColor(if (on) accent() else Color.TRANSPARENT) }, LinearLayout.LayoutParams(dp(30), dp(2)))
            subs.addView(colv)
        }
        c.addView(subs)
        if (subTab == 2) { buildTemplates(c); return }

        // vista previa de la página
        val pv = PagePreviewView(this).apply { tpl = this@CreateDiaryActivity.tpl; paperColor = paperColorNow(); decor = this@CreateDiaryActivity.decor }
        c.addView(pv, LinearLayout.LayoutParams(dp(130), dp(184)).also { it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(14) })

        c.addView(label("Estilo de papel"))
        val styles = ArrayList<View>()
        for (i in Panels.TPL_NAMES.indices) {
            val cell = FrameLayout(this).apply { setPadding(dp(3), dp(3), dp(3), dp(3)); background = ring(i == tpl && real == 0, 8) }
            cell.addView(PagePreviewView(this).apply { this.tpl = i; paperColor = paperColorNow() }, FrameLayout.LayoutParams(dp(58), dp(80)))
            cell.setOnClickListener { tpl = i; real = 0; render() }
            styles.add(cell)
        }
        c.addView(hrow(styles, 6))

        c.addView(label("color"))
        val pcols = ArrayList<View>()
        val names = Panels.PAPER_NAMES
        for (i in 0 until 7) pcols.add(View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(CanvasPageView.PAPER_COLORS[i])
                setStroke(if (i == paper) dp(3) else dp(1), if (i == paper) accent() else 0xFF555555.toInt())
            }
            contentDescription = names[i]
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
            setOnClickListener { paper = i; render() }
        })
        c.addView(hrow(pcols, 12))

        if (subTab == 0) {
            c.addView(label("fondo"))
            val fonds = ArrayList<View>()
            for (d in 0..13) {
                val cell = FrameLayout(this).apply { setPadding(dp(3), dp(3), dp(3), dp(3)); background = ring(d == decor, 8) }
                cell.addView(TextView(this).apply {
                    text = if (d == 0) "🚫" else Decors.shortEmoji(d); textSize = 22f; gravity = Gravity.CENTER
                    setBackgroundColor(0xFF2A2A2A.toInt())
                }, FrameLayout.LayoutParams(dp(64), dp(48)))
                cell.setOnClickListener { decor = d; render() }
                fonds.add(cell)
            }
            c.addView(hrow(fonds, 6))
        }
    }

    private fun paperColorNow(): Int = CanvasPageView.PAPER_COLORS[paper.coerceIn(0, 7)]

    private fun buildTemplates(c: LinearLayout) {
        c.addView(label("Plantillas con contenido"))
        val names = CanvasPageView.REAL_TPL_NAMES
        val variants = intArrayOf(7, 2, 4, 5, 6, 5, 3, 1, 4, 0)
        val avail = resources.displayMetrics.widthPixels - dp(28)
        val w = (avail - dp(12) * 2) / 3
        var i = 0
        while (i < names.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0 until 3) {
                val idx = i + k
                if (idx >= names.size) break
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(3), dp(3), dp(3), dp(3))
                    background = ring(real == idx + 1, 10) }
                cell.addView(TileArtView(this).apply { mode = TileArtView.PAGE; variant = variants[idx]; title = names[idx] }, LinearLayout.LayoutParams(w - dp(6), ((w - dp(6)) * 1.25f).toInt()))
                cell.addView(TextView(this).apply { text = names[idx]; textSize = 11.5f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.text_primary)); maxLines = 2; setPadding(0, dp(5), 0, dp(2)) },
                    LinearLayout.LayoutParams(w - dp(6), ViewGroup.LayoutParams.WRAP_CONTENT))
                cell.setOnClickListener { real = if (real == idx + 1) 0 else idx + 1; render() }
                row.addView(cell, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(12) })
            }
            c.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(12) })
            i += 3
        }
    }

    /* ---------------- crear ---------------- */

    private fun create() {
        nameField?.let { name = it.text.toString() }
        val title = name.ifBlank { "Nuevo Diario" }
        val pages: MutableList<KPage> = ArrayList()
        pages.add(KPage(Ids.next(), tpl, paper, decor))
        if (real > 0) {
            pages[0].template = 3
            pages[0].items.addAll(CanvasPageView.buildRealTemplate(real - 1))
        }
        val nb = Store.createNotebook(title, style, ArrayList(), seed, pages)
        val u = imgUri
        if (u != null) Store.saveCoverImage(this, nb, u)
        else { nb.coverColor = solid; Store.saveMeta(nb) }
        Toast.makeText(this, "¡Diario creado! ✍️", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, EditorActivity::class.java).putExtra("id", nb.id))
        finish()
    }
}
