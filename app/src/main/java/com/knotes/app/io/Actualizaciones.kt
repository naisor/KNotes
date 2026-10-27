package com.knotes.app.io

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.knotes.app.ui.Dialogs
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/* ============================================================
   Actualizaciones — canal de GitHub para ACTUALIZAR la app,
   con GIST para el control de versiones.

   ✅ YA CONECTADO → gist «Aquí se controlará las versiones de
   la App KNotes» (archivo KNotes-Updates.json).

   CÓMO PUBLICAR UNA VERSIÓN NUEVA:
   1) Sube el APK a un GitHub Release des un repositorio PÚBLICO
      (los Release de repos privados NO dejan descargar al
      público: el teléfono responde 403/404 y aun así no baja).
   2) En el gist, edita KNotes-Updates.json:
        {
          "version": 12,          ← NÚMERO ENTERO: el versionCode del APK nuevo
          "name": "v1.5.6",       ← nombre bonito para el anuncio
          "notas": "...",         ← lo que se le cuenta al usuario
          "apk": "https://github.com/usuario/repo/releases/download/KNotes-vX.Y.Z/KNotes-vX.Y.Z.apk"
        }
   3) Nada más: la app (al abrir, máximo cada 12 h, o desde
      «Buscar actualizaciones») compara «version» con SU
      versionCode y si es mayor:
        · enseña un banner PEQUEÑO arriba con «Ir»,
        · dice «Nueva versión disponible — vX» en Ajustes,
        · y al buscar la descarga con progreso e instala.
   NOTA IMPORTANTE: el repositorio del release DEBE ser público.
   ============================================================ */
object Actualizaciones {

    /** ⇩ AQUI VA TU GIST (URL raw del JSON de versiones) — CONECTADA */
    const val GIST_URL_HERE =
        "https://gist.githubusercontent.com/naisor/b4fea4980d70f8c807aab70299ef7362/raw/KNotes-Updates.json"

    /** ⇩ AQUI VA TU GITHUB (URL directa del .apk más reciente) — repuesto solo */
    const val APK_URL_HERE =
        "https://github.com/NaisorStudio/KNotes/releases/download/KNotes-v1.5.6/KNotes-v1.5.6.apk"

    private const val CHECK_EVERY_MS = 12L * 60 * 60 * 1000   // 12 h

    /** estado visible para el banner de INICIO y la fila de Ajustes */
    @Volatile var available = false
    @Volatile var availableName = ""
    /** avisa a la Pantalla de Inicio para pintar/esconder el banner al instante */
    var uiListener: (() -> Unit)? = null

    /** Texto de la fila (Ajustes): «Nueva versión disponible — vX» cuando hay novedad */
    fun status(): String =
        if (available) "🔔 Nueva versión disponible — $availableName"
        else "Toca para buscar versión nueva"

    /** Busca si hay versión nueva. auto=true = silencioso al abrir la app (solo banner). */
    fun check(activity: Activity, auto: Boolean) {
        val sp = activity.getSharedPreferences("knotes", Context.MODE_PRIVATE)
        val last = sp.getLong("updateLastCheck", 0)
        val now = System.currentTimeMillis()
        if (auto && now - last < CHECK_EVERY_MS) return
        sp.edit().putLong("updateLastCheck", now).apply()

        Thread {
            var errorUI = ""
            var newCode = -1
            var name = ""
            var notes = ""
            var apk = ""
            try {
                // ↺ anti-caché: cada búsqueda lleva un sello distinto → gist FRESCO
                val fresh = GIST_URL_HERE + (if (GIST_URL_HERE.contains("?")) "&" else "?") +
                    "cb=" + now.toString(36)
                val conn = URL(fresh).openConnection() as java.net.HttpURLConnection
                try {
                    conn.connectTimeout = 12000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("Cache-Control", "no-cache")
                    val txt = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(txt)
                    newCode = json.optInt("version", 0)
                    name = json.optString("name", "nueva versión")
                    notes = json.optString("notas", "")
                    apk = json.optString("apk", APK_URL_HERE)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                errorUI = e.message ?: "error de red"
            }
            activity.runOnUiThread {
                if (newCode > 0) {
                    val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    val myCode = if (android.os.Build.VERSION.SDK_INT >= 28)
                        info.longVersionCode.toInt() else info.versionCode
                    if (newCode > myCode) {
                        available = true
                        availableName = name
                        uiListener?.invoke()
                        if (!auto) download(activity, apk, name, notes)
                    } else {
                        available = false
                        availableName = ""
                        uiListener?.invoke()
                        if (!auto) Toast.makeText(activity, "Ya tienes la versión más reciente ✔ ($name)", Toast.LENGTH_SHORT).show()
                    }
                } else if (!auto) {
                    // JSON válido pero sin «version» numérica, o gist no alcanzado — se dice de frente:
                    Toast.makeText(activity, if (errorUI.isBlank())
                        "El gist no tiene «version» numérica — revisa KNotes-Updates.json"
                    else "No se pudo buscar: $errorUI", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /* ======= descarga PROPIA con progreso (el DownloadManager del sistema no
       entiende los redirecciones de GitHub en todos los casos) ======= */

    fun download(activity: Activity, apkUrl: String, name: String, notes: String) {
        val safe = name.replace(" ", "_").replace("/", "_")
        val f = File(activity.cacheDir, "updates/KNotes-$safe.apk")
        f.parentFile?.mkdirs()
        if (f.exists()) f.delete()
        if (apkUrl.isBlank()) {
            Toast.makeText(activity, "El gist no dice dónde está el APK («apk»)", Toast.LENGTH_LONG).show()
            return
        }
        val stop = AtomicBoolean(false)
        val d = Dialogs.light(activity)
            .setTitle("Descargando $name 💿")
            .setMessage(if (notes.isBlank()) "Conectando…" else notes)
            .setCancelable(false)
            .setNegativeButton("Cancelar") { _, _ -> stop.set(true) }
            .create()
            .also { it.show() }
        Thread {
            var ok = false
            var errMsg = ""
            try {
                val conn = URL(apkUrl).openConnection() as java.net.HttpURLConnection
                try {
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "KNotes-Update/1.5")
                    val code = conn.responseCode
                    if (code != 200) {
                        errMsg = "El enlace respondió $code — revisa que la release esté publicada y que el repositorio sea público"
                    } else {
                        val total = conn.contentLengthLong
                        var done = 0L
                        var lastUp = 0L
                        conn.inputStream.use { ins ->
                            f.outputStream().use { out ->
                                val buf = ByteArray(16384)
                                while (!stop.get()) {
                                    val r = ins.read(buf)
                                    if (r < 0) break
                                    out.write(buf, 0, r)
                                    done += r
                                    val t = System.currentTimeMillis()
                                    if (t - lastUp > 250) {
                                        lastUp = t
                                        val msg = if (total > 0)
                                            "${done * 100 / total} %  ·  ${done / 1048576}/${total / 1048576} MB"
                                        else "${done / 1048576} MB descargados…"
                                        activity.runOnUiThread { d.setMessage(msg) }
                                    }
                                }
                            }
                        }
                        ok = !stop.get()
                        if (stop.get()) f.delete()
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                errMsg = e.message ?: "fallo de red"
            }
            activity.runOnUiThread {
                d.dismiss()
                when {
                    ok -> install(activity, f)
                    stop.get() -> Toast.makeText(activity, "Descarga cancelada", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(activity, "No se pudo descargar: $errMsg", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** abre el instalador del sistema con el APK descargado (permiso «apps desconocidas» una vez) */
    private fun install(activity: Activity, f: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                activity, "${activity.packageName}.files", f)
            val it = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(it)
        } catch (e: Exception) {
            Toast.makeText(activity, "No se pudo abrir el instalador: ${e.message ?: ""}", Toast.LENGTH_LONG).show()
        }
    }
}
