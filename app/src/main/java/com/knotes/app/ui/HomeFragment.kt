package com.knotes.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.knotes.app.EditorActivity
import com.knotes.app.R
import com.knotes.app.data.KPage
import com.knotes.app.data.Notebook
import com.knotes.app.data.Prefs
import com.knotes.app.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Portada: «Todos los diarios de ⟨nombre⟩» con rejilla, chips, banners, FAB
 *  y SELECCIÓN MÚLTIPLE (mantener pulsado): eliminar, mover, compartir, info. */
class HomeFragment : Fragment() {

    private lateinit var fabNew: View
    private lateinit var grid: RecyclerView
    private lateinit var chipsRow: LinearLayout
    private lateinit var selBar: LinearLayout
    private lateinit var tvSelCount: TextView
    private lateinit var adapter: HomeAdapter
    private var filterTag: String = "todos"
    private var searchText: String = ""
    private var sortMode: Int = 0  // 0 recientes, 1 favoritos primero, 2 alfabético

    /* selección múltiple */
    private var selectionMode = false
    private val selected = LinkedHashMap<String, Notebook>()
    private val sdfCard = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val sdfFull = SimpleDateFormat("d 'de' MMMM yyyy · HH:mm", Locale.getDefault())

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_home, c, false)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val prefs = requireContext().getSharedPreferences("knotes", Context.MODE_PRIVATE)
        sortMode = prefs.getInt("sort", 0)
        grid = v.findViewById(R.id.home_grid)
        chipsRow = v.findViewById(R.id.chips_row)
        selBar = v.findViewById(R.id.sel_bar)
        tvSelCount = v.findViewById(R.id.tv_sel_count)
        fabNew = v.findViewById(R.id.fab_new)
        adapter = HomeAdapter()
        // tarjetas compactas como en la captura: 3 a 6 columnas según el ancho disponible
        val usable = resources.displayMetrics.widthPixels - dp(62) - dp(24)
        val span = (usable / dp(104)).coerceIn(3, 6)
        val glm = GridLayoutManager(requireContext(), span)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (adapter.isBannerPos(position)) span else 1
        }
        grid.layoutManager = glm
        grid.adapter = adapter

        /* Título personalizado con el nombre del primer arranque */
        val name = Prefs.userName
        val title = v.findViewById<TextView>(R.id.home_title)
        title.text = if (name.isBlank()) "Todos los diarios" else "Todos los diarios de $name"
        // «subir para ver»: toca el título y la rejilla sube a la primera
        title.setOnClickListener { grid.smoothScrollToPosition(0) }

        // el scroll deja VER los diarios de abajo: padding que despeja la barra + el FAB
        grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, dp(96))

        /* barra de selección múltiple */
        v.findViewById<ImageButton>(R.id.ib_sel_close).setOnClickListener { exitSelection() }
        v.findViewById<ImageButton>(R.id.ib_sel_delete).setOnClickListener { selDelete() }
        v.findViewById<ImageButton>(R.id.ib_sel_move).setOnClickListener { selMove() }
        v.findViewById<ImageButton>(R.id.ib_sel_share).setOnClickListener { selShare() }
        v.findViewById<ImageButton>(R.id.ib_sel_info).setOnClickListener { selInfo() }

        /* banner PEQUEÑO de actualización: arriba, con el botón «Ir» → buscar actualizaciones */
        v.findViewById<View>(R.id.banner_update_go).setOnClickListener {
            (activity as? com.knotes.app.MainActivity)?.show("settings")
        }
        com.knotes.app.io.Actualizaciones.uiListener = { updBanner() }
        updBanner()

        v.findViewById<ImageButton>(R.id.btn_search).setOnClickListener { openSearch() }
        v.findViewById<ImageButton>(R.id.btn_more).setOnClickListener { openMore(v) }
        // regalo (círculo amarillo suave con regalo a color, como la captura)
        v.findViewById<ImageButton>(R.id.btn_gift).apply {
            setOnClickListener {
                Dialogs.light(requireContext())
                    .setTitle("🎁 Sorpresas de KNotes")
                    .setMessage("Nuevas portadas y stickers locales desbloqueadas al crear tu primer diario. ¡Todo gratis y sin conexión!")
                    .setPositiveButton("Genial", null).show()
            }
        }
        // botón flotante «nuevo diario»: círculo del acento con cruz oscura
        v.findViewById<ImageButton>(R.id.fab_new).apply {
            background = requireContext().getDrawable(Gender.circleBg())
            setOnClickListener { createDialog() }
        }
        // carpeta con estrella: crear etiquetas/filtros extra
        v.findViewById<ImageButton>(R.id.btn_folders).setOnClickListener { tagFoldersDialog() }

        refreshChips()
        refresh()
    }

    /* ================== PORTADA CON FOTO: el usuario carga su imagen ================== */

    private var pendingCoverUri: android.net.Uri? = null
    private var coverPickTarget: Notebook? = null

    private val coverPick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = coverPickTarget
        if (uri != null) {
            if (target == null) {
                pendingCoverUri = uri   // vienes del CREADOR de diarios
                Toast.makeText(requireContext(), "Foto cargada ✓ — toca CREAR para mantenerla", Toast.LENGTH_SHORT).show()
            } else if (Store.saveCoverImage(requireContext(), target, uri)) {
                Toast.makeText(requireContext(), "🖼 Portada con tu foto aplicada ✓", Toast.LENGTH_SHORT).show()
                refresh()
            } else Toast.makeText(requireContext(), "No se pudo usar esa imagen 😕", Toast.LENGTH_SHORT).show()
        }
        coverPickTarget = null
    }

    private fun goDiscover() {
        // Descubrir se eliminó a pedido: queda la biblioteca de plantillas (brújula 🧭)
        Toast.makeText(requireContext(), "Descubrir fue retirado; usa la biblioteca 🧭 del raíl", Toast.LENGTH_SHORT).show()
    }

    /* Banner de actualización: SOLO se muestra si el gist ya anunció una versión nueva */
    private fun updBanner() {
        val v = view ?: return
        val up = com.knotes.app.io.Actualizaciones
        val bn = v.findViewById<View>(R.id.banner_update)
        if (up.available) {
            bn.visibility = View.VISIBLE
            v.findViewById<TextView>(R.id.banner_update_msg).text =
                "🔔 Nueva versión disponible — ${up.availableName}"
        } else bn.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        com.knotes.app.io.Actualizaciones.uiListener = null
    }

        private fun noteList(): List<Notebook> {
        val base = Store.all()
        val searched = if (searchText.isBlank()) base else base.filter {
            it.title.contains(searchText, true) || it.tags.any { t -> t.contains(searchText, true) }
        }
        val tagged = if (filterTag == "todos") searched else searched.filter { nb -> nb.tags.contains(filterTag) }
        return when (sortMode) {
            1 -> tagged.sortedByDescending { it.favorite }
            2 -> tagged.sortedBy { it.title.lowercase() }
            else -> tagged
        }
    }

    private fun refresh() {
        if (!this::adapter.isInitialized) return
        adapter.notifyDataSetChanged()
    }

    private fun allFilterTags(): List<String> {
        val extra = requireContext().getSharedPreferences("knotes", Context.MODE_PRIVATE)
            .getStringSet("extraTags", emptySet()) ?: emptySet()
        return listOf("todos") + (Store.allTags() + extra.toList()).distinct().filter { it != "todos" }
    }

    private fun refreshChips() {
        chipsRow.removeAllViews()
        for (tag in allFilterTags()) {
            val on = tag == filterTag
            val tv = TextView(requireContext())
            // GANCHO ✓ en el filtro elegido (pedido #3)
            tv.text = if (on) "✓ $tag" else tag
            tv.setPadding(dp(16), dp(7), dp(16), dp(7))
            tv.textSize = 14f
            tv.setTextColor(if (on) 0xFF1A1A1A.toInt() else requireContext().getColor(R.color.text_dim))
            tv.background = requireContext().getDrawable(if (on) Gender.chipSelBg() else R.drawable.bg_chip_normal)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(8)
            tv.layoutParams = lp
            tv.setOnClickListener { filterTag = tag; refreshChips(); refresh() }
            chipsRow.addView(tv)
        }
    }

    /** carpeta ★: crea etiquetas (filtros) nuevas o quita las que creaste */
    private fun tagFoldersDialog() {
        val ctx = requireContext()
        val sp = ctx.getSharedPreferences("knotes", Context.MODE_PRIVATE)
        val input = EditText(ctx).apply {
            hint = "Nueva etiqueta (ej. Work)"
            setTextColor(0xFF38322F.toInt()); setHintTextColor(0xFF9A9186.toInt())
            background = ctx.getDrawable(R.drawable.bg_cell_white)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        Dialogs.light(ctx)
            .setTitle("🏷 Etiquetas / filtros")
            .setMessage("Crea una etiqueta y asígnala a un diario desde su menú ▾ → Etiquetas. Aparece como filtro arriba.")
            .setView(frame(input))
            .setPositiveButton("Crear") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    val set = LinkedHashSet(sp.getStringSet("extraTags", emptySet()) ?: emptySet())
                    set.add(t)
                    sp.edit().putStringSet("extraTags", set).apply()
                    refreshChips()
                }
            }
            .setNeutralButton("Quitar las mías") { _, _ ->
                sp.edit().remove("extraTags").apply()
                if (filterTag != "todos" && !Store.allTags().contains(filterTag)) filterTag = "todos"
                refreshChips(); refresh()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /* ================== SELECCIÓN MÚLTIPLE ================== */

    private fun enterSelection(nb: Notebook) {
        selectionMode = true
        selected.clear()
        selected[nb.id] = nb
        selBar.visibility = View.VISIBLE
        fabNew.visibility = View.INVISIBLE
        grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, dp(24))
        updateSelBar(); refresh()
        Toast.makeText(requireContext(), "Toca tarjetas para sumar o quitar ✓", Toast.LENGTH_SHORT).show()
    }

    private fun exitSelection() {
        selectionMode = false
        selected.clear()
        selBar.visibility = View.GONE
        fabNew.visibility = View.VISIBLE
        grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, dp(96))
        refresh()
    }

    private fun toggleSelect(nb: Notebook) {
        if (selected.containsKey(nb.id)) selected.remove(nb.id) else selected[nb.id] = nb
        if (selected.isEmpty()) exitSelection() else updateSelBar()
        refresh()
    }

    private fun updateSelBar() {
        tvSelCount.text = "${selected.size} seleccionado${if (selected.size == 1) "" else "s"}"
    }

    private fun selDelete() {
        if (selected.isEmpty()) return
        Dialogs.light(requireContext())
            .setTitle("¿Mover ${selected.size} ${if (selected.size == 1) "diario" else "diarios"} a la papelera?")
            .setMessage("Podrás restaurarlos desde la papelera.")
            .setPositiveButton("Mover") { _, _ ->
                for (nb in selected.values) Store.toTrash(nb)
                Toast.makeText(requireContext(), "A la papelera 🗑", Toast.LENGTH_SHORT).show()
                exitSelection(); refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun selMove() {
        Dialogs.light(requireContext())
            .setTitle("Mover ${selected.size}…")
            .setItems(arrayOf("🗑 A la papelera", "⭐ Marcar como favorito", "⤵ Duplicar")) { _, w ->
                for (nb in selected.values) when (w) {
                    0 -> Store.toTrash(nb)
                    1 -> { nb.favorite = true; Store.saveMeta(nb) }
                    else -> Store.duplicate(nb)
                }
                Toast.makeText(requireContext(), "Listo ✅", Toast.LENGTH_SHORT).show()
                exitSelection(); refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun selShare() {
        if (selected.isEmpty()) return
        com.knotes.app.io.ExportUtil.shareDiaries(requireActivity(), ArrayList(selected.keys))
    }

    private fun selInfo() {
        if (selected.isEmpty()) return
        val sb = StringBuilder()
        for (nb in selected.values) {
            val dir = java.io.File(com.knotes.app.data.Store.root(), nb.id)
            val pages = dir.listFiles { f -> f.name.startsWith("p") && f.name.endsWith(".json") }?.size ?: 0
            val bytes = dir.walkBottomUp().filter { it.isFile }.fold(0L) { acc, f -> acc + f.length() }
            val peso = when {
                bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
                bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
            sb.append("📔 ${nb.title}\n")
            sb.append("   📅 Creado: ${sdfFull.format(Date(nb.createdAt))}\n")
            sb.append("   ✏️ Última edición: ${sdfFull.format(Date(nb.updatedAt))}\n")
            sb.append("   📄 Páginas: ${pages.coerceAtLeast(0)}\n")
            sb.append("   ⚖️ Peso: $peso\n\n")
        }
        val tv = TextView(requireContext()).apply {
            text = sb.toString(); textSize = 14f
            setTextColor(0xFF2A2630.toInt())          // SIEMPRE oscuro: legible en modo claro/oscuro
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val sv = android.widget.ScrollView(requireContext()).apply { addView(tv) }
        Dialogs.light(requireContext())
            .setTitle("ℹ️ Información (${selected.size})")
            .setView(sv)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    /* ---------- acciones ---------- */

    private fun openSearch() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.search_hint); setPadding(dp(18), dp(14), dp(18), dp(14));
            background = requireContext().getDrawable(R.drawable.bg_cell_white);
            setTextColor(0xFF38322F.toInt())
            setHintTextColor(0xFF9A9186.toInt())
        }
        Dialogs.light(requireContext())
            .setTitle("Buscar")
            .setView(frame(input))
            .setPositiveButton("Buscar") { _, _ -> searchText = input.text.toString(); refresh() }
            .setNegativeButton("Limpiar") { _, _ -> searchText = ""; refresh() }
            .show()
    }

    private fun frame(input: View): FrameLayout {
        return FrameLayout(requireContext()).apply {
            setPadding(dp(18), dp(4), dp(18), 0)
            addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** ⋮ = SOLO opciones (el botón + y la tarjeta «Nuevo Diario» crean diarios) */
    private fun openMore(anchor: View) {
        val pm = PopupMenu(requireContext(), anchor.findViewById(R.id.btn_more), Gravity.END)
        pm.menu.add(0, 1, 0, "⚡ Nota rápida")
        pm.menu.add(0, 2, 0, "📕 Importar PDF")
        pm.menu.add(0, 3, 0, "📥 Importar diario (.knotes)")
        pm.menu.add(0, 4, 0, (if (sortMode == 0) "✓ " else "") + "Ordenar: recientes")
        pm.menu.add(0, 5, 0, (if (sortMode == 1) "✓ " else "") + "Ordenar: favoritos primero")
        pm.menu.add(0, 6, 0, (if (sortMode == 2) "✓ " else "") + "Ordenar: alfabético")
        pm.menu.add(0, 7, 0, "Acerca de KNotes")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> quickNote()
                2 -> importPdfNotebook()
                3 -> (activity as? com.knotes.app.MainActivity)?.launchImportFile()
                4, 5, 6 -> { sortMode = item.itemId - 4; requireContext().getSharedPreferences("knotes", Context.MODE_PRIVATE).edit().putInt("sort", sortMode).apply(); refresh() }
                7 -> Dialogs.light(requireContext()).setTitle("KNotes v1.8.0").setMessage("Diarios con trazos SIEMPRE editables.\nHecho con ❤ en Kotlin. Sin internet, 100 % local.").setPositiveButton("OK", null).show()
            }
            true
        }
        pm.show()
    }

    private var chosenStyle: Int = 0

    /** «Nuevo diario»: abre el asistente de pantalla completa (Portada → Página) */
    fun createDialog() {
        startActivity(Intent(requireContext(), com.knotes.app.CreateDiaryActivity::class.java))
    }

    /** Nota rápida: un diario de una página en blanco, listo para escribir */
    private fun quickNote() {
        val fmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("es"))
        val pages: MutableList<com.knotes.app.data.KPage> = ArrayList()
        pages.add(com.knotes.app.data.KPage(com.knotes.app.data.Ids.next(), 3, 1, 0))
        val nb = Store.createNotebook("Nota " + fmt.format(Date()), Random.nextInt(NotebookCoverView.coverStyles()), ArrayList(), Random.nextLong(), pages)
        refreshChips(); refresh()
        openEditor(nb)
    }

    /** Importar PDF: crea un diario y abre el selector de PDF dentro del editor */
    private fun importPdfNotebook() {
        val pages: MutableList<com.knotes.app.data.KPage> = ArrayList()
        pages.add(com.knotes.app.data.KPage(com.knotes.app.data.Ids.next(), 3, 1, 0))
        val nb = Store.createNotebook("PDF importado", Random.nextInt(NotebookCoverView.coverStyles()), ArrayList(), Random.nextLong(), pages)
        refreshChips(); refresh()
        startActivity(Intent(requireContext(), EditorActivity::class.java).putExtra("id", nb.id).putExtra("importPdf", true))
    }

    fun openEditor(nb: Notebook) {
        requireContext().startActivity(Intent(requireContext(), EditorActivity::class.java).putExtra("id", nb.id))
    }

    private fun high(grid: GridLayout, chosen: Int) {
        val ctx = requireContext()
        for (i in 0 until grid.childCount) {
            (grid.getChildAt(i) as FrameLayout).background =
                ctx.getDrawable(if ((grid.getChildAt(i).tag as Int) == chosen) Gender.chipSelBg() else R.drawable.bg_card_soft)
        }
    }

    /* ---------- Adapter ---------- */

    private fun bannersLeft(): Boolean {
        val sp = requireContext().getSharedPreferences("knotes", Context.MODE_PRIVATE)
        return !(sp.getBoolean("banner1_hidden", false) && sp.getBoolean("banner2_hidden", false))
    }

    inner class HomeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        val TYPE_CREATE = 0
        val TYPE_NOTE = 1
        val TYPE_BANNERS = 2

        private fun showBanners() = !selectionMode && bannersLeft()

        fun isBannerPos(pos: Int) = showBanners() && pos == noteList().size + 1

        override fun getItemCount(): Int = noteList().size + 1 + (if (showBanners()) 1 else 0)

        override fun getItemViewType(position: Int) = when {
            position == 0 -> TYPE_CREATE
            isBannerPos(position) -> TYPE_BANNERS
            else -> TYPE_NOTE
        }

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): RecyclerView.ViewHolder = when (vt) {
            TYPE_CREATE -> CreateHolder(LayoutInflater.from(requireContext()).inflate(R.layout.item_create, parent, false))
            TYPE_BANNERS -> BannerHolder(FrameLayout(requireContext()).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            else -> NoteHolder(LayoutInflater.from(requireContext()).inflate(R.layout.item_notebook, parent, false))
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            when (h) {
                is CreateHolder -> h.itemView.setOnClickListener { createDialog() }
                is BannerHolder -> h.bind()
                is NoteHolder -> {
                    val nb = noteList()[pos - 1]
                    h.bind(nb)
                }
            }
        }
    }

    /** Fila de dos banners dentro del scroll (Discover + Tutoriales), cada uno con su ✕ */
    inner class BannerHolder(private val box: FrameLayout) : RecyclerView.ViewHolder(box) {
        fun bind() {
            val ctx = requireContext()
            val sp = ctx.getSharedPreferences("knotes", Context.MODE_PRIVATE)
            box.removeAllViews()
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(6), dp(18), dp(6), dp(8))
            }
            fun card(kind: Int, key: String, caption: String, onOpen: () -> Unit): View {
                val hidden = sp.getBoolean(key, false)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = dp(6); lp.marginEnd = dp(6)
                if (hidden) return View(ctx).apply { layoutParams = lp }
                val frame = FrameLayout(ctx).apply { layoutParams = lp; setOnClickListener { onOpen() } }
                val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                val art = BannerArtView(ctx)
                art.kind = kind
                col.addView(art, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)))
                val cap = TextView(ctx).apply {
                    text = caption; textSize = 12.5f; gravity = Gravity.CENTER
                    setTextColor(0xFF111111.toInt()); setBackgroundColor(Color.WHITE)
                    maxLines = 2; setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                col.addView(cap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                frame.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                val close = TextView(ctx).apply {
                    text = "✕"; textSize = 15f; setTextColor(0xFF222222.toInt()); gravity = Gravity.CENTER
                    setOnClickListener { sp.edit().putBoolean(key, true).apply(); adapter.notifyDataSetChanged() }
                }
                val clp = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END)
                clp.setMargins(0, dp(2), dp(2), 0)
                frame.addView(close, clp)
                return frame
            }
            row.addView(card(0, "banner1_hidden", "Explore now for inspiration!") {
                (activity as? com.knotes.app.MainActivity)?.show("discover")
            })
            row.addView(card(1, "banner2_hidden", "Enhance your journaling with our tutorials") {
                Dialogs.light(ctx)
                    .setTitle("Tutorial breve")
                    .setMessage("1. Crea un diario → 2. Bordea con el lápiz → 3. Guarda 💾\n\nCuando vuelvas a entrar podrás borrar cualquier trazo con la goma o el lazo.")
                    .setPositiveButton("OK", null).show()
            })
            box.addView(row)
        }
    }

    inner class CreateHolder(v: View) : RecyclerView.ViewHolder(v)

    inner class NoteHolder(v: View) : RecyclerView.ViewHolder(v) {
        val cover: NotebookCoverView = v.findViewById(R.id.cover)
        val tv: TextView = v.findViewById(R.id.tv_title)
        val date: TextView = v.findViewById(R.id.tv_date)
        val badge: TextView = v.findViewById(R.id.badge_sel)
        val heart: ImageButton = v.findViewById(R.id.btn_favorite)
        val menuBtn: ImageButton = v.findViewById(R.id.btn_menu)

        fun bind(nb: Notebook) {
            cover.style = nb.coverStyle
            cover.seed = nb.coverSeed
            cover.imgPath = nb.coverImg       // portada con FOTO si la tiene
            cover.solid = nb.coverColor       // o de COLOR sólido
            cover.invalidate()
            tv.text = nb.title
            // fecha de CREACIÓN debajo del nombre
            date.text = sdfCard.format(Date(nb.createdAt))
            heart.setImageResource(if (nb.favorite) R.drawable.ic_heart_fill else R.drawable.ic_heart)
            heart.imageTintList = android.content.res.ColorStateList.valueOf(Gender.heart())

            // estado de selección múltiple
            val isSel = selected.containsKey(nb.id)
            if (selectionMode) {
                badge.visibility = View.VISIBLE
                badge.text = if (isSel) "✓" else ""
                badge.setTextColor(Color.WHITE)
                badge.background = requireContext().getDrawable(if (isSel) Gender.circleBg() else R.drawable.bg_circle_white)
            } else {
                badge.visibility = View.GONE
            }

            itemView.setOnClickListener { if (selectionMode) toggleSelect(nb) else openEditor(nb) }
            // MANTENER PRESIONADO el diario: OPCIONES directamente (carátula, renombrar…)
            itemView.setOnLongClickListener { if (!selectionMode) showMenu(itemView, nb); true }
            menuBtn.setOnClickListener { if (selectionMode) toggleSelect(nb) else showMenu(it, nb) }
            heart.setOnClickListener {
                nb.favorite = !nb.favorite; Store.saveMeta(nb); refresh()
            }
        }

        fun showMenu(anchor: View, nb: Notebook) {
            val pm = PopupMenu(requireContext(), anchor, Gravity.NO_GRAVITY)
            pm.menu.add(0, 1, 0, "🎨 Cambiar carátula")
            pm.menu.add(0, 2, 0, "Renombrar")
            pm.menu.add(0, 3, 0, "Etiquetas")
            pm.menu.add(0, 4, 0, if (nb.favorite) "Quitar de favoritos" else "Favorito ⭐")
            pm.menu.add(0, 5, 0, "Duplicar")
            pm.menu.add(0, 6, 0, "Mover a la papelera")
            pm.menu.add(0, 7, 0, "Seleccionar (barra múltiple)")
            pm.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> coverDialog(nb)
                    2 -> renameDialog(nb)
                    3 -> tagsDialog(nb)
                    4 -> { nb.favorite = !nb.favorite; Store.saveMeta(nb); refresh() }
                    5 -> { Store.duplicate(nb); refresh() }
                    6 -> { Store.toTrash(nb); Toast.makeText(requireContext(), "Diario en la papelera 🗑", Toast.LENGTH_SHORT).show(); refresh() }
                    7 -> enterSelection(nb)
                }
                true
            }
            pm.show()
        }

        fun renameDialog(nb: Notebook) {
            val input = EditText(requireContext()).apply { setText(nb.title); setTextColor(0xFF38322F.toInt()); background = requireContext().getDrawable(R.drawable.bg_cell_white); setPadding(dp(14), dp(10), dp(14), dp(10)) }
            Dialogs.light(requireContext())
                .setTitle("Renombrar diario")
                .setView(frame(input))
                .setPositiveButton("Guardar") { _, _ ->
                    nb.title = input.text.toString().ifBlank { nb.title }
                    Store.saveMeta(nb); refresh()
                }.setNegativeButton("Cancelar", null).show()
        }

        fun coverDialog(nb: Notebook) {
            val ctx = requireContext()
            val gridLayout = GridLayout(ctx).apply { columnCount = 3; setPadding(dp(14), dp(10), dp(14), 0) }
            val chosen = intArrayOf(nb.coverStyle)
            for (i in 0 until NotebookCoverView.coverStyles()) {
                val cell = FrameLayout(ctx).apply { setPadding(dp(6), dp(6), dp(6), dp(6)); tag = i }
                val c = NotebookCoverView(ctx).apply { layoutParams = ViewGroup.LayoutParams(dp(84), dp(110)); style = i; seed = if (i == nb.coverStyle) nb.coverSeed else Random.nextLong() }
                cell.addView(c)
                cell.setOnClickListener { chosen[0] = i; high(gridLayout, chosen[0]) }
                gridLayout.addView(cell, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)))
            }
            high(gridLayout, nb.coverStyle)
            val sc = android.widget.ScrollView(ctx).apply { addView(gridLayout) }
            Dialogs.light(ctx)
                .setTitle("Cambiar portada")
                .setView(sc)
                .setPositiveButton("Aplicar") { _, _ ->
                    // al aplicar un DISEÑO se quita la foto (diseño manda)
                    nb.coverImg = null
                    Store.saveMeta(nb.also { it.coverStyle = chosen[0] })
                    refresh()
                }
                .setNeutralButton("🖼 Con mi foto") { _, _ ->
                    coverPickTarget = nb
                    coverPick.launch("image/*")
                }
                .setNegativeButton("Cancelar", null).show()
        }

        fun high(grid: GridLayout, chosen: Int) {
            val ctx = requireContext()
            for (i in 0 until grid.childCount) {
                (grid.getChildAt(i) as FrameLayout).background = ctx.getDrawable(if ((grid.getChildAt(i).tag as Int) == chosen) Gender.chipSelBg() else R.drawable.bg_card_soft)
            }
        }

        fun tagsDialog(nb: Notebook) {
            val input = EditText(requireContext()).apply {
                hint = "separadas por comas: trabajo, ideas..."
                setText(nb.tags.joinToString())
                setTextColor(0xFF38322F.toInt()); background = requireContext().getDrawable(R.drawable.bg_cell_white); setPadding(dp(14), dp(10), dp(14), dp(10)); setRawInputType(android.text.InputType.TYPE_CLASS_TEXT)
            }
            Dialogs.light(requireContext())
                .setTitle("Etiquetas")
                .setView(frame(input))
                .setPositiveButton("Guardar") { _, _ ->
                    nb.tags = input.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                    Store.saveMeta(nb); refreshChips(); refresh()
                }.setNegativeButton("Cancelar", null).show()
        }
    }
}
