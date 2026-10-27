package com.knotes.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.knotes.app.R
import com.knotes.app.data.Ids
import com.knotes.app.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** Tarea del calendario. day = yyyyMMdd */
class Task(val id: String, var title: String, var day: Int, var time: String = "", var done: Boolean = false)

/** Persistencia local (JSON en las preferencias): tareas y objetivos mensuales. */
object TaskStore {
    private const val K_TASKS = "tasks_json"
    private const val K_GOALS = "goals_json"

    fun dayKey(c: Calendar): Int = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    fun today(): Int = dayKey(Calendar.getInstance())
    fun monthKey(c: Calendar): String = "%04d%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)

    fun calOf(day: Int): Calendar = Calendar.getInstance().apply {
        set(day / 10000, (day / 100) % 100 - 1, day % 100, 12, 0, 0)
    }

    fun load(): MutableList<Task> {
        val out = ArrayList<Task>()
        try {
            val arr = JSONArray(Prefs.raw().getString(K_TASKS, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Task(o.getString("id"), o.getString("t"), o.getInt("d"), o.optString("h", ""), o.optBoolean("x", false)))
            }
        } catch (_: Exception) { }
        return out
    }

    fun save(list: List<Task>) {
        val arr = JSONArray()
        for (t in list) arr.put(JSONObject().put("id", t.id).put("t", t.title).put("d", t.day).put("h", t.time).put("x", t.done))
        Prefs.raw().edit().putString(K_TASKS, arr.toString()).apply()
    }

    fun add(title: String, day: Int, time: String) {
        val l = load(); l.add(Task(Ids.next(), title, day, time)); save(l)
    }

    fun toggle(id: String) { val l = load(); l.firstOrNull { it.id == id }?.let { it.done = !it.done }; save(l) }
    fun delete(id: String) { val l = load(); l.removeAll { it.id == id }; save(l) }

    fun goals(month: String): MutableList<String> {
        val out = ArrayList<String>()
        try {
            val arr = JSONObject(Prefs.raw().getString(K_GOALS, "{}")).optJSONArray(month)
            if (arr != null) for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (_: Exception) { }
        return out
    }

    fun saveGoals(month: String, list: List<String>) {
        val o = try { JSONObject(Prefs.raw().getString(K_GOALS, "{}")) } catch (_: Exception) { JSONObject() }
        o.put(month, JSONArray(list))
        Prefs.raw().edit().putString(K_GOALS, o.toString()).apply()
    }
}

object TaskUi {
    private val ES = Locale("es")

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun monthName(c: Calendar): String =
        java.text.SimpleDateFormat("MMMM", ES).format(c.time).uppercase(ES)

    fun weekdayName(c: Calendar): String = java.text.SimpleDateFormat("EEEE", ES).format(c.time)

    /** control segmentado: fondo oscuro redondeado, la opción activa en rosa */
    fun segmented(ctx: Context, labels: List<String>, sel: Int, onSel: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
            background = ctx.getDrawable(R.drawable.bg_chip_normal)
        }
        for ((i, l) in labels.withIndex()) {
            val on = i == sel
            row.addView(TextView(ctx).apply {
                text = l; textSize = 13.5f; gravity = Gravity.CENTER
                setTextColor(if (on) 0xFF1A1A1A.toInt() else ctx.getColor(R.color.text_primary))
                setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
                if (on) background = ctx.getDrawable(R.drawable.bg_chip_selected)
                setOnClickListener { onSel(i) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return row
    }

    /** diálogo «nueva tarea»: título + fecha + hora opcional */
    fun addTaskDialog(ctx: Context, day: Int, onDone: () -> Unit) {
        var d = day
        var time = ""
        val input = EditText(ctx).apply {
            hint = "¿Qué quieres hacer?"
            setTextColor(0xFF38322F.toInt()); setHintTextColor(0xFF9A9186.toInt())
            background = ctx.getDrawable(R.drawable.bg_cell_white)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
        }
        val dateBtn = TextView(ctx).apply { textSize = 14f; setTextColor(0xFF5A6AA8.toInt()); setPadding(dp(ctx, 4), dp(ctx, 12), dp(ctx, 4), dp(ctx, 4)) }
        val timeBtn = TextView(ctx).apply { textSize = 14f; setTextColor(0xFF5A6AA8.toInt()); setPadding(dp(ctx, 4), dp(ctx, 8), dp(ctx, 4), dp(ctx, 4)) }
        fun paint() {
            val c = TaskStore.calOf(d)
            dateBtn.text = "📅  ${weekdayName(c)} ${c.get(Calendar.DAY_OF_MONTH)} de ${monthName(c).lowercase(ES)}"
            timeBtn.text = if (time.isEmpty()) "⏰  Añadir hora (opcional)" else "⏰  $time"
        }
        paint()
        dateBtn.setOnClickListener {
            val c = TaskStore.calOf(d)
            DatePickerDialog(ctx, { _, y, m, dd -> d = y * 10000 + (m + 1) * 100 + dd; paint() },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        timeBtn.setOnClickListener {
            TimePickerDialog(ctx, { _, h, mi -> time = "%02d:%02d".format(h, mi); paint() }, 9, 0, true).show()
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), 0)
            addView(input); addView(dateBtn); addView(timeBtn)
        }
        Dialogs.light(ctx).setTitle("Nueva tarea").setView(box)
            .setPositiveButton("Agregar") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) { TaskStore.add(t, d, time); onDone() }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    /** fila de tarea con casilla; mantener pulsado = borrar */
    fun taskRow(ctx: Context, t: Task, onChange: () -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = ctx.getDrawable(R.drawable.bg_card_soft)
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        }
        row.addView(TextView(ctx).apply {
            text = if (t.done) "☑" else "☐"; textSize = 20f
            setTextColor(if (t.done) Gender.accent(ctx) else ctx.getColor(R.color.text_chip))
            setPadding(0, 0, dp(ctx, 10), 0)
            setOnClickListener { TaskStore.toggle(t.id); onChange() }
        })
        row.addView(TextView(ctx).apply {
            text = t.title; textSize = 14.5f
            setTextColor(if (t.done) ctx.getColor(R.color.text_dim) else ctx.getColor(R.color.text_primary))
            if (t.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (t.time.isNotEmpty()) row.addView(TextView(ctx).apply {
            text = t.time; textSize = 12f; setTextColor(ctx.getColor(R.color.text_dim))
        })
        row.setOnLongClickListener {
            Dialogs.light(ctx).setTitle("¿Borrar «${t.title}»?")
                .setPositiveButton("Borrar") { _, _ -> TaskStore.delete(t.id); onChange() }
                .setNegativeButton("Cancelar", null).show()
            true
        }
        return row
    }
}

/** Ilustraciones vacías: 0 = portapapeles con persona · 1 = clima sin datos */
class IllustrationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var kind = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (kind == 1) { drawWeather(c, w, h); return }
        // mancha rosa
        p.color = Gender.accent(context); c.drawOval(RectF(w * 0.08f, h * 0.78f, w * 0.92f, h * 0.98f), p)
        // portapapeles
        p.color = 0xFFF4F4F8.toInt(); c.drawRoundRect(RectF(w * 0.18f, h * 0.12f, w * 0.52f, h * 0.88f), 8f, 8f, p)
        p.color = 0xFF5A78C8.toInt(); c.drawRoundRect(RectF(w * 0.28f, h * 0.06f, w * 0.42f, h * 0.16f), 5f, 5f, p)
        for (k in 0..5) {
            p.color = if (k < 3) Gender.accent(context) else 0xFFC8C8D0.toInt()
            c.drawRoundRect(RectF(w * 0.26f, h * (0.22f + k * 0.1f), w * 0.46f, h * (0.255f + k * 0.1f)), 3f, 3f, p)
        }
        p.color = 0xFF5A78C8.toInt(); c.drawCircle(w * 0.23f, h * 0.31f, w * 0.015f, p); c.drawCircle(w * 0.23f, h * 0.41f, w * 0.015f, p)
        // persona arrodillada
        p.color = 0xFF22305A.toInt(); c.drawCircle(w * 0.62f, h * 0.38f, w * 0.045f, p)
        p.color = 0xFFEF5B5B.toInt(); c.drawRoundRect(RectF(w * 0.55f, h * 0.43f, w * 0.72f, h * 0.66f), 12f, 12f, p)
        p.color = 0xFFEF5B5B.toInt(); c.drawRoundRect(RectF(w * 0.45f, h * 0.45f, w * 0.6f, h * 0.5f), 8f, 8f, p)
        p.color = 0xFF3B6FE0.toInt(); c.drawRoundRect(RectF(w * 0.5f, h * 0.64f, w * 0.78f, h * 0.76f), 10f, 10f, p)
        p.color = 0xFF3B6FE0.toInt(); c.drawRoundRect(RectF(w * 0.66f, h * 0.7f, w * 0.8f, h * 0.84f), 8f, 8f, p)
        p.color = 0xFF1A1A1A.toInt(); c.drawRoundRect(RectF(w * 0.46f, h * 0.82f, w * 0.58f, h * 0.86f), 3f, 3f, p)
        // hojas verdes
        p.color = 0xFF2E8B57.toInt(); c.drawOval(RectF(w * 0.74f, h * 0.66f, w * 0.96f, h * 0.9f), p)
        p.color = 0xFF3FAE70.toInt(); c.drawOval(RectF(w * 0.8f, h * 0.58f, w * 0.94f, h * 0.78f), p)
        // triángulos decorativos
        p.color = Gender.accent(context)
        val tri = Path().apply { moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.68f, h * 0.3f); lineTo(w * 0.57f, h * 0.3f); close() }
        c.drawPath(tri, p)
    }

    private fun drawWeather(c: Canvas, w: Float, h: Float) {
        // sol detrás
        p.color = 0xFFE9D9B4.toInt(); c.drawCircle(w * 0.5f, h * 0.38f, w * 0.14f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            c.drawLine(w * 0.5f + Math.cos(a).toFloat() * w * 0.2f, h * 0.38f + Math.sin(a).toFloat() * w * 0.2f,
                w * 0.5f + Math.cos(a).toFloat() * w * 0.25f, h * 0.38f + Math.sin(a).toFloat() * w * 0.25f, p)
        }
        p.style = Paint.Style.FILL
        // nube gris
        p.color = 0xFFB0B0B8.toInt()
        c.drawCircle(w * 0.4f, h * 0.55f, w * 0.12f, p); c.drawCircle(w * 0.55f, h * 0.5f, w * 0.15f, p)
        c.drawRoundRect(RectF(w * 0.26f, h * 0.55f, w * 0.7f, h * 0.68f), 20f, 20f, p)
        // X roja
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = 0xFFEF4444.toInt()
        c.drawLine(w * 0.42f, h * 0.5f, w * 0.58f, h * 0.66f, p); c.drawLine(w * 0.58f, h * 0.5f, w * 0.42f, h * 0.66f, p)
        p.style = Paint.Style.FILL
    }
}
