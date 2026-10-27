package com.knotes.app.ui

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
import androidx.fragment.app.Fragment
import com.knotes.app.R
import java.util.Calendar

/** Calendario: lista · mes · semana · día, con clima, objetivos mensuales y tareas. */
class CalendarFragment : Fragment() {

    private var mode = 1                      // 0 lista · 1 mes · 2 semana · 3 día
    private val shown: Calendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var selDay = TaskStore.today()

    private lateinit var body: LinearLayout
    private lateinit var headRow: LinearLayout

    private fun dp(v: Int) = TaskUi.dp(requireContext(), v)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx).apply { setBackgroundColor(ctx.getColor(R.color.bg_app)) }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        headRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        col.addView(headRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))
        body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(90)) }
        val sv = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(body) }
        col.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(col)
        root.addView(ImageButton(ctx).apply {
            background = ctx.getDrawable(com.knotes.app.ui.Gender.circleBg())
            setImageResource(R.drawable.ic_add); imageTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A1A.toInt())
            elevation = dp(8).toFloat()
            setOnClickListener { TaskUi.addTaskDialog(ctx, selDay) { render() } }
        }, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END).also { it.setMargins(0, 0, dp(16), dp(16)) })
        render()
        return root
    }

    private fun render() {
        val ctx = requireContext()
        headRow.removeAllViews()
        fun arrow(res: Int, delta: Int) = ImageButton(ctx).apply {
            setImageResource(res); background = null
            imageTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.text_primary))
            setOnClickListener { shown.add(Calendar.MONTH, delta); render() }
        }
        headRow.addView(arrow(R.drawable.ic_chevron_right, -1).apply { rotation = 180f }, LinearLayout.LayoutParams(dp(30), dp(36)))
        headRow.addView(TextView(ctx).apply {
            text = TaskUi.monthName(shown); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(requireContext().getColor(R.color.text_primary))
            gravity = Gravity.CENTER; setPadding(dp(6), 0, dp(6), 0)
        })
        headRow.addView(arrow(R.drawable.ic_chevron_right, 1), LinearLayout.LayoutParams(dp(30), dp(36)))
        headRow.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        headRow.addView(TaskUi.segmented(ctx, listOf("lista", "mes", "semana", "día"), mode) { mode = it; render() })

        body.removeAllViews()
        when (mode) {
            1 -> monthView()
            0 -> listView()
            2 -> weekView()
            else -> dayView()
        }
    }

    /* ---------- tarjeta de clima (sin datos, como la captura) ---------- */
    private fun weatherCard(): View {
        val ctx = requireContext()
        val c = TaskStore.calOf(selDay)
        val card = FrameLayout(ctx).apply { background = ctx.getDrawable(R.drawable.bg_weather_card) }
        card.addView(IllustrationView(ctx).also { it.kind = 1 }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(90), Gravity.TOP).also { it.topMargin = dp(10) })
        val txt = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), dp(12)) }
        txt.addView(TextView(ctx).apply { text = TaskUi.weekdayName(c); textSize = 13f; setTextColor(Color.WHITE) })
        txt.addView(TextView(ctx).apply {
            text = "${c.get(Calendar.DAY_OF_MONTH)} ${TaskUi.monthName(c).lowercase()}"; textSize = 13f; setTextColor(Color.WHITE)
        })
        card.addView(txt, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        return card
    }

    private fun goalsCard(): View {
        val ctx = requireContext()
        val key = TaskStore.monthKey(shown)
        val goals = TaskStore.goals(key)
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; background = ctx.getDrawable(R.drawable.bg_card_soft)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        card.addView(TextView(ctx).apply {
            text = "OBJETIVOS MENSUALES"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Gender.accent(requireContext()))
        })
        for ((i, g) in goals.withIndex()) card.addView(TextView(ctx).apply {
            text = "• $g"; textSize = 13.5f; setTextColor(requireContext().getColor(R.color.text_primary)); setPadding(0, dp(6), 0, 0)
            setOnLongClickListener {
                Dialogs.light(ctx).setTitle("¿Quitar este objetivo?")
                    .setPositiveButton("Quitar") { _, _ -> goals.removeAt(i); TaskStore.saveGoals(key, goals); render() }
                    .setNegativeButton("Cancelar", null).show(); true
            }
        })
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0)
            addView(TextView(ctx).apply {
                text = "+"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(0xFF1A1A1A.toInt())
                background = ctx.getDrawable(R.drawable.bg_circle_pink)
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(TextView(ctx).apply { text = "Agregar Objetivos"; textSize = 13.5f; setTextColor(ctx.getColor(R.color.text_dim)); setPadding(dp(10), 0, 0, 0) })
            setOnClickListener {
                val input = EditText(ctx).apply {
                    hint = "Objetivo del mes"; setTextColor(0xFF38322F.toInt()); setHintTextColor(0xFF9A9186.toInt())
                    background = ctx.getDrawable(R.drawable.bg_cell_white); setPadding(dp(14), dp(12), dp(14), dp(12))
                }
                val fr = FrameLayout(ctx).apply { setPadding(dp(18), dp(4), dp(18), 0); addView(input) }
                Dialogs.light(ctx).setTitle("Objetivos de ${TaskUi.monthName(shown).lowercase()}").setView(fr)
                    .setPositiveButton("Agregar") { _, _ ->
                        val t = input.text.toString().trim()
                        if (t.isNotEmpty()) { goals.add(t); TaskStore.saveGoals(key, goals); render() }
                    }.setNegativeButton("Cancelar", null).show()
            }
        })
        return card
    }

    private fun tasksPanel(day: Int): View {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        box.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply { text = "tarea"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(requireContext().getColor(R.color.text_primary)) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(ctx).apply {
                text = "ver todo"; textSize = 13f; setTextColor(ctx.getColor(R.color.text_chip)); paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                setOnClickListener { mode = 0; render() }
            })
        })
        val list = TaskStore.load().filter { it.day == day }.sortedBy { it.time }
        if (list.isEmpty()) {
            box.addView(IllustrationView(ctx), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)).also { it.topMargin = dp(20) })
            box.addView(TextView(ctx).apply {
                text = "+  Agregar tarea aquí"; textSize = 13.5f; gravity = Gravity.CENTER; setTextColor(ctx.getColor(R.color.text_dim))
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { TaskUi.addTaskDialog(ctx, day) { render() } }
            })
        } else for (t in list) box.addView(TaskUi.taskRow(ctx, t) { render() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })
        return box
    }

    /* ---------- vista MES ---------- */
    private fun monthView() {
        val ctx = requireContext()
        val wide = resources.displayMetrics.widthPixels - dp(62) >= dp(520)
        val grid = monthGrid()
        if (wide) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val left = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            left.addView(weatherCard(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)))
            left.addView(tasksPanel(selDay), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(18) })
            val right = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            right.addView(goalsCard(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            right.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) })
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.36f).also { it.marginEnd = dp(12) })
            row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.64f))
            body.addView(row)
        } else {
            val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            top.addView(weatherCard(), LinearLayout.LayoutParams(0, dp(150), 0.4f).also { it.marginEnd = dp(10) })
            top.addView(goalsCard(), LinearLayout.LayoutParams(0, dp(150), 0.6f))
            body.addView(top)
            body.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(12) })
            body.addView(tasksPanel(selDay), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(16) })
        }
    }

    private fun monthGrid(): View {
        val ctx = requireContext()
        val tasks = TaskStore.load().groupBy { it.day }
        val today = TaskStore.today()
        val wrap = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = ctx.getDrawable(R.drawable.bg_card_soft) }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; background = ctx.getDrawable(R.drawable.bg_cal_head) }
        for (n in listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")) head.addView(TextView(ctx).apply {
            text = n; textSize = 12.5f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(0xFF1A1A1A.toInt())
            setPadding(0, dp(9), 0, dp(9))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        wrap.addView(head)
        val first = (shown.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        first.add(Calendar.DAY_OF_MONTH, -offset)
        for (r in 0 until 6) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (cI in 0 until 7) {
                val key = TaskStore.dayKey(first)
                val inMonth = first.get(Calendar.MONTH) == shown.get(Calendar.MONTH)
                val isToday = key == today
                val cell = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(2), dp(2))
                    background = ctx.getDrawable(if (key == selDay && !isToday) R.drawable.bg_cal_cell_sel else if (isToday) R.drawable.bg_cal_cell_today else R.drawable.bg_cal_cell)
                    setOnClickListener { selDay = key; render() }
                }
                cell.addView(TextView(ctx).apply {
                    text = first.get(Calendar.DAY_OF_MONTH).toString(); textSize = 12f
                    setTextColor(if (isToday) Gender.accent(requireContext()) else if (inMonth) requireContext().getColor(R.color.text_primary) else 0xFF5A5A5A.toInt())
                })
                tasks[key]?.take(2)?.forEach { t ->
                    cell.addView(TextView(ctx).apply {
                        text = t.title; textSize = 9f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(0xFF1A1A1A.toInt()); setBackgroundColor(Gender.accent(requireContext())); setPadding(dp(2), 0, dp(2), 0)
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(2) })
                }
                row.addView(cell, LinearLayout.LayoutParams(0, dp(58), 1f))
                first.add(Calendar.DAY_OF_MONTH, 1)
            }
            wrap.addView(row)
        }
        return wrap
    }

    /* ---------- vista LISTA ---------- */
    private fun listView() {
        val ctx = requireContext()
        val ym = shown.get(Calendar.YEAR) * 100 + shown.get(Calendar.MONTH) + 1
        val list = TaskStore.load().filter { it.day / 100 == ym }.sortedWith(compareBy({ it.day }, { it.time }))
        if (list.isEmpty()) { emptyMsg("No hay tareas en ${TaskUi.monthName(shown).lowercase()}"); return }
        var last = -1
        for (t in list) {
            if (t.day != last) {
                last = t.day
                val c = TaskStore.calOf(t.day)
                body.addView(TextView(ctx).apply {
                    text = "${TaskUi.weekdayName(c)} ${c.get(Calendar.DAY_OF_MONTH)}"; textSize = 13f
                    setTextColor(Gender.accent(requireContext())); setPadding(dp(4), dp(14), 0, dp(4))
                })
            }
            body.addView(TaskUi.taskRow(ctx, t) { render() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(6) })
        }
    }

    /* ---------- vista SEMANA ---------- */
    private fun weekView() {
        val ctx = requireContext()
        val start = TaskStore.calOf(selDay)
        start.add(Calendar.DAY_OF_MONTH, -((start.get(Calendar.DAY_OF_WEEK) + 5) % 7))
        val tasks = TaskStore.load()
        for (i in 0 until 7) {
            val key = TaskStore.dayKey(start)
            val isToday = key == TaskStore.today()
            body.addView(TextView(ctx).apply {
                text = "${TaskUi.weekdayName(start)} ${start.get(Calendar.DAY_OF_MONTH)}"; textSize = 13.5f
                setTextColor(if (isToday) Gender.accent(requireContext()) else requireContext().getColor(R.color.text_primary)); typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(4), dp(12), 0, dp(4))
                setOnClickListener { selDay = key; mode = 3; render() }
            })
            val ts = tasks.filter { it.day == key }.sortedBy { it.time }
            if (ts.isEmpty()) body.addView(TextView(ctx).apply { text = "—"; setTextColor(ctx.getColor(R.color.text_dim)); setPadding(dp(8), 0, 0, 0) })
            for (t in ts) body.addView(TaskUi.taskRow(ctx, t) { render() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(4) })
            start.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    /* ---------- vista DÍA ---------- */
    private fun dayView() {
        val ctx = requireContext()
        val c = TaskStore.calOf(selDay)
        body.addView(TextView(ctx).apply {
            text = "${TaskUi.weekdayName(c)} ${c.get(Calendar.DAY_OF_MONTH)} de ${TaskUi.monthName(c).lowercase()}"
            textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(requireContext().getColor(R.color.text_primary)); setPadding(dp(4), dp(8), 0, dp(8))
        })
        body.addView(tasksPanel(selDay))
    }

    private fun emptyMsg(msg: String) {
        val ctx = requireContext()
        body.addView(IllustrationView(ctx), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).also { it.topMargin = dp(40) })
        body.addView(TextView(ctx).apply { text = msg; gravity = Gravity.CENTER; setTextColor(ctx.getColor(R.color.text_dim)); setPadding(0, dp(8), 0, 0) })
    }
}
