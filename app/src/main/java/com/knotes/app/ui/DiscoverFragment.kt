package com.knotes.app.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
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
import androidx.fragment.app.Fragment
import com.knotes.app.EditorActivity
import com.knotes.app.R
import com.knotes.app.canvas.CanvasPageView
import com.knotes.app.data.Ids
import com.knotes.app.data.KPage
import com.knotes.app.data.Prefs
import com.knotes.app.data.Store

/** «Descubrir»: pestañas inicio · Templates · Stickers · Book Journal · Sample Journals · Sticky. */
class DiscoverFragment : Fragment() {

    private class Entry(val title: String, val tab: String, val action: () -> Unit)

    private class StickerPack(val name: String, val bg: Int, val accent: Int, val emojis: List<String>)

    private val tabs = listOf("inicio", "Templates", "Stickers", "Book Journal", "Sample Journals", "Sticky")
    private var current = 0
    private lateinit var tabRow: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private val registry = ArrayList<Entry>()

    private val packs = listOf(
        StickerPack("Flower", 0xFFF7D6DE.toInt(), 0xFFD9906F.toInt(), listOf("🌸", "🌷", "🌹", "🌻", "🌺", "🌼", "💐", "🪷", "🍀", "🌿")),
        StickerPack("Plant", 0xFFDCE6F6.toInt(), 0xFFE6E8C9.toInt(), listOf("🪴", "🌵", "🌿", "🍃", "🌱", "🌳", "🌴", "🍀", "🌾", "🪹")),
        StickerPack("Cute", 0xFFF9E0BF.toInt(), 0xFFF3B77A.toInt(), listOf("🐱", "🧸", "☕", "🧋", "🍰", "🐻", "🐰", "🧁", "🍩", "🐼")),
        StickerPack("Inspiration", 0xFFE0D6F2.toInt(), 0xFFC5B5E6.toInt(), listOf("✨", "💖", "🌈", "💪", "🙏", "🌟", "🕊️", "🔥", "💡", "🎯")),
        StickerPack("Weather & Season", 0xFFF2E6D6.toInt(), 0xFFD9A98B.toInt(), listOf("☀️", "🌧️", "❄️", "🍂", "🌈", "⛅", "🌙", "⛄", "🍁", "🌸")),
        StickerPack("Work", 0xFFF6CFE6.toInt(), 0xFFD9569E.toInt(), listOf("💻", "📎", "📅", "📌", "✏️", "📊", "🗂️", "📞", "✉️", "🧮")),
        StickerPack("Entertainment", 0xFFFBF1CB.toInt(), 0xFF7FB3B0.toInt(), listOf("🎬", "🎮", "🎧", "📺", "🍿", "🎤", "🎸", "🎲", "📚", "🎨")),
        StickerPack("Study", 0xFFD8F0F4.toInt(), 0xFF8FB8A0.toInt(), listOf("📚", "🎒", "✏️", "📐", "🔬", "🧪", "🌍", "💡", "🧮", "📝")),
        StickerPack("Agenda", 0xFFF6D6E0.toInt(), 0xFF9C8AD0.toInt(), listOf("📅", "🗓️", "⏰", "📆", "📍", "✅", "🔔", "📋", "⌛", "📝")),
        StickerPack("Food", 0xFFF9DDD8.toInt(), 0xFFCDA98B.toInt(), listOf("🍎", "🍕", "🥑", "🍩", "🍜", "🧋", "🍓", "🥞", "🍫", "🍯")),
        StickerPack("Party", 0xFFE4D6F4.toInt(), 0xFFB09AD8.toInt(), listOf("🎂", "🎈", "🎁", "🎉", "🥳", "🎊", "🍾", "🧁", "🎀", "🪅")),
        StickerPack("Sea Life", 0xFFD6F0DB.toInt(), 0xFF5B77C8.toInt(), listOf("🐠", "🦀", "🐚", "🌊", "🐙", "🐬", "⭐", "🏖️", "🦑", "🐳")),
        StickerPack("Travel", 0xFFD8E8F8.toInt(), 0xFF9CC4E8.toInt(), listOf("✈️", "🧳", "🗺️", "📷", "🏝️", "🚗", "⛺", "🚂", "🧭", "🌅")),
        StickerPack("Sports", 0xFFDDF0D5.toInt(), 0xFFF0B060.toInt(), listOf("⚽", "🏀", "🎾", "🏐", "🏊", "🚴", "🥇", "🏋️", "🧘", "⛳")),
        StickerPack("Music", 0xFFEBD8F0.toInt(), 0xFFB07DC8.toInt(), listOf("🎵", "🎶", "🎹", "🎻", "🥁", "🎷", "🎺", "🎼", "🎙️", "🪕"))
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ctx.getColor(R.color.bg_app))
        }
        // —— cabecera ——
        val head = FrameLayout(ctx)
        head.addView(TextView(ctx).apply {
            text = "Descubrir"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ctx.getColor(R.color.text_primary))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        val right = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, dp(12), 0) }
        right.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_gift_color); background = ctx.getDrawable(R.drawable.bg_circle_gift)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                Dialogs.light(ctx).setTitle("🎁 Descubrir")
                    .setMessage("Plantillas, stickers, diarios y notas adhesivas: todo es local y gratis. Toca cualquier loseta para usarla.")
                    .setPositiveButton("OK", null).show()
            }
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        right.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_search); background = null
            imageTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.text_primary))
            setOnClickListener { searchDialog() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)).also { it.marginStart = dp(8) })
        head.addView(right, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        root.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        // —— pestañas ——
        val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        tabRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), 0, dp(14), 0) }
        hs.addView(tabRow)
        root.addView(hs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        // —— contenido ——
        scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }
        content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(24)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        select(0)
        return root
    }

    private fun select(idx: Int) {
        current = idx
        val ctx = requireContext()
        tabRow.removeAllViews()
        for ((i, name) in tabs.withIndex()) {
            val on = i == idx
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(TextView(ctx).apply {
                text = name; textSize = 14.5f
                setTextColor(if (on) ctx.getColor(R.color.text_primary) else ctx.getColor(R.color.text_dim))
                typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setPadding(dp(14), dp(10), dp(14), dp(6))
            })
            col.addView(View(ctx).apply {
                setBackgroundColor(if (on) Gender.accent(ctx) else Color.TRANSPARENT)
            }, LinearLayout.LayoutParams(dp(34), dp(2)))
            col.setOnClickListener { select(i) }
            tabRow.addView(col)
        }
        registry.clear()
        content.removeAllViews()
        when (idx) {
            0 -> buildHome()
            1 -> buildTemplates()
            2 -> buildStickers()
            3 -> buildBooks()
            4 -> buildSamples()
            else -> buildSticky()
        }
        scroll.scrollTo(0, 0)
    }

    /* ================== bloques reutilizables ================== */

    private fun sectionHeader(title: String, moreTab: Int = -1) {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(14), dp(4), dp(8)) }
        row.addView(TextView(ctx).apply {
            text = title; textSize = 15f; setTextColor(ctx.getColor(R.color.text_primary)); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (moreTab >= 0) row.addView(TextView(ctx).apply {
            text = "ver todo  ›"; textSize = 12.5f; setTextColor(ctx.getColor(R.color.text_chip)); setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener { select(moreTab) }
        })
        content.addView(row)
    }

    private fun colWidth(cols: Int, gapDp: Int = 10): Int {
        val avail = resources.displayMetrics.widthPixels - dp(62) - dp(24)
        return (avail - dp(gapDp) * (cols - 1)) / cols
    }

    /** loseta = ilustración + etiqueta; w en px, hRatio = alto/ancho de la ilustración */
    private fun tile(w: Int, hRatio: Float, label: String, cfg: (TileArtView) -> Unit, onClick: () -> Unit, tab: String): View {
        val ctx = requireContext()
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val art = TileArtView(ctx); cfg(art)
        col.addView(art, LinearLayout.LayoutParams(w, (w * hRatio).toInt()))
        col.addView(TextView(ctx).apply {
            text = label; textSize = 12.5f; gravity = Gravity.CENTER; maxLines = 2
            setTextColor(ctx.getColor(R.color.text_primary)); setPadding(0, dp(7), 0, dp(4))
        }, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.setOnClickListener { onClick() }
        registry.add(Entry(label, tab, onClick))
        return col
    }

    private fun hRow(views: List<View>, gapDp: Int = 12) {
        val ctx = requireContext()
        val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (v in views) row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(gapDp) })
        hs.addView(row)
        content.addView(hs)
    }

    private fun grid(cols: Int, views: List<View>, gapDp: Int = 10) {
        val ctx = requireContext()
        var i = 0
        while (i < views.size) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0 until cols) {
                val v = views.getOrNull(i + k)
                if (v != null) row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(gapDp) })
            }
            content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(10) })
            i += cols
        }
    }

    /* ================== pestañas ================== */

    private fun packTile(pk: StickerPack, w: Int) = tile(w, 1.0f, pk.name, {
        it.mode = TileArtView.STICKERS; it.bg = pk.bg; it.accent = pk.accent; it.emojis = pk.emojis
    }, { packDialog(pk) }, "Stickers")

    private fun bookTile(i: Int, w: Int) = tile(w, 1.0f, "Journal ${i + 1}", {
        it.mode = TileArtView.BOOK; it.variant = i
    }, { useTemplate("Book Journal ${i + 1}", if (i % 3 == 1) 1 else 0, 1, 0, 0, 6, i) }, "Book Journal")

    private fun buildHome() {
        val w = dp(124)
        sectionHeader("Templates", 1)
        hRow(listOf(
            tile(w, 1.0f, "All-in-One Planner", { it.variant = 0 }, { select(1) }, "inicio"),
            tile(w, 1.0f, "Daily organizer", { it.variant = 2 }, { select(1) }, "inicio"),
            tile(w, 1.0f, "Weekly organizer", { it.variant = 4 }, { select(1) }, "inicio")))
        sectionHeader("Stickers", 2)
        hRow(packs.take(3).map { packTile(it, w) })
        sectionHeader("Book Journal", 3)
        hRow((0..2).map { bookTile(it, w) })
        sectionHeader("Sample Journals", 4)
        hRow(sampleEntries().take(3).map { sampleTile(it, w) })
    }

    private class Sample(val name: String, val variant: Int, val tpl: Int, val real: Int, val pages: Int)

    private fun sampleEntries() = listOf(
        Sample("Daily Checklist", 0, 0, 3, 4), Sample("Diary", 1, 0, 0, 6),
        Sample("Simple Scheduler", 2, 9, 2, 4), Sample("Monthly Review", 3, 7, 0, 4),
        Sample("Yearly Planner", 4, 5, 0, 4), Sample("Monthly Overview 2026", 5, 1, 6, 12),
        Sample("Monthly", 6, 5, 0, 12), Sample("Daily Planner", 7, 9, 2, 8)
    )

    private fun sampleTile(s: Sample, w: Int) = tile(w, 0.8f, s.name, {
        it.mode = TileArtView.SAMPLE; it.variant = s.variant; it.title = s.name
    }, { useTemplate(s.name, s.tpl, 1, 0, s.real, s.pages, s.variant) }, "Sample Journals")

    private fun buildTemplates() {
        val ctx = requireContext()
        content.addView(TextView(ctx).apply {
            text = "🧭 Biblioteca completa (más de 40 plantillas por tema)  ›"
            textSize = 13f; setTextColor(0xFF1A1A1A.toInt()); setBackgroundResource(R.drawable.bg_chip_selected)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { TemplatesLibrary.open(ctx) { (activity as? com.knotes.app.MainActivity)?.launchImportFile() } }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(6) })
        val w = dp(150)
        fun t(name: String, variant: Int, tpl: Int, real: Int, pages: Int) =
            tile(w, 1.0f, name, { it.variant = variant; it.title = name }, { useTemplate(name, tpl, 1, 0, real, pages, variant) }, "Templates")
        sectionHeader("All-in-One Planner")
        hRow(listOf(t("Daily routine", 0, 9, 2, 6), t("Checklist", 1, 0, 3, 4), t("Notes grid", 6, 1, 0, 4)))
        sectionHeader("Daily organizer")
        hRow(listOf(t("Horizontal", 2, 9, 2, 6), t("Vertical", 3, 0, 2, 6), t("Minimal", 1, 3, 0, 4)))
        sectionHeader("Weekly organizer")
        hRow(listOf(t("Weekly to do", 4, 0, 3, 4), t("Weekly schedule", 5, 5, 6, 8), t("Week list", 7, 0, 1, 4)))
    }

    private fun buildStickers() {
        val w = colWidth(3)
        content.addView(View(requireContext()), LinearLayout.LayoutParams(1, dp(8)))
        grid(3, packs.map { packTile(it, w) })
    }

    private fun buildBooks() {
        val w = colWidth(3)
        content.addView(View(requireContext()), LinearLayout.LayoutParams(1, dp(8)))
        grid(3, (0..9).map { bookTile(it, w) })
    }

    private fun buildSamples() {
        val all = sampleEntries()
        val w = colWidth(2)
        sectionHeader("Simple")
        grid(2, all.take(4).map { sampleTile(it, w) })
        sectionHeader("Planning")
        grid(2, all.drop(4).map { sampleTile(it, w) })
    }

    private fun buildSticky() {
        val names = listOf("Label", "Annotate 1", "Basic note", "Cute hand drawn", "Cute hand drawn 2", "Cute hand drawn 3",
            "Flower", "Memphis drawing", "Note 1", "Note 2", "Shapes", "Paper")
        val w = colWidth(3)
        content.addView(View(requireContext()), LinearLayout.LayoutParams(1, dp(8)))
        grid(3, names.mapIndexed { i, n ->
            tile(w, 1.0f, n, {
                it.mode = TileArtView.STICKY; it.variant = i
                it.bg = if (i % 3 == 0 || i % 3 == 1 && i > 5) 0xFFF7DDE5.toInt() else 0xFFEFEAD8.toInt()
                if (i % 2 == 0) it.bg = 0xFFF7DDE5.toInt() else it.bg = 0xFFEFEAD8.toInt()
            }, {
                for (e in listOf("🗒️", "📝", "📌", "📎", "🏷️", "⭐")) Prefs.addCustomSticker(e)
                Toast.makeText(requireContext(), "«$n» añadida: encuentra sus notas en Stickers → Míos 📝", Toast.LENGTH_LONG).show()
            }, "Sticky")
        })
    }

    /* ================== acciones ================== */

    private fun packDialog(pk: StickerPack) {
        Dialogs.light(requireContext())
            .setTitle("Stickers: ${pk.name}")
            .setMessage(pk.emojis.joinToString("  ") + "\n\n¿Añadirlos a «Míos» para usarlos en el editor?")
            .setPositiveButton("Añadir") { _, _ ->
                for (e in pk.emojis) Prefs.addCustomSticker(e)
                Toast.makeText(requireContext(), "${pk.emojis.size} stickers añadidos ✨", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null).show()
    }

    private fun useTemplate(title: String, tpl: Int, paper: Int, decor: Int, real: Int, nPages: Int, styleSeed: Int = 0) {
        val ctx = requireContext()
        val pgs: MutableList<KPage> = ArrayList()
        for (n in 0 until nPages.coerceAtLeast(1)) pgs.add(KPage(Ids.next(), tpl.coerceIn(0, 9), paper.coerceIn(0, 7), decor.coerceIn(0, 13)))
        if (real > 0) {
            pgs[0].paper = 1
            pgs[0].template = 3
            pgs[0].items.addAll(CanvasPageView.buildRealTemplate(real - 1))
        }
        val style = Math.floorMod(title.hashCode() + styleSeed, NotebookCoverView.coverStyles())
        val nb = Store.createNotebook(title, style, ArrayList(), title.hashCode().toLong(), pgs)
        Toast.makeText(ctx, "✅ «$title» lista en tus Diarios", Toast.LENGTH_SHORT).show()
        startActivity(Intent(ctx, EditorActivity::class.java).putExtra("id", nb.id))
    }

    private fun searchDialog() {
        val ctx = requireContext()
        // el catálogo completo (todas las pestañas) para buscar
        val saved = current
        val all = ArrayList<Entry>()
        for (i in 1 until tabs.size) { select(i); all.addAll(registry) }
        select(saved)
        val input = EditText(ctx).apply {
            hint = "Buscar plantillas, stickers, diarios…"
            setTextColor(0xFF38322F.toInt()); setHintTextColor(0xFF9A9186.toInt())
            background = ctx.getDrawable(R.drawable.bg_cell_white); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val frame = FrameLayout(ctx).apply { setPadding(dp(18), dp(4), dp(18), 0); addView(input) }
        Dialogs.light(ctx).setTitle("Buscar").setView(frame)
            .setPositiveButton("Buscar") { _, _ ->
                val q = input.text.toString().trim()
                val hits = all.filter { it.title.contains(q, true) }.distinctBy { it.title }
                if (hits.isEmpty()) Toast.makeText(ctx, "Sin resultados 🔎", Toast.LENGTH_SHORT).show()
                else Dialogs.light(ctx).setTitle("Resultados (${hits.size})")
                    .setItems(hits.map { "${it.title}  ·  ${it.tab}" }.toTypedArray()) { _, w -> hits[w].action() }
                    .setNegativeButton("Cerrar", null).show()
            }
            .setNegativeButton("Cancelar", null).show()
    }
}
