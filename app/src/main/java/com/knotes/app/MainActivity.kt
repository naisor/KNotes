package com.knotes.app

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.knotes.app.ui.BackupUtil
import com.knotes.app.ui.FeedFragment
import com.knotes.app.ui.DiscoverFragment
import com.knotes.app.ui.CalendarFragment
import com.knotes.app.ui.TasksFragment
import com.knotes.app.ui.HomeFragment
import com.knotes.app.ui.MiscFragments
import com.knotes.app.ui.TemplatesLibrary
import com.knotes.app.ui.Dialogs
import com.knotes.app.ui.Gender
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction

/**
 * MainActivity — raíl lateral izquierdo + navegación inferior.
 * · raíl: diarios / 🧭 plantillas externas / 💾 respaldo / ayuda / papelera
 * · bottom: Diarios / Calendario / Tareas / Mercado / Ajustes
 */
class MainActivity : AppCompatActivity() {

    lateinit var railNotes: ImageButton
    lateinit var railCompass: ImageButton
    lateinit var railStore: ImageButton
    lateinit var railTrash: ImageButton
    lateinit var railHelp: ImageButton

    /** los 5 iconos de la barra inferior: diarios · calendario · tareas · mercado · ajustes */
    private val navBtns = ArrayList<Pair<String, ImageButton>>()

    var currentTab = "home"

    /** ya mostré el cuadro del nombre en esta sesión (no repetir en rotaciones/vueltas) */
    private var askedName = false

    /* destinación del respaldo (0 JSON 1 TXT 2 HTML) */
    private var backupKind = 0

    private val backupCreate = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { BackupUtil.writeAll(this, it, backupKind) }
    }

    private val importOpen = registerForActivityResult(
        ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { BackupUtil.restoreFrom(this, it) }
    }

    /** para la biblioteca de plantillas: importar archivo externo */
    fun launchImportFile() {
        importOpen.launch(arrayOf("*/*"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.knotes.app.ui.Gender.applyTheme(this)
        setContentView(R.layout.activity_main)
        paintStatus()

        railNotes = findViewById(R.id.rail_notes)
        railCompass = findViewById(R.id.rail_compass)
        railStore = findViewById(R.id.rail_store)
        railTrash = findViewById(R.id.rail_trash)
        railHelp = findViewById(R.id.rail_help)

        railNotes.setOnClickListener { show("home") }
        railCompass.setOnClickListener { show("discover") }
        railStore.setOnClickListener { openBackup() }
        railTrash.setOnClickListener { show("trash") }
        railHelp.setOnClickListener { showHelp() }

        // barra inferior de 5 iconos (sin texto, el activo con círculo de color)
        navBtns.add("home" to findViewById<ImageButton>(R.id.nav_journals))
        navBtns.add("calendar" to findViewById<ImageButton>(R.id.nav_calendar))
        navBtns.add("tasks" to findViewById<ImageButton>(R.id.nav_tasks))
        navBtns.add("market" to findViewById<ImageButton>(R.id.nav_market))
        navBtns.add("settings" to findViewById<ImageButton>(R.id.nav_settings))
        for ((tab, btn) in navBtns) btn.setOnClickListener { show(tab) }

        // lápiz flotante del borde: abre el último diario editado (o crea uno)
        findViewById<ImageButton>(R.id.float_pen_main).setOnClickListener {
            val last = com.knotes.app.data.Store.all().maxByOrNull { it.updatedAt }
            if (last != null) {
                startActivity(android.content.Intent(this, EditorActivity::class.java).putExtra("id", last.id))
            } else {
                show("home")
                supportFragmentManager.fragments.filterIsInstance<HomeFragment>().firstOrNull()?.createDialog()
            }
        }

        if (savedInstanceState == null) show("home")
        // Primer arranque: ¿mujer u hombre? + tu nombre (se recuerda para siempre)
        if (!Gender.configured) Gender.onboarding(this)
    }

    /** status bar con el color y el contraste del tema ACTIVO (claro/oscuro) */
    fun paintStatus() {
        window.statusBarColor = resources.getColor(R.color.bg_app, theme)
        val night = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.decorView.systemUiVisibility = if (night)
            window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        else
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    private fun showHelp() {
        Dialogs.light(this)
            .setTitle("Ayuda de KNotes")
            .setMessage(
                "• Pulsa «+» o «Nuevo Diario» para crear cuadernos ilimitados.\n" +
                "• 🧭 Brújula: biblioteca de plantillas con vista previa; «Descargar» las incorpora. Ahí también importas plantillas o respaldos de archivo.\n" +
                "• ✉️ Sobre: respalda TODOS tus diarios (JSON restaurable, TXT o HTML) en tu teléfono o Drive con el selector de Archivos.\n" +
                "• En el editor: lápiz, rotulador, lápiz suave, subrayador, goma, texto, imágenes, enlaces, stickers y exportar a TXT/PDF/Word/JPG/PNG.\n" +
                "• GUARDA y vuelve a entrar: tus trazos siguen siendo editables y borrables. La goma borra por trazo o por zona, y con el lazo mueves o eliminas cualquier elemento.\n" +
                "• Dos dedos para zoom / desplazarte. Cambia el tipo de página: rayado, cuadrícula, puntos o en blanco."
            )
            .setPositiveButton("¡Entendido!", null)
            .show()
    }

    /* ================== respaldo ================== */

    private fun openBackup() {
        val items = arrayOf(
            "✉️ Crear respaldo (JSON · restaurable)",
            "📝 Crear respaldo (TXT legible)",
            "🌐 Crear respaldo (HTML legible)",
            "♻️ Restaurar respaldo (.json)",
            "☁️ Subir respaldo a Drive (Google)",
            "ℹ️ ¿Qué contiene el respaldo?"
        )
        Dialogs.light(this)
            .setTitle("💾 Respaldo de todos tus diarios")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { backupKind = 0; backupCreate.launch("KNotes-respaldo-${BackupUtil.dateTag()}.json") }
                    1 -> { backupKind = 1; backupCreate.launch("KNotes-respaldo-${BackupUtil.dateTag()}.txt") }
                    2 -> { backupKind = 2; backupCreate.launch("KNotes-respaldo-${BackupUtil.dateTag()}.html") }
                    3 -> importOpen.launch(arrayOf("*/*"))
                    4 -> backupToDrive()
                    else -> Dialogs.light(this)
                        .setTitle("ℹ️ Sobre el respaldo")
                        .setMessage(
                            "El respaldo guarda TODOS tus diarios y páginas en el mismo ORDEN en que fueron creados.\n\n" +
                            "· JSON: copia completa (trazos, textos, stickers, enlaces e imágenes) — este es el que puedes RESTAURAR.\n" +
                            "· TXT/HTML: solo lectura para archivar o imprimir.\n\n" +
                            "ElSelector de Archivos te deja guardar en Descargas o en Google Drive. Restaurar añade los diarios sin pisar los que ya existen."
                        )
                        .setPositiveButton("OK", null).show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /** Genera el JSON restaurable y lo ENVÍA a Drive (compartiendo, sin cuenta) */
    private fun backupToDrive() {
        try {
            val dir = java.io.File(cacheDir, "export").apply { mkdirs() }
            val f = java.io.File(dir, "KNotes-respaldo-${BackupUtil.dateTag()}.json")
            f.writeText(BackupUtil.buildJson(com.knotes.app.data.Store.all()))
            val u = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.files", f)
            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, u)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Respaldo KNotes")
                putExtra(android.content.Intent.EXTRA_TITLE, "Respaldo KNotes · sube tu Google Drive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(share, "Subir a Drive ☁️ (elige Drive entre tus apps)"))
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo preparar el respaldo 😕", Toast.LENGTH_SHORT).show()
        }
    }

    fun show(tab: String) {
        currentTab = tab
        val f: Fragment = when (tab) {
            "market" -> FeedFragment.newMarket()
            "discover" -> DiscoverFragment()
            "calendar" -> CalendarFragment()
            "tasks" -> TasksFragment()
            "trash" -> MiscFragments.TrashFragment()
            "settings" -> MiscFragments.SettingsFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_host, f)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
        paintRail()
        paintBottomNav()
    }

    private fun paintRail() {
        val dim = resources.getColor(R.color.text_chip, theme)
        fun bind(btn: ImageButton, active: Boolean, activeIcon: Int?, idleIcon: Int) {
            if (active) {
                btn.background = getDrawable(Gender.circleBg())
                btn.imageTintList = null
                btn.setImageResource(activeIcon ?: idleIcon)
                if (activeIcon == null) btn.imageTintList = ColorStateList.valueOf(0xFF1A1A1A.toInt())
            } else {
                btn.background = null
                btn.setImageResource(idleIcon)
                btn.imageTintList = ColorStateList.valueOf(dim)
            }
        }
        // el cuaderno activo es amarillo sobre círculo rosa; inactivo = contorno
        bind(railNotes, currentTab == "home", R.drawable.ic_rail_journal, R.drawable.ic_o_journal)
        bind(railCompass, currentTab == "discover", null, R.drawable.ic_o_compass)
        bind(railStore, false, null, R.drawable.ic_o_store)
        bind(railTrash, currentTab == "trash", null, R.drawable.ic_o_trash)
        bind(railHelp, false, null, R.drawable.ic_o_help)
    }

    private fun paintBottomNav() {
        val sel = when (currentTab) {
            "trash", "discover" -> ""          // no pertenecen a la barra inferior
            else -> currentTab
        }
        val idle = resources.getColor(R.color.text_chip, theme)
        for ((tab, btn) in navBtns) {
            val on = tab == sel
            btn.background = if (on) getDrawable(Gender.circleBg()) else null
            btn.imageTintList = ColorStateList.valueOf(if (on) 0xFF1A1A1A.toInt() else idle)
        }
    }

    override fun onResume() {
        super.onResume()
        paintStatus()
        // refresca al volver del editor
        supportFragmentManager.findFragmentById(R.id.content_host)?.let {
            supportFragmentManager.beginTransaction().detach(it).attach(it).commitAllowingStateLoss()
        }
        // buscador de actualizaciones en silencio (aún si GIST_URL_HERE está configurado)
        com.knotes.app.io.Actualizaciones.check(this, auto = true)
        // NOMBRE SIN DEFINIR: el cuadro sale CADA VEZ QUE INGRESAS a la app,
        // y se apaga solito después de que lo fijes (una sola vez por sesión abierta)
        if (Gender.configured && !askedName &&
            com.knotes.app.data.Prefs.userName.isBlank()) {
            askedName = true
            Gender.askName(this)
        }
    }
}
