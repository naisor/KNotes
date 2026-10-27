package com.knotes.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.knotes.app.R
import java.util.Calendar

/** Tareas: caducado · lista · mes · semana · Estadísticas, con tira de días. */
class TasksFragment : Fragment() {

    private var mode = 1                  // 0 caducado · 1 lista · 2 mes · 3 semana · 4 estadísticas
    private var selDay = TaskStore.today()

    private lateinit var headRow: LinearLayout
    private lateinit var strip: LinearLayout
    private lateinit var stripScroll: HorizontalScrollView
    private lateinit var body: LinearLayout

    private fun dp(v: Int) = TaskUi.dp(requireContext(), v)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val root = FrameLayout(ctx).apply { setBackgroundColor(ctx.getColor(R.color.bg_app)) }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        headRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(10), dp(8))
        }
        col.addView(headRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))
        stripScroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        strip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(10), dp(4), dp(10), dp(8)) }
        stripScroll.addView(strip)
        col.addView(stripScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(90)) }
        col.addView(ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(body) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(col)
        root.addView(ImageButton(ctx).apply {
            background = ctx.getDrawable(Gender.circleBg())
            setImageResource(R.drawable.ic_add); imageTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A1A.toInt())
            elevation = dp(8).toFloat()
            setOnClickListener { TaskUi.addTaskDialog(ctx, selDay) { render() } }
        }, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END).also { it.setMargins(0, 0, dp(16), dp(16)) })
        render()
        return root
    }

    private fun render() {
        val ctx = requireContext()
        val sel = TaskStore.calOf(selDay)
        headRow.removeAllViews()
        headRow.addView(TextView(ctx).apply {
            text = TaskUi.monthName(sel); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(requireContext().getColor(R.color.text_primary))
        })
        headRow.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        val hs = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        hs.addView(TaskUi.segmented(ctx, listOf("caducado", "lista", "mes", "semana", "Estadísticas"), mode) { mode = it; render() })
        headRow.addView(hs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // tira de días: 3 días atrás … 12 adelante (como la captura: dom 18 … vie 30)
        strip.removeAllViews()
        val days = arrayOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")
        val c = TaskStore.calOf(selDay)
        c.add(Calendar.DAY_OF_MONTH, -4)
        var selView: View? = null
        for (n in 0 until 16) {
            val key = TaskStore.dayKey(c)
            val on = key == selDay
            val chip = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = ctx.getDrawable(if (on) Gender.chipSelBg() else R.drawable.bg_chip_normal)
                setOnClickListener { selDay = key; if (mode == 0 || mode == 4) mode = 1; render() }
            }
            chip.addView(TextView(ctx).apply {
                text = c.get(Calendar.DAY_OF_MONTH).toString(); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (on) 0xFF1A1A1A.toInt() else requireContext().getColor(R.color.text_primary))
            })
            chip.addView(TextView(ctx).apply {
                text = days[c.get(Calendar.DAY_OF_WEEK) - 1]; textSize = 11f
                setTextColor(if (on) 0xFF1A1A1A.toInt() else ctx.getColor(R.color.text_dim))
            })
            strip.addView(chip, LinearLayout.LayoutParams(dp(52), dp(54)).also { it.marginEnd = dp(8) })
            if (on) selView = chip
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        stripScroll.visibility = if (mode == 4 || mode == 0) View.GONE else View.VISIBLE
        stripScroll.post { selView?.let { stripScroll.scrollTo(maxOf(0, it.left - dp(120)), 0) } }

        body.removeAllViews()
        val all = TaskStore.load()
        when (mode) {
            0 -> showList(all.filter { it.day < TaskStore.today() && !it.done }.sortedBy { it.day }, "Nada caducado 🎉", true)
            1 -> showList(all.filter { it.day == selDay }.sortedBy { it.time }, null, false)
            2 -> { val ym = selDay / 100; showList(all.filter { it.day / 100 == ym }.sortedBy { it.day }, null, true) }
            3 -> {
                val st = TaskStore.calOf(selDay); st.add(Calendar.DAY_OF_MONTH, -((st.get(Calendar.DAY_OF_WEEK) + 5) % 7))
                val a = TaskStore.dayKey(st); st.add(Calendar.DAY_OF_MONTH, 6); val b = TaskStore.dayKey(st)
                showList(all.filter { it.day in a..b }.sortedBy { it.day }, null, true)
            }
            else -> stats(all)
        }
    }

    private fun showList(list: List<Task>, emptyText: String?, withDates: Boolean) {
        val ctx = requireContext()
        if (list.isEmpty()) {
            body.addView(IllustrationView(ctx), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)).also { it.topMargin = dp(70) })
            body.addView(TextView(ctx).apply {
                text = emptyText ?: "+  Agregar tarea aquí"; gravity = Gravity.CENTER; textSize = 13.5f
                setTextColor(ctx.getColor(R.color.text_dim)); setPadding(0, dp(8), 0, dp(8))
                if (emptyText == null) setOnClickListener { TaskUi.addTaskDialog(ctx, selDay) { render() } }
            })
            return
        }
        var last = -1
        for (t in list) {
            if (withDates && t.day != last) {
                last = t.day
                val c = TaskStore.calOf(t.day)
                body.addView(TextView(ctx).apply {
                    text = "${TaskUi.weekdayName(c)} ${c.get(Calendar.DAY_OF_MONTH)}"; textSize = 13f
                    setTextColor(Gender.accent(requireContext())); setPadding(dp(4), dp(12), 0, dp(4))
                })
            }
            body.addView(TaskUi.taskRow(ctx, t) { render() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(6) })
        }
    }

    private fun stats(all: List<Task>) {
        val ctx = requireContext()
        val done = all.count { it.done }
        val late = all.count { !it.done && it.day < TaskStore.today() }
        val pending = all.size - done - late
        val pct = if (all.isEmpty()) 0 else done * 100 / all.size
        fun card(title: String, value: String, color: Int) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = ctx.getDrawable(R.drawable.bg_card_soft); setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            row.addView(TextView(ctx).apply { text = title; textSize = 14.5f; setTextColor(requireContext().getColor(R.color.text_primary)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(ctx).apply { text = value; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color) })
            body.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10) })
        }
        card("Total de tareas", all.size.toString(), requireContext().getColor(R.color.text_primary))
        card("Completadas ✅", done.toString(), Gender.accent(requireContext()))
        card("Pendientes", pending.toString(), 0xFF8FD3C9.toInt())
        card("Caducadas", late.toString(), 0xFFEF7B7B.toInt())
        val bar = FrameLayout(ctx).apply { background = ctx.getDrawable(R.drawable.bg_chip_normal) }
        bar.addView(View(ctx).apply { background = ctx.getDrawable(Gender.chipSelBg()) },
            FrameLayout.LayoutParams(maxOf(dp(12), (resources.displayMetrics.widthPixels - dp(62) - dp(28)) * pct / 100), dp(14)))
        body.addView(TextView(ctx).apply { text = "Progreso: $pct %"; setTextColor(ctx.getColor(R.color.text_chip)); setPadding(dp(4), dp(18), 0, dp(6)) })
        body.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14)))
    }
}
