package com.knotes.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/* ============================================================
   Prefs — preferencias globales (archivo «knotes», compartido).
   · theme: 0 sigue al sistema · 1 claro · 2 oscuro (por defecto)  (se recuerda)
   · gender: 0 sin definir · 1 mujer · 2 hombre      (se recuerda)
   · userName: para el título "Todos los diarios de …"
   ============================================================ */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = c.getSharedPreferences("knotes", Context.MODE_PRIVATE)
    }

    fun raw(): SharedPreferences = sp

    var theme: Int
        get() = sp.getInt("theme", 2)   // OSCURO por defecto (como las capturas)
        set(v) = sp.edit().putInt("theme", v).apply()

    var gender: Int
        get() = sp.getInt("gender", 0)
        set(v) = sp.edit().putInt("gender", v).apply()

    var userName: String
        get() = sp.getString("userName", "") ?: ""
        set(v) = sp.edit().putString("userName", v).apply()

    /* stickers propios del usuario (emojis/libres guardados) */
    fun customStickers(): MutableSet<String> =
        LinkedHashSet(sp.getStringSet("customStickers", LinkedHashSet()) ?: LinkedHashSet())
    fun addCustomSticker(e: String) {
        if (e.isNotBlank()) {
            val s = customStickers(); s.add(e)
            sp.edit().putStringSet("customStickers", s).apply()
        }
    }

    /* posición del botón flotante del lápiz en el editor */
    var floatPenFx: Float
        get() = sp.getFloat("floatPenFx", -1f)
        set(v) = sp.edit().putFloat("floatPenFx", v).apply()
    var floatPenFy: Float
        get() = sp.getFloat("floatPenFy", -1f)
        set(v) = sp.edit().putFloat("floatPenFy", v).apply()

    /** Dirección de desplazamiento del diario: 0 vertical, 1 horizontal (dos dedos cambian de página) */
    var scrollDir: Int
        get() = sp.getInt("scrollDir", 1)
        set(v) = sp.edit().putInt("scrollDir", v).apply()

    /** Tipo de página por DEFECTO al crear diarios (0 rayado … 9 agenda) */
    var defaultTemplate: Int
        get() = sp.getInt("defaultTemplate", 0)
        set(v) = sp.edit().putInt("defaultTemplate", v).apply()
}

/** Aplica el modo día/noche elegido (o «sigue al sistema»). */
object ThemeKit {
    fun apply() {
        val mode = when (Prefs.theme) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
